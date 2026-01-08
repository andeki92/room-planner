package com.roomplanner.common

import com.roomplanner.localization.AppLanguage

/**
 * Get the system's current language.
 * Returns Norwegian for Norwegian locales, English otherwise.
 */
expect fun getSystemLanguage(): AppLanguage
