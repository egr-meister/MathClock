package com.mathclock.app.data.repository

import com.mathclock.app.data.local.CalculationDao
import com.mathclock.app.data.local.CalculationEntity
import com.mathclock.app.data.local.MathClockDatabase
import com.mathclock.app.data.local.PreferencesStore
import com.mathclock.app.domain.calculator.CalcOperator
import com.mathclock.app.domain.calculator.CalculationRecord
import com.mathclock.app.domain.progress.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CalculationItem(
    val id: Long,
    val record: CalculationRecord,
    val createdAt: Long,
)

class CalculatorRepository(
    private val dao: CalculationDao,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    val history: Flow<List<CalculationItem>> = dao.observeAll().map { list ->
        list.mapNotNull { e ->
            val op = CalcOperator.entries.firstOrNull { it.name == e.operator } ?: return@mapNotNull null
            CalculationItem(e.id, CalculationRecord(e.leftOperand, op, e.rightOperand, e.result, e.rounded), e.createdAt)
        }
    }

    suspend fun add(record: CalculationRecord) = withContext(io) {
        dao.insert(
            CalculationEntity(
                leftOperand = record.left,
                operator = record.operator.name,
                rightOperand = record.right,
                result = record.result,
                rounded = record.rounded,
                createdAt = clock.nowMillis(),
            ),
        )
        dao.trimTo(KEEP)
    }

    suspend fun clear() = withContext(io) { dao.deleteAll() }

    companion object {
        const val KEEP = 50
    }
}

/**
 * Thin coroutine wrapper around [PracticeService]: moves work to the IO dispatcher and publishes a
 * change counter so screens re-query after writes.
 */
class PracticeRepository(
    val service: PracticeService,
    private val io: CoroutineDispatcher,
) {
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun bump() = _changes.update { it + 1 }

    suspend fun <T> read(block: suspend PracticeService.() -> T): T = withContext(io) { service.block() }

    suspend fun <T> write(block: suspend PracticeService.() -> T): T = withContext(io) {
        try {
            service.block()
        } finally {
            bump()
        }
    }
}

class DataResetter(
    private val db: MathClockDatabase,
    private val prefs: PreferencesStore,
    private val practice: PracticeRepository,
    private val io: CoroutineDispatcher,
) {
    suspend fun clearAll() = withContext(io) {
        practice.write { clearProgress() }
        db.calculationDao().deleteAll()
        db.clearAllTables()
        prefs.clearAll()
    }
}
