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
import androidx.compose.material.icons.filled.Architecture
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
import com.roomplanner.localization.strings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radial context menu for vertex actions.
 * Appears when vertex is tapped in SELECT mode.
 *
 * Phase 1.5: Delete
 * Phase 1.6.3: Set Angle (between connected lines)
 * Future: Multiple actions (Edit, Properties, Merge)
 *
 * @param anchorPosition Screen position of the vertex (center point)
 * @param onSetAngle Callback when Set Angle action is selected (Phase 1.6.3)
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
    co.touchlab.kermit.Logger.d {
        "VertexRadialMenu shown at anchor: $anchorPosition"
    }

    val strings = strings()
    var selectedItem by remember { mutableStateOf<VertexAction?>(null) }

    // Menu items with radial positions (2 items at 180° apart)
    val items =
        listOf(
            VertexMenuItem(
                action = VertexAction.SET_ANGLE,
                icon = Icons.Default.Architecture, // Angle/ruler icon
                label = "Set Angle", // Phase 1.6.3
                angle = 0f, // Right of vertex
            ),
            VertexMenuItem(
                action = VertexAction.DELETE,
                icon = Icons.Default.Delete,
                label = strings.deleteButton,
                angle = 180f, // Left of vertex
            ),
        )

    // Radial menu parameters
    val radius = 50.dp // Distance from vertex center (reduced from 80dp)
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

                            co.touchlab.kermit.Logger.d {
                                "VertexMenu: Tap at $tapPosition (anchor: $anchorPosition)"
                            }

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

                            if (tappedItem != null) {
                                co.touchlab.kermit.Logger.i {
                                    "✓ VertexMenu: Selected $tappedItem via tap"
                                }
                                when (tappedItem) {
                                    VertexAction.SET_ANGLE -> {
                                        onSetAngle()
                                        onDismiss() // Dismiss AFTER action
                                    }
                                    VertexAction.DELETE -> {
                                        onDelete()
                                        onDismiss() // Dismiss AFTER action
                                    }
                                }
                            } else {
                                co.touchlab.kermit.Logger.d {
                                    "VertexMenu: Tap outside items, dismissing"
                                }
                                onDismiss() // Only dismiss if tapped outside
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    // Handle drag (for drag-to-select)
                    detectDragGestures(
                        onDragStart = { startPosition ->
                            co.touchlab.kermit.Logger.d {
                                "VertexMenu: Drag started at $startPosition"
                            }
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
                                co.touchlab.kermit.Logger.d {
                                    "VertexMenu: Drag selection changed to $selectedItem"
                                }
                            }
                        },
                        onDragEnd = {
                            co.touchlab.kermit.Logger.d {
                                "VertexMenu: Drag ended, selectedItem: $selectedItem"
                            }
                            selectedItem?.let { action ->
                                co.touchlab.kermit.Logger.i {
                                    "✓ VertexMenu: Selected $action via drag"
                                }
                                when (action) {
                                    VertexAction.SET_ANGLE -> {
                                        onSetAngle()
                                        onDismiss() // Dismiss AFTER action
                                    }
                                    VertexAction.DELETE -> {
                                        onDelete()
                                        onDismiss() // Dismiss AFTER action
                                    }
                                }
                            } ?: run {
                                // No action selected - dismiss anyway
                                onDismiss()
                            }
                        },
                        onDragCancel = {
                            co.touchlab.kermit.Logger
                                .d { "VertexMenu: Drag cancelled" }
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

            VertexMenuItemComposable(
                icon = item.icon,
                label = item.label,
                position = itemPosition,
                isDelete = item.action == VertexAction.DELETE,
                isSelected = selectedItem == item.action,
                size = itemSize,
            )
        }
    }
}

/**
 * Menu item data class.
 */
private data class VertexMenuItem(
    val action: VertexAction,
    val icon: ImageVector,
    val label: String,
    val angle: Float, // In degrees: 0 = right, 90 = down, 180 = left, 270 = up
)

/**
 * Vertex action enum.
 */
private enum class VertexAction {
    SET_ANGLE, // Phase 1.6.3
    DELETE,
    // Future: EDIT, PROPERTIES, MERGE
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
private fun VertexMenuItemComposable(
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
