package com.example.boschlaser.ui

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos

/** Preserve the native property panels' accelerate/decelerate animation curve. */
internal val propertyPanelEasing = Easing { fraction -> ((cos((fraction + 1.0) * PI) + 1.0) / 2.0).toFloat() }
