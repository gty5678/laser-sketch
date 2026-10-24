package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp

/** Host-provided resources keep platform appearance separate from page arrangement. */
data class SketchScreenStyle(
    val toolbarBackground: Color,
    val toolbarElevation: Dp,
    val editToolbarHeight: Dp,
    val actionsTop: Dp,
    val actionsEnd: Dp,
    val actionsHeight: Dp,
    val selectionActionsEnd: Dp,
    val selectionActionsWidth: Dp,
    val bottomMargin: Dp,
    val propertyHorizontalMargin: Dp,
    val propertyGap: Dp,
)

/** Panels overlay the full viewport; expanding properties never resizes the sketch canvas. */
@Composable
fun SketchScreen(
    showProperties: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    style: SketchScreenStyle,
    canvas: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
    editToolbar: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    selectionActions: @Composable () -> Unit,
    gridSnap: @Composable () -> Unit,
    properties: @Composable () -> Unit,
    dialogs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) { canvas() }
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = style.toolbarBackground, shadowElevation = style.toolbarElevation) {
            Column(Modifier.padding(top = topInset)) {
                toolbar()
                Box(Modifier.fillMaxWidth().height(style.editToolbarHeight)) { editToolbar() }
            }
        }
        Layout(modifier = Modifier.fillMaxSize().padding(bottom = bottomInset + style.bottomMargin),
            content = {
                Box { gridSnap() }
                if (showProperties) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = style.propertyHorizontalMargin)) { properties() }
                }
            }) { measurables, constraints ->
            // Measure each overlay against the full available height, as in the Android host.
            // Reserving snap-control height first would shrink tall property panels.
            val overlayConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val grid = measurables[0].measure(overlayConstraints)
            val property = if (showProperties) measurables[1].measure(overlayConstraints) else null
            val propertyOffset = property?.let { it.height + style.propertyGap.roundToPx() } ?: 0
            layout(constraints.maxWidth, constraints.maxHeight) {
                property?.let { it.placeRelative(0, constraints.maxHeight - it.height) }
                grid.placeRelative((constraints.maxWidth - grid.width) / 2,
                    constraints.maxHeight - propertyOffset - grid.height)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = topInset + style.actionsTop, end = style.actionsEnd)
            .height(style.actionsHeight)) { actions() }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = style.selectionActionsEnd)
            .width(style.selectionActionsWidth)) { selectionActions() }
        dialogs()
    }
}
