package com.example.boschlaser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Page arrangement is shared; photo, platform input and resources arrive through host slots. */
@Composable
fun AnnotationScreen(
    background: Color,
    actionEndMargin: Dp,
    propertyMargin: Dp,
    toolbar: @Composable () -> Unit,
    placementOptions: @Composable () -> Unit,
    canvas: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    properties: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(background)) {
        toolbar()
        placementOptions()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize()) { canvas() }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(propertyMargin)) { properties() }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = actionEndMargin)) { actions() }
        }
    }
}
