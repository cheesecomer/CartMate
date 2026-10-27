package com.cheesecomer.cartmate.domain.product

/** 辞書の品目の ID。一度使った ID は再利用しない */
@JvmInline
value class CanonicalId(val value: String)

/** 周期のグループの ID。品目とは別の名前空間 */
@JvmInline
value class CycleGroupId(val value: String)

/**
 * アプリに同梱する名寄せ辞書（docs/design/suggestions.md「名寄せ辞書」）。照合は端末の中だけで行う
 */
data class NormalizationDictionary(
    val version: String,
    /** 品目の表示名 */
    val canonicalNames: Map<CanonicalId, String>,
    /** 別名。キーは NameNormalizer.normalizedName で正規化した名前 */
    val aliases: Map<String, Alias>,
    /** 周期だけ合わせて数えるグループ */
    val cycleGroups: Map<CanonicalId, CycleGroupId> = emptyMap(),
    /** 自動では同じ商品にしない品目の組（順不同） */
    val neverMerge: Set<Set<CanonicalId>> = emptySet(),
    /** 商品名・備考から属性を取り出すための語（フェーズ4で使う）。キーは正規化した語 */
    val attributes: Map<String, Attribute> = emptyMap(),
) {
    /** 属性の語。[value] は、kind が ITEM なら品目の ID、それ以外は属性の値（「茶葉」「詰め替え」など） */
    data class Attribute(val kind: AttributeKind, val value: String)

    enum class AttributeKind {
        /** 品目を指す語（「麦茶」）。既定の同義語（[Match.DEFAULT]）を上書きする */
        ITEM,

        /** 形態（茶葉、ティーバッグ、飲料、粉末） */
        FORM,

        /** 用途（燃えるゴミ用、資源ゴミ用） */
        USE,

        /** 種類（詰め替え、本体） */
        VARIANT,

        /** 判定に使わない語（特売なら、いつもの、安いやつ） */
        IGNORE,
    }

    data class Alias(val canonicalId: CanonicalId, val match: Match)

    enum class Match {
        /** 完全一致だけ */
        EXACT,

        /** 購入履歴の前方一致の照合にも使う */
        PREFIX,

        /**
         * 既定の同義語（「お茶」→ 緑茶）。商品名や備考に品目の語（「麦茶」）があればそちらを優先するので、確定ではない。
         * 備考を使う判定（フェーズ4）を作るまでは、自動の名寄せには使わない
         */
        DEFAULT,
    }

    /** [normalizedName] に完全一致する別名 */
    fun lookup(normalizedName: String): Alias? = aliases[normalizedName]

    /** [a] と [b] を自動で同じ商品にしてよいか */
    fun mayMerge(a: CanonicalId, b: CanonicalId): Boolean = a == b || setOf(a, b) !in neverMerge

    companion object {
        val EMPTY = NormalizationDictionary(version = "empty", canonicalNames = emptyMap(), aliases = emptyMap())
    }
}
