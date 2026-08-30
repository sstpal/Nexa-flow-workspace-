#!/bin/bash
cat << 'INNER_EOF' > /tmp/ThemePrefsExt.kt
    private const val KEY_CLEANUP_SCHEDULE = "cleanup_schedule_days"
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
    }

    fun setCleanupSchedule(context: Context, days: Int) {
        cleanupScheduleDays.intValue = days
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CLEANUP_SCHEDULE, days).apply()
    }
INNER_EOF
