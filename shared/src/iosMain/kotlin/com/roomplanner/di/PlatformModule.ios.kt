package com.roomplanner.di

import com.roomplanner.data.storage.FileStorage
import org.koin.dsl.module

actual val platformModule = module {
    single { FileStorage() }  // iOS implementation
}
