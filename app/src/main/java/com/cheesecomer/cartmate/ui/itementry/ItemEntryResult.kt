package com.cheesecomer.cartmate.ui.itementry

/**
 * 商品詳細入力画面を閉じたときの結果。リスト詳細の NavBackStackEntry の SavedStateHandle に [KEY] で渡す
 */
enum class ItemEntryResult {
    ADDED,

    /** 追加した。買い終わったリストへの、最後のチェックから間もない追加なので、買い忘れか聞く（[com.cheesecomer.cartmate.domain.model.AddedItem.asksRound]） */
    ADDED_ASKS_ROUND,
    UPDATED,
    CANCELLED,

    /** 編集中に、ほかの操作でチェックされていた */
    EDIT_REJECTED_COMPLETED,

    /** 編集中に、ほかの操作で削除されていた */
    EDIT_REJECTED_NOT_FOUND,
    ;

    companion object {
        const val KEY = "item_entry_result"
    }
}
