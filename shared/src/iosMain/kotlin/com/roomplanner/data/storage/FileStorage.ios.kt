package com.roomplanner.data.storage

import co.touchlab.kermit.Logger
import com.roomplanner.data.models.Project
import com.roomplanner.data.models.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual class FileStorage {
    private val fileManager = NSFileManager.defaultManager
    private val documentsDir: NSURL by lazy {
        fileManager
            .URLsForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask,
            ).first() as NSURL
    }

    private val projectsDir: NSURL by lazy {
        documentsDir.URLByAppendingPathComponent("projects", isDirectory = true)!!.also {
            createDirectoryIfNeeded(it)
        }
    }

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    actual suspend fun saveProject(project: Project): Result<Unit> =
        runCatching {
            val projectDir = projectsDir.URLByAppendingPathComponent(project.id, isDirectory = true)!!
            createDirectoryIfNeeded(projectDir)

            val metadataPath = projectDir.URLByAppendingPathComponent("metadata.json")!!.path!!
            val jsonString = json.encodeToString(project)

            val success =
                (jsonString as NSString).writeToFile(
                    metadataPath,
                    atomically = true,
                    encoding = NSUTF8StringEncoding,
                    error = null,
                )

            if (success) {
                Logger.i { "✓ Project saved: ${project.name} (${project.id})" }
            } else {
                throw Exception("Failed to write project file")
            }
        }

    actual suspend fun loadProject(projectId: String): Result<Project> =
        runCatching {
            val metadataPath =
                projectsDir
                    .URLByAppendingPathComponent(projectId, isDirectory = true)!!
                    .URLByAppendingPathComponent("metadata.json")!!
                    .path!!

            val jsonString =
                NSString.stringWithContentsOfFile(
                    metadataPath,
                    encoding = NSUTF8StringEncoding,
                    error = null,
                ) ?: throw Exception("Project not found: $projectId")

            json.decodeFromString<Project>(jsonString)
        }

    actual suspend fun listProjects(): Result<List<Project>> =
        runCatching {
            val contents =
                fileManager.contentsOfDirectoryAtURL(
                    projectsDir,
                    includingPropertiesForKeys = null,
                    options = 0u,
                    error = null,
                ) ?: emptyList<Any>()

            contents
                .mapNotNull { url ->
                    val projectId = (url as NSURL).lastPathComponent ?: return@mapNotNull null
                    loadProject(projectId).getOrNull()
                }.sortedByDescending { it.modifiedAt }
        }

    actual suspend fun deleteProject(projectId: String): Result<Unit> =
        runCatching {
            val projectDir = projectsDir.URLByAppendingPathComponent(projectId, isDirectory = true)!!
            fileManager.removeItemAtURL(projectDir, error = null)
            Logger.i { "✓ Project deleted: $projectId (metadata + drawing data)" }
        }

    actual suspend fun saveProjectDrawing(
        projectId: String,
        drawingState: com.roomplanner.data.models.ProjectDrawingState,
    ): Result<Unit> =
        runCatching {
            val projectDir = projectsDir.URLByAppendingPathComponent(projectId, isDirectory = true)!!
            createDirectoryIfNeeded(projectDir)

            val drawingPath = projectDir.URLByAppendingPathComponent("drawing.json")!!.path!!
            val jsonString = json.encodeToString(drawingState)

            val success =
                (jsonString as NSString).writeToFile(
                    drawingPath,
                    atomically = true,
                    encoding = NSUTF8StringEncoding,
                    error = null,
                )

            if (success) {
                Logger.d { "✓ Drawing state saved for project: $projectId" }
            } else {
                throw Exception("Failed to write drawing file")
            }
        }

    actual suspend fun loadProjectDrawing(projectId: String): Result<com.roomplanner.data.models.ProjectDrawingState> =
        runCatching {
            val drawingPath =
                projectsDir
                    .URLByAppendingPathComponent(projectId, isDirectory = true)!!
                    .URLByAppendingPathComponent("drawing.json")!!
                    .path!!

            val jsonString =
                NSString.stringWithContentsOfFile(
                    drawingPath,
                    encoding = NSUTF8StringEncoding,
                    error = null,
                )

            if (jsonString != null) {
                json.decodeFromString<com.roomplanner.data.models.ProjectDrawingState>(jsonString)
            } else {
                // New project - return empty drawing state
                Logger.d { "No drawing.json found for project $projectId, returning empty state" }
                com.roomplanner.data.models.ProjectDrawingState
                    .empty()
            }
        }

    actual suspend fun saveSettings(settings: Settings): Result<Unit> =
        runCatching {
            val settingsPath = documentsDir.URLByAppendingPathComponent("settings.json")!!.path!!
            val jsonString = json.encodeToString(settings)

            (jsonString as NSString).writeToFile(
                settingsPath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
        }

    actual suspend fun loadSettings(): Result<Settings> =
        runCatching {
            val settingsPath = documentsDir.URLByAppendingPathComponent("settings.json")!!.path!!

            val jsonString =
                NSString.stringWithContentsOfFile(
                    settingsPath,
                    encoding = NSUTF8StringEncoding,
                    error = null,
                )

            if (jsonString != null) {
                json.decodeFromString<Settings>(jsonString)
            } else {
                Settings.default() // First launch
            }
        }

    private fun createDirectoryIfNeeded(url: NSURL) {
        if (!fileManager.fileExistsAtPath(url.path!!)) {
            fileManager.createDirectoryAtURL(
                url,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }
}
