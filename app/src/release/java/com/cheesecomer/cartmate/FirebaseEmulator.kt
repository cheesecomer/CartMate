package com.cheesecomer.cartmate

import java.time.Clock

/** リリース版はエミュレータにつながない（debug の FirebaseEmulator を参照） */
object FirebaseEmulator {
    val enabled: Boolean get() = false

    val testUserSignInVisible: Boolean get() = false

    val clock: Clock = Clock.systemDefaultZone()

    fun install() = Unit

    suspend fun signInTestUser() = Unit
}
