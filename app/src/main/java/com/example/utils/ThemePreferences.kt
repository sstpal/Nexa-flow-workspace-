package com.example.utils

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.example.ui.theme.AppThemeMode

object ThemePreferences {
    private const val PREFS_NAME = "nexaflow_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    val currentThemeMode = mutableStateOf(AppThemeMode.SYSTEM)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        currentThemeMode.value = try {
            AppThemeMode.valueOf(savedName ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
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
