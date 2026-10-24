package com.example.boschlaser.shared.annotation

/** Labels remain user text; selecting a unit never converts geometry or infers area. */
object AnnotationQuantityLabel {
    data class Draft(val text: String, val unit: Int)
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
