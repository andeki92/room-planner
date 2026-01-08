package com.roomplanner.data.storage

import android.content.Context
import co.touchlab.kermit.Logger
import com.roomplanner.data.models.Project
import com.roomplanner.data.models.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual class FileStorage(
    private val context: Context,
) {
    private val filesDir = context.filesDir
    private val projectsDir = File(filesDir, "projects").also { it.mkdirs() }

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    actual suspend fun saveProject(project: Project): Result<Unit> =
        runCatching {
            val projectDir = File(projectsDir, project.id).also { it.mkdirs() }
            val metadataFile = File(projectDir, "metadata.json")

            val jsonString = json.encodeToString(project)
            metadataFile.writeText(jsonString)

            Logger.i { "✓ Project saved: ${project.name} (${project.id})" }
        }

    actual suspend fun loadProject(projectId: String): Result<Project> =
        runCatching {
            val metadataFile = File(projectsDir, "$projectId/metadata.json")
            if (!metadataFile.exists()) {
                throw Exception("Project not found: $projectId")
            }

            val jsonString = metadataFile.readText()
            json.decodeFromString<Project>(jsonString)
        }

    actual suspend fun listProjects(): Result<List<Project>> =
        runCatching {
            projectsDir
                .listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { dir ->
                    loadProject(dir.name).getOrNull()
                }?.sortedByDescending { it.modifiedAt }
                ?: emptyList()
        }

    actual suspend fun deleteProject(projectId: String): Result<Unit> =
        runCatching {
            val projectDir = File(projectsDir, projectId)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                Logger.i { "✓ Project deleted: $projectId" }
            }
        }

    actual suspend fun saveSettings(settings: Settings): Result<Unit> =
        runCatching {
            val settingsFile = File(filesDir, "settings.json")
            val jsonString = json.encodeToString(settings)
            settingsFile.writeText(jsonString)
        }

    actual suspend fun loadSettings(): Result<Settings> =
        runCatching {
            val settingsFile = File(filesDir, "settings.json")
            if (settingsFile.exists()) {
                val jsonString = settingsFile.readText()
                json.decodeFromString<Settings>(jsonString)
            } else {
                Settings.default() // First launch
            }
        }
}
