package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.ShoppingList

/**
 * 削除したリストの表示ルール（docs/design/sync.md「リスト削除の競合」）。削除したリストでも、自分に見える未購入の項目が
 * 残っている間は表示する。削除と同時にほかの端末で項目が追加された場合や、削除したあとに Undo した場合に、項目が見えなくならないように
 */
object ListDisplay {

    fun isShown(list: ShoppingList, openCount: Int): Boolean = list.status == ListStatus.ACTIVE || openCount > 0

    /** [lists] のうち表示するもの。[openCounts] はリストごとの、自分に見える未購入の件数 */
    fun shown(lists: List<ShoppingList>, openCounts: Map<ListId, Int>): List<ShoppingList> =
        lists.filter { isShown(it, openCounts[it.id] ?: 0) }
}
