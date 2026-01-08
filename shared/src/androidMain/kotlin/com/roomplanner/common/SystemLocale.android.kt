package com.roomplanner.common

import com.roomplanner.localization.AppLanguage
import java.util.Locale

actual fun getSystemLanguage(): AppLanguage {
    val locale = Locale.getDefault().language
    return when (locale) {
        "nb", "nn", "no" -> AppLanguage.NORWEGIAN // Norwegian Bokmål, Nynorsk, or generic Norwegian
        else -> AppLanguage.ENGLISH
    }
}
