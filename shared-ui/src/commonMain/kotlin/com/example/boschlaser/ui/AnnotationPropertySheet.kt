package com.example.boschlaser.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Platform resources provide appearance; the sheet owns only transient presentation state. */
data class AnnotationPropertySheetStyle(
    val background: Color,
    val handleColor: Color,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val elevation: Dp,
    val cornerRadius: Dp,
    val handleAreaHeight: Dp,
    val handleWidth: Dp,
    val handleHeight: Dp,
    val handleCornerRadius: Dp,
    val dismissThreshold: Dp,
    val exitExtraDistance: Dp,
)

/** Matches the native panel's downward-only dragging and its alpha floor. */
internal fun propertySheetDragAlpha(distance: Float, height: Float): Float =
    (1f - distance.coerceAtLeast(0f) / height.coerceAtLeast(1f) * 0.55f).coerceIn(0.45f, 1f)

@Composable
fun AnnotationPropertySheet(
    visible: Boolean,
    resetKey: Int,
    animateDismiss: Boolean,
    handleLabel: String,
    style: AnnotationPropertySheetStyle,
    onSwipeDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var present by remember { mutableStateOf(visible) }
    var offset by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(0f) }
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onSwipeDismiss)
    val latestVisible by rememberUpdatedState(visible)
    val latestResetKey by rememberUpdatedState(resetKey)
    val density = LocalDensity.current
    val threshold = with(density) { style.dismissThreshold.toPx() }
    val exitExtra = with(density) { style.exitExtraDistance.toPx() }

    suspend fun settle(target: Float, targetAlpha: Float, duration: Int) {
        val startOffset = offset
        val startAlpha = alpha
        animate(0f, 1f, animationSpec = tween(duration, easing = propertyPanelEasing)) { fraction, _ ->
            offset = startOffset + (target - startOffset) * fraction
            alpha = startAlpha + (targetAlpha - startAlpha) * fraction
        }
    }

    LaunchedEffect(visible, resetKey, animateDismiss) {
        settleJob?.cancel()
        if (visible) {
            present = true
            offset = 0f
            alpha = 1f
        } else {
            if (present && animateDismiss) settle(height + exitExtra, 0f, 180)
            present = false
            offset = 0f
            alpha = 1f
        }
    }
    if (!visible && !present) return

    Surface(modifier.fillMaxWidth().testTag("annotation-property-sheet")
        .onSizeChanged { height = it.height.toFloat() }
        .graphicsLayer { translationY = offset; this.alpha = alpha },
        color = style.background, shape = RoundedCornerShape(style.cornerRadius), shadowElevation = style.elevation) {
        Column(Modifier.padding(horizontal = style.horizontalPadding, vertical = style.verticalPadding)) {
            Box(Modifier.fillMaxWidth().height(style.handleAreaHeight).testTag("annotation-property-handle")
                .semantics { contentDescription = handleLabel }
                .onGloballyPositioned { handleCoordinates = it }
                .pointerInput(visible, resetKey, threshold) {
                    if (!visible) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        settleJob?.cancel()
                        val startY = handleCoordinates?.takeIf { it.isAttached }?.localToRoot(down.position)?.y
                            ?: (down.position.y + offset)
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val currentY = handleCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.y
                                    ?: (change.position.y + offset)
                                offset = (currentY - startY).coerceAtLeast(0f)
                                alpha = propertySheetDragAlpha(offset, height)
                                change.consume()
                            }
                        } finally {
                            // Input cancellation also cancels the pointer coroutine. Only a host
                            // visibility/selection reset should suppress the original CANCEL rule.
                            if (latestVisible && latestResetKey == resetKey) {
                                if (offset >= threshold) latestDismiss()
                                else settleJob = scope.launch { settle(0f, 1f, 140) }
                            }
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(style.handleWidth, style.handleHeight)
                    .background(style.handleColor, RoundedCornerShape(style.handleCornerRadius)))
            }
            content()
        }
    }
}
