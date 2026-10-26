package com.cheesecomer.cartmate.domain.family

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MergeOrderTest {

    @Test
    fun `合流先の末尾に元の並び順のまま加える`() {
        val source = mapOf("薬局" to 2.5, "スーパー" to 1.0, "イオン" to 7.0)
        val appended = MergeOrder.appended(source, targetMax = 4.0)
        assertThat(appended).containsExactly("スーパー", 5.0, "薬局", 6.0, "イオン", 7.0).inOrder()
    }

    @Test
    fun `合流先にリストがなければ先頭から並べる`() {
        val appended = MergeOrder.appended(mapOf("a" to 10.0, "b" to 20.0), targetMax = null)
        assertThat(appended).containsExactly("a", 1.0, "b", 2.0).inOrder()
    }

    @Test
    fun `写すリストがなければ空`() {
        assertThat(MergeOrder.appended(emptyMap<String, Double>(), targetMax = 3.0)).isEmpty()
    }
}
