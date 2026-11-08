package com.example.boschlaser.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.boschlaser.ui.resources.*
import org.jetbrains.compose.resources.painterResource

/** Shared vector sources also feed Android's R.drawable for existing native host adapters. */
@Composable
internal fun DrawingIcon(name: String, modifier: Modifier = Modifier) {
    Icon(painterResource(Res.allDrawableResources.getValue("ic_$name")), contentDescription = null, modifier = modifier)
}
