package com.cheesecomer.cartmate.domain.notice

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.UserId

/**
 * 他のメンバーによるチェックの通知（docs/design/ui.md「他のメンバーによるチェックの通知」）
 */
object OtherChecks {

    /**
     * [previous] から [current] の間に、自分以外（[me]）がチェックした項目。画面を開いた時点の一覧（[previous] が null）では何も返さない。
     * 未購入だった項目がチェック済みになったものだけを数える（チェック済みの項目が新しく一覧に入っただけのものは数えない）
     */
    fun newlyChecked(previous: List<Item>?, current: List<Item>, me: UserId): List<Item> {
        if (previous == null) return emptyList()
        val wasOpen: Set<ItemId> = previous.filter { it.status == ItemStatus.OPEN }.mapTo(HashSet()) { it.id }
        return current.filter {
            it.id in wasOpen && it.status == ItemStatus.COMPLETED && it.completedBy != null && it.completedBy != me
        }
    }

    /** 通知1件。「[checker]さんが[firstItemName]ほか[othersCount]件をチェックしました」 */
    data class Summary(val checker: UserId, val firstItemName: String, val othersCount: Int)

    /** まとめて通知する。チェックした人ごとに1件にし、チェックした順に並べる */
    fun summarize(checked: List<Item>): List<Summary> =
        checked.filter { it.completedBy != null }
            .groupBy { it.completedBy!! }
            .map { (checker, items) -> Summary(checker, items.first().inputName, items.size - 1) }
}
