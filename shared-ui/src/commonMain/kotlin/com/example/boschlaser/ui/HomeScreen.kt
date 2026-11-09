package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Android supplies resources and actions; the shared page owns layout and scrolling. */
data class HomeLabels(
    val importProject: String,
    val photos: String,
    val sketches: String,
    val addPhoto: String,
    val addSketch: String,
    val device: String,
    val addSymbol: String,
)

data class HomeStyle(val photoAction: Color, val sketchAction: Color,
    val connectedDevice: Color, val disconnectedDevice: Color, val actionContent: Color)

@Composable
fun HomeScreen(
    labels: HomeLabels,
    style: HomeStyle,
    connected: Boolean,
    showDevice: Boolean,
    onImport: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddSketch: () -> Unit,
    onToggleDevice: () -> Unit,
    photos: @Composable () -> Unit,
    sketches: @Composable () -> Unit,
    measurements: @Composable () -> Unit,
    device: @Composable () -> Unit,
    deviceIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    settings: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    settings()
                    TextButton(onClick = onImport) { Text(labels.importProject) }
                }
                Text(labels.photos, Modifier.padding(top = 18.dp, bottom = 6.dp), style = MaterialTheme.typography.titleLarge)
                photos()
                Text(labels.sketches, Modifier.padding(top = 18.dp, bottom = 6.dp), style = MaterialTheme.typography.titleLarge)
                sketches()
                measurements()
            }
            if (showDevice) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { device() }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 88.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtendedFloatingActionButton(onClick = onAddSketch, modifier = Modifier.weight(1f)
                .semantics { contentDescription = labels.addSketch }, shape = CircleShape,
                containerColor = style.sketchAction, contentColor = style.actionContent) {
                Text(labels.addSketch, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            ExtendedFloatingActionButton(onClick = onAddPhoto, modifier = Modifier.weight(1f)
                .semantics { contentDescription = labels.addPhoto }, shape = CircleShape,
                containerColor = style.photoAction, contentColor = style.actionContent) {
                Text(labels.addPhoto, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        FloatingActionButton(onClick = onToggleDevice, modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 22.dp).size(60.dp)
            .semantics { contentDescription = labels.device }, shape = CircleShape,
            containerColor = if (connected) style.connectedDevice else style.disconnectedDevice,
            contentColor = style.actionContent) { deviceIcon() }
    }
}
