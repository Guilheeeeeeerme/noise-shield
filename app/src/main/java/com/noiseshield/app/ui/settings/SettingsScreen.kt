package com.noiseshield.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noiseshield.app.R
import com.noiseshield.app.data.AppLanguage
import com.noiseshield.app.data.AppThemeMode
import com.noiseshield.app.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    currentLanguage: AppLanguage,
    onBack: () -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onShowOnboarding: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
        ) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.themeMode == mode,
                        onClick = { onTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentLanguage == AppLanguage.ENGLISH,
                    onClick = { onLanguage(AppLanguage.ENGLISH) },
                    label = { Text(stringResource(R.string.lang_en)) },
                )
                FilterChip(
                    selected = currentLanguage == AppLanguage.PORTUGUESE,
                    onClick = { onLanguage(AppLanguage.PORTUGUESE) },
                    label = { Text(stringResource(R.string.lang_pt)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_onboarding),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowOnboarding)
                    .padding(vertical = 12.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}
