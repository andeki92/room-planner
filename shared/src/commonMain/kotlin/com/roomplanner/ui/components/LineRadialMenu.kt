package com.roomplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.localization.strings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radial context menu for line actions.
 * Appears when line is tapped in SELECT mode.
 *
 * Phase 1.5: Set Dimension, Delete
 * Future: Additional actions (Split, Convert to arc, etc.)
 *
 * @param anchorPosition Screen position for menu center (typically line midpoint)
 * @param onSetDimension Callback when "Set Dimension" action is selected
 * @param onDelete Callback when "Delete" action is selected
 * @param onSplit Callback when "Split Line" action is selected (future feature)
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
    Logger.d { "LineRadialMenu shown at anchor: $anchorPosition" }

    val strings = strings()
    var selectedItem by remember { mutableStateOf<LineAction?>(null) }

    // Menu items with radial positions (2 items at ~180° apart)
    val items =
        listOf(
            LineMenuItem(
                action = LineAction.SET_DIMENSION,
                icon = Icons.Default.Edit,
                label = strings.setDimension,
                angle = 165f, // Upper-left
            ),
            LineMenuItem(
                action = LineAction.DELETE,
                icon = Icons.Default.Delete,
                label = strings.deleteButton,
                angle = 105f, // Upper
            ),
            // Future: Split line at point
            // LineMenuItem(
            //     action = LineAction.SPLIT,
            //     icon = Icons.Default.CallSplit,
            //     label = strings.splitLine,
            //     angle = 45f,  // Upper-right
            // ),
        )

    // Radial menu parameters
    val radius = 50.dp // Distance from anchor center (reduced from 80dp)
    val itemSize = 56.dp // Size of each menu item
    val density = LocalDensity.current

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Dim background
                .pointerInput(Unit) {
                    // Handle tap (for quick selection)
                    detectTapGestures(
                        onTap = { tapPosition ->
                            val radiusPx = with(density) { radius.toPx() }
                            val itemSizePx = with(density) { itemSize.toPx() }

                            Logger.d { "LineMenu: Tap at $tapPosition (anchor: $anchorPosition)" }

                            // Find which item was tapped
                            val tappedItem =
                                items
                                    .find { item ->
                                        val itemPosition =
                                            calculateRadialPosition(
                                                anchor = anchorPosition,
                                                radius = radiusPx,
                                                angle = item.angle,
                                            )
                                        val dist = distance(tapPosition, itemPosition)
                                        dist < itemSizePx / 2
                                    }?.action

                            when (tappedItem) {
                                LineAction.SET_DIMENSION -> {
                                    Logger.i { "✓ LineMenu: Set Dimension selected via tap" }
                                    onSetDimension()
                                    onDismiss()
                                }

                                LineAction.DELETE -> {
                                    Logger.i { "✓ LineMenu: Delete selected via tap" }
                                    onDelete()
                                    onDismiss()
                                }

                                LineAction.SPLIT -> {
                                    Logger.i { "✓ LineMenu: Split selected via tap (future)" }
                                    onSplit()
                                    onDismiss()
                                }

                                null -> {
                                    Logger.d { "LineMenu: Tap outside items, dismissing" }
                                    onDismiss()
                                }
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    // Handle drag (for drag-to-select)
                    detectDragGestures(
                        onDragStart = { startPosition ->
                            Logger.d { "LineMenu: Drag started at $startPosition" }
                        },
                        onDrag = { change, _ ->
                            val dragPosition = change.position
                            val previousSelection = selectedItem
                            selectedItem =
                                items
                                    .find { item ->
                                        val itemPosition =
                                            calculateRadialPosition(
                                                anchor = anchorPosition,
                                                radius = with(density) { radius.toPx() },
                                                angle = item.angle,
                                            )
                                        distance(dragPosition, itemPosition) <
                                            with(density) { itemSize.toPx() } / 2
                                    }?.action

                            if (selectedItem != previousSelection) {
                                Logger.d { "LineMenu: Drag selection changed to $selectedItem" }
                            }
                        },
                        onDragEnd = {
                            Logger.d { "LineMenu: Drag ended, selectedItem: $selectedItem" }
                            selectedItem?.let { action ->
                                Logger.i { "✓ LineMenu: Selected $action via drag" }
                                when (action) {
                                    LineAction.SET_DIMENSION -> onSetDimension()
                                    LineAction.DELETE -> onDelete()
                                    LineAction.SPLIT -> onSplit()
                                }
                                onDismiss()
                            } ?: run {
                                // No action selected - dismiss anyway
                                onDismiss()
                            }
                        },
                        onDragCancel = {
                            Logger.d { "LineMenu: Drag cancelled" }
                            onDismiss()
                        },
                    )
                },
    ) {
        // Draw each menu item at its radial position
        items.forEach { item ->
            val itemPosition =
                calculateRadialPosition(
                    anchor = anchorPosition,
                    radius = with(density) { radius.toPx() },
                    angle = item.angle,
                )

            LineMenuItemComposable(
                icon = item.icon,
                label = item.label,
                position = itemPosition,
                isDelete = item.action == LineAction.DELETE,
                isSelected = selectedItem == item.action,
                size = itemSize,
            )
        }
    }
}

/**
 * Menu item data class.
 */
private data class LineMenuItem(
    val action: LineAction,
    val icon: ImageVector,
    val label: String,
    val angle: Float, // In degrees: 0 = right, 90 = down, 180 = left, 270 = up
)

/**
 * Line action enum.
 */
private enum class LineAction {
    SET_DIMENSION,
    DELETE,
    SPLIT,
}

/**
 * Calculate radial position around anchor point.
 * CRITICAL: Y is inverted for Compose coordinate system (top-left origin).
 */
private fun calculateRadialPosition(
    anchor: Offset,
    radius: Float,
    angle: Float,
): Offset {
    val angleRad = angle * PI.toFloat() / 180f

    return Offset(
        x = anchor.x + radius * cos(angleRad),
        y = anchor.y - radius * sin(angleRad), // Inverted for Compose!
    )
}

/**
 * Individual radial menu item (circular button).
 */
@Composable
private fun LineMenuItemComposable(
    icon: ImageVector,
    label: String,
    position: Offset,
    isDelete: Boolean,
    isSelected: Boolean,
    size: Dp,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .offset(
                    x = with(density) { position.x.toDp() } - size / 2,
                    y = with(density) { position.y.toDp() } - size / 2,
                ).size(size)
                .shadow(
                    elevation = if (isSelected) 12.dp else 4.dp,
                    shape = CircleShape,
                ).background(
                    color =
                        when {
                            isDelete && isSelected ->
                                MaterialTheme.colorScheme.error.copy(alpha = 0.9f)

                            isDelete -> MaterialTheme.colorScheme.errorContainer
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    shape = CircleShape,
                ).border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color =
                        if (isDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint =
                if (isDelete) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Calculate screen-space distance between two points.
 */
private fun distance(
    a: Offset,
    b: Offset,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}
