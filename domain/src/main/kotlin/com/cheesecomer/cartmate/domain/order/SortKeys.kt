package com.cheesecomer.cartmate.domain.order

/**
 * 並び順の値（sortKey）の計算。動かした1件だけを、前後の値の中間に書き換える（docs/design/data-model.md）
 */
object SortKeys {
    const val STEP = 1.0

    /** 末尾に追加するときの値 */
    fun after(max: Double?): Double = (max ?: 0.0) + STEP

    /** [previous] と [next] の間の値。間に取れる値がなければ null（振り直しが必要） */
    fun between(previous: Double?, next: Double?): Double? = when {
        previous == null && next == null -> STEP
        previous == null -> next!! - STEP
        next == null -> previous + STEP
        else -> {
            val middle = previous + (next - previous) / 2
            middle.takeIf { it > previous && it < next }
        }
    }

    /** [count] 件ぶんの等間隔の値 */
    fun rebalanced(count: Int): List<Double> = (1..count).map { it * STEP }

    sealed interface Move<out K> {
        /** 動かした1件だけを [key] にする */
        data class Single(val key: Double) : Move<Nothing>
        /** 全件を [keys] に振り直す */
        data class Rebalance<K>(val keys: Map<K, Double>) : Move<K>
    }

    /**
     * [ordered]（今の並び）の中で、[moved] を [previous] と [next] の間に動かすときの書き込み内容。
     * [previous] / [next] が null なら、それぞれ先頭・末尾に動かす
     */
    fun <K> move(ordered: List<K>, keyOf: (K) -> Double, moved: K, previous: K?, next: K?): Move<K> {
        between(previous?.let(keyOf), next?.let(keyOf))?.let { return Move.Single(it) }
        val others = ordered - moved
        val insertAt = when {
            previous != null -> others.indexOf(previous) + 1
            next != null -> others.indexOf(next)
            else -> others.size
        }
        val reordered = others.toMutableList().apply { add(insertAt, moved) }
        return Move.Rebalance(reordered.zip(rebalanced(reordered.size)).toMap())
    }
}
