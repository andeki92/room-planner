# Phase 1.5: Dimension Constraints & Annotations - Implementation Plan

**Goal**: Implement dimension constraints to enable precise CAD drawing with locked measurements and parametric editing.

**Current State**:
- ✅ Phase 1.1: Basic drawing (tap to place, continuous lines)
- ✅ Phase 1.2: Pan/zoom navigation (planned)
- ✅ Phase 1.3: Smart snapping (planned)
- ✅ Phase 1.4: Selection & editing (planned)
- ❌ No dimension constraints (can't lock line to specific length)
- ❌ No dimension labels (can't see measurements)
- ❌ No constraint solver (can't maintain dimensions when editing)

**Target State**:
- Tap line → Set dimension (e.g., "240 cm")
- Line locks to that length (constraint enforced)
- Visual dimension labels on all lines
- Moving vertices maintains dimension constraints
- Conflicting constraints detected and reported
- Parametric editing: change dimension → geometry updates

---

## Overview

Dimension constraints are **essential for CAD** - they transform sketches into precise construction documents. Without dimensions, contractors can't build from your plans.

**Key Capabilities**:
1. **Lock line lengths** - "This wall is exactly 240 cm"
2. **Visual feedback** - Dimension labels on all lines
3. **Parametric editing** - Change dimension → geometry adjusts automatically
4. **Constraint solver** - Maintains all constraints simultaneously
5. **Building code foundation** - Clearances, minimums depend on dimensions

**Why Phase 1.5 (Not Phase 2)?**
- More important than BREP topology for contractors
- Required for real floor plans (not just sketches)
- Foundation for building code validation
- Enables parametric workflows (edit history)

**Architecture Pattern**: Event-driven constraints
- User sets dimension → emits `ConstraintEvent.ConstraintAdded`
- `ConstraintSolver` listens → enforces dimension
- Iterative relaxation (like spec.md § 5.4)
- Visual layer renders dimension labels

---

## Phase 1.5.1: Constraint Data Model

### 1.1 Extend AppState with Constraints

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/AppState.kt`

**Change**: Add constraints field

```kotlin
@Serializable
data class AppState(
    // ... existing fields ...

    // Phase 1.4: Selection
    val selectedVertices: Set<String> = emptySet(),
    val selectedLines: Set<String> = emptySet(),

    // Phase 1.5: Constraints (NEW)
    val constraints: Map<String, Constraint> = emptyMap(),
    val showDimensions: Boolean = true,  // Toggle dimension labels
    val dimensionPrecision: Int = 1,     // Decimal places (e.g., 240.5 cm)
) {
    // ... existing methods ...

    /**
     * Helper: Add constraint immutably.
     */
    fun withConstraint(constraint: Constraint) = copy(
        constraints = constraints + (constraint.id to constraint)
    )

    /**
     * Helper: Remove constraint.
     */
    fun withoutConstraint(constraintId: String) = copy(
        constraints = constraints - constraintId
    )

    /**
     * Helper: Get constraints affecting a line.
     */
    fun getConstraintsForLine(lineId: String): List<Constraint> =
        constraints.values.filter { constraint ->
            when (constraint) {
                is Constraint.Distance -> constraint.lineId == lineId
                is Constraint.Angle -> constraint.lineId1 == lineId || constraint.lineId2 == lineId
                // ... other constraint types
            }
        }
}
```

### 1.2 Define Constraint Types

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/models/Constraint.kt` (NEW)

```kotlin
package com.roomplanner.data.models

import com.roomplanner.common.generateUUID
import kotlinx.serialization.Serializable

/**
 * Geometric constraint definitions.
 * Phase 1.5: Distance constraints (lock line lengths)
 * Future: Angle, parallel, perpendicular, etc.
 */
@Serializable
sealed interface Constraint {
    val id: String
    val enabled: Boolean

    /**
     * Distance constraint: Lock line to specific length.
     * Most important constraint for CAD.
     */
    @Serializable
    data class Distance(
        override val id: String = generateUUID(),
        val lineId: String,              // Line to constrain
        val distance: Double,            // Target length (world units, cm or inches)
        override val enabled: Boolean = true,
        val userSet: Boolean = true      // User-defined (vs auto-generated)
    ) : Constraint

    /**
     * Angle constraint: Lock angle between two lines.
     * Example: 90° for perpendicular walls
     * Phase 1.5: Not implemented yet (Phase 2)
     */
    @Serializable
    data class Angle(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        val angle: Double,               // Degrees (0-360)
        override val enabled: Boolean = true,
        val userSet: Boolean = true
    ) : Constraint

    /**
     * Parallel constraint: Keep two lines parallel.
     * Phase 1.5: Not implemented yet (Phase 2)
     */
    @Serializable
    data class Parallel(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        override val enabled: Boolean = true
    ) : Constraint

    /**
     * Perpendicular constraint: Keep two lines at 90°.
     * Phase 1.5: Not implemented yet (Phase 2)
     */
    @Serializable
    data class Perpendicular(
        override val id: String = generateUUID(),
        val lineId1: String,
        val lineId2: String,
        override val enabled: Boolean = true
    ) : Constraint
}

/**
 * Constraint solver status.
 * Reports convergence, conflicts, etc.
 */
@Serializable
data class ConstraintSolverStatus(
    val converged: Boolean = false,
    val iterations: Int = 0,
    val maxError: Double = 0.0,
    val conflicts: List<ConstraintConflict> = emptyList()
)

/**
 * Constraint conflict report.
 * When constraints are impossible to satisfy simultaneously.
 */
@Serializable
data class ConstraintConflict(
    val constraint1: String,  // Constraint ID
    val constraint2: String,  // Conflicting constraint ID
    val description: String   // Human-readable explanation
)
```

**Rationale**:
- Start with `Distance` constraint only (Phase 1.5)
- Other constraint types designed but not implemented (Phase 2)
- `enabled` flag allows temporary disabling
- `userSet` distinguishes user constraints from auto-generated

---

## Phase 1.5.2: Constraint Events

### 2.1 Add Constraint Events

**File**: `shared/src/commonMain/kotlin/com/roomplanner/data/events/AppEvent.kt`

**Change**: Add `ConstraintEvent` sealed interface

```kotlin
/**
 * Constraint events (Phase 1.5)
 * User interactions with dimension constraints.
 */
sealed interface ConstraintEvent : AppEvent {
    /**
     * User added a dimension constraint to a line.
     */
    data class ConstraintAdded(
        val constraint: Constraint
    ) : ConstraintEvent

    /**
     * User removed a constraint.
     */
    data class ConstraintRemoved(
        val constraintId: String
    ) : ConstraintEvent

    /**
     * User edited an existing constraint (changed dimension).
     */
    data class ConstraintModified(
        val constraintId: String,
        val newConstraint: Constraint
    ) : ConstraintEvent

    /**
     * User toggled constraint enabled state.
     */
    data class ConstraintToggled(
        val constraintId: String,
        val enabled: Boolean
    ) : ConstraintEvent

    /**
     * Constraint solver requested (after geometry change).
     * Internal event (not directly user-triggered).
     */
    data object SolveConstraints : ConstraintEvent
}
```

---

## Phase 1.5.3: Constraint Solver

### 3.1 Implement Iterative Constraint Solver

**File**: `shared/src/commonMain/kotlin/com/roomplanner/domain/constraints/ConstraintSolver.kt` (NEW)

```kotlin
package com.roomplanner.domain.constraints

import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.ConstraintEvent
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.AppState
import com.roomplanner.data.models.Constraint
import com.roomplanner.data.models.ConstraintSolverStatus
import com.roomplanner.domain.geometry.Point2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ConstraintSolver maintains geometric constraints using iterative relaxation.
 *
 * Algorithm: Gauss-Seidel relaxation
 * - For each constraint, calculate error
 * - Apply correction proportional to error
 * - Repeat until converged (error < tolerance) or max iterations
 *
 * Based on spec.md § 5.4 Constraint System
 *
 * Design rationale:
 * - Iterative (not analytical) - handles over-constrained systems gracefully
 * - Local corrections - each constraint moves vertices slightly
 * - Respects fixed vertices - constraints don't move locked vertices
 * - Converges quickly for typical floor plans (rectangular rooms)
 */
class ConstraintSolver(
    private val eventBus: EventBus,
    private val stateManager: StateManager,
) {
    companion object {
        const val MAX_ITERATIONS = 100
        const val TOLERANCE = 0.001  // 0.001 cm = 0.01 mm (sub-millimeter precision)
        const val RELAXATION_FACTOR = 0.5  // Damping to prevent oscillation
    }

    init {
        Logger.i { "✓ ConstraintSolver initialized" }

        // Listen to constraint events
        eventBus.events
            .filterIsInstance<ConstraintEvent>()
            .onEach { event -> handleEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private suspend fun handleEvent(event: ConstraintEvent) {
        when (event) {
            is ConstraintEvent.ConstraintAdded -> {
                stateManager.updateState { it.withConstraint(event.constraint) }
                solveConstraints()
            }
            is ConstraintEvent.ConstraintRemoved -> {
                stateManager.updateState { it.withoutConstraint(event.constraintId) }
            }
            is ConstraintEvent.ConstraintModified -> {
                stateManager.updateState {
                    it.withoutConstraint(event.constraintId)
                        .withConstraint(event.newConstraint)
                }
                solveConstraints()
            }
            is ConstraintEvent.ConstraintToggled -> {
                val state = stateManager.state.value
                val constraint = state.constraints[event.constraintId] ?: return
                val updated = when (constraint) {
                    is Constraint.Distance -> constraint.copy(enabled = event.enabled)
                    is Constraint.Angle -> constraint.copy(enabled = event.enabled)
                    is Constraint.Parallel -> constraint.copy(enabled = event.enabled)
                    is Constraint.Perpendicular -> constraint.copy(enabled = event.enabled)
                }
                stateManager.updateState {
                    it.copy(constraints = it.constraints + (event.constraintId to updated))
                }
                if (event.enabled) solveConstraints()
            }
            is ConstraintEvent.SolveConstraints -> {
                solveConstraints()
            }
        }
    }

    /**
     * Main solver loop: iteratively apply constraints until convergence.
     */
    private suspend fun solveConstraints() {
        val state = stateManager.state.value
        val constraints = state.constraints.values.filter { it.enabled }

        if (constraints.isEmpty()) {
            Logger.d { "  No constraints to solve" }
            return
        }

        Logger.d { "→ Solving ${constraints.size} constraints..." }

        repeat(MAX_ITERATIONS) { iteration ->
            var maxError = 0.0

            constraints.forEach { constraint ->
                val error = applyConstraint(constraint, stateManager.state.value)
                maxError = max(maxError, abs(error))
            }

            Logger.d { "  Iteration $iteration: max error = $maxError" }

            if (maxError < TOLERANCE) {
                Logger.i { "✓ Constraints converged in $iteration iterations (error: $maxError)" }
                return
            }
        }

        Logger.w { "⚠ Constraints did not converge (max iterations reached)" }
    }

    /**
     * Apply single constraint, return error magnitude.
     */
    private fun applyConstraint(constraint: Constraint, state: AppState): Double {
        return when (constraint) {
            is Constraint.Distance -> applyDistanceConstraint(constraint, state)
            is Constraint.Angle -> 0.0  // Phase 2
            is Constraint.Parallel -> 0.0  // Phase 2
            is Constraint.Perpendicular -> 0.0  // Phase 2
        }
    }

    /**
     * Apply distance constraint: lock line to specific length.
     *
     * Algorithm:
     * 1. Calculate current line length
     * 2. Calculate error = current - target
     * 3. Move both endpoints toward/away to correct error
     * 4. Respect fixed vertices (don't move them)
     */
    private fun applyDistanceConstraint(
        constraint: Constraint.Distance,
        state: AppState
    ): Double {
        val line = state.lines.find { it.id == constraint.lineId } ?: return 0.0

        val v1 = state.vertices[line.geometry.startVertexId] ?: return 0.0
        val v2 = state.vertices[line.geometry.endVertexId] ?: return 0.0

        // Current length
        val currentLength = v1.position.distanceTo(v2.position)

        // Error
        val error = currentLength - constraint.distance

        if (abs(error) < TOLERANCE) {
            return error  // Already satisfied
        }

        // Direction vector (v1 → v2)
        val dx = v2.position.x - v1.position.x
        val dy = v2.position.y - v1.position.y
        val length = sqrt(dx * dx + dy * dy)

        if (length < 0.0001) {
            return error  // Degenerate line (can't fix)
        }

        // Unit direction vector
        val ux = dx / length
        val uy = dy / length

        // Correction magnitude (split between endpoints)
        val correction = error * RELAXATION_FACTOR

        // Move vertices (unless fixed)
        if (!v1.fixed && !v2.fixed) {
            // Move both vertices (split correction)
            val halfCorrection = correction / 2.0

            val newV1 = v1.copy(
                position = Point2(
                    v1.position.x + ux * halfCorrection,
                    v1.position.y + uy * halfCorrection
                )
            )

            val newV2 = v2.copy(
                position = Point2(
                    v2.position.x - ux * halfCorrection,
                    v2.position.y - uy * halfCorrection
                )
            )

            stateManager.updateState { state ->
                state.copy(
                    vertices = state.vertices +
                        (v1.id to newV1) +
                        (v2.id to newV2)
                )
            }
        } else if (!v1.fixed) {
            // Only move v1 (v2 is fixed)
            val newV1 = v1.copy(
                position = Point2(
                    v1.position.x + ux * correction,
                    v1.position.y + uy * correction
                )
            )

            stateManager.updateState { state ->
                state.copy(vertices = state.vertices + (v1.id to newV1))
            }
        } else if (!v2.fixed) {
            // Only move v2 (v1 is fixed)
            val newV2 = v2.copy(
                position = Point2(
                    v2.position.x - ux * correction,
                    v2.position.y - uy * correction
                )
            )

            stateManager.updateState { state ->
                state.copy(vertices = state.vertices + (v2.id to newV2))
            }
        }
        // else: both fixed - can't satisfy constraint (conflict)

        return error
    }
}
```

**Key Algorithm Details**:
- **Gauss-Seidel relaxation**: Each constraint applied in sequence
- **Damping factor (0.5)**: Prevents oscillation
- **Fixed vertices respected**: Locked vertices don't move
- **Converges quickly**: Typical floor plans converge in 5-10 iterations
- **Graceful degradation**: Over-constrained systems don't crash

---

## Phase 1.5.4: Dimension Input UI

### 4.1 Add Dimension Dialog

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DimensionInputDialog.kt` (NEW)

```kotlin
package com.roomplanner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.localization.strings

/**
 * Dialog for entering dimension constraints.
 * Optimized for quick numeric input (phone keypad).
 */
@Composable
fun DimensionInputDialog(
    initialValue: Double?,
    units: MeasurementUnits,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = strings()
    var inputText by remember {
        mutableStateOf(initialValue?.toString() ?: "")
    }

    val unitLabel = when (units) {
        MeasurementUnits.IMPERIAL -> "inches"
        MeasurementUnits.METRIC -> "cm"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.setDimension) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("${strings.length} ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick presets (common dimensions)
                Text(
                    strings.commonDimensions,
                    style = MaterialTheme.typography.labelSmall
                )

                val presets = when (units) {
                    MeasurementUnits.METRIC -> listOf(60.0, 80.0, 120.0, 180.0, 240.0, 300.0)
                    MeasurementUnits.IMPERIAL -> listOf(24.0, 30.0, 36.0, 48.0, 60.0, 96.0)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { preset ->
                        OutlinedButton(
                            onClick = { inputText = preset.toString() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(preset.toInt().toString())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    inputText.toDoubleOrNull()?.let { value ->
                        if (value > 0) {
                            onConfirm(value)
                            onDismiss()
                        }
                    }
                },
                enabled = inputText.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text(strings.setButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        },
        modifier = modifier
    )
}
```

### 4.2 Integrate Dialog in DrawingCanvas

**File**: `shared/src/commonMain/kotlin/com/roomplanner/ui/components/DrawingCanvas.kt`

**Change**: Add dimension input flow

```kotlin
@Composable
fun DrawingCanvas(
    state: AppState,
    eventBus: EventBus,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var showDimensionDialog by remember { mutableStateOf(false) }
    var selectedLineForDimension by remember { mutableStateOf<String?>(null) }

    // ... existing canvas code ...

    // Dimension input dialog
    if (showDimensionDialog && selectedLineForDimension != null) {
        val line = state.lines.find { it.id == selectedLineForDimension }
        val currentConstraint = state.getConstraintsForLine(selectedLineForDimension!!)
            .filterIsInstance<Constraint.Distance>()
            .firstOrNull()

        DimensionInputDialog(
            initialValue = currentConstraint?.distance,
            units = state.settings.measurementUnits,
            onDismiss = {
                showDimensionDialog = false
                selectedLineForDimension = null
            },
            onConfirm = { dimension ->
                coroutineScope.launch {
                    val constraint = Constraint.Distance(
                        lineId = selectedLineForDimension!!,
                        distance = dimension
                    )
                    eventBus.emit(ConstraintEvent.ConstraintAdded(constraint))
                }
            }
        )
    }
}
```

---

## Phase 1.5.5: Visual Dimension Labels

### 5.1 Add Dimension Label Rendering

**File**: `DrawingCanvas.kt`

**Change**: Add dimension label drawing layer

```kotlin
private fun DrawScope.drawDimensionLabels(
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    if (!state.showDimensions) return

    state.constraints.values.forEach { constraint ->
        when (constraint) {
            is Constraint.Distance -> {
                drawDistanceDimension(constraint, state, camera, config)
            }
            // Other constraint types (Phase 2)
            else -> {}
        }
    }
}

private fun DrawScope.drawDistanceDimension(
    constraint: Constraint.Distance,
    state: AppState,
    camera: CameraTransform,
    config: com.roomplanner.data.models.DrawingConfig,
) {
    val line = state.lines.find { it.id == constraint.lineId } ?: return
    val v1 = state.vertices[line.geometry.startVertexId] ?: return
    val v2 = state.vertices[line.geometry.endVertexId] ?: return

    // Line midpoint (label position)
    val midpoint = Point2(
        (v1.position.x + v2.position.x) / 2.0,
        (v1.position.y + v2.position.y) / 2.0
    )
    val midpointScreen = worldToScreen(midpoint, camera)

    // Perpendicular offset (label above/beside line)
    val dx = v2.position.x - v1.position.x
    val dy = v2.position.y - v1.position.y
    val length = sqrt(dx * dx + dy * dy)

    if (length < 0.001) return

    // Perpendicular unit vector
    val perpX = -dy / length
    val perpY = dx / length

    // Offset distance (pixels)
    val offsetDistance = 20f / camera.zoom

    val labelPosition = Offset(
        midpointScreen.x + (perpX * offsetDistance).toFloat(),
        midpointScreen.y + (perpY * offsetDistance).toFloat()
    )

    // Format dimension text
    val dimensionText = formatDimension(
        constraint.distance,
        state.settings.measurementUnits,
        state.dimensionPrecision
    )

    // Draw background rect (for readability)
    val textPaint = android.text.TextPaint().apply {
        textSize = 12f / camera.zoom
        color = android.graphics.Color.BLACK
    }
    val textBounds = android.graphics.Rect()
    textPaint.getTextBounds(dimensionText, 0, dimensionText.length, textBounds)

    drawRect(
        color = Color.White.copy(alpha = 0.9f),
        topLeft = Offset(
            labelPosition.x - textBounds.width() / 2f - 4f,
            labelPosition.y - textBounds.height() / 2f - 4f
        ),
        size = Size(
            textBounds.width() + 8f,
            textBounds.height() + 8f
        )
    )

    // Draw text
    drawContext.canvas.nativeCanvas.drawText(
        dimensionText,
        labelPosition.x,
        labelPosition.y,
        textPaint
    )

    // Draw dimension arrows (optional - for clarity)
    drawDimensionArrows(
        worldToScreen(v1.position, camera),
        worldToScreen(v2.position, camera),
        camera
    )
}

/**
 * Format dimension with units.
 */
private fun formatDimension(
    value: Double,
    units: MeasurementUnits,
    precision: Int
): String {
    val formatted = String.format("%.${precision}f", value)
    val unit = when (units) {
        MeasurementUnits.METRIC -> "cm"
        MeasurementUnits.IMPERIAL -> "\""
    }
    return "$formatted $unit"
}

/**
 * Draw dimension arrows at line endpoints.
 */
private fun DrawScope.drawDimensionArrows(
    start: Offset,
    end: Offset,
    camera: CameraTransform
) {
    val arrowSize = 8f / camera.zoom
    val dx = end.x - start.x
    val dy = end.y - start.y
    val angle = atan2(dy, dx)

    // Start arrow
    val arrow1Path = Path().apply {
        moveTo(start.x, start.y)
        lineTo(
            start.x + arrowSize * cos(angle + 2.8).toFloat(),
            start.y + arrowSize * sin(angle + 2.8).toFloat()
        )
        moveTo(start.x, start.y)
        lineTo(
            start.x + arrowSize * cos(angle - 2.8).toFloat(),
            start.y + arrowSize * sin(angle - 2.8).toFloat()
        )
    }

    // End arrow (reversed)
    val arrow2Path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(
            end.x + arrowSize * cos(angle + Math.PI + 2.8).toFloat(),
            end.y + arrowSize * sin(angle + Math.PI + 2.8).toFloat()
        )
        moveTo(end.x, end.y)
        lineTo(
            end.x + arrowSize * cos(angle + Math.PI - 2.8).toFloat(),
            end.y + arrowSize * sin(angle + Math.PI - 2.8).toFloat()
        )
    }

    drawPath(arrow1Path, color = Color.Black, style = Stroke(width = 1f / camera.zoom))
    drawPath(arrow2Path, color = Color.Black, style = Stroke(width = 1f / camera.zoom))
}
```

---

## Phase 1.5.6: Integration & Workflow

### 6.1 Update GeometryManager

**File**: `GeometryManager.kt`

**Change**: Trigger constraint solver after geometry changes

```kotlin
private suspend fun handleEvent(event: GeometryEvent) {
    when (event) {
        is GeometryEvent.PointPlaced -> {
            // ... existing logic ...

            // After creating edge, trigger constraint solver
            if (createdEdge) {
                eventBus.emit(ConstraintEvent.SolveConstraints)
            }
        }
        // ... other events ...
    }
}
```

### 6.2 Update SelectionManager

**File**: `SelectionManager.kt`

**Change**: After dragging vertex, solve constraints

```kotlin
private suspend fun handleVertexDragEnded(event: SelectionEvent.VertexDragEnded) {
    Logger.i { "✓ Vertex moved: ${event.vertexId} → (${event.finalPosition.x}, ${event.finalPosition.y})" }

    // Trigger constraint solver (maintain dimensions)
    eventBus.emit(ConstraintEvent.SolveConstraints)
}
```

### 6.3 Register ConstraintSolver in DI

**File**: `shared/src/commonMain/kotlin/com/roomplanner/di/AppModule.kt`

```kotlin
val commonModule = module {
    single { EventBus() }
    single { StateManager() }
    single { GeometryManager(get(), get()) }
    single { SelectionManager(get(), get()) }
    single { ConstraintSolver(get(), get()) }  // NEW
}
```

---

## Phase 1.5.7: User Workflow

**Typical Usage**:

1. **Draw walls** (Phase 1.1)
   - Tap to place vertices
   - Lines connect automatically

2. **Set dimensions** (Phase 1.5)
   - Tap line
   - Dimension dialog appears
   - Enter "240" → 240 cm constraint added
   - Line locks to that length

3. **Edit geometry** (Phase 1.4)
   - Drag vertex
   - Constraint solver maintains 240 cm dimension
   - Other connected lines adjust

4. **Change dimension** (Phase 1.5)
   - Tap dimension label
   - Change "240" → "300"
   - Geometry updates automatically (parametric!)

5. **Visual feedback** (Phase 1.5)
   - All constrained lines show dimensions
   - Over-constrained lines highlighted in red (conflict)
   - Solver status in status bar

---

## Phase 1.5.8: Localization Strings

**File**: `Strings.kt`

**Add**:
```kotlin
interface Strings {
    // ... existing strings ...

    // Phase 1.5: Dimensions
    val setDimension: String
    val length: String
    val commonDimensions: String
    fun constraintAdded(dimension: String): String
    fun constraintConflict(): String
    val showDimensions: String
    val hideDimensions: String
}

// English
object EnglishStrings : Strings {
    override val setDimension = "Set Dimension"
    override val length = "Length"
    override val commonDimensions = "Common:"
    override fun constraintAdded(dimension: String) = "Dimension locked: $dimension"
    override fun constraintConflict() = "Constraint conflict detected"
    override val showDimensions = "Show Dimensions"
    override val hideDimensions = "Hide Dimensions"
}

// Norwegian
object NorwegianStrings : Strings {
    override val setDimension = "Angi Dimensjon"
    override val length = "Lengde"
    override val commonDimensions = "Vanlige:"
    override fun constraintAdded(dimension: String) = "Dimensjon låst: $dimension"
    override fun constraintConflict() = "Konflikt oppdaget"
    override val showDimensions = "Vis Dimensjoner"
    override val hideDimensions = "Skjul Dimensjoner"
}
```

---

## Phase 1.5.9: Testing & Validation

### 9.1 Manual Testing

**Test Case 1: Set Simple Dimension**
- [ ] Draw line (any length)
- [ ] Tap line → Dimension dialog appears
- [ ] Enter "200" → Confirm
- [ ] Line adjusts to exactly 200 cm
- [ ] Dimension label "200 cm" appears on line
- [ ] Console: "✓ Dimension locked: 200 cm"

**Test Case 2: Maintain Dimension While Editing**
- [ ] Create 200 cm line (A→B)
- [ ] Add dimension constraint
- [ ] Drag vertex A to new location
- [ ] Line length stays 200 cm (vertex B moves)
- [ ] Console: "✓ Constraints converged in 3 iterations"

**Test Case 3: Change Existing Dimension**
- [ ] Tap dimension label "200 cm"
- [ ] Dialog pre-fills with "200"
- [ ] Change to "300" → Confirm
- [ ] Line extends to 300 cm
- [ ] Label updates to "300 cm"

**Test Case 4: Rectangular Room with 4 Dimensions**
- [ ] Draw 4 lines forming rectangle
- [ ] Set dimensions: 300cm, 200cm, 300cm, 200cm
- [ ] Drag any vertex → shape stays rectangular
- [ ] All dimensions maintained
- [ ] Console: "✓ Constraints converged in 5 iterations"

**Test Case 5: Over-Constrained System**
- [ ] Draw triangle (3 lines)
- [ ] Set all 3 dimensions: 100cm, 100cm, 100cm (equilateral)
- [ ] Add 4th dimension to one side: 150cm (conflict!)
- [ ] Console: "⚠ Constraint conflict detected"
- [ ] Conflicting constraint highlighted in red

**Test Case 6: Fixed Vertex**
- [ ] Draw line with dimension 200 cm
- [ ] Lock vertex A (mark as fixed)
- [ ] Drag vertex A → doesn't move (locked)
- [ ] Drag vertex B → moves, dimension maintained

### 9.2 Edge Cases

- [ ] Dimension on very short line (< 1cm) → Prevents degeneracy
- [ ] Dimension on very long line (> 1000cm) → Works correctly
- [ ] 100 constraints on complex floor plan → Converges in <500ms
- [ ] Undo dimension constraint → Line unconstrained, dimension label disappears
- [ ] Toggle dimension visibility → Labels hide but constraints still active
- [ ] Zoom to 10x → Dimension labels stay readable

### 9.3 Console Output Validation

**Expected logs**:
```
→ Constraint added: Distance (line: abc-123, distance: 200.0 cm)
→ Solving 1 constraints...
  Iteration 0: max error = 15.2
  Iteration 1: max error = 7.6
  Iteration 2: max error = 3.8
  Iteration 3: max error = 0.0005
✓ Constraints converged in 3 iterations (error: 0.0005)
```

---

## Success Criteria

✅ Phase 1.5 complete when:

**Functional Requirements**:
- [ ] Can set dimension on any line (tap → enter value)
- [ ] Dimension constraint enforced (line locks to length)
- [ ] Visual dimension labels render correctly
- [ ] Constraint solver converges (<100 iterations)
- [ ] Editing geometry maintains dimensions
- [ ] Can change dimensions (parametric editing)
- [ ] Over-constrained systems detected

**Quality Requirements**:
- [ ] Solver converges in <500ms for typical floor plans
- [ ] Dimension labels readable at all zoom levels
- [ ] No crashes with conflicting constraints
- [ ] Undo/redo works with constraints
- [ ] Console logs show convergence status

**Architecture Requirements**:
- [ ] Event-driven (ConstraintEvent types)
- [ ] Immutable state (Constraint in AppState)
- [ ] Constraint solver follows spec.md § 5.4
- [ ] Localized strings (English + Norwegian)
- [ ] Follows CLAUDE.md patterns

---

## File Changes Summary

**New Files**:
1. `Constraint.kt` - Constraint data models
2. `ConstraintSolver.kt` - Iterative solver
3. `DimensionInputDialog.kt` - UI for dimension entry

**Modified Files**:
1. `AppState.kt` - Add constraints field
2. `AppEvent.kt` - Add ConstraintEvent types
3. `DrawingCanvas.kt` - Dimension label rendering, dialog integration
4. `GeometryManager.kt` - Trigger solver after geometry changes
5. `SelectionManager.kt` - Trigger solver after vertex drag
6. `AppModule.kt` - Register ConstraintSolver
7. `Strings.kt` - Add dimension-related strings

---

## Architecture Notes

**Why Iterative Solver (Not Analytical)?**
- **Handles over-constraints gracefully** - Doesn't crash when impossible
- **Extensible** - Easy to add new constraint types
- **Predictable** - User sees gradual convergence
- **Proven approach** - Used by SolveSpace, FreeCAD, etc.

**Performance**:
- Typical convergence: 5-10 iterations
- Typical time: <100ms for 10 constraints
- Scales linearly: O(n) per iteration where n = # constraints
- Real floor plans: 20-50 constraints, <500ms

**Future Optimizations** (not Phase 1.5):
- Analytical solver for simple cases (triangle, rectangle)
- Constraint graph analysis (detect conflicts early)
- Incremental solving (only affected constraints)
- GPU acceleration (parallel constraint evaluation)

---

## Next Phase

**Phase 2.0**: BREP Topology
- Replace simple edges with halfedge mesh
- Constraints work with BREP (edge IDs persist)
- Face area constraints (room must be ≥X sq ft)
- Angle constraints (perpendicular, parallel)
- Wall thickness (parallel offset curves with fixed distance)

**Phase 1.5 enables Phase 2** because:
- Constraint system architecture proven
- Solver handles complex scenarios
- Parametric editing foundation established
- Users can create precise rooms (not sketches)

---

## Implementation Order

1. **Phase 1.5.1** - Data models (Constraint, AppState changes)
2. **Phase 1.5.2** - Events (ConstraintEvent types)
3. **Phase 1.5.3** - Solver (iterative relaxation)
4. **Phase 1.5.4** - Input UI (dimension dialog)
5. **Phase 1.5.5** - Visual rendering (dimension labels)
6. **Phase 1.5.6** - Integration (trigger solver after geometry changes)
7. **Phase 1.5.7** - Localization (strings)
8. **Phase 1.5.8** - Testing (manual validation)

**Estimated Time**: 5-6 hours implementation + testing

**Critical Path**: Solver → Events → UI → Rendering
