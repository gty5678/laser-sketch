package com.example.boschlaser.shared.annotation

/** Labels remain independent of photo geometry; unit conversion preserves numeric quantities. */
object AnnotationQuantityLabel {
    data class Draft(val text: String, val unit: Int)
    /** Converts labels without inferring dimensions from photo geometry. */
    fun convert(text: String, from: Int, to: Int, units: List<String>): String? {
        if (from !in units.indices || to !in units.indices) return null
        if (from == to || text.isBlank()) return text
        fun factor(unit: String): Double? = when (unit) {
            "mm", "mm²" -> 1.0
            "cm" -> 10.0
            "m" -> 1000.0
            "cm²" -> 100.0
            "m²" -> 1000000.0
            else -> null
        }
        val number = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val result = number * ((factor(units[from]) ?: return null) / (factor(units[to]) ?: return null))
        if (!result.isFinite()) return null
        return result.toString().removeSuffix(".0")
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
