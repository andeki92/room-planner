package com.roomplanner.di

import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import org.koin.dsl.module

/**
 * Shared dependency injection module (common across iOS/Android)
 */
val commonModule =
    module {
        // Singletons
        single { EventBus() }
        single { StateManager() }

        // Platform-specific FileStorage is defined in platform modules
    }
