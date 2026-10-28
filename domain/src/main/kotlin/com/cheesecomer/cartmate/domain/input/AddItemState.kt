package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.model.ProductId

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

    /** 候補の長押しで、商品の画面（商品名の管理）を開いている。戻ってきたらシートを開き直す（候補はそのときの状態で作り直す） */
    data class Managing(override val text: String) : AddItemState

    companion object {
        val Initial: AddItemState = Idle("")
    }
}

/**
 * サジェストの候補。前回の数量・単位は、購入の記録がなければ null。
 * [memo] は、備考の属性（「茶葉」「2L」）で分けて数えた候補の前回の備考で、「＋」で一緒に入れる
 */
data class Suggestion(
    val name: String,
    val lastQuantity: Double?,
    val lastUnit: String?,
    val memo: String? = null,
    /** 候補の商品（統合先）。長押しで商品の画面を開く */
    val productId: ProductId? = null,
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
    /** 候補を長押しした。商品の画面を開く */
    data class SuggestionLongPressed(val suggestion: Suggestion) : AddItemEvent
    /** 商品の画面から戻った（リスト詳細が再び前面に出た） */
    data object ProductClosed : AddItemEvent
    /** 「数量を設定して追加」 */
    data object DetailRequested : AddItemEvent
    /** 戻るキー、幕のタップ、下へのスワイプでシートを閉じた */
    data object Dismissed : AddItemEvent
    /** 商品詳細入力画面から戻った。[added] は追加したかどうか */
    data class EntryClosed(val added: Boolean) : AddItemEvent
}

sealed interface AddItemEffect {
    /** すぐにリストの末尾に追加する。公開範囲はそのリストの直前の公開範囲 */
    data class AddItem(val name: String, val quantity: Double?, val unit: String?, val memo: String? = null) : AddItemEffect

    /** 商品詳細入力画面を開く。数量・単位はその初期値 */
    data class OpenEntry(val name: String, val quantity: Double?, val unit: String?) : AddItemEffect

    /** 商品の画面（商品名の管理）を開く。まとめる・非表示などを、サジェストから直接できるように */
    data class OpenProduct(val productId: ProductId) : AddItemEffect
}

data class AddItemTransition(val state: AddItemState, val effect: AddItemEffect? = null)
