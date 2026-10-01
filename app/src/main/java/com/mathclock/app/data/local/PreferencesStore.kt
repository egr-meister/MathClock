package com.mathclock.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mathclock.app.domain.generation.Difficulty
import com.mathclock.app.domain.time.TimeFormat
import com.mathclock.app.domain.time.TimeMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val defaultDifficulty: Difficulty = Difficulty.EASY,
    val timeFormat: TimeFormat = TimeFormat.H24,
    val showMinuteLabels: Boolean = false,
    val reducedMotion: Boolean = false,
    /** Explore-the-clock value; 09:00 on first use. */
    val exploredMinutes: Int = DEFAULT_EXPLORED,
    /** Last topic chosen in free practice ("MIXED" or a Topic name). */
    val practiceTopic: String = "MIXED",
) {
    companion object {
        val DEFAULT_EXPLORED = TimeMath.of(9, 0)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mathclock_prefs")

class PreferencesStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val difficulty = stringPreferencesKey("default_difficulty")
        val timeFormat = stringPreferencesKey("time_format")
        val minuteLabels = booleanPreferencesKey("show_minute_labels")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val explored = intPreferencesKey("explored_minutes")
        val practiceTopic = stringPreferencesKey("practice_topic")
        val calculatorDraft = stringPreferencesKey("calculator_draft")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            defaultDifficulty = p[Keys.difficulty]?.let { v -> Difficulty.entries.firstOrNull { it.name == v } } ?: Difficulty.EASY,
            timeFormat = p[Keys.timeFormat]?.let { v -> TimeFormat.entries.firstOrNull { it.name == v } } ?: TimeFormat.H24,
            showMinuteLabels = p[Keys.minuteLabels] ?: false,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            exploredMinutes = p[Keys.explored]?.let(TimeMath::normalize) ?: AppSettings.DEFAULT_EXPLORED,
            practiceTopic = p[Keys.practiceTopic] ?: "MIXED",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setDifficulty(d: Difficulty) = store.edit { it[Keys.difficulty] = d.name }
    suspend fun setTimeFormat(f: TimeFormat) = store.edit { it[Keys.timeFormat] = f.name }
    suspend fun setShowMinuteLabels(v: Boolean) = store.edit { it[Keys.minuteLabels] = v }
    suspend fun setReducedMotion(v: Boolean) = store.edit { it[Keys.reducedMotion] = v }
    suspend fun setExploredMinutes(m: Int) = store.edit { it[Keys.explored] = TimeMath.normalize(m) }
    suspend fun setPracticeTopic(t: String) = store.edit { it[Keys.practiceTopic] = t }

    val calculatorDraft: Flow<String?> = store.data.map { it[Keys.calculatorDraft] }
    suspend fun setCalculatorDraft(encoded: String) = store.edit { it[Keys.calculatorDraft] = encoded }
    suspend fun clearCalculatorDraft() = store.edit { it.remove(Keys.calculatorDraft) }

    suspend fun clearAll() = store.edit { it.clear() }
}
