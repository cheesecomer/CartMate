package com.cheesecomer.cartmate.domain.order

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SortKeysTest {

    @Test
    fun `末尾に追加するときは、最大値より大きい値にする。空なら最初の値`() {
        assertThat(SortKeys.after(null)).isEqualTo(SortKeys.STEP)
        assertThat(SortKeys.after(3.0)).isGreaterThan(3.0)
    }

    @Test
    fun `前後の間に置くときは、中間の値にする`() {
        assertThat(SortKeys.between(1.0, 2.0)).isEqualTo(1.5)
    }

    @Test
    fun `先頭に置くときは、次の値より小さく、末尾に置くときは前の値より大きくする`() {
        assertThat(SortKeys.between(null, 1.0)!!).isLessThan(1.0)
        assertThat(SortKeys.between(5.0, null)!!).isGreaterThan(5.0)
        assertThat(SortKeys.between(null, null)).isEqualTo(SortKeys.STEP)
    }

    @Test
    fun `中間の値が取れないほど近い場合は null を返し、振り直しが必要だと知らせる`() {
        val a = 1.0
        val b = Math.nextUp(a)
        assertThat(SortKeys.between(a, b)).isNull()
    }

    @Test
    fun `振り直しは、件数ぶんの等間隔の値を昇順で返す`() {
        assertThat(SortKeys.rebalanced(3)).containsExactly(1.0, 2.0, 3.0).inOrder()
    }

    @Test
    fun `並べた順番の一覧から、動かした項目の新しい値を求める`() {
        // a, b, c の並びで c を a と b の間へ
        val keys = mapOf("a" to 1.0, "b" to 2.0, "c" to 3.0)
        val move = SortKeys.move(listOf("a", "b", "c"), keys::getValue, moved = "c", previous = "a", next = "b")
        assertThat(move).isEqualTo(SortKeys.Move.Single(1.5))
    }

    @Test
    fun `中間の値が取れない場合は、動かしたあとの並びで全体を振り直す`() {
        val a = 1.0
        val keys = mapOf("a" to a, "b" to Math.nextUp(a), "c" to 3.0)
        val move = SortKeys.move(listOf("a", "b", "c"), keys::getValue, moved = "c", previous = "a", next = "b")
        assertThat(move).isEqualTo(SortKeys.Move.Rebalance(mapOf("a" to 1.0, "c" to 2.0, "b" to 3.0)))
    }
}
