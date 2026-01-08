package com.roomplanner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import com.roomplanner.data.events.NavigationEvent
import com.roomplanner.data.models.AppMode
import com.roomplanner.ui.screens.FloorPlanScreen
import com.roomplanner.ui.screens.ProjectBrowserScreen
import com.roomplanner.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun App() {
    val stateManager: StateManager = koinInject()
    val eventBus: EventBus = koinInject()
    val scope = rememberCoroutineScope()

    val appState by stateManager.state.collectAsState()

    // Listen to navigation events and update state
    LaunchedEffect(Unit) {
        eventBus.events
            .filterIsInstance<NavigationEvent.ModeChanged>()
            .collect { event ->
                Logger.i { "Mode: ${appState.currentMode} → ${event.mode}" }
                stateManager.setMode(event.mode)
            }
    }

    MaterialTheme {
        AnimatedContent(
            targetState = appState.currentMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
            }
        ) { mode ->
            when (mode) {
                is AppMode.ProjectBrowser -> {
                    ProjectBrowserScreen(
                        onNavigate = { newMode ->
                            scope.launch {
                                eventBus.emit(NavigationEvent.ModeChanged(newMode))
                            }
                        }
                    )
                }

                is AppMode.FloorPlan -> {
                    FloorPlanScreen(
                        projectId = mode.projectId,
                        onNavigate = { newMode ->
                            scope.launch {
                                eventBus.emit(NavigationEvent.ModeChanged(newMode))
                            }
                        }
                    )
                }

                is AppMode.Settings -> {
                    SettingsScreen(
                        onNavigate = { newMode ->
                            scope.launch {
                                eventBus.emit(NavigationEvent.ModeChanged(newMode))
                            }
                        }
                    )
                }
            }
        }
    }
}
