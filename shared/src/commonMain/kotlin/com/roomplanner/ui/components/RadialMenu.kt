package com.roomplanner.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radial menu item configuration.
 *
 * @param id Unique identifier for the action
 * @param icon Icon to display
 * @param contentDescription Accessibility description
 * @param angle Position in degrees (0 = right, 90 = up in Compose coords, 180 = left, 270 = down)
 * @param isDestructive Whether this is a destructive action (shown in error color)
 */
data class RadialMenuItem(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val angle: Float,
    val isDestructive: Boolean = false,
)

/**
 * Radial menu configuration.
 */
data class RadialMenuConfig(
    val radius: Dp = 60.dp,
    val itemSize: Dp = 52.dp,
    val backdropColor: Color = Color.Black.copy(alpha = 0.4f),
    val useBlurBackdrop: Boolean = true,
    val blurRadius: Dp = 20.dp,
)

/**
 * Shared radial context menu component.
 *
 * Displays circular menu items arranged radially around an anchor point.
 * Supports both tap and drag-to-select interactions.
 *
 * @param anchorPosition Screen position for menu center
 * @param items List of menu items to display
 * @param onItemSelected Callback when an item is selected (receives item id)
 * @param onDismiss Callback to close the menu
 * @param config Visual configuration
 * @param modifier Optional modifier
 */
@Composable
fun RadialMenu(
    anchorPosition: Offset,
    items: List<RadialMenuItem>,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    config: RadialMenuConfig = RadialMenuConfig(),
    modifier: Modifier = Modifier,
) {
    Logger.d { "RadialMenu shown at anchor: $anchorPosition with ${items.size} items" }

    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // Trigger entrance animation
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Layer 1: Backdrop with optional blur (separate from menu items)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (config.useBlurBackdrop) {
                            Modifier.blur(
                                radius = config.blurRadius,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            )
                        } else {
                            Modifier
                        },
                    ).background(config.backdropColor),
        )

        // Layer 2: Gesture handling (transparent, catches all input)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Handle tap (for quick selection)
                        detectTapGestures(
                            onTap = { tapPosition ->
                                val radiusPx = with(density) { config.radius.toPx() }
                                val itemSizePx = with(density) { config.itemSize.toPx() }

                                Logger.d { "RadialMenu: Tap at $tapPosition" }

                                // Find which item was tapped
                                val tappedItem =
                                    items.find { item ->
                                        val itemPosition =
                                            calculateRadialPosition(
                                                anchor = anchorPosition,
                                                radius = radiusPx,
                                                angle = item.angle,
                                            )
                                        distance(tapPosition, itemPosition) < itemSizePx / 2
                                    }

                                if (tappedItem != null) {
                                    Logger.i { "✓ RadialMenu: Selected ${tappedItem.id} via tap" }
                                    onItemSelected(tappedItem.id)
                                    onDismiss()
                                } else {
                                    Logger.d { "RadialMenu: Tap outside items, dismissing" }
                                    onDismiss()
                                }
                            },
                        )
                    }.pointerInput(Unit) {
                        // Handle drag (for drag-to-select)
                        detectDragGestures(
                            onDragStart = { startPosition ->
                                Logger.d { "RadialMenu: Drag started at $startPosition" }
                            },
                            onDrag = { change, _ ->
                                val dragPosition = change.position
                                val radiusPx = with(density) { config.radius.toPx() }
                                val itemSizePx = with(density) { config.itemSize.toPx() }

                                val previousSelection = selectedItemId
                                selectedItemId =
                                    items
                                        .find { item ->
                                            val itemPosition =
                                                calculateRadialPosition(
                                                    anchor = anchorPosition,
                                                    radius = radiusPx,
                                                    angle = item.angle,
                                                )
                                            distance(dragPosition, itemPosition) < itemSizePx / 2
                                        }?.id

                                if (selectedItemId != previousSelection) {
                                    Logger.d { "RadialMenu: Drag selection changed to $selectedItemId" }
                                }
                            },
                            onDragEnd = {
                                Logger.d { "RadialMenu: Drag ended, selectedItem: $selectedItemId" }
                                selectedItemId?.let { id ->
                                    Logger.i { "✓ RadialMenu: Selected $id via drag" }
                                    onItemSelected(id)
                                }
                                onDismiss()
                            },
                            onDragCancel = {
                                Logger.d { "RadialMenu: Drag cancelled" }
                                onDismiss()
                            },
                        )
                    },
        )

        // Layer 3: Menu items (rendered above backdrop, NOT blurred)
        items.forEachIndexed { index, item ->
            val itemPosition =
                calculateRadialPosition(
                    anchor = anchorPosition,
                    radius = with(density) { config.radius.toPx() },
                    angle = item.angle,
                )

            RadialMenuItemComposable(
                icon = item.icon,
                contentDescription = item.contentDescription,
                position = itemPosition,
                isDestructive = item.isDestructive,
                isSelected = selectedItemId == item.id,
                size = config.itemSize,
                isVisible = isVisible,
                animationDelay = index * 30, // Stagger animation
            )
        }
    }
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
 * Individual radial menu item (circular button with animation).
 */
@Composable
private fun RadialMenuItemComposable(
    icon: ImageVector,
    contentDescription: String,
    position: Offset,
    isDestructive: Boolean,
    isSelected: Boolean,
    size: Dp,
    isVisible: Boolean,
    animationDelay: Int,
) {
    val density = LocalDensity.current

    // Scale animation for entrance
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(animationDelay.toLong())
            animationTriggered = true
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "menuItemScale",
    )

    // Selection scale boost
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh,
            ),
        label = "selectionScale",
    )

    Box(
        modifier =
            Modifier
                .offset(
                    x = with(density) { position.x.toDp() } - size / 2,
                    y = with(density) { position.y.toDp() } - size / 2,
                ).size(size)
                .scale(scale * selectionScale)
                .shadow(
                    elevation = if (isSelected) 12.dp else 6.dp,
                    shape = CircleShape,
                ).background(
                    color =
                        when {
                            isDestructive && isSelected ->
                                MaterialTheme.colorScheme.error.copy(alpha = 0.95f)

                            isDestructive ->
                                MaterialTheme.colorScheme.errorContainer

                            isSelected ->
                                MaterialTheme.colorScheme.primaryContainer

                            else ->
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        },
                    shape = CircleShape,
                ).border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color =
                        if (isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        },
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint =
                when {
                    isDestructive && isSelected -> MaterialTheme.colorScheme.onError
                    isDestructive -> MaterialTheme.colorScheme.error
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Calculate screen-space distance between two points.
 */
private fun distance(
    a: Offset,
    b: Offset
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}
