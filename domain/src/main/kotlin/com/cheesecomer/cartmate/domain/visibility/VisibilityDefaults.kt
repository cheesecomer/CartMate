package com.cheesecomer.cartmate.domain.visibility

import com.cheesecomer.cartmate.domain.model.Visibility

/**
 * 項目を追加するときの公開範囲（docs/requirements.md §3.4）。
 * 共有のリストでは、商品詳細入力画面で直前に選んだ公開範囲を、そのリストの次の追加の初期値にする
 */
object VisibilityDefaults {
    /** 追加するときの公開範囲。[remembered] はそのリストで直前に選んだ公開範囲（まだ選んでいなければ null） */
    fun initial(listVisibility: Visibility, remembered: Visibility?): Visibility = when (listVisibility) {
        // プライベートのリストの項目は、すべてプライベート
        Visibility.PRIVATE -> Visibility.PRIVATE
        Visibility.SHARED -> remembered ?: Visibility.SHARED
    }

    /** 商品詳細入力画面で公開範囲を選べるか */
    fun selectable(listVisibility: Visibility): Boolean = listVisibility == Visibility.SHARED
}
