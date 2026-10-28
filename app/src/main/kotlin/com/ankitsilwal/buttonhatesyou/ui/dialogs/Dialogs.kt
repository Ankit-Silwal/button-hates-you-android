package com.ankitsilwal.buttonhatesyou.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.data.OpenedChest
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Chest
import com.ankitsilwal.buttonhatesyou.economy.ChestLoot
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.Lines
import com.ankitsilwal.buttonhatesyou.ui.common.AppSettings
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Meter
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.needColor
import com.ankitsilwal.buttonhatesyou.ui.common.rememberNow
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/** A cream card that springs in. */
@Composable
fun PopCard(onDismiss: () -> Unit, dismissable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0.7f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = dismissable, dismissOnBackPress = true, usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .background(Palette.Cream, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/* ------------------------------ the energy gate ------------------------------ */

/**
 * He refuses to play (src/house/EnergyGate.jsx): asleep, tired or hungry,
 * with a jump to the room that fixes it.
 */
@Composable
fun GateDialog(why: String, look: Look, store: ProfileStore, onKitchen: () -> Unit, onBedroom: () -> Unit, onClose: () -> Unit) {
    val line = remember(why) { Lines.pick(Lines.GATE[why] ?: Lines.GATE.getValue("tired")) }
    val now = store.currentNeeds()
    val energy = now.energy.toFloat()
    val hunger = now.hunger.toFloat()
    PopCard(onClose) {
        Box(Modifier.size(120.dp, 130.dp), contentAlignment = Alignment.Center) {
            Avatar(look, Face(mood = if (why == "hungry") Mood.Bored else if (why == "asleep") Mood.Sleep else Mood.Tired, sleepy = why != "hungry"), size = 80.dp, legs = false)
        }
        Text(line, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        GateMeter("⚡", energy)
        GateMeter("🍔", hunger)
        Text(
            (if (why == "asleep") "He is in bed. " else "") + "A game needs ${(Needs.PLAY_COST["adventure"] ?: 2.0).toInt()} energy and a tummy that is at least ${Needs.PLAY_MIN_HUNGER.toInt()} full. Food is cheap, naps are free.",
            fontSize = 13.sp, color = Palette.InkSoft, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (why == "hungry") Pill("Kitchen", onKitchen, icon = "🍳") else Pill("Bedroom", onBedroom, icon = "🛏️")
            Pill("Fine", onClose, kind = PillKind.Ghost)
        }
    }
}

@Composable
private fun GateMeter(icon: String, v: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Meter(v, needColor(v), Modifier.weight(1f), height = 12.dp)
        Spacer(Modifier.width(8.dp))
        Text("${v.toInt()}", fontWeight = FontWeight.ExtraBold)
    }
}

/* ------------------------------ the daily chest ------------------------------ */

private enum class ChestPhase { Closed, Shaking, Open }

private fun untilText(t: Long, now: Long): String {
    val m = max(1, ceil((t - now) / 60_000.0).toInt())
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private data class LootLine(val key: String, val icon: String, val text: String)

private fun lootList(l: ChestLoot): List<LootLine> {
    val out = mutableListOf(LootLine("coins", "🪙", "${l.coins} coins"))
    l.food?.let { out.add(LootLine("food", "🍪", "${House.FOOD[it]?.name ?: "A snack"} (free, in the fridge)")) }
    if (l.energy) out.add(LootLine("energy", "⚡", "Full energy"))
    if (l.diamonds > 0) out.add(LootLine("diamonds", "💎", "${l.diamonds} diamond${if (l.diamonds > 1) "s" else ""}!"))
    return out
}

/** A row of 7 dots: where today falls in the week's streak (day 7 is the big one). */
@Composable
fun StreakDots(streak: Int, today: Boolean) {
    val inWeek = if (streak > 0) ((streak - 1) % Chest.bigEvery) + 1 else 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.semantics { contentDescription = "Streak: $streak day${if (streak == 1) "" else "s"}" }) {
        for (i in 0 until Chest.bigEvery) {
            val on = i < inWeek
            val big = i == Chest.bigEvery - 1
            Box(
                Modifier
                    .size(if (big) 16.dp else 12.dp)
                    .background(if (on) (if (i == inWeek - 1 && today) Palette.Btn else Palette.Mustard) else Color(0x22000000), CircleShape),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("🔥 $streak", fontWeight = FontWeight.ExtraBold)
    }
}

/** The chest itself: a wooden box with a lid and a lock; [open] 0..1 lifts the lid, [shake] rattles it. */
fun DrawScope.drawChest(open: Float, shake: Float, big: Boolean, t: Float) {
    val w = size.width
    val h = size.height
    val bw = w * 0.7f
    val bh = h * 0.42f
    val bx = (w - bw) / 2
    val by = h * 0.5f
    val wood = if (big) Color(0xFF8E3B4E) else Color(0xFFB8854F)
    val woodD = if (big) Color(0xFF652635) else Color(0xFF8F6335)
    val band = if (big) Color(0xFFF2C14E) else Color(0xFFE6B422)
    // rays
    if (open > 0f) {
        rotate(t * 20f, Offset(w / 2, by)) {
            for (i in 0 until 12) {
                rotate(i * 30f, Offset(w / 2, by)) {
                    drawRect(Color(0xFFFFE9A8).copy(alpha = 0.35f * open), Offset(w / 2 - 6f, by - h * 0.6f), Size(12f, h * 0.6f))
                }
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xCCFFF3B0), Color.Transparent), Offset(w / 2, by), w * 0.45f), w * 0.45f, Offset(w / 2, by), alpha = open)
    }
    rotate(sin(t * 40f) * 6f * shake, Offset(w / 2, by + bh)) {
        drawOval(Color(0x33000000), Offset(bx - 6f, by + bh - 8f), Size(bw + 12f, 22f))
        drawRoundRect(woodD, Offset(bx, by + 6f), Size(bw, bh), CornerRadius(12f))
        drawRoundRect(wood, Offset(bx, by), Size(bw, bh - 6f), CornerRadius(12f))
        for (x in listOf(0.18f, 0.82f)) drawRect(band, Offset(bx + bw * x - 6f, by), Size(12f, bh - 6f))
        // the lid, swinging up
        val lh = h * 0.2f
        rotate(-80f * open, Offset(bx + bw, by)) {
            drawRoundRect(woodD, Offset(bx - 4f, by - lh + 4f), Size(bw + 8f, lh), CornerRadius(26f, 26f))
            drawRoundRect(wood, Offset(bx - 4f, by - lh), Size(bw + 8f, lh), CornerRadius(26f, 26f))
            for (x in listOf(0.18f, 0.82f)) drawRect(band, Offset(bx + bw * x - 6f, by - lh), Size(12f, lh))
        }
        // the lock
        if (open < 0.5f) {
            drawRoundRect(band, Offset(w / 2 - 14f, by - 10f), Size(28f, 30f), CornerRadius(6f))
            drawCircle(woodD, 4f, Offset(w / 2, by + 4f))
        }
    }
}

/** The daily chest (src/achievements/Chest.jsx): tap, it shakes, the lid pops, the loot flies out. */
@Composable
fun ChestDialog(store: ProfileStore, wallet: Wallet, onClose: () -> Unit) {
    val now = rememberNow(30_000)
    val chest = remember(wallet, now) { store.chest() }
    val fb = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(ChestPhase.Closed) }
    var loot by remember { mutableStateOf<OpenedChest?>(null) }
    val shake = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(10_000f, tween(10_000_000, easing = { it })) }

    fun open() {
        if (phase != ChestPhase.Closed || !chest.ready) return
        phase = ChestPhase.Shaking
        fb.tick()
        scope.launch {
            shake.animateTo(1f, tween(150))
            delay(600)
            val got = store.openChest()
            shake.animateTo(0f, tween(100))
            if (got == null) { phase = ChestPhase.Closed; return@launch }
            loot = got
            phase = ChestPhase.Open
            fb.thud()
            fb.sound("win")
            lid.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val big = loot?.loot?.big ?: Chest.isBigDay(chest.streak)
    PopCard(onClose) {
        Text(if (big) "DAY 7: THE BIG CHEST" else "DAILY CHEST", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Palette.InkSoft)
        Box(
            Modifier
                .size(200.dp, 170.dp)
                .clickable(enabled = phase == ChestPhase.Closed && chest.ready, role = Role.Button, onClickLabel = "Open the chest") { open() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(200.dp, 170.dp)) {
                drawChest(lid.value, shake.value, big, t.value)
            }
            if (phase == ChestPhase.Open) loot?.let { FlyingLoot(lootList(it.loot)) }
        }
        val l = loot
        when {
            phase == ChestPhase.Open && l != null -> {
                Text(if (l.loot.diamonds > 0) "Ooh, shiny." else if (l.loot.big) "Jackpot. He is furious." else "Free stuff. He hates that.", style = MaterialTheme.typography.headlineSmall)
                lootList(l.loot).forEachIndexed { i, it -> LootRow(it, i) }
                StreakDots(l.streak, today = true)
                Pill("Nice", onClose)
            }
            chest.ready -> {
                Text(if (phase == ChestPhase.Shaking) "Opening…" else "Tap the chest", style = MaterialTheme.typography.headlineSmall)
                Text(
                    (if (chest.streak > 1) "Day ${chest.streak} in a row. " else "") +
                        if (Chest.isBigDay(chest.streak)) "A big one: coins, a fancy snack, energy and a diamond." else "Come back ${Chest.bigEvery} days in a row for a big one.",
                    textAlign = TextAlign.Center, color = Palette.InkSoft,
                )
                StreakDots(chest.alive, today = false)
            }
            else -> {
                Text("Already opened today", style = MaterialTheme.typography.headlineSmall)
                Text("The next one is ready in ${untilText(chest.next, now)}.", color = Palette.InkSoft, textAlign = TextAlign.Center)
                StreakDots(chest.alive, today = false)
                Pill("Close", onClose, kind = PillKind.Ghost)
            }
        }
    }
}

@Composable
private fun FlyingLoot(items: List<LootLine>) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1100)) }
    val all = items + items
    Box(Modifier.size(200.dp, 170.dp)) {
        all.forEachIndexed { i, it ->
            val a = Math.toRadians((-150.0 + i * 300.0 / max(1, all.size - 1)) - 90.0 + 90.0)
            val d = 90f * p.value
            Text(
                it.icon,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = (kotlin.math.sin(a) * d).toFloat() * density
                        translationY = (-kotlin.math.cos(a) * d).toFloat() * density + p.value * p.value * 40f * density
                        alpha = 1f - p.value * 0.6f
                    },
            )
        }
    }
}

@Composable
private fun LootRow(it: LootLine, i: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150L * i + 200); a.animateTo(1f, spring(dampingRatio = 0.5f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = a.value; translationX = (1f - a.value) * 40f }
            .background(if (it.key == "diamonds") Color(0xFFDDF1FB) else Color(0xFFFFF3DD), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(it.icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(it.text, fontWeight = FontWeight.Bold)
    }
}

/* -------------------------------- settings -------------------------------- */

@Composable
fun SettingsDialog(settings: AppSettings, onReset: () -> Unit, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    PopCard(onClose) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingRow("🔊", "Sound", settings.sound) { settings.changeSound(it) }
        SettingRow("📳", "Vibration", settings.vibration) { settings.changeVibration(it) }
        Spacer(Modifier.height(4.dp))
        Pill("Reset progress", { confirm = true }, icon = "🗑️", kind = PillKind.Ghost)
        Pill("Done", onClose)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset everything?") },
            text = { Text("Coins, diamonds, clothes, furniture, food and achievements all go. He will not remember you. He will be thrilled.") },
            confirmButton = { TextButton({ confirm = false; onReset() }) { Text("Reset", color = Palette.Alert, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Keep my stuff") } },
            containerColor = Palette.Cream,
        )
    }
}

@Composable
private fun SettingRow(icon: String, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val fb = LocalFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Palette.Wall, RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!on); fb.tick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(on, { onChange(it); fb.tick() }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Btn))
    }
}
