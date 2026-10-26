package com.cheesecomer.cartmate.data.firestore

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DocumentPresenceTest {

    @Test
    fun `ログイン直後にキャッシュにないだけなら、まだ分からないとする`() {
        // ログインした直後は、自分の users がまだ端末のキャッシュになく、サーバーの結果より先に「ない」が届く。
        // これを「家族に参加していない」と判定すると、家族の作成・参加の画面が一瞬見える
        assertThat(documentPresence(exists = false, fromCache = true)).isNull()
    }

    @Test
    fun `サーバーがないと返したら、ない`() {
        assertThat(documentPresence(exists = false, fromCache = false)).isFalse()
    }

    @Test
    fun `キャッシュにあれば、ある（オフラインで起動した場合）`() {
        assertThat(documentPresence(exists = true, fromCache = true)).isTrue()
    }

    @Test
    fun `サーバーにあれば、ある`() {
        assertThat(documentPresence(exists = true, fromCache = false)).isTrue()
    }
}
