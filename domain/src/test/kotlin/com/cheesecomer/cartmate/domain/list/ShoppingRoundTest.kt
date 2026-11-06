package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ShoppingRoundTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val windowStart = now - Duration.ofHours(24)

    private fun item(id: String, status: ItemStatus, completedAt: Instant? = null) = Item(
        id = ItemId(id),
        listId = ListId("super"),
        inputName = id,
        sortKey = 1.0,
        quantity = 1.0,
        unit = null,
        memo = null,
        visibility = Visibility.SHARED,
        status = status,
        createdAt = now - Duration.ofDays(1),
        createdBy = UserId("taro"),
        completedAt = completedAt,
        completedBy = completedAt?.let { UserId("taro") },
    )

    @Test
    fun `片付けていなければ直近24時間のチェック済みを表示する`() {
        assertThat(ShoppingRound.isShown(windowStart, windowStart, clearedAt = null)).isTrue()
        assertThat(ShoppingRound.isShown(windowStart.minusSeconds(1), windowStart, clearedAt = null)).isFalse()
    }

    @Test
    fun `片付けたあとは、片付けた時刻より後のチェック済みだけを表示する`() {
        val cleared = now - Duration.ofHours(2)
        assertThat(ShoppingRound.isShown(cleared.plusSeconds(1), windowStart, cleared)).isTrue()
        assertThat(ShoppingRound.isShown(cleared.minusSeconds(1), windowStart, cleared)).isFalse()
    }

    @Test
    fun `片付けた時刻と同じ時刻のチェックは片付けたものとする`() {
        val cleared = now - Duration.ofHours(2)
        assertThat(ShoppingRound.isShown(cleared, windowStart, cleared)).isFalse()
    }

    @Test
    fun `表示するのは未購入の項目と、片付けより後で24時間以内にチェックした項目`() {
        val cleared = now - Duration.ofHours(2)
        val open = item("牛乳", ItemStatus.OPEN)
        val recent = item("卵", ItemStatus.COMPLETED, now - Duration.ofHours(1))
        val beforeClear = item("豆腐", ItemStatus.COMPLETED, now - Duration.ofHours(3))
        assertThat(ShoppingRound.visible(listOf(open, recent, beforeClear), windowStart, cleared)).containsExactly(open, recent)
    }

    @Test
    fun `最後のチェックから2時間を過ぎて買い終わったリストに追加すると、聞かずに片付ける`() {
        val items = listOf(
            item("牛乳", ItemStatus.COMPLETED, now - Duration.ofHours(5)),
            item("卵", ItemStatus.COMPLETED, now - Duration.ofHours(2).plusSeconds(1)),
        )
        assertThat(ShoppingRound.onAdd(items, now)).isEqualTo(ShoppingRound.OnAdd.CLEAR)
    }

    @Test
    fun `最後のチェックから2時間以内に買い終わったリストに追加すると、買い忘れか聞く`() {
        val items = listOf(
            item("牛乳", ItemStatus.COMPLETED, now - Duration.ofHours(5)),
            item("卵", ItemStatus.COMPLETED, now - Duration.ofHours(2)),
        )
        assertThat(ShoppingRound.onAdd(items, now)).isEqualTo(ShoppingRound.OnAdd.ASK)
    }

    @Test
    fun `買い物の途中（未購入が残っている）の追加では片付けない`() {
        val items = listOf(item("牛乳", ItemStatus.COMPLETED, now - Duration.ofHours(5)), item("卵", ItemStatus.OPEN))
        assertThat(ShoppingRound.onAdd(items, now)).isEqualTo(ShoppingRound.OnAdd.KEEP)
    }

    @Test
    fun `表示中のチェック済みがなければ片付けない`() {
        assertThat(ShoppingRound.onAdd(emptyList(), now)).isEqualTo(ShoppingRound.OnAdd.KEEP)
    }
}
