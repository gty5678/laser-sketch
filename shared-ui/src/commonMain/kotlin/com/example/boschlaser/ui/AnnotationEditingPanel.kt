package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Selection properties overlay the photo only while editing; there is no drag handle. */
@Composable
fun AnnotationEditingPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("annotation-editing-panel"),
        shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
