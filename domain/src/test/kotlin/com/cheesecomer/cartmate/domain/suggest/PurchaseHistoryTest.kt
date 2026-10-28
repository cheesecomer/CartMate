package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.CanonicalId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class PurchaseHistoryTest {

    private fun product(name: String, mergedInto: Product? = null) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = name,
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH,
        mergedInto = mergedInto?.id,
    )

    private fun purchase(id: String, product: Product, day: Long, active: Boolean = true, excluded: Boolean = false) = Purchase(
        itemId = ItemId(id),
        listId = ListId("l"),
        listNameSnapshot = "スーパー",
        inputName = product.displayName,
        quantity = 1.0,
        unit = "個",
        memo = null,
        visibility = Visibility.SHARED,
        checkedAt = Instant.EPOCH.plusSeconds(day * 86_400),
        purchasedAt = Instant.EPOCH.plusSeconds(day * 86_400),
        purchasedBy = UserId("taro"),
        active = active,
        revokedAt = null,
        revokedBy = null,
        productId = product.id,
        excludedFromPrediction = excluded,
    )

    @Test
    fun `統合している商品と辞書でまとまる商品の記録を新しい順に出し、Undo した記録は出さない`() {
        val milk = product("牛乳")
        val typo = product("牛入", mergedInto = milk)
        val kana = product("ぎゅうにゅう")
        val other = product("豆乳")
        val dictionary = NormalizationDictionary(
            version = "test",
            canonicalNames = mapOf(CanonicalId("milk") to "牛乳"),
            aliases = mapOf(
                "牛乳" to NormalizationDictionary.Alias(CanonicalId("milk"), NormalizationDictionary.Match.EXACT),
                "ぎゅうにゅう" to NormalizationDictionary.Alias(CanonicalId("milk"), NormalizationDictionary.Match.PREFIX),
            ),
        )
        val purchases = listOf(
            purchase("a", milk, 1),
            purchase("b", typo, 2, excluded = true),
            purchase("c", kana, 3),
            purchase("d", milk, 4, active = false),
            purchase("e", other, 5),
        )
        val history = PurchaseHistory.of(milk.id, listOf(milk, typo, kana, other), purchases, dictionary)
        assertThat(history.map { it.itemId.value }).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun `前回の購入日と、予測に使う記録から求めた周期を出す。記録が少なければ周期は出さない`() {
        val milk = product("牛乳")
        val history = listOf(
            purchase("a", milk, day = 0),
            purchase("b", milk, day = 14),
            purchase("c", milk, day = 28),
            // 予測に使わない記録は、周期に入れない（前回の購入日には入れる）
            purchase("d", milk, day = 30, excluded = true),
        )
        val summary = PurchaseHistory.summary(history, java.time.ZoneOffset.UTC)!!
        assertThat(summary.lastPurchase).isEqualTo(java.time.LocalDate.of(1970, 1, 31))
        assertThat(summary.typicalIntervalDays).isEqualTo(14)

        assertThat(PurchaseHistory.summary(history.take(2), java.time.ZoneOffset.UTC)!!.typicalIntervalDays).isNull()
        assertThat(PurchaseHistory.summary(emptyList(), java.time.ZoneOffset.UTC)).isNull()
    }
}
