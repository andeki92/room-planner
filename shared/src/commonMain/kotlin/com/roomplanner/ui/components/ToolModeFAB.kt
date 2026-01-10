package com.roomplanner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.roomplanner.data.models.ToolMode
import com.roomplanner.localization.strings

/**
 * Floating action button showing current tool mode.
 * Tapping opens radial menu for tool selection.
 *
 * Phase 1.4b: Reports FAB position for radial menu anchor.
 *
 * @param currentMode Currently active tool mode
 * @param onClick Called when FAB is tapped
 * @param onPositionMeasured Called with FAB center position when laid out
 */
@Composable
fun ToolModeFAB(
    currentMode: ToolMode,
    onClick: () -> Unit,
    onPositionMeasured: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()

    FloatingActionButton(
        onClick = onClick,
        modifier =
            modifier
                .padding(16.dp)
                .onGloballyPositioned { coordinates ->
                    // Calculate center of FAB
                    // Use localToWindow to convert local center to window coordinates
                    val size = coordinates.size
                    val localCenter = Offset(size.width / 2f, size.height / 2f)
                    val windowCenter = coordinates.localToWindow(localCenter)

                    onPositionMeasured(windowCenter)
                    co.touchlab.kermit.Logger
                        .d { "FAB center measured: $windowCenter (size: ${size.width}x${size.height})" }
                },
    ) {
        Icon(
            imageVector =
                when (currentMode) {
                    ToolMode.DRAW -> Icons.Default.Edit
                    ToolMode.SELECT -> Icons.Default.TouchApp
                },
            contentDescription =
                when (currentMode) {
                    ToolMode.DRAW -> strings.drawToolButton
                    ToolMode.SELECT -> strings.selectToolButton
                },
        )
    }
}
