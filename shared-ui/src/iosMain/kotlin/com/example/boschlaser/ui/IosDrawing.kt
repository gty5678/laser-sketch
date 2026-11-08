@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import kotlinx.coroutines.ensureActive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import platform.UIKit.UIImage
import platform.Foundation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun millimeters(value: Double): String = label("length_mm").replace("%s", decimal(value))
internal fun decimal(value: Double): String = formatNumber(value, 1, 0)
internal fun fixedDecimal(value: Double, places: Int): String = formatNumber(value, places, places)
private fun formatNumber(value: Double, maximum: Int, minimum: Int): String {
    require(value.isFinite())
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale("en_US_POSIX")
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = false
        maximumFractionDigits = maximum.toULong()
        minimumFractionDigits = minimum.toULong()
        roundingMode = NSNumberFormatterRoundHalfUp
    }
    return checkNotNull(formatter.stringFromNumber(NSNumber(double = value)))
}

@Composable
internal fun iosSketchStyle(): SketchSceneStyle {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        val controls = ControlDrawingStyle(colors.primary, colors.primary.copy(alpha = .2f))
        SketchSceneStyle(GridDrawingStyle(colors.outlineVariant, 1f, colors.outline, 1f),
            WallDrawingStyle(androidx.compose.ui.graphics.Color(0xFF555555), colors.onSurface, 2f),
            WallDrawingStyle(colors.primaryContainer, colors.primary, 2f),
            OpeningDrawingStyle(colors.onSurface, 2f, colors.onSurface, 2f),
            OpeningDrawingStyle(colors.primary, 3f, colors.primary, 2f),
            ColumnDrawingStyle(colors.secondaryContainer, colors.primary, 2f, colors.primary, colors.primaryContainer, 8f),
            WallControlDrawingStyle(colors.outline, 1f, null, controls), WallControlDrawingStyle(colors.primary, 2f, null, controls),
            TextStyle(color = colors.onSurface, fontSize = 12.sp), colors.primary, 2f, 20.0,
            invalidDimensionLabel = label("invalid_dimension"), fixedReferenceLabel = label("fixed_reference"), movingReferenceLabel = label("moving_reference"))
    }
}

internal fun decodePhoto(bytes: ByteArray, maximumDimension: Int = 4096): ImageBitmap {
    val photo = checkNotNull(UIImage.imageWithData(bytes.nsData()))
    return Image.makeFromEncoded(normalizedPhoto(photo, maximumDimension.toDouble())).toComposeImageBitmap()
}
internal fun png(bitmap: ImageBitmap): ByteArray = checkNotNull(Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes

@Composable
internal fun IosProjectThumbnail(project: Project, store: IosProjectStore) {
    val style = iosSketchStyle()
    val measurer = rememberTextMeasurer()
    val photo by produceState<ImageBitmap?>(null, project.manifest.projectId, project.manifest.updatedAt) {
        value = withContext(Dispatchers.Default) {
            project.annotations.keys.firstOrNull()?.let { id -> runCatching { decodePhoto(store.asset(project, id), 512) }.getOrNull() }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.White)
        val thumbnail = photo
        if (thumbnail != null && project.sketch.walls.isEmpty() && project.sketch.columns.isEmpty()) {
            drawImage(thumbnail, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        } else {
            val dimensions = Vec2(size.width.toDouble(), size.height.toDouble())
            drawSketchScene(project.sketch, EditorState(), PlacementSettings(), sketchExportViewport(project.sketch, dimensions),
                dimensions, style, SketchSceneOptions(100.0, showGrid = false, rotationHandleOffsetPx = 0.0), measurer, ::millimeters)
        }
    }
}

internal fun sketchPng(document: SketchDocument, style: SketchSceneStyle, measurer: TextMeasurer): ByteArray {
    val bitmap = ImageBitmap(2000, 2000)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(2000f, 2000f)) {
        drawRect(Color.White)
        val sizePx = Vec2(2000.0, 2000.0)
        drawSketchScene(document, EditorState(), PlacementSettings(), sketchExportViewport(document, sizePx), sizePx, style,
            SketchSceneOptions(100.0, showGrid = false, showSelection = false, rotationHandleOffsetPx = 0.0), measurer, ::millimeters)
    }
    return png(bitmap)
}

@Composable
internal fun rememberIosTextPainter(): AnnotationTextPainter {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val density = LocalDensity.current
    return remember(measurer, density) { object : AnnotationTextPainter {
        private fun layout(text: String, size: Double) = measurer.measure(text.replace('\n', ' ').replace('\r', ' '),
            TextStyle(fontSize = (size / density.density / density.fontScale).sp, fontWeight = FontWeight.Bold), softWrap = false, maxLines = 1)
        override fun measure(text: String, fontSizePx: Double): AnnotationTextMetrics {
            val result = layout(text, fontSizePx)
            return AnnotationTextMetrics(result.size.width.toDouble(), -result.firstBaseline.toDouble(), result.size.height - result.firstBaseline.toDouble())
        }
        override fun draw(scope: DrawScope, text: String, fontSizePx: Double, colorArgb: Int, baselineCenter: Vec2) {
            val result = layout(text, fontSizePx)
            with(scope) { drawText(result, Color(colorArgb), topLeft = Offset((baselineCenter.x - result.size.width / 2).toFloat(),
                (baselineCenter.y - result.firstBaseline).toFloat())) }
        }
    } }
}

internal suspend fun correctedIosPhoto(image: androidx.compose.ui.graphics.ImageBitmap, settings: com.example.boschlaser.shared.annotation.PhotoCorrection, maximum: Int): androidx.compose.ui.graphics.ImageBitmap {
    val context = kotlinx.coroutines.currentCoroutineContext()
    val pixels = IntArray(image.width * image.height)
    image.readPixels(pixels)
    val result = com.example.boschlaser.shared.annotation.correctPhoto(pixels, image.width, image.height, settings, maximum, checkCancelled = { context.ensureActive() })
    val bytes = ByteArray(result.pixels.size * 4)
    result.pixels.forEachIndexed { index, color ->
        bytes[index * 4] = (color ushr 16).toByte()
        bytes[index * 4 + 1] = (color ushr 8).toByte()
        bytes[index * 4 + 2] = color.toByte()
        bytes[index * 4 + 3] = 255.toByte()
    }
    return org.jetbrains.skia.Image.makeRaster(org.jetbrains.skia.ImageInfo(result.width, result.height,
        org.jetbrains.skia.ColorType.RGBA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE), bytes, result.width * 4).toComposeImageBitmap()
}
