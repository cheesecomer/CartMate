package com.cheesecomer.cartmate

/** リリース版はエミュレータにつながない（debug の FirebaseEmulator を参照） */
object FirebaseEmulator {
    val enabled: Boolean get() = false

    fun install() = Unit

    suspend fun signInTestUser() = Unit
}
