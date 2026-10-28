package com.cheesecomer.cartmate.domain.suggest

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.ItemStatus
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.model.UserId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.product.CanonicalId
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Alias
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary.Match
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine.DueResult
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine.MatchReason
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SuggestionEngineTest {

    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 1, 1)
    private val taro = UserId("taro")
    private var nextId = 0

    private fun product(name: String, mergedInto: Product? = null, hiddenAt: Instant? = null, suggestionExcluded: Boolean = false) =
        Product(
            id = ProductId(NameNormalizer.productId(name)),
            displayName = name,
            normalizedName = NameNormalizer.normalizedName(name),
            searchKey = NameNormalizer.searchKey(name),
            createdAt = Instant.EPOCH,
            mergedInto = mergedInto?.id,
            hiddenAt = hiddenAt,
            suggestionExcluded = suggestionExcluded,
        )

    private fun at(day: Long): Instant = day0.plusDays(day).atStartOfDay(zone).toInstant()

    private fun purchase(
        product: Product,
        day: Long,
        list: String = "スーパー",
        quantity: Double? = 1.0,
        unit: String? = "個",
        memo: String? = null,
        active: Boolean = true,
        excluded: Boolean = false,
    ) = Purchase(
        itemId = ItemId("p${nextId++}"),
        listId = ListId(list),
        listNameSnapshot = list,
        inputName = product.displayName,
        quantity = quantity,
        unit = unit,
        memo = memo,
        visibility = Visibility.SHARED,
        checkedAt = at(day),
        purchasedAt = at(day),
        purchasedBy = taro,
        active = active,
        revokedAt = null,
        revokedBy = null,
        productId = product.id,
        excludedFromPrediction = excluded,
    )

    /** [product] を [days] の日に買った */
    private fun bought(product: Product, vararg days: Long, list: String = "スーパー") = days.map { purchase(product, it, list) }

    private fun openItem(product: Product, list: String = "スーパー") = Item(
        id = ItemId("i${nextId++}"),
        listId = ListId(list),
        inputName = product.displayName,
        sortKey = 1.0,
        quantity = 1.0,
        unit = "個",
        memo = null,
        visibility = Visibility.SHARED,
        status = ItemStatus.OPEN,
        createdAt = at(0),
        createdBy = taro,
        completedAt = null,
        completedBy = null,
        productId = product.id,
    )

    private fun engine(
        products: List<Product>,
        purchases: List<Purchase>,
        today: Long,
        openItems: List<Item> = emptyList(),
        dictionary: NormalizationDictionary = NormalizationDictionary.EMPTY,
    ) = SuggestionEngine(products, purchases, openItems, dictionary, day0.plusDays(today), zone)

    /** 出す商品の名前。関連するリストがなく何も出さない場合（NoContext）も空 */
    private fun SuggestionEngine.dueNames(list: String = "スーパー"): List<String> =
        (due(list) as? DueResult.Suggestions)?.items.orEmpty().map { it.product.displayName }

    // 未入力時のサジェスト

    @Test
    fun `経過率が0点80以上3点0以下の商品だけを出す`() {
        val milk = product("牛乳")
        // 100日ごとに買い、最後は200日目
        val purchases = bought(milk, 0, 100, 200)
        assertThat(engine(listOf(milk), purchases, today = 279).dueNames()).isEmpty()
        assertThat(engine(listOf(milk), purchases, today = 280).dueNames()).containsExactly("牛乳")
        assertThat(engine(listOf(milk), purchases, today = 500).dueNames()).containsExactly("牛乳")
        assertThat(engine(listOf(milk), purchases, today = 501).dueNames()).isEmpty()
    }

    @Test
    fun `経過率の大きい順に並べ、補足の日数と前回の数量を持つ`() {
        val milk = product("牛乳")
        val eggs = product("卵")
        val purchases = bought(milk, 0, 10, 20) + bought(eggs, 0, 10) + purchase(eggs, 18, quantity = 2.0, unit = "パック")
        val items = (engine(listOf(milk, eggs), purchases, today = 29).due("スーパー") as DueResult.Suggestions).items
        assertThat(items.map { it.product.displayName }).containsExactly("卵", "牛乳").inOrder()
        assertThat(items.first().daysSinceLast).isEqualTo(11)
        assertThat(items.first().lastQuantity).isEqualTo(2.0)
        assertThat(items.first().lastUnit).isEqualTo("パック")
    }

    @Test
    fun `未購入にある商品は、統合元の名前で登録されていても統合先で一致すれば出さない`() {
        val milk = product("牛乳")
        val kana = product("ぎゅうにゅう", mergedInto = milk)
        val purchases = bought(milk, 0, 10, 20)
        assertThat(engine(listOf(milk, kana), purchases, today = 29, openItems = listOf(openItem(kana))).dueNames()).isEmpty()
    }

    @Test
    fun `同じ名前のリストの履歴も関連するリストもなければ、何も出さない`() {
        val milk = product("牛乳")
        assertThat(engine(listOf(milk), bought(milk, 0, 10, 20), today = 29).due("ホームセンター")).isEqualTo(DueResult.NoContext)
    }

    @Test
    fun `周期は全リストの履歴を合わせて計算する`() {
        val milk = product("牛乳")
        val purchases = bought(milk, 0, list = "スーパー") + bought(milk, 10, list = "コンビニ") + bought(milk, 20, list = "スーパー")
        assertThat(engine(listOf(milk), purchases, today = 29).dueNames("スーパー")).containsExactly("牛乳")
    }

    @Test
    fun `提案から除外した商品は出さない`() {
        val milk = product("牛乳", suggestionExcluded = true)
        assertThat(engine(listOf(milk), bought(milk, 0, 10, 20), today = 29).dueNames()).isEmpty()
    }

    @Test
    fun `Undo した記録と予測に使わない記録は周期に使わない`() {
        val milk = product("牛乳")
        val purchases = bought(milk, 0, 10) + purchase(milk, 20, active = false) + purchase(milk, 25, excluded = true)
        // 使える購入日が2日なので推定しない
        assertThat(engine(listOf(milk), purchases + bought(milk, list = "スーパー"), today = 29).dueNames()).isEmpty()
    }

    @Test
    fun `非表示の商品は出さず、また表示されても非表示にする前の記録は使わない`() {
        val hiddenAt = at(21)
        val putty = product("パテ", hiddenAt = hiddenAt)
        val purchases = bought(putty, 0, 10, 20)
        assertThat(engine(listOf(putty), purchases, today = 29).dueNames()).isEmpty()
        // 非表示にしたあとに1回買ってまた表示された。前の3回は使わないので、購入日は1日だけ
        val again = engine(listOf(putty), purchases + bought(putty, 40), today = 69)
        assertThat(again.dueNames()).isEmpty()
        assertThat(again.complete("パ", "スーパー").map { it.product.displayName }).containsExactly("パテ")
    }

    @Test
    fun `統合された商品の名前は出さず、統合先の名前を出す`() {
        val milk = product("牛乳")
        val typo = product("牛入", mergedInto = milk)
        // 最後に買ったのは誤字の名前
        val purchases = bought(milk, 0, 10) + bought(typo, 20)
        val engine = engine(listOf(milk, typo), purchases, today = 29)
        assertThat(engine.dueNames()).containsExactly("牛乳")
        assertThat(engine.complete("牛", "スーパー").map { it.product.displayName }).containsExactly("牛乳")
    }

    @Test
    fun `周期のグループは周期だけを合わせて数える`() {
        val bread6 = product("食パン6枚切り")
        val bread8 = product("食パン8枚切り")
        val dictionary = NormalizationDictionary(
            version = "test",
            canonicalNames = mapOf(CanonicalId("bread_6") to "食パン6枚切り", CanonicalId("bread_8") to "食パン8枚切り"),
            aliases = mapOf(
                "食パン6枚切り" to Alias(CanonicalId("bread_6"), Match.EXACT),
                "食パン8枚切り" to Alias(CanonicalId("bread_8"), Match.EXACT),
            ),
            cycleGroups = mapOf(
                CanonicalId("bread_6") to com.cheesecomer.cartmate.domain.product.CycleGroupId("cycle_bread"),
                CanonicalId("bread_8") to com.cheesecomer.cartmate.domain.product.CycleGroupId("cycle_bread"),
            ),
        )
        val purchases = bought(bread6, 0, 20) + bought(bread8, 10)
        val engine = engine(listOf(bread6, bread8), purchases, today = 28, dictionary = dictionary)
        // 6枚切りと8枚切りを合わせて10日ごと。最後に買った6枚切りを出す
        assertThat(engine.dueNames()).containsExactly("食パン6枚切り")
        // 片方が未購入にあれば、もう片方も出さない
        assertThat(engine(listOf(bread6, bread8), purchases, today = 28, openItems = listOf(openItem(bread8)), dictionary = dictionary).dueNames())
            .isEmpty()
    }

    // 入力中の補完

    @Test
    fun `前方一致を部分一致より優先し、同じなら購入回数の多い順`() {
        val milk = product("牛乳")
        val milkTea = product("牛乳パン")
        val coffeeMilk = product("コーヒー牛乳")
        val purchases = bought(milk, 0) + bought(milkTea, 0, 5) + bought(coffeeMilk, 0, 5, 9)
        val completions = engine(listOf(milk, milkTea, coffeeMilk), purchases, today = 10).complete("牛乳", "スーパー")
        assertThat(completions.map { it.product.displayName }).containsExactly("牛乳パン", "牛乳", "コーヒー牛乳").inOrder()
        assertThat(completions.last().reason).isEqualTo(MatchReason.PARTIAL)
    }

    @Test
    fun `部分一致だけの候補は2回以上買った商品に限る`() {
        val coffeeMilk = product("コーヒー牛乳")
        assertThat(engine(listOf(coffeeMilk), bought(coffeeMilk, 0), today = 10).complete("牛乳", "スーパー")).isEmpty()
    }

    @Test
    fun `辞書で、履歴の商品と入力を照合する（履歴が秋刀魚で入力がさんま）`() {
        val saury = product("秋刀魚")
        val dictionary = NormalizationDictionary(
            version = "test",
            canonicalNames = mapOf(CanonicalId("saury") to "さんま"),
            aliases = mapOf("秋刀魚" to Alias(CanonicalId("saury"), Match.PREFIX), "サンマ" to Alias(CanonicalId("saury"), Match.PREFIX)),
        )
        val completions = engine(listOf(saury), bought(saury, 0), today = 10, dictionary = dictionary).complete("さんま", "スーパー")
        assertThat(completions.map { it.product.displayName }).containsExactly("秋刀魚")
        assertThat(completions.single().reason).isEqualTo(MatchReason.DICTIONARY)
    }

    @Test
    fun `辞書に載っているだけで買っていない商品は候補にしない`() {
        val dictionary = NormalizationDictionary(
            version = "test",
            canonicalNames = mapOf(CanonicalId("saury") to "さんま"),
            aliases = mapOf("秋刀魚" to Alias(CanonicalId("saury"), Match.PREFIX)),
        )
        assertThat(engine(emptyList(), emptyList(), today = 10, dictionary = dictionary).complete("さんま", "スーパー")).isEmpty()
    }

    @Test
    fun `未購入にある商品には追加済みを付け、前回の数量と早すぎる再購入を持つ`() {
        val milk = product("牛乳")
        val purchases = bought(milk, 0, 10) + purchase(milk, 20, quantity = 2.0, unit = "本")
        val completion = engine(listOf(milk), purchases, today = 22, openItems = listOf(openItem(milk))).complete("ぎゅ", "スーパー")
        assertThat(completion).isEmpty()
        val single = engine(listOf(milk), purchases, today = 22, openItems = listOf(openItem(milk))).complete("牛", "スーパー").single()
        assertThat(single.alreadyAdded).isTrue()
        assertThat(single.lastQuantity).isEqualTo(2.0)
        assertThat(single.lastUnit).isEqualTo("本")
        assertThat(single.early).isEqualTo(EarlyPurchaseNotice(day0.plusDays(20), typicalIntervalDays = 10))
    }

    @Test
    fun `非表示の商品は補完に出さない`() {
        val test = product("テスト", hiddenAt = at(5))
        assertThat(engine(listOf(test), bought(test, 0), today = 10).complete("テ", "スーパー")).isEmpty()
    }

    @Test
    fun `補完は最大8件`() {
        val products = (1..10).map { product("牛乳$it") }
        val purchases = products.flatMap { bought(it, 0) }
        assertThat(engine(products, purchases, today = 10).complete("牛乳", "スーパー")).hasSize(8)
    }

    // 商品詳細入力画面

    @Test
    fun `前回の数量・購入日・普段の周期を返す`() {
        val milk = product("牛乳")
        val purchases = bought(milk, 0, 14) + purchase(milk, 28, quantity = 1.0, unit = "パック")
        val previous = engine(listOf(milk), purchases, today = 30).previous("牛乳")!!
        assertThat(previous.quantity).isEqualTo(1.0)
        assertThat(previous.unit).isEqualTo("パック")
        assertThat(previous.date).isEqualTo(day0.plusDays(28))
        assertThat(previous.typicalIntervalDays).isEqualTo(14)
        assertThat(previous.early).isNotNull()
    }

    @Test
    fun `購入の記録がなければ前回の購入はない`() {
        assertThat(engine(emptyList(), emptyList(), today = 0).previous("牛乳")).isNull()
    }

    @Test
    fun `備考の候補は新しい順・重複なし・最大3件・空は除く`() {
        val milk = product("牛乳")
        val purchases = listOf("低脂肪", "", "特売なら", "低脂肪", "1L", "大きいの").mapIndexed { day, memo ->
            purchase(milk, day.toLong(), memo = memo)
        }
        assertThat(engine(listOf(milk), purchases, today = 10).memoCandidates("牛乳"))
            .containsExactly("大きいの", "1L", "低脂肪").inOrder()
    }

    // 商品名と備考による判定（docs/design/suggestions.md）

    private val teaDictionary = NormalizationDictionary(
        version = "test",
        canonicalNames = mapOf(CanonicalId("green_tea") to "緑茶", CanonicalId("barley_tea") to "麦茶"),
        aliases = mapOf(
            "緑茶" to Alias(CanonicalId("green_tea"), Match.EXACT),
            "麦茶" to Alias(CanonicalId("barley_tea"), Match.EXACT),
            "お茶" to Alias(CanonicalId("green_tea"), Match.DEFAULT),
        ),
        attributes = mapOf(
            "麦茶" to NormalizationDictionary.Attribute(NormalizationDictionary.AttributeKind.ITEM, "barley_tea"),
            "緑茶" to NormalizationDictionary.Attribute(NormalizationDictionary.AttributeKind.ITEM, "green_tea"),
            "茶葉" to NormalizationDictionary.Attribute(NormalizationDictionary.AttributeKind.FORM, "茶葉"),
            "特売なら" to NormalizationDictionary.Attribute(NormalizationDictionary.AttributeKind.IGNORE, "-"),
        ),
    )

    private fun teaEngine(products: List<Product>, purchases: List<Purchase>, today: Long) =
        engine(products, purchases, today, dictionary = teaDictionary)

    @Test
    fun `品目の指定がないお茶は、既定の同義語として緑茶と合わせて数える`() {
        val tea = product("お茶")
        val green = product("緑茶")
        val purchases = bought(tea, 0, 20) + bought(green, 10)
        assertThat(teaEngine(listOf(tea, green), purchases, today = 28).dueNames()).hasSize(1)
    }

    @Test
    fun `お茶に備考で麦茶と書いた記録は、麦茶として数える`() {
        val tea = product("お茶")
        val barley = product("麦茶")
        val purchases = listOf(purchase(tea, 0, memo = "麦茶")) + bought(barley, 10, 20)
        val engine = teaEngine(listOf(tea, barley), purchases, today = 28)
        assertThat(engine.dueNames()).containsExactly("麦茶")
    }

    @Test
    fun `緑茶に備考で麦茶と書いた記録は、食い違うので緑茶にも麦茶にもまとめない`() {
        val green = product("緑茶")
        val purchases = bought(green, 0, 10) + purchase(green, 20, memo = "麦茶")
        // 緑茶として使える購入日は2日だけなので、予測しない
        assertThat(teaEngine(listOf(green), purchases, today = 28).dueNames()).isEmpty()
    }

    @Test
    fun `備考の属性が違う記録は別に数え、属性と前回の備考を添えて出す`() {
        val green = product("緑茶")
        val leaf = listOf(0L, 10, 20).map { purchase(green, it, memo = "茶葉") }
        val plain = bought(green, 1, 11, 21)
        val items = (teaEngine(listOf(green), leaf + plain, today = 29).due("スーパー") as DueResult.Suggestions).items
        assertThat(items).hasSize(2)
        val withLeaf = items.single { it.labels.isNotEmpty() }
        assertThat(withLeaf.labels).containsExactly("茶葉")
        assertThat(withLeaf.lastMemo).isEqualTo("茶葉")
        val withoutLeaf = items.single { it.labels.isEmpty() }
        assertThat(withoutLeaf.lastMemo).isNull()
    }

    @Test
    fun `属性が未指定の記録と指定のある記録は、合わせて数えない`() {
        val green = product("緑茶")
        val purchases = bought(green, 0, 10) + purchase(green, 20, memo = "2L")
        assertThat(teaEngine(listOf(green), purchases, today = 28).dueNames()).isEmpty()
    }

    @Test
    fun `判定に使わない語だけの備考は、分けずに数える`() {
        val milk = product("牛乳")
        val purchases = bought(milk, 0, 10) + purchase(milk, 20, memo = "特売なら")
        assertThat(teaEngine(listOf(milk), purchases, today = 28).dueNames()).containsExactly("牛乳")
    }

    @Test
    fun `容量は書き方が違っても同じものとして数え、2Lと表示する`() {
        val green = product("緑茶")
        val purchases = listOf(purchase(green, 0, memo = "2L"), purchase(green, 10, memo = "2リットル"), purchase(green, 20, memo = "2000ml"))
        val item = (teaEngine(listOf(green), purchases, today = 28).due("スーパー") as DueResult.Suggestions).items.single()
        assertThat(item.labels).containsExactly("2L")
    }
}

