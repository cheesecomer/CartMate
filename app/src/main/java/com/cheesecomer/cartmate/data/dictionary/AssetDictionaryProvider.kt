package com.cheesecomer.cartmate.data.dictionary

import android.content.Context
import android.util.Log
import com.cheesecomer.cartmate.domain.product.DictionarySource
import com.cheesecomer.cartmate.domain.product.NormalizationDictionary
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue

/**
 * アセットの dictionary/ にある名寄せ辞書を、起動後に別スレッドで読み込む（docs/design/architecture.md「名寄せ辞書」）。
 * 読み込み終わるまでは空の辞書を流す。読み込みに失敗しても、空の辞書のまま動く（名寄せをしないだけ）
 */
@Singleton
class AssetDictionaryProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionaryProvider {

    private val dictionary = MutableStateFlow(NormalizationDictionary.EMPTY)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { measureTimedValue { load(context) } }
                .onSuccess { (loaded, duration) ->
                    Log.i(TAG, "loaded ${loaded.version}: ${loaded.aliases.size} aliases in $duration")
                    dictionary.value = loaded
                }
                .onFailure { Log.e(TAG, "failed to load the dictionary", it) }
        }
    }

    override fun observeDictionary(): Flow<NormalizationDictionary> = dictionary.asStateFlow()

    companion object {
        private const val TAG = "AssetDictionaryProvider"
        private const val DIR = "dictionary"

        fun load(context: Context): NormalizationDictionary {
            fun read(name: String) = context.assets.open("$DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            return DictionarySource(
                version = read("VERSION"),
                items = read("items.csv"),
                aliases = read("aliases.csv"),
                cycleGroups = read("cycle_groups.csv"),
                neverMerge = read("never_merge.csv"),
                retiredIds = read("retired_ids.csv"),
                attributes = read("attributes.csv"),
            ).toDictionary()
        }
    }
}
