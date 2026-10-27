package com.cheesecomer.cartmate.data.dictionary

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.domain.product.DictionarySource
import com.cheesecomer.cartmate.domain.text.NameNormalizer
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * 名寄せ辞書の実機での計測（docs/design/suggestions.md「計測」）。通常のテストでは飛ばし、
 * `-Pandroid.testInstrumentationRunnerArguments.dictionaryBenchmark=true` を付けたときだけ流す。
 * 結果は Logcat（DictionaryBenchmark）に出す
 */
@RunWith(AndroidJUnit4::class)
class DictionaryBenchmark {

    @Before
    fun onlyWhenRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dictionaryBenchmark") == "true")
    }

    @Test
    fun 同梱の辞書の読み込み() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AssetDictionaryProvider.load(context)
        val (dictionary, duration) = measureTimedValue { AssetDictionaryProvider.load(context) }
        log("bundled ${dictionary.version}: ${dictionary.aliases.size} aliases, load $duration")
    }

    @Test
    fun 合成した辞書の読み込みと検索() {
        for (size in listOf(10_000, 50_000, 100_000)) measure(size)
    }

    private fun measure(aliasCount: Int) {
        val source = synthetic(aliasCount)
        val csvBytes = (source.items + source.aliases).toByteArray().size
        // 1回目は JIT などの影響が大きいので、2回目を計る
        source.toDictionary()
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val (dictionary, parse) = measureTimedValue { source.toDictionary() }
        System.gc()
        val retained = runtime.totalMemory() - runtime.freeMemory() - before

        val random = Random(1)
        val keys = dictionary.aliases.keys.toList()
        val queries = List(100_000) { if (random.nextBoolean()) keys[random.nextInt(keys.size)] else NameNormalizer.normalizedName("ない名前$it") }
        var hits = 0
        val lookups = measureTime { for (q in queries) if (dictionary.lookup(q) != null) hits++ }
        val normalizeAndLookup = measureTime { for (q in queries.take(10_000)) dictionary.lookup(NameNormalizer.normalizedName(q)) }

        log(
            "aliases=$aliasCount csv=${csvBytes / 1024}KiB parse=$parse retained≈${retained / 1024}KiB " +
                "lookup100k=$lookups (hits=$hits) normalize+lookup10k=$normalizeAndLookup",
        )
        check(dictionary.aliases.isNotEmpty())
    }

    /** 実データに近い文字の分布（かな・カナ・食品の漢字）で、[aliasCount] 件の別名と、その5分の1の品目を作る */
    private fun synthetic(aliasCount: Int): DictionarySource {
        val random = Random(42)
        val pool = (('ぁ'..'ゔ') + ('ァ'..'ヶ') + "卵醤油砂糖塩味噌酢米食牛乳豆腐納大根人参玉葱肉鶏豚鮭海苔洗剤柔軟歯磨粉紙袋缶詰麺茶菓子".toList())
        fun name(n: Int) = String(CharArray(n) { pool[random.nextInt(pool.size)] })
        val itemCount = aliasCount / 5
        val items = StringBuilder("canonical_id,name,source\n")
        val used = HashSet<String>()
        repeat(itemCount) { i ->
            var n: String
            do n = name(3 + random.nextInt(4)) while (!used.add(NameNormalizer.normalizedName(n)))
            items.append("i$i,$n,bench\n")
        }
        val aliases = StringBuilder("alias,canonical_id,match,source\n")
        var added = 0
        while (added < aliasCount - itemCount) {
            val n = name(3 + random.nextInt(5))
            if (!used.add(NameNormalizer.normalizedName(n))) continue
            aliases.append("$n,i${random.nextInt(itemCount)},prefix,bench\n")
            added++
        }
        return DictionarySource("bench", items.toString(), aliases.toString(), "a,b\n", "a,b,c\n", "a,b,c\n")
    }

    private fun log(message: String) {
        Log.i("DictionaryBenchmark", message)
        println("DictionaryBenchmark: $message")
    }

}
