package com.example.utils

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.example.ui.theme.AppThemeMode

object ThemePreferences {
    private const val PREFS_NAME = "nexaflow_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_CLEANUP_SCHEDULE = "cleanup_schedule_days"
    private const val KEY_LAST_CLEANUP = "last_cleanup_time"

    val currentThemeMode = mutableStateOf(AppThemeMode.SYSTEM)
    val cleanupScheduleDays = androidx.compose.runtime.mutableIntStateOf(0)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        currentThemeMode.value = try {
            AppThemeMode.valueOf(savedName ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
        cleanupScheduleDays.intValue = prefs.getInt(KEY_CLEANUP_SCHEDULE, 0)
        
        // Auto-cleanup logic
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
        val days = cleanupScheduleDays.intValue
        if (days > 0) {
            val millisToWait = days * 24 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - lastCleanup > millisToWait) {
                try {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    prefs.edit().putLong(KEY_LAST_CLEANUP, System.currentTimeMillis()).apply()
                } catch(e: Exception) {}
            }
        }
    }

    fun setCleanupSchedule(context: Context, days: Int) {
        cleanupScheduleDays.intValue = days
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CLEANUP_SCHEDULE, days).apply()
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        currentThemeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun toggleTheme(context: Context) {
        val nextMode = when (currentThemeMode.value) {
            AppThemeMode.SYSTEM -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
        }
        setThemeMode(context, nextMode)
    }
}
