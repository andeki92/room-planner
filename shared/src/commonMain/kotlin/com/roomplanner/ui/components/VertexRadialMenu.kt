package com.roomplanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.roomplanner.localization.strings

/**
 * Radial context menu for vertex actions.
 * Appears on long press of a vertex.
 *
 * Actions:
 * - Set Angle: Configure angle constraint between connected lines
 * - Delete: Remove the vertex
 *
 * @param anchorPosition Screen position of the vertex (center point)
 * @param onSetAngle Callback when Set Angle action is selected
 * @param onDelete Callback when Delete action is selected
 * @param onDismiss Callback to close the menu
 */
@Composable
fun VertexRadialMenu(
    anchorPosition: Offset,
    onSetAngle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()

    val items =
        listOf(
            RadialMenuItem(
                id = "set_angle",
                icon = Icons.Default.Architecture,
                contentDescription = "Set Angle",
                angle = 0f, // Right of vertex
                isDestructive = false,
            ),
            RadialMenuItem(
                id = "delete",
                icon = Icons.Default.Delete,
                contentDescription = strings.deleteButton,
                angle = 180f, // Left of vertex
                isDestructive = true,
            ),
        )

    RadialMenu(
        anchorPosition = anchorPosition,
        items = items,
        onItemSelected = { id ->
            when (id) {
                "set_angle" -> onSetAngle()
                "delete" -> onDelete()
            }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
