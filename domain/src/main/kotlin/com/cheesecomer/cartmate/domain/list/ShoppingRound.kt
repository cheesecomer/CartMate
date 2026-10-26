package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemStatus
import java.time.Duration
import java.time.Instant

/**
 * 1回の買い物（docs/requirements.md §3.2「前回の買い物の片付け」）。買い終わったリスト（未購入が0件）に項目を追加したら、
 * 次の買い物が始まったとみなし、前回のチェック済みの項目を片付ける（リスト詳細と進捗に出さない。購入の記録は残す）
 */
object ShoppingRound {

    /** チェックした項目を、元の位置に打ち消し線で残す長さ */
    val COMPLETED_WINDOW: Duration = Duration.ofHours(24)

    /**
     * [completedAt] にチェックした項目を、リスト詳細と進捗に出すか。直近24時間（[windowStart] 以降）で、
     * 前回の片付け（[clearedAt]）より後にチェックしたものだけを出す。片付けた時刻と同じ時刻のチェックは片付けたものとする
     */
    fun isShown(completedAt: Instant, windowStart: Instant, clearedAt: Instant?): Boolean =
        !completedAt.isBefore(windowStart) && (clearedAt == null || completedAt.isAfter(clearedAt))

    /** リスト詳細に表示する項目。未購入の項目と、[isShown] のチェック済みの項目 */
    fun visible(items: List<Item>, windowStart: Instant, clearedAt: Instant?): List<Item> =
        items.filter { it.status == ItemStatus.OPEN || (it.completedAt != null && isShown(it.completedAt, windowStart, clearedAt)) }

    /**
     * 項目を追加するときに、前回のチェック済みを片付けるか。[items] はそのリストの、自分に見えて表示中の項目。
     * 未購入が0件で、表示中のチェック済みがあるときだけ片付ける（買い物の途中の追加では片付けない）
     */
    fun clearsOnAdd(items: List<Item>): Boolean =
        items.none { it.status == ItemStatus.OPEN } && items.any { it.status == ItemStatus.COMPLETED }
}
