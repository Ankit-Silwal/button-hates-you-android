package com.ankitsilwal.buttonhatesyou.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.AchievementState
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Chest
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.common.IconChip
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.fmt
import com.ankitsilwal.buttonhatesyou.ui.dialogs.StreakDots
import com.ankitsilwal.buttonhatesyou.ui.dialogs.drawChest
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin
import com.ankitsilwal.buttonhatesyou.ui.art.IconText
import com.ankitsilwal.buttonhatesyou.ui.art.iconColor
import com.ankitsilwal.buttonhatesyou.ui.art.AppIcon
import com.ankitsilwal.buttonhatesyou.ui.art.GameIconTile

private val MEDAL_COLORS = listOf(Color(0xFFCD8B4E), Color(0xFFB9C2CC), Color(0xFFF2C14E))
private val LEVELS = Achievements.ACHIEVEMENTS.sumOf { it.levels.size }

/** Every achievement, by category (src/achievements/Achievements.jsx); claim levels for diamonds. */
@Composable
fun AchievementsScreen(store: ProfileStore, wallet: Wallet, look: Look, onBack: () -> Unit, onChest: () -> Unit) {
    BackHandler(onBack = onBack)
    val fb = LocalFeedback.current
    val states = remember(wallet) { Achievements.states(wallet) }
    val claimed = states.sumOf { it.level }
    val earned = states.sumOf { s -> s.a.levels.take(s.level).sumOf { it.diamonds } }
    val waiting = states.sumOf { it.claimable }
    val chest = remember(wallet) { store.chest() }
    var burst by remember { mutableStateOf<Pair<String, Int>?>(null) } // achievement id, diamonds
    var burstN by remember { mutableStateOf(0) }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8EDDC), Palette.Bg)))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 64.dp, bottom = insets.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(80.dp, 92.dp), contentAlignment = Alignment.Center) {
                        Avatar(look, Face(mood = if (waiting > 0) Mood.Annoyed else Mood.Smug), size = 56.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        IconText("Achievements", style = MaterialTheme.typography.headlineMedium)
                        IconText("Diamonds don't grow on trees. They grow on achievements. Hit a goal, then claim its medal for 💎.", fontSize = 13.sp, color = Palette.InkSoft)
                    }
                }
            }
            item(key = "overview") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("$claimed", "/$LEVELS", "medals", Modifier.weight(1f))
                    Stat("$earned", "/${Achievements.ACHIEVEMENT_DIAMONDS}", "💎 earned", Modifier.weight(1f))
                    Stat("$waiting", "", "ready to claim", Modifier.weight(1f), hot = waiting > 0)
                }
            }
            item(key = "chest") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Surface)
                        .border(if (chest.ready) 1.5.dp else 1.dp, if (chest.ready) Palette.Warn else Palette.Hairline, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { fb.tick(); onChest() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(72.dp, 60.dp)) { drawChest(if (chest.ready) 0f else 1f, 0f, chest.ready && Chest.isBigDay(chest.streak), 0f) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        IconText(if (chest.ready) "Daily chest ready!" else "Chest opened today", fontWeight = FontWeight.Bold)
                        StreakDots(chest.alive, today = false)
                    }
                }
            }
            for (cat in Achievements.CATEGORIES) {
                item(key = "cat-${cat.id}") {
                    Column(Modifier.padding(top = 8.dp)) {
                        IconText(cat.name, style = MaterialTheme.typography.titleLarge)
                        IconText(cat.blurb, fontSize = 13.sp, color = Palette.InkSoft)
                    }
                }
                items(states.filter { it.a.cat == cat.id }, key = { it.a.id }) { s ->
                    AchCard(s, burst?.takeIf { it.first == s.a.id }?.second, burstN) {
                        val got = store.claimAchievement(s.a.id, s.level + 1)
                        if (got != null) {
                            fb.thud()
                            fb.sound("win")
                            burst = s.a.id to got.diamonds
                            burstN++
                        }
                    }
                }
            }
        }
        // the top bar
        Row(
            Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding()).height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip("←", "Back home", onBack)
            Spacer(Modifier.width(8.dp))
            IconText("🪙 ${fmt(wallet.coins)}   💎 ${fmt(wallet.diamonds)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow).background(Palette.Surface, RoundedCornerShape(12.dp)).border(1.dp, Palette.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Stat(big: String, small: String, label: String, modifier: Modifier, hot: Boolean = false) {
    Column(
        modifier.background(Palette.Surface, RoundedCornerShape(14.dp)).border(1.dp, if (hot) Palette.Btn.copy(alpha = 0.5f) else Palette.Hairline, RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            IconText(big, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (hot) Palette.BtnDark else Palette.Ink)
            IconText(small, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Palette.InkSoft, modifier = Modifier.padding(bottom = 3.dp))
        }
        IconText(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.InkSoft)
    }
}

@Composable
private fun AchCard(s: AchievementState, burstDiamonds: Int?, burstN: Int, onClaim: () -> Unit) {
    val a = s.a
    val done = s.next == null
    val shown = s.next ?: a.levels.last()
    val from = if (s.level > 0) a.levels[s.level - 1].at else 0
    val pct = if (done) 1f else ((s.value - from).toFloat() / (shown.at - from).coerceAtLeast(1)).coerceIn(0f, 1f)
    val medal = Achievements.MEDALS[minOf(s.level, Achievements.MEDALS.size - 1)]
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(if (s.claimable > 0) 4.dp else 1.dp, RoundedCornerShape(16.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                .background(Palette.Surface, RoundedCornerShape(16.dp))
                .border(if (s.claimable > 0) 1.5.dp else 1.dp, if (s.claimable > 0) Palette.Btn.copy(alpha = 0.55f) else Palette.Hairline, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // a game's medal wears the game's icon; the rest their own, on a soft badge
                val game = a.game
                if (game != null) GameIconTile(game, Modifier.size(44.dp), corner = 14.dp)
                else Box(Modifier.size(44.dp).background(iconColor(a.icon).copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    AppIcon(a.icon, 26.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    IconText(a.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconText(if (done) "All three medals. Show-off." else a.goal(shown.at), fontSize = 12.sp, color = Palette.InkSoft)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in Achievements.MEDALS.indices) {
                        val got = i < s.level
                        val ready = !got && i < s.level + s.claimable
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(if (got) MEDAL_COLORS[i] else if (ready) MEDAL_COLORS[i].copy(alpha = 0.45f) else Color(0x14000000), CircleShape)
                                .border(if (ready) 2.dp else 0.dp, if (ready) MEDAL_COLORS[i] else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconText("★", fontSize = 13.sp, color = if (got) Color.White else Color(0x55000000))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.ankitsilwal.buttonhatesyou.ui.common.Meter(pct * 100f, if (done) Color(0xFF7CC08A) else Palette.Mustard, Modifier.weight(1f), height = 10.dp)
                Spacer(Modifier.width(8.dp))
                IconText("${fmt(minOf(s.value, shown.at))} / ${fmt(shown.at)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    IconText("✓ Complete", fontWeight = FontWeight.Bold, color = Color(0xFF2F6B3A), modifier = Modifier.weight(1f))
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(MEDAL_COLORS[Achievements.MEDALS.indexOf(medal)], CircleShape))
                        Spacer(Modifier.width(6.dp))
                        IconText("${medal.name} · +${s.next!!.diamonds} 💎", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (s.claimable > 0) Pill("Claim", onClaim, height = 46.dp)
            }
        }
        if (burstDiamonds != null) Burst(burstDiamonds, burstN, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Burst(diamonds: Int, n: Int, modifier: Modifier) {
    val p = remember(n) { Animatable(0f) }
    LaunchedEffect(n) { p.animateTo(1f, tween(1200)) }
    if (p.value >= 1f) return
    Box(modifier.size(160.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until 10) {
            val ang = Math.toRadians(i * 36.0)
            val d = (60 + (i % 3) * 18) * p.value
            IconText(
                if (i % 2 == 1) "💎" else "✦",
                fontSize = 16.sp,
                color = Palette.Mustard,
                modifier = Modifier.graphicsLayer {
                    translationX = (cos(ang) * d).toFloat() * density
                    translationY = (sin(ang) * d).toFloat() * density
                    alpha = 1f - p.value
                },
            )
        }
        IconText(
            "+$diamonds 💎",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7FA8),
            modifier = Modifier.graphicsLayer {
                translationY = -40f * p.value * density
                alpha = 1f - p.value * p.value
                scaleX = 0.8f + 0.4f * p.value; scaleY = 0.8f + 0.4f * p.value
            },
        )
    }
}
