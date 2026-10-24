package com.example.boschlaser.platform

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.AnnotationTextMetrics
import com.example.boschlaser.ui.AnnotationTextPainter

/** Native font metrics and glyph shaping only; label geometry belongs to shared UI. */
internal class AndroidAnnotationTextPainter : AnnotationTextPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    override fun measure(text: String, fontSizePx: Double): AnnotationTextMetrics {
        paint.textSize = fontSizePx.toFloat()
        val metrics = paint.fontMetrics
        return AnnotationTextMetrics(paint.measureText(text).toDouble(), metrics.ascent.toDouble(), metrics.descent.toDouble())
    }
    override fun draw(scope: DrawScope, text: String, fontSizePx: Double, colorArgb: Int, baselineCenter: Vec2) {
        paint.textSize = fontSizePx.toFloat()
        paint.color = colorArgb
        scope.drawIntoCanvas { it.nativeCanvas.drawText(text, baselineCenter.x.toFloat(), baselineCenter.y.toFloat(), paint) }
    }
}
