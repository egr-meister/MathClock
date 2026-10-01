package com.mathclock.app

import android.content.Context
import com.mathclock.app.data.local.MathClockDatabase
import com.mathclock.app.data.local.PreferencesStore
import com.mathclock.app.data.repository.CalculatorRepository
import com.mathclock.app.data.repository.DataResetter
import com.mathclock.app.data.repository.PracticeRepository
import com.mathclock.app.data.repository.PracticeService
import com.mathclock.app.data.repository.RoomPracticeStore
import com.mathclock.app.domain.progress.Clock
import com.mathclock.app.domain.progress.DateProvider
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import kotlin.random.Random

/** Manual dependency injection. */
class AppContainer(context: Context) {
    private val io = Dispatchers.IO
    val clock: Clock = Clock { System.currentTimeMillis() }
    val dates: DateProvider = DateProvider { LocalDate.now() }

    val database: MathClockDatabase by lazy { MathClockDatabase.build(context) }
    val preferences: PreferencesStore by lazy { PreferencesStore(context) }

    val practice: PracticeRepository by lazy {
        PracticeRepository(PracticeService(RoomPracticeStore(database), Random.Default, clock, dates), io)
    }
    val calculator: CalculatorRepository by lazy { CalculatorRepository(database.calculationDao(), clock, io) }
    val resetter: DataResetter by lazy { DataResetter(database, preferences, practice, io) }
}
