package com.cheesecomer.cartmate.domain.visibility

import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VisibilityDefaultsTest {

    @Test
    fun `共有のリストで初回は共有`() {
        assertThat(VisibilityDefaults.initial(Visibility.SHARED, remembered = null)).isEqualTo(Visibility.SHARED)
    }

    @Test
    fun `共有のリストでは直前に選んだ公開範囲を引き継ぐ`() {
        assertThat(VisibilityDefaults.initial(Visibility.SHARED, Visibility.PRIVATE)).isEqualTo(Visibility.PRIVATE)
        assertThat(VisibilityDefaults.initial(Visibility.SHARED, Visibility.SHARED)).isEqualTo(Visibility.SHARED)
    }

    @Test
    fun `プライベートのリストでは常にプライベート`() {
        assertThat(VisibilityDefaults.initial(Visibility.PRIVATE, null)).isEqualTo(Visibility.PRIVATE)
        assertThat(VisibilityDefaults.initial(Visibility.PRIVATE, Visibility.SHARED)).isEqualTo(Visibility.PRIVATE)
    }

    @Test
    fun `公開範囲を選べるのは共有のリストだけ`() {
        assertThat(VisibilityDefaults.selectable(Visibility.SHARED)).isTrue()
        assertThat(VisibilityDefaults.selectable(Visibility.PRIVATE)).isFalse()
    }
}
