package com.roomplanner.domain.geometry

import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.domain.drawing.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Helper for detecting rectangular chains in drawing geometry.
 *
 * Phase 1.6.1: Smart propagation for rectangles.
 * When adjusting one wall's dimension, propagate to opposite wall
 * instead of breaking 90° angles.
 *
 * Rectangle definition:
 * - Exactly 4 edges forming closed loop
 * - All angles are 90° (within tolerance)
 * - Connected in sequence
 *
 * Design rationale:
 * - Pure utility (no state, no events)
 * - Immutable analysis (doesn't modify state)
 * - Returns structured data for solver to use
 */
object RectangleDetector {
    /**
     * Rectangle structure representing a 4-sided closed loop.
     *
     * @param lines The 4 lines forming the rectangle (ordered clockwise or counter-clockwise)
     * @param vertices IDs of the 4 corner vertices (ordered)
     */
    data class Rectangle(
        val lines: List<Line>,
        val vertices: List<String>,
    ) {
        /**
         * Get the line opposite to the given line in the rectangle.
         *
         * In a rectangle with lines [top, right, bottom, left]:
         * - top (index 0) ↔ bottom (index 2)
         * - right (index 1) ↔ left (index 3)
         *
         * @param line Line to find opposite of
         * @return Opposite line, or null if line not in rectangle
         */
        fun oppositeLine(line: Line): Line? {
            val index = lines.indexOfFirst { it.id == line.id }
            if (index == -1) return null

            val oppositeIndex = (index + 2) % 4
            return lines[oppositeIndex]
        }
    }

    /**
     * Detect if a line is part of a rectangle.
     *
     * Algorithm:
     * 1. Start from given line
     * 2. Follow connected lines in both directions
     * 3. Check if forms closed 4-sided loop
     * 4. Verify all angles are 90° (within tolerance)
     *
     * @param line Line to check
     * @param drawingState Current drawing state
     * @return Rectangle if detected, null otherwise
     */
    fun detectRectangle(
        line: Line,
        drawingState: ProjectDrawingState,
    ): Rectangle? {
        // Try to build a 4-sided loop starting from this line
        val path = buildPath(line, drawingState)

        // Not a closed 4-sided loop
        if (path == null || path.size != 4) return null

        // Verify all angles are 90°
        if (!hasAllRightAngles(path, drawingState)) return null

        // Extract vertex IDs in order
        val vertexIds = mutableListOf<String>()
        path.forEach { pathLine ->
            if (vertexIds.isEmpty()) {
                vertexIds.add(pathLine.startVertexId)
            }
            vertexIds.add(pathLine.endVertexId)
        }
        // Remove duplicate (closed loop has start = end)
        if (vertexIds.first() == vertexIds.last()) {
            vertexIds.removeLast()
        }

        return Rectangle(
            lines = path,
            vertices = vertexIds,
        )
    }

    /**
     * Build path of connected lines starting from given line.
     * Stops when:
     * - Loop closes (returns to start vertex)
     * - No more connections found
     * - More than 4 lines (not a rectangle)
     *
     * @return List of lines forming path, or null if invalid
     */
    private fun buildPath(
        startLine: Line,
        drawingState: ProjectDrawingState,
    ): List<Line>? {
        val path = mutableListOf(startLine)
        var currentVertex = startLine.endVertexId
        val startVertex = startLine.startVertexId

        // Follow connections until we close the loop or hit dead end
        repeat(4) {
            // Maximum 4 lines for rectangle
            if (currentVertex == startVertex) {
                // Loop closed!
                return path
            }

            // Find next connected line (not the one we just came from)
            val connectedLines =
                drawingState
                    .getLinesConnectedToVertex(currentVertex)
                    .filter { it.id !in path.map { p -> p.id } }

            if (connectedLines.isEmpty()) {
                // Dead end - not a closed loop
                return null
            }

            if (connectedLines.size > 1) {
                // Multiple branches - ambiguous, not a simple rectangle
                return null
            }

            val nextLine = connectedLines.first()
            path.add(nextLine)

            // Move to next vertex
            currentVertex =
                if (nextLine.startVertexId == currentVertex) {
                    nextLine.endVertexId
                } else {
                    nextLine.startVertexId
                }
        }

        // If we get here, we have 4 lines but didn't close the loop
        return null
    }

    /**
     * Check if all angles in path are 90° (within tolerance).
     *
     * @param path Lines forming the path
     * @param drawingState Current drawing state
     * @return True if all angles are 90° ± tolerance
     */
    private fun hasAllRightAngles(
        path: List<Line>,
        drawingState: ProjectDrawingState,
    ): Boolean {
        if (path.size != 4) return false

        // Check angle at each corner
        for (i in path.indices) {
            val line1 = path[i]
            val line2 = path[(i + 1) % 4]

            val angle = calculateAngleBetween(line1, line2, drawingState)

            // Allow 85° - 95° (same tolerance as auto-detection)
            if (angle < 85.0 || angle > 95.0) {
                return false
            }
        }

        return true
    }

    /**
     * Calculate angle between two connected lines in degrees.
     *
     * @return Angle in degrees (0-360)
     */
    private fun calculateAngleBetween(
        line1: Line,
        line2: Line,
        drawingState: ProjectDrawingState,
    ): Double {
        val v1Start = drawingState.vertices[line1.startVertexId] ?: return 0.0
        val v1End = drawingState.vertices[line1.endVertexId] ?: return 0.0
        val v2Start = drawingState.vertices[line2.startVertexId] ?: return 0.0
        val v2End = drawingState.vertices[line2.endVertexId] ?: return 0.0

        // Direction vectors
        val dx1 = v1End.position.x - v1Start.position.x
        val dy1 = v1End.position.y - v1Start.position.y
        val dx2 = v2End.position.x - v2Start.position.x
        val dy2 = v2End.position.y - v2Start.position.y

        // Angles from horizontal
        val angle1 = atan2(dy1, dx1)
        val angle2 = atan2(dy2, dx2)

        // Relative angle
        var relativeAngle = angle2 - angle1

        // Normalize to 0-360
        while (relativeAngle < 0) relativeAngle += 2 * PI
        while (relativeAngle >= 2 * PI) relativeAngle -= 2 * PI

        val degrees = abs(relativeAngle) * 180.0 / PI

        // Return smallest angle (0-180)
        return if (degrees > 180) 360 - degrees else degrees
    }
}
