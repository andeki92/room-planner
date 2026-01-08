package com.roomplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.Project
import com.roomplanner.data.storage.FileStorage
import com.roomplanner.localization.strings
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanScreen(
    projectId: String,
    onNavigate: (AppMode) -> Unit,
) {
    val fileStorage: FileStorage = koinInject()
    val strings = strings()

    var project by remember { mutableStateOf<Project?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load project
    LaunchedEffect(projectId) {
        fileStorage
            .loadProject(projectId)
            .onSuccess {
                project = it
                Logger.i { "✓ Project loaded: ${it.name}" }
            }.onFailure {
                Logger.e { "✗ Failed to load project: ${it.message}" }
            }
        isLoading = false
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = strings.floorPlanMode,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.drawingCanvasComingSoon,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
