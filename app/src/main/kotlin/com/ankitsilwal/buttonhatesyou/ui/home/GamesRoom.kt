package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ankitsilwal.buttonhatesyou.data.Favorites
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
import com.ankitsilwal.buttonhatesyou.ui.art.GameArt
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
    val favorites = Favorites.get(LocalContext.current)
    val fb = LocalFeedback.current
    // starred games first, in the order they were starred; the rest keep the menu order
    val starred = favorites.ids.mapNotNull { id -> games.firstOrNull { it.id == id } }
    val rest = games.filter { it.id !in favorites }
    fun toggle(g: GameInfo) {
        fb.tick()
        favorites.toggle(g.id)
    }

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
            if (starred.isNotEmpty()) {
                item(key = "fav-head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead("Favorites", star = true, Modifier.animateItem())
                }
                items(starred, key = { it.id }) { g ->
                    GameTile(g, soon = false, dim = blocked, favorite = true, onStar = { toggle(g) }, modifier = Modifier.animateItem()) { env.play(g.id) }
                }
                item(key = "all-head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead("All games", star = false, Modifier.animateItem())
                }
            } else {
                item(key = "tip", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Tap the star on a game (or hold it) to pin it to the top.",
                        fontSize = 12.sp,
                        color = Palette.InkSoft,
                        modifier = Modifier.animateItem().padding(horizontal = 4.dp),
                    )
                }
            }
            items(rest, key = { it.id }) { g ->
                val soon = Arcade.byId(g.id) == null
                GameTile(g, soon = soon, dim = blocked, favorite = false, onStar = { toggle(g) }, modifier = Modifier.animateItem()) { env.play(g.id) }
            }
        }
    }
}

@Composable
private fun SectionHead(text: String, star: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (star) {
            Canvas(Modifier.size(16.dp)) { starShape(Color(0xFFF2B320), filled = true) }
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, color = Palette.InkSoft)
    }
}

/** One game in the grid: a picture of it and its name, with a star to pin it to the top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameTile(g: GameInfo, soon: Boolean, dim: Boolean, favorite: Boolean, onStar: () -> Unit, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    val fb = LocalFeedback.current
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (soon) 0.55f else if (dim) 0.8f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                enabled = !soon,
                role = Role.Button,
                onClickLabel = "Play ${g.name}",
                onLongClickLabel = if (favorite) "Remove from favorites" else "Add to favorites",
                onLongClick = onStar,
            ) { fb.tick(); onPlay() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .shadow(if (soon) 0.dp else 6.dp, RoundedCornerShape(22.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow),
        ) {
            GameArt(g.id, Modifier.fillMaxSize())
            if (!soon) StarButton(favorite, onStar, Modifier.align(Alignment.TopEnd).padding(4.dp))
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

/** The round star in a tile's corner: hollow, or filled gold once it's a favorite (with a little pop). */
@Composable
private fun StarButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on != seen && !reduce) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
        seen = on
    }
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = if (on) "Remove from favorites" else "Add to favorites", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .background(if (on) Color(0xF2FFFFFF) else Color(0x66000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) { starShape(if (on) Color(0xFFF2B320) else Color.White, filled = on) }
        }
    }
}

/** A five-pointed star filling the scope, solid or outlined. */
private fun DrawScope.starShape(color: Color, filled: Boolean) {
    val cx = size.width / 2
    val cy = size.height / 2 + size.height * 0.04f
    val outer = minOf(size.width, size.height) / 2
    val inner = outer * 0.48f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (filled) drawPath(path, color)
    else drawPath(path, color, style = Stroke(width = outer * 0.2f, join = StrokeJoin.Round))
}
