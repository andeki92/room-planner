# Constraint Solver Redesign Plan (Graph-Based DOF Analysis)

## Executive Summary

Complete redesign of the geometric constraint solver using proper graph-based decomposition and degrees of freedom (DOF) analysis. This follows industry-standard approaches used in professional CAD systems like SolveSpace, FreeCAD, and D-Cubed.

**Current Status**: Broken - applying angle constraints causes entire drawing to "smoosh" because solver doesn't understand which vertices should move vs stay fixed.

**Goal**: Robust, predictable constraint solving that only affects the minimal set of vertices needed to satisfy each constraint.

---

## Research Sources

### Academic & Professional
1. **[Geometric constraint solving - Wikipedia](https://en.wikipedia.org/wiki/Geometric_constraint_solving)**
   - Overview: Graph-based decomposition, DOF analysis, sequential solving
   - Key concept: Transform undirected constraint graph → directed acyclic dependency graph

2. **[SolveSpace - Technology](https://solvespace.com/tech.pl)**
   - Professional CAD using modified Newton's method
   - Equations represented as symbolic algebra
   - Convergence failures indicate real constraint problems (not algorithm bugs)

3. **[2D Geometric Constraint Solver Tutorial](https://vmnnk.com/en/2023-10-18/2d-geometric-constraint-solver)**
   - Practical Python implementation using scipy.optimize.minimize with SLSQP
   - **Key insight**: Variable substitution reduces dimensionality
   - Example: Coincident points → single shared variable (not constraint equation)

4. **[Graph reduction method - ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0965997810001006)**
   - S-DR decomposes well-constrained systems into sequentially solvable sub-systems
   - Uses graph analysis to find solving order

5. **[Degrees of Freedom Analysis - Springer](https://link.springer.com/chapter/10.1007/978-3-642-60607-6_10)**
   - Geometric primitives have intrinsic DOF in their embedding space
   - Constraints reduce DOF of connected elements

### Code Examples
6. **[GitHub - kasznar/geometric-constraint-solver](https://github.com/kasznar/geometric-constraint-solver)**
   - Simple symbolic algebra system
   - Tree traversal for derivatives

7. **[pygeosolve Python library](https://seands.github.io/pygeosolve/)**
   - Solves lines and points using SciPy optimization
   - Clean API for constraint definition

---

## Problem Analysis

### Why Current Solver Fails

#### Issue 1: No Graph Analysis
```kotlin
// Current approach (WRONG):
constraints.forEach { constraint ->
    applyConstraint(constraint) // Modifies vertices blindly
}
```

**Problem**: All vertices move simultaneously. No understanding of dependencies.

**Example**: Setting angle constraint on line AB rotates it, but also moves all connected vertices (C, D, E...) even though they shouldn't be affected.

#### Issue 2: No DOF Tracking
- Point in 2D = **2 DOF** (x, y coordinates)
- Fixed point = **0 DOF** (anchored)
- Distance constraint removes **1 DOF** (length locked)
- Angle constraint removes **1 DOF** (rotation locked)

**Problem**: Solver doesn't track when system becomes over-constrained (DOF < 0).

#### Issue 3: No Decomposition
- Solver treats all constraints as one giant system
- Should decompose into:
  1. **Rigid clusters** (fully constrained, solvable analytically)
  2. **Semi-rigid** (partially constrained, needs iteration)
  3. **Free** (under-constrained, use default positions)

---

## Proposed Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────┐
│                  ConstraintGraphBuilder                  │
│  - Builds graph: vertices=elements, edges=constraints   │
│  - Tracks DOF for each element                          │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│               ConstraintGraphAnalyzer                    │
│  - Validates DOF (detect over/under-constrained)        │
│  - Finds rigid clusters (Tarjan's algorithm)            │
│  - Decomposes into solvable sub-problems                │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                  SequentialSolver                        │
│  - Solves rigid clusters first (analytical)             │
│  - Propagates to semi-rigid (iterative)                 │
│  - Leaves free elements unchanged                       │
└─────────────────────────────────────────────────────────┘
```

### Data Structures

#### ConstraintGraph
```kotlin
data class ConstraintGraph(
    val nodes: Map<String, GeometricElement>,
    val edges: Map<String, Constraint>,
    val adjacency: Map<String, List<String>>, // element ID → connected element IDs
)

sealed class GeometricElement {
    abstract val id: String
    abstract val dof: Int // Degrees of freedom

    data class Point(
        override val id: String,
        val position: Point2,
        val fixed: Boolean, // If fixed, DOF = 0
    ) : GeometricElement() {
        override val dof = if (fixed) 0 else 2
    }

    // Future: Line, Circle, Arc...
}
```

#### ConstraintCluster
```kotlin
data class ConstraintCluster(
    val elements: Set<String>, // Element IDs in this cluster
    val constraints: Set<String>, // Constraint IDs
    val totalDOF: Int, // Sum of element DOFs
    val constrainedDOF: Int, // DOFs removed by constraints
    val status: ClusterStatus,
)

enum class ClusterStatus {
    RIGID,          // totalDOF == constrainedDOF (fully constrained)
    SEMI_RIGID,     // 0 < totalDOF - constrainedDOF < totalDOF
    UNDER_CONSTRAINED, // totalDOF > constrainedDOF
    OVER_CONSTRAINED,  // totalDOF < constrainedDOF (ERROR!)
}
```

---

## Implementation Plan

### Phase 1: Graph Infrastructure (Week 1)

#### Step 1.1: Create ConstraintGraph data structure
**Files to create**:
- `/domain/constraints/ConstraintGraph.kt` - Graph data structure
- `/domain/constraints/GeometricElement.kt` - Element types with DOF

**Implementation**:
```kotlin
class ConstraintGraphBuilder(private val drawingState: ProjectDrawingState) {
    fun buildGraph(): ConstraintGraph {
        val nodes = mutableMapOf<String, GeometricElement>()
        val edges = mutableMapOf<String, Constraint>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        // Convert vertices → GeometricElement.Point
        drawingState.vertices.forEach { (id, vertex) ->
            nodes[id] = GeometricElement.Point(
                id = id,
                position = vertex.position,
                fixed = vertex.fixed
            )
        }

        // Convert constraints → edges
        drawingState.constraints.forEach { (id, constraint) ->
            edges[id] = constraint

            // Build adjacency list
            val elements = constraint.affectedElements()
            elements.forEach { elementId ->
                adjacency.getOrPut(elementId) { mutableListOf() }
                    .addAll(elements.filter { it != elementId })
            }
        }

        return ConstraintGraph(nodes, edges, adjacency)
    }
}

// Extension: Get elements affected by constraint
fun Constraint.affectedElements(): List<String> = when (this) {
    is Constraint.Distance -> listOf(lineId) // Line endpoints
    is Constraint.Angle -> listOf(lineId1, lineId2)
    is Constraint.Perpendicular -> listOf(lineId1, lineId2)
    is Constraint.Parallel -> listOf(lineId1, lineId2)
}
```

#### Step 1.2: Implement DOF calculation
**Files to modify**:
- `/domain/constraints/ConstraintGraphAnalyzer.kt` - DOF analysis

**Implementation**:
```kotlin
class ConstraintGraphAnalyzer(private val graph: ConstraintGraph) {

    /**
     * Calculate total DOF for a set of elements.
     */
    fun calculateTotalDOF(elementIds: Set<String>): Int {
        return elementIds.sumOf { id ->
            graph.nodes[id]?.dof ?: 0
        }
    }

    /**
     * Calculate DOF removed by constraints.
     */
    fun calculateConstrainedDOF(constraintIds: Set<String>): Int {
        return constraintIds.sumOf { id ->
            when (val constraint = graph.edges[id]) {
                is Constraint.Distance -> 1 // Removes 1 DOF (length)
                is Constraint.Angle -> 1 // Removes 1 DOF (rotation)
                is Constraint.Perpendicular -> 1 // Removes 1 DOF (90° angle)
                is Constraint.Parallel -> 1 // Removes 1 DOF (0° angle)
                else -> 0
            }
        }
    }

    /**
     * Check if adding a constraint would over-constrain the system.
     */
    fun wouldOverConstrain(newConstraint: Constraint): Boolean {
        val affectedElements = newConstraint.affectedElements()
            .flatMap { getConnectedElements(it) }
            .toSet()

        val currentConstraints = findConstraintsAffecting(affectedElements)

        val totalDOF = calculateTotalDOF(affectedElements)
        val currentConstrained = calculateConstrainedDOF(currentConstraints)
        val newConstrained = currentConstrained + 1 // Add new constraint

        return newConstrained > totalDOF
    }

    /**
     * Find all elements connected to given element (via constraints).
     */
    private fun getConnectedElements(elementId: String): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(elementId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)

            val neighbors = graph.adjacency[current] ?: emptyList()
            queue.addAll(neighbors.filter { it !in visited })
        }

        return visited
    }
}
```

**Test cases**:
1. Empty graph → DOF = 0
2. Single free point → DOF = 2
3. Single fixed point → DOF = 0
4. Two points with distance constraint → DOF = 2+2-1 = 3
5. Triangle (3 points, 3 distance constraints) → DOF = 6-3 = 3 (can translate/rotate)
6. Rectangle with all sides constrained → Check if over-constrained

---

### Phase 2: Graph Decomposition (Week 2)

#### Step 2.1: Identify rigid clusters
**Algorithm**: Use Tarjan's Strongly Connected Components

A cluster is **rigid** if:
- DOF_total - DOF_constrained == 3 (can only translate/rotate as unit)
- OR all elements are fixed (DOF = 0)

**Files to create**:
- `/domain/constraints/ClusterDetector.kt`

**Implementation**:
```kotlin
class ClusterDetector(private val graph: ConstraintGraph) {

    /**
     * Decompose graph into constraint clusters.
     * Uses modified Tarjan's algorithm to find strongly connected components.
     */
    fun findClusters(): List<ConstraintCluster> {
        val clusters = mutableListOf<ConstraintCluster>()
        val visited = mutableSetOf<String>()

        graph.nodes.keys.forEach { elementId ->
            if (elementId !in visited) {
                val cluster = exploreCluster(elementId, visited)
                clusters.add(cluster)
            }
        }

        return clusters
    }

    private fun exploreCluster(
        startElement: String,
        visited: MutableSet<String>
    ): ConstraintCluster {
        val elements = mutableSetOf<String>()
        val constraints = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startElement)

        while (queue.isNotEmpty()) {
            val elementId = queue.removeFirst()
            if (elementId in visited) continue
            visited.add(elementId)
            elements.add(elementId)

            // Find constraints connected to this element
            graph.edges.forEach { (constraintId, constraint) ->
                if (elementId in constraint.affectedElements()) {
                    constraints.add(constraintId)

                    // Add other elements from this constraint
                    constraint.affectedElements()
                        .filter { it !in visited }
                        .forEach { queue.add(it) }
                }
            }
        }

        val totalDOF = calculateTotalDOF(elements)
        val constrainedDOF = calculateConstrainedDOF(constraints)

        return ConstraintCluster(
            elements = elements,
            constraints = constraints,
            totalDOF = totalDOF,
            constrainedDOF = constrainedDOF,
            status = determineStatus(totalDOF, constrainedDOF)
        )
    }

    private fun determineStatus(totalDOF: Int, constrainedDOF: Int): ClusterStatus {
        return when {
            constrainedDOF > totalDOF -> ClusterStatus.OVER_CONSTRAINED
            constrainedDOF == totalDOF -> ClusterStatus.RIGID
            constrainedDOF > 0 -> ClusterStatus.SEMI_RIGID
            else -> ClusterStatus.UNDER_CONSTRAINED
        }
    }
}
```

#### Step 2.2: Variable substitution optimization
**Key insight from research**: Don't create constraint equations for coincident points - just use same variable!

**Example**:
```kotlin
// BAD (current): Two separate points with constraint equation
val p1 = Point2(x1, y1) // 2 DOF
val p2 = Point2(x2, y2) // 2 DOF
val constraint = Coincident(p1, p2) // Equation: distance(p1, p2) == 0
// Total: 4 DOF - 2 constraints (x1==x2, y1==y2) = 2 DOF

// GOOD: Single shared variable
val p = Point2(x, y) // 2 DOF
// Both references point to same variable
// Total: 2 DOF (no constraint needed!)
```

**Implementation**: When building graph, merge coincident vertices.

---

### Phase 3: Sequential Solver (Week 3)

#### Step 3.1: Solve rigid clusters analytically
**Files to create**:
- `/domain/constraints/RigidClusterSolver.kt`

For simple cases, use direct formulas instead of iteration:

```kotlin
class RigidClusterSolver {

    fun solveCluster(cluster: ConstraintCluster, graph: ConstraintGraph): Map<String, Point2> {
        // Special case: Triangle with all 3 sides constrained
        if (isTriangle(cluster)) {
            return solveTriangle(cluster, graph)
        }

        // Special case: Two points with distance constraint
        if (isTwoPointDistance(cluster)) {
            return solveTwoPointDistance(cluster, graph)
        }

        // General case: Use iterative solver
        return solveIteratively(cluster, graph)
    }

    private fun solveTriangle(cluster: ConstraintCluster, graph: ConstraintGraph): Map<String, Point2> {
        // Get 3 points and 3 distance constraints
        val points = cluster.elements.map { graph.nodes[it] as GeometricElement.Point }
        val constraints = cluster.constraints.map { graph.edges[it] as Constraint.Distance }

        // Use law of cosines to calculate positions
        // Given distances a, b, c and one fixed point:
        // 1. Place point A at origin (or keep if fixed)
        // 2. Place point B at distance c from A
        // 3. Calculate point C position using law of cosines

        val (a, b, c) = constraints.map { it.distance }
        val fixedPoint = points.find { it.fixed } ?: points[0]

        // ... analytical solution ...

        return mapOf(/* positions */)
    }

    private fun solveTwoPointDistance(cluster: ConstraintCluster, graph: ConstraintGraph): Map<String, Point2> {
        val points = cluster.elements.map { graph.nodes[it] as GeometricElement.Point }
        val constraint = cluster.constraints.first() as Constraint.Distance

        val (p1, p2) = points
        val targetDistance = constraint.distance

        // If one point is fixed, move the other along the line
        val (fixed, free) = if (p1.fixed) p1 to p2 else p2 to p1

        val direction = (free.position - fixed.position).normalize()
        val newPosition = fixed.position + direction * targetDistance

        return mapOf(free.id to newPosition)
    }
}
```

#### Step 3.2: Solve semi-rigid clusters iteratively
**Files to modify**:
- Refactor existing ConstraintSolver to only work on single cluster

```kotlin
class IterativeClusterSolver {

    fun solveCluster(
        cluster: ConstraintCluster,
        graph: ConstraintGraph,
        maxIterations: Int = 100
    ): Map<String, Point2> {
        // Only solve elements in THIS cluster (not global!)
        val localElements = cluster.elements.mapNotNull { graph.nodes[it] }
        val localConstraints = cluster.constraints.mapNotNull { graph.edges[it] }

        // Use Position-Based Dynamics approach (already implemented)
        repeat(maxIterations) { iteration ->
            var maxError = 0.0

            localConstraints.forEach { constraint ->
                val error = applyConstraintToCluster(constraint, localElements)
                maxError = max(maxError, abs(error))
            }

            if (maxError < TOLERANCE) {
                Logger.i { "✓ Cluster converged in $iteration iterations" }
                return extractPositions(localElements)
            }
        }

        Logger.w { "⚠ Cluster did not converge" }
        return extractPositions(localElements)
    }

    private fun applyConstraintToCluster(
        constraint: Constraint,
        elements: List<GeometricElement>
    ): Double {
        // Apply constraint ONLY to elements in this cluster
        // Use existing PBD formulas
        // ...
    }
}
```

#### Step 3.3: Orchestrate solving order
**Files to create**:
- `/domain/constraints/ConstraintOrchestrator.kt`

```kotlin
class ConstraintOrchestrator(
    private val graphBuilder: ConstraintGraphBuilder,
    private val analyzer: ConstraintGraphAnalyzer,
    private val clusterDetector: ClusterDetector,
    private val rigidSolver: RigidClusterSolver,
    private val iterativeSolver: IterativeClusterSolver,
) {

    suspend fun solve(drawingState: ProjectDrawingState): ProjectDrawingState {
        // 1. Build constraint graph
        val graph = graphBuilder.buildGraph()

        // 2. Decompose into clusters
        val clusters = clusterDetector.findClusters()

        // 3. Check for over-constraints
        clusters.forEach { cluster ->
            if (cluster.status == ClusterStatus.OVER_CONSTRAINED) {
                throw OverConstrainedException("Cluster ${cluster.elements} is over-constrained")
            }
        }

        // 4. Sort clusters by solving order (dependencies)
        val sortedClusters = topologicalSort(clusters, graph)

        // 5. Solve each cluster in order
        val updatedPositions = mutableMapOf<String, Point2>()

        sortedClusters.forEach { cluster ->
            val solution = when (cluster.status) {
                ClusterStatus.RIGID -> rigidSolver.solveCluster(cluster, graph)
                ClusterStatus.SEMI_RIGID -> iterativeSolver.solveCluster(cluster, graph)
                ClusterStatus.UNDER_CONSTRAINED -> emptyMap() // Keep current positions
                ClusterStatus.OVER_CONSTRAINED -> throw OverConstrainedException()
            }

            updatedPositions.putAll(solution)
        }

        // 6. Update drawing state with new positions
        return drawingState.withUpdatedVertices(updatedPositions)
    }

    private fun topologicalSort(
        clusters: List<ConstraintCluster>,
        graph: ConstraintGraph
    ): List<ConstraintCluster> {
        // Sort clusters so that fixed clusters are solved first,
        // then clusters that depend on them, etc.
        // Standard topological sort algorithm
        // ...
    }
}
```

---

### Phase 4: Integration (Week 4)

#### Step 4.1: Replace current ConstraintSolver
**Files to modify**:
- `/domain/constraints/ConstraintSolver.kt` - Completely rewrite

**New structure**:
```kotlin
class ConstraintSolver(
    private val eventBus: EventBus,
    private val stateManager: StateManager,
) {
    private val orchestrator = ConstraintOrchestrator(
        graphBuilder = ConstraintGraphBuilder(),
        analyzer = ConstraintGraphAnalyzer(),
        clusterDetector = ClusterDetector(),
        rigidSolver = RigidClusterSolver(),
        iterativeSolver = IterativeClusterSolver(),
    )

    init {
        // Listen to constraint events (same as before)
        eventBus.events
            .filterIsInstance<ConstraintEvent>()
            .onEach { event -> handleConstraintEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))

        // Listen to geometry events (same as before)
        eventBus.events
            .filterIsInstance<GeometryEvent>()
            .onEach { event -> handleGeometryEvent(event) }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private suspend fun handleConstraintEvent(event: ConstraintEvent) {
        when (event) {
            is ConstraintEvent.ConstraintAdded -> {
                // Validate BEFORE adding
                val graph = orchestrator.graphBuilder.buildGraph()
                if (orchestrator.analyzer.wouldOverConstrain(event.constraint)) {
                    eventBus.emit(ConstraintEvent.ConstraintConflict(
                        message = "Adding this constraint would over-constrain the system"
                    ))
                    return
                }

                // Add constraint
                stateManager.updateState { state ->
                    state.updateDrawingState { it.withConstraint(event.constraint) }
                }

                // Solve
                solveConstraints()
            }

            // ... other events ...
        }
    }

    private suspend fun solveConstraints() {
        val state = stateManager.state.value
        val drawingState = state.projectDrawingState ?: return

        try {
            val updatedState = orchestrator.solve(drawingState)

            stateManager.updateState { state ->
                state.copy(projectDrawingState = updatedState)
            }

            Logger.i { "✓ Constraints solved successfully" }
        } catch (e: OverConstrainedException) {
            Logger.e { "✗ Over-constrained system: ${e.message}" }
            eventBus.emit(ConstraintEvent.ConstraintConflict(
                message = e.message ?: "System is over-constrained"
            ))
        }
    }
}
```

#### Step 4.2: Update UI to show DOF status
**Visual feedback**:
- Show DOF count in status bar ("3 DOF remaining")
- Color-code constraints:
  - Green = satisfied and valid
  - Yellow = under-constrained (needs more constraints)
  - Red = over-constrained (conflict)

---

## Testing Strategy

### Unit Tests

#### Test 1: DOF Calculation
```kotlin
@Test
fun `single free point has 2 DOF`() {
    val graph = buildGraph(points = listOf(Point(fixed = false)))
    assertEquals(2, analyzer.calculateTotalDOF(graph.nodes.keys))
}

@Test
fun `distance constraint removes 1 DOF`() {
    val graph = buildGraph(
        points = listOf(Point(id="p1"), Point(id="p2")),
        constraints = listOf(Distance(p1, p2, 100.0))
    )
    val constrained = analyzer.calculateConstrainedDOF(graph.edges.keys)
    assertEquals(1, constrained)
}

@Test
fun `triangle with 3 sides is rigid`() {
    val graph = buildTriangle(sideA=100, sideB=100, sideC=100)
    val clusters = clusterDetector.findClusters()
    assertEquals(1, clusters.size)
    assertEquals(ClusterStatus.RIGID, clusters[0].status)
}
```

#### Test 2: Over-Constraint Detection
```kotlin
@Test
fun `adding 4th constraint to triangle causes over-constraint`() {
    val graph = buildTriangle()
    val newConstraint = Angle(line1, line2, 90.0)

    assertTrue(analyzer.wouldOverConstrain(newConstraint))
}
```

#### Test 3: Cluster Decomposition
```kotlin
@Test
fun `two separate shapes form two clusters`() {
    val graph = buildGraph(
        shapes = listOf(
            Square(size=100),  // Cluster 1
            Triangle(size=50)  // Cluster 2
        )
    )

    val clusters = clusterDetector.findClusters()
    assertEquals(2, clusters.size)
}
```

### Integration Tests

#### Test 4: Rectangle Stays Rectangular
```kotlin
@Test
fun `constraining one wall of rectangle adjusts opposite wall`() {
    // Draw 100x80 rectangle
    val rect = drawRectangle(width=100, height=80)

    // Constrain top wall to 120cm
    addConstraint(Distance(rect.topWall, 120.0))

    val solved = orchestrator.solve(rect)

    // Bottom wall should also be 120cm (rectangle preserved)
    assertEquals(120.0, solved.bottomWall.length, tolerance=0.1)

    // All angles should still be 90°
    assertEquals(90.0, solved.topLeft.angle, tolerance=0.1)
    assertEquals(90.0, solved.topRight.angle, tolerance=0.1)
}
```

---

## Migration Strategy

### Phase 1: Parallel Implementation
- Keep old ConstraintSolver.kt as `ConstraintSolverLegacy.kt`
- Implement new solver in separate package: `/domain/constraints/v2/`
- Use feature flag to switch between old/new

### Phase 2: A/B Testing
- Test new solver on simple cases (squares, triangles)
- Gradually enable for more complex shapes
- Monitor for regressions

### Phase 3: Full Migration
- Remove legacy solver
- Move v2 → main package
- Update documentation

---

## Success Criteria

### Definition of Done

1. ✅ Draw rectangle, constrain one wall → opposite wall adjusts, angles stay 90°
2. ✅ Draw triangle, constrain all 3 sides → system is rigid (can only translate/rotate)
3. ✅ Try to over-constrain → clear error message before constraint is added
4. ✅ Angle constraint on one line → only that line rotates, rest of drawing unchanged
5. ✅ Solver performance: <100ms for 50 constraints, 20 vertices
6. ✅ No "smooshing" - vertices only move when directly affected by constraint

### Non-Goals (Future Work)

- ❌ 3D constraints (stays 2D)
- ❌ Parametric equations (just geometric constraints)
- ❌ Undo/redo for constraint solver (Phase 5)
- ❌ Constraint relaxation (automatic conflict resolution)

---

## Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| 1. Graph Infrastructure | 1 week | None |
| 2. Graph Decomposition | 1 week | Phase 1 |
| 3. Sequential Solver | 1 week | Phase 2 |
| 4. Integration & Testing | 1 week | Phase 3 |
| **Total** | **4 weeks** | |

**Realistic estimate**: 4-6 weeks with one developer (includes testing, debugging, edge cases)

---

## Risk Mitigation

### Risk 1: Algorithm Complexity
**Mitigation**: Start with simple cases (2 points + distance). Gradually add complexity.

### Risk 2: Performance
**Mitigation**: Profile early. Use analytical solutions for common patterns (triangles, rectangles).

### Risk 3: Edge Cases
**Mitigation**: Comprehensive unit tests. Fuzzing with random constraint combinations.

---

## References

### Research Papers
- [Geometric constraint solving - Wikipedia](https://en.wikipedia.org/wiki/Geometric_constraint_solving)
- [2D Geometric Constraint Solver Tutorial](https://vmnnk.com/en/2023-10-18/2d-geometric-constraint-solver)
- [Graph reduction method - ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0965997810001006)
- [Degrees of Freedom Analysis - Springer](https://link.springer.com/chapter/10.1007/978-3-642-60607-6_10)

### Professional Tools
- [SolveSpace - Technology](https://solvespace.com/tech.pl)
- [pygeosolve Python library](https://seands.github.io/pygeosolve/)
- [GitHub - kasznar/geometric-constraint-solver](https://github.com/kasznar/geometric-constraint-solver)

### Books
- "Geometric Constraint Solving and Applications" (Springer)
- "Parametric and Feature-Based CAD/CAM" by Jami Shah

---

**Last Updated**: 2026-01-11
**Status**: Planning Phase
**Next Action**: Review plan, get approval, start Phase 1