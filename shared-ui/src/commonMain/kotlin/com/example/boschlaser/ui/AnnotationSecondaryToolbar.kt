package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A stable second row keeps the photo viewport fixed when the active tool changes. */
@Composable
fun AnnotationSecondaryToolbar(scrollable: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Surface {
        Box(Modifier.fillMaxWidth().height(52.dp).testTag("annotation-secondary-toolbar"),
            contentAlignment = Alignment.CenterStart) {
            if (scrollable) {
                ScrollableTools(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}
