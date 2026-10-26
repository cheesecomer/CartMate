package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import kotlinx.coroutines.flow.Flow

/** 家族の商品と、名寄せの訂正（docs/design/suggestions.md「サジェストの補正」）。どれもオフラインでも行える */
interface ProductRepository {
    /** 家族の商品すべて（数百件の見込み）。画面に出すのは、自分に見える項目か購入の記録があるものだけにする */
    fun observeProducts(): Flow<List<Product>>

    /** [from] を [into] に統合する。[into] は統合先までたどったもの（統合されていない商品）を渡す */
    suspend fun merge(from: ProductId, into: ProductId)

    suspend fun unmerge(id: ProductId)

    /** 名寄せ辞書でまとめないようにする（「別の商品として扱う」）。false で辞書の名寄せに戻す */
    suspend fun setDictionaryOptOut(id: ProductId, optOut: Boolean)
}
