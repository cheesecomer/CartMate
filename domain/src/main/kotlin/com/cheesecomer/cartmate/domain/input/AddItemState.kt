package com.cheesecomer.cartmate.domain.input

/**
 * リスト詳細の商品追加欄とサジェストのシートの状態。遷移の一覧は docs/design/ui.md「商品追加欄とサジェストのシート」。
 * 候補の中身（時期が近い商品・補完）はフェーズ4で追加する。
 */
sealed interface AddItemState {
    val text: String

    /** シートを閉じている。商品追加欄に入力途中の商品名を残す */
    data class Idle(override val text: String) : AddItemState

    /** シートを開いている。空なら時期が近い商品、入力中なら補完を提示する */
    data class Open(override val text: String) : AddItemState

    /** 商品詳細入力画面を開いている。閉じて戻ってきたらシートを開き直す */
    data class Entering(override val text: String) : AddItemState

    companion object {
        val Initial: AddItemState = Idle("")
    }
}

/** サジェストの候補。前回の数量・単位は、購入の記録がなければ null */
data class Suggestion(
    val name: String,
    val lastQuantity: Double?,
    val lastUnit: String?,
)

sealed interface AddItemEvent {
    /** 画面下部の商品追加欄をタップした */
    data object FieldFocused : AddItemEvent
    data class TextChanged(val text: String) : AddItemEvent
    /** シートの入力欄で IME の Enter/Done */
    data object Submitted : AddItemEvent
    /** 候補の右端の「＋」 */
    data class SuggestionAdded(val suggestion: Suggestion) : AddItemEvent
    /** 候補の名前の部分をタップした */
    data class SuggestionOpened(val suggestion: Suggestion) : AddItemEvent
    /** 「数量を設定して追加」 */
    data object DetailRequested : AddItemEvent
    /** 戻るキー、幕のタップ、下へのスワイプでシートを閉じた */
    data object Dismissed : AddItemEvent
    /** 商品詳細入力画面から戻った。[added] は追加したかどうか */
    data class EntryClosed(val added: Boolean) : AddItemEvent
}

sealed interface AddItemEffect {
    /** すぐにリストの末尾に追加する。公開範囲はそのリストの直前の公開範囲 */
    data class AddItem(val name: String, val quantity: Double?, val unit: String?) : AddItemEffect

    /** 商品詳細入力画面を開く。数量・単位はその初期値 */
    data class OpenEntry(val name: String, val quantity: Double?, val unit: String?) : AddItemEffect
}

data class AddItemTransition(val state: AddItemState, val effect: AddItemEffect? = null)
