package com.cheesecomer.cartmate.domain.suggest

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ListSimilarityTest {

    private fun set(vararg names: String) = names.toSet()

    @Test
    fun `どちらかのリストの商品が5件に満たなければ判定しない`() {
        assertThat(ListSimilarity.overlap(set("a", "b", "c", "d"), set("a", "b", "c", "d", "e"))).isNull()
    }

    @Test
    fun `共通の商品が3件に満たなければ判定しない`() {
        assertThat(ListSimilarity.overlap(set("a", "b", "c", "d", "e"), set("a", "b", "x", "y", "z"))).isNull()
    }

    @Test
    fun `重複係数は共通の件数を小さいほうの件数で割る`() {
        val small = set("a", "b", "c", "d", "e")
        val large = set("a", "b", "c", "x", "y", "z", "w", "v", "u", "t")
        assertThat(ListSimilarity.overlap(small, large)).isEqualTo(0.6)
    }

    @Test
    fun `重複係数が0点5以上のリストの商品だけを関連付ける`() {
        val profiles = mapOf(
            "スーパー" to set("a", "b", "c", "d", "e", "f", "g", "h"),
            // 共通3件 / 6件 = 0.5
            "西友" to set("a", "b", "c", "x", "y", "z"),
            // 共通3件 / 7件 < 0.5
            "ドラッグストア" to set("a", "b", "c", "p", "q", "r", "s"),
        )
        val relevance = ListSimilarity.relevance("スーパー", profiles)
        assertThat(relevance["a"]).isEqualTo(1.0)
        assertThat(relevance["x"]).isEqualTo(0.5)
        assertThat(relevance).doesNotContainKey("p")
    }

    @Test
    fun `新しいリストで同じ名前の履歴がなければ空`() {
        val profiles = mapOf("スーパー" to set("a", "b", "c", "d", "e"))
        assertThat(ListSimilarity.relevance("ホームセンター", profiles)).isEmpty()
    }

    @Test
    fun `リスト名は全角半角や大文字小文字の違いを同じとみなす`() {
        assertThat(ListSimilarity.listKey("ＳＥＩＹＵ ")).isEqualTo(ListSimilarity.listKey("seiyu"))
    }
}
