package com.roomplanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.models.AppMode
import com.roomplanner.data.models.MeasurementUnits
import com.roomplanner.data.models.Settings
import com.roomplanner.data.storage.FileStorage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (AppMode) -> Unit
) {
    val stateManager: StateManager = koinInject()
    val fileStorage: FileStorage = koinInject()
    val scope = rememberCoroutineScope()

    val appState by stateManager.state.collectAsState()
    val settings = appState.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(AppMode.ProjectBrowser) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Measurement Units Setting
            Text(
                text = "Measurement Units",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            MeasurementUnits.entries.forEach { unit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSettings = settings.copy(measurementUnits = unit)
                            stateManager.updateSettings(newSettings)

                            scope.launch {
                                fileStorage.saveSettings(newSettings)
                                    .onSuccess {
                                        Logger.i { "✓ Settings saved: Units = $unit" }
                                    }
                                    .onFailure {
                                        Logger.e { "✗ Failed to save settings: ${it.message}" }
                                    }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.measurementUnits == unit,
                        onClick = null  // Click handled by Row
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (unit) {
                            MeasurementUnits.IMPERIAL -> "Imperial (feet, inches)"
                            MeasurementUnits.METRIC -> "Metric (meters, centimeters)"
                        }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Placeholder for future settings
            Text(
                text = "More settings coming in Phase 1+",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
