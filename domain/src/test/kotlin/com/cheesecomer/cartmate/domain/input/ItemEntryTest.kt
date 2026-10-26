package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class ItemEntryTest {

    private fun newEntry(
        quantity: Double? = null,
        unit: String? = "個",
        listVisibility: Visibility = Visibility.SHARED,
        remembered: Visibility? = null,
    ) = ItemEntry.forNew("牛乳", quantity, unit, listVisibility, remembered)

    @Nested
    inner class 初期値 {
        @Test
        fun `新規は、渡した数量・単位を初期値にする`() {
            val entry = newEntry(quantity = 2.0, unit = "パック")
            assertThat(entry.quantity).isEqualTo("2")
            assertThat(entry.unit).isEqualTo("パック")
            assertThat(entry.memo).isEmpty()
            assertThat(entry.editing).isNull()
        }

        @Test
        fun `数量がなければ1にする`() {
            assertThat(newEntry(quantity = null).quantity).isEqualTo("1")
        }

        @Test
        fun `共有のリストでは公開範囲を選べ、初期値は直前の公開範囲`() {
            val entry = newEntry(remembered = Visibility.PRIVATE)
            assertThat(entry.visibilitySelectable).isTrue()
            assertThat(entry.visibility).isEqualTo(Visibility.PRIVATE)
        }

        @Test
        fun `プライベートのリストでは公開範囲を選べず、プライベートになる`() {
            val entry = newEntry(listVisibility = Visibility.PRIVATE)
            assertThat(entry.visibilitySelectable).isFalse()
            assertThat(entry.visibility).isEqualTo(Visibility.PRIVATE)
            assertThat(entry.withVisibility(Visibility.SHARED).visibility).isEqualTo(Visibility.PRIVATE)
        }

        @Test
        fun `編集は項目の内容を初期値にし、公開範囲は変えられない`() {
            val item = Item(
                id = ItemId("i"), listId = ListId("l"), inputName = "卵", sortKey = 1.0,
                quantity = 1.5, unit = null, memo = "Lサイズ", visibility = Visibility.SHARED,
                status = ItemStatus.OPEN, createdAt = Instant.EPOCH, createdBy = UserId("u"),
                completedAt = null, completedBy = null,
            )
            val entry = ItemEntry.forEdit(item)
            assertThat(entry.editing).isEqualTo(ItemId("i"))
            assertThat(listOf(entry.name, entry.quantity, entry.unit, entry.memo)).containsExactly("卵", "1.5", "", "Lサイズ").inOrder()
            assertThat(entry.visibilitySelectable).isFalse()
            assertThat(entry.withVisibility(Visibility.PRIVATE).visibility).isEqualTo(Visibility.SHARED)
        }
    }

    @Nested
    inner class 数量 {
        @Test
        fun `「＋」で1増え、「−」で1減る`() {
            val entry = newEntry(quantity = 2.0)
            assertThat(entry.increment().quantity).isEqualTo("3")
            assertThat(entry.decrement().quantity).isEqualTo("1")
        }

        @Test
        fun `「−」で1未満にはしない`() {
            assertThat(newEntry(quantity = 1.0).decrement().quantity).isEqualTo("1")
            assertThat(newEntry(quantity = 1.5).decrement().quantity).isEqualTo("1")
        }

        @Test
        fun `上限を超えて増やさない`() {
            assertThat(newEntry(quantity = 9999.0).increment().quantity).isEqualTo("9999")
        }

        @Test
        fun `数値でない数量から「＋」を押すと1になる`() {
            assertThat(newEntry().copy(quantity = "abc").increment().quantity).isEqualTo("1")
        }
    }

    @Nested
    inner class 確定 {
        @ParameterizedTest
        @ValueSource(strings = ["", "0", "-1", "abc", "10000"])
        fun `数量が空・0以下・数値でない・上限超えなら確定できない`(quantity: String) {
            val entry = newEntry().copy(quantity = quantity)
            assertThat(entry.canConfirm).isFalse()
            assertThat(entry.confirm()).isNull()
        }

        @Test
        fun `商品名が空白だけなら確定できない`() {
            assertThat(newEntry().copy(name = "  ").canConfirm).isFalse()
        }

        @Test
        fun `単位と備考は空でもよく、前後の空白を除いて空なら null にする`() {
            val values = newEntry().copy(name = " 牛乳 ", quantity = "２", unit = " ", memo = "  ").confirm()
            assertThat(values).isEqualTo(ItemEntry.Values("牛乳", 2.0, null, null, Visibility.SHARED))
        }

        @Test
        fun `選んだ公開範囲と備考で確定する`() {
            val values = newEntry().copy(memo = " 特売なら2本 ").withVisibility(Visibility.PRIVATE).confirm()
            assertThat(values).isEqualTo(ItemEntry.Values("牛乳", 1.0, "個", "特売なら2本", Visibility.PRIVATE))
        }
    }
}
