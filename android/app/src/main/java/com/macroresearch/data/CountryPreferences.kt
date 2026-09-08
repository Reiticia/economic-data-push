package com.macroresearch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CountryPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _selectedCountries = MutableStateFlow(loadCountries())
    val selectedCountries: StateFlow<Set<String>> = _selectedCountries.asStateFlow()

    fun setCountries(countries: Set<String>) {
        val supported = countries.intersect(SUPPORTED_COUNTRIES.toSet())
        preferences.edit().putStringSet(SELECTED_COUNTRIES_KEY, supported).apply()
        _selectedCountries.value = supported
    }

    fun setCountryEnabled(country: String, enabled: Boolean) {
        if (country !in SUPPORTED_COUNTRIES) return
        val updated = if (enabled) {
            _selectedCountries.value + country
        } else {
            _selectedCountries.value - country
        }
        setCountries(updated)
    }

    private fun loadCountries(): Set<String> {
        if (!preferences.contains(SELECTED_COUNTRIES_KEY)) return DEFAULT_COUNTRIES
        return preferences.getStringSet(SELECTED_COUNTRIES_KEY, emptySet())
            .orEmpty()
            .intersect(SUPPORTED_COUNTRIES.toSet())
    }

    companion object {
        val SUPPORTED_COUNTRIES = listOf(
            "United States",
            "Euro Area",
            "China",
            "Japan",
            "United Kingdom",
        )
        val DEFAULT_COUNTRIES = setOf("United States", "Euro Area", "China", "Japan")

        private const val PREFERENCES_NAME = "research_preferences"
        private const val SELECTED_COUNTRIES_KEY = "selected_countries"
    }
}
