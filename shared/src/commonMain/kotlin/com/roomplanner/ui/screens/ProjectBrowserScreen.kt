package com.roomplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.Project
import com.roomplanner.data.storage.FileStorage
import com.roomplanner.ui.components.CreateProjectDialog
import com.roomplanner.ui.components.ProjectCard
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectBrowserScreen(
    onNavigate: (AppMode) -> Unit
) {
    val fileStorage: FileStorage = koinInject()
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Load projects on first composition
    LaunchedEffect(Unit) {
        Logger.i { "✓ Phase 0: ProjectBrowser screen initialized" }
        fileStorage.listProjects()
            .onSuccess {
                projects = it
                Logger.i { "Loaded ${it.size} projects" }
            }
            .onFailure {
                Logger.e { "✗ Failed to load projects: ${it.message}" }
            }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Planner") },
                actions = {
                    IconButton(onClick = { onNavigate(AppMode.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Project")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                projects.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No projects yet",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to create your first project",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> {
                    // Project grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onClick = {
                                    Logger.i { "Opening project: ${project.name}" }
                                    onNavigate(AppMode.FloorPlan(project.id))
                                },
                                onDelete = {
                                    scope.launch {
                                        fileStorage.deleteProject(project.id)
                                            .onSuccess {
                                                projects = projects.filter { it.id != project.id }
                                            }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create project dialog
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { projectName ->
                scope.launch {
                    val newProject = Project.create(projectName)
                    fileStorage.saveProject(newProject)
                        .onSuccess {
                            projects = listOf(newProject) + projects
                            showCreateDialog = false
                            Logger.i { "✓ Project created: $projectName" }
                        }
                        .onFailure {
                            Logger.e { "✗ Failed to create project: ${it.message}" }
                        }
                }
            }
        )
    }
}
