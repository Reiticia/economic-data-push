package com.macroresearch.data

import com.macroresearch.data.local.EventDao
import com.macroresearch.data.local.FollowedEventEntity
import com.macroresearch.data.local.asEntity
import com.macroresearch.data.local.asExternalModel
import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.EventDetailResponse
import com.macroresearch.data.model.EventObservation
import com.macroresearch.data.model.MarketQuotesResponse
import com.macroresearch.data.model.MarketResponse
import com.macroresearch.data.remote.DirectMarketClient
import com.macroresearch.data.remote.TradingEconomicsClient
import com.macroresearch.data.remote.TranslationClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class MacroRepository(
    private val calendarClient: TradingEconomicsClient,
    private val marketClient: DirectMarketClient,
    private val translationClient: TranslationClient,
    private val analysisEngine: LocalAnalysisEngine,
    private val dao: EventDao,
    private val countryPreferences: CountryPreferences,
    private val marketPreferences: MarketPreferences,
    private val translationPreferences: TranslationPreferences,
) {
    val selectedCountries: StateFlow<Set<String>> = countryPreferences.selectedCountries
    val selectedMarkets: StateFlow<List<String>> = marketPreferences.selectedMarkets
    val translationSettings: StateFlow<TranslationSettings> = translationPreferences.settings
    private val _translationError = MutableStateFlow<String?>(null)
    val translationError = _translationError.asStateFlow()

    private val marketCache = ConcurrentHashMap<Long, CachedMarket>()
    private val historyMutex = Mutex()
    private val translationMutex = Mutex()
    private var historySyncedAt = 0L

    fun observeUpcoming(): Flow<List<EconomicEvent>> =
        dao.observeUpcoming(Instant.now().toString()).map { events ->
            events.map { it.asExternalModel() }
        }

    fun observeEvent(id: Long): Flow<EconomicEvent?> =
        dao.observeEvent(id).map { it?.asExternalModel() }

    fun observeFollowed(id: Long): Flow<Boolean> = dao.observeFollowed(id)

    suspend fun isFollowed(id: Long): Boolean = dao.isFollowed(id)

    suspend fun refreshUpcoming(days: Int = 7) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val events = mergeCachedTranslations(
            calendarClient.events(today.minusDays(1), today.plusDays(days.toLong())),
        )
        // Publish source data immediately. Translation is an enhancement and must not
        // block the calendar from appearing.
        dao.upsert(events.map(EconomicEvent::asEntity))
        val selected = selectedCountries.value
        val localToday = LocalDate.now()
        val now = Instant.now()
        val priorityEvents = events
            .filter { it.country in selected }
            .filter { event ->
                runCatching { !Instant.parse(event.eventTime).isBefore(now) }.getOrDefault(false)
            }
            .sortedWith(
                compareBy<EconomicEvent> { event ->
                    val date = runCatching {
                        Instant.parse(event.eventTime).atZone(ZoneId.systemDefault()).toLocalDate()
                    }.getOrNull()
                    if (date == localToday) 0 else 1
                }.thenBy { it.eventTime },
            )
            .take(30)
        if (priorityEvents.isNotEmpty()) {
            dao.upsert(enrichTranslations(priorityEvents).map(EconomicEvent::asEntity))
        }
        dao.deleteOlderThan(today.minusDays(120).atStartOfDay().toInstant(ZoneOffset.UTC).toString())
    }

    suspend fun calendar(
        date: LocalDate,
        country: String? = null,
        minimumImportance: Int? = null,
    ): List<EconomicEvent> {
        val events = mergeCachedTranslations(calendarClient.events(date, date))
            .filter { country == null || it.country == country }
            .filter { minimumImportance == null || it.importance >= minimumImportance }
        dao.upsert(events.map(EconomicEvent::asEntity))
        val selected = selectedCountries.value
        val priorityEvents = events.filter { country != null || it.country in selected }
        val localized = enrichTranslations(priorityEvents)
        val localizedById = localized.associateBy(EconomicEvent::id)
        if (localized.isNotEmpty()) dao.upsert(localized.map(EconomicEvent::asEntity))
        return events.map { localizedById[it.id] ?: it }
    }

    suspend fun event(id: Long): EventDetailResponse {
        val event = dao.event(id)?.asExternalModel() ?: error("Event is not available in the local cache")
        val observations = if (event.actual == null) emptyList() else listOf(
            EventObservation(
                id = TradingEconomicsClient.stableId("observation|${event.id}|${event.actual}"),
                eventId = event.id,
                observedAt = Instant.now().toString(),
                actual = event.actual,
                previous = event.previous,
                consensus = event.consensus,
                forecast = event.forecast,
            ),
        )
        return EventDetailResponse(event, observations)
    }

    suspend fun analysis(id: Long): AnalysisReport {
        val event = event(id).event
        return analysisEngine.analyze(event, market(id).reactions)
    }

    suspend fun market(id: Long): MarketResponse {
        val cached = marketCache[id]
        if (cached != null && System.currentTimeMillis() - cached.savedAt < MARKET_CACHE_MS) {
            return cached.value
        }
        val response = marketClient.eventMarket(event(id).event)
        marketCache[id] = CachedMarket(System.currentTimeMillis(), response)
        return response
    }

    suspend fun marketQuotes(symbols: List<String>? = null): MarketQuotesResponse =
        marketClient.quotes(symbols ?: MarketPreferences.SUPPORTED_MARKETS)

    suspend fun history(
        country: String? = null,
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<EconomicEvent> {
        var syncError: Throwable? = null
        if (offset == 0) {
            runCatching { syncRecentHistory() }.onFailure { syncError = it }
        }
        val page = dao.history(Instant.now().toString(), country, category, limit, offset)
            .map { it.asExternalModel() }
        if (page.isEmpty()) syncError?.let { throw it }
        val selected = selectedCountries.value
        val priorityPage = page.filter { country != null || it.country in selected }
        val localized = enrichTranslations(priorityPage)
        val localizedById = localized.associateBy(EconomicEvent::id)
        if (localized.isNotEmpty()) dao.upsert(localized.map(EconomicEvent::asEntity))
        return page.map { localizedById[it.id] ?: it }
    }

    suspend fun correctTranslation(event: EconomicEvent, zhCn: String, zhTw: String) {
        val simplified = zhCn.trim()
        val traditional = zhTw.trim()
        require(simplified.isNotEmpty() && traditional.isNotEmpty()) {
            "Both translations are required"
        }
        dao.updateTranslation(event.event, simplified, traditional)
    }

    suspend fun retranslateEventName(event: EconomicEvent): Pair<String, String> =
        translationMutex.withLock {
            val settings = translationPreferences.settings.value
            val apiKey = translationPreferences.apiKey()
            require(settings.configured && apiKey != null) {
                "Configure an API key in Settings first"
            }
            val translated = translationClient.translate(listOf(event.event), settings, apiKey)
            val result = translated[event.event]
                ?: error("AI did not return a translation for this event")
            dao.updateTranslation(event.event, result.first, result.second)
            _translationError.value = null
            result
        }

    suspend fun translationModels(baseUrl: String): List<String> {
        val apiKey = translationPreferences.apiKey()
            ?: error("Save an API key before fetching models")
        val normalizedUrl = TranslationPreferences.normalizeBaseUrl(baseUrl)
        return runCatching { translationClient.models(normalizedUrl, apiKey) }
            .onSuccess { _translationError.value = null }
            .onFailure { _translationError.value = it.message ?: "Unable to fetch models" }
            .getOrThrow()
    }

    fun saveTranslationSettings(apiKey: String, baseUrl: String, model: String) {
        translationPreferences.save(apiKey, baseUrl, model)
        _translationError.value = null
    }

    fun updateTranslationProvider(baseUrl: String, model: String) {
        translationPreferences.updateProvider(baseUrl, model)
        _translationError.value = null
    }

    fun clearTranslationSettings() {
        translationPreferences.clear()
        _translationError.value = null
    }

    suspend fun setFollowed(id: Long, followed: Boolean) {
        if (followed) dao.follow(FollowedEventEntity(id)) else dao.unfollow(id)
    }

    fun setCountries(countries: Set<String>) = countryPreferences.setCountries(countries)

    fun setCountryEnabled(country: String, enabled: Boolean) =
        countryPreferences.setCountryEnabled(country, enabled)

    fun setMarketEnabled(market: String, enabled: Boolean) =
        marketPreferences.setMarketEnabled(market, enabled)

    private suspend fun syncRecentHistory() = historyMutex.withLock {
        if (System.currentTimeMillis() - historySyncedAt < HISTORY_CACHE_MS) return@withLock
        val today = LocalDate.now(ZoneOffset.UTC)
        val events = calendarClient.events(today.minusDays(30), today.minusDays(1))
        val merged = mergeCachedTranslations(events)
        dao.upsert(merged.map(EconomicEvent::asEntity))
        historySyncedAt = System.currentTimeMillis()
    }

    private suspend fun enrichTranslations(events: List<EconomicEvent>): List<EconomicEvent> =
        translationMutex.withLock {
            // Re-read cached translations only after acquiring the lock. This prevents a
            // slower failed refresh from overwriting translations saved by another screen.
            val merged = mergeCachedTranslations(events)
            val settings = translationPreferences.settings.value
            val apiKey = translationPreferences.apiKey()
            if (!settings.configured || apiKey == null) return@withLock merged
            val missingNames = merged
                .filter { it.eventZhCn.isNullOrBlank() || it.eventZhTw.isNullOrBlank() }
                .map(EconomicEvent::event)
                .distinct()
            if (missingNames.isEmpty()) return@withLock merged

            val translated = mutableMapOf<String, Pair<String, String>>()
            runCatching {
                missingNames.chunked(5).forEach { batch ->
                    val batchResult = translationClient.translate(batch, settings, apiKey)
                    translated += batchResult
                    // Persist every successful batch before starting the next one. A slow
                    // or failed later batch can no longer discard completed translations.
                    dao.upsert(
                        merged.mapNotNull { event ->
                            batchResult[event.event]?.let { (zhCn, zhTw) ->
                                event.copy(eventZhCn = zhCn, eventZhTw = zhTw).asEntity()
                            }
                        },
                    )
                }
            }.onSuccess {
                _translationError.value = null
            }.onFailure { error ->
                _translationError.value = error.message ?: "Translation failed"
            }
            merged.map { event ->
                translated[event.event]?.let { (zhCn, zhTw) ->
                    event.copy(eventZhCn = zhCn, eventZhTw = zhTw)
                } ?: event
            }
        }

    private suspend fun mergeCachedTranslations(events: List<EconomicEvent>): List<EconomicEvent> {
        val names = events.map(EconomicEvent::event).distinct()
        if (names.isEmpty()) return events
        val cached = names.chunked(500)
            .flatMap { dao.translations(it) }
            .associate { it.event to (it.eventZhCn to it.eventZhTw) }
        return events.map { event ->
            val translation = cached[event.event]
            if (translation == null) event else event.copy(
                eventZhCn = event.eventZhCn ?: translation.first,
                eventZhTw = event.eventZhTw ?: translation.second,
            )
        }
    }

    private data class CachedMarket(val savedAt: Long, val value: MarketResponse)

    companion object {
        private const val MARKET_CACHE_MS = 30_000L
        private const val HISTORY_CACHE_MS = 10 * 60_000L
    }
}
