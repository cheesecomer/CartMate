package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Attribute
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.AttributeKind
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import java.time.Instant

/** AI の名寄せのテストで使う辞書・商品・購入・判定 */
internal object AiJudgementTestData {
    val sesameDressing = CanonicalId("sesame_dressing")
    val milk = CanonicalId("milk")
    val soyMilk = CanonicalId("soy_milk")
    val greenTea = CanonicalId("green_tea")

    val dictionary = NormalizationDictionary(
        version = "test",
        canonicalNames = mapOf(sesameDressing to "ごまドレッシング", milk to "牛乳", soyMilk to "豆乳", greenTea to "緑茶"),
        aliases = mapOf(
            "ごまドレ" to Alias(sesameDressing, Match.EXACT),
            "ごまドレッシング" to Alias(sesameDressing, Match.EXACT),
            "牛乳" to Alias(milk, Match.EXACT),
            "豆乳" to Alias(soyMilk, Match.EXACT),
            "お茶" to Alias(greenTea, Match.DEFAULT),
        ),
        neverMerge = setOf(setOf(milk, soyMilk)),
        attributes = mapOf(
            "詰め替え" to Attribute(AttributeKind.VARIANT, "詰め替え"),
            "ペットボトル" to Attribute(AttributeKind.FORM, "飲料"),
        ),
    )

    fun product(name: String, createdAt: Long = 0, mergedInto: Product? = null, optOut: Boolean = false) = Product(
        id = ProductId(NameNormalizer.productId(name)),
        displayName = NameNormalizer.displayName(name),
        normalizedName = NameNormalizer.normalizedName(name),
        searchKey = NameNormalizer.searchKey(name),
        createdAt = Instant.EPOCH.plusSeconds(createdAt),
        mergedInto = mergedInto?.id,
        dictionaryOptOut = optOut,
    )

    fun purchase(
        product: Product,
        day: Long = 0,
        memo: String? = null,
        visibility: Visibility = Visibility.SHARED,
        active: Boolean = true,
        id: String = "${product.id.value}-$day",
    ) = Purchase(
        itemId = ItemId(id),
        listId = ListId("l"),
        listNameSnapshot = "スーパー",
        inputName = product.displayName,
        quantity = null,
        unit = null,
        memo = memo,
        visibility = visibility,
        checkedAt = Instant.EPOCH.plusSeconds(day * 86_400),
        purchasedAt = Instant.EPOCH.plusSeconds(day * 86_400),
        purchasedBy = UserId("taro"),
        active = active,
        revokedAt = null,
        revokedBy = null,
        productId = product.id,
    )

    /** 名前だけ（属性なし）の、今のバージョンの判定 */
    fun judgement(
        product: Product,
        item: String?,
        confidence: Double = 0.9,
        qualifiers: Map<String, List<String>> = emptyMap(),
        form: String? = null,
        volume: String? = null,
        use: String? = null,
        variant: String? = null,
        status: NameJudgement.Status = NameJudgement.Status.DONE,
        version: String = NameJudgement.CURRENT_VERSION,
    ): NameJudgement {
        val key = JudgementKey.of(product.normalizedName, qualifiers)
        return NameJudgement(
            key = key,
            normalizedName = product.normalizedName,
            qualifiers = qualifiers,
            status = status,
            judgeVersion = version,
            requestedAt = Instant.EPOCH,
            judgedAt = Instant.EPOCH,
            item = item,
            form = form,
            volume = volume,
            use = use,
            variant = variant,
            confidence = confidence,
            reason = "${product.displayName}は${item}",
        )
    }
}
