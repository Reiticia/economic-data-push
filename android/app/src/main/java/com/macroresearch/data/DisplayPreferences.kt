package com.macroresearch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-only density preference. Never changes the device density or accessibility font scale. */
enum class DisplayMode {
    AUTO, COMPACT, SYSTEM;

    companion object {
        fun fromStored(value: String?): DisplayMode = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

class DisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ui_display", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(DisplayMode.fromStored(preferences.getString("mode", null)))
    val mode = _mode.asStateFlow()

    fun setMode(mode: DisplayMode) {
        preferences.edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }
}
