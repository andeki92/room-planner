package com.roomplanner.localization

import kotlinx.serialization.Serializable

/**
 * Supported languages in the app.
 * When adding a new language:
 * 1. Add enum value here
 * 2. Compiler will fail on `strings` property (exhaustive when)
 * 3. Create new Strings implementation
 * 4. Add branch in `strings` property
 *
 * This ensures compile-time safety for all translations.
 */
@Serializable
enum class AppLanguage {
    ENGLISH,
    NORWEGIAN,
    ;

    /**
     * Get the Strings implementation for this language.
     * Adding a new language without implementation = compilation error.
     */
    val strings: Strings
        get() =
            when (this) {
                ENGLISH -> EnglishStrings
                NORWEGIAN -> NorwegianStrings
            }

    /**
     * Display name in the language itself (for language selector UI).
     * Shows "English" for English, "Norsk" for Norwegian.
     */
    val nativeName: String
        get() =
            when (this) {
                ENGLISH -> "English"
                NORWEGIAN -> "Norsk"
            }
}
