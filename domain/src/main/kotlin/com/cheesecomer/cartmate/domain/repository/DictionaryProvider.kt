package com.cheesecomer.cartmate.domain.repository

import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import kotlinx.coroutines.flow.Flow

/** 同梱の名寄せ辞書。読み込み終わるまでは空の辞書を流す */
fun interface DictionaryProvider {
    fun observeDictionary(): Flow<NormalizationDictionary>
}
