package com.example.boschlaser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal data class WheelColor(val hue: Float, val saturation: Float, val brightness: Float) {
    fun argb(): Int {
        val h = ((hue % 360f + 360f) % 360f) / 60f
        val c = brightness * saturation
        val x = c * (1f - abs(h % 2f - 1f))
        val m = brightness - c
        val rgb = when (h.toInt()) {
            0 -> listOf(c, x, 0f)
            1 -> listOf(x, c, 0f)
            2 -> listOf(0f, c, x)
            3 -> listOf(0f, x, c)
            4 -> listOf(x, 0f, c)
            else -> listOf(c, 0f, x)
        }.map { ((it + m) * 255f).roundToInt().coerceIn(0, 255) }
        return 0xFF000000.toInt() or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
    }
}

internal fun wheelColor(argb: Int): WheelColor {
    val r = ((argb ushr 16) and 255) / 255f
    val g = ((argb ushr 8) and 255) / 255f
    val b = (argb and 255) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return WheelColor((hue + 360f) % 360f, if (max == 0f) 0f else delta / max, max)
}

/** Pointer positions outside the disk clamp to its edge during a drag. */
internal fun wheelPosition(x: Float, y: Float, radius: Float, brightness: Float): WheelColor {
    if (radius <= 0f) return WheelColor(0f, 0f, brightness)
    return WheelColor((atan2(y, x) * 180f / PI.toFloat() + 360f) % 360f,
        (hypot(x, y) / radius).coerceIn(0f, 1f), brightness)
}

@Composable
internal fun AnnotationColorWheel(color: WheelColor, wheelLabel: String, brightnessLabel: String,
    onColor: (WheelColor) -> Unit) {
    val latestColor by rememberUpdatedState(color)
    val latestChange by rememberUpdatedState(onColor)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).testTag("annotation-color-wheel")
        .semantics { contentDescription = wheelLabel }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f - 10.dp.toPx()
                if ((down.position - center).getDistance() <= radius) {
                    fun select(position: Offset) {
                        val point = position - center
                        latestChange(wheelPosition(point.x, point.y, radius, latestColor.brightness))
                    }
                    down.consume()
                    select(down.position)
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        select(change.position)
                        change.consume()
                    } while (change.pressed)
                }
            }
        }) {
        val radius = size.minDimension / 2f - 10.dp.toPx()
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan,
            Color.Blue, Color.Magenta, Color.Red)), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - color.brightness), radius)
        val angle = color.hue * PI.toFloat() / 180f
        val marker = center + Offset(cos(angle), sin(angle)) * (radius * color.saturation)
        drawCircle(Color.Black, 7.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 7.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
    }
    Text(brightnessLabel, style = MaterialTheme.typography.labelLarge)
    Slider(color.brightness, { onColor(color.copy(brightness = it)) },
        modifier = Modifier.testTag("annotation-color-brightness").semantics { contentDescription = brightnessLabel })
}
