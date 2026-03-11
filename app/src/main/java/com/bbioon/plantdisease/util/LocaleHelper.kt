package com.bbioon.plantdisease.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(languageCode)
        } else {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    fun getCurrentLocale(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                locales[0]?.language ?: "en"
            } else "en"
        } else {
            context.resources.configuration.locales[0]?.language ?: "en"
        }
    }

    fun isRtl(languageCode: String): Boolean = languageCode == "ar"
}
