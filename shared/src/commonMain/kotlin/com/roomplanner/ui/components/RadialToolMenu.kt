package com.roomplanner.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roomplanner.data.models.ToolMode
import com.roomplanner.localization.strings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TRUE radial menu with drag-to-select interaction.
 * Items positioned in circle around FAB anchor point.
 *
 * Interaction:
 * 1. Press FAB → menu items fan out
 * 2. Drag finger to item → item highlights
 * 3. Release → item selected, menu closes
 * 4. Release outside → menu closes, no selection
 *
 * @param currentMode Currently active tool mode
 * @param anchorPosition Position of FAB center (screen coordinates)
 * @param onToolSelected Called when user releases on an item
 * @param onDismiss Called when menu should close
 */
@Composable
fun RadialToolMenu(
    currentMode: ToolMode,
    anchorPosition: Offset,
    onToolSelected: (ToolMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    co.touchlab.kermit.Logger
        .d { "RadialToolMenu shown at anchor: $anchorPosition, current mode: $currentMode" }

    val strings = strings()
    var selectedItem by remember { mutableStateOf<ToolMode?>(null) }

    // Menu items with radial positions
    // FAB is in bottom-right, so menu fans out in upper-left quadrant (90° to 180°)
    val items =
        listOf(
            MenuItem(
                mode = ToolMode.DRAW,
                icon = Icons.Default.Edit,
                label = strings.drawToolButton,
                angle = 165f, // Near-left (15° from left)
            ),
            MenuItem(
                mode = ToolMode.SELECT,
                icon = Icons.Default.TouchApp,
                label = strings.selectToolButton,
                angle = 105f, // Near-top (15° from top)
            ),
        )

    // Radial menu parameters
    val radius = 100.dp // Distance from FAB center
    val itemSize = 56.dp // Size of each menu item
    val density = LocalDensity.current

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent overlay
                .pointerInput(Unit) {
                    // Handle tap (for quick selection)
                    detectTapGestures(
                        onTap = { tapPosition ->
                            val radiusPx = with(density) { radius.toPx() }
                            val itemSizePx = with(density) { itemSize.toPx() }

                            co.touchlab.kermit.Logger.d {
                                "RadialMenu: Tap at $tapPosition (anchor: $anchorPosition, radius: ${radiusPx}px)"
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
                                        co.touchlab.kermit.Logger.d {
                                            "  Item ${item.mode} at $itemPosition (angle ${item.angle}°), distance: ${dist}px, threshold: ${itemSizePx / 2}px"
                                        }
                                        dist < itemSizePx / 2
                                    }?.mode

                            if (tappedItem != null) {
                                co.touchlab.kermit.Logger
                                    .i { "✓ RadialMenu: Selected $tappedItem via tap" }
                                onToolSelected(tappedItem)
                            } else {
                                co.touchlab.kermit.Logger
                                    .d { "RadialMenu: Tap outside items, dismissing" }
                            }
                            onDismiss()
                        },
                    )
                }.pointerInput(Unit) {
                    // Handle drag (for drag-to-select)
                    detectDragGestures(
                        onDragStart = { startPosition ->
                            co.touchlab.kermit.Logger
                                .d { "RadialMenu: Drag started at $startPosition" }
                        },
                        onDrag = { change, _ ->
                            // Find which item the finger is over
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
                                        distance(dragPosition, itemPosition) < with(density) { itemSize.toPx() } / 2
                                    }?.mode

                            if (selectedItem != previousSelection) {
                                co.touchlab.kermit.Logger
                                    .d { "RadialMenu: Drag selection changed to $selectedItem" }
                            }
                        },
                        onDragEnd = {
                            co.touchlab.kermit.Logger
                                .d { "RadialMenu: Drag ended, selectedItem: $selectedItem" }
                            // Select the highlighted item
                            selectedItem?.let {
                                co.touchlab.kermit.Logger
                                    .i { "✓ RadialMenu: Selected $it via drag" }
                                onToolSelected(it)
                            }
                            onDismiss()
                        },
                        onDragCancel = {
                            co.touchlab.kermit.Logger
                                .d { "RadialMenu: Drag cancelled" }
                            onDismiss()
                        },
                    )
                },
    ) {
        // Debug: Draw visual indicators for FAB center and item positions
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radiusPx = with(density) { radius.toPx() }
            val itemSizePx = with(density) { itemSize.toPx() }

            // Draw FAB center (red dot)
            drawCircle(
                color = Color.Red,
                radius = 10f,
                center = anchorPosition,
            )

            // Draw radius circle (debug)
            drawCircle(
                color = Color.Yellow.copy(alpha = 0.3f),
                radius = radiusPx,
                center = anchorPosition,
                style = Stroke(width = 2f),
            )

            // Draw item positions
            items.forEach { item ->
                val itemPosition =
                    calculateRadialPosition(
                        anchor = anchorPosition,
                        radius = radiusPx,
                        angle = item.angle,
                    )

                // Draw item hit area (green circle)
                drawCircle(
                    color = Color.Green.copy(alpha = 0.3f),
                    radius = itemSizePx / 2,
                    center = itemPosition,
                )

                // Draw item center (blue dot)
                drawCircle(
                    color = Color.Blue,
                    radius = 5f,
                    center = itemPosition,
                )
            }
        }

        // Draw each menu item at its radial position
        items.forEach { item ->
            val itemPosition =
                calculateRadialPosition(
                    anchor = anchorPosition,
                    radius = with(density) { radius.toPx() },
                    angle = item.angle,
                )

            RadialMenuItem(
                icon = item.icon,
                label = item.label,
                position = itemPosition,
                isActive = currentMode == item.mode,
                isSelected = selectedItem == item.mode,
                size = itemSize,
            )
        }
    }
}

/**
 * Menu item data class.
 */
private data class MenuItem(
    val mode: ToolMode,
    val icon: ImageVector,
    val label: String,
    val angle: Float, // In degrees: 0 = right, 90 = up, 180 = left, 270 = down
)

/**
 * Calculate radial position around anchor point.
 */
private fun calculateRadialPosition(
    anchor: Offset,
    radius: Float,
    angle: Float,
): Offset {
    val angleRad = angle * PI.toFloat() / 180f

    return Offset(
        x = anchor.x + radius * cos(angleRad),
        y = anchor.y - radius * sin(angleRad),
    )
}

/**
 * Individual radial menu item (circular button).
 */
@Composable
private fun RadialMenuItem(
    icon: ImageVector,
    label: String,
    position: Offset,
    isActive: Boolean,
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
                            isActive -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    shape = CircleShape,
                ).border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint =
                when {
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
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
