package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.input.AddItemState.Detail
import com.cheesecomer.cartmate.domain.input.AddItemState.Editing
import com.cheesecomer.cartmate.domain.input.AddItemState.FocusedEmpty
import com.cheesecomer.cartmate.domain.input.AddItemState.Idle
import com.cheesecomer.cartmate.domain.input.AddItemState.Typing
import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AddItemReducerTest {

    private fun reduce(state: AddItemState, event: AddItemEvent) = AddItemReducer.reduce(state, event)

    @Nested
    inner class Idleから {
        @Test
        fun `空欄でフォーカスすると FocusedEmpty`() {
            assertThat(reduce(Idle(""), AddItemEvent.NameFocusChanged(true)).state).isEqualTo(FocusedEmpty)
        }

        @Test
        fun `入力が残った状態でフォーカスすると Typing`() {
            assertThat(reduce(Idle("牛乳"), AddItemEvent.NameFocusChanged(true)).state).isEqualTo(Typing("牛乳"))
        }

        @Test
        fun `Enter は無視する`() {
            assertThat(reduce(Idle("牛乳"), AddItemEvent.NameSubmitted)).isEqualTo(AddItemTransition(Idle("牛乳")))
        }
    }

    @Nested
    inner class 商品名の入力中 {
        @Test
        fun `文字を入力すると Typing、すべて消すと FocusedEmpty`() {
            val typing = reduce(FocusedEmpty, AddItemEvent.NameChanged("牛")).state
            assertThat(typing).isEqualTo(Typing("牛"))
            assertThat(reduce(typing, AddItemEvent.NameChanged("")).state).isEqualTo(FocusedEmpty)
        }

        @Test
        fun `フォーカスを失うと Idle になり、入力は保持する`() {
            assertThat(reduce(Typing("牛乳"), AddItemEvent.NameFocusChanged(false)).state).isEqualTo(Idle("牛乳"))
            assertThat(reduce(FocusedEmpty, AddItemEvent.NameFocusChanged(false)).state).isEqualTo(Idle(""))
        }

        @Test
        fun `戻るキーで Idle になる`() {
            assertThat(reduce(Typing("牛乳"), AddItemEvent.Cancelled).state).isEqualTo(Idle("牛乳"))
        }

        @Test
        fun `Enter で数量入力に進む。名前の前後の空白は除き、数量は1、単位は空`() {
            val transition = reduce(Typing(" 牛乳 "), AddItemEvent.NameSubmitted)
            assertThat(transition.state).isEqualTo(Detail(name = "牛乳", quantity = "1", unit = ""))
            assertThat(transition.effect).isNull()
        }

        @Test
        fun `空白だけの入力や未入力では Enter しても進まない`() {
            assertThat(reduce(Typing("  "), AddItemEvent.NameSubmitted).state).isEqualTo(Typing("  "))
            assertThat(reduce(FocusedEmpty, AddItemEvent.NameSubmitted).state).isEqualTo(FocusedEmpty)
        }
    }

    @Nested
    inner class 数量入力 {
        private val detail = Detail(name = "牛乳", quantity = "1", unit = "")

        @Test
        fun `確定すると項目を追加し、商品名の入力に戻る`() {
            val transition = reduce(detail.copy(quantity = "2", unit = " 本 "), AddItemEvent.Confirmed)
            assertThat(transition.state).isEqualTo(FocusedEmpty)
            assertThat(transition.effect).isEqualTo(AddItemEffect.AddItem("牛乳", 2.0, "本"))
        }

        @Test
        fun `数量と単位が空欄なら未指定として追加する`() {
            val transition = reduce(detail.copy(quantity = "", unit = ""), AddItemEvent.Confirmed)
            assertThat(transition.effect).isEqualTo(AddItemEffect.AddItem("牛乳", null, null))
        }

        @Test
        fun `数量が不正なら確定できない`() {
            val invalid = detail.copy(quantity = "0")
            assertThat(invalid.canConfirm).isFalse()
            assertThat(reduce(invalid, AddItemEvent.Confirmed)).isEqualTo(AddItemTransition(invalid))
        }

        @Test
        fun `数量と単位を編集できる`() {
            val edited = reduce(reduce(detail, AddItemEvent.QuantityChanged("3")).state, AddItemEvent.UnitChanged("個"))
            assertThat(edited.state).isEqualTo(detail.copy(quantity = "3", unit = "個"))
        }

        @Test
        fun `キャンセルすると名前の入力に戻る`() {
            assertThat(reduce(detail, AddItemEvent.Cancelled).state).isEqualTo(Typing("牛乳"))
        }

        @Test
        fun `商品名の欄のフォーカスが変わっても下書きを保持する`() {
            assertThat(reduce(detail, AddItemEvent.NameFocusChanged(false)).state).isEqualTo(detail)
            assertThat(reduce(detail, AddItemEvent.NameFocusChanged(true)).state).isEqualTo(detail)
        }
    }

    @Nested
    inner class 編集 {
        private val item = Item(
            id = ItemId("i1"),
            listId = ListId("l1"),
            inputName = "牛乳",
            quantity = 2.0,
            unit = "本",
            status = ItemStatus.OPEN,
            createdAt = Instant.EPOCH,
            createdBy = UserId("u"),
            completedAt = null,
            completedBy = null,
        )
        private val editing = Editing(ItemId("i1"), name = "牛乳", quantity = "2", unit = "本", draft = "")

        @Test
        fun `項目をタップすると、その内容で編集を始める`() {
            assertThat(reduce(Idle(""), AddItemEvent.EditRequested(item)).state).isEqualTo(editing)
        }

        @Test
        fun `数量・単位が未指定の項目は空欄で編集を始める`() {
            val state = reduce(Idle(""), AddItemEvent.EditRequested(item.copy(quantity = null, unit = null))).state
            assertThat(state).isEqualTo(editing.copy(quantity = "", unit = ""))
        }

        @Test
        fun `入力途中の商品名は、編集のあとに戻す`() {
            val started = reduce(Typing("卵"), AddItemEvent.EditRequested(item)).state
            assertThat((started as Editing).draft).isEqualTo("卵")
            assertThat(reduce(started, AddItemEvent.Cancelled).state).isEqualTo(Idle("卵"))
        }

        @Test
        fun `数量入力の途中でタップした場合は、その商品名を入力途中として残す`() {
            val started = reduce(Detail("卵", "1", ""), AddItemEvent.EditRequested(item)).state
            assertThat((started as Editing).draft).isEqualTo("卵")
        }

        @Test
        fun `確定すると更新し、編集前の状態に戻る`() {
            val edited = editing.copy(name = " 低脂肪乳 ", quantity = "", unit = " ", draft = "卵")
            val transition = reduce(edited, AddItemEvent.Confirmed)
            assertThat(transition.state).isEqualTo(Idle("卵"))
            assertThat(transition.effect).isEqualTo(AddItemEffect.UpdateItem(ItemId("i1"), "低脂肪乳", null, null))
        }

        @Test
        fun `商品名が空、または数量が不正なら確定できない`() {
            listOf(editing.copy(name = "  "), editing.copy(quantity = "0")).forEach { invalid ->
                assertThat(invalid.canConfirm).isFalse()
                assertThat(reduce(invalid, AddItemEvent.Confirmed)).isEqualTo(AddItemTransition(invalid))
            }
        }

        @Test
        fun `商品名・数量・単位を変更できる`() {
            var state: AddItemState = editing
            state = reduce(state, AddItemEvent.NameChanged("豆乳")).state
            state = reduce(state, AddItemEvent.QuantityChanged("3")).state
            state = reduce(state, AddItemEvent.UnitChanged("パック")).state
            assertThat(state).isEqualTo(editing.copy(name = "豆乳", quantity = "3", unit = "パック"))
        }

        @Test
        fun `別の項目をタップすると、その項目の編集に切り替わる`() {
            val other = item.copy(id = ItemId("i2"), inputName = "卵", quantity = 1.0, unit = "パック")
            val state = reduce(editing.copy(draft = "パン"), AddItemEvent.EditRequested(other)).state
            assertThat(state).isEqualTo(Editing(ItemId("i2"), "卵", "1", "パック", draft = "パン"))
        }
    }
}
