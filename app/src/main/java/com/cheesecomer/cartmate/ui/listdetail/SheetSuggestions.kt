package com.cheesecomer.cartmate.ui.listdetail

import com.cheesecomer.cartmate.domain.input.Suggestion
import com.cheesecomer.cartmate.domain.model.ListId
import com.cheesecomer.cartmate.domain.suggest.EarlyPurchaseNotice
import com.cheesecomer.cartmate.domain.suggest.SuggestionEngine

/** サジェストのシートの候補（docs/design/ui.md「商品追加欄とサジェストのシート」） */
sealed interface SheetSuggestions {
    /** 読み込み中。何も出さない */
    data object Loading : SheetSuggestions

    /** 時期が近い商品を出せない（未入力で、関連する履歴がないか候補がない）。「商品名を入力すると候補が出ます」 */
    data object Hint : SheetSuggestions

    /** 未入力なら時期が近い商品、入力中なら補完。入力中で候補がなければ空 */
    data class Items(val rows: List<SuggestionRow>) : SheetSuggestions

    companion object {
        /**
         * [engine] から、入力中の [text] と [listName] のリストの候補を作る。[listNames] は「追加済み（スーパー）」に出すリストの名前で、
         * [listId]（開いているリスト）を先頭にする
         */
        fun of(
            engine: SuggestionEngine,
            text: String,
            listName: String,
            listId: ListId? = null,
            listNames: Map<ListId, String> = emptyMap(),
        ): SheetSuggestions {
            if (text.isBlank()) {
                val due = engine.due(listName) as? SuggestionEngine.DueResult.Suggestions ?: return Hint
                if (due.items.isEmpty()) return Hint
                return Items(due.items.map { item ->
                    SuggestionRow(
                        Suggestion(item.product.displayName, item.lastQuantity, item.lastUnit, item.lastMemo, productId = item.product.id),
                        SuggestionNote.Due(item.estimate.typicalIntervalDays, item.daysSinceLast),
                        // 同じ名前でも備考の属性で分けて数えた候補は、「緑茶（茶葉）」のように見分ける
                        label = if (item.labels.isEmpty()) item.product.displayName else "${item.product.displayName}（${item.labels.joinToString("・")}）",
                    )
                })
            }
            return Items(engine.complete(text, listName).map { completion ->
                SuggestionRow(
                    Suggestion(completion.product.displayName, completion.lastQuantity, completion.lastUnit, productId = completion.product.id),
                    SuggestionNote.Completion(
                        alreadyAdded = completion.alreadyAdded,
                        early = completion.early,
                        addedLists = completion.addedListIds
                            .sortedWith(compareBy<ListId> { it != listId }.thenBy { listNames[it].orEmpty() })
                            .mapNotNull { listNames[it] },
                    ),
                )
            })
        }
    }
}

data class SuggestionRow(
    val suggestion: Suggestion,
    val note: SuggestionNote,
    /** 表示する名前 */
    val label: String = suggestion.name,
)

/** 候補の名前の下に出す補足 */
sealed interface SuggestionNote {
    /** 「約14日ごと・最後は16日前」 */
    data class Due(val intervalDays: Int, val daysSinceLast: Long) : SuggestionNote

    /**
     * 「追加済み（スーパー）」と、早すぎる再購入の「前回 10/18 に購入・いつもは約14日ごと」。
     * [addedLists] は未購入の項目があるリストの名前（開いているリストが先頭）
     */
    data class Completion(
        val alreadyAdded: Boolean,
        val early: EarlyPurchaseNotice?,
        val addedLists: List<String> = emptyList(),
    ) : SuggestionNote
}
