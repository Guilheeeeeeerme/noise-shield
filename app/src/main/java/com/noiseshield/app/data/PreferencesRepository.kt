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
    val lastSound: MaskingSoundId = MaskingSoundId.WHITE_NOISE,
    /** Unused for UI; app gain is always 1.0 (device volume only). Kept for migration. */
    val lastVolume: Float = 1.0f,
    val favorites: Set<MaskingSoundId> = emptySet(),
    /** When true, auto-switch uses fixed mid Switching/Fade. Default ON. */
    val adaptiveModeEnabled: Boolean = true,
    /** Switching when Adaptive is OFF. Default mid. Selective (0) → Eager (1). */
    val adaptiveSwitching: Float = 0.5f,
    /** Fade when Adaptive is OFF. Default mid. Gentle (0) → Fast (1). Soft-start + switch patience. */
    val adaptiveFade: Float = 0.5f,
    val safetyWarningAcknowledged: Boolean = false,
    val feedbackCounters: Map<MaskingSoundId, Pair<Int, Int>> = emptyMap(),
    val preferredInput: AudioDevicePreference = AudioDevicePreference(),
    val preferredOutput: AudioDevicePreference = AudioDevicePreference(),
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val themeMode = stringPreferencesKey("theme_mode")
        val lastSound = stringPreferencesKey("last_sound")
        val lastVolume = floatPreferencesKey("last_volume")
        val favorites = stringSetPreferencesKey("favorites")
        val adaptiveModeEnabled = booleanPreferencesKey("adaptive_mode_enabled")
        val adaptiveSwitching = floatPreferencesKey("adaptive_switching")
        val adaptiveFade = floatPreferencesKey("adaptive_fade")
        /** Legacy keys for migration. */
        val adaptiveSensitivity = floatPreferencesKey("adaptive_sensitivity")
        val adaptiveDelay = floatPreferencesKey("adaptive_delay")
        val safetyWarningAcknowledged = booleanPreferencesKey("safety_warning_acknowledged")
        val feedbackCounters = stringSetPreferencesKey("feedback_counters")
        val preferredInputFingerprint = stringPreferencesKey("preferred_input_fingerprint")
        val preferredOutputFingerprint = stringPreferencesKey("preferred_output_fingerprint")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val storedSwitching = prefs[Keys.adaptiveSwitching] ?: prefs[Keys.adaptiveSensitivity]
        val storedFade = prefs[Keys.adaptiveFade] ?: prefs[Keys.adaptiveDelay]
        val storedMode = prefs[Keys.adaptiveModeEnabled]
        // Prior Off via sensitivity=0 (no mode key) → Adaptive OFF, reset switching to mid.
        val adaptiveModeEnabled = when {
            storedMode != null -> storedMode
            storedSwitching != null && storedSwitching <= 0f -> false
            else -> true
        }
        val adaptiveSwitching = when {
            storedSwitching != null && storedSwitching <= 0f && storedMode == null -> 0.5f
            storedSwitching != null -> storedSwitching.coerceIn(0f, 1f)
            else -> 0.5f
        }
        val adaptiveFade = (storedFade ?: 0.5f).coerceIn(0f, 1f)
        UserPreferences(
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            themeMode = runCatching {
                AppThemeMode.valueOf(prefs[Keys.themeMode] ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
            lastSound = runCatching {
                MaskingSoundId.valueOf(prefs[Keys.lastSound] ?: MaskingSoundId.WHITE_NOISE.name)
            }.getOrDefault(MaskingSoundId.WHITE_NOISE),
            lastVolume = 1.0f,
            favorites = (prefs[Keys.favorites] ?: emptySet()).mapNotNull {
                runCatching { MaskingSoundId.valueOf(it) }.getOrNull()
            }.toSet(),
            adaptiveModeEnabled = adaptiveModeEnabled,
            adaptiveSwitching = adaptiveSwitching,
            adaptiveFade = adaptiveFade,
            safetyWarningAcknowledged = prefs[Keys.safetyWarningAcknowledged] ?: false,
            feedbackCounters = decodeFeedbackCounters(prefs[Keys.feedbackCounters].orEmpty()),
            preferredInput = AudioDevicePreference(
                fingerprint = prefs[Keys.preferredInputFingerprint]
                    ?: AudioDevicePreference.FINGERPRINT_AUTO,
            ),
            preferredOutput = AudioDevicePreference(
                fingerprint = prefs[Keys.preferredOutputFingerprint]
                    ?: AudioDevicePreference.FINGERPRINT_AUTO,
            ),
        )
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.onboardingDone] = done }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setLastSound(sound: MaskingSoundId) {
        context.dataStore.edit { it[Keys.lastSound] = sound.name }
    }

    suspend fun setLastVolume(volume: Float) {
        context.dataStore.edit { it[Keys.lastVolume] = volume.coerceIn(0f, 1f) }
    }

    suspend fun toggleFavorite(sound: MaskingSoundId) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.favorites]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(sound.name)) current.remove(sound.name)
            prefs[Keys.favorites] = current
        }
    }

    suspend fun setAdaptiveModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.adaptiveModeEnabled] = enabled }
    }

    suspend fun setAdaptiveSwitching(value: Float) {
        context.dataStore.edit {
            it[Keys.adaptiveSwitching] = value.coerceIn(0f, 1f)
            it.remove(Keys.adaptiveSensitivity)
        }
    }

    suspend fun setAdaptiveFade(value: Float) {
        context.dataStore.edit {
            it[Keys.adaptiveFade] = value.coerceIn(0f, 1f)
            it.remove(Keys.adaptiveDelay)
        }
    }

    suspend fun acknowledgeSafetyWarning() {
        context.dataStore.edit { it[Keys.safetyWarningAcknowledged] = true }
    }

    suspend fun recordFeedback(sound: MaskingSoundId, helped: Boolean) {
        context.dataStore.edit { prefs ->
            val counters = decodeFeedbackCounters(prefs[Keys.feedbackCounters].orEmpty()).toMutableMap()
            val current = counters[sound] ?: (0 to 0)
            counters[sound] = if (helped) current.copy(first = current.first + 1)
            else current.copy(second = current.second + 1)
            prefs[Keys.feedbackCounters] = counters.map { (id, value) ->
                "${id.name}:${value.first}:${value.second}"
            }.toSet()
        }
    }

    suspend fun setPreferredInput(fingerprint: String) {
        context.dataStore.edit {
            it[Keys.preferredInputFingerprint] = fingerprint.ifBlank {
                AudioDevicePreference.FINGERPRINT_AUTO
            }
        }
    }

    suspend fun setPreferredOutput(fingerprint: String) {
        context.dataStore.edit {
            it[Keys.preferredOutputFingerprint] = fingerprint.ifBlank {
                AudioDevicePreference.FINGERPRINT_AUTO
            }
        }
    }

    private fun decodeFeedbackCounters(values: Set<String>): Map<MaskingSoundId, Pair<Int, Int>> =
        values.mapNotNull { encoded ->
            val parts = encoded.split(':')
            if (parts.size != 3) return@mapNotNull null
            val sound = runCatching { MaskingSoundId.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val helped = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
            val notHelped = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
            sound to (helped to notHelped)
        }.toMap()
}
