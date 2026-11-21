package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemStatus
import java.time.Duration
import java.time.Instant

/**
 * 1回の買い物（docs/requirements.md §3.2「前回の買い物の片付け」）。買い物を終えたリストに項目を追加したら、
 * 次の買い物が始まったとみなし、前回のチェック済みの項目を片付ける（リスト詳細と進捗に出さない。購入の記録は残す）。
 * 最後のチェックから間もなければ、買い忘れか次の買い物かを聞いてから片付ける（未購入が残っていれば買い物の途中とみなす）
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
     * 最後のチェックからこの長さの間に、買い終わったリストへ追加したら、買い忘れか次の買い物かを聞く（フィードバック #013）。
     * 買い物から帰ってすぐ買い忘れを足したときに、今買った物が見えなくならないようにする
     */
    val FORGOTTEN_WINDOW: Duration = Duration.ofHours(2)

    /** 項目を追加するときの、前回のチェック済みの扱い */
    enum class OnAdd {
        /** 片付けない（買い物の途中か、片付けるチェック済みがない） */
        KEEP,

        /** 買い忘れか次の買い物かを聞く。聞くまでは片付けない */
        ASK,

        /** 聞かずに片付ける */
        CLEAR,
    }

    /**
     * [now] に項目を追加するときに、前回のチェック済みを片付けるか。[items] はそのリストの、自分に見えて表示中の項目。
     * 表示中のチェック済みがあり、最後のチェックから [FORGOTTEN_WINDOW] を過ぎていれば、聞かずに片付ける。
     * 店で見つからず未購入のまま残した商品があっても片付ける（未購入の項目は残り、次の買い物に持ち越す）。
     * [FORGOTTEN_WINDOW] 以内なら、未購入が0件のときだけ聞き、未購入が残っていれば買い物の途中とみなして片付けない
     */
    fun onAdd(items: List<Item>, now: Instant): OnAdd {
        val lastCompleted = items.filter { it.status == ItemStatus.COMPLETED }.mapNotNull { it.completedAt }.maxOrNull()
            ?: return OnAdd.KEEP
        return when {
            lastCompleted.isBefore(now - FORGOTTEN_WINDOW) -> OnAdd.CLEAR
            items.any { it.status == ItemStatus.OPEN } -> OnAdd.KEEP
            else -> OnAdd.ASK
        }
    }
}
