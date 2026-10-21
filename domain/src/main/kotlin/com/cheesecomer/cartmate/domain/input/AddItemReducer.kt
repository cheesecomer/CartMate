package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.input.AddItemState.Detail
import com.cheesecomer.cartmate.domain.input.AddItemState.Editing
import com.cheesecomer.cartmate.domain.input.AddItemState.FocusedEmpty
import com.cheesecomer.cartmate.domain.input.AddItemState.Idle
import com.cheesecomer.cartmate.domain.input.AddItemState.Typing
import com.cheesecomer.cartmate.domain.model.Item

object AddItemReducer {

    fun reduce(state: AddItemState, event: AddItemEvent): AddItemTransition {
        // 項目のタップは、どの状態からでも編集を始める。追加の途中だった数量・単位は破棄する
        if (event is AddItemEvent.EditRequested) return AddItemTransition(startEditing(event.item, draftOf(state)))
        return when (state) {
            is Idle -> reduceIdle(state, event)
            FocusedEmpty, is Typing -> reduceNameInput(state, event)
            is Detail -> reduceDetail(state, event)
            is Editing -> reduceEditing(state, event)
        }
    }

    private fun reduceIdle(state: Idle, event: AddItemEvent): AddItemTransition = when (event) {
        is AddItemEvent.NameFocusChanged ->
            if (event.focused) AddItemTransition(nameInputState(state.text)) else AddItemTransition(state)
        // フォーカスがない間の入力は通常起きないが、テキストだけは保持する
        is AddItemEvent.NameChanged -> AddItemTransition(Idle(event.text))
        else -> AddItemTransition(state)
    }

    private fun reduceNameInput(state: AddItemState, event: AddItemEvent): AddItemTransition {
        val text = (state as? Typing)?.text.orEmpty()
        return when (event) {
            is AddItemEvent.NameFocusChanged ->
                if (event.focused) AddItemTransition(state) else AddItemTransition(Idle(text))
            is AddItemEvent.NameChanged -> AddItemTransition(nameInputState(event.text))
            AddItemEvent.NameSubmitted -> {
                val name = text.trim()
                if (name.isEmpty()) {
                    AddItemTransition(state)
                } else {
                    AddItemTransition(Detail(name = name, quantity = AddItemState.DEFAULT_QUANTITY, unit = ""))
                }
            }
            AddItemEvent.Cancelled -> AddItemTransition(Idle(text))
            else -> AddItemTransition(state)
        }
    }

    private fun reduceDetail(state: Detail, event: AddItemEvent): AddItemTransition = when (event) {
        is AddItemEvent.QuantityChanged -> AddItemTransition(state.copy(quantity = event.text))
        is AddItemEvent.UnitChanged -> AddItemTransition(state.copy(unit = event.text))
        AddItemEvent.Confirmed -> when (val quantity = state.quantityInput) {
            is QuantityInput.Valid -> AddItemTransition(
                state = FocusedEmpty,
                effect = AddItemEffect.AddItem(state.name, quantity.value, normalizeUnit(state.unit)),
            )
            QuantityInput.Invalid -> AddItemTransition(state)
        }
        AddItemEvent.Cancelled -> AddItemTransition(Typing(state.name))
        // フォーカスを失っても下書きを保持する
        else -> AddItemTransition(state)
    }

    private fun reduceEditing(state: Editing, event: AddItemEvent): AddItemTransition = when (event) {
        is AddItemEvent.NameChanged -> AddItemTransition(state.copy(name = event.text))
        is AddItemEvent.QuantityChanged -> AddItemTransition(state.copy(quantity = event.text))
        is AddItemEvent.UnitChanged -> AddItemTransition(state.copy(unit = event.text))
        AddItemEvent.Confirmed -> {
            val quantity = state.quantityInput
            if (quantity is QuantityInput.Valid && state.name.isNotBlank()) {
                AddItemTransition(
                    state = Idle(state.draft),
                    effect = AddItemEffect.UpdateItem(
                        itemId = state.itemId,
                        name = state.name.trim(),
                        quantity = quantity.value,
                        unit = normalizeUnit(state.unit),
                    ),
                )
            } else {
                AddItemTransition(state)
            }
        }
        AddItemEvent.Cancelled -> AddItemTransition(Idle(state.draft))
        else -> AddItemTransition(state)
    }

    private fun startEditing(item: Item, draft: String) = Editing(
        itemId = item.id,
        name = item.inputName,
        quantity = item.quantity?.let(::formatQuantity).orEmpty(),
        unit = item.unit.orEmpty(),
        draft = draft,
    )

    /** 編集を始める前に商品名の欄にあった文字。数量入力中なら、その商品名を残す */
    private fun draftOf(state: AddItemState): String = when (state) {
        is Idle -> state.text
        FocusedEmpty -> ""
        is Typing -> state.text
        is Detail -> state.name
        is Editing -> state.draft
    }

    private fun nameInputState(text: String): AddItemState = if (text.isEmpty()) FocusedEmpty else Typing(text)
}
