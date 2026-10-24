package com.example.boschlaser

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.shared.annotation.PhotoPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationNoticeInstrumentedTest {
    @get:org.junit.Rule val foregroundHost: org.junit.rules.TestRule = ForegroundTestHostRule()

    @Test fun creationMessagesCarryExplicitToastPolicyAndKeepOriginalPromptTiming() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val source = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                val view = ImageAnnotationView(context).apply { setBitmap(source); layout(0, 0, 400, 400) }
                val notices = mutableListOf<AnnotationNotice>()
                view.onMessage = { notices += it }
                fun tap(point: PhotoPoint) {
                    val screen = view.photoToScreen(point)
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(0, 0, action, screen.x, screen.y, 0)
                        try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
                    }
                }
                view.setMode(AnnotationMode.AREA)
                for (point in listOf(PhotoPoint(.2, .2), PhotoPoint(.8, .2), PhotoPoint(.8, .8))) tap(point)
                assertEquals(listOf(true, false, false), notices.map { it.showToast })
                assertEquals(listOf(context.getString(R.string.annotation_area_first_point_hint),
                    context.getString(R.string.annotation_area_second_point_hint),
                    context.getString(R.string.annotation_area_created_hint)), notices.map { it.text })
                notices.clear()
                view.setMode(AnnotationMode.ANGLE)
                for (point in listOf(PhotoPoint(.2, .2), PhotoPoint(.5, .5), PhotoPoint(.8, .2))) tap(point)
                assertEquals(listOf(true, true, true), notices.map { it.showToast })
                assertEquals(listOf(context.getString(R.string.annotation_angle_first_point_hint),
                    context.getString(R.string.annotation_angle_vertex_hint),
                    context.getString(R.string.annotation_angle_created_hint)), notices.map { it.text })
                notices.clear()
                view.setMode(AnnotationMode.TEXT)
                tap(PhotoPoint(.5, .5))
                assertEquals(listOf(AnnotationNotice(context.getString(R.string.annotation_text_created_hint))), notices)
                assertEquals(3, view.snapshotDocument().annotations.size)
            } finally { source.recycle() }
        }
    }
}
