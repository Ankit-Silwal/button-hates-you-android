package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.common.IconChip
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Meter
import com.ankitsilwal.buttonhatesyou.ui.common.fmt
import com.ankitsilwal.buttonhatesyou.ui.common.needColor
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlin.math.min
import kotlin.math.roundToInt
import com.ankitsilwal.buttonhatesyou.ui.art.AppIcon
import com.ankitsilwal.buttonhatesyou.ui.art.IconText

val TOP_BAR_H = 58.dp
val DOCK_H = 64.dp

/** Which room needs a look (the dock's red dot). */
fun alertOf(page: Page, n: NeedsState): Boolean = when (page) {
    Page.Kitchen -> n.hunger < 25
    Page.Bedroom -> n.energy < 25 && !n.asleep
    Page.Bath -> n.dirt >= 40
    else -> false
}

/** The room that fixes the lowest need (src/house/EnergyChip.jsx). */
fun fixFor(n: NeedsState): Page {
    val e = n.energy.toInt()
    val h = n.hunger.toInt()
    val clean = (100 - n.dirt).roundToInt()
    return when {
        clean < 25 && clean <= min(e, h) -> Page.Bath
        n.asleep || e <= h -> Page.Bedroom
        else -> Page.Kitchen
    }
}

/** Coins, diamonds, the needs chip, achievements, the daily chest and settings. */
@Composable
fun TopBar(
    wallet: Wallet,
    needs: NeedsState,
    achievementsReady: Int,
    chestReady: Boolean,
    onNeeds: () -> Unit,
    onAchievements: () -> Unit,
    onChest: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(TOP_BAR_H).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WalletChip(wallet.coins, wallet.diamonds)
        NeedsChip(needs, onNeeds)
        Spacer(Modifier.weight(1f))
        IconChip("🏆", if (achievementsReady > 0) "Achievements: $achievementsReady ready to claim" else "Achievements", onAchievements, badge = achievementsReady.takeIf { it > 0 }?.toString(), hot = achievementsReady > 0)
        IconChip("🎁", if (chestReady) "Daily chest: ready to open" else "Daily chest", onChest, badge = if (chestReady) "!" else null, hot = chestReady)
        IconChip("⚙️", "Settings", onSettings)
    }
}

@Composable
private fun frosted(): Modifier = Modifier
    .shadow(4.dp, RoundedCornerShape(50), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
    .background(Palette.Cream.copy(alpha = 0.92f), RoundedCornerShape(50))
    .border(1.dp, Color.White, RoundedCornerShape(50))

@Composable
private fun WalletChip(coins: Int, diamonds: Int) {
    Column(
        frosted().heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 3.dp).semantics { contentDescription = "$coins coins, $diamonds diamonds" },
        verticalArrangement = Arrangement.Center,
    ) {
        IconText("🪙 ${fmt(coins)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Clip)
        IconText("💎 ${fmt(diamonds)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 17.sp, color = Color(0xFF2E7FA8), maxLines = 1)
    }
}

@Composable
private fun NeedsChip(n: NeedsState, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    val e = n.energy.toFloat()
    val h = n.hunger.toFloat()
    val clean = (100 - n.dirt).toFloat()
    val label = if (n.asleep) "Asleep. Hunger ${h.toInt()}, clean ${clean.toInt()}" else "Hunger ${h.toInt()}, energy ${e.toInt()}, clean ${clean.toInt()}"
    Column(
        frosted()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Open the room that helps") { fb.tick(); onClick() }
            .semantics { contentDescription = label }
            .heightIn(min = 48.dp)
            .width(92.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        NeedLine("🍔", h)
        NeedLine(if (n.asleep) "💤" else "⚡", e)
        NeedLine("🫧", clean)
    }
}

@Composable
private fun NeedLine(icon: String, v: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, 11.dp)
        Spacer(Modifier.width(3.dp))
        Meter(v, needColor(v), Modifier.weight(1f), height = 6.dp)
    }
}

/** The five rooms along the bottom. */
@Composable
fun Dock(current: Int, needs: NeedsState, onGo: (Page) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(DOCK_H)
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .background(Palette.Cream.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Page.entries.forEachIndexed { i, p -> DockButton(p, i == current, alertOf(p, needs), Modifier.weight(1f)) { onGo(p) } }
    }
}

@Composable
private fun DockButton(p: Page, on: Boolean, alert: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    val bg by animateColorAsState(if (on) Palette.Mustard.copy(alpha = 0.55f) else Color.Transparent, label = "dock")
    val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "lift")
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(role = Role.Tab, onClickLabel = p.title) { fb.tick(); onClick() }
            .semantics { contentDescription = p.title + if (alert) ", needs attention" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(p.icon, 24.dp, tint = if (on) null else Palette.InkSoft, modifier = Modifier.graphicsLayer { translationY = -lift * 2.dp.toPx(); scaleX = 1f + 0.12f * lift; scaleY = 1f + 0.12f * lift })
            IconText(
                p.title.replace("Living Room", "Living"),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (on) Palette.Ink else Palette.InkSoft,
                maxLines = 1,
            )
        }
        if (alert) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .size(10.dp)
                    .background(Palette.Alert, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
            )
        }
    }
}
