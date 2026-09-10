package com.macroresearch.data.remote

import com.macroresearch.data.model.EconomicEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class TradingEconomicsClient(private val client: OkHttpClient) {
    suspend fun events(start: LocalDate, end: LocalDate): List<EconomicEvent> =
        withContext(Dispatchers.IO) {
            require(!end.isBefore(start)) { "Calendar end date is before start date" }
            val url = CALENDAR_URL.toHttpUrl().newBuilder()
                .addQueryParameter("d1", start.toString())
                .addQueryParameter("d2", end.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Cookie", "cal-timezone-offset=0")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Calendar provider returned HTTP ${response.code}" }
                parse(response.body?.string().orEmpty())
            }
        }

    internal fun parse(html: String, now: Instant = Instant.now()): List<EconomicEvent> {
        val document = Jsoup.parse(html)
        return document.select("tr[data-event], tr[data-url], tr[data-id]").mapNotNull { row ->
            val eventName = row.attrOrNull("data-event", "data-title")
                ?: row.selectFirst(".calendar-event, .calendar-item, [data-event]")?.cleanText()
                ?: return@mapNotNull null
            val eventTime = row.attrOrNull("data-date", "data-datetime", "data-time")
                ?.let(::parseDateTime)
                ?: parseTableDateTime(row)
                ?: return@mapNotNull null
            val country = normalizeCountry(
                row.attrOrNull("data-country")
                    ?: row.selectFirst(".calendar-country, .country, .calendar-iso")?.cleanText()
                    ?: "Unknown",
            )
            val category = row.attrOrNull("data-category") ?: eventName
            val actualText = row.cellValue("data-actual", "#actual, .calendar-actual, [data-field=actual]")
            val previousText = row.cellValue("data-previous", "#previous, .calendar-previous, [data-field=previous]")
            val consensusText = row.cellValue("data-consensus", "#consensus, .calendar-consensus, [data-field=consensus]")
            val forecastText = row.cellValue("data-forecast", "#forecast, .calendar-forecast, [data-field=forecast]")
            val providerId = row.attrOrNull("data-id", "data-event-id", "data-url")
                ?: "$country|${eventTime.epochSecond}|$eventName"
            val actual = parseNumber(actualText)
            EconomicEvent(
                id = stableId("$country|${eventTime.epochSecond}|${eventName.lowercase(Locale.ROOT)}"),
                provider = "trading_economics",
                providerId = providerId,
                releaseGroupId = null,
                country = country,
                currency = row.attrOrNull("data-currency") ?: countryCurrency(country),
                category = category,
                event = eventName,
                eventTime = eventTime.toString(),
                importance = parseImportance(row),
                actual = actual,
                previous = parseNumber(previousText),
                consensus = parseNumber(consensusText),
                forecast = parseNumber(forecastText),
                unit = listOf(actualText, consensusText, previousText, forecastText)
                    .firstNotNullOfOrNull(::detectUnit),
                status = when {
                    actual == null -> "scheduled"
                    eventTime.isBefore(now.minusSeconds(86_400)) -> "historical"
                    else -> "released"
                },
            )
        }.distinctBy { it.id }.sortedBy { it.eventTime }
    }

    private fun parseDateTime(value: String): Instant? {
        val clean = value.trim()
        runCatching { return OffsetDateTime.parse(clean).toInstant() }
        DATE_TIME_FORMATS.forEach { format ->
            runCatching { return LocalDateTime.parse(clean, format).toInstant(ZoneOffset.UTC) }
        }
        return null
    }

    private fun parseTableDateTime(row: Element): Instant? {
        val cell = row.selectFirst("td:first-child") ?: return null
        val date = cell.classNames().firstNotNullOfOrNull { token ->
            runCatching { LocalDate.parse(token) }.getOrNull()
        } ?: return null
        val timeText = cell.cleanText()
        val time = TIME_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { LocalTime.parse(timeText, format) }.getOrNull()
        } ?: return null
        return date.atTime(time).toInstant(ZoneOffset.UTC)
    }

    private fun parseImportance(row: Element): Int {
        row.attrOrNull("data-importance")?.trim()?.toIntOrNull()?.let { return it.coerceIn(0, 3) }
        val text = row.selectFirst(".calendar-importance, .importance")?.cleanText().orEmpty()
        text.toIntOrNull()?.let { return it.coerceIn(0, 3) }
        val lower = text.lowercase(Locale.ROOT)
        if ("high" in lower) return 3
        if ("medium" in lower) return 2
        if ("low" in lower) return 1
        val className = row.selectFirst("span[class*=calendar-date-]")?.classNames().orEmpty()
            .firstOrNull { it.startsWith("calendar-date-") }
        return className?.substringAfterLast('-')?.toIntOrNull()?.coerceIn(0, 3)
            ?: text.count { it == '★' }.coerceIn(0, 3)
    }

    companion object {
        private const val CALENDAR_URL = "https://tradingeconomics.com/calendar"
        private val DATE_TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        )
        private val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("HH:mm", Locale.US),
        )

        internal fun stableId(value: String): Long {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            var id = 0L
            repeat(8) { index -> id = (id shl 8) or (digest[index].toLong() and 0xff) }
            return id and Long.MAX_VALUE
        }

        internal fun parseNumber(raw: String?): String? {
            var value = raw?.cleanText().orEmpty()
            if (value.isEmpty() || value in setOf("-", "--", "N/A")) return null
            value = value.replace("%", "").replace(",", "")
                .replace("$", "").replace("€", "").replace("£", "")
                .replace('−', '-').trim().trimStart('<', '>')
            val suffix = value.lastOrNull()?.uppercaseChar()
            val multiplier = when (suffix) {
                'K' -> BigDecimal("1000")
                'M' -> BigDecimal("1000000")
                'B' -> BigDecimal("1000000000")
                'T' -> BigDecimal("1000000000000")
                else -> BigDecimal.ONE
            }
            if (multiplier != BigDecimal.ONE) value = value.dropLast(1).trim()
            return value.toBigDecimalOrNull()?.multiply(multiplier)?.stripTrailingZeros()?.toPlainString()
        }

        private fun detectUnit(raw: String?): String? {
            val value = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return when {
                '%' in value -> "%"
                value.endsWith('K') -> "count"
                value.endsWith('M') || value.endsWith('B') || value.endsWith('T') -> "currency"
                else -> null
            }
        }

        private fun normalizeCountry(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
            "us", "usa", "united states" -> "United States"
            "uk", "united kingdom" -> "United Kingdom"
            "eu", "european union" -> "European Union"
            "opec" -> "OPEC"
            else -> raw.trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
                .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
        }

        private fun countryCurrency(country: String): String? = when (country) {
            "United States" -> "USD"
            "Euro Area", "European Union" -> "EUR"
            "United Kingdom" -> "GBP"
            "Japan" -> "JPY"
            "Australia" -> "AUD"
            "Canada" -> "CAD"
            "New Zealand" -> "NZD"
            "Switzerland" -> "CHF"
            "China" -> "CNY"
            else -> null
        }

        private fun String.cleanText(): String = replace('\u00a0', ' ')
            .split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ").trim()

        private fun Element.cleanText(): String = text().cleanText()

        private fun Element.attrOrNull(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            attr(name).cleanText().takeIf(String::isNotEmpty)
        }

        private fun Element.cellValue(attribute: String, selector: String): String? =
            attrOrNull(attribute) ?: selectFirst(selector)?.cleanText()?.takeIf(String::isNotEmpty)
    }
}
