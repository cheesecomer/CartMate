package com.cheesecomer.cartmate.domain.list

import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.ShoppingList
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ListDisplayTest {

    private fun list(id: String, status: ListStatus) =
        ShoppingList(ListId(id), id, null, Visibility.SHARED, 1.0, Instant.EPOCH, UserId("taro"), status)

    @Test
    fun `削除していないリストは表示する`() {
        assertThat(ListDisplay.isShown(list("a", ListStatus.ACTIVE), openCount = 0)).isTrue()
    }

    @Test
    fun `削除したリストは表示しない`() {
        assertThat(ListDisplay.isShown(list("a", ListStatus.DELETED), openCount = 0)).isFalse()
    }

    @Test
    fun `削除したリストでも、未購入の項目が残っていれば表示する`() {
        assertThat(ListDisplay.isShown(list("a", ListStatus.DELETED), openCount = 1)).isTrue()
    }

    @Test
    fun `並び順を保ったまま、表示するリストだけを残す`() {
        val active = list("a", ListStatus.ACTIVE)
        val deletedWithItems = list("b", ListStatus.DELETED)
        val deleted = list("c", ListStatus.DELETED)
        val shown = ListDisplay.shown(listOf(active, deletedWithItems, deleted), mapOf(ListId("b") to 2))
        assertThat(shown).containsExactly(active, deletedWithItems).inOrder()
    }
}
