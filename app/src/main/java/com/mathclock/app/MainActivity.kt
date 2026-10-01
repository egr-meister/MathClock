package com.mathclock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathclock.app.data.local.AppSettings
import com.mathclock.app.ui.MathClockNavHost
import com.mathclock.app.ui.theme.LocalReducedMotion
import com.mathclock.app.ui.theme.MathClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Matching launch screen, dismissed as soon as the first frame is ready (no artificial delay).
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MathClockApplication).container
        setContent {
            val settings by container.preferences.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CompositionLocalProvider(LocalReducedMotion provides settings.reducedMotion) {
                MathClockTheme {
                    // Horizontal display-cutout insets are applied once here; each screen handles
                    // system bars itself (Scaffold / safeDrawing), so content never sits under them.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                    ) {
                        MathClockNavHost()
                    }
                }
            }
        }
    }
}
