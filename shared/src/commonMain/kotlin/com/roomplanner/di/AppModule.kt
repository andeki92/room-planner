package com.roomplanner.di

import com.roomplanner.data.StateManager
import com.roomplanner.data.events.EventBus
import com.roomplanner.domain.constraints.ConstraintSolver
import com.roomplanner.domain.geometry.GeometryManager
import org.koin.dsl.module

/**
 * Shared dependency injection module (common across iOS/Android)
 */
val commonModule =
    module {
        // Singletons
        single { EventBus() }
        single { StateManager() }

        // Domain managers (event handlers)
        single { GeometryManager(get(), get()) }

        // Constraint solver (Phase 1.5)
        single { ConstraintSolver(get(), get()) }

        // Platform-specific FileStorage is defined in platform modules
    }
