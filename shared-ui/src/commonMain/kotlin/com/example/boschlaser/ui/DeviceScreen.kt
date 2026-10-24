package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Ready means protocol initialization completed; permission and discovery remain host responsibilities. */
data class DeviceScreenState(val status: String, val device: String, val connected: Boolean = false, val canConnectSaved: Boolean = false)
data class DeviceLabels(val title: String, val scan: String, val connectSaved: String, val disconnect: String)

@Composable
fun DeviceScreen(state: DeviceScreenState, labels: DeviceLabels,
    onScan: () -> Unit, onConnectSaved: () -> Unit, onDisconnect: () -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(labels.title, style = MaterialTheme.typography.titleLarge)
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
            Text(state.device, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text(labels.scan) }
                Button(onClick = onConnectSaved, enabled = state.canConnectSaved && !state.connected, modifier = Modifier.weight(1f)) { Text(labels.connectSaved) }
                OutlinedButton(onClick = onDisconnect, enabled = state.connected, modifier = Modifier.weight(1f)) { Text(labels.disconnect) }
            }
        }
    }
}
