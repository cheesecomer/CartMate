package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.Purchase
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface PurchaseRepository {
    /** [since] 以降に記録された購入。無効化（Undo）されたものも含む。他のメンバーのプライベートな購入は含まない */
    fun observePurchases(since: Instant): Flow<List<Purchase>>

    /**
     * 購入の記録を「予測に使わない」にする（false で戻す）。記録は消さない。家族で共有し、オフラインでも行える
     * （docs/requirements.md §4.1）。共有の記録は家族の誰でも、プライベートの記録は本人だけが切り替えられる
     */
    suspend fun setExcludedFromPrediction(id: ItemId, excluded: Boolean)
}
