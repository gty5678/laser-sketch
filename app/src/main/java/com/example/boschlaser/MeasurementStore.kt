package com.example.boschlaser

/** In-process measurement bridge shared by the BLE and photo annotation screens. */
object MeasurementStore {
    private val values = mutableListOf<Float>()
    private val listeners = mutableSetOf<(List<Float>) -> Unit>()

    fun add(millimeters: Float) {
        val snapshot = synchronized(this) {
            values += millimeters
            if (values.size > 200) values.removeAt(0)
            values.asReversed().toList()
        }
        notifyListeners(snapshot)
    }

    fun clear() {
        synchronized(this) { values.clear() }
        notifyListeners(emptyList())
    }

    fun replaceAll(millimeters: List<Float>) {
        val snapshot = synchronized(this) {
            values.clear()
            values += millimeters.takeLast(200)
            values.asReversed().toList()
        }
        notifyListeners(snapshot)
    }

    @Synchronized
    fun newestFirst(): List<Float> = values.asReversed().toList()

    fun addListener(listener: (List<Float>) -> Unit) {
        val snapshot = synchronized(this) {
            listeners += listener
            values.asReversed().toList()
        }
        listener(snapshot)
    }

    fun removeListener(listener: (List<Float>) -> Unit) {
        synchronized(this) { listeners -= listener }
    }

    private fun notifyListeners(snapshot: List<Float>) {
        val currentListeners = synchronized(this) { listeners.toList() }
        currentListeners.forEach { it(snapshot) }
    }
}
