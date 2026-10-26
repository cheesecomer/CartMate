package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.model.Item
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.domain.model.Visibility
import com.cheesecomer.cartmate.domain.visibility.VisibilityDefaults

/**
 * 商品詳細入力画面の入力内容（docs/design/ui.md「商品詳細入力画面」）。新規の追加と、未購入の項目の編集で使う
 */
data class ItemEntry(
    /** 編集する項目。null なら新規 */
    val editing: ItemId?,
    val name: String,
    val quantity: String,
    val unit: String,
    val memo: String,
    val visibility: Visibility,
    /** 公開範囲を選べるか。共有のリストへの新規追加だけ選べる */
    val visibilitySelectable: Boolean,
) {
    /** 数量。空、0以下、数値でないときは null */
    val quantityValue: Double? get() = (QuantityInput.parse(quantity) as? QuantityInput.Valid)?.value

    val canConfirm: Boolean get() = name.isNotBlank() && quantityValue != null

    fun increment(): ItemEntry = copy(quantity = formatQuantity(minOf((quantityValue ?: 0.0) + 1, Item.MAX_QUANTITY)))

    /** 1未満にはしない */
    fun decrement(): ItemEntry = copy(quantity = formatQuantity(maxOf((quantityValue ?: 1.0) - 1, 1.0)))

    fun withVisibility(visibility: Visibility): ItemEntry = if (visibilitySelectable) copy(visibility = visibility) else this

    /** 確定する値。確定できない入力なら null */
    fun confirm(): Values? {
        val quantity = quantityValue ?: return null
        if (name.isBlank()) return null
        return Values(name.trim(), quantity, normalizeUnit(unit), memo.trim().ifEmpty { null }, visibility)
    }

    data class Values(
        val name: String,
        val quantity: Double,
        val unit: String?,
        val memo: String?,
        val visibility: Visibility,
    )

    companion object {
        /** 新規。[quantity] と [unit] は、候補から来た場合の前回の値か、既定の 1・「個」 */
        fun forNew(
            name: String,
            quantity: Double?,
            unit: String?,
            listVisibility: Visibility,
            remembered: Visibility?,
        ) = ItemEntry(
            editing = null,
            name = name,
            quantity = formatQuantity(quantity ?: AddItemReducer.DEFAULT_QUANTITY),
            unit = unit.orEmpty(),
            memo = "",
            visibility = VisibilityDefaults.initial(listVisibility, remembered),
            visibilitySelectable = VisibilityDefaults.selectable(listVisibility),
        )

        /** 未購入の項目の編集。公開範囲は表示するだけで変えられない */
        fun forEdit(item: Item) = ItemEntry(
            editing = item.id,
            name = item.inputName,
            quantity = item.quantity?.let(::formatQuantity).orEmpty(),
            unit = item.unit.orEmpty(),
            memo = item.memo.orEmpty(),
            visibility = item.visibility,
            visibilitySelectable = false,
        )
    }
}
