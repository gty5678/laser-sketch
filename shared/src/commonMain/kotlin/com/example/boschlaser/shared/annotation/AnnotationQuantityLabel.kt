package com.example.boschlaser.shared.annotation

import kotlin.math.pow

/** Labels remain independent of photo geometry; unit conversion preserves numeric quantities. */
object AnnotationQuantityLabel {
    data class Draft(val text: String, val unit: Int)
    /** Converts labels without inferring dimensions from photo geometry. */
    fun convert(text: String, from: Int, to: Int, units: List<String>): String? {
        if (from !in units.indices || to !in units.indices) return null
        if (from == to || text.isBlank()) return text
        fun exponent(unit: String): Int? = when (unit) {
            "mm", "mm²" -> 0
            "cm" -> 1
            "m" -> 3
            "cm²" -> 2
            "m²" -> 6
            else -> null
        }
        val value = text.trim()
        if (value.length > 4096) return null
        val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val shift = (exponent(units[from]) ?: return null) - (exponent(units[to]) ?: return null)
        val result = number * 10.0.pow(shift.toDouble())
        if (!result.isFinite()) return null
        if (result == 0.0) return "0"
        // Metric conversions shift a decimal point. Formatting a multiplied Double exposes binary tails.
        val parts = Regex("([+-]?)([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE]([+-]?[0-9]+))?").matchEntire(value) ?: return null
        val mantissa = parts.groupValues[2]
        val sourceExponent = parts.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val allDigits = mantissa.replace(".", "")
        val leadingZeros = allDigits.takeWhile { it == '0' }.length
        val digits = allDigits.drop(leadingZeros)
        val position = mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length
        val decimalPosition = position + sourceExponent + shift - leadingZeros
        val plain = when {
            decimalPosition <= 0 -> "0." + "0".repeat(-decimalPosition) + digits
            decimalPosition >= digits.length -> digits + "0".repeat(decimalPosition - digits.length)
            else -> digits.take(decimalPosition) + "." + digits.drop(decimalPosition)
        }
        val normalized = if ('.' in plain) plain.trimEnd('0').trimEnd('.') else plain
        return if (parts.groupValues[1] == "-") "-$normalized" else normalized
    }

    fun parse(label: String, units: List<String>): Draft {
        require(units.isNotEmpty())
        val parts = label.trim().split(" ")
        return Draft(parts.firstOrNull().orEmpty(), (parts.getOrNull(1)?.let(units::indexOf) ?: -1).coerceAtLeast(0))
    }
    fun format(text: String, unit: Int, units: List<String>): String {
        require(unit in units.indices)
        val value = text.trim()
        return if (value.isEmpty()) "" else "$value ${units[unit]}"
    }
}
