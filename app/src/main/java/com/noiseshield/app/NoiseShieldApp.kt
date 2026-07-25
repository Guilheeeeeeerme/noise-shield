package com.noiseshield.app

import android.app.Application
import com.noiseshield.app.audio.MaskingEngine
import com.noiseshield.app.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NoiseShieldApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var maskingEngine: MaskingEngine
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
        maskingEngine = MaskingEngine(appScope)
        maskingEngine.init()
    }
}
