package com.ankitsilwal.buttonhatesyou.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Look

// Every arcade game, in menu order. Ids match the web version.
object Arcade {
    val games: List<GameSpec> = listOf(
        GameSpec("flap") { FlapGame(it) },
        GameSpec("catch") { CatchGame(it) },
    )

    // Display names, as on the web.
    val titles: Map<String, String> = mapOf(
        "flap" to "Flappy Regret",
        "catch" to "Snack Attack",
        "whack" to "Whack-a-Button",
        "stack" to "Tower of Regret",
        "pop" to "Pop Tantrum",
    )

    fun byId(id: String): GameSpec? = games.firstOrNull { it.id == id }
}

// Dev-only: a list of the games; tap one to play it in GameHost. Not wired
// into the app (the UI wires the real screens).
@Composable
fun GameDebugScreen(look: Look = Look()) {
    var playing by remember { mutableStateOf<GameSpec?>(null) }
    var runId by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf("") }
    var continues by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    val spec = playing
    if (spec != null) {
        BackHandler { playing = null }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.runtime.key(runId) {
                GameHost(
                    spec = spec,
                    look = look,
                    paused = paused,
                    onOver = { score, coins, ms ->
                        last = "${Arcade.titles[spec.id]}: score $score, coins $coins, ${ms / 1000}s, continues $continues"
                        playing = null
                    },
                    onContinueRequest = {
                        // the debug screen grants one free Continue per run
                        if (continues < 1) {
                            continues++
                            true
                        } else {
                            false
                        }
                    },
                )
            }
            Text(
                if (paused) "Resume" else "Pause",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .clickable { paused = !paused }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1024))
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Arcade (debug)", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (last.isNotEmpty()) Text(last, color = Color(0xFFFFD8A8))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Arcade.games, key = { it.id }) { g ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF34204A), RoundedCornerShape(14.dp))
                        .clickable {
                            continues = 0
                            paused = false
                            runId++
                            playing = g
                        }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Arcade.titles[g.id] ?: g.id, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(g.id, color = Color(0xFFB9A6D0))
                }
            }
        }
    }
}
