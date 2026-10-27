package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.model.ProductId
import com.cheesecomer.cartmate.domain.product.ProductMerge
import kotlinx.coroutines.flow.Flow

/** 家族の商品と、名寄せの訂正（docs/design/suggestions.md「サジェストの補正」）。どれもオフラインでも行える */
interface ProductRepository {
    /** 家族の商品すべて（数百件の見込み）。画面に出すのは、自分に見える項目か購入の記録があるものだけにする */
    fun observeProducts(): Flow<List<Product>>

    /**
     * 統合・付け替え・解除（[ProductMerge.mergeChanges]、[ProductMerge.unmergeChanges]）を1つの書き込みで行う。
     * すべて反映されるか、何も反映されないかのどちらか。完了は待たない（オフラインでも画面を進めるため）
     */
    suspend fun applyMergeChanges(changes: List<ProductMerge.Change>)

    /**
     * 統合や解除がサーバーで拒否された（ほかのメンバーが先に統合関係を変えていた）。画面をまたいで知らせるため、
     * アプリ全体で1か所から受け取る
     */
    fun observeRejectedMergeChanges(): Flow<Unit>

    /**
     * 前回アプリを終了するまでに確定しなかった統合や解除を確かめる。送り終えたあとでサーバーの状態が期待どおりでなければ、
     * [observeRejectedMergeChanges] で知らせる（アプリを終了している間に拒否された場合）
     */
    suspend fun verifyPendingMergeChanges()

    /** 名寄せ辞書でまとめないようにする（「別の商品として扱う」）。false で辞書の名寄せに戻す */
    suspend fun setDictionaryOptOut(id: ProductId, optOut: Boolean)

    /** 時期が近い商品の提案に出さない（補完には出す）。false で戻す。家族で共有し、オフラインでも行える */
    suspend fun setSuggestionExcluded(id: ProductId, excluded: Boolean)
}
