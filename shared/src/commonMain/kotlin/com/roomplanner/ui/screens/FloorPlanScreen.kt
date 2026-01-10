package com.roomplanner.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.Project
import com.roomplanner.data.storage.FileStorage
import com.roomplanner.domain.geometry.GeometryManager
import com.roomplanner.localization.strings
import com.roomplanner.ui.components.DrawingCanvas
import com.roomplanner.ui.components.ToolModeFAB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanScreen(
    projectId: String,
    onNavigate: (AppMode) -> Unit,
) {
    val fileStorage: FileStorage = koinInject()
    val stateManager: StateManager = koinInject()
    val eventBus: EventBus = koinInject()
    val geometryManager: GeometryManager = koinInject()
    val strings = strings()
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf<Project?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Collect app state for drawing
    val appState by stateManager.state.collectAsState()

    // Load project metadata and drawing state
    LaunchedEffect(projectId) {
        // CRITICAL: Clear any previous project state FIRST
        stateManager.updateState { it.clearProjectDrawing() }
        Logger.d { "Cleared previous project state before loading $projectId" }

        // Load project metadata
        fileStorage
            .loadProject(projectId)
            .onSuccess {
                project = it
                Logger.i { "✓ Project loaded: ${it.name}" }
            }.onFailure {
                Logger.e { "✗ Failed to load project: ${it.message}" }
            }

        // Load drawing state for this project
        fileStorage
            .loadProjectDrawing(projectId)
            .onSuccess { drawingState ->
                stateManager.updateState { state ->
                    state
                        .copy(currentProjectId = projectId)
                        .withProjectDrawing(drawingState)
                }
                Logger.i {
                    "✓ Drawing state loaded: ${drawingState.vertices.size} vertices, ${drawingState.lines.size} lines"
                }
            }.onFailure {
                Logger.e { "✗ Failed to load drawing state: ${it.message}" }
            }

        isLoading = false
    }

    // Auto-save drawing state when leaving screen
    DisposableEffect(projectId) {
        onDispose {
            // Save drawing state when screen is disposed
            // Use CoroutineScope with Default dispatcher for cleanup
            val currentDrawingState = stateManager.state.value.projectDrawingState
            if (currentDrawingState != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    fileStorage
                        .saveProjectDrawing(projectId, currentDrawingState)
                        .onSuccess {
                            Logger.i { "✓ Drawing state auto-saved on screen exit" }
                        }.onFailure {
                            Logger.e { "✗ Failed to auto-save drawing state: ${it.message}" }
                        }
                }
            }
            // Clear drawing state from global state
            stateManager.updateState { it.clearProjectDrawing() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: strings.loadingProject) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(AppMode.ProjectBrowser) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backButton,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        // Main content box - this is the coordinate space for both FAB and RadialMenu
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Drawing canvas
                DrawingCanvas(
                    state = appState,
                    eventBus = eventBus,
                    modifier = Modifier.fillMaxSize(),
                )

                // Status overlay (bottom)
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = strings.drawingInstructions,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.vertexCount(appState.projectDrawingState?.vertices?.size ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Phase 1.4c: Self-contained FAB with radial menu
                appState.projectDrawingState?.let { drawingState ->
                    ToolModeFAB(
                        currentMode = drawingState.toolMode,
                        onToolSelected = { mode ->
                            // Synchronous state update (no event bus race condition)
                            stateManager.updateState { state ->
                                state.updateDrawingState { drawingState ->
                                    val clearedSelection =
                                        if (mode == com.roomplanner.data.models.ToolMode.DRAW) {
                                            drawingState.clearSelection()
                                        } else {
                                            drawingState
                                        }
                                    clearedSelection.withToolMode(mode)
                                }
                            }
                            Logger.i { "✓ Tool mode changed: $mode" }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}
