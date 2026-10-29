package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.GameInfo
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.games.Arcade
import com.ankitsilwal.buttonhatesyou.ui.art.GameIconTile
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette

/** Please Don't first (the original game), then the adventure games in menu order: the ones the arcade has, then the ones still coming. */
fun gameList(): List<GameInfo> {
    val ready = Arcade.games.mapNotNull { s -> Catalog.GAME[s.id]?.takeIf { it.payout != null } }
    val story = listOfNotNull(Catalog.GAME[Arcade.pleaseDont.id])
    return story + ready + Catalog.ADVENTURE_GAMES.filter { g -> ready.none { it.id == g.id } }
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
    val games = remember { gameList() }
    val blocked = needs.asleep || hungry || tired

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8EDDC), Palette.Bg, Color(0xFFEFDDC6))))) {
        // starts under the top bar (and is clipped there), so tiles never scroll behind the chips
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize().padding(top = env.topInset),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = env.bottomInset + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Box(Modifier.size(84.dp, 96.dp), contentAlignment = Alignment.Center) {
                        Avatar(env.look, env.face, size = 58.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Games", style = MaterialTheme.typography.headlineMedium)
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                        if (blocked) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (hungry && !needs.asleep) Pill("Kitchen", { env.go(Page.Kitchen) }, icon = "🍳", height = 48.dp)
                                if (tired || needs.asleep) Pill("Bedroom", { env.go(Page.Bedroom) }, icon = "🛏️", kind = PillKind.Alt, height = 48.dp)
                            }
                        }
                    }
                }
            }
            items(games, key = { it.id }) { g ->
                GameTile(g, soon = Arcade.byId(g.id) == null, dim = blocked) { env.play(g.id) }
            }
        }
    }
}

/** One game in the grid: its icon tile and its name. */
@Composable
private fun GameTile(g: GameInfo, soon: Boolean, dim: Boolean, onPlay: () -> Unit) {
    val fb = LocalFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (soon) 0.55f else if (dim) 0.8f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !soon, role = Role.Button, onClickLabel = "Play ${g.name}") { fb.tick(); onPlay() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(if (soon) 0.dp else 6.dp, RoundedCornerShape(22.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow),
        ) {
            GameIconTile(g.id, Modifier.fillMaxSize())
            if (soon) {
                Text(
                    "SOON",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE9B8),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).background(Color(0xFF3B2A20), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            g.name,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
