package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.input.AddItemState.Entering
import com.cheesecomer.cartmate.domain.input.AddItemState.Idle
import com.cheesecomer.cartmate.domain.input.AddItemState.Open
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AddItemReducerTest {

    private fun reduce(state: AddItemState, event: AddItemEvent) = AddItemReducer.reduce(state, event)

    private val withHistory = Suggestion("卵", lastQuantity = 2.0, lastUnit = "パック")
    private val withoutHistory = Suggestion("たまご", lastQuantity = null, lastUnit = null)

    @Nested
    inner class Idleから {
        @Test
        fun `商品追加欄をタップすると、入力途中の商品名を引き継いでシートを開く`() {
            assertThat(reduce(Idle("牛"), AddItemEvent.FieldFocused)).isEqualTo(AddItemTransition(Open("牛")))
        }

        @Test
        fun `Enter は無視する`() {
            assertThat(reduce(Idle("牛乳"), AddItemEvent.Submitted)).isEqualTo(AddItemTransition(Idle("牛乳")))
        }

        @Test
        fun `入力は保持する`() {
            assertThat(reduce(Idle(""), AddItemEvent.TextChanged("牛")).state).isEqualTo(Idle("牛"))
        }
    }

    @Nested
    inner class Openから {
        @Test
        fun `入力するとテキストが変わる`() {
            assertThat(reduce(Open(""), AddItemEvent.TextChanged("牛乳"))).isEqualTo(AddItemTransition(Open("牛乳")))
        }

        @Test
        fun `Enter で数量1・単位「個」で追加し、シートを開いたまま入力欄を空にする`() {
            assertThat(reduce(Open(" 牛乳 "), AddItemEvent.Submitted)).isEqualTo(
                AddItemTransition(Open(""), AddItemEffect.AddItem("牛乳", 1.0, "個")),
            )
        }

        @Test
        fun `空白だけで Enter を押しても追加しない`() {
            assertThat(reduce(Open("  "), AddItemEvent.Submitted)).isEqualTo(AddItemTransition(Open("  ")))
        }

        @Test
        fun `候補の「＋」で前回の数量・単位で追加する`() {
            assertThat(reduce(Open("たま"), AddItemEvent.SuggestionAdded(withHistory))).isEqualTo(
                AddItemTransition(Open(""), AddItemEffect.AddItem("卵", 2.0, "パック")),
            )
        }

        @Test
        fun `前回の数量・単位がない候補の「＋」は、1・「個」で追加する`() {
            assertThat(reduce(Open("たま"), AddItemEvent.SuggestionAdded(withoutHistory)).effect)
                .isEqualTo(AddItemEffect.AddItem("たまご", 1.0, "個"))
        }

        @Test
        fun `前回の単位がなく数量だけある候補は、単位なしで追加する`() {
            val suggestion = Suggestion("バナナ", lastQuantity = 3.0, lastUnit = null)
            assertThat(reduce(Open(""), AddItemEvent.SuggestionAdded(suggestion)).effect)
                .isEqualTo(AddItemEffect.AddItem("バナナ", 3.0, null))
        }

        @Test
        fun `候補の名前をタップすると、候補の内容で商品詳細入力画面を開き、入力した文字は保持する`() {
            assertThat(reduce(Open("たま"), AddItemEvent.SuggestionOpened(withHistory))).isEqualTo(
                AddItemTransition(Entering("たま"), AddItemEffect.OpenEntry("卵", 2.0, "パック")),
            )
        }

        @Test
        fun `「数量を設定して追加」で、入力した商品名で商品詳細入力画面を開く`() {
            assertThat(reduce(Open(" 豆乳 "), AddItemEvent.DetailRequested)).isEqualTo(
                AddItemTransition(Entering(" 豆乳 "), AddItemEffect.OpenEntry("豆乳", 1.0, "個")),
            )
        }

        @Test
        fun `未入力では「数量を設定して追加」は何もしない`() {
            assertThat(reduce(Open(""), AddItemEvent.DetailRequested)).isEqualTo(AddItemTransition(Open("")))
        }

        @Test
        fun `シートを閉じても入力途中の商品名は残る`() {
            assertThat(reduce(Open("牛"), AddItemEvent.Dismissed)).isEqualTo(AddItemTransition(Idle("牛")))
        }
    }

    @Nested
    inner class Enteringから {
        @Test
        fun `追加して戻ると、シートを閉じて商品追加欄を空にする`() {
            assertThat(reduce(Entering("豆乳"), AddItemEvent.EntryClosed(added = true)))
                .isEqualTo(AddItemTransition(Idle("")))
        }

        @Test
        fun `追加せずに戻ると、入力した商品名のままシートに戻る`() {
            assertThat(reduce(Entering("豆乳"), AddItemEvent.EntryClosed(added = false)))
                .isEqualTo(AddItemTransition(Open("豆乳")))
        }

        @Test
        fun `ほかのイベントは無視する`() {
            assertThat(reduce(Entering("豆乳"), AddItemEvent.Submitted)).isEqualTo(AddItemTransition(Entering("豆乳")))
        }
    }

    @Test
    fun `候補に前回の備考があれば、＋でその備考も一緒に追加する`() {
        val suggestion = Suggestion("緑茶", 1.0, "袋", memo = "茶葉")
        assertThat(AddItemReducer.reduce(AddItemState.Open(""), AddItemEvent.SuggestionAdded(suggestion)).effect)
            .isEqualTo(AddItemEffect.AddItem("緑茶", 1.0, "袋", memo = "茶葉"))
    }

    @Nested
    inner class 候補の長押し {
        private val egg = Suggestion("卵", 2.0, "パック", productId = com.cheesecomer.cartmate.domain.model.ProductId("n_egg"))

        @Test
        fun `長押しすると、シートを閉じて商品の画面を開き、入力した商品名は商品追加欄に残す`() {
            assertThat(reduce(Open("た"), AddItemEvent.SuggestionLongPressed(egg)))
                .isEqualTo(AddItemTransition(Idle("た"), AddItemEffect.OpenProduct(egg.productId!!)))
        }

        @Test
        fun `商品の分からない候補は、長押ししても何もしない`() {
            assertThat(reduce(Open(""), AddItemEvent.SuggestionLongPressed(withHistory))).isEqualTo(AddItemTransition(Open("")))
        }

    }
}
