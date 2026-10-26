package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.input.AddItemState.Entering
import com.cheesecomer.cartmate.domain.input.AddItemState.Idle
import com.cheesecomer.cartmate.domain.input.AddItemState.Open

object AddItemReducer {
    /** Enter と「数量を設定して追加」で使う数量・単位 */
    const val DEFAULT_QUANTITY = 1.0
    const val DEFAULT_UNIT = "個"

    fun reduce(state: AddItemState, event: AddItemEvent): AddItemTransition = when (state) {
        is Idle -> reduceIdle(state, event)
        is Open -> reduceOpen(state, event)
        is Entering -> reduceEntering(state, event)
    }

    private fun reduceIdle(state: Idle, event: AddItemEvent): AddItemTransition = when (event) {
        AddItemEvent.FieldFocused -> AddItemTransition(Open(state.text))
        // シートを閉じている間の入力は通常起きないが、テキストだけは保持する
        is AddItemEvent.TextChanged -> AddItemTransition(Idle(event.text))
        else -> AddItemTransition(state)
    }

    private fun reduceOpen(state: Open, event: AddItemEvent): AddItemTransition {
        val name = state.text.trim()
        return when (event) {
            is AddItemEvent.TextChanged -> AddItemTransition(Open(event.text))
            // 連続して入力できるよう、シートは開いたまま入力欄を空にする
            AddItemEvent.Submitted ->
                if (name.isEmpty()) {
                    AddItemTransition(state)
                } else {
                    AddItemTransition(Open(""), AddItemEffect.AddItem(name, DEFAULT_QUANTITY, DEFAULT_UNIT))
                }
            is AddItemEvent.SuggestionAdded -> {
                val (quantity, unit) = lastOrDefault(event.suggestion)
                AddItemTransition(Open(""), AddItemEffect.AddItem(event.suggestion.name, quantity, unit))
            }
            is AddItemEvent.SuggestionOpened -> {
                val (quantity, unit) = lastOrDefault(event.suggestion)
                AddItemTransition(Entering(state.text), AddItemEffect.OpenEntry(event.suggestion.name, quantity, unit))
            }
            AddItemEvent.DetailRequested ->
                if (name.isEmpty()) {
                    AddItemTransition(state)
                } else {
                    AddItemTransition(Entering(state.text), AddItemEffect.OpenEntry(name, DEFAULT_QUANTITY, DEFAULT_UNIT))
                }
            AddItemEvent.Dismissed -> AddItemTransition(Idle(state.text))
            else -> AddItemTransition(state)
        }
    }

    private fun reduceEntering(state: Entering, event: AddItemEvent): AddItemTransition = when (event) {
        // 追加したらリスト詳細に戻り、商品追加欄は空にする。やめたらシートに戻り、入力した商品名を残す
        is AddItemEvent.EntryClosed -> AddItemTransition(if (event.added) Idle("") else Open(state.text))
        else -> AddItemTransition(state)
    }

    /** 候補の前回の数量・単位。購入の記録がなければ 1・「個」 */
    private fun lastOrDefault(suggestion: Suggestion): Pair<Double?, String?> =
        if (suggestion.lastQuantity == null && suggestion.lastUnit == null) {
            DEFAULT_QUANTITY to DEFAULT_UNIT
        } else {
            (suggestion.lastQuantity ?: DEFAULT_QUANTITY) to suggestion.lastUnit
        }
}
