package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class SelectionActionTarget { NONE, WALL, DOOR }

/** Host selection chooses the actions; both door flips retain their independent commands. */
@Composable
fun SelectionActions(
    target: SelectionActionTarget,
    straightenLabel: String,
    hingeLabel: String,
    sideLabel: String,
    onStraighten: () -> Unit,
    onFlipHinge: () -> Unit,
    onFlipSide: () -> Unit,
    straightenIcon: @Composable () -> Unit,
    hingeIcon: @Composable () -> Unit,
    sideIcon: @Composable () -> Unit,
) {
    @Composable
    fun action(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
        FloatingActionButton(onClick = onClick, shape = CircleShape,
            modifier = Modifier.size(56.dp).semantics { contentDescription = label },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary, content = icon)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (target) {
            SelectionActionTarget.NONE -> Unit
            SelectionActionTarget.WALL -> action(straightenLabel, onStraighten, straightenIcon)
            SelectionActionTarget.DOOR -> {
                action(hingeLabel, onFlipHinge, hingeIcon)
                action(sideLabel, onFlipSide, sideIcon)
            }
        }
    }
}
