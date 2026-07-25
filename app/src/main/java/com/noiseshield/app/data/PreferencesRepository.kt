package com.noiseshield.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noise_shield")

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val lastSound: MaskingSoundId = MaskingSoundId.WHITE_NOISE,
    val lastVolume: Float = 0.5f,
    val favorites: Set<MaskingSoundId> = emptySet(),
    val lastFeedbackHelped: Boolean? = null,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val themeMode = stringPreferencesKey("theme_mode")
        val language = stringPreferencesKey("language")
        val lastSound = stringPreferencesKey("last_sound")
        val lastVolume = floatPreferencesKey("last_volume")
        val favorites = stringSetPreferencesKey("favorites")
        val lastFeedback = stringPreferencesKey("last_feedback")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs[Keys.themeMode] ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
            language = AppLanguage.fromTag(prefs[Keys.language] ?: AppLanguage.ENGLISH.tag),
            lastSound = runCatching {
                MaskingSoundId.valueOf(prefs[Keys.lastSound] ?: MaskingSoundId.WHITE_NOISE.name)
            }.getOrDefault(MaskingSoundId.WHITE_NOISE),
            lastVolume = prefs[Keys.lastVolume] ?: 0.5f,
            favorites = (prefs[Keys.favorites] ?: emptySet()).mapNotNull {
                runCatching { MaskingSoundId.valueOf(it) }.getOrNull()
            }.toSet(),
            lastFeedbackHelped = when (prefs[Keys.lastFeedback]) {
                "yes" -> true
                "no" -> false
                else -> null
            },
        )
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.onboardingDone] = done }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.language] = language.tag }
    }

    suspend fun setLastSound(sound: MaskingSoundId) {
        context.dataStore.edit { it[Keys.lastSound] = sound.name }
    }

    suspend fun setLastVolume(volume: Float) {
        context.dataStore.edit { it[Keys.lastVolume] = volume }
    }

    suspend fun toggleFavorite(sound: MaskingSoundId) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.favorites]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(sound.name)) current.remove(sound.name)
            prefs[Keys.favorites] = current
        }
    }

    suspend fun setFeedbackHelped(helped: Boolean) {
        context.dataStore.edit { it[Keys.lastFeedback] = if (helped) "yes" else "no" }
    }
}
