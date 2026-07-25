package com.noiseshield.app.ui.session

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noiseshield.app.R
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.NoiseLevelBucket

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onTogglePlay: () -> Unit,
    onSelectSound: (MaskingSoundId) -> Unit,
    onVolume: (Float) -> Unit,
    onTimer: (Int?) -> Unit,
    onToggleFavorite: (MaskingSoundId) -> Unit,
    onSettings: () -> Unit,
    onFeedback: (Boolean) -> Unit,
    onDismissFeedback: () -> Unit,
    onRequestMic: () -> Unit,
) {
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
            if (state.limitedMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                        .padding(12.dp),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.limited_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.limited_mode_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onRequestMic) {
                            Text(stringResource(R.string.action_grant_mic))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            NoiseLevelIndicator(
                level = state.estimate?.levelBucket,
                profileLabel = state.estimate?.broadProfile?.name?.lowercase(),
                limited = state.limitedMode,
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (state.playing) stringResource(R.string.session_playing)
                else stringResource(R.string.session_ready),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.label_volume), modifier = Modifier.fillMaxWidth())
            Slider(
                value = state.volume,
                onValueChange = onVolume,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
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
            state.timerRemainingSec?.let { sec ->
                Text(
                    stringResource(R.string.timer_remaining, sec / 60, sec % 60),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
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
                    val fav = sound in state.favorites
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { onSelectSound(sound) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(soundLabel(sound))
                        Icon(
                            imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onToggleFavorite(sound) },
                            tint = if (fav) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showFeedback) {
        AlertDialog(
            onDismissRequest = onDismissFeedback,
            title = { Text(stringResource(R.string.feedback_title)) },
            text = { Text(stringResource(R.string.feedback_body)) },
            confirmButton = {
                TextButton(onClick = { onFeedback(true) }) {
                    Text(stringResource(R.string.feedback_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { onFeedback(false) }) {
                    Text(stringResource(R.string.feedback_no))
                }
            },
        )
    }
}

@Composable
private fun NoiseLevelIndicator(
    level: NoiseLevelBucket?,
    profileLabel: String?,
    limited: Boolean,
) {
    val fill = when (level) {
        NoiseLevelBucket.LOW -> 0.25f
        NoiseLevelBucket.MEDIUM -> 0.55f
        NoiseLevelBucket.HIGH -> 0.9f
        null -> 0.1f
    }
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
                    .size((20 + (80 * fill)).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + fill * 0.4f)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (limited) {
                stringResource(R.string.analysis_unavailable)
            } else {
                stringResource(
                    R.string.analysis_status,
                    level?.name?.lowercase() ?: "—",
                    profileLabel ?: "—",
                )
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

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
