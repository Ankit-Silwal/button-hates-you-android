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

private val ChipShape = RoundedCornerShape(12.dp)

@Composable
private fun frosted(): Modifier = Modifier
    .shadow(2.dp, ChipShape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
    .background(Palette.Surface.copy(alpha = 0.94f), ChipShape)
    .border(1.dp, Palette.Hairline, ChipShape)

@Composable
private fun WalletChip(coins: Int, diamonds: Int) {
    Column(
        frosted().heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "$coins coins, $diamonds diamonds" },
        verticalArrangement = Arrangement.Center,
    ) {
        IconText("🪙 ${fmt(coins)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Clip)
        IconText("💎 ${fmt(diamonds)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, color = Palette.InkSoft, maxLines = 1)
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
            .clip(ChipShape)
            .clickable(role = Role.Button, onClickLabel = "Open the room that helps") { fb.tick(); onClick() }
            .semantics { contentDescription = label }
            .heightIn(min = 48.dp)
            .width(88.dp)
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
        AppIcon(icon, 11.dp, tint = Palette.InkSoft)
        Spacer(Modifier.width(5.dp))
        Meter(v, needColor(v), Modifier.weight(1f), height = 4.dp)
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
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
            .border(1.dp, Palette.Hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Page.entries.forEachIndexed { i, p -> DockButton(p, i == current, alertOf(p, needs), Modifier.weight(1f)) { onGo(p) } }
    }
}

// A Material-style tab: a soft indicator behind the icon, the label beneath.
@Composable
private fun DockButton(p: Page, on: Boolean, alert: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    val indicator by animateColorAsState(if (on) Palette.Btn.copy(alpha = 0.12f) else Color.Transparent, label = "dock")
    val tint by animateColorAsState(if (on) Palette.BtnDark else Palette.InkSoft, label = "dockTint")
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClickLabel = p.title) { fb.tick(); onClick() }
            .semantics { contentDescription = p.title + if (alert) ", needs attention" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(48.dp, 28.dp).background(indicator, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                AppIcon(p.icon, 20.dp, tint = tint)
                if (alert) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 8.dp)
                            .size(8.dp)
                            .background(Palette.Bad, CircleShape)
                            .border(1.5.dp, Palette.Surface, CircleShape),
                    )
                }
            }
            IconText(
                p.title.replace("Living Room", "Living"),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                color = if (on) Palette.Ink else Palette.InkSoft,
                maxLines = 1,
            )
        }
    }
}
