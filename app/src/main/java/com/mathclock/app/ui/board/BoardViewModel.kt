package com.mathclock.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.AppSettings
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.time.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveSessionInfo(
    val id: Long,
    val topicLabel: String,
    val answered: Int,
    val total: Int,
)

data class BoardUiState(
    val settings: AppSettings = AppSettings(),
    val exploredMinutes: Int = AppSettings.DEFAULT_EXPLORED,
    val todayAnswered: Int = 0,
    /** Null when today's set has not been generated yet. */
    val dailyAnswered: Int? = null,
    val dailyTotal: Int = 10,
    val activeFree: ActiveSessionInfo? = null,
)

class BoardViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = container.preferences
    private val practice = container.practice

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    private val refreshTick = MutableStateFlow(0)
    private var persistJob: Job? = null
    private var dragging = false

    init {
        viewModelScope.launch {
            prefs.settings.collect { s ->
                _state.update {
                    it.copy(settings = s, exploredMinutes = if (dragging) it.exploredMinutes else s.exploredMinutes)
                }
            }
        }
        viewModelScope.launch {
            combine(practice.changes, refreshTick) { a, b -> a to b }.collectLatest { load() }
        }
    }

    fun refresh() = refreshTick.update { it + 1 }

    private suspend fun load() {
        val result = practice.read {
            val today = today()
            val daily = dailySetFor(today)
            val active = activeFreeSession()
            Triple(answeredOn(today), daily, active)
        }
        val (todayCount, daily, active) = result
        _state.update {
            it.copy(
                todayAnswered = todayCount,
                dailyAnswered = daily?.answeredCount,
                dailyTotal = daily?.questions?.size ?: 10,
                activeFree = active?.let { a ->
                    ActiveSessionInfo(
                        id = a.session.id,
                        topicLabel = Topic.entries.firstOrNull { t -> t.name == a.session.topicSelection }?.title ?: "Mixed topics",
                        answered = a.answeredCount,
                        total = a.questions.size,
                    )
                },
            )
        }
    }

    fun setExplored(minutes: Int) {
        dragging = true
        _state.update { it.copy(exploredMinutes = minutes) }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(300)
            prefs.setExploredMinutes(minutes)
            dragging = false
        }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch { prefs.setTimeFormat(format) }
    }

    fun openDaily(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val s = prefs.current()
            val id = practice.write { getOrCreateDailySet(today(), s.defaultDifficulty, s.timeFormat) }
            onReady(id)
        }
    }

    fun endActive() {
        val id = _state.value.activeFree?.id ?: return
        viewModelScope.launch { practice.write { endSession(id) } }
    }
}
