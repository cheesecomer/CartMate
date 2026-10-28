package com.cheesecomer.cartmate.testing

import androidx.compose.ui.platform.UriHandler

/** ブラウザを開く代わりに、開こうとした URL を覚える。`LocalUriHandler` に差し替えて使う */
class RecordingUriHandler : UriHandler {
    val opened = mutableListOf<String>()

    override fun openUri(uri: String) {
        opened += uri
    }
}
