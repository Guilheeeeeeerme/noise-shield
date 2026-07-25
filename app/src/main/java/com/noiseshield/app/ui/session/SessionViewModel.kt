package com.noiseshield.app.ui.session

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.noiseshield.app.NoiseShieldApp
import com.noiseshield.app.audio.AudioDeviceCatalog
import com.noiseshield.app.data.AppLanguage
import com.noiseshield.app.data.AppThemeMode
import com.noiseshield.app.data.AudioDevicePreference
import com.noiseshield.app.data.AudioRouteDevice
import com.noiseshield.app.data.CoverState
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.MaskingPreset
import com.noiseshield.app.data.NoiseAnalysis
import com.noiseshield.app.data.NoiseLevelBucket
import com.noiseshield.app.data.PreferencesRepository
import com.noiseshield.app.data.UserPreferences
import com.noiseshield.app.service.MaskingPlaybackService
import com.noiseshield.app.service.NativeMaskingPlayer
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import java.util.concurrent.CancellationException

enum class SessionRuntimeState {
    INITIALIZING,
    READY,
    STARTING,
    AUDIBLE,
    FADING,
    MUTED_BY_DEVICE,
    TIMER_ENDED,
    PERMISSION_REQUIRED,
    CAPTURING,
    FOCUS_DELAYED,
    RECOVERING,
    ERROR,
}

data class SessionUiState(
    val playing: Boolean = false,
    val audible: Boolean = false,
    val systemMediaVolumePercent: Int = 0,
    val sound: MaskingSoundId = MaskingSoundId.WHITE_NOISE,
    val volume: Float = 0.5f,
    val timerRemainingSec: Int? = null,
    val limitedMode: Boolean = true,
    val estimate: NoiseAnalysis? = null,
    /** Fraction of full app gain applied after ambient ducking (null when not analyzing). */
    val maskIntensity: Float? = null,
    val coverState: CoverState? = null,
    /** Brief banner when adaptive mode auto-switches the mask. */
    val adaptiveSwitchTo: MaskingSoundId? = null,
    val inputDevices: List<AudioRouteDevice> = emptyList(),
    val outputDevices: List<AudioRouteDevice> = emptyList(),
    val selectedInputFingerprint: String = AudioDevicePreference.FINGERPRINT_AUTO,
    val selectedOutputFingerprint: String = AudioDevicePreference.FINGERPRINT_AUTO,
    val favorites: Set<MaskingSoundId> = emptySet(),
    val showSafetyWarning: Boolean = false,
    val showBreakReminder: Boolean = false,
    val runtimeState: SessionRuntimeState = SessionRuntimeState.INITIALIZING,
    val prefs: UserPreferences = UserPreferences(),
)

class SessionViewModel(
    private val appContext: Context,
    private val prefsRepo: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingStart = false
    private var uiForeground = false
    private var safetyMonitorJob: Job? = null
    private var volumePersistenceJob: Job? = null
    private var playbackReconcileJob: Job? = null
    private var requestedPlaying: Boolean? = null
    private var timerDeadlineElapsedRealtime: Long? = null
    private var lastAppliedInputDeviceId: Int? = null
    private var lastAppliedOutputDeviceId: Int? = null
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshAudioDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshAudioDevices()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncPlayer(player)
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            controller.removeListener(playerListener)
            this@SessionViewModel.controller = null
            controllerFuture?.let(MediaController::releaseFuture)
            controllerFuture = null
            _state.update { it.copy(runtimeState = SessionRuntimeState.INITIALIZING) }
            viewModelScope.launch {
                delay(500L)
                connectController()
            }
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                MaskingPlaybackService.COMMAND_ANALYSIS_EVENT -> receiveAnalysis(args)
                MaskingPlaybackService.COMMAND_TIMER_EVENT -> receiveTimer(args)
                MaskingPlaybackService.COMMAND_BREAK_REMINDER ->
                    _state.update { it.copy(showBreakReminder = true) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    init {
        connectController()
        audioManager?.registerAudioDeviceCallback(deviceCallback, mainHandler)
        refreshAudioDevices()

        viewModelScope.launch {
            prefsRepo.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        prefs = prefs,
                        favorites = prefs.favorites,
                        sound = if (!it.playing) prefs.lastSound else it.sound,
                        volume = if (!it.playing) prefs.lastVolume else it.volume,
                        selectedInputFingerprint = prefs.preferredInput.fingerprint,
                        selectedOutputFingerprint = prefs.preferredOutput.fingerprint,
                    )
                }
                if (controller != null && !_state.value.playing) applyPreferencesToController(prefs)
            }
        }
        refreshMicPermission()
    }

    private fun connectController() {
        if (controller != null || controllerFuture?.isDone == false) return
        val token = SessionToken(
            appContext,
            ComponentName(appContext, MaskingPlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    controller = future.get().also { mediaController ->
                        mediaController.addListener(playerListener)
                        applyPreferencesToController(_state.value.prefs)
                        setUiForeground(uiForeground)
                        if (pendingStart) {
                            pendingStart = false
                            startSession()
                        } else {
                            syncPlayer(mediaController)
                        }
                    }
                } catch (_: ExecutionException) {
                    _state.update { it.copy(runtimeState = SessionRuntimeState.ERROR) }
                } catch (_: CancellationException) {
                    // ViewModel cleared or a superseded reconnection attempt.
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    _state.update { it.copy(runtimeState = SessionRuntimeState.ERROR) }
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun refreshMicPermission() {
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        _state.update {
            it.copy(
                limitedMode = !granted,
                runtimeState = if (!granted) SessionRuntimeState.PERMISSION_REQUIRED
                else if (it.playing) SessionRuntimeState.CAPTURING else SessionRuntimeState.READY,
                estimate = if (granted) it.estimate else null,
                maskIntensity = if (granted) it.maskIntensity else null,
                coverState = if (granted) it.coverState else null,
            )
        }
        setUiForeground(uiForeground)
    }

    fun setUiForeground(foreground: Boolean) {
        uiForeground = foreground
        sendBooleanCommand(MaskingPlaybackService.COMMAND_SET_UI_FOREGROUND, foreground)
        if (!foreground) {
            _state.update { it.copy(estimate = null, maskIntensity = null, coverState = null) }
        }
    }

    fun togglePlay() {
        val current = _state.value
        if (current.playing || current.audible) stopSession() else startSession()
    }

    fun startSession() {
        refreshMicPermission()
        val mediaController = controller
        if (mediaController == null) {
            pendingStart = true
            return
        }
        val current = _state.value
        requestedPlaying = true
        mediaController.setMediaItem(NativeMaskingPlayer.mediaItemFor(current.sound))
        sendVolume(current.volume)
        mediaController.prepare()
        mediaController.play()
        _state.update {
            it.copy(
                playing = true,
                volume = current.volume,
                runtimeState = SessionRuntimeState.STARTING,
                systemMediaVolumePercent = systemMediaVolumePercent(),
            )
        }
        if (!current.prefs.safetyWarningAcknowledged &&
            (current.volume > 0.7f || isSystemMediaVolumeHigh())
        ) {
            _state.update { it.copy(showSafetyWarning = true) }
        }
        safetyMonitorJob?.cancel()
        safetyMonitorJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val latest = _state.value
                if (!latest.playing) break
                updateMediaVolumeState()
                if (!latest.prefs.safetyWarningAcknowledged &&
                    (latest.volume > 0.7f || isSystemMediaVolumeHigh())
                ) {
                    _state.update { it.copy(showSafetyWarning = true) }
                }
            }
        }
        setUiForeground(uiForeground)
        viewModelScope.launch {
            prefsRepo.setLastSound(current.sound)
            prefsRepo.setLastVolume(current.volume)
        }
        reconcilePlayback(true)
    }

    fun stopSession() {
        requestedPlaying = false
        controller?.pause()
        safetyMonitorJob?.cancel()
        _state.update {
            it.copy(
                playing = false,
                estimate = null,
                maskIntensity = null,
                coverState = null,
                adaptiveSwitchTo = null,
            )
        }
        reconcilePlayback(false)
    }

    fun selectSound(sound: MaskingSoundId) {
        _state.update { it.copy(sound = sound) }
        controller?.setMediaItem(NativeMaskingPlayer.mediaItemFor(sound))
        viewModelScope.launch { prefsRepo.setLastSound(sound) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        sendVolume(clamped)
        volumePersistenceJob?.cancel()
        volumePersistenceJob = viewModelScope.launch {
            delay(250L)
            prefsRepo.setLastVolume(clamped)
        }
    }

    fun setMaskingPreset(preset: MaskingPreset) {
        _state.update {
            it.copy(prefs = it.prefs.copy(maskingPreset = preset))
        }
        sendAdaptiveParams(
            enabled = true,
            switching = preset.switching,
            fade = preset.fade,
        )
        viewModelScope.launch { prefsRepo.setMaskingPreset(preset) }
    }

    fun setTimerMinutes(minutes: Int?) {
        val durationMs = (minutes?.coerceAtLeast(0)?.toLong() ?: 0L) * 60_000L
        val args = Bundle().apply {
            putLong(MaskingPlaybackService.ARG_DURATION_MS, durationMs)
        }
        controller?.sendCustomCommand(
            SessionCommand(MaskingPlaybackService.COMMAND_SET_TIMER, Bundle.EMPTY),
            args,
        )
        if (durationMs == 0L) {
            timerDeadlineElapsedRealtime = null
            _state.update { it.copy(timerRemainingSec = null) }
        }
    }

    fun toggleFavorite(sound: MaskingSoundId) {
        viewModelScope.launch { prefsRepo.toggleFavorite(sound) }
    }

    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM || language.tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setInputDevice(fingerprint: String) {
        if (_state.value.playing) return
        val normalized = fingerprint.ifBlank { AudioDevicePreference.FINGERPRINT_AUTO }
        _state.update { it.copy(selectedInputFingerprint = normalized) }
        val prefs = _state.value.prefs.copy(
            preferredInput = AudioDevicePreference(fingerprint = normalized),
        )
        _state.update { it.copy(prefs = prefs) }
        applyAudioRouting(prefs)
        viewModelScope.launch { prefsRepo.setPreferredInput(normalized) }
    }

    fun setOutputDevice(fingerprint: String) {
        if (_state.value.playing) return
        val normalized = fingerprint.ifBlank { AudioDevicePreference.FINGERPRINT_AUTO }
        _state.update { it.copy(selectedOutputFingerprint = normalized) }
        val prefs = _state.value.prefs.copy(
            preferredOutput = AudioDevicePreference(fingerprint = normalized),
        )
        _state.update { it.copy(prefs = prefs) }
        applyAudioRouting(prefs)
        viewModelScope.launch { prefsRepo.setPreferredOutput(normalized) }
    }

    fun dismissSafetyWarning() {
        viewModelScope.launch { prefsRepo.acknowledgeSafetyWarning() }
        _state.update { it.copy(showSafetyWarning = false) }
    }

    fun dismissBreakReminder() = _state.update { it.copy(showBreakReminder = false) }
    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingDone(true) }
    }

    private fun receiveAnalysis(args: Bundle) {
        if (!uiForeground || _state.value.limitedMode) return
        val sound = args.getString(MaskingPlaybackService.ARG_SOUND_ID)?.let {
            runCatching { MaskingSoundId.valueOf(it) }.getOrNull()
        } ?: return
        val coverOrdinal = args.getInt(MaskingPlaybackService.ARG_COVER_STATE, 0)
        val analysis = NoiseAnalysis(
            relativeDbfs = args.getFloat(MaskingPlaybackService.ARG_RELATIVE_DBFS),
            levelBucket = NoiseLevelBucket.fromOrdinal(
                args.getInt(MaskingPlaybackService.ARG_LEVEL_BUCKET),
            ),
            suggestedSoundId = sound,
            confidence = args.getFloat(MaskingPlaybackService.ARG_CONFIDENCE),
            melBandEnergies = args.getFloatArray(MaskingPlaybackService.ARG_MEL_ENERGIES)
                ?.toList().orEmpty(),
            capturedAtElapsedRealtime = args.getLong(MaskingPlaybackService.ARG_CAPTURED_AT),
            selfMatch = args.getFloat(MaskingPlaybackService.ARG_SELF_MATCH, 0f),
            residualDbfs = args.getFloat(
                MaskingPlaybackService.ARG_RESIDUAL_DBFS,
                args.getFloat(MaskingPlaybackService.ARG_RELATIVE_DBFS),
            ),
            coverState = CoverState.entries.getOrElse(coverOrdinal) { CoverState.LISTENING },
        )
        val intensity = if (args.containsKey(MaskingPlaybackService.ARG_MASK_INTENSITY)) {
            args.getFloat(MaskingPlaybackService.ARG_MASK_INTENSITY).coerceIn(0f, 1f)
        } else {
            null
        }
        _state.update {
            it.copy(
                estimate = analysis,
                maskIntensity = intensity,
                coverState = analysis.coverState,
                runtimeState = SessionRuntimeState.CAPTURING,
            )
        }
    }

    private fun syncPlayer(player: Player) {
        val displayedPlaying = requestedPlaying ?: player.playWhenReady
        val previousSound = _state.value.sound
        val sound = player.currentMediaItem?.mediaId?.let {
            runCatching { MaskingSoundId.valueOf(it) }.getOrNull()
        } ?: _state.value.sound
        val runtime = when {
            player.playbackState == Player.STATE_BUFFERING && _state.value.audible ->
                SessionRuntimeState.FADING
            player.playbackState == Player.STATE_BUFFERING -> SessionRuntimeState.STARTING
            player.playbackSuppressionReason ==
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                SessionRuntimeState.FOCUS_DELAYED
            player.playerError != null -> SessionRuntimeState.ERROR
            player.isPlaying && systemMediaVolumePercent() == 0 ->
                SessionRuntimeState.MUTED_BY_DEVICE
            player.isPlaying && !_state.value.limitedMode -> SessionRuntimeState.CAPTURING
            player.isPlaying -> SessionRuntimeState.AUDIBLE
            player.playWhenReady -> SessionRuntimeState.STARTING
            _state.value.runtimeState == SessionRuntimeState.TIMER_ENDED ->
                SessionRuntimeState.TIMER_ENDED
            _state.value.limitedMode -> SessionRuntimeState.PERMISSION_REQUIRED
            else -> SessionRuntimeState.READY
        }
        val adaptiveSwitch = if (
            sound != previousSound &&
            _state.value.playing &&
            !_state.value.limitedMode &&
            sound == _state.value.estimate?.suggestedSoundId
        ) {
            sound
        } else if (sound == previousSound) {
            _state.value.adaptiveSwitchTo
        } else {
            null
        }
        _state.update {
            it.copy(
                playing = displayedPlaying,
                audible = player.isPlaying && systemMediaVolumePercent() > 0,
                sound = sound,
                systemMediaVolumePercent = systemMediaVolumePercent(),
                runtimeState = runtime,
                adaptiveSwitchTo = adaptiveSwitch,
                maskIntensity = if (displayedPlaying) it.maskIntensity else null,
            )
        }
        if (sound != previousSound) {
            viewModelScope.launch { prefsRepo.setLastSound(sound) }
            if (adaptiveSwitch == sound) {
                viewModelScope.launch {
                    delay(3_500L)
                    _state.update { state ->
                        if (state.adaptiveSwitchTo == sound) {
                            state.copy(adaptiveSwitchTo = null)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    private fun reconcilePlayback(shouldPlay: Boolean) {
        playbackReconcileJob?.cancel()
        playbackReconcileJob = viewModelScope.launch {
            delay(500L)
            controller?.let { mediaController ->
                if (mediaController.playWhenReady != shouldPlay) {
                    if (shouldPlay) mediaController.play() else mediaController.pause()
                }
            }
            delay(300L)
            if (requestedPlaying == shouldPlay) {
                requestedPlaying = null
                controller?.let(::syncPlayer)
            }
        }
    }

    private fun receiveTimer(args: Bundle) {
        val deadline = args.getLong(MaskingPlaybackService.ARG_TIMER_DEADLINE)
        val pausedRemaining = args.getLong(MaskingPlaybackService.ARG_TIMER_REMAINING)
        val completed = args.getBoolean(MaskingPlaybackService.ARG_TIMER_COMPLETED)
        if (completed) requestedPlaying = null
        timerDeadlineElapsedRealtime = deadline.takeIf { it > 0L }
        val remainingMs = when {
            deadline > 0L -> deadline - SystemClock.elapsedRealtime()
            pausedRemaining > 0L -> pausedRemaining
            else -> 0L
        }.coerceAtLeast(0L)
        _state.update {
            it.copy(
                playing = if (completed) false else it.playing,
                audible = if (completed) false else it.audible,
                timerRemainingSec = remainingMs.takeIf { value -> value > 0L }
                    ?.let { value -> ((value + 999L) / 1_000L).toInt() },
                runtimeState = if (completed) {
                    SessionRuntimeState.TIMER_ENDED
                } else {
                    it.runtimeState
                },
            )
        }
    }

    private fun sendBooleanCommand(action: String, enabled: Boolean) {
        val args = Bundle().apply { putBoolean(MaskingPlaybackService.ARG_ENABLED, enabled) }
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    private fun sendAdaptiveParams(enabled: Boolean, switching: Float, fade: Float) {
        val args = Bundle().apply {
            putBoolean(MaskingPlaybackService.ARG_ENABLED, enabled)
            putFloat(MaskingPlaybackService.ARG_SWITCHING, switching.coerceIn(0f, 1f))
            putFloat(MaskingPlaybackService.ARG_FADE, fade.coerceIn(0f, 1f))
        }
        controller?.sendCustomCommand(
            SessionCommand(MaskingPlaybackService.COMMAND_SET_ADAPTIVE_PARAMS, Bundle.EMPTY),
            args,
        )
    }

    private fun sendVolume(volume: Float) {
        val args = Bundle().apply {
            putFloat(MaskingPlaybackService.ARG_VOLUME, volume.coerceIn(0f, 1f))
        }
        controller?.sendCustomCommand(
            SessionCommand(MaskingPlaybackService.COMMAND_SET_APP_VOLUME, Bundle.EMPTY),
            args,
        )
    }

    private fun isSystemMediaVolumeHigh(): Boolean {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return maximum > 0 &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum > 0.7f
    }

    private fun systemMediaVolumePercent(): Int {
        val maximum = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return 0
        if (maximum <= 0) return 0
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current * 100f) / maximum).toInt().coerceIn(0, 100)
    }

    private fun updateMediaVolumeState() {
        val percent = systemMediaVolumePercent()
        _state.update {
            val runtime = when {
                it.playing && percent == 0 -> SessionRuntimeState.MUTED_BY_DEVICE
                it.runtimeState == SessionRuntimeState.MUTED_BY_DEVICE && !it.limitedMode ->
                    SessionRuntimeState.CAPTURING
                it.runtimeState == SessionRuntimeState.MUTED_BY_DEVICE ->
                    SessionRuntimeState.AUDIBLE
                else -> it.runtimeState
            }
            it.copy(
                systemMediaVolumePercent = percent,
                audible = it.playing && percent > 0 &&
                    runtime != SessionRuntimeState.STARTING &&
                    runtime != SessionRuntimeState.FOCUS_DELAYED &&
                    runtime != SessionRuntimeState.RECOVERING,
                runtimeState = runtime,
            )
        }
    }

    private fun applyPreferencesToController(prefs: UserPreferences) {
        controller?.apply {
            setMediaItem(NativeMaskingPlayer.mediaItemFor(prefs.lastSound))
        }
        sendVolume(prefs.lastVolume)
        sendAdaptiveParams(
            enabled = true,
            switching = prefs.maskingPreset.switching,
            fade = prefs.maskingPreset.fade,
        )
        applyAudioRouting(prefs)
    }

    private fun applyAudioRouting(prefs: UserPreferences) {
        val inputs = AudioDeviceCatalog.listInputs(appContext)
        val outputs = AudioDeviceCatalog.listOutputs(appContext)
        _state.update {
            it.copy(
                inputDevices = inputs,
                outputDevices = outputs,
                selectedInputFingerprint = prefs.preferredInput.fingerprint,
                selectedOutputFingerprint = prefs.preferredOutput.fingerprint,
            )
        }
        val inputId = AudioDeviceCatalog.resolveDeviceId(
            available = inputs,
            preference = prefs.preferredInput,
            preferBuiltinWhenAuto = true,
            preferBluetoothWhenAuto = false,
        )
        val outputId = AudioDeviceCatalog.resolveDeviceId(
            available = outputs,
            preference = prefs.preferredOutput,
            preferBuiltinWhenAuto = false,
            preferBluetoothWhenAuto = true,
        )
        if (inputId == lastAppliedInputDeviceId && outputId == lastAppliedOutputDeviceId) {
            return
        }
        lastAppliedInputDeviceId = inputId
        lastAppliedOutputDeviceId = outputId
        val mediaController = controller ?: return
        val args = Bundle().apply {
            putInt(MaskingPlaybackService.ARG_INPUT_DEVICE_ID, inputId)
            putInt(MaskingPlaybackService.ARG_OUTPUT_DEVICE_ID, outputId)
        }
        mediaController.sendCustomCommand(
            SessionCommand(MaskingPlaybackService.COMMAND_SET_AUDIO_DEVICES, Bundle.EMPTY),
            args,
        )
    }

    private fun refreshAudioDevices() {
        applyAudioRouting(_state.value.prefs)
    }

    override fun onCleared() {
        safetyMonitorJob?.cancel()
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        super.onCleared()
    }

    companion object {
        fun factory(app: NoiseShieldApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(app, app.preferencesRepository) as T
            }
    }
}
