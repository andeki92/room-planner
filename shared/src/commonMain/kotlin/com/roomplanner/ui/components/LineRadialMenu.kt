package com.roomplanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.roomplanner.localization.strings

/**
 * Radial context menu for line actions.
 * Appears on long press of a line.
 *
 * Actions:
 * - Set Dimension: Configure distance constraint for the line
 * - Delete: Remove the line
 *
 * @param anchorPosition Screen position for menu center (typically line midpoint)
 * @param onSetDimension Callback when Set Dimension action is selected
 * @param onDelete Callback when Delete action is selected
 * @param onSplit Callback when Split Line action is selected (future feature)
 * @param onDismiss Callback to close the menu
 */
@Composable
fun LineRadialMenu(
    anchorPosition: Offset,
    onSetDimension: () -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings()

    val items =
        listOf(
            RadialMenuItem(
                id = "set_dimension",
                icon = Icons.Default.Straighten,
                contentDescription = strings.setDimension,
                angle = 135f, // Upper-left
                isDestructive = false,
            ),
            RadialMenuItem(
                id = "delete",
                icon = Icons.Default.Delete,
                contentDescription = strings.deleteButton,
                angle = 45f, // Upper-right
                isDestructive = true,
            ),
        )

    RadialMenu(
        anchorPosition = anchorPosition,
        items = items,
        onItemSelected = { id ->
            when (id) {
                "set_dimension" -> onSetDimension()
                "delete" -> onDelete()
                "split" -> onSplit()
            }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
