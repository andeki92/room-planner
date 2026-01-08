package com.roomplanner.common

import com.roomplanner.localization.AppLanguage
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun getSystemLanguage(): AppLanguage {
    val locale = NSLocale.currentLocale.languageCode
    return when (locale) {
        "nb", "nn", "no" -> AppLanguage.NORWEGIAN // Norwegian Bokmål, Nynorsk, or generic Norwegian
        else -> AppLanguage.ENGLISH
    }
}
