package com.mathclock.app.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathclock.app.data.repository.LoadedSession
import com.mathclock.app.data.repository.toExercise
import com.mathclock.app.domain.generation.ExerciseText
import com.mathclock.app.domain.generation.Topic
import com.mathclock.app.domain.progress.Scoring
import com.mathclock.app.domain.progress.SessionStatus
import com.mathclock.app.domain.progress.SessionType
import com.mathclock.app.ui.clock.AnalogClock
import com.mathclock.app.ui.theme.ClassroomColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatDateTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.US)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US).format(date)

fun statusLabel(session: com.mathclock.app.data.local.PracticeSessionEntity, today: LocalDate?): String {
    val status = SessionStatus.valueOf(session.status)
    return when {
        status == SessionStatus.COMPLETED -> "Completed"
        status == SessionStatus.ENDED_EARLY -> "Ended early"
        session.type == SessionType.DAILY.name && today != null && session.dailyDate != today.toString() -> "Incomplete"
        else -> "In progress"
    }
}

@Composable
fun SessionReview(session: LoadedSession, today: LocalDate?, modifier: Modifier = Modifier) {
    val s = session.session
    val format = session.timeFormat
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val title = when (session.type) {
            SessionType.DAILY -> "Today's Practice" + (session.dailyDate?.let { " · ${formatDate(it)}" } ?: "")
            SessionType.FREE -> Topic.entries.firstOrNull { it.name == s.topicSelection }?.title ?: "Mixed Practice"
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Surface(
            color = ClassroomColors.NotebookPaper,
            border = BorderStroke(1.dp, ClassroomColors.NotebookLine),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Scoring.accuracyLabel(session.answeredCount, session.correctCount),
                    style = MaterialTheme.typography.displaySmall,
                    color = ClassroomColors.Navy,
                )
                Text("${session.correctCount} correct out of ${session.answeredCount} answered (${session.questions.size} questions)")
                Text("Status: ${statusLabel(s, today)}")
                Text("Started: ${formatDateTime(s.startedAt)}")
                Text("Difficulty: ${session.difficulty.label} · Format: ${format.label}")
                Text(
                    "Unanswered questions are not counted as mistakes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClassroomColors.TextMuted,
                )
            }
        }
        Text("By topic", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        session.topicScores().filter { (t, _) -> session.questions.any { it.topic == t.name } }.forEach { (topic, score) ->
            Row(Modifier.fillMaxWidth()) {
                Text(topic.title, modifier = Modifier.weight(1f))
                Text(
                    if (score.answered == 0) "No answers yet" else "${score.correct}/${score.answered} · ${Scoring.accuracyLabel(score.answered, score.correct)}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text("Questions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        session.questions.forEach { q ->
            val ex = q.toExercise()
            val answered = q.submittedAnswer != null
            val correct = q.isCorrect == true
            Surface(
                color = ClassroomColors.ClockFace,
                border = BorderStroke(
                    1.5.dp,
                    when {
                        !answered -> ClassroomColors.ClockFrame
                        correct -> ClassroomColors.Correct
                        else -> ClassroomColors.Incorrect
                    },
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${q.position + 1}. ${ex.topic.title}" + when {
                            !answered -> " — not answered"
                            correct -> " — ✓ correct"
                            else -> " — ✗ not correct"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = ClassroomColors.TextMuted,
                    )
                    if (ex.topic == Topic.READ_CLOCK) {
                        AnalogClock(
                            minutes = ex.correctAnswer,
                            description = "Question clock",
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    }
                    Text(ExerciseText.prompt(ex, format), style = MaterialTheme.typography.titleMedium)
                    if (answered) {
                        Text("Your answer: ${ExerciseText.answerLabel(ex, q.submittedAnswer ?: 0, format)}")
                    }
                    // Unanswered questions of an unfinished set keep their answer hidden.
                    if (answered || session.status != SessionStatus.IN_PROGRESS) {
                        Text("Correct answer: ${ExerciseText.answerLabel(ex, ex.correctAnswer, format)}", fontWeight = FontWeight.SemiBold)
                    }
                    if (q.hintUsed) Text("Hint used", style = MaterialTheme.typography.bodyMedium, color = ClassroomColors.TextMuted)
                    if (answered) Text(ExerciseText.explanation(ex, format), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
