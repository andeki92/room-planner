package com.roomplanner.di

import com.roomplanner.data.storage.FileStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { FileStorage(androidContext()) }  // Android implementation needs Context
}
