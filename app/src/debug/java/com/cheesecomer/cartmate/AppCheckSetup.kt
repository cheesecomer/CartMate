package com.cheesecomer.cartmate

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * デバッグ版の App Check（docs/design/security.md「App Check」）。起動時に Logcat（DebugAppCheckProvider）に出る
 * デバッグ用のトークンを、Firebase コンソールの App Check に登録する
 */
object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
