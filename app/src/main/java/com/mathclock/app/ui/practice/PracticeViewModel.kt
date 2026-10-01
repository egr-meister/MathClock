package com.mathclock.app.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathclock.app.AppContainer
import com.mathclock.app.data.local.TimeQuestionEntity
import com.mathclock.app.data.repository.LoadedSession
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.SessionStatus
import com.mathclock.app.domain.progress.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PracticeUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val session: LoadedSession? = null,
    val position: Int = 0,
    val selectedOption: Int? = null,
    val clockValue: Int? = null,
    val showHint: Boolean = false,
    val showResults: Boolean = false,
    val dayChanged: Boolean = false,
    val busy: Boolean = false,
    val today: LocalDate? = null,
) {
    val question: TimeQuestionEntity? get() = session?.questions?.getOrNull(position)
    val isLast: Boolean get() = session != null && position >= session.questions.size - 1
}

class PracticeViewModel(
    container: AppContainer,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val practice = container.practice
    private val prefs = container.preferences
    private val sessionId: Long = checkNotNull(handle.get<Long>(ARG_SESSION_ID)) { "sessionId missing" }

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { initialLoad() }
    }

    private suspend fun initialLoad() {
        val (loaded, today) = practice.read { load(sessionId) to today() }
        if (loaded == null) {
            _state.update { it.copy(loading = false, missing = true) }
            return
        }
        val position = loaded.session.currentPosition.coerceIn(0, (loaded.questions.size - 1).coerceAtLeast(0))
        val results = handle.get<Boolean>(KEY_RESULTS) == true ||
            (loaded.isComplete && loaded.questions.getOrNull(position)?.submittedAnswer != null && position == loaded.questions.size - 1) ||
            loaded.status != SessionStatus.IN_PROGRESS && loaded.type == SessionType.FREE
        _state.update { applyQuestionState(it.copy(loading = false, session = loaded, position = position, showResults = results, today = today)) }
    }

    /** Restores per-question UI state (pending selection, clock position, hint) from saved state / DB. */
    private fun applyQuestionState(s: PracticeUiState): PracticeUiState {
        val q = s.question ?: return s
        return s.copy(
            selectedOption = handle.get<Int>(keySelected(q.id)),
            clockValue = handle.get<Int>(keyClock(q.id)) ?: q.startMinutes,
            showHint = q.hintUsed,
        )
    }

    private suspend fun reload() {
        val loaded = practice.read { load(sessionId) } ?: return
        _state.update { applyQuestionState(it.copy(session = loaded)) }
    }

    fun select(option: Int) {
        val q = _state.value.question ?: return
        if (q.submittedAnswer != null) return
        handle[keySelected(q.id)] = option
        _state.update { it.copy(selectedOption = option) }
    }

    fun setClock(minutes: Int) {
        val q = _state.value.question ?: return
        handle[keyClock(q.id)] = minutes
        _state.update { it.copy(clockValue = minutes) }
    }

    fun check() {
        val s = _state.value
        val q = s.question ?: return
        if (q.submittedAnswer != null || s.busy) return
        val answer = if (q.topic == Topic.SET_CLOCK.name) s.clockValue ?: q.startMinutes else s.selectedOption ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                practice.write { recordAnswer(q.id, answer) }
                reload()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun showHint() {
        val q = _state.value.question ?: return
        _state.update { it.copy(showHint = true) }
        if (!q.hintUsed) viewModelScope.launch { practice.write { markHintUsed(q.id) }; reload() }
    }

    fun next() {
        val s = _state.value
        val session = s.session ?: return
        if (s.question?.submittedAnswer == null) return
        viewModelScope.launch {
            val today = practice.read { today() }
            val dayChanged = session.type == SessionType.DAILY && session.dailyDate != today && !session.isComplete
            if (s.isLast) {
                handle[KEY_RESULTS] = true
                _state.update { it.copy(showResults = true, today = today, dayChanged = dayChanged) }
            } else {
                val newPos = s.position + 1
                practice.write { setPosition(sessionId, newPos) }
                _state.update { applyQuestionState(it.copy(position = newPos, showHint = false, today = today, dayChanged = dayChanged)) }
            }
        }
    }

    /** Jumps to the first unanswered question (used from the results view of an unfinished set). */
    fun goToFirstUnanswered() {
        val session = _state.value.session ?: return
        val idx = session.questions.indexOfFirst { it.submittedAnswer == null }
        if (idx < 0) return
        handle[KEY_RESULTS] = false
        viewModelScope.launch {
            practice.write { setPosition(sessionId, idx) }
            _state.update { applyQuestionState(it.copy(position = idx, showResults = false)) }
        }
    }

    fun dismissDayChanged() = _state.update { it.copy(dayChanged = false) }

    fun openTodaysSet(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val settings = prefs.current()
            val id = practice.write { getOrCreateDailySet(today(), settings.defaultDifficulty, settings.timeFormat) }
            onReady(id)
        }
    }

    fun endSession(onDone: () -> Unit) {
        viewModelScope.launch {
            practice.write { endSession(sessionId) }
            onDone()
        }
    }

    fun restart(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = practice.write { restartFreeSession() }
            if (id != null) onReady(id)
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private const val KEY_RESULTS = "show_results"
        private fun keySelected(id: Long) = "selected_$id"
        private fun keyClock(id: Long) = "clock_$id"
    }
}
