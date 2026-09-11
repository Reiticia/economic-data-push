package com.macroresearch.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.releaseStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.net.Proxy
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Economic calendar aggregated on-device from keyless JSON sources.
 *
 * Primary source is TradingView's economic calendar endpoint, which covers arbitrary
 * date ranges with actual / previous / forecast values. When TradingView fails or
 * returns nothing and the requested range overlaps the current week, the client falls
 * back to the Forex Factory weekly JSON feed. Neither source needs an API key.
 */
data class CalendarFetchResult(val events: List<EconomicEvent>, val warning: String? = null)

class EconomicCalendarClient(
    private val client: OkHttpClient,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val proxy: () -> Proxy? = { null },
    private val primaryUrl: String = TRADING_VIEW_URL,
    private val fallbackUrl: String = FOREX_FACTORY_URL,
) {
    suspend fun events(
        start: LocalDate,
        end: LocalDate,
        countryCodes: Collection<String>? = null,
    ): List<EconomicEvent> = fetch(start, end, countryCodes).events

    suspend fun fetch(
        start: LocalDate,
        end: LocalDate,
        countryCodes: Collection<String>? = null,
    ): CalendarFetchResult = withContext(Dispatchers.IO) {
        require(!end.isBefore(start)) { "Calendar end date is before start date" }
        val now = Instant.now()
        val http = proxy()?.let { client.newBuilder().proxy(it).build() } ?: client
        var primaryError: Exception? = null
        val primaryEvents = try {
            fetchTradingView(http, start, end, countryCodes, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            primaryError = error
            emptyList()
        }
        if (primaryEvents.isNotEmpty()) return@withContext CalendarFetchResult(primaryEvents)
        val fallback = try {
            fetchForexFactory(http, start, end, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw primaryError ?: error
        }
        // A weekly schedule is not a successful historical result sync. Expose degradation
        // even when it returned rows, so missing actuals are never mistaken for future releases.
        CalendarFetchResult(
            fallback,
            primaryError?.let { "${it.javaClass.simpleName}: ${it.message}" }
                ?: if (fallback.isNotEmpty()) "Primary source returned no events; weekly schedule only" else null,
        )
    }

    private fun fetchTradingView(
        http: OkHttpClient,
        start: LocalDate,
        end: LocalDate,
        countryCodes: Collection<String>?,
        now: Instant,
    ): List<EconomicEvent> {
        val from = start.atStartOfDay(zone).toInstant()
        // `to` is an inclusive instant: the last second of the local day, so the window covers
        // exactly the requested local days instead of pulling in the next day's all-day rows.
        val to = end.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
        val builder = primaryUrl.toHttpUrl().newBuilder()
            .addQueryParameter("from", DateTimeFormatter.ISO_INSTANT.format(from))
            .addQueryParameter("to", DateTimeFormatter.ISO_INSTANT.format(to))
        countryCodes?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("countries", it.joinToString(",")) }
        val request = Request.Builder()
            .url(builder.build())
            .header("Accept", "application/json")
            .header("Origin", "https://www.tradingview.com")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Calendar provider returned HTTP ${response.code}" }
            return parseTradingView(response.body?.string().orEmpty(), now)
        }
    }

    private fun fetchForexFactory(http: OkHttpClient, start: LocalDate, end: LocalDate, now: Instant): List<EconomicEvent> {
        val request = Request.Builder()
            .url(fallbackUrl)
            .header("Accept", "application/json")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Fallback calendar returned HTTP ${response.code}" }
            return parseForexFactory(response.body?.string().orEmpty(), now, start, end)
        }
    }

    internal fun parseTradingView(json: String, now: Instant = Instant.now()): List<EconomicEvent> {
        val payload = JsonParser.parseString(json)
        if (!payload.isJsonObject) error("Unexpected TradingView calendar payload")
        val root = payload.asJsonObject
        when (val status = root.text("status")) {
            "no_data" -> return emptyList()
            "ok" -> Unit
            else -> error("TradingView calendar status: ${status ?: "missing"}")
        }
        val result = root.get("result")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return emptyList()
        return result.map { element ->
            if (!element.isJsonObject) return@map null
            val entry = element.asJsonObject
            val providerId = entry.text("id") ?: return@map null
            val title = entry.text("title").normalized() ?: return@map null
            val eventTime = entry.text("date")?.let { value ->
                runCatching { Instant.parse(value) }.getOrNull()
            } ?: return@map null
            val countryCode = entry.text("country").normalized() ?: "Unknown"
            val country = TRADING_VIEW_COUNTRIES[countryCode] ?: countryCode
            val actual = entry.number("actual")
            // TradingView publishes a single market expectation. Store it as the consensus
            // baseline too, otherwise the rule engine has nothing to compare the actual against
            // and every release would be classified as neutral.
            val expectation = entry.number("forecast")
            val unit = entry.text("unit").normalized()
                ?.takeIf { !it.equals("None", ignoreCase = true) }
            EconomicEvent(
                id = stableEventId("trading_view|$providerId"),
                provider = "trading_view",
                providerId = providerId,
                releaseGroupId = null,
                country = country,
                currency = entry.text("currency").normalized(),
                category = entry.text("indicator").normalized() ?: title,
                event = title,
                eventTime = eventTime.toString(),
                importance = when (entry.integer("importance")) {
                    null -> 1
                    1 -> 3
                    0 -> 2
                    else -> 1
                },
                actual = actual,
                previous = entry.number("previous"),
                consensus = expectation,
                forecast = expectation,
                unit = unit,
                status = eventStatus(actual, eventTime, now),
            )
        }.filterNotNull().distinctBy(EconomicEvent::id).sortedBy(EconomicEvent::eventTime)
    }

    internal fun parseForexFactory(
        json: String,
        now: Instant = Instant.now(),
        start: LocalDate = LocalDate.now(zone),
        end: LocalDate = start,
    ): List<EconomicEvent> {
        val payload = JsonParser.parseString(json)
        if (!payload.isJsonArray) error("Unexpected Forex Factory calendar payload")
        val rangeStart = start.atStartOfDay(zone).toInstant()
        val rangeEnd = end.plusDays(1).atStartOfDay(zone).toInstant()
        return payload.asJsonArray.map { element ->
            if (!element.isJsonObject) return@map null
            val entry = element.asJsonObject
            val title = entry.text("title").normalized() ?: return@map null
            val dateText = entry.text("date") ?: return@map null
            val eventTime = runCatching { OffsetDateTime.parse(dateText).toInstant() }.getOrNull()
                ?: return@map null
            if (eventTime < rangeStart || eventTime >= rangeEnd) return@map null
            val currency = entry.text("country").normalized()?.uppercase(Locale.ROOT) ?: "Unknown"
            val actual = parseEventNumber(entry.text("actual"))
            // The weekly feed also exposes one expectation value; use it as the consensus baseline.
            val expectation = parseEventNumber(entry.text("forecast"))
            val unit = listOf(entry.text("actual"), entry.text("forecast"), entry.text("previous"))
                .firstNotNullOfOrNull(::detectUnit)
            EconomicEvent(
                id = stableEventId("forex_factory|$dateText|$currency|${title.lowercase(Locale.ROOT)}"),
                provider = "forex_factory",
                providerId = "$currency|$dateText|$title",
                releaseGroupId = null,
                country = CURRENCIES_BY_FEED[currency] ?: currency,
                currency = currency.takeUnless { it == "Unknown" },
                category = title,
                event = title,
                eventTime = eventTime.toString(),
                importance = when (entry.text("impact")?.trim()?.lowercase(Locale.ROOT)) {
                    "high" -> 3
                    "medium" -> 2
                    else -> 1
                },
                actual = actual,
                previous = parseEventNumber(entry.text("previous")),
                consensus = expectation,
                forecast = expectation,
                unit = unit,
                status = eventStatus(actual, eventTime, now),
            )
        }.filterNotNull().distinctBy(EconomicEvent::id).sortedBy(EconomicEvent::eventTime)
    }

    private fun eventStatus(actual: String?, eventTime: Instant, now: Instant): String =
        releaseStatus(actual, eventTime, now)

    private fun String?.normalized(): String? = this?.trim()?.takeUnless(String::isEmpty)

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString

    private fun JsonObject.number(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asJsonPrimitive?.asBigDecimal
            ?.let(BigDecimal::stripTrailingZeros)?.toPlainString()

    private fun JsonObject.integer(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    companion object {
        private const val TRADING_VIEW_URL = "https://economic-calendar.tradingview.com/events"
        private const val FOREX_FACTORY_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"

        private val COUNTRY_CODES by lazy {
            TRADING_VIEW_COUNTRIES.entries.associate { (code, name) -> name to code }
        }

        /** Maps supported display names ("United States") onto TradingView codes ("US"). */
        fun codesFor(countries: Collection<String>): List<String> =
            countries.mapNotNull { COUNTRY_CODES[it.trim()] }.distinct()

        private val TRADING_VIEW_COUNTRIES = mapOf(
            "US" to "United States",
            "EU" to "Euro Area",
            "CN" to "China",
            "JP" to "Japan",
            "GB" to "United Kingdom",
            "DE" to "Germany",
            "FR" to "France",
            "IT" to "Italy",
            "ES" to "Spain",
            "NL" to "Netherlands",
            "PT" to "Portugal",
            "GR" to "Greece",
            "IE" to "Ireland",
            "AT" to "Austria",
            "BE" to "Belgium",
            "FI" to "Finland",
            "LU" to "Luxembourg",
            "MT" to "Malta",
            "CY" to "Cyprus",
            "SK" to "Slovakia",
            "SI" to "Slovenia",
            "EE" to "Estonia",
            "LV" to "Latvia",
            "LT" to "Lithuania",
            "AU" to "Australia",
            "CA" to "Canada",
            "CH" to "Switzerland",
            "NZ" to "New Zealand",
            "KR" to "South Korea",
            "IN" to "India",
            "SG" to "Singapore",
            "HK" to "Hong Kong",
            "TW" to "Taiwan",
            "BR" to "Brazil",
            "MX" to "Mexico",
            "ZA" to "South Africa",
            "TR" to "Turkey",
            "RU" to "Russia",
            "SE" to "Sweden",
            "NO" to "Norway",
            "DK" to "Denmark",
            "PL" to "Poland",
            "CZ" to "Czechia",
            "HU" to "Hungary",
            "RO" to "Romania",
            "IL" to "Israel",
            "SA" to "Saudi Arabia",
            "ID" to "Indonesia",
            "MY" to "Malaysia",
            "TH" to "Thailand",
            "PH" to "Philippines",
            "VN" to "Vietnam",
            "AR" to "Argentina",
            "CL" to "Chile",
            "CO" to "Colombia",
            "PE" to "Peru",
            "EG" to "Egypt",
            "NG" to "Nigeria",
            "UA" to "Ukraine",
        )

        private val CURRENCIES_BY_FEED = mapOf(
            "USD" to "United States",
            "EUR" to "Euro Area",
            "CNY" to "China",
            "JPY" to "Japan",
            "GBP" to "United Kingdom",
            "AUD" to "Australia",
            "CAD" to "Canada",
            "CHF" to "Switzerland",
            "NZD" to "New Zealand",
        )
    }
}

/** Stable 63-bit non-negative digest so Room upserts overwrite the same event on refresh. */
internal fun stableEventId(value: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    var id = 0L
    repeat(8) { index -> id = (id shl 8) or (digest[index].toLong() and 0xff) }
    return id and Long.MAX_VALUE
}

/** Normalizes display values such as "0.2%", "768B" or "1,234" into plain decimal strings. */
internal fun parseEventNumber(raw: String?): String? {
    var value = raw?.trim().orEmpty()
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
