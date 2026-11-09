package com.cheesecomer.cartmate.domain.product

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.model.Purchase
import com.cheesecomer.cartmate.domain.text.ChoiceName
import com.cheesecomer.cartmate.domain.text.NameNormalizer

/**
 * AI の名寄せの候補（docs/design/suggestions.md「候補にする条件」）。AI の判定は候補を出すだけで、自動ではまとめない。
 *
 * 2つの商品の品目が同じで、属性に矛盾がなく（未指定と指定ありは一致とみなさない）、統合禁止に当たらず、
 * AI の信頼度がしきい値以上のとき、候補にする。品目は、AI の判定の品目か、辞書で決まる品目（辞書でまとまる商品）。
 * AI の品目が辞書にある名前なら、辞書の品目として比べる（「胡麻ドレ」の AI の品目「ごまドレッシング」と、辞書の「ごまドレ」）。
 * 少なくとも一方は AI の判定でなければならない（どちらも辞書なら、すでに辞書でまとまっている）
 */
object AiCandidates {

    /** 候補にする AI の信頼度の下限 */
    const val MIN_CONFIDENCE = 0.7

    /** 属性の種類（[Qualifier.kind]） */
    private val KINDS = listOf("form", "use", "variant", "volume")

    /**
     * 候補の組。選ぶと [source] を [target] にまとめる。
     * [target] は、辞書でまとまる商品があればそちら（辞書の名寄せを保つ）、なければ先に登録された商品
     */
    data class Candidate(val source: Product, val target: Product, val reasons: List<Reason>) {
        val pair: Pair get() = Pair.of(source.id, target.id)
    }

    /** AI の判定の理由。[name] は判定した商品の名前 */
    data class Reason(val name: String, val text: String)

    /** 向きのない商品の組。「この候補を出さない」で保存する */
    data class Pair private constructor(val first: ProductId, val second: ProductId) {
        /** 保存に使う文字列 */
        fun encode(): String = "${first.value}|${second.value}"

        companion object {
            fun of(a: ProductId, b: ProductId): Pair = if (a.value <= b.value) Pair(a, b) else Pair(b, a)

            fun decode(value: String): Pair? {
                val parts = value.split("|")
                return if (parts.size == 2 && parts.all { it.isNotEmpty() }) of(ProductId(parts[0]), ProductId(parts[1])) else null
            }
        }
    }

    /** 比べるための、商品の品目と属性 */
    private data class Identity(
        val product: Product,
        /** 辞書の品目なら "c:" に ID、AI の品目なら "n:" に正規化した名前 */
        val item: String,
        val canonical: CanonicalId?,
        val attributes: Map<String, String?>,
        val fromDictionary: Boolean,
        val reason: Reason?,
    )

    /**
     * 家族の候補の組。[visible] は自分に見える商品で、非表示・常に非表示の商品を除いたもの（呼ぶ側で決める）。
     * [judgements] は家族の判定（キーごと）、[dismissed] はこの端末で「この候補を出さない」にした組
     */
    fun find(
        products: Collection<Product>,
        visible: Collection<Product>,
        purchases: Collection<Purchase>,
        judgements: Map<String, NameJudgement>,
        dictionary: NormalizationDictionary,
        dismissed: Set<Pair> = emptySet(),
    ): List<Candidate> {
        val resolver = ProductResolver(products, dictionary)
        val identities = visible
            .filter { it.mergedInto == null && !ChoiceName.hasChoice(it.displayName) }
            .mapNotNull { identity(it, products, purchases, judgements, dictionary, resolver) }
        val candidates = ArrayList<Candidate>()
        for ((i, a) in identities.withIndex()) {
            for (b in identities.drop(i + 1)) {
                if (a.fromDictionary && b.fromDictionary) continue
                if (a.item != b.item || a.attributes != b.attributes) continue
                if (a.canonical != null && b.canonical != null && !dictionary.mayMerge(a.canonical, b.canonical)) continue
                if (Pair.of(a.product.id, b.product.id) in dismissed) continue
                val (source, target) = direction(a, b)
                candidates += Candidate(source.product, target.product, listOfNotNull(source.reason, target.reason))
            }
        }
        return candidates.sortedWith(compareBy({ it.target.displayName }, { it.source.displayName }))
    }

    /**
     * 商品 [id]（統合先）の候補。1つに決まるときだけ返す（表記ゆれのチップ。どちらにまとめるか決められないときは出さない）
     */
    fun forProduct(id: ProductId, candidates: List<Candidate>): Candidate? =
        candidates.filter { it.source.id == id || it.target.id == id }.singleOrNull()

    private fun identity(
        product: Product,
        products: Collection<Product>,
        purchases: Collection<Purchase>,
        judgements: Map<String, NameJudgement>,
        dictionary: NormalizationDictionary,
        resolver: ProductResolver,
    ): Identity? {
        val reason = resolver.resolve(product.id).reason
        if (reason is ProductResolver.Reason.Dictionary) {
            val attributes = AttributeExtractor.extract(product.displayName, null, dictionary).qualifiers
            return Identity(
                product,
                item = "c:${reason.canonicalId.value}",
                canonical = reason.canonicalId,
                attributes = KINDS.associateWith { kind -> attributes.filter { it.kind == kind }.map { it.value }.sorted().joinToString(",").ifEmpty { null } },
                fromDictionary = true,
                reason = null,
            )
        }
        if (reason != ProductResolver.Reason.Self) return null
        val request = JudgementRequest.of(product.id, products, purchases, dictionary) ?: return null
        val judgement = judgements[request.key]?.takeIf { it.isCurrent && it.status == NameJudgement.Status.DONE } ?: return null
        val item = judgement.item?.let(NameNormalizer::normalizedName)?.takeIf { it.isNotEmpty() } ?: return null
        if ((judgement.confidence ?: 0.0) < MIN_CONFIDENCE) return null
        val canonical = dictionary.lookup(item)?.takeIf { it.match != NormalizationDictionary.Match.DEFAULT }?.canonicalId
        return Identity(
            product,
            item = canonical?.let { "c:${it.value}" } ?: "n:$item",
            canonical = canonical,
            attributes = mapOf(
                "form" to judgement.form,
                "use" to judgement.use,
                "variant" to judgement.variant,
                "volume" to judgement.volume,
            ).mapValues { (_, value) -> value?.let(NameNormalizer::normalizedName)?.ifEmpty { null } },
            fromDictionary = false,
            reason = judgement.reason?.let { Reason(product.displayName, it) },
        )
    }

    private fun direction(a: Identity, b: Identity): kotlin.Pair<Identity, Identity> = when {
        a.fromDictionary -> b to a
        b.fromDictionary -> a to b
        a.product.createdAt <= b.product.createdAt -> b to a
        else -> a to b
    }
}
