package com.cheesecomer.cartmate.testing

import app.cash.turbine.ReceiveTurbine

/** [predicate] を満たす値が届くまで読み進め、その値を返す */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
