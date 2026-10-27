package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

object ThemeManager {
    private const val PREFS_NAME = "remotelink_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private var prefs: SharedPreferences? = null

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(false)
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            val savedMode = sp.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
            _themeMode.value = try {
                AppThemeMode.valueOf(savedMode)
            } catch (_: Exception) {
                AppThemeMode.DARK
            }
            _isDynamicColor.value = sp.getBoolean(KEY_DYNAMIC_COLOR, false)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _isDynamicColor.value = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    fun cycleThemeMode() {
        val next = when (_themeMode.value) {
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
            AppThemeMode.SYSTEM -> AppThemeMode.DARK
        }
        setThemeMode(next)
    }
}
