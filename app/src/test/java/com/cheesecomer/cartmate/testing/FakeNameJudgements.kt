package com.cheesecomer.cartmate.testing

import com.cheesecomer.cartmate.domain.model.Product
import com.cheesecomer.cartmate.domain.product.AiCandidates
import com.cheesecomer.cartmate.domain.product.JudgementKey
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NameJudgement
import com.cheesecomer.cartmate.domain.repository.AiDismissalRepository
import com.cheesecomer.cartmate.domain.repository.NameJudgementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** 関数の代わりに、頼まれた判定を記録する。判定は [judgements] に置く */
class FakeNameJudgementRepository : NameJudgementRepository {
    val judgements = MutableStateFlow<List<NameJudgement>>(emptyList())
    val requests = MutableStateFlow<List<JudgementRequest>>(emptyList())

    override fun observeJudgements(): Flow<List<NameJudgement>> = judgements

    override suspend fun requestJudgement(request: JudgementRequest, dictionaryVersion: String) {
        requests.update { it + request }
    }

    /** [product] の名前だけの判定（AI の品目 [item]）を置く */
    fun judge(product: Product, item: String, reason: String = "${product.displayName}は$item") {
        val judgement = NameJudgement(
            key = JudgementKey.of(product.normalizedName, emptyMap()),
            normalizedName = product.normalizedName,
            qualifiers = emptyMap(),
            status = NameJudgement.Status.DONE,
            judgeVersion = NameJudgement.CURRENT_VERSION,
            requestedAt = Instant.EPOCH,
            judgedAt = Instant.EPOCH,
            item = item,
            confidence = 0.9,
            reason = reason,
        )
        judgements.update { it + judgement }
    }
}

class FakeAiDismissalRepository : AiDismissalRepository {
    val dismissed = MutableStateFlow<Set<AiCandidates.Pair>>(emptySet())

    override fun observeDismissed(): Flow<Set<AiCandidates.Pair>> = dismissed

    override suspend fun dismiss(pair: AiCandidates.Pair) = dismissed.update { it + pair }
}
