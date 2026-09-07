package com.macroresearch.ui.settings

import java.util.Locale

// Native names stay recognizable regardless of the currently selected language.
enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"),
    SimplifiedChinese("zh-CN", "简体中文"),
    TraditionalChinese("zh-TW", "繁體中文");

    companion object {
        fun fromLocale(locale: Locale): AppLanguage = when {
            locale.language != "zh" -> English
            locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> TraditionalChinese
            else -> SimplifiedChinese
        }
    }
}
