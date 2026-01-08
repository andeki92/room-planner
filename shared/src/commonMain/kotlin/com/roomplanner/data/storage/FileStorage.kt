package com.roomplanner.data.storage

import com.roomplanner.data.models.Project
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
 *     metadata.json     (Project data)
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
     * Delete project and all associated files
     */
    suspend fun deleteProject(projectId: String): Result<Unit>

    /**
     * Save settings
     */
    suspend fun saveSettings(settings: Settings): Result<Unit>

    /**
     * Load settings
     */
    suspend fun loadSettings(): Result<Settings>
}
