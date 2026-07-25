package com.noiseshield.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.noiseshield.app.R
import com.noiseshield.app.data.AppLanguage
import com.noiseshield.app.data.AppThemeMode
import com.noiseshield.app.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    currentLanguage: AppLanguage,
    onBack: () -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onShowOnboarding: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = currentLanguage == language,
                        onClick = { onLanguage(language) },
                        label = {
                            Text(
                                when (language) {
                                    AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
                                    AppLanguage.ENGLISH -> stringResource(R.string.lang_en)
                                    AppLanguage.PORTUGUESE -> stringResource(R.string.lang_pt)
                                    AppLanguage.SPANISH -> stringResource(R.string.lang_es)
                                    AppLanguage.CHINESE_SIMPLIFIED -> stringResource(R.string.lang_zh)
                                    AppLanguage.FRENCH -> stringResource(R.string.lang_fr)
                                },
                            )
                        },
                    )
                }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.support_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.support_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(SUPPORT_EMAIL)) },
                    ) {
                        Text(stringResource(R.string.support_copy_pix, SUPPORT_EMAIL))
                    }
                    TextButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:$SUPPORT_EMAIL"),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                    ) {
                        Text(stringResource(R.string.support_contact, SUPPORT_EMAIL))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

private const val SUPPORT_EMAIL = "ferreiraga@outlook.com"
