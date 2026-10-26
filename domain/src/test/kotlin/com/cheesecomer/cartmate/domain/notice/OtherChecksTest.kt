package com.cheesecomer.cartmate.domain.notice

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Member
import com.cheesecomer.cartmate.domain.model.MemberRole
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class OtherChecksTest {

    private val me = UserId("taro")
    private val hanako = UserId("hanako")
    private val kenta = UserId("kenta")

    private fun item(name: String, checker: UserId? = null) = Item(
        id = ItemId(name),
        listId = ListId("super"),
        inputName = name,
        sortKey = 1.0,
        quantity = null,
        unit = null,
        memo = null,
        visibility = Visibility.SHARED,
        status = if (checker == null) ItemStatus.OPEN else ItemStatus.COMPLETED,
        createdAt = Instant.EPOCH,
        createdBy = me,
        completedAt = checker?.let { Instant.EPOCH },
        completedBy = checker,
    )

    @Test
    fun `画面を開いた時点ですでにチェック済みの項目は通知しない`() {
        assertThat(OtherChecks.newlyChecked(previous = null, current = listOf(item("牛乳", hanako)), me = me)).isEmpty()
    }

    @Test
    fun `ほかのメンバーが未購入の項目をチェックしたら通知する`() {
        val checked = OtherChecks.newlyChecked(listOf(item("牛乳"), item("卵")), listOf(item("牛乳", hanako), item("卵")), me)
        assertThat(checked.map { it.inputName }).containsExactly("牛乳")
    }

    @Test
    fun `自分のチェックは通知しない`() {
        assertThat(OtherChecks.newlyChecked(listOf(item("牛乳")), listOf(item("牛乳", me)), me)).isEmpty()
    }

    @Test
    fun `チェック済みの項目が新しく一覧に入っただけなら通知しない`() {
        assertThat(OtherChecks.newlyChecked(emptyList(), listOf(item("牛乳", hanako)), me)).isEmpty()
    }

    @Test
    fun `チェックした人ごとに1件にまとめる`() {
        val summaries = OtherChecks.summarize(listOf(item("牛乳", hanako), item("卵", kenta), item("パン", hanako), item("豆腐", hanako)))
        assertThat(summaries).containsExactly(
            OtherChecks.Summary(hanako, "牛乳", othersCount = 2),
            OtherChecks.Summary(kenta, "卵", othersCount = 0),
        ).inOrder()
    }

    @Test
    fun `家族にいない人の名前は分からない`() {
        val names = MemberNames(listOf(Member(hanako, MemberRole.MEMBER, "山田 花子", null, Instant.EPOCH)))
        assertThat(names.of(hanako)).isEqualTo("山田 花子")
        assertThat(names.of(kenta)).isNull()
    }
}
