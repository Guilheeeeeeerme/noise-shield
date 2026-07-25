package com.noiseshield.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier.modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.noiseshield.app.data.AppLanguage
import com.noiseshield.app.ui.onboarding.OnboardingScreen
import com.noiseshield.app.ui.session.SessionScreen
import com.noiseshield.app.ui.session.SessionUiState
import com.noiseshield.app.ui.session.SessionViewModel
import com.noiseshield.app.ui.settings.SettingsScreen
import com.noiseshield.app.ui.theme.NoiseShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SessionViewModel by viewModels {
        SessionViewModel.factory(application as NoiseShieldApp)
    }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshMicPermission()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* foreground service notification best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            NoiseShieldTheme(themeMode = state.prefs.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        state = state,
                        viewModel = viewModel,
                        onRequestMic = {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onLanguage = { lang ->
                            viewModel.setLanguage(lang)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(lang.tag),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshMicPermission()
    }
}

@Composable
private fun AppRoot(
    state: SessionUiState,
    viewModel: SessionViewModel,
    onRequestMic: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    var showOnboarding by rememberSaveable {
        mutableStateOf(false)
    }
    val needOnboarding = !state.prefs.onboardingDone || showOnboarding

    if (needOnboarding) {
        OnboardingScreen(
            onFinished = {
                showOnboarding = false
                viewModel.completeOnboarding()
            },
        )
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "session") {
        composable("session") {
            SessionScreen(
                state = state,
                onTogglePlay = viewModel::togglePlay,
                onSelectSound = { viewModel.selectSound(it, manual = true) },
                onVolume = viewModel::setVolume,
                onTimer = viewModel::setTimerMinutes,
                onToggleFavorite = viewModel::toggleFavorite,
                onSettings = { navController.navigate("settings") },
                onFeedback = viewModel::submitFeedback,
                onDismissFeedback = viewModel::dismissFeedback,
                onRequestMic = onRequestMic,
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = state.prefs,
                onBack = { navController.popBackStack() },
                onTheme = viewModel::setTheme,
                onLanguage = onLanguage,
                onShowOnboarding = { showOnboarding = true },
            )
        }
    }
}
