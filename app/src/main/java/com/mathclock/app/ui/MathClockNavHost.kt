package com.mathclock.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mathclock.app.ui.board.BoardScreen
import com.mathclock.app.ui.calculator.CalculatorHistoryScreen
import com.mathclock.app.ui.calculator.CalculatorScreen
import com.mathclock.app.ui.clock.ExploreScreen
import com.mathclock.app.ui.history.HistoryDetailScreen
import com.mathclock.app.ui.history.HistoryScreen
import com.mathclock.app.ui.practice.PracticeScreen
import com.mathclock.app.ui.practice.PracticeSetupScreen
import com.mathclock.app.ui.practice.PracticeViewModel
import com.mathclock.app.ui.settings.PrivacyScreen
import com.mathclock.app.ui.settings.SettingsScreen

object Routes {
    const val BOARD = "board"
    const val EXPLORE = "explore"
    const val SETUP = "setup?topic={topic}"
    const val PRACTICE = "practice/{${PracticeViewModel.ARG_SESSION_ID}}"
    const val CALCULATOR = "calculator"
    const val CALC_HISTORY = "calculator/history"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{sessionId}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun setup(topic: String?) = if (topic == null) "setup" else "setup?topic=$topic"
    fun practice(id: Long) = "practice/$id"
    fun historyDetail(id: Long) = "history/$id"
}

private const val USE_VALUE_KEY = "calc_use_value"

/** Opens a practice session; the board stays as the only entry below it, so Back returns to the board. */
private fun NavHostController.openSession(id: Long) {
    navigate(Routes.practice(id)) {
        popUpTo(Routes.BOARD)
        launchSingleTop = true
    }
}

@Composable
fun MathClockNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.BOARD) {
        composable(Routes.BOARD) {
            BoardScreen(
                onExplore = { navController.navigate(Routes.EXPLORE) },
                onPracticeTopic = { topic -> navController.navigate(Routes.setup(topic?.name)) },
                onOpenSession = { id -> navController.openSession(id) },
                onCalculator = { navController.navigate(Routes.CALCULATOR) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EXPLORE) {
            ExploreScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.SETUP,
            arguments = listOf(navArgument("topic") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            PracticeSetupScreen(
                initialTopic = entry.arguments?.getString("topic"),
                onBack = { navController.popBackStack() },
                onStarted = { id -> navController.openSession(id) },
            )
        }
        composable(
            Routes.PRACTICE,
            arguments = listOf(navArgument(PracticeViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            PracticeScreen(
                onBack = { navController.popBackStack(Routes.BOARD, inclusive = false) },
                onOpenSession = { id -> navController.openSession(id) },
            )
        }
        composable(Routes.CALCULATOR) { entry ->
            val pending by entry.savedStateHandle.getStateFlow<String?>(USE_VALUE_KEY, null).collectAsStateWithLifecycle()
            CalculatorScreen(
                onBack = { navController.popBackStack() },
                onHistory = { navController.navigate(Routes.CALC_HISTORY) },
                pendingValue = pending,
                onPendingConsumed = { entry.savedStateHandle[USE_VALUE_KEY] = null },
            )
        }
        composable(Routes.CALC_HISTORY) {
            CalculatorHistoryScreen(
                onBack = { navController.popBackStack() },
                onUseResult = { value ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(USE_VALUE_KEY, value)
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.historyDetail(id)) },
            )
        }
        composable(
            Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            HistoryDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
