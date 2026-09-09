package com.macroresearch.data

import com.macroresearch.data.local.EventDao
import com.macroresearch.data.local.FollowedEventEntity
import com.macroresearch.data.local.asEntity
import com.macroresearch.data.local.asExternalModel
import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.EventDetailResponse
import com.macroresearch.data.model.MarketResponse
import com.macroresearch.data.model.MarketQuotesResponse
import com.macroresearch.data.model.SocketEvent
import com.macroresearch.data.remote.MacroApi
import com.macroresearch.data.remote.MacroSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class MacroRepository(
    private val api: MacroApi,
    private val dao: EventDao,
    private val socket: MacroSocket,
    private val countryPreferences: CountryPreferences,
    private val marketPreferences: MarketPreferences,
) {
    val socketEvents: SharedFlow<SocketEvent> = socket.events
    val selectedCountries: StateFlow<Set<String>> = countryPreferences.selectedCountries
    val selectedMarkets: StateFlow<List<String>> = marketPreferences.selectedMarkets

    fun observeUpcoming(): Flow<List<EconomicEvent>> =
        dao.observeUpcoming(Instant.now().toString()).map { events ->
            events.map { it.asExternalModel() }
        }

    fun observeEvent(id: Long): Flow<EconomicEvent?> =
        dao.observeEvent(id).map { it?.asExternalModel() }

    fun observeFollowed(id: Long): Flow<Boolean> = dao.observeFollowed(id)

    suspend fun isFollowed(id: Long): Boolean = dao.isFollowed(id)

    suspend fun refreshUpcoming(days: Int = 7) {
        val events = api.upcoming(days)
        dao.upsert(events.map(EconomicEvent::asEntity))
    }

    suspend fun calendar(
        date: LocalDate,
        country: String? = null,
        minimumImportance: Int? = null,
    ): List<EconomicEvent> {
        val events = api.calendar(date.toString(), date.toString(), country, minimumImportance)
        dao.upsert(events.map(EconomicEvent::asEntity))
        return events
    }

    suspend fun event(id: Long): EventDetailResponse {
        val response = api.event(id)
        dao.upsert(listOf(response.event.asEntity()))
        return response
    }

    suspend fun analysis(id: Long): AnalysisReport = api.analysis(id)

    suspend fun market(id: Long): MarketResponse = api.market(id)

    suspend fun marketQuotes(symbols: List<String>? = null): MarketQuotesResponse =
        api.marketQuotes(symbols?.joinToString(","))

    suspend fun history(
        country: String? = null,
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<EconomicEvent> = api.history(country, category, limit, offset)

    suspend fun setFollowed(id: Long, followed: Boolean) {
        if (followed) dao.follow(FollowedEventEntity(id)) else dao.unfollow(id)
    }

    fun setCountries(countries: Set<String>) = countryPreferences.setCountries(countries)

    fun setCountryEnabled(country: String, enabled: Boolean) =
        countryPreferences.setCountryEnabled(country, enabled)

    fun setMarketEnabled(market: String, enabled: Boolean) =
        marketPreferences.setMarketEnabled(market, enabled)

    fun connectSocket() = socket.connect()
    fun closeSocket() = socket.close()
}
