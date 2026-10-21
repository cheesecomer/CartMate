package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.input.QuantityInput.Companion.parse
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId

/**
 * 画面下部の商品追加欄の状態。遷移の一覧は docs/design/ui.md「商品追加欄の状態」。
 * サジェスト（候補のタップ、表記ゆれ、早すぎる再購入の注意）はフェーズ3〜4で追加する。
 */
sealed interface AddItemState {
    /** 商品名の欄にフォーカスがない。サジェストは表示しない */
    data class Idle(val text: String) : AddItemState

    /** フォーカス中で未入力。時期が近い商品を提示する */
    data object FocusedEmpty : AddItemState

    /** フォーカス中で入力あり。名前の補完を提示する */
    data class Typing(val text: String) : AddItemState

    /** 数量・単位の入力（インライン展開） */
    data class Detail(
        val name: String,
        val quantity: String,
        val unit: String,
    ) : AddItemState {
        val quantityInput: QuantityInput get() = parse(quantity)
        val canConfirm: Boolean get() = quantityInput is QuantityInput.Valid
    }

    /** 既存の項目の編集。[draft] は編集を始める前に商品名の欄に入力していた文字で、編集が終わったら戻す */
    data class Editing(
        val itemId: ItemId,
        val name: String,
        val quantity: String,
        val unit: String,
        val draft: String,
    ) : AddItemState {
        val quantityInput: QuantityInput get() = parse(quantity)
        val canConfirm: Boolean get() = name.isNotBlank() && quantityInput is QuantityInput.Valid
    }

    companion object {
        val Initial: AddItemState = Idle("")
        const val DEFAULT_QUANTITY = "1"
    }
}

sealed interface AddItemEvent {
    data class NameFocusChanged(val focused: Boolean) : AddItemEvent
    data class NameChanged(val text: String) : AddItemEvent
    /** 商品名の欄で IME の Enter/Done */
    data object NameSubmitted : AddItemEvent
    data class QuantityChanged(val text: String) : AddItemEvent
    data class UnitChanged(val text: String) : AddItemEvent
    /** 数量欄の IME Done、または「追加」「更新」ボタン */
    data object Confirmed : AddItemEvent
    /** ✕ ボタン、または戻るキー */
    data object Cancelled : AddItemEvent
    /** 未購入の項目の「編集」ボタンを押した */
    data class EditRequested(val item: Item) : AddItemEvent
}

sealed interface AddItemEffect {
    data class AddItem(val name: String, val quantity: Double?, val unit: String?) : AddItemEffect
    data class UpdateItem(val itemId: ItemId, val name: String, val quantity: Double?, val unit: String?) :
        AddItemEffect
}

data class AddItemTransition(val state: AddItemState, val effect: AddItemEffect? = null)
