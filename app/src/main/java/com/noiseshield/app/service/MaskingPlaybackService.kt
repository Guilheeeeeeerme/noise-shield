package com.noiseshield.app.service

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.noiseshield.app.data.CoverState
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.NoiseAnalysis
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

class MaskingPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: NativeMaskingPlayer
    private lateinit var session: MediaSession
    private var timerDeadlineElapsedRealtime: Long? = null
    private var timerRemainingWhenPausedMs: Long? = null
    private var adaptiveModeEnabled = true
    private var adaptiveSwitching = 0.5f
    private var adaptiveFade = 0.5f
    private var uiForeground = false
    private var latestAnalysis: NoiseAnalysis? = null
    private var manualOverride = false
    private var manualBaseline: List<Float>? = null
    private var manualShiftUpdates = 0
    private var candidateSound: MaskingSoundId? = null
    private var candidateUpdates = 0
    private var adaptiveCooldownUntil = 0L
    private var breakReminderDeadline = 0L
    private var breakReminderSent = false
    private var smoothedAmbientScale = 1f
    private var hasAmbientScale = false
    private var coveredLatched = false
    private var ambientTargetScale = 1f
    private var preferredInputDeviceId = 0
    private var preferredOutputDeviceId = 0
    private val stopPausedService = Runnable {
        if (!player.playWhenReady) stopSelf()
    }
    private val envelopeTick = object : Runnable {
        override fun run() {
            if (!hasAmbientScale || !player.playWhenReady) return
            val target = ambientTargetScale
            val alpha = if (target > smoothedAmbientScale) ATTACK_ALPHA else RELEASE_ALPHA
            smoothedAmbientScale += alpha * (target - smoothedAmbientScale)
            player.setAmbientScale(smoothedAmbientScale)
            handler.postDelayed(this, ENVELOPE_TICK_MS)
        }
    }

    private val timerTick = object : Runnable {
        override fun run() {
            val deadline = timerDeadlineElapsedRealtime ?: return
            if (!player.isPlaying) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                timerDeadlineElapsedRealtime = null
                player.pause()
                player.stopCapture()
                broadcastTimer(completed = true)
                return
            }
            broadcastTimer()
            handler.postDelayed(this, TIMER_TICK_MS)
        }
    }
    private val breakReminderTick = object : Runnable {
        override fun run() {
            if (!player.isPlaying) return
            if (!breakReminderSent && SystemClock.elapsedRealtime() >= breakReminderDeadline) {
                breakReminderSent = true
                session.broadcastCustomCommand(
                    SessionCommand(COMMAND_BREAK_REMINDER, Bundle.EMPTY),
                    Bundle.EMPTY,
                )
                return
            }
            handler.postDelayed(this, BREAK_REMINDER_TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = NativeMaskingPlayer(this, serviceScope)
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                handler.removeCallbacks(stopPausedService)
                if (playWhenReady) {
                    player.setSoftStartFade(effectiveFade())
                    if (uiForeground) player.startCapture()
                } else {
                    player.stopCapture()
                    resetAmbientIntensity()
                    handler.postDelayed(stopPausedService, PAUSED_SERVICE_STOP_DELAY_MS)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(timerTick)
                handler.removeCallbacks(breakReminderTick)
                if (isPlaying) {
                    timerRemainingWhenPausedMs?.let {
                        timerDeadlineElapsedRealtime = SystemClock.elapsedRealtime() + it
                        timerRemainingWhenPausedMs = null
                    }
                    if (timerDeadlineElapsedRealtime != null) handler.post(timerTick)
                    breakReminderDeadline = SystemClock.elapsedRealtime() + BREAK_REMINDER_DELAY_MS
                    breakReminderSent = false
                    handler.post(breakReminderTick)
                } else {
                    timerDeadlineElapsedRealtime?.let {
                        timerRemainingWhenPausedMs =
                            (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    }
                    timerDeadlineElapsedRealtime = null
                    broadcastTimer()
                    breakReminderDeadline = 0L
                    breakReminderSent = false
                }
            }
        })
        serviceScope.launch {
            player.estimate.collect { analysis ->
                if (analysis == null) return@collect
                handler.post {
                    if (!uiForeground || !player.playWhenReady) return@post
                    latestAnalysis = analysis
                    val scored = scoreResidual(analysis, player.currentSound)
                    latestAnalysis = scored
                    val intensity = applyAmbientIntensity(scored)
                    maybeAdapt(scored)
                    val args = Bundle().apply {
                        putFloat(ARG_RELATIVE_DBFS, scored.relativeDbfs)
                        putInt(ARG_LEVEL_BUCKET, scored.levelBucket.ordinal)
                        putString(ARG_SOUND_ID, scored.suggestedSoundId.name)
                        putFloat(ARG_CONFIDENCE, scored.confidence)
                        putFloatArray(ARG_MEL_ENERGIES, scored.melBandEnergies.toFloatArray())
                        putLong(ARG_CAPTURED_AT, scored.capturedAtElapsedRealtime)
                        putFloat(ARG_MASK_INTENSITY, intensity)
                        putFloat(ARG_SELF_MATCH, scored.selfMatch)
                        putFloat(ARG_RESIDUAL_DBFS, scored.residualDbfs)
                        putInt(ARG_COVER_STATE, scored.coverState.ordinal)
                    }
                    session.broadcastCustomCommand(
                        SessionCommand(COMMAND_ANALYSIS_EVENT, Bundle.EMPTY),
                        args,
                    )
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        player.stopCapture()
        uiForeground = false
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTick)
        handler.removeCallbacks(breakReminderTick)
        handler.removeCallbacks(envelopeTick)
        handler.removeCallbacksAndMessages(null)
        player.stopCapture()
        session.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = SessionCommands.Builder()
                .add(SessionCommand(COMMAND_SET_TIMER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_ADAPTIVE_PARAMS, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_UI_FOREGROUND, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_AUDIO_DEVICES, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_ANALYSIS_EVENT, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_TIMER_EVENT, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_BREAK_REMINDER, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_GET_AUDIO_METRICS, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SET_TIMER -> {
                    val durationMs = args.getLong(ARG_DURATION_MS, 0L)
                    timerDeadlineElapsedRealtime = if (durationMs > 0L && player.isPlaying) {
                        SystemClock.elapsedRealtime() + durationMs
                    } else null
                    timerRemainingWhenPausedMs =
                        if (durationMs > 0L && !player.isPlaying) durationMs else null
                    handler.removeCallbacks(timerTick)
                    if (timerDeadlineElapsedRealtime != null && player.isPlaying) {
                        handler.post(timerTick)
                    }
                    broadcastTimer()
                }
                COMMAND_SET_ADAPTIVE_PARAMS -> {
                    adaptiveModeEnabled = args.getBoolean(ARG_ENABLED, true)
                    adaptiveSwitching = args.getFloat(ARG_SWITCHING, 0.5f).coerceIn(0f, 1f)
                    adaptiveFade = args.getFloat(ARG_FADE, 0.5f).coerceIn(0f, 1f)
                    player.setSoftStartFade(effectiveFade())
                }
                COMMAND_SET_UI_FOREGROUND -> {
                    uiForeground = args.getBoolean(ARG_ENABLED, false)
                    if (uiForeground && player.playWhenReady) {
                        player.startCapture()
                    } else {
                        player.stopCapture()
                        resetAmbientIntensity()
                    }
                }
                COMMAND_SET_AUDIO_DEVICES -> {
                    preferredInputDeviceId = args.getInt(ARG_INPUT_DEVICE_ID, 0)
                    preferredOutputDeviceId = args.getInt(ARG_OUTPUT_DEVICE_ID, 0)
                    player.setPreferredDevices(preferredInputDeviceId, preferredOutputDeviceId)
                }
                COMMAND_GET_AUDIO_METRICS -> {
                    val metrics = Bundle().apply { putInt(ARG_XRUN_COUNT, player.xRunCount()) }
                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS, metrics),
                    )
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            @Player.Command playerCommand: Int,
        ): Int {
            if (playerCommand == Player.COMMAND_SET_MEDIA_ITEM && player.playWhenReady) {
                manualOverride = true
                manualBaseline = latestAnalysis?.melBandEnergies
                manualShiftUpdates = 0
            }
            return SessionResult.RESULT_SUCCESS
        }
    }

    private fun broadcastTimer(completed: Boolean = false) {
        val args = Bundle().apply {
            putLong(ARG_TIMER_DEADLINE, timerDeadlineElapsedRealtime ?: 0L)
            putLong(ARG_TIMER_REMAINING, timerRemainingWhenPausedMs ?: 0L)
            putBoolean(ARG_TIMER_COMPLETED, completed)
        }
        session.broadcastCustomCommand(SessionCommand(COMMAND_TIMER_EVENT, Bundle.EMPTY), args)
    }

    /**
     * Maps residual (non-mask) dBFS to mask intensity under the user volume ceiling.
     * Fast attack / slow release via a 250 ms envelope tick; Covered holds against raising.
     */
    private fun applyAmbientIntensity(analysis: NoiseAnalysis): Float {
        var target = dbfsToAmbientScale(analysis.residualDbfs)
        if (analysis.coverState == CoverState.COVERED || coveredLatched) {
            coveredLatched = analysis.selfMatch >= COVERED_EXIT_SELF_MATCH &&
                analysis.residualDbfs <= COVERED_EXIT_RESIDUAL_DBFS
            if (coveredLatched) {
                target = min(target, smoothedAmbientScale)
            }
        }
        ambientTargetScale = target
        if (!hasAmbientScale) {
            // Begin from the currently audible level; never snap to near-silence.
            smoothedAmbientScale = 1f
            hasAmbientScale = true
            player.setAmbientScale(smoothedAmbientScale)
            handler.removeCallbacks(envelopeTick)
            handler.post(envelopeTick)
        }
        return player.maskIntensity
    }

    private fun resetAmbientIntensity() {
        handler.removeCallbacks(envelopeTick)
        hasAmbientScale = false
        smoothedAmbientScale = 1f
        ambientTargetScale = 1f
        coveredLatched = false
        player.resetAmbientScale()
    }

    private fun scoreResidual(analysis: NoiseAnalysis, currentSound: MaskingSoundId): NoiseAnalysis {
        val match = selfMatch(currentSound, analysis.melBandEnergies)
        val residualFraction = (1f - match).coerceIn(0.01f, 1f)
        val residualDbfs = analysis.relativeDbfs + 20f * log10(residualFraction.toDouble()).toFloat()
        val coverState = when {
            match >= COVERED_ENTER_SELF_MATCH &&
                residualDbfs <= COVERED_ENTER_RESIDUAL_DBFS -> CoverState.COVERED
            residualDbfs > AMBIENT_DBFS_FLOOR + 5f -> CoverState.MASKING_EXTERNAL
            else -> CoverState.LISTENING
        }
        if (coverState == CoverState.COVERED) coveredLatched = true
        return analysis.copy(
            selfMatch = match,
            residualDbfs = residualDbfs,
            coverState = if (coveredLatched && match >= COVERED_EXIT_SELF_MATCH) {
                CoverState.COVERED
            } else {
                coverState
            },
        )
    }

    private fun selfMatch(sound: MaskingSoundId, spectrum: List<Float>): Float {
        if (spectrum.size != MEL_BANDS) return 0f
        val weights = maskWeights(sound)
        val total = weights.sum().coerceAtLeast(0.0001f)
        val distance = spectrum.indices.sumOf {
            abs(spectrum[it] - weights[it] / total).toDouble()
        }.toFloat()
        return (1f - distance / 2f).coerceIn(0f, 1f)
    }

    private fun maybeAdapt(analysis: NoiseAnalysis) {
        if (!player.isPlaying) return
        if (manualOverride) {
            val baseline = manualBaseline ?: analysis.melBandEnergies.also { manualBaseline = it }
            val distance = baseline.zip(analysis.melBandEnergies)
                .sumOf { (before, after) -> abs(before - after).toDouble() }.toFloat()
            manualShiftUpdates = if (distance > MANUAL_OVERRIDE_DISTANCE) {
                manualShiftUpdates + 1
            } else 0
            if (manualShiftUpdates < MANUAL_OVERRIDE_UPDATES) return
            manualOverride = false
            manualBaseline = null
            manualShiftUpdates = 0
        }
        if (!adaptiveModeEnabled) return
        val switching = adaptiveSwitching
        val requiredImprovement = scoreImprovementForSwitching(switching)
        val requiredStable = stableUpdatesForSwitching(switching)
        val cooldownMs = cooldownMsForSwitching(switching)
        val now = SystemClock.elapsedRealtime()
        val suggested = analysis.suggestedSoundId
        val improvement = maskingScore(suggested, analysis.melBandEnergies) -
            maskingScore(player.currentSound, analysis.melBandEnergies)
        if (analysis.confidence < MIN_CONFIDENCE ||
            improvement < requiredImprovement || suggested == player.currentSound ||
            now < adaptiveCooldownUntil) return
        if (candidateSound == suggested) candidateUpdates++ else {
            candidateSound = suggested
            candidateUpdates = 1
        }
        if (candidateUpdates < requiredStable) return
        player.setSound(suggested)
        adaptiveCooldownUntil = now + cooldownMs
        candidateSound = null
        candidateUpdates = 0
    }

    private fun effectiveFade(): Float = adaptiveFade

    /** Higher Switching → lower score-improvement gate. 0→0.20, 0.5→0.10, 1→0.05. */
    private fun scoreImprovementForSwitching(switching: Float): Float {
        val t = switching.coerceIn(0f, 1f)
        return if (t <= 0.5f) {
            SCORE_IMPROVEMENT_MAX - (t / 0.5f) * (SCORE_IMPROVEMENT_MAX - SCORE_IMPROVEMENT_MID)
        } else {
            SCORE_IMPROVEMENT_MID - ((t - 0.5f) / 0.5f) * (SCORE_IMPROVEMENT_MID - SCORE_IMPROVEMENT_MIN)
        }
    }

    /** Switching: 0 = Selective, 1 = Eager. */
    private fun stableUpdatesForSwitching(switching: Float): Int {
        val patience = 1f - switching.coerceIn(0f, 1f)
        return (1f + patience * 5f).toInt().coerceIn(STABLE_UPDATES_MIN, STABLE_UPDATES_MAX)
    }

    /** Switching: 0 = Selective, 1 = Eager. */
    private fun cooldownMsForSwitching(switching: Float): Long {
        val patience = 1f - switching.coerceIn(0f, 1f)
        return if (patience <= 0.5f) {
            (COOLDOWN_MS_MIN + (patience / 0.5f) * (COOLDOWN_MS_MID - COOLDOWN_MS_MIN)).toLong()
        } else {
            (COOLDOWN_MS_MID + ((patience - 0.5f) / 0.5f) * (COOLDOWN_MS_MAX - COOLDOWN_MS_MID)).toLong()
        }
    }

    private fun maskingScore(sound: MaskingSoundId, spectrum: List<Float>): Float {
        if (spectrum.size != MEL_BANDS) return Float.NEGATIVE_INFINITY
        val weights = maskWeights(sound)
        val total = weights.sum().coerceAtLeast(0.0001f)
        return -spectrum.indices.sumOf {
            abs(spectrum[it] - weights[it] / total).toDouble()
        }.toFloat()
    }

    private fun maskWeights(sound: MaskingSoundId): FloatArray = FloatArray(MEL_BANDS) { band ->
        val x = band / (MEL_BANDS - 1f)
        when (sound) {
            MaskingSoundId.WHITE_NOISE -> 0.70f + 0.30f * x
            MaskingSoundId.PINK_NOISE -> 1.00f - 0.45f * x
            MaskingSoundId.BROWN_NOISE -> 1.00f - 0.75f * x
            MaskingSoundId.OCEAN_WAVES -> 0.70f + 0.25f * kotlin.math.sin(x * Math.PI).toFloat()
            MaskingSoundId.RAIN -> 0.75f + 0.20f * x
            MaskingSoundId.FAN -> 0.95f - 0.35f * x
            MaskingSoundId.AIR_CONDITIONER -> 1.00f - 0.55f * x
            MaskingSoundId.CAFE_AMBIENCE -> 0.70f + 0.30f * kotlin.math.sin(x * Math.PI).toFloat()
        }
    }

    companion object {
        const val COMMAND_SET_TIMER = "com.noiseshield.app.SET_TIMER"
        const val COMMAND_SET_ADAPTIVE_PARAMS = "com.noiseshield.app.SET_ADAPTIVE_PARAMS"
        const val COMMAND_SET_UI_FOREGROUND = "com.noiseshield.app.SET_UI_FOREGROUND"
        const val COMMAND_SET_AUDIO_DEVICES = "com.noiseshield.app.SET_AUDIO_DEVICES"
        const val COMMAND_ANALYSIS_EVENT = "com.noiseshield.app.ANALYSIS_EVENT"
        const val COMMAND_TIMER_EVENT = "com.noiseshield.app.TIMER_EVENT"
        const val COMMAND_BREAK_REMINDER = "com.noiseshield.app.BREAK_REMINDER"
        const val COMMAND_GET_AUDIO_METRICS = "com.noiseshield.app.GET_AUDIO_METRICS"
        const val ARG_DURATION_MS = "duration_ms"
        const val ARG_ENABLED = "enabled"
        const val ARG_SWITCHING = "switching"
        const val ARG_FADE = "fade"
        const val ARG_RELATIVE_DBFS = "relative_dbfs"
        const val ARG_LEVEL_BUCKET = "level_bucket"
        const val ARG_SOUND_ID = "sound_id"
        const val ARG_CONFIDENCE = "confidence"
        const val ARG_MEL_ENERGIES = "mel_energies"
        const val ARG_CAPTURED_AT = "captured_at"
        const val ARG_MASK_INTENSITY = "mask_intensity"
        const val ARG_SELF_MATCH = "self_match"
        const val ARG_RESIDUAL_DBFS = "residual_dbfs"
        const val ARG_COVER_STATE = "cover_state"
        const val ARG_INPUT_DEVICE_ID = "input_device_id"
        const val ARG_OUTPUT_DEVICE_ID = "output_device_id"
        const val ARG_TIMER_DEADLINE = "timer_deadline"
        const val ARG_TIMER_REMAINING = "timer_remaining"
        const val ARG_TIMER_COMPLETED = "timer_completed"
        const val ARG_XRUN_COUNT = "xrun_count"
        private const val TIMER_TICK_MS = 1_000L
        private const val BREAK_REMINDER_TICK_MS = 60_000L
        private const val BREAK_REMINDER_DELAY_MS = 60 * 60 * 1_000L
        private const val PAUSED_SERVICE_STOP_DELAY_MS = 30_000L
        private const val MEL_BANDS = 24
        private const val MIN_CONFIDENCE = 0.03f
        private const val SCORE_IMPROVEMENT_MIN = 0.05f
        private const val SCORE_IMPROVEMENT_MID = 0.10f
        private const val SCORE_IMPROVEMENT_MAX = 0.20f
        private const val STABLE_UPDATES_MIN = 1
        private const val STABLE_UPDATES_MAX = 6
        private const val COOLDOWN_MS_MIN = 5_000L
        private const val COOLDOWN_MS_MID = 15_000L
        private const val COOLDOWN_MS_MAX = 45_000L
        private const val MANUAL_OVERRIDE_DISTANCE = 0.25f
        private const val MANUAL_OVERRIDE_UPDATES = 5
        /** 250 ms envelope ticks: ~0.7–1 s attack, ~5–7 s release. */
        private const val ENVELOPE_TICK_MS = 250L
        private const val ATTACK_ALPHA = 0.45f
        private const val RELEASE_ALPHA = 0.10f
        private const val AMBIENT_DBFS_FLOOR = -50f
        private const val AMBIENT_DBFS_CEILING = -25f
        private const val AMBIENT_SCALE_MIN = 0.20f
        private const val COVERED_ENTER_SELF_MATCH = 0.65f
        private const val COVERED_EXIT_SELF_MATCH = 0.55f
        private const val COVERED_ENTER_RESIDUAL_DBFS = -40f
        private const val COVERED_EXIT_RESIDUAL_DBFS = -35f

        fun dbfsToAmbientScale(relativeDbfs: Float): Float {
            if (relativeDbfs <= AMBIENT_DBFS_FLOOR) return AMBIENT_SCALE_MIN
            if (relativeDbfs >= AMBIENT_DBFS_CEILING) return 1f
            val t = (relativeDbfs - AMBIENT_DBFS_FLOOR) /
                (AMBIENT_DBFS_CEILING - AMBIENT_DBFS_FLOOR)
            return AMBIENT_SCALE_MIN + t * (1f - AMBIENT_SCALE_MIN)
        }
    }
}
