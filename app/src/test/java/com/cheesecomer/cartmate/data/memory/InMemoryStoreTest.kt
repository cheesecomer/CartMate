package com.cheesecomer.cartmate.data.memory

import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.ListStatus
import com.cheesecomer.cartmate.domain.model.NewItem
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.repository.CompleteResult
import com.cheesecomer.cartmate.domain.repository.DeleteItemResult
import com.cheesecomer.cartmate.domain.repository.DeleteListResult
import com.cheesecomer.cartmate.domain.repository.ItemUpdate
import com.cheesecomer.cartmate.domain.repository.RenameListResult
import com.cheesecomer.cartmate.domain.repository.ReopenResult
import com.cheesecomer.cartmate.domain.repository.UpdateItemResult
import com.cheesecomer.cartmate.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InMemoryStoreTest {

    private val clock = MutableClock()
    private var user = UserId("taro")
    private val store = InMemoryStore(clock) { user }

    private suspend fun createListWithItem(): Pair<ListId, ItemId> {
        val listId = store.createList("スーパー")
        val itemId = store.addItem(NewItem(listId, "牛乳", 2.0, "本"))
        return listId to itemId
    }

    @Test
    fun `チェックすると項目がチェック済みになり、購入が1件記録される`() = runTest {
        val (listId, itemId) = createListWithItem()
        val checkedAt = clock.now.minusSeconds(30)
        clock.advance(Duration.ofMinutes(1))

        assertThat(store.completeItem(itemId, checkedAt)).isEqualTo(CompleteResult.Completed)

        assertThat(store.observeOpenItems().first()).isEmpty()
        val completed = store.observeListItems(listId, Instant.EPOCH).first().single()
        assertThat(completed.status).isEqualTo(ItemStatus.COMPLETED)
        assertThat(completed.completedBy).isEqualTo(user)
        assertThat(completed.completedAt).isEqualTo(clock.now)

        val purchase = store.observePurchases(Instant.EPOCH).first().single()
        assertThat(purchase.itemId).isEqualTo(itemId)
        assertThat(purchase.listNameSnapshot).isEqualTo("スーパー")
        assertThat(purchase.inputName).isEqualTo("牛乳")
        assertThat(purchase.quantity).isEqualTo(2.0)
        assertThat(purchase.unit).isEqualTo("本")
        assertThat(purchase.checkedAt).isEqualTo(checkedAt)
        assertThat(purchase.purchasedAt).isEqualTo(clock.now)
        assertThat(purchase.active).isTrue()
    }

    @Test
    fun `チェック済みの項目をもう一度チェックすると、先にチェックした人と時刻を返し、購入は増えない`() = runTest {
        val (_, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)
        val firstAt = clock.now

        user = UserId("hanako")
        clock.advance(Duration.ofMinutes(5))
        val result = store.completeItem(itemId, clock.now)

        assertThat(result).isEqualTo(CompleteResult.AlreadyCompleted(UserId("taro"), firstAt))
        assertThat(store.observePurchases(Instant.EPOCH).first()).hasSize(1)
    }

    @Test
    fun `Undo すると未購入に戻り、購入が無効になる`() = runTest {
        val (_, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)
        clock.advance(Duration.ofSeconds(3))

        assertThat(store.reopenItem(itemId)).isEqualTo(ReopenResult.Reopened)

        val item = store.observeOpenItems().first().single()
        assertThat(item.completedAt).isNull()
        assertThat(item.completedBy).isNull()
        val purchase = store.observePurchases(Instant.EPOCH).first().single()
        assertThat(purchase.active).isFalse()
        assertThat(purchase.revokedAt).isEqualTo(clock.now)
        assertThat(purchase.revokedBy).isEqualTo(user)
    }

    @Test
    fun `未購入の項目を Undo すると AlreadyOpen`() = runTest {
        val (_, itemId) = createListWithItem()
        assertThat(store.reopenItem(itemId)).isEqualTo(ReopenResult.AlreadyOpen)
    }

    @Test
    fun `Undo のあとに再チェックすると、同じ購入が有効に戻る`() = runTest {
        val (_, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)
        store.reopenItem(itemId)
        clock.advance(Duration.ofMinutes(10))

        assertThat(store.completeItem(itemId, clock.now)).isEqualTo(CompleteResult.Completed)

        val purchase = store.observePurchases(Instant.EPOCH).first().single()
        assertThat(purchase.active).isTrue()
        assertThat(purchase.purchasedAt).isEqualTo(clock.now)
        assertThat(purchase.revokedAt).isNull()
    }

    @Test
    fun `未購入の項目は削除でき、同じ ID で復元できる`() = runTest {
        val (_, itemId) = createListWithItem()
        val item = store.observeOpenItems().first().single()

        assertThat(store.deleteItem(itemId)).isEqualTo(DeleteItemResult.Deleted)
        assertThat(store.observeOpenItems().first()).isEmpty()

        clock.advance(Duration.ofSeconds(2))
        store.restoreItem(item)
        val restored = store.observeOpenItems().first().single()
        assertThat(restored.id).isEqualTo(itemId)
        assertThat(restored.inputName).isEqualTo("牛乳")
        // 並び順は末尾になる
        assertThat(restored.createdAt).isEqualTo(clock.now)
    }

    @Test
    fun `チェック済みの項目は削除できない`() = runTest {
        val (_, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)
        assertThat(store.deleteItem(itemId)).isEqualTo(DeleteItemResult.AlreadyCompleted)
    }

    @Test
    fun `未購入の項目があるリストは削除できない`() = runTest {
        val (listId, _) = createListWithItem()
        assertThat(store.deleteList(listId)).isEqualTo(DeleteListResult.NotEmpty)
    }

    @Test
    fun `リストを削除しても購入の記録は残る`() = runTest {
        val (listId, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)

        assertThat(store.deleteList(listId)).isEqualTo(DeleteListResult.Deleted)

        assertThat(store.observeLists().first().single().status).isEqualTo(ListStatus.DELETED)
        assertThat(store.observePurchases(Instant.EPOCH).first().single().listNameSnapshot).isEqualTo("スーパー")
    }

    @Test
    fun `リストの項目は、未購入と指定時刻以降のチェック済みを追加した順に返す`() = runTest {
        val listId = store.createList("スーパー")
        val otherListId = store.createList("薬局")
        val old = store.addItem(NewItem(listId, "古いチェック済み", null, null))
        clock.advance(Duration.ofSeconds(1))
        store.addItem(NewItem(listId, "牛乳", null, null))
        clock.advance(Duration.ofSeconds(1))
        val checked = store.addItem(NewItem(listId, "卵", null, null))
        clock.advance(Duration.ofSeconds(1))
        store.addItem(NewItem(listId, "パン", null, null))
        store.addItem(NewItem(otherListId, "目薬", null, null))
        store.completeItem(old, clock.now)
        clock.advance(Duration.ofHours(25))
        val since = clock.now.minus(Duration.ofHours(24))
        store.completeItem(checked, clock.now)

        val names = store.observeListItems(listId, since).first().map { it.inputName }
        assertThat(names).containsExactly("牛乳", "卵", "パン").inOrder()
    }

    @Test
    fun `未購入の項目は商品名・数量・単位を変更できる`() = runTest {
        val (_, itemId) = createListWithItem()

        assertThat(store.updateItem(itemId, ItemUpdate("低脂肪乳", null, null))).isEqualTo(UpdateItemResult.Updated)

        val item = store.observeOpenItems().first().single()
        assertThat(item.inputName).isEqualTo("低脂肪乳")
        assertThat(item.quantity).isNull()
        assertThat(item.unit).isNull()
    }

    @Test
    fun `チェック済みや存在しない項目は変更できない`() = runTest {
        val (_, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)

        assertThat(store.updateItem(itemId, ItemUpdate("低脂肪乳", 1.0, null)))
            .isEqualTo(UpdateItemResult.AlreadyCompleted)
        assertThat(store.updateItem(ItemId("none"), ItemUpdate("低脂肪乳", 1.0, null)))
            .isEqualTo(UpdateItemResult.NotFound)
    }

    @Test
    fun `リスト名を変更しても、購入の記録に残したリスト名は変わらない`() = runTest {
        val (listId, itemId) = createListWithItem()
        store.completeItem(itemId, clock.now)

        assertThat(store.renameList(listId, "イオン")).isEqualTo(RenameListResult.Renamed)

        assertThat(store.observeLists().first().single().name).isEqualTo("イオン")
        assertThat(store.observePurchases(Instant.EPOCH).first().single().listNameSnapshot).isEqualTo("スーパー")
    }

    @Test
    fun `削除したリストや存在しないリストの名前は変更できない`() = runTest {
        val listId = store.createList("薬局")
        store.deleteList(listId)

        assertThat(store.renameList(listId, "ドラッグストア")).isEqualTo(RenameListResult.NotFound)
        assertThat(store.renameList(ListId("none"), "ドラッグストア")).isEqualTo(RenameListResult.NotFound)
    }
}
