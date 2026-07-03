package com.example

import android.content.Context
import android.content.SharedPreferences

class WatchdogManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("BotPrefs", Context.MODE_PRIVATE)
    
    var isEnabled: Boolean
        get() = prefs.getBoolean("watchdog_enabled", false)
        set(value) = prefs.edit().putBoolean("watchdog_enabled", value).apply()
        
    var restartCount: Int
        get() = prefs.getInt("watchdog_restarts", 0)
        set(value) = prefs.edit().putInt("watchdog_restarts", value).apply()
        
    fun incrementRestartCount() {
        restartCount += 1
    }
    
    fun resetCounter() {
        restartCount = 0
    }
    
    val maxRestarts = 5
}
