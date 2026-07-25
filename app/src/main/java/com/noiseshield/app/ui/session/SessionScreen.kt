package com.noiseshield.app.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noiseshield.app.R
import com.noiseshield.app.data.AudioDevicePreference
import com.noiseshield.app.data.AudioRouteDevice
import com.noiseshield.app.data.CoverState
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.MaskingPreset
import com.noiseshield.app.data.NoiseLevelBucket

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onTogglePlay: () -> Unit,
    onSelectSound: (MaskingSoundId) -> Unit,
    onVolume: (Float) -> Unit,
    onTimer: (Int?) -> Unit,
    onSettings: () -> Unit,
    onRequestMic: () -> Unit,
    onMaskingPreset: (MaskingPreset) -> Unit,
    onInputDevice: (String) -> Unit,
    onOutputDevice: (String) -> Unit,
    onDismissSafetyWarning: () -> Unit,
    onDismissBreakReminder: () -> Unit,
) {
    val sessionLocked = state.playing
    val sessionActive = state.playing || state.audible
    var routesExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                soundLabel(state.sound),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            state.timerRemainingSec?.let { sec ->
                Text(
                    stringResource(R.string.timer_remaining, sec / 60, sec % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        enabled = state.runtimeState != SessionRuntimeState.INITIALIZING,
                        onClick = onTogglePlay,
                    )
                    .alpha(if (state.runtimeState == SessionRuntimeState.INITIALIZING) 0.45f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                if (sessionActive) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = stringResource(R.string.action_pause),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (state.audible) stringResource(R.string.session_playing)
                else stringResource(R.string.session_ready),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    when (state.runtimeState) {
                        SessionRuntimeState.INITIALIZING -> R.string.state_initializing
                        SessionRuntimeState.READY -> R.string.state_ready
                        SessionRuntimeState.STARTING -> R.string.state_starting
                        SessionRuntimeState.AUDIBLE -> R.string.state_audible
                        SessionRuntimeState.FADING -> R.string.state_fading
                        SessionRuntimeState.MUTED_BY_DEVICE -> R.string.state_muted_by_device
                        SessionRuntimeState.TIMER_ENDED -> R.string.state_timer_ended
                        SessionRuntimeState.PERMISSION_REQUIRED -> R.string.state_permission_required
                        SessionRuntimeState.CAPTURING -> R.string.state_capturing
                        SessionRuntimeState.FOCUS_DELAYED -> R.string.state_focus_delayed
                        SessionRuntimeState.RECOVERING -> R.string.state_recovering
                        SessionRuntimeState.ERROR -> R.string.state_error
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            if (state.runtimeState == SessionRuntimeState.MUTED_BY_DEVICE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.media_volume_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.label_volume),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.volume_percent, (state.volume * 100f).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = state.volume,
                onValueChange = onVolume,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.label_sounds),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaskingSoundId.entries.forEach { sound ->
                    val selected = state.sound == sound
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { onSelectSound(sound) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(soundLabel(sound))
                    }
                }
            }
            state.adaptiveSwitchTo?.let { adaptiveSwitchTo ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.adaptive_switching, soundLabel(adaptiveSwitchTo)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.label_timer), modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data class TimerOpt(val minutes: Int?, val label: Int)
                listOf(
                    TimerOpt(null, R.string.timer_off),
                    TimerOpt(15, R.string.timer_15),
                    TimerOpt(30, R.string.timer_30),
                    TimerOpt(60, R.string.timer_60),
                ).forEach { opt ->
                    val selected = if (opt.minutes == null) {
                        state.timerRemainingSec == null
                    } else {
                        val rem = state.timerRemainingSec
                        rem != null && (rem + 59) / 60 == opt.minutes
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onTimer(opt.minutes) },
                        label = { Text(stringResource(opt.label)) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.label_masking_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    if (state.limitedMode) R.string.masking_mode_manual_description
                    else R.string.masking_mode_description,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MaskingPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.prefs.maskingPreset == preset,
                        onClick = { onMaskingPreset(preset) },
                        label = { Text(maskingPresetLabel(preset)) },
                    )
                }
            }
            Text(
                maskingPresetDescription(state.prefs.maskingPreset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            if (state.limitedMode) {
                TextButton(onClick = onRequestMic) {
                    Text(stringResource(R.string.action_grant_mic))
                }
            }

            Spacer(Modifier.height(24.dp))
            if (!state.limitedMode && state.playing) {
                NoiseLevelIndicator(
                    level = state.estimate?.levelBucket,
                    relativeDbfs = state.estimate?.relativeDbfs,
                    maskIntensity = state.maskIntensity,
                    suggestedSound = state.estimate?.suggestedSoundId,
                    coverState = state.coverState,
                    limited = false,
                )
            }

            if (state.inputDevices.size > 1 || state.outputDevices.size > 1) {
                TextButton(onClick = { routesExpanded = !routesExpanded }) {
                    Text(
                        stringResource(
                            if (routesExpanded) R.string.action_hide_audio_devices
                            else R.string.action_show_audio_devices,
                        ),
                    )
                }
                if (sessionLocked) {
                    Text(
                        stringResource(R.string.session_controls_locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (routesExpanded) {
                    AudioRoutePickers(
                        inputDevices = state.inputDevices,
                        outputDevices = state.outputDevices,
                        selectedInputFingerprint = state.selectedInputFingerprint,
                        selectedOutputFingerprint = state.selectedOutputFingerprint,
                        enabled = !sessionLocked,
                        onInputDevice = onInputDevice,
                        onOutputDevice = onOutputDevice,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showSafetyWarning) {
        AlertDialog(
            onDismissRequest = onDismissSafetyWarning,
            title = { Text(stringResource(R.string.safety_warning_title)) },
            text = { Text(stringResource(R.string.safety_warning_body)) },
            confirmButton = {
                TextButton(onClick = onDismissSafetyWarning) {
                    Text(stringResource(R.string.action_understand))
                }
            },
        )
    }
    if (state.showBreakReminder) {
        AlertDialog(
            onDismissRequest = onDismissBreakReminder,
            title = { Text(stringResource(R.string.break_reminder_title)) },
            text = { Text(stringResource(R.string.break_reminder_body)) },
            confirmButton = {
                TextButton(onClick = onDismissBreakReminder) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

@Composable
private fun NoiseLevelIndicator(
    level: NoiseLevelBucket?,
    relativeDbfs: Float?,
    maskIntensity: Float?,
    suggestedSound: MaskingSoundId?,
    coverState: CoverState?,
    limited: Boolean,
) {
    val continuousFill = when {
        limited || relativeDbfs == null -> 0.1f
        else -> dbfsToMeterFill(relativeDbfs)
    }
    val animatedFill by animateFloatAsState(
        targetValue = continuousFill,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 180f),
        label = "noise level",
    )
    val indicatorColor by animateColorAsState(
        targetValue = when (coverState) {
            CoverState.COVERED -> MaterialTheme.colorScheme.tertiary
            CoverState.MASKING_EXTERNAL -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "noise color",
    )
    val pulseTransition = rememberInfiniteTransition(label = "noise pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1_050 - animatedFill * 500).toInt().coerceAtLeast(420),
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "noise pulse scale",
    )
    val intensity = maskIntensity?.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size((48 + (58 * animatedFill)).dp)
                    .border(
                        width = 2.dp,
                        color = indicatorColor.copy(alpha = 0.18f + animatedFill * 0.18f),
                        shape = CircleShape,
                    )
                    .clip(CircleShape),
            )
            Box(
                modifier = Modifier
                    .size((24 + (62 * animatedFill)).dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(CircleShape)
                    .background(
                        indicatorColor.copy(
                            alpha = 0.4f + animatedFill * 0.42f,
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!limited && coverState != null) {
            Text(
                stringResource(
                    when (coverState) {
                        CoverState.LISTENING -> R.string.cover_listening
                        CoverState.MASKING_EXTERNAL -> R.string.cover_masking_external
                        CoverState.COVERED -> R.string.cover_covered
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            if (limited) {
                stringResource(R.string.analysis_unavailable)
            } else {
                stringResource(
                    R.string.analysis_status,
                    level?.let { levelLabel(it) } ?: "—",
                    suggestedSound?.let { soundLabel(it) } ?: "—",
                )
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (!limited && intensity != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.mask_intensity_status,
                    (intensity * 100f).toInt().coerceIn(0, 100),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioRoutePickers(
    inputDevices: List<AudioRouteDevice>,
    outputDevices: List<AudioRouteDevice>,
    selectedInputFingerprint: String,
    selectedOutputFingerprint: String,
    enabled: Boolean,
    onInputDevice: (String) -> Unit,
    onOutputDevice: (String) -> Unit,
) {
    val showInput = inputDevices.size > 1
    val showOutput = outputDevices.size > 1
    if (!showInput && !showOutput) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        if (showInput) {
            Text(
                stringResource(R.string.label_input),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DeviceChipRow(
                devices = inputDevices,
                selectedFingerprint = selectedInputFingerprint,
                autoLabel = stringResource(R.string.device_auto_phone_mic),
                enabled = enabled,
                onSelect = onInputDevice,
            )
        }
        if (showInput && showOutput) Spacer(Modifier.height(16.dp))
        if (showOutput) {
            Text(
                stringResource(R.string.label_output),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DeviceChipRow(
                devices = outputDevices,
                selectedFingerprint = selectedOutputFingerprint,
                autoLabel = stringResource(R.string.device_auto_bt_speaker),
                enabled = enabled,
                onSelect = onOutputDevice,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceChipRow(
    devices: List<AudioRouteDevice>,
    selectedFingerprint: String,
    autoLabel: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val autoSelected = selectedFingerprint == AudioDevicePreference.FINGERPRINT_AUTO
    val matchedFingerprint = when {
        autoSelected -> null
        else -> devices.firstOrNull { it.fingerprint == selectedFingerprint }?.fingerprint
            ?: devices.firstOrNull { device ->
                val type = selectedFingerprint.substringBefore('|').toIntOrNull()
                type != null && device.type == type
            }?.fingerprint
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilterChip(
            selected = autoSelected || matchedFingerprint == null,
            onClick = { onSelect(AudioDevicePreference.FINGERPRINT_AUTO) },
            enabled = enabled,
            label = { Text(autoLabel) },
        )
        devices.forEach { device ->
            FilterChip(
                selected = matchedFingerprint == device.fingerprint,
                onClick = { onSelect(device.fingerprint) },
                enabled = enabled,
                label = { Text(device.name) },
            )
        }
    }
}

private fun dbfsToMeterFill(relativeDbfs: Float): Float {
    // Map roughly -60..-10 dBFS to 0.1..1.0 for the meter disc.
    val t = ((relativeDbfs + 60f) / 50f).coerceIn(0f, 1f)
    return 0.1f + t * 0.9f
}

@Composable
private fun maskingPresetLabel(preset: MaskingPreset): String = stringResource(
    when (preset) {
        MaskingPreset.NORMAL -> R.string.mode_normal
        MaskingPreset.SLEEP -> R.string.mode_sleep
        MaskingPreset.FOCUS -> R.string.mode_focus
        MaskingPreset.OFFICE -> R.string.mode_office
        MaskingPreset.TRAVEL -> R.string.mode_travel
    },
)

@Composable
private fun maskingPresetDescription(preset: MaskingPreset): String = stringResource(
    when (preset) {
        MaskingPreset.NORMAL -> R.string.mode_normal_description
        MaskingPreset.SLEEP -> R.string.mode_sleep_description
        MaskingPreset.FOCUS -> R.string.mode_focus_description
        MaskingPreset.OFFICE -> R.string.mode_office_description
        MaskingPreset.TRAVEL -> R.string.mode_travel_description
    },
)

@Composable
private fun soundLabel(sound: MaskingSoundId): String = stringResource(
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
)

@Composable
private fun levelLabel(level: NoiseLevelBucket): String = stringResource(
    when (level) {
        NoiseLevelBucket.LOW -> R.string.level_low
        NoiseLevelBucket.MEDIUM -> R.string.level_medium
        NoiseLevelBucket.HIGH -> R.string.level_high
    },
)
