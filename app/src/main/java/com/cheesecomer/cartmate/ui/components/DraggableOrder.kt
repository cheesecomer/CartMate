package com.cheesecomer.cartmate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * ドラッグでの並び替え中の、画面上の並び。ドラッグ中は画面の並びだけを入れ替え、指を離したときに
 * 動かした1件の前後を返して、呼び出し側が Repository に確定させる（docs/design/ui.md「並び替え」）
 */
class DraggableOrder<T>(private val keyOf: (T) -> Any) {
    // LazyColumn の項目の一覧はこの値から作るので、変わったら描き直されるよう Compose の状態にする
    internal var source by mutableStateOf<List<T>>(emptyList())

    /** ドラッグ中、または確定した結果が届くまでの画面上の並び。null なら [source] をそのまま表示する */
    private var local by mutableStateOf<List<T>?>(null)
    private var dragging by mutableStateOf(false)

    val items: List<T> get() = local ?: source

    fun startDrag() {
        local = items
        dragging = true
    }

    fun move(fromKey: Any?, toKey: Any?) {
        val current = local ?: return
        val from = current.indexOfFirst { keyOf(it) == fromKey }
        val to = current.indexOfFirst { keyOf(it) == toKey }
        if (from < 0 || to < 0) return
        local = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** ドラッグを終える。並びが変わっていれば、動かした項目の前後（null は先頭・末尾）を返す */
    fun finishDrag(key: Any): Neighbors<T>? {
        dragging = false
        val current = local ?: return null
        if (current.map(keyOf) == source.map(keyOf)) {
            local = null
            return null
        }
        // 確定した並びが届くまで、画面上の並びを保つ（元の並びに一瞬戻らないように）
        return neighborsAt(current, current.indexOfFirst { keyOf(it) == key })
    }

    /** 1つ上へ動かすときの前後。先頭なら null */
    fun moveUpTarget(key: Any): Neighbors<T>? {
        val index = items.indexOfFirst { keyOf(it) == key }
        if (index <= 0) return null
        return Neighbors(items.getOrNull(index - 2), items[index - 1])
    }

    /** 1つ下へ動かすときの前後。末尾なら null */
    fun moveDownTarget(key: Any): Neighbors<T>? {
        val index = items.indexOfFirst { keyOf(it) == key }
        if (index < 0 || index >= items.lastIndex) return null
        return Neighbors(items[index + 1], items.getOrNull(index + 2))
    }

    internal fun onSourceChanged() {
        if (!dragging) local = null
    }

    private fun neighborsAt(list: List<T>, index: Int) = Neighbors(list.getOrNull(index - 1), list.getOrNull(index + 1))

    data class Neighbors<T>(val previous: T?, val next: T?)
}

@Composable
fun <T> rememberDraggableOrder(source: List<T>, keyOf: (T) -> Any): DraggableOrder<T> {
    val order = remember { DraggableOrder(keyOf) }
    order.source = source
    LaunchedEffect(source) { order.onSourceChanged() }
    return order
}
