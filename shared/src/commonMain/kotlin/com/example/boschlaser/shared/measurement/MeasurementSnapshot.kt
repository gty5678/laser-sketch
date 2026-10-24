package com.example.boschlaser.shared.measurement

/** Newest-first IDs, including a bounded picker whose oldest record can fall out. */
fun isNewestMeasurementAdded(previousIds: List<String>?, currentIds: List<String>): Boolean {
    if (previousIds == null || currentIds.isEmpty() || currentIds.first() in previousIds) return false
    return when (currentIds.size) {
        previousIds.size + 1 -> currentIds.drop(1) == previousIds
        previousIds.size -> currentIds.drop(1) == previousIds.dropLast(1)
        else -> false
    }
}
