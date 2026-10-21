package com.cheesecomer.cartmate.domain.input

import com.cheesecomer.cartmate.domain.model.Item
import java.math.BigDecimal
import java.text.Normalizer

sealed interface QuantityInput {
    /** 空欄なら [value] は null（未指定） */
    data class Valid(val value: Double?) : QuantityInput
    data object Invalid : QuantityInput

    companion object {
        fun parse(text: String): QuantityInput {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
            if (normalized.isEmpty()) return Valid(null)
            val value = normalized.toDoubleOrNull() ?: return Invalid
            return if (value.isFinite() && value > 0 && value <= Item.MAX_QUANTITY) Valid(value) else Invalid
        }
    }
}

/** 1.0 → "1"、1.50 → "1.5" */
fun formatQuantity(value: Double): String =
    BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

/** 前後の空白を除き、空なら null（未指定） */
fun normalizeUnit(text: String): String? = text.trim().ifEmpty { null }
