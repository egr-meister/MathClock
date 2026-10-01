package com.mathclock.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Decimals are stored as canonical strings (BigDecimal.stripTrailingZeros().toPlainString()). */
@Entity(tableName = "calculations", indices = [Index(value = ["createdAt"])])
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leftOperand: String,
    val operator: String,
    val rightOperand: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "practice_sessions",
    indices = [
        Index(value = ["dailyDate"], unique = true),
        Index(value = ["type", "status"]),
    ],
)
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SessionType name. */
    val type: String,
    /** ISO local date (yyyy-MM-dd) for daily sets; null for free practice. Unique when non-null. */
    val dailyDate: String?,
    /** Topic name, "MIXED", or "DAILY". */
    val topicSelection: String,
    val difficulty: String,
    val timeFormat: String,
    val startedAt: Long,
    val finishedAt: Long?,
    /** SessionStatus name. */
    val status: String,
    /** Question position currently shown (0-based), restored after process recreation. */
    val currentPosition: Int = 0,
)

@Entity(
    tableName = "time_questions",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "position"], unique = true),
        Index(value = ["answeredLocalDate"]),
    ],
)
data class TimeQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    /** Topic name. */
    val topic: String,
    val startMinutes: Int,
    val endMinutes: Int?,
    val durationMinutes: Int?,
    val targetMinutes: Int?,
    val crossesMidnight: Boolean,
    /** Comma-separated option values in display order (empty for Set the Clock). */
    val answerOptions: String,
    val submittedAnswer: Int?,
    val answeredAt: Long?,
    /** ISO local date at the moment of answering; never rewritten (time-zone changes don't move totals). */
    val answeredLocalDate: String?,
    val isCorrect: Boolean?,
    val hintUsed: Boolean,
)

@Entity(tableName = "progress_totals")
data class ProgressTotalsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val answeredCount: Int,
    val correctCount: Int,
    val completedSessionCount: Int,
    /** "TOPIC=answered/correct;..." */
    val perTopicCounts: String,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

data class SessionSummaryRow(
    @Embedded val session: PracticeSessionEntity,
    val answered: Int,
    val correct: Int,
    val total: Int,
)
