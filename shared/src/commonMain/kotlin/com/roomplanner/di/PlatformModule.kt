package com.roomplanner.di

import org.koin.core.module.Module

/**
 * Platform-specific dependencies (FileStorage, etc.)
 */
expect val platformModule: Module
