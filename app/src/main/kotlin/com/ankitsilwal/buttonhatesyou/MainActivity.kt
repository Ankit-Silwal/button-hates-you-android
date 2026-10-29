package com.ankitsilwal.buttonhatesyou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ankitsilwal.buttonhatesyou.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // the splash stays up while Preload loads the save, sounds and art
        installSplashScreen().setKeepOnScreenCondition { !Preload.ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // the UI is set only once warmed up: its drawing shares caches with the warm-up
        Preload.start(this) {
            runOnUiThread { if (!isDestroyed) setContent { App() } }
        }
    }
}
