package com.example.boschlaser

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchViewSettingsInstrumentedTest {
    @Test
    fun magnifierZoomAcceptsOnlyConfiguredRange() {
        if (Looper.myLooper() == null) Looper.prepare()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SketchView(context)

        assertEquals(SketchView.DEFAULT_MAGNIFIER_ZOOM, view.magnifierZoom(), 0f)
        assertTrue(view.setMagnifierZoom(4.5f))
        assertEquals(4.5f, view.magnifierZoom(), 0f)
        assertFalse(view.setMagnifierZoom(SketchView.MIN_MAGNIFIER_ZOOM - .1f))
        assertFalse(view.setMagnifierZoom(SketchView.MAX_MAGNIFIER_ZOOM + .1f))
        assertEquals(4.5f, view.magnifierZoom(), 0f)
    }
}
