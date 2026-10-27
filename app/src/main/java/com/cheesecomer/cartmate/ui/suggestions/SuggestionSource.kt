package com.cheesecomer.cartmate.ui.suggestions

import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import com.cheesecomer.cartmate.domain.repository.ItemRepository
import com.cheesecomer.cartmate.domain.repository.ProductRepository
import com.cheesecomer.cartmate.domain.repository.PurchaseRepository
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * 予測とサジェストの入力（docs/design/suggestions.md「計算の実行場所」）。商品・直近365日の購入の記録・家族の未購入の項目・
 * 名寄せ辞書をまとめ、どれかが変わったら [SuggestionEngine] を作り直す。どれも自分に見えるものだけを購読している
 */
class SuggestionSource @Inject constructor(
    private val productRepository: ProductRepository,
    private val itemRepository: ItemRepository,
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
    private val dictionaryProvider: DictionaryProvider,
) {
    fun observe(): Flow<SuggestionEngine> = combine(
        productRepository.observeProducts(),
        itemRepository.observeOpenItems(),
        purchaseRepository.observePurchases(since = clock.instant().minus(PURCHASE_WINDOW)),
        dictionaryProvider.observeDictionary(),
    ) { products, openItems, purchases, dictionary ->
        SuggestionEngine(products, purchases, openItems, dictionary, LocalDate.now(clock), clock.zone)
    }.flowOn(Dispatchers.Default)

    private companion object {
        /** 購入の記録は直近365日分を使う（docs/design/data-model.md「購入履歴の取得範囲」） */
        val PURCHASE_WINDOW: Duration = Duration.ofDays(365)
    }
}
