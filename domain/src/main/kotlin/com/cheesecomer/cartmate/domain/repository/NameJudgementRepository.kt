package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.product.AiCandidates
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NameJudgement
import kotlinx.coroutines.flow.Flow

/** AI の名寄せの判定（docs/design/suggestions.md「AI による名寄せの候補」）。判定は関数が書き、家族で共有する */
interface NameJudgementRepository {
    /** 家族の判定すべて */
    fun observeJudgements(): Flow<List<NameJudgement>>

    /**
     * 関数に判定を頼む。オンラインでだけ行える。判定は [observeJudgements] に届く。
     * 失敗しても例外は投げない（アプリは辞書と購入履歴だけで動き続ける）
     */
    suspend fun requestJudgement(request: JudgementRequest, dictionaryVersion: String)
}

/** 「この候補を出さない」にした AI の候補の組。端末の中に、ユーザーと家族ごとに保存し、家族では共有しない */
interface AiDismissalRepository {
    fun observeDismissed(): Flow<Set<AiCandidates.Pair>>

    suspend fun dismiss(pair: AiCandidates.Pair)
}
