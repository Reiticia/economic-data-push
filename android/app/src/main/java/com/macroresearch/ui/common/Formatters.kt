package com.macroresearch.ui.common

import com.macroresearch.data.model.EconomicEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)

fun EconomicEvent.localTime(): String = runCatching {
    Instant.parse(eventTime).atZone(ZoneId.systemDefault()).format(timeFormatter)
}.getOrDefault("--:--")

fun EconomicEvent.localDate(): String = runCatching {
    Instant.parse(eventTime).atZone(ZoneId.systemDefault()).format(dateFormatter)
}.getOrDefault(eventTime)

fun EconomicEvent.value(value: String?): String {
    if (value == null) return "--"
    val number = value.toBigDecimalOrNull() ?: return value
    return when (unit) {
        "%" -> "${number.stripTrailingZeros().toPlainString()}%"
        "count" -> compact(number)
        "currency" -> "$${compact(number)}"
        else -> number.stripTrailingZeros().toPlainString()
    }
}

fun EconomicEvent.surprise(): BigDecimal? {
    val actualValue = actual?.toBigDecimalOrNull() ?: return null
    val consensusValue = consensus?.toBigDecimalOrNull() ?: return null
    return actualValue - consensusValue
}

fun signed(value: BigDecimal, suffix: String = ""): String {
    val sign = if (value.signum() > 0) "+" else ""
    return "$sign${value.stripTrailingZeros().toPlainString()}$suffix"
}

fun countdown(eventTime: String, now: Instant = Instant.now()): String {
    val target = runCatching { Instant.parse(eventTime) }.getOrNull() ?: return "--:--:--"
    val seconds = Duration.between(now, target).seconds
    if (seconds <= 0) return "已公布"
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remainder)
}

fun flag(country: String): String = when (country.lowercase()) {
    "united states", "us", "usa" -> "🇺🇸"
    "euro area", "european union" -> "🇪🇺"
    "china" -> "🇨🇳"
    "japan" -> "🇯🇵"
    "united kingdom", "uk" -> "🇬🇧"
    "australia" -> "🇦🇺"
    "canada" -> "🇨🇦"
    else -> "🌐"
}

fun formatChange(value: Double?, unit: String): String {
    if (value == null) return "--"
    val suffix = if (unit == "basis_points") "bp" else "%"
    return "%+.2f%s".format(value, suffix)
}

private fun compact(number: BigDecimal): String {
    val absolute = number.abs()
    val (divisor, suffix) = when {
        absolute >= BigDecimal("1000000000000") -> BigDecimal("1000000000000") to "T"
        absolute >= BigDecimal("1000000000") -> BigDecimal("1000000000") to "B"
        absolute >= BigDecimal("1000000") -> BigDecimal("1000000") to "M"
        absolute >= BigDecimal("1000") -> BigDecimal("1000") to "K"
        else -> return number.stripTrailingZeros().toPlainString()
    }
    return number.divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix
}

