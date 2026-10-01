package com.mathclock.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mathclock.app.AppContainer
import com.mathclock.app.MathClockApplication

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as MathClockApplication).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
