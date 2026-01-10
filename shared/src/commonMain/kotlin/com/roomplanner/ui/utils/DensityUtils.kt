package com.roomplanner.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Convert dp to pixels using current density.
 * Use in Composable context where LocalDensity is available.
 */
@Composable
fun Float.dpToPx(): Float {
    val density = LocalDensity.current
    return with(density) { this@dpToPx.dp.toPx() }
}

/**
 * Convert dp to pixels using explicit density.
 * Use in DrawScope or non-Composable contexts.
 */
fun Float.dpToPx(density: Density): Float = with(density) { this@dpToPx.dp.toPx() }

/**
 * Convert pixels to dp using explicit density.
 */
fun Float.pxToDp(density: Density): Dp = with(density) { this@pxToDp.toDp() }
