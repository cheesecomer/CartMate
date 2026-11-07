package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.HouseholdId

/** 起動時と状態が変わったときに表示する画面（docs/design/ui.md「最初に表示する画面」） */
object StartDestination {

    /**
     * [signedIn] はログインしているか、[householdId] は users/{uid}.householdId、
     * [isMember] はその家族に自分の members があるか（まだ読めていなければ null）、
     * [denied] は本人なら必ず読める自分の users の読み取りを、サーバーに断られたか（App Check など。フィードバック #020）
     */
    fun of(signedIn: Boolean, householdId: HouseholdId?, isMember: Boolean?, denied: Boolean = false): Destination = when {
        !signedIn -> Destination.Login
        denied -> Destination.Unavailable
        householdId == null -> Destination.Onboarding(removed = false)
        isMember == null -> Destination.Loading
        // オーナーはほかの人の users を書き換えられないので、外された人は householdId が残ったまま members だけが消える
        !isMember -> Destination.Onboarding(removed = true)
        else -> Destination.Lists(householdId)
    }
}

sealed interface Destination {
    data object Loading : Destination
    data object Login : Destination
    /** 家族の作成・参加。[removed] なら「家族から外されました」を表示する */
    data class Onboarding(val removed: Boolean) : Destination
    data class Lists(val householdId: HouseholdId) : Destination
    /** サーバーに読み取りを断られて、所属が分からない。読めるようになれば切り替わる */
    data object Unavailable : Destination
}
