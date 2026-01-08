package com.roomplanner.data.storage

import co.touchlab.kermit.Logger
import com.roomplanner.data.models.Project
import com.roomplanner.data.models.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask

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

            json.decodeFromString<Project>(jsonString as String)
        }

    actual suspend fun listProjects(): Result<List<Project>> =
        runCatching {
            val contents =
                fileManager.contentsOfDirectoryAtURL(
                    projectsDir,
                    includingPropertiesForKeys = null,
                    options = 0u,
                    error = null,
                ) as? List<*> ?: emptyList<Any>()

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
            Logger.i { "✓ Project deleted: $projectId" }
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
                json.decodeFromString<Settings>(jsonString as String)
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
