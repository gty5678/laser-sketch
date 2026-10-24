package com.example.boschlaser.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class SketchPropertySheetStyle(
    val background: Color,
    val titleColor: Color,
    val titleSize: TextUnit,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val elevation: Dp,
    val minimumDismissDistance: Dp,
)

internal fun sketchPanelDragStarts(dx: Float, dy: Float, touchSlop: Float): Boolean = dy > touchSlop && dy > abs(dx)
internal fun sketchPanelDragAlpha(distance: Float, height: Float): Float =
    (1f - distance.coerceAtLeast(0f) / height.coerceAtLeast(1f)).coerceIn(0.35f, 1f)
internal fun sketchPanelDismissDistance(height: Float, minimum: Float): Float = max(minimum, height * 0.18f)

/** Full-panel downward drag, preserving child clicks until vertical touch slop is crossed. */
@Composable
fun SketchPropertySheet(
    visible: Boolean,
    resetKey: Int,
    animateDismiss: Boolean,
    title: String,
    style: SketchPropertySheetStyle,
    touchSlop: Float,
    onDismiss: () -> Unit,
    onPresenceChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var present by remember { mutableStateOf(visible) }
    var offset by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(0f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestPresence by rememberUpdatedState(onPresenceChanged)
    val latestVisible by rememberUpdatedState(visible)
    val latestResetKey by rememberUpdatedState(resetKey)
    val density = LocalDensity.current
    val minimumDistance = with(density) { style.minimumDismissDistance.toPx() }

    suspend fun settle(target: Float, targetAlpha: Float) {
        val startOffset = offset
        val startAlpha = alpha
        animate(0f, 1f, animationSpec = tween(160, easing = propertyPanelEasing)) { fraction, _ ->
            offset = startOffset + (target - startOffset) * fraction
            alpha = startAlpha + (targetAlpha - startAlpha) * fraction
        }
    }
    LaunchedEffect(visible, resetKey, animateDismiss) {
        settleJob?.cancel()
        if (visible) {
            present = true
            latestPresence(true)
            offset = 0f
            alpha = 1f
        } else {
            if (present && animateDismiss) settle(height, 0f)
            present = false
            latestPresence(false)
            offset = 0f
            alpha = 1f
        }
    }
    if (!visible && !present) return

    Surface(modifier.fillMaxWidth().testTag("sketch-property-sheet")
        .onSizeChanged { height = it.height.toFloat() }
        .graphicsLayer { translationY = offset; this.alpha = alpha }
        .onGloballyPositioned { coordinates = it }
        .pointerInput(visible, resetKey, touchSlop, minimumDistance) {
            if (!visible) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val start = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position) ?: down.position
                var dragging = false
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Compose represents cancellation as an already-consumed up.
                            released = !change.isConsumed
                            if (dragging) change.consume()
                            break
                        }
                        val point = coordinates?.takeIf { it.isAttached }?.localToRoot(change.position) ?: change.position
                        val dx = point.x - start.x
                        val dy = point.y - start.y
                        if (!dragging && sketchPanelDragStarts(dx, dy, touchSlop)) {
                            settleJob?.cancel()
                            dragging = true
                        }
                        if (dragging) {
                            offset = dy.coerceAtLeast(0f)
                            alpha = sketchPanelDragAlpha(offset, height)
                            change.consume()
                        }
                    }
                } finally {
                    // A cancelled pointer always rolls back, unlike the photo panel's handle.
                    // Host selection/visibility resets must not restart an obsolete gesture.
                    if (dragging && latestVisible && latestResetKey == resetKey) {
                        if (released && offset >= sketchPanelDismissDistance(height, minimumDistance)) {
                            focus.clearFocus(force = true)
                            latestDismiss()
                        } else settleJob = scope.launch { settle(0f, 1f) }
                    }
                }
            }
        }, color = style.background, shadowElevation = style.elevation) {
        Column(Modifier.padding(start = style.horizontalPadding, end = style.horizontalPadding,
            top = style.topPadding, bottom = style.bottomPadding)) {
            Text(title, modifier = Modifier.fillMaxWidth(), style = TextStyle(color = style.titleColor, fontSize = style.titleSize))
            content()
        }
    }
}
