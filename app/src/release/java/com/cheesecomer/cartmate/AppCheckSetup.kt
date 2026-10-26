package com.cheesecomer.cartmate

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** リリース版の App Check（docs/design/security.md「App Check」）。Play Integrity で、改造していないアプリからのアクセスだと示す */
object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
