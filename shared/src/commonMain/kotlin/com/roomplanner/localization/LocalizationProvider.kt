package com.roomplanner.localization

import androidx.compose.runtime.*
import com.roomplanner.data.StateManager
import org.koin.compose.koinInject

/**
 * Composition local for accessing current strings.
 * Reactive: automatically updates when language setting changes.
 */
val LocalStrings = compositionLocalOf<Strings> { EnglishStrings }

/**
 * Provider component that observes Settings.language and provides
 * reactive Strings context to all child composables.
 *
 * Wraps the entire app to make strings() available everywhere.
 */
@Composable
fun LocalizationProvider(content: @Composable () -> Unit) {
    val stateManager: StateManager = koinInject()
    val appState by stateManager.state.collectAsState()

    // Get strings implementation for current language
    val strings = appState.settings.language.strings

    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

/**
 * Convenience function for accessing strings in composables.
 * Usage: val strings = strings()
 *        Text(strings.appTitle)
 */
@Composable
fun strings(): Strings = LocalStrings.current
