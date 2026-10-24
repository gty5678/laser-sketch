package com.example.boschlaser.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max

/** Platform pixels and encoding; annotation coordinates remain in the shared document. */
object AndroidPhotoCodec {
    fun read(resolver: ContentResolver, uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.setTargetSampleSize(max(1, ceil(max(info.size.width, info.size.height) / 3000.0).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    fun decode(bytes: ByteArray, targetSize: Int = 3000): Bitmap? {
        require(targetSize > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) }.toByteArray()
    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
}
