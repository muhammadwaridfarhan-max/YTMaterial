package com.example.ytclient

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.ytclient.auth.AuthManager
import com.example.ytclient.data.AppDownloader
import com.example.ytclient.settings.AppLanguage
import com.example.ytclient.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class YTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        AuthManager.init(this)
        val language = runBlocking { SettingsManager.settings.first() }.language
        applyLanguage(this, language)
    }

    companion object {
        fun applyLanguage(context: Context, language: AppLanguage) {
            val tag = language.tag
            val desired = tag ?: ""
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (current != desired) {
                val locales = if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }

            val locale = tag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            val country = locale.country.ifBlank { if (locale.language == "id") "ID" else "US" }
            NewPipe.init(
                AppDownloader(),
                Localization.fromLocale(locale),
                ContentCountry(country)
            )
        }
    }
}
