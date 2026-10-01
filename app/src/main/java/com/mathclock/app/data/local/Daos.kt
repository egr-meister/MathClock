package com.mathclock.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CalculationEntity>>

    @Insert
    suspend fun insert(entity: CalculationEntity): Long

    @Query(
        "DELETE FROM calculations WHERE id NOT IN " +
            "(SELECT id FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM calculations")
    suspend fun deleteAll()
}

@Dao
interface PracticeDao {
    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun session(id: Long): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE dailyDate = :date")
    suspend fun dailySession(date: String): PracticeSessionEntity?

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Insert
    suspend fun insertQuestions(questions: List<TimeQuestionEntity>)

    @Query("SELECT * FROM time_questions WHERE sessionId = :sessionId ORDER BY position")
    suspend fun questions(sessionId: Long): List<TimeQuestionEntity>

    @Query("SELECT * FROM time_questions WHERE id = :id")
    suspend fun question(id: Long): TimeQuestionEntity?

    /** Returns 1 only for the first answer; later attempts update nothing. */
    @Query(
        "UPDATE time_questions SET submittedAnswer = :answer, answeredAt = :answeredAt, " +
            "answeredLocalDate = :answeredLocalDate, isCorrect = :isCorrect " +
            "WHERE id = :id AND submittedAnswer IS NULL",
    )
    suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int

    @Query("UPDATE time_questions SET hintUsed = 1 WHERE id = :id")
    suspend fun markHintUsed(id: Long)

    @Query("UPDATE practice_sessions SET status = :status, finishedAt = :finishedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)

    @Query("UPDATE practice_sessions SET currentPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM practice_sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)

    @Query(
        "SELECT * FROM practice_sessions WHERE type = 'FREE' AND status = 'IN_PROGRESS' " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun activeFreeSession(): PracticeSessionEntity?

    @Query("SELECT COUNT(*) FROM time_questions WHERE sessionId = :sessionId AND submittedAnswer IS NOT NULL")
    suspend fun answeredCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM time_questions WHERE answeredLocalDate = :date")
    suspend fun answeredOn(date: String): Int

    @Query(
        "SELECT id FROM practice_sessions WHERE type = 'FREE' AND status != 'IN_PROGRESS' " +
            "ORDER BY startedAt DESC, id DESC LIMIT -1 OFFSET :keep",
    )
    suspend fun prunableFreeSessionIds(keep: Int): List<Long>

    @Query(
        "SELECT id FROM practice_sessions WHERE type = 'DAILY' " +
            "ORDER BY dailyDate DESC LIMIT -1 OFFSET :keep",
    )
    suspend fun prunableDailySessionIds(keep: Int): List<Long>

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM time_questions q WHERE q.sessionId = s.id AND q.submittedAnswer IS NOT NULL) AS answered, " +
            "(SELECT COUNT(*) FROM time_questions q WHERE q.sessionId = s.id AND q.isCorrect = 1) AS correct, " +
            "(SELECT COUNT(*) FROM time_questions q WHERE q.sessionId = s.id) AS total " +
            "FROM practice_sessions s ORDER BY s.startedAt DESC, s.id DESC",
    )
    suspend fun sessionSummaries(): List<SessionSummaryRow>

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM progress_totals WHERE id = 0")
    suspend fun totals(): ProgressTotalsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTotals(totals: ProgressTotalsEntity)

    @Query("DELETE FROM progress_totals")
    suspend fun deleteTotals()
}
