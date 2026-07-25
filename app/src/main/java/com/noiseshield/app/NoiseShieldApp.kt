package com.noiseshield.app

import android.app.Application
import com.noiseshield.app.data.PreferencesRepository

class NoiseShieldApp : Application() {
    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
    }
}
