package com.ankitsilwal.buttonhatesyou.ui.home

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.GameInfo
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.games.Arcade
import com.ankitsilwal.buttonhatesyou.games.Game
import com.ankitsilwal.buttonhatesyou.games.GameContext
import com.ankitsilwal.buttonhatesyou.games.GameSpec
import com.ankitsilwal.buttonhatesyou.games.Haptics
import com.ankitsilwal.buttonhatesyou.games.Sfx
import com.ankitsilwal.buttonhatesyou.games.WORLD_H
import com.ankitsilwal.buttonhatesyou.games.WORLD_W
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.fmt
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlin.math.max

/** The adventure games in menu order: the ones the arcade has first, then the ones still coming. */
fun gameList(): List<GameInfo> {
    val ready = Arcade.games.mapNotNull { s -> Catalog.GAME[s.id]?.takeIf { it.payout != null } }
    return ready + Catalog.ADVENTURE_GAMES.filter { g -> ready.none { it.id == g.id } }
}

/** The left panel: every game (src/hub/GamesPane.jsx). */
@Composable
fun GamesRoom(env: RoomEnv) {
    val needs = env.needs
    val hungry = needs.hunger < Needs.PLAY_MIN_HUNGER
    val tired = needs.energy < (Needs.PLAY_COST["adventure"] ?: 2.0)
    val note = when {
        needs.asleep -> "He's asleep. Wake him up in the Bedroom before he can play."
        hungry && tired -> "Too hungry and too tired to play. Feed him, then let him nap."
        hungry -> "Too hungry to play. Feed him in the Kitchen first."
        tired -> "Too tired to play. Let him nap in the Bedroom."
        else -> "Every run pays coins: the better you do, the more you earn."
    }
    val best = remember(env.wallet) { Achievements.statsOf(env.wallet).best }
    val games = remember { gameList() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8EDDC), Palette.Bg, Color(0xFFEFDDC6))))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = env.topInset + 8.dp, bottom = env.bottomInset + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Box(Modifier.size(84.dp, 96.dp), contentAlignment = Alignment.Center) {
                        Avatar(env.look, env.face, size = 58.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Games", style = MaterialTheme.typography.headlineMedium)
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                        if (needs.asleep || hungry || tired) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hungry && !needs.asleep) Pill("Kitchen", { env.go(Page.Kitchen) }, icon = "🍳", height = 48.dp)
                                if (tired || needs.asleep) Pill("Bedroom", { env.go(Page.Bedroom) }, icon = "🛏️", kind = PillKind.Alt, height = 48.dp)
                            }
                        }
                    }
                }
            }
            items(games, key = { it.id }) { g ->
                val spec = Arcade.byId(g.id)
                GameCard(g, spec, env.look, best[g.id] ?: 0) { env.play(g.id) }
            }
        }
    }
}

@Composable
private fun GameCard(g: GameInfo, spec: GameSpec?, look: Look, best: Int, onPlay: () -> Unit) {
    val fb = LocalFeedback.current
    val soon = spec == null
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(if (soon) 0.dp else 6.dp, RoundedCornerShape(22.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .clip(RoundedCornerShape(22.dp))
            .background(if (soon) Palette.Cream.copy(alpha = 0.6f) else Palette.Cream)
            .border(1.dp, Color.White, RoundedCornerShape(22.dp))
            .clickable(enabled = !soon, role = Role.Button, onClickLabel = "Play ${g.name}") { fb.tick(); onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 84.dp, height = 104.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(g.color)),
            contentAlignment = Alignment.Center,
        ) {
            if (spec != null) GameStill(spec, look) else Text(g.emoji, fontSize = 40.sp)
            if (spec != null) {
                Text(
                    g.emoji,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color(0xCCFFFAF2), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (soon) {
                    Spacer(Modifier.width(6.dp))
                    Text("COMING SOON", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFE9B8), modifier = Modifier.background(Color(0xFF3B2A20), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            Text(g.blurb, fontSize = 12.sp, lineHeight = 15.sp, color = Palette.InkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.prize, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A5A10), modifier = Modifier.background(Color(0xFFFFEFC4), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
                if (best > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("Best ${fmt(best)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
                }
            }
        }
        if (!soon) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(40.dp).background(Palette.Btn, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private object Quiet : Sfx, Haptics {
    override fun play(name: String) {}
    override fun tap() {}
    override fun thud() {}
}

/** A still frame of the game: one instance, stepped a moment, drawn once. */
@Composable
private fun GameStill(spec: GameSpec, look: Look) {
    val game: Game? = remember(spec.id) {
        try {
            spec.create(GameContext(look, Quiet, Quiet)).also { g -> repeat(20) { g.update(1f / 60f) } }
        } catch (e: Throwable) {
            Log.w("GamesRoom", "no still for ${spec.id}", e)
            null
        }
    }
    if (game == null) return
    Canvas(Modifier.fillMaxSize()) {
        // fill the card (crop the sides of the 480x800 world)
        val k = max(size.width / WORLD_W, size.height / WORLD_H)
        val ox = (size.width - WORLD_W * k) / 2
        val oy = (size.height - WORLD_H * k) / 2
        clipRect {
            withTransform({
                translate(ox, oy)
                scale(k, k, Offset.Zero)
            }) {
                try {
                    with(game) { draw() }
                } catch (e: Throwable) {
                    Log.w("GamesRoom", "still failed for ${spec.id}", e)
                }
            }
        }
    }
}
