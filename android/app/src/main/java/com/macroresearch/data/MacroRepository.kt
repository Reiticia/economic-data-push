package com.macroresearch.data

import com.macroresearch.data.local.AiAnalysisEntity
import com.macroresearch.data.local.AnalysisDao
import com.macroresearch.data.local.EventDao
import com.macroresearch.data.local.FollowedEventEntity
import com.macroresearch.data.local.asEntity
import com.macroresearch.data.local.asExternalModel
import com.macroresearch.data.model.AiAnalysis
import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.EventDetailResponse
import com.macroresearch.data.model.EventObservation
import com.macroresearch.data.model.MarketQuotesResponse
import com.macroresearch.data.model.MarketResponse
import com.macroresearch.data.remote.AiAnalysisClient
import com.macroresearch.data.remote.AiAnalysisInput
import com.macroresearch.data.remote.DirectMarketClient
import com.macroresearch.data.remote.EconomicCalendarClient
import com.macroresearch.data.remote.TranslationClient
import com.macroresearch.data.remote.stableEventId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class MacroRepository(
    private val calendarClient: EconomicCalendarClient,
    private val calendarNetwork: CalendarNetworkPreferences,
    private val analysisPreferences: AnalysisPreferences,
    private val marketClient: DirectMarketClient,
    private val translationClient: TranslationClient,
    private val aiAnalysisClient: AiAnalysisClient,
    private val analysisEngine: LocalAnalysisEngine,
    private val dao: EventDao,
    private val analysisDao: AnalysisDao,
    private val countryPreferences: CountryPreferences,
    private val marketPreferences: MarketPreferences,
    private val translationPreferences: TranslationPreferences,
) {
    val selectedCountries: StateFlow<Set<String>> = countryPreferences.selectedCountries
    val selectedMarkets: StateFlow<List<String>> = marketPreferences.selectedMarkets
    val translationSettings: StateFlow<TranslationSettings> = translationPreferences.settings
    private val _translationError = MutableStateFlow<String?>(null)
    val translationError = _translationError.asStateFlow()
    val calendarProxy = calendarNetwork.address
    val analysisMethod: StateFlow<AnalysisMethod> = analysisPreferences.method

    fun setAnalysisMethod(method: AnalysisMethod) = analysisPreferences.setMethod(method)

    private val _calendarWarning = MutableStateFlow<String?>(null)
    val calendarWarning = _calendarWarning.asStateFlow()

    fun saveCalendarProxy(address: String) {
        calendarNetwork.save(address)
        historySyncedAt = 0L
    }

    private suspend fun fetchCalendar(start: LocalDate, end: LocalDate): List<EconomicEvent> {
        val result = calendarClient.fetch(start, end)
        _calendarWarning.value = result.warning
        return result.events
    }

    /** Emits after freshly reviewed translations are persisted so lists can re-read them. */
    private val _translationsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val translationsUpdated: SharedFlow<Unit> = _translationsUpdated.asSharedFlow()

    private val releaseRefresher = EventReleaseRefresher(calendarClient, dao)
    private val marketCache = ConcurrentHashMap<Long, CachedMarket>()
    private val historyMutex = Mutex()
    private val translationMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        // Drop rows cached by calendar providers that are no longer used so the
        // upcoming list cannot show the same event twice after an app update.
        dao.deleteByProviders(LEGACY_CALENDAR_PROVIDERS)
        // Days follow the device zone so the refresh window matches the times shown on cards.
        val today = LocalDate.now()
        val source = mergeCachedTranslations(
            fetchCalendar(today.minusDays(1), today.plusDays(days.toLong())),
        )
        // Hydrate matching fallback rows in place, retaining their IDs, follows and names.
        val events = dao.mergeCalendar(source.map(EconomicEvent::asEntity)).map { it.asExternalModel() }
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
            // Home renders from Room, so translations may arrive after the list is shown.
            repositoryScope.launch { runCatching { enrichTranslations(priorityEvents) } }
        }
        dao.deleteOlderThan(today.minusDays(120).atStartOfDay(ZoneId.systemDefault()).toInstant().toString())
    }

    suspend fun calendar(
        date: LocalDate,
        country: String? = null,
        minimumImportance: Int? = null,
    ): List<EconomicEvent> {
        val source = mergeCachedTranslations(fetchCalendar(date, date))
            .filter { country == null || it.country == country }
            .filter { minimumImportance == null || it.importance >= minimumImportance }
        val events = dao.mergeCalendar(source.map(EconomicEvent::asEntity)).map { it.asExternalModel() }
        // Translation is an enhancement and must never hold back the day's list.
        val priorityEvents = events.filter { country != null || it.country in selectedCountries.value }
        if (priorityEvents.isNotEmpty()) {
            repositoryScope.launch { runCatching { enrichTranslations(priorityEvents) } }
        }
        return events
    }

    suspend fun event(id: Long): EventDetailResponse {
        val event = dao.event(id)?.asExternalModel() ?: error("Event is not available in the local cache")
        val observations = if (event.actual == null) emptyList() else listOf(
            EventObservation(
                id = stableEventId("observation|${event.id}|${event.actual}"),
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

    /**
     * User-triggered retry for one event whose release value is still missing. Bypasses the
     * history sync interval, keeps the cached row and its translated name, and reports the
     * source warning so the caller can explain why a value is still absent.
     */
    suspend fun refreshEventRelease(id: Long): EventDetailResponse {
        val warning = releaseRefresher.refresh(id).warning
        _calendarWarning.value = warning
        return event(id)
    }

    suspend fun analysis(id: Long): AnalysisReport {
        val event = event(id).event
        return analysisEngine.analyze(event, market(id).reactions)
    }

    fun observeAiAnalysis(eventId: Long): Flow<AiAnalysis?> =
        analysisDao.observe(eventId).map { it?.toModel() }

    suspend fun aiAnalysis(eventId: Long): AiAnalysis? = analysisDao.analysis(eventId)?.toModel()

    /**
     * Builds the AI briefing for a released event and stores it locally. Re-running
     * replaces the cached row and bumps its revision, so the user can always refresh it.
     */
    suspend fun generateAiAnalysis(eventId: Long, languageTag: String): AiAnalysis {
        val settings = translationPreferences.settings.value
        val apiKey = translationPreferences.apiKey()
        require(settings.configured && apiKey != null) {
            "Configure an API key in Settings first"
        }
        val event = dao.event(eventId)?.asExternalModel()
            ?: error("Event is not available in the local cache")
        require(event.actual != null) { "The release has no published value yet" }
        val report = analysisEngine.analyze(event, market(eventId).reactions)
        val draft = aiAnalysisClient.analyze(
            AiAnalysisInput(
                event = event,
                macroSignal = report.macroSignal,
                rawSurprise = report.rawSurprise,
                expectedReactions = report.expectedReactions,
                observedReactions = report.observedReactions,
                languageTag = languageTag,
            ),
            settings = settings,
            apiKey = apiKey,
            method = analysisPreferences.method.value,
        )
        val analysis = AiAnalysis(
            eventId = eventId,
            revision = (analysisDao.analysis(eventId)?.revision ?: 0) + 1,
            chain = draft.chain,
            dataAnalysis = draft.dataAnalysis,
            marketOutlook = draft.marketOutlook,
            risks = draft.risks,
            model = settings.model,
            generatedAt = Instant.now().toString(),
        )
        analysisDao.upsert(analysis.toEntity())
        return analysis
    }

    private fun AiAnalysisEntity.toModel(): AiAnalysis = AiAnalysis(
        eventId = eventId,
        revision = revision,
        chain = aiAnalysisClient.decodeChain(chainJson),
        dataAnalysis = dataAnalysis,
        marketOutlook = marketOutlook,
        risks = risks,
        model = model,
        generatedAt = generatedAt,
    )

    private fun AiAnalysis.toEntity(): AiAnalysisEntity = AiAnalysisEntity(
        eventId = eventId,
        revision = revision,
        chainJson = aiAnalysisClient.encodeChain(chain),
        dataAnalysis = dataAnalysis,
        marketOutlook = marketOutlook,
        risks = risks,
        model = model,
        generatedAt = generatedAt,
    )

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
        forceRefresh: Boolean = false,
    ): List<EconomicEvent> {
        var syncError: Throwable? = null
        var page = dao.history(Instant.now().toString(), country, category, limit, offset)
            .map { it.asExternalModel() }
        if (offset == 0 && (forceRefresh || historySyncDue())) {
            // History must be refreshed before it is rendered. Returning rows cached while the
            // events were still upcoming leaves the list stuck on stale values and statuses.
            try {
                syncRecentHistory(forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                syncError = error
                _calendarWarning.value = "${error.javaClass.simpleName}: ${error.message}"
            }
            page = dao.history(Instant.now().toString(), country, category, limit, offset)
                .map { it.asExternalModel() }
        }
        if (page.isEmpty()) syncError?.let { throw it }
        // Translation is an enhancement: return the cached page immediately and let the
        // reviewed translations stream in through [translationsUpdated] instead.
        val priorityPage = page.filter { country != null || it.country in selectedCountries.value }
        if (priorityPage.isNotEmpty()) {
            repositoryScope.launch { runCatching { enrichTranslations(priorityPage) } }
        }
        return page
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
            val translated = translationClient.translateVerified(
                listOf(event.event),
                settings,
                apiKey,
                retryRejected = true,
            )
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

    private fun historySyncDue(): Boolean =
        System.currentTimeMillis() - historySyncedAt >= HISTORY_CACHE_MS

    private suspend fun syncRecentHistory(force: Boolean = false) = historyMutex.withLock {
        if (!force && !historySyncDue()) return@withLock
        val today = LocalDate.now()
        // The history screen only ever displays supported countries, and the calendar API
        // accepts a country filter: fetching worldwide rows would download several MB and
        // hit the endpoint's event cap for nothing.
        val countryCodes = EconomicCalendarClient.codesFor(CountryPreferences.SUPPORTED_COUNTRIES)
        // Include today's elapsed releases too; history queries still exclude future rows.
        val result = calendarClient.fetch(today.minusDays(30), today, countryCodes)
        _calendarWarning.value = result.warning
        val merged = mergeCachedTranslations(result.events)
        dao.mergeCalendar(merged.map(EconomicEvent::asEntity))
        // A successful weekly fallback did not fill historical actuals. Allow the next retry.
        if (result.warning == null) historySyncedAt = System.currentTimeMillis()
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

            // Batches run with bounded concurrency and fail independently: a single
            // slow or rejected batch no longer stalls the whole page load. Each batch is
            // AI-reviewed and only confirmed translations are returned, so the persistence
            // step below only ever stores reviewed text.
            val semaphore = Semaphore(TRANSLATION_MAX_CONCURRENT_BATCHES)
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    missingNames.chunked(TRANSLATION_BATCH_SIZE).map { batch ->
                        async {
                            semaphore.withPermit {
                                runCatching { translationClient.translateVerified(batch, settings, apiKey) }
                            }
                        }
                    }.awaitAll()
                }
            }
            val translated = mutableMapOf<String, Pair<String, String>>()
            var firstError: String? = null
            results.forEach { result ->
                result.onSuccess { batchResult -> translated += batchResult }
                    .onFailure { error -> firstError = firstError ?: error.message ?: "Translation failed" }
            }
            if (translated.isNotEmpty()) {
                // Only update name columns. A slow AI response must never overwrite newly
                // published actuals/status with the pre-release snapshot it started from.
                translated.forEach { (name, translation) ->
                    dao.updateTranslation(name, translation.first, translation.second)
                }
                // Lists render before enrichment finishes; this tells them to re-read.
                _translationsUpdated.tryEmit(Unit)
            }
            _translationError.value = firstError
            merged.map { event ->
                translated[event.event]?.let { (zhCn, zhTw) ->
                    event.copy(eventZhCn = zhCn, eventZhTw = zhTw)
                } ?: event
            }
        }

    /** Name-keyed translations already stored locally; also used to refresh rendered lists. */
    suspend fun cachedTranslations(names: Collection<String>): Map<String, Pair<String, String>> {
        val distinct = names.map(String::trim).filter(String::isNotEmpty).distinct()
        if (distinct.isEmpty()) return emptyMap()
        return distinct.chunked(500)
            .flatMap { dao.translations(it) }
            .associate { it.event to (it.eventZhCn to it.eventZhTw) }
    }

    private suspend fun mergeCachedTranslations(events: List<EconomicEvent>): List<EconomicEvent> {
        if (events.isEmpty()) return events
        val cached = cachedTranslations(events.map(EconomicEvent::event))
        if (cached.isEmpty()) return events
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
        private const val TRANSLATION_BATCH_SIZE = 5
        private const val TRANSLATION_MAX_CONCURRENT_BATCHES = 4

        /** Providers replaced by EconomicCalendarClient; their cached rows are dropped on refresh. */
        private val LEGACY_CALENDAR_PROVIDERS = listOf("trading_economics")
    }
}
