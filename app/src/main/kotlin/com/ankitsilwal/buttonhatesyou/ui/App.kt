package com.ankitsilwal.buttonhatesyou.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// The whole app. For now: a placeholder until the rooms land.
@Composable
fun App() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFFF4E7D4)), contentAlignment = Alignment.Center) {
            Text("Please don't press anything.")
        }
    }
}
