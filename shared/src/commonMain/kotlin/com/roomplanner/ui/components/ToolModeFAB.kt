package com.roomplanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.roomplanner.data.models.ToolMode
import com.roomplanner.localization.strings
import com.roomplanner.ui.utils.dpToPx

/**
 * Floating action button showing current tool mode with integrated radial menu.
 *
 * Phase 1.4c: Self-contained component that manages its own radial menu state.
 * Tapping the FAB opens a radial menu for tool selection.
 *
 * @param currentMode Currently active tool mode
 * @param onToolSelected Called when user selects a tool from the radial menu
 * @param modifier Modifier for the FAB container
 */
@Composable
fun ToolModeFAB(
    currentMode: ToolMode,
    onToolSelected: (ToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()
    var showMenu by remember { mutableStateOf(false) }
    var fabCenter by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        // FAB button
        FloatingActionButton(
            onClick = { showMenu = true },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .onGloballyPositioned { coordinates ->
                        // Calculate FAB center in window coordinates
                        val size = coordinates.size
                        val localCenter = Offset(size.width / 2f, size.height / 2f)
                        val windowCenter = coordinates.localToWindow(localCenter)
                        fabCenter = windowCenter
                        co.touchlab.kermit.Logger.d {
                            "FAB center measured: $windowCenter (size: ${size.width}x${size.height})"
                        }
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

        // Radial menu overlay (shown when FAB is tapped)
        if (showMenu) {
            RadialToolMenu(
                currentMode = currentMode,
                anchorPosition = fabCenter - Offset(0f, 105f.dpToPx()),
                onToolSelected = { mode ->
                    onToolSelected(mode)
                    showMenu = false // Close menu after selection
                },
                onDismiss = { showMenu = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
