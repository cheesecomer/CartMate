package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.model.Purchase
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface PurchaseRepository {
    /** [since] 以降に記録された購入。無効化（Undo）されたものも含む。他のメンバーのプライベートな購入は含まない */
    fun observePurchases(since: Instant): Flow<List<Purchase>>
}
