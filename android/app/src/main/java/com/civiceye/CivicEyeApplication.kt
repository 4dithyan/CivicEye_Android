package com.civiceye

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CivicEyeApplication : Application() {
    
    companion object {
        lateinit var instance: CivicEyeApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize helpers
        com.civiceye.util.LocationHelper.initialize(this)
    }
}
