package com.cheesecomer.cartmate.domain.family

import com.cheesecomer.cartmate.domain.order.SortKeys

/**
 * 合流で写すリストの並び順（docs/requirements.md §3.5「ほかの家族への合流」）。
 * 合流先のリストの末尾に、元の並び順のまま加える。同じ名前のリストがあってもまとめない
 */
object MergeOrder {

    /**
     * [sourceSortKeys] は写すリストの今の sortKey、[targetMax] は合流先のリストの sortKey の最大値（リストがなければ null）。
     * 写したあとの sortKey を、元と同じ順で返す
     */
    fun <K> appended(sourceSortKeys: Map<K, Double>, targetMax: Double?): Map<K, Double> {
        var last = targetMax
        return sourceSortKeys.entries
            .sortedBy { it.value }
            .associate { (key, _) ->
                val next = SortKeys.after(last)
                last = next
                key to next
            }
    }
}
