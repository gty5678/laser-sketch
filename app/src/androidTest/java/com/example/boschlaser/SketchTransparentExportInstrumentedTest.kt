package com.example.boschlaser

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchTransparentExportInstrumentedTest {
    @Test fun emptyPngExportIsTransparentAndPreviewStaysWhite() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = SketchView(InstrumentationRegistry.getInstrumentation().targetContext)
            val transparent = view.exportBitmap(320, 240, transparent = true)
            try {
                for (y in 0 until transparent.height) for (x in 0 until transparent.width) {
                    assertEquals(0, Color.alpha(transparent.getPixel(x, y)))
                }
            } finally { transparent.recycle() }
            val preview = view.exportPreviewBitmap()
            try { assertEquals(Color.WHITE, preview.getPixel(0, 0)) } finally { preview.recycle() }
        }
    }
}
