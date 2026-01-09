package com.roomplanner.data.storage

import com.roomplanner.data.models.Project
import com.roomplanner.data.models.ProjectDrawingState
import com.roomplanner.data.models.Settings

/**
 * Platform-specific file storage interface.
 *
 * Storage locations:
 * - iOS: Documents directory (backed up by iCloud)
 * - Android: getFilesDir() (app-private storage)
 *
 * File structure:
 * ```
 * /projects/
 *   /project-uuid-1/
 *     metadata.json     (Project metadata: name, dates)
 *     drawing.json      (Drawing state: vertices, lines, camera)
 *   /project-uuid-2/
 *     ...
 * /settings.json
 * ```
 */
expect class FileStorage {
    /**
     * Save project metadata
     */
    suspend fun saveProject(project: Project): Result<Unit>

    /**
     * Load project by ID
     */
    suspend fun loadProject(projectId: String): Result<Project>

    /**
     * List all projects (metadata only, for browser grid)
     */
    suspend fun listProjects(): Result<List<Project>>

    /**
     * Delete project and all associated files (metadata + drawing data)
     */
    suspend fun deleteProject(projectId: String): Result<Unit>

    /**
     * Save project drawing state (vertices, lines, camera, etc.)
     */
    suspend fun saveProjectDrawing(
        projectId: String,
        drawingState: ProjectDrawingState,
    ): Result<Unit>

    /**
     * Load project drawing state.
     * Returns empty state if drawing.json doesn't exist (new project).
     */
    suspend fun loadProjectDrawing(projectId: String): Result<ProjectDrawingState>

    /**
     * Save settings
     */
    suspend fun saveSettings(settings: Settings): Result<Unit>

    /**
     * Load settings
     */
    suspend fun loadSettings(): Result<Settings>
}
