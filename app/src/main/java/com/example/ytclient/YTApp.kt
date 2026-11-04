package com.example.ytclient

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ytclient.data.Net
import okhttp3.OkHttpClient
import kotlin.concurrent.thread
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.ytclient.auth.AuthManager
import com.example.ytclient.data.AppDownloader
import com.example.ytclient.data.SubscriptionManager
import com.example.ytclient.settings.AppLanguage
import com.example.ytclient.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class YTApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        AuthManager.init(this)
        com.example.ytclient.auth.AccountManager.init(this)
        com.example.ytclient.data.SearchHistory.init(this)
        SubscriptionManager.init(this)
        val language = runBlocking { SettingsManager.settings.first() }.language
        applyLanguage(this, language)
        // Warm up DNS (and the DoH fallback) off the main thread so the first feed request starts faster.
        thread(isDaemon = true) { runCatching { Net.dns.lookup("www.youtube.com") } }
    }

    /** One shared image loader: memory + disk cache and the same DNS fallback, so scrolling re-uses thumbnails. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { OkHttpClient.Builder().dns(Net.dns).build() }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizePercent(0.02)
                .build()
        }
        .allowRgb565(true)
        .crossfade(120)
        .build()

    companion object {
        @Volatile
        private var lastInit: String? = null

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
            // applyLanguage runs from Application, Activity and a LaunchedEffect: init only on a real change.
            val key = locale.toLanguageTag() + "|" + country
            if (key != lastInit) {
                lastInit = key
                NewPipe.init(
                    AppDownloader(),
                    Localization.fromLocale(locale),
                    ContentCountry(country)
                )
            }
        }
    }
}
