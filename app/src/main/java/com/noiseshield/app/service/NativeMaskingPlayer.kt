package com.noiseshield.app.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.noiseshield.app.R
import com.noiseshield.app.audio.MaskingEngine
import com.noiseshield.app.audio.AssetSoundDecoder
import com.noiseshield.app.data.MaskingSoundId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class NativeMaskingPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(Looper.getMainLooper()), AudioManager.OnAudioFocusChangeListener {
    private val engine = MaskingEngine(scope)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val releaseEngine = Runnable {
        if (!playWhenReady) {
            engine.release()
            initialized = false
            playbackState = Player.STATE_IDLE
            invalidateState()
        }
    }

    private var initialized = false
    private var loadingAsset = false
    private var playWhenReady = false
    private var playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var playbackState = Player.STATE_IDLE
    private var playerError: PlaybackException? = null
    private var focusSuppressed = false
    private var resumeAfterTransientLoss = false
    private var selectedSound = MaskingSoundId.WHITE_NOISE
    private var volume = 1.0f
    /** Ambient-linked intensity under the user volume ceiling (1 = full slider). */
    private var ambientScale = 1f
    /** Audio-focus duck multiplier (1 = unducked). */
    private var focusDuck = 1f
    /** Soft-start multiplier: begins audibly, then ramps to 1 over fade duration. */
    private var introScale = 0f
    /** Fade slider 0=Gentle (long) … 1=Fast (short). Mid 0.5 → 2s. */
    private var softStartFade = 0.5f
    private var preferredInputDeviceId = 0
    private var preferredOutputDeviceId = 0
    private var focusRequest: AudioFocusRequest? = null
    private var recoveryObserver: Job? = null
    private var decodeJob: Job? = null
    private val introRampTick = object : Runnable {
        override fun run() {
            if (!playWhenReady) return
            val rampMs = introRampMs().coerceAtLeast(1f)
            val elapsed = System.currentTimeMillis() - introRampStartedAtMs
            val progress = (elapsed.toFloat() / rampMs).coerceIn(0f, 1f)
            introScale = INTRO_SCALE_MIN + progress * (1f - INTRO_SCALE_MIN)
            applyEffectiveVolume()
            if (introScale < 1f) {
                handler.postDelayed(this, INTRO_TICK_MS)
            }
        }
    }
    private var introRampStartedAtMs = 0L

    val estimate get() = engine.estimate
    val currentSound get() = selectedSound
    /** Actual app gain after focus ducking and intro fade. */
    val maskIntensity: Float
        get() = (volume * focusDuck * introScale).coerceIn(0f, 1f)

    init {
        recoveryObserver = scope.launch {
            engine.recoveryState.collect { recovery ->
                handler.post {
                    playbackState = when {
                        recovery != 0 -> Player.STATE_BUFFERING
                        initialized && !loadingAsset -> Player.STATE_READY
                        else -> Player.STATE_IDLE
                    }
                    invalidateState()
                }
            }
        }
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_SET_VOLUME)
            .build()
        val playlist = MaskingSoundId.entries.map { sound ->
            val item = localizedMediaItemFor(sound)
            MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, playWhenReadyChangeReason)
            .setPlaybackState(playbackState)
            .setPlayerError(playerError)
            .setPlaybackSuppressionReason(
                if (focusSuppressed) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                else Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            )
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(selectedSound.index)
            .setVolume(volume)
            .build()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        ensureInitialized()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        handler.removeCallbacks(releaseEngine)
        if (playWhenReady) {
            ensureInitialized()
            if (!initialized) {
                this.playWhenReady = false
                handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                invalidateState()
                return Futures.immediateVoidFuture()
            }
            when (requestAudioFocus()) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    focusSuppressed = false
                    resumeAfterTransientLoss = false
                    this.playWhenReady = true
                    startSoftIntro()
                    engine.setPlaying(true)
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    focusSuppressed = true
                    resumeAfterTransientLoss = true
                    this.playWhenReady = true
                    startSoftIntro()
                    engine.setPlaying(false)
                }
                else -> {
                    focusSuppressed = false
                    this.playWhenReady = false
                    cancelSoftIntro()
                    engine.setPlaying(false)
                    handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                }
            }
        } else {
            this.playWhenReady = false
            focusSuppressed = false
            resumeAfterTransientLoss = false
            focusDuck = 1f
            cancelSoftIntro()
            engine.setPlaying(false)
            abandonAudioFocus()
            handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        focusDuck = 1f
        cancelSoftIntro()
        engine.setPlaying(false)
        engine.stopCapture()
        resetAmbientScale()
        abandonAudioFocus()
        handler.removeCallbacks(releaseEngine)
        handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseNative()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(
        volume: Float,
        volumeOperationType: Int,
    ): ListenableFuture<*> {
        setAppVolume(volume)
        return Futures.immediateVoidFuture()
    }

    fun setAppVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        applyEffectiveVolume()
        invalidateState()
    }

    /**
     * Sets ambient intensity (0..1). Quiet rooms use a low scale so the mask
     * stays under the user volume ceiling without exceeding it.
     */
    fun setAmbientScale(scale: Float) {
        ambientScale = scale.coerceIn(0f, 1f)
        applyEffectiveVolume()
    }

    fun resetAmbientScale() {
        ambientScale = 1f
        applyEffectiveVolume()
    }

    /**
     * Sets soft-start fade length. 0 = Gentle (4s), 0.5 = mid (2s), 1 = Fast (1s).
     * Applies on the next Play; does not restart an in-progress intro.
     */
    fun setSoftStartFade(fade: Float) {
        softStartFade = fade.coerceIn(0f, 1f)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val selected = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
        val sound = selected?.mediaId?.let { id ->
            runCatching { MaskingSoundId.valueOf(id) }.getOrNull()
        } ?: selectedSound
        setSound(sound)
        return Futures.immediateVoidFuture()
    }

    fun setSound(sound: MaskingSoundId, crossfadeSeconds: Float = transitionSeconds()) {
        if (sound == selectedSound && initialized) return
        selectedSound = sound
        if (initialized) prepareSound(sound, crossfadeSeconds)
        invalidateState()
    }

    fun startCapture(): Boolean {
        if (!initialized) return false
        engine.setInputDeviceId(preferredInputDeviceId)
        return engine.startCapture()
    }

    fun stopCapture() {
        engine.stopCapture()
        resetAmbientScale()
    }

    fun setPreferredDevices(inputDeviceId: Int, outputDeviceId: Int) {
        val nextInput = inputDeviceId.coerceAtLeast(0)
        val nextOutput = outputDeviceId.coerceAtLeast(0)
        val inputChanged = nextInput != preferredInputDeviceId
        val outputChanged = nextOutput != preferredOutputDeviceId
        preferredInputDeviceId = nextInput
        preferredOutputDeviceId = nextOutput
        if (!initialized) return
        if (inputChanged) engine.setInputDeviceId(preferredInputDeviceId)
        if (outputChanged) engine.setOutputDeviceId(preferredOutputDeviceId)
    }

    fun xRunCount(): Int = engine.xRunCount()

    override fun onAudioFocusChange(focusChange: Int) {
        handler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusSuppressed = false
                    focusDuck = 1f
                    applyEffectiveVolume()
                    if (playWhenReady && resumeAfterTransientLoss) {
                        startSoftIntro()
                        engine.setPlaying(true)
                    }
                    resumeAfterTransientLoss = false
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeAfterTransientLoss = playWhenReady
                    focusSuppressed = true
                    engine.setPlaying(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    focusSuppressed = false
                    focusDuck = DUCK_FACTOR
                    applyEffectiveVolume()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeAfterTransientLoss = false
                    focusSuppressed = false
                    focusDuck = 1f
                    playWhenReady = false
                    playWhenReadyChangeReason =
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                    cancelSoftIntro()
                    engine.setPlaying(false)
                    abandonAudioFocus()
                    handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                }
            }
            invalidateState()
        }
    }

    private fun applyEffectiveVolume() {
        if (!initialized) return
        val effective = (volume * focusDuck * introScale).coerceIn(0f, 1f)
        engine.setVolume(effective)
    }

    private fun startSoftIntro() {
        handler.removeCallbacks(introRampTick)
        introScale = INTRO_SCALE_MIN
        applyEffectiveVolume()
        introRampStartedAtMs = System.currentTimeMillis()
        handler.post(introRampTick)
    }

    private fun introRampMs(): Float {
        val fade = softStartFade.coerceIn(0f, 1f)
        return if (fade >= 0.5f) {
            INTRO_RAMP_MS_MID + (fade - 0.5f) / 0.5f * (INTRO_RAMP_MS_MIN - INTRO_RAMP_MS_MID)
        } else {
            INTRO_RAMP_MS_MAX + (fade / 0.5f) * (INTRO_RAMP_MS_MID - INTRO_RAMP_MS_MAX)
        }
    }

    private fun cancelSoftIntro() {
        handler.removeCallbacks(introRampTick)
        introScale = 0f
        applyEffectiveVolume()
    }

    fun releaseNative() {
        recoveryObserver?.cancel()
        recoveryObserver = null
        decodeJob?.cancel()
        decodeJob = null
        handler.removeCallbacks(releaseEngine)
        handler.removeCallbacks(introRampTick)
        engine.stopCapture()
        engine.release()
        abandonAudioFocus()
        initialized = false
    }

    private fun ensureInitialized() {
        if (initialized) return
        initialized = engine.init()
        playbackState = if (initialized) Player.STATE_READY else Player.STATE_IDLE
        playerError = if (initialized) null else PlaybackException(
            context.getString(R.string.error_audio_initialization),
            null,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
        if (initialized) {
            engine.setInputDeviceId(preferredInputDeviceId)
            engine.setOutputDeviceId(preferredOutputDeviceId)
            prepareSound(selectedSound, 0f)
            applyEffectiveVolume()
        }
    }

    private fun prepareSound(sound: MaskingSoundId, crossfadeSeconds: Float) {
        decodeJob?.cancel()
        val assetFile = sound.assetFile
        if (assetFile == null) {
            loadingAsset = false
            engine.setSound(sound, crossfadeSeconds)
            playbackState = Player.STATE_READY
            invalidateState()
            return
        }
        loadingAsset = true
        playbackState = Player.STATE_BUFFERING
        // Keep output audible while the requested ambience is decoded.
        engine.setSound(MaskingSoundId.PINK_NOISE, 0f)
        invalidateState()
        decodeJob = scope.launch(Dispatchers.IO) {
            val decoded = AssetSoundDecoder.decode(context, sound)
            if (decoded != null) {
                engine.loadPcm16(sound, decoded.samples, decoded.sampleRate)
            }
            handler.post {
                if (selectedSound == sound && initialized) {
                    if (decoded != null) engine.setSound(sound, crossfadeSeconds)
                    loadingAsset = false
                    playbackState = Player.STATE_READY
                    invalidateState()
                }
            }
        }
    }

    private fun transitionSeconds(): Float = introRampMs() / 1_000f

    private fun requestAudioFocus(): Int {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(this, handler)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun localizedMediaItemFor(sound: MaskingSoundId): MediaItem =
        MediaItem.Builder()
            .setMediaId(sound.name)
            .setUri("noiseshield://${sound.name}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(
                        context.getString(
                            when (sound) {
                                MaskingSoundId.WHITE_NOISE -> R.string.sound_white_noise
                                MaskingSoundId.PINK_NOISE -> R.string.sound_pink_noise
                                MaskingSoundId.BROWN_NOISE -> R.string.sound_brown_noise
                                MaskingSoundId.OCEAN_WAVES -> R.string.sound_ocean_waves
                                MaskingSoundId.RAIN -> R.string.sound_rain
                                MaskingSoundId.FAN -> R.string.sound_fan
                                MaskingSoundId.AIR_CONDITIONER -> R.string.sound_air_conditioner
                                MaskingSoundId.CAFE_AMBIENCE -> R.string.sound_cafe_ambience
                            },
                        ),
                    )
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    companion object {
        private const val ENGINE_RELEASE_DELAY_MS = 30_000L
        private const val DUCK_FACTOR = 0.2f
        private const val AMBIENT_SCALE_MIN = 0.20f
        private const val INTRO_SCALE_MIN = 0.20f
        /** Fade Fast (1) → 1s; mid (0.5) → 2s; Gentle (0) → 4s. */
        private const val INTRO_RAMP_MS_MIN = 1_000f
        private const val INTRO_RAMP_MS_MID = 2_000f
        private const val INTRO_RAMP_MS_MAX = 4_000f
        private const val INTRO_TICK_MS = 50L

        fun mediaItemFor(sound: MaskingSoundId): MediaItem =
            MediaItem.Builder()
                .setMediaId(sound.name)
                .setUri("noiseshield://${sound.name}")
                .build()
    }
}
