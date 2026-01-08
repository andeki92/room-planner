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
import com.roomplanner.data.storage.FileStorage
import com.roomplanner.localization.AppLanguage
import com.roomplanner.localization.strings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigate: (AppMode) -> Unit) {
    val stateManager: StateManager = koinInject()
    val fileStorage: FileStorage = koinInject()
    val scope = rememberCoroutineScope()
    val strings = strings()

    val appState by stateManager.state.collectAsState()
    val settings = appState.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
        ) {
            // Language Setting
            Text(
                text = strings.languageLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            AppLanguage.entries.forEach { language ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSettings = settings.copy(language = language)
                                stateManager.updateSettings(newSettings)

                                scope.launch {
                                    fileStorage
                                        .saveSettings(newSettings)
                                        .onSuccess {
                                            Logger.i { "✓ Settings saved: Language = ${language.nativeName}" }
                                        }.onFailure {
                                            Logger.e { "✗ Failed to save settings: ${it.message}" }
                                        }
                                }
                            }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.language == language,
                        onClick = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = language.nativeName)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Measurement Units Setting
            Text(
                text = strings.measurementUnitsLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            MeasurementUnits.entries.forEach { unit ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newSettings = settings.copy(measurementUnits = unit)
                                stateManager.updateSettings(newSettings)

                                scope.launch {
                                    fileStorage
                                        .saveSettings(newSettings)
                                        .onSuccess {
                                            Logger.i { "✓ Settings saved: Units = $unit" }
                                        }.onFailure {
                                            Logger.e { "✗ Failed to save settings: ${it.message}" }
                                        }
                                }
                            }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.measurementUnits == unit,
                        onClick = null, // Click handled by Row
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            when (unit) {
                                MeasurementUnits.IMPERIAL -> strings.imperialLabel
                                MeasurementUnits.METRIC -> strings.metricLabel
                            },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Placeholder for future settings
            Text(
                text = strings.moreSettingsComingSoon,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
