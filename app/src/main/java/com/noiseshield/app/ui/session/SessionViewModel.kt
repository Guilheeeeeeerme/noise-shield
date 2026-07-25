package com.noiseshield.app.ui.session

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noiseshield.app.NoiseShieldApp
import com.noiseshield.app.data.AppLanguage
import com.noiseshield.app.data.AppThemeMode
import com.noiseshield.app.data.BroadProfile
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.NoiseEstimate
import com.noiseshield.app.data.PROFILE_TO_SOUND
import com.noiseshield.app.data.PreferencesRepository
import com.noiseshield.app.data.UserPreferences
import com.noiseshield.app.service.MaskingPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SessionUiState(
    val playing: Boolean = false,
    val sound: MaskingSoundId = MaskingSoundId.WHITE_NOISE,
    val volume: Float = 0.5f,
    val timerRemainingSec: Int? = null,
    val limitedMode: Boolean = true,
    val estimate: NoiseEstimate? = null,
    val manualOverride: Boolean = false,
    val lastAutoProfile: BroadProfile? = null,
    val favorites: Set<MaskingSoundId> = emptySet(),
    val showFeedback: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
)

class SessionViewModel(
    private val appContext: Context,
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var service: MaskingPlaybackService? = null
    private var bound = false
    private var timerJob: Job? = null
    private var estimateJob: Job? = null
    private var pendingPlay = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as MaskingPlaybackService.LocalBinder
            service = local.getService()
            bound = true
            val s = _state.value
            if (pendingPlay || s.playing) {
                pendingPlay = false
                service?.configure(s.sound, s.volume, true)
                _state.update { it.copy(playing = true) }
                if (!s.limitedMode) {
                    service?.startCapture()
                    watchEstimates()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    init {
        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        prefs = prefs,
                        favorites = prefs.favorites,
                        sound = if (!it.playing) prefs.lastSound else it.sound,
                        volume = if (!it.playing) prefs.lastVolume else it.volume,
                    )
                }
            }
        }
        refreshMicPermission()
    }

    fun bindIfNeeded() {
        if (bound) return
        MaskingPlaybackService.start(appContext)
        appContext.bindService(
            Intent(appContext, MaskingPlaybackService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun refreshMicPermission() {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(limitedMode = !granted) }
        if (!granted) {
            service?.stopCapture()
            estimateJob?.cancel()
            _state.update { it.copy(estimate = null) }
        }
    }

    fun togglePlay() {
        val next = !_state.value.playing
        if (next) startSession() else stopSession(showFeedback = true)
    }

    fun startSession() {
        refreshMicPermission()
        val s = _state.value
        _state.update { it.copy(playing = true, showFeedback = false) }
        viewModelScope.launch {
            prefsRepo.setLastSound(s.sound)
            prefsRepo.setLastVolume(s.volume)
        }
        if (service != null) {
            service?.configure(s.sound, s.volume, true)
            if (!s.limitedMode) {
                service?.startCapture()
                watchEstimates()
            }
        } else {
            pendingPlay = true
            ensureServiceStarted()
        }
    }

    fun stopSession(showFeedback: Boolean = false) {
        timerJob?.cancel()
        estimateJob?.cancel()
        service?.setPlaying(false)
        service?.stopCapture()
        _state.update {
            it.copy(
                playing = false,
                timerRemainingSec = null,
                estimate = null,
                showFeedback = showFeedback,
                manualOverride = false,
            )
        }
    }

    fun selectSound(sound: MaskingSoundId, manual: Boolean = true) {
        _state.update {
            it.copy(
                sound = sound,
                manualOverride = manual,
                lastAutoProfile = if (manual) {
                    it.estimate?.broadProfile ?: it.lastAutoProfile
                } else {
                    it.lastAutoProfile
                },
            )
        }
        service?.setSound(sound)
        viewModelScope.launch { prefsRepo.setLastSound(sound) }
    }

    fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume) }
        service?.setVolume(volume)
        viewModelScope.launch { prefsRepo.setLastVolume(volume) }
    }

    fun setTimerMinutes(minutes: Int?) {
        timerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(timerRemainingSec = null) }
            return
        }
        _state.update { it.copy(timerRemainingSec = minutes * 60) }
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val remaining = _state.value.timerRemainingSec ?: break
                if (remaining <= 1) {
                    stopSession(showFeedback = true)
                    break
                }
                _state.update { it.copy(timerRemainingSec = remaining - 1) }
            }
        }
    }

    fun toggleFavorite(sound: MaskingSoundId) {
        viewModelScope.launch { prefsRepo.toggleFavorite(sound) }
    }

    fun submitFeedback(helped: Boolean) {
        viewModelScope.launch { prefsRepo.setFeedbackHelped(helped) }
        _state.update { it.copy(showFeedback = false) }
    }

    fun dismissFeedback() {
        _state.update { it.copy(showFeedback = false) }
    }

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingDone(true) }
    }

    private fun watchEstimates() {
        estimateJob?.cancel()
        val svc = service ?: return
        estimateJob = viewModelScope.launch {
            svc.estimate.collect { estimate ->
                _state.update { it.copy(estimate = estimate) }
                if (estimate == null) return@collect
                maybeAutoApply(estimate.broadProfile)
            }
        }
    }

    private fun maybeAutoApply(profile: BroadProfile) {
        val s = _state.value
        if (!s.playing || s.limitedMode) return
        // Manual override holds until ambient profile changes.
        if (s.manualOverride && s.lastAutoProfile == profile) return

        val mapped = PROFILE_TO_SOUND[profile] ?: MaskingSoundId.WHITE_NOISE
        if (mapped == s.sound && s.lastAutoProfile == profile && !s.manualOverride) return

        _state.update {
            it.copy(
                sound = mapped,
                lastAutoProfile = profile,
                manualOverride = false,
            )
        }
        service?.setSound(mapped, crossfade = 0.4f)
    }

    private fun ensureServiceStarted() {
        MaskingPlaybackService.start(appContext)
        bindIfNeeded()
    }

    override fun onCleared() {
        if (bound) {
            appContext.unbindService(connection)
            bound = false
        }
        super.onCleared()
    }

    companion object {
        fun factory(app: NoiseShieldApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionViewModel(app, app.preferencesRepository) as T
                }
            }
    }
}
