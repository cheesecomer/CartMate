package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.UserId

/**
 * 招待を開いたときに、参加・合流・参加しないのどれにするか（docs/requirements.md §3.5「参加の確認」「ほかの家族への合流」）
 */
object JoinPolicy {

    /**
     * [current] は今の家族（参加していなければ null）、[currentMembers] はその家族のメンバー、
     * [target] は招待先の家族
     */
    fun decide(me: UserId, current: HouseholdId?, currentMembers: List<Member>, target: HouseholdId): JoinMode = when {
        current == null -> JoinMode.Join
        current == target -> JoinMode.AlreadyInThisFamily
        // 自分しかいない家族からは、リストを持って合流できる
        currentMembers.all { it.userId == me } -> JoinMode.Merge(from = current)
        else -> JoinMode.AlreadyInAnotherFamily
    }
}

sealed interface JoinMode {
    /** 家族に参加していないので、そのまま参加する */
    data object Join : JoinMode
    /** 自分しかいない家族 [from] から、リストなどを移して合流する */
    data class Merge(val from: HouseholdId) : JoinMode
    /** 招待先がすでに参加している家族 */
    data object AlreadyInThisFamily : JoinMode
    /** ほかのメンバーがいる家族に参加している。「すでに家族に参加しています」 */
    data object AlreadyInAnotherFamily : JoinMode
}
