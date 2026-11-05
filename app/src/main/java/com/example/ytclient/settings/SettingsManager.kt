package com.example.ytclient.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorSource { DYNAMIC, CUSTOM }

enum class AppLanguage(val tag: String?, val display: String) {
    SYSTEM(null, "System default"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    PORTUGUESE("pt", "Português");

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

enum class Resolution(val height: Int) {
    AUTO(-1), P2160(2160), P1440(1440), P1080(1080), P720(720), P480(480), P360(360)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DYNAMIC,
    val seedColor: Int = DEFAULT_SEED,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val resolution: Resolution = Resolution.AUTO,
    val blurStrength: Int = 20,
    val blurEnabled: Boolean = true,
    val liquidGlass: Boolean = false,
    val liveBlur: Boolean = true
) {
    companion object {
        const val DEFAULT_SEED: Int = 0xFFD93025.toInt() // YouTube red
    }
}

private val Context.store: DataStore<Preferences> by preferencesDataStore("ytmaterial_settings")

object SettingsManager {
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
    private val KEY_SEED = intPreferencesKey("seed_color")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_RESOLUTION = stringPreferencesKey("resolution")
    private val KEY_BLUR = intPreferencesKey("blur_strength")
    private val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
    private val KEY_LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
    private val KEY_LIVE_BLUR = booleanPreferencesKey("live_blur")

    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.store
    }

    val settings: Flow<AppSettings> by lazy {
        store.data.map { p ->
            AppSettings(
                themeMode = p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                colorSource = p[KEY_COLOR_SOURCE]?.let {
                    runCatching { ColorSource.valueOf(it) }.getOrNull()
                } ?: ColorSource.DYNAMIC,
                seedColor = p[KEY_SEED] ?: AppSettings.DEFAULT_SEED,
                language = AppLanguage.fromTag(p[KEY_LANGUAGE]),
                resolution = p[KEY_RESOLUTION]?.let {
                    runCatching { Resolution.valueOf(it) }.getOrNull()
                } ?: Resolution.AUTO,
                blurStrength = p[KEY_BLUR] ?: 20,
                blurEnabled = p[KEY_BLUR_ENABLED] ?: true,
                liquidGlass = p[KEY_LIQUID_GLASS] ?: false,
                liveBlur = p[KEY_LIVE_BLUR] ?: true
            )
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setColorSource(source: ColorSource) {
        store.edit { it[KEY_COLOR_SOURCE] = source.name }
    }

    suspend fun setSeedColor(color: Int) {
        store.edit { it[KEY_SEED] = color }
    }

    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[KEY_LANGUAGE] = language.tag ?: "system" }
    }

    suspend fun setResolution(resolution: Resolution) {
        store.edit { it[KEY_RESOLUTION] = resolution.name }
    }

    suspend fun setBlurStrength(strength: Int) {
        store.edit { it[KEY_BLUR] = strength }
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        store.edit { it[KEY_BLUR_ENABLED] = enabled }
    }

    suspend fun setLiquidGlass(enabled: Boolean) {
        store.edit { it[KEY_LIQUID_GLASS] = enabled }
    }

    suspend fun setLiveBlur(enabled: Boolean) {
        store.edit { it[KEY_LIVE_BLUR] = enabled }
    }
}
