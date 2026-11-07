package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StartDestinationTest {

    private val household = HouseholdId("h1")

    @Test
    fun `ログインしていなければログイン画面`() {
        assertThat(StartDestination.of(signedIn = false, householdId = household, isMember = true))
            .isEqualTo(Destination.Login)
    }

    @Test
    fun `家族に参加していなければ家族の作成・参加`() {
        assertThat(StartDestination.of(signedIn = true, householdId = null, isMember = null))
            .isEqualTo(Destination.Onboarding(removed = false))
    }

    @Test
    fun `メンバーかどうかを読めるまでは読み込み中`() {
        assertThat(StartDestination.of(signedIn = true, householdId = household, isMember = null))
            .isEqualTo(Destination.Loading)
    }

    @Test
    fun `家族のメンバーならリスト一覧`() {
        assertThat(StartDestination.of(signedIn = true, householdId = household, isMember = true))
            .isEqualTo(Destination.Lists(household))
    }

    /** 本人なら必ず読める users を断られたのは、家族がないからではない。作成・参加の画面に進めると、そこから進めない（フィードバック #020） */
    @Test
    fun `自分の users の読み取りを断られたら、家族の作成・参加ではなく接続できない画面`() {
        assertThat(StartDestination.of(signedIn = true, householdId = null, isMember = null, denied = true))
            .isEqualTo(Destination.Unavailable)
    }

    @Test
    fun `家族が残っているのにメンバーでなければ外された`() {
        assertThat(StartDestination.of(signedIn = true, householdId = household, isMember = false))
            .isEqualTo(Destination.Onboarding(removed = true))
    }
}
