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

class MaskingPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: NativeMaskingPlayer
    private lateinit var session: MediaSession
    private var timerDeadlineElapsedRealtime: Long? = null
    private var timerRemainingWhenPausedMs: Long? = null
    private var adaptiveModeEnabled = true
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
    private val stopPausedService = Runnable {
        if (!player.playWhenReady) stopSelf()
    }

    private val timerTick = object : Runnable {
        override fun run() {
            val deadline = timerDeadlineElapsedRealtime ?: return
            if (!player.isPlaying) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                timerDeadlineElapsedRealtime = null
                player.pause()
                player.stopCapture()
                broadcastTimer()
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
                    if (uiForeground) player.startCapture()
                } else {
                    player.stopCapture()
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
                val args = Bundle().apply {
                    putFloat(ARG_RELATIVE_DBFS, analysis.relativeDbfs)
                    putInt(ARG_LEVEL_BUCKET, analysis.levelBucket.ordinal)
                    putString(ARG_SOUND_ID, analysis.suggestedSoundId.name)
                    putFloat(ARG_CONFIDENCE, analysis.confidence)
                    putFloatArray(ARG_MEL_ENERGIES, analysis.melBandEnergies.toFloatArray())
                    putLong(ARG_CAPTURED_AT, analysis.capturedAtElapsedRealtime)
                }
                handler.post {
                    latestAnalysis = analysis
                    maybeAdapt(analysis)
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
                .add(SessionCommand(COMMAND_SET_ADAPTIVE_MODE, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SET_UI_FOREGROUND, Bundle.EMPTY))
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
                COMMAND_SET_ADAPTIVE_MODE -> {
                    adaptiveModeEnabled = args.getBoolean(ARG_ENABLED, true)
                }
                COMMAND_SET_UI_FOREGROUND -> {
                    uiForeground = args.getBoolean(ARG_ENABLED, false)
                    if (uiForeground && player.playWhenReady) player.startCapture()
                    else player.stopCapture()
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

    private fun broadcastTimer() {
        val args = Bundle().apply {
            putLong(ARG_TIMER_DEADLINE, timerDeadlineElapsedRealtime ?: 0L)
            putLong(ARG_TIMER_REMAINING, timerRemainingWhenPausedMs ?: 0L)
        }
        session.broadcastCustomCommand(SessionCommand(COMMAND_TIMER_EVENT, Bundle.EMPTY), args)
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
        val now = SystemClock.elapsedRealtime()
        val suggested = analysis.suggestedSoundId
        val improvement = maskingScore(suggested, analysis.melBandEnergies) -
            maskingScore(player.currentSound, analysis.melBandEnergies)
        if (!adaptiveModeEnabled || analysis.confidence < MIN_CONFIDENCE ||
            improvement < REQUIRED_SCORE_IMPROVEMENT || suggested == player.currentSound ||
            now < adaptiveCooldownUntil) return
        if (candidateSound == suggested) candidateUpdates++ else {
            candidateSound = suggested
            candidateUpdates = 1
        }
        if (candidateUpdates < REQUIRED_STABLE_UPDATES) return
        player.setSound(suggested)
        adaptiveCooldownUntil = now + ADAPTIVE_COOLDOWN_MS
        candidateSound = null
        candidateUpdates = 0
    }

    private fun maskingScore(sound: MaskingSoundId, spectrum: List<Float>): Float {
        if (spectrum.size != MEL_BANDS) return Float.NEGATIVE_INFINITY
        val weights = FloatArray(MEL_BANDS) { band ->
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
        val total = weights.sum().coerceAtLeast(0.0001f)
        return -spectrum.indices.sumOf {
            abs(spectrum[it] - weights[it] / total).toDouble()
        }.toFloat()
    }

    companion object {
        const val COMMAND_SET_TIMER = "com.noiseshield.app.SET_TIMER"
        const val COMMAND_SET_ADAPTIVE_MODE = "com.noiseshield.app.SET_ADAPTIVE_MODE"
        const val COMMAND_SET_UI_FOREGROUND = "com.noiseshield.app.SET_UI_FOREGROUND"
        const val COMMAND_ANALYSIS_EVENT = "com.noiseshield.app.ANALYSIS_EVENT"
        const val COMMAND_TIMER_EVENT = "com.noiseshield.app.TIMER_EVENT"
        const val COMMAND_BREAK_REMINDER = "com.noiseshield.app.BREAK_REMINDER"
        const val COMMAND_GET_AUDIO_METRICS = "com.noiseshield.app.GET_AUDIO_METRICS"
        const val ARG_DURATION_MS = "duration_ms"
        const val ARG_ENABLED = "enabled"
        const val ARG_RELATIVE_DBFS = "relative_dbfs"
        const val ARG_LEVEL_BUCKET = "level_bucket"
        const val ARG_SOUND_ID = "sound_id"
        const val ARG_CONFIDENCE = "confidence"
        const val ARG_MEL_ENERGIES = "mel_energies"
        const val ARG_CAPTURED_AT = "captured_at"
        const val ARG_TIMER_DEADLINE = "timer_deadline"
        const val ARG_TIMER_REMAINING = "timer_remaining"
        const val ARG_XRUN_COUNT = "xrun_count"
        private const val TIMER_TICK_MS = 1_000L
        private const val BREAK_REMINDER_TICK_MS = 60_000L
        private const val BREAK_REMINDER_DELAY_MS = 60 * 60 * 1_000L
        private const val PAUSED_SERVICE_STOP_DELAY_MS = 30_000L
        private const val MEL_BANDS = 24
        private const val MIN_CONFIDENCE = 0.03f
        private const val REQUIRED_SCORE_IMPROVEMENT = 0.10f
        private const val REQUIRED_STABLE_UPDATES = 3
        private const val ADAPTIVE_COOLDOWN_MS = 15_000L
        private const val MANUAL_OVERRIDE_DISTANCE = 0.25f
        private const val MANUAL_OVERRIDE_UPDATES = 5
    }
}
