package com.example.boschlaser.shared.annotation

import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.*

/** Centered projective controls; no crop rectangle is selected. */
data class PhotoCorrection(val horizontal: Double = 0.0, val vertical: Double = 0.0, val lens: Double = 0.0, val rotation: Double = 0.0) {
    fun valid(): Boolean = listOf(horizontal, vertical, lens).all { it.isFinite() && it in -0.3..0.3 } && rotation.isFinite() && rotation in -45.0..45.0

    fun forward(point: Vec2, aspect: Double = 1.0): Vec2 {
        val x = point.x * 2 - 1; val y = point.y * 2 - 1
        val radial = 1 + lens * 0.4 * (x * x + y * y)
        val dx = x * radial; val dy = y * radial
        val z = 1 + horizontal * dx + vertical * dy
        val angle = rotation * PI / 180
        val px = dx / z; val py = dy / z
        return Vec2(px * cos(angle) - py * sin(angle) / aspect, px * sin(angle) * aspect + py * cos(angle))
    }
}

/** A conservative enclosing rectangle includes curved lens edges, even with both perspective controls. */
class PhotoCorrectionMapping(private val correction: PhotoCorrection, private val aspect: Double = 1.0) {
    init { require(correction.valid() && aspect.isFinite() && aspect > 0) }
    private val cosine = cos(correction.rotation * PI / 180)
    private val sine = sin(correction.rotation * PI / 180)
    private val k = correction.lens * 0.4
    private val maximumRadius = sqrt(2.0) * (1 + 2 * k)
    // Cache radial inversion once, instead of 24 binary-search iterations per output pixel.
    private val radialScales = if (k == 0.0) null else DoubleArray(4097) { index ->
        val radius = maximumRadius * index / 4096
        var low = 0.0; var high = sqrt(2.0)
        repeat(32) {
            val mid = (low + high) / 2
            if (mid * (1 + k * mid * mid) < radius) low = mid else high = mid
        }
        if (index == 0) 1.0 else (low + high) / (2 * radius)
    }
    private val extent = max(1.0, 1 + correction.lens * 0.8)
    private val bounds = listOf(-extent to -extent, extent to -extent, extent to extent, -extent to extent).map { (x, y) ->
        val z = 1 + correction.horizontal * x + correction.vertical * y
        val px = x / z; val py = y / z
        Vec2(px * cosine - py * sine / aspect, px * sine * aspect + py * cosine)
    }
    val left = bounds.minOf { it.x }
    val top = bounds.minOf { it.y }
    val width = bounds.maxOf { it.x } - left
    val height = bounds.maxOf { it.y } - top

    fun source(u: Double, v: Double): Vec2 {
        val output = DoubleArray(2)
        sourceInto(u, v, output)
        return Vec2(output[0], output[1])
    }

    internal fun sourceInto(u: Double, v: Double, output: DoubleArray) {
        val rotatedX = left + u * width; val rotatedY = top + v * height
        val x = rotatedX * cosine + rotatedY * sine / aspect
        val y = -rotatedX * sine * aspect + rotatedY * cosine
        val z = 1 - correction.horizontal * x - correction.vertical * y
        output[0] = -1.0; output[1] = -1.0
        if (z <= 1e-9) return
        val dx = x / z; val dy = y / z
        var scale = 1.0
        val table = radialScales
        if (table != null) {
            val radius = hypot(dx, dy)
            if (radius > maximumRadius + 1e-9) return
            val position = (radius / maximumRadius * 4096).coerceIn(0.0, 4096.0)
            val index = min(position.toInt(), 4095)
            val fraction = position - index
            scale = table[index] + (table[index + 1] - table[index]) * fraction
        }
        output[0] = (dx * scale + 1) / 2
        output[1] = (dy * scale + 1) / 2
    }
}

data class CorrectedPixels(val width: Int, val height: Int, val pixels: IntArray)

fun correctPhoto(pixels: IntArray, width: Int, height: Int, correction: PhotoCorrection, maximum: Int = 3000, checkCancelled: () -> Unit = {}): CorrectedPixels {
    require(width > 1 && height > 1 && pixels.size == width * height && maximum > 1 && correction.valid())
    checkCancelled()
    if (correction == PhotoCorrection() && maximum >= max(width, height)) {
        return CorrectedPixels(width, height, pixels.copyOf())
    }
    val mapping = PhotoCorrectionMapping(correction, width.toDouble() / height)
    val w = mapping.width * width / 2
    val h = mapping.height * height / 2
    val scale = min(1.0, maximum / max(w, h))
    val ow = max(2, (w * scale).roundToInt()); val oh = max(2, (h * scale).roundToInt())
    val output = IntArray(ow * oh)
    val point = DoubleArray(2)
    for (y in 0 until oh) {
        checkCancelled()
        for (x in 0 until ow) {
            mapping.sourceInto(x.toDouble() / (ow - 1), y.toDouble() / (oh - 1), point)
            if (point[0] !in -1e-7..1.0000001 || point[1] !in -1e-7..1.0000001) { output[y * ow + x] = 0xffffffff.toInt(); continue }
            val sx = point[0].coerceIn(0.0, 1.0) * (width - 1); val sy = point[1].coerceIn(0.0, 1.0) * (height - 1)
            val ix = sx.toInt(); val iy = sy.toInt()
            val nx = min(ix + 1, width - 1); val ny = min(iy + 1, height - 1)
            val tx = sx - ix; val ty = sy - iy
            var color = 0xff000000.toInt()
            for (shift in 0..16 step 8) {
                fun channel(px: Int) = (px ushr shift) and 255
                val top = channel(pixels[iy * width + ix]) * (1 - tx) + channel(pixels[iy * width + nx]) * tx
                val bottom = channel(pixels[ny * width + ix]) * (1 - tx) + channel(pixels[ny * width + nx]) * tx
                color = color or (((top * (1 - ty) + bottom * ty).roundToInt()) shl shift)
            }
            output[y * ow + x] = color
    }
    }
    return CorrectedPixels(ow, oh, output)
}
