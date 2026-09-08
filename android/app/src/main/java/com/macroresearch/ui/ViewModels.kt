package com.macroresearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.macroresearch.data.MacroRepository
import com.macroresearch.data.model.AnalysisReport
import com.macroresearch.data.model.EconomicEvent
import com.macroresearch.data.model.EventDetailResponse
import com.macroresearch.data.model.MarketResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LoadState<T>(
    val value: T? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val repository: MacroRepository) : ViewModel() {
    val events: StateFlow<List<EconomicEvent>> = combine(
        repository.observeUpcoming(),
        repository.selectedCountries,
    ) { events, countries ->
        events.filter { it.country in countries }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _refresh = MutableStateFlow(LoadState<Unit>())
    val refresh = _refresh.asStateFlow()
    private val _market = MutableStateFlow<MarketResponse?>(null)
    val market = _market.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _refresh.value = LoadState(loading = true)
        runCatching { repository.refreshUpcoming() }
            .onSuccess { _refresh.value = LoadState(Unit, loading = false) }
            .onFailure { _refresh.value = LoadState(loading = false, error = it.message) }
    }

    fun loadMarket(eventId: Long?) = viewModelScope.launch {
        _market.value = eventId?.let { runCatching { repository.market(it) }.getOrNull() }
    }
}

data class CalendarState(
    val date: LocalDate = LocalDate.now(),
    val events: List<EconomicEvent> = emptyList(),
    val importance: Set<Int> = setOf(2, 3),
    val countries: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val filtered: List<EconomicEvent> get() = events.filter {
        it.importance in importance && it.country in countries
    }
}

class CalendarViewModel(private val repository: MacroRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        CalendarState(countries = repository.selectedCountries.value),
    )
    val state = _state.asStateFlow()

    init {
        selectDate(LocalDate.now())
        viewModelScope.launch {
            repository.selectedCountries.collect { countries ->
                _state.value = _state.value.copy(countries = countries)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date, loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.calendar(date) }
                .onSuccess { _state.value = _state.value.copy(events = it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun applyFilters(importance: Set<Int>, countries: Set<String>) {
        _state.value = _state.value.copy(importance = importance, countries = countries)
        repository.setCountries(countries)
    }
}

data class EventDetailState(
    val detail: EventDetailResponse? = null,
    val market: MarketResponse? = null,
    val followed: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class EventDetailViewModel(
    private val id: Long,
    private val repository: MacroRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EventDetailState())
    val state = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.observeFollowed(id).collect { followed ->
                _state.value = _state.value.copy(followed = followed)
            }
        }
        viewModelScope.launch {
            repository.socketEvents.collect { event ->
                if (event.eventId == id) refresh()
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val detail = async { repository.event(id) }
            val market = async { runCatching { repository.market(id) }.getOrNull() }
            detail.await() to market.await()
        }.onSuccess { (detail, market) ->
            _state.value = _state.value.copy(detail = detail, market = market, loading = false)
        }.onFailure {
            _state.value = _state.value.copy(loading = false, error = it.message)
        }
    }

    fun toggleFollowed() = viewModelScope.launch {
        repository.setFollowed(id, !_state.value.followed)
    }
}

data class AnalysisState(
    val event: EconomicEvent? = null,
    val report: AnalysisReport? = null,
    val market: MarketResponse? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class AnalysisViewModel(
    private val id: Long,
    private val repository: MacroRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = AnalysisState(loading = true)
        val eventResult = runCatching { repository.event(id).event }
        if (eventResult.isFailure) {
            _state.value = AnalysisState(loading = false, error = eventResult.exceptionOrNull()?.message)
            return@launch
        }
        val report = async { runCatching { repository.analysis(id) } }
        val market = async { runCatching { repository.market(id) } }
        val reportResult = report.await()
        _state.value = AnalysisState(
            event = eventResult.getOrNull(),
            report = reportResult.getOrNull(),
            market = market.await().getOrNull(),
            loading = false,
            error = reportResult.exceptionOrNull()?.message,
        )
    }
}

class HistoryViewModel(private val repository: MacroRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoadState<List<EconomicEvent>>())
    val state = _state.asStateFlow()
    private val _category = MutableStateFlow<String?>(null)
    val category = _category.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private var selectedCountries = repository.selectedCountries.value
    private var sourceOffset = 0
    private var request: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedCountries.collect { countries ->
                selectedCountries = countries
                refresh()
            }
        }
    }

    fun refresh(category: String? = _category.value) {
        request?.cancel()
        _category.value = category
        sourceOffset = 0
        _hasMore.value = selectedCountries.isNotEmpty()
        _state.value = LoadState(value = emptyList(), loading = false)
        if (selectedCountries.isNotEmpty()) loadMore()
    }

    fun loadMore() {
        if (_state.value.loading || !_hasMore.value) return
        val existing = _state.value.value.orEmpty()
        val category = _category.value
        _state.value = LoadState(existing, loading = true)
        request = viewModelScope.launch {
            try {
                val page = repository.history(
                    country = selectedCountries.singleOrNull(),
                    category = category,
                    limit = 100,
                    offset = sourceOffset,
                )
                sourceOffset += page.size
                val visiblePage = page.filter { it.country in selectedCountries }
                val merged = (existing + visiblePage).distinctBy { it.id }
                _hasMore.value = page.size == 100
                _state.value = LoadState(merged, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = LoadState(existing, loading = false, error = error.message)
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <R : ViewModel> create(modelClass: Class<R>): R = create() as R
    }
