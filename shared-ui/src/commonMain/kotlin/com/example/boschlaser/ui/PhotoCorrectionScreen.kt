package com.example.boschlaser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.example.boschlaser.shared.annotation.PhotoCorrection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate

data class PhotoCorrectionLabels(val title: String, val hint: String, val lens: String, val reset: String, val original: String, val apply: String, val error: String, val horizontal: String, val vertical: String, val rotation: String)

@Composable
fun PhotoCorrectionScreen(image: ImageBitmap, labels: PhotoCorrectionLabels,
    render: suspend (PhotoCorrection, Int) -> ImageBitmap, onDone: suspend (PhotoCorrection?) -> Unit) {
    var settings by remember { mutableStateOf(PhotoCorrection()) }
    var preview by remember { mutableStateOf(image) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    fun update(next: PhotoCorrection) {
        if (next != settings) { ready = false; settings = next }
    }
    LaunchedEffect(image) {
        snapshotFlow { settings to dragging }.conflate().collect { (next, moving) ->
            try {
                preview = render(next, if (moving) 400 else 700)
                failed = false
                ready = next == settings
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    val scope = rememberCoroutineScope()
    fun finish(value: PhotoCorrection?) { scope.launch {
        busy = true
        try { onDone(value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Text(labels.title, style = MaterialTheme.typography.titleLarge)
        Text(labels.hint, style = MaterialTheme.typography.bodySmall)
        Image(preview, contentDescription = labels.title, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("photo-correction-preview")
                .drawWithCache {
                    // Match the centered, fitted image; keep guides level while the photo transforms.
                    val scale = minOf(size.width / preview.width, size.height / preview.height)
                    val width = preview.width * scale
                    val height = preview.height * scale
                    val left = (size.width - width) / 2f
                    val top = (size.height - height) / 2f
                    val outline = 3.dp.toPx()
                    val stroke = 1.dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                            val x = left + width * fraction
                            val y = top + height * fraction
                            val horizontalStart = Offset(left, y)
                            val horizontalEnd = Offset(left + width, y)
                            val verticalStart = Offset(x, top)
                            val verticalEnd = Offset(x, top + height)
                            drawLine(Color.Black.copy(alpha = 0.65f), horizontalStart, horizontalEnd, outline)
                            drawLine(Color.Black.copy(alpha = 0.65f), verticalStart, verticalEnd, outline)
                            drawLine(Color.White.copy(alpha = 0.85f), horizontalStart, horizontalEnd, stroke)
                            drawLine(Color.White.copy(alpha = 0.85f), verticalStart, verticalEnd, stroke)
                        }
                    }
                })
        Text(labels.rotation + " · " + ((settings.rotation * 10).toInt() / 10.0) + "°")
        Slider(settings.rotation.toFloat(), { dragging = true; update(settings.copy(rotation = it.toDouble())) },
            onValueChangeFinished = { dragging = false }, valueRange = -45f..45f, enabled = !busy,
            modifier = Modifier.testTag("photo-correction-rotation"))
        Text(labels.horizontal)
        Slider(settings.horizontal.toFloat(), { dragging = true; update(settings.copy(horizontal = it.toDouble())) },
            onValueChangeFinished = { dragging = false }, valueRange = -0.3f..0.3f, enabled = !busy, modifier = Modifier.testTag("photo-correction-horizontal"))
        Text(labels.vertical)
        Slider(settings.vertical.toFloat(), { dragging = true; update(settings.copy(vertical = it.toDouble())) },
            onValueChangeFinished = { dragging = false }, valueRange = -0.3f..0.3f, enabled = !busy, modifier = Modifier.testTag("photo-correction-vertical"))
        Text(labels.lens)
        Slider(settings.lens.toFloat(), { dragging = true; update(settings.copy(lens = it.toDouble())) },
            onValueChangeFinished = { dragging = false }, valueRange = -0.3f..0.3f, enabled = !busy, modifier = Modifier.testTag("photo-correction-lens"))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(labels.error, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = !busy, onClick = { update(PhotoCorrection()) }) { Text(labels.reset) }
            TextButton(enabled = !busy, onClick = { finish(null) }) { Text(labels.original) }
            Button(enabled = !busy && ready && !failed, onClick = { finish(settings) }) { Text(labels.apply) }
        }
    }
}
