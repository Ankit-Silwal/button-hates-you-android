package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.ui.Lines
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.NeedHud
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.PillRow
import com.ankitsilwal.buttonhatesyou.ui.common.rememberNow
import com.ankitsilwal.buttonhatesyou.ui.room.Eating
import com.ankitsilwal.buttonhatesyou.ui.room.LampButton
import com.ankitsilwal.buttonhatesyou.ui.room.RoomKind
import com.ankitsilwal.buttonhatesyou.ui.room.RoomScene
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import com.ankitsilwal.buttonhatesyou.ui.art.IconText

/** A mood that shows for a moment after a line (then his needs decide again). */
@Composable
fun rememberFlash(): Pair<Mood?, (Mood?) -> Unit> {
    var flash by remember { mutableStateOf<Mood?>(null) }
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(n) {
        if (flash != null) {
            delay(2600)
            flash = null
        }
    }
    return flash to { m: Mood? -> flash = m; n++ }
}

/* =============================== living room =============================== */

@Composable
fun LivingRoom(env: RoomEnv, active: Boolean, speaker: Speaker, flash: Mood?, setFlash: (Mood?) -> Unit) {
    var pokes by remember { mutableIntStateOf(0) }
    val face = if (flash != null && !env.needs.asleep) env.face.copy(mood = flash) else env.face
    RoomFrame(
        env,
        scene = { reserve ->
            RoomScene(
                kind = RoomKind.Living,
                room = env.roomOf("living"),
                sky = env.clock.sky,
                clock = env.clock,
                look = env.look,
                face = face,
                asleep = env.needs.asleep,
                line = speaker.line,
                active = active,
                topInset = env.topInset,
                bottomReserve = reserve,
                preview = env.previewIn("living"),
                onPoke = {
                    pokes++
                    val (text, mood) = Lines.poke(pokes, env.face, env.needs.asleep)
                    speaker.say(text)
                    setFlash(mood)
                },
            )
        },
        tray = {
            PillRow {
                Pill("Play", { env.go(Page.Games) }, icon = "🎮", index = 0, modifier = Modifier.weight(1f))
                Pill("Wardrobe", { env.open(Panel.Wardrobe) }, icon = "👕", kind = PillKind.Alt, index = 1, modifier = Modifier.weight(1.25f))
                Pill("Decorate", { env.open(Panel.Decor("living")) }, icon = "🛋️", kind = PillKind.House, index = 2, modifier = Modifier.weight(1.25f))
            }
        },
    )
}

/* ================================= kitchen ================================= */

@Composable
fun KitchenRoom(env: RoomEnv, active: Boolean, speaker: Speaker) {
    val needs = env.needs
    val wallet = env.wallet
    val fb = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var eating by remember { mutableStateOf<Eating?>(null) }
    var fx by remember { mutableStateOf<String?>(null) }
    val stock = House.FOODS.filter { (wallet.pantry[it.id] ?: 0) > 0 }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        speaker.say(
            when {
                needs.asleep -> "Zzz…"
                needs.hunger < 25 -> Lines.pick(Lines.HUNGRY)
                needs.hunger > 92 -> Lines.pick(Lines.FULL)
                stock.isNotEmpty() -> "What is on the menu?"
                else -> "The fridge is empty. Typical."
            },
        )
    }
    LaunchedEffect(env.quip) { env.quip?.takeIf { it.page == Page.Kitchen }?.let { speaker.say(it.text) } }

    fun eat(id: String) {
        val food = House.FOOD[id] ?: return
        if (eating != null) return
        if (needs.asleep) return speaker.say(Lines.ASLEEP_HINT)
        if ((wallet.pantry[id] ?: 0) < 1) return speaker.say("None of those left. Buy some more?")
        if (!env.store.feed(id)) return speaker.say("Something went wrong. Try again.")
        fb.sound("pop")
        eating = Eating(id, System.currentTimeMillis())
        fx = null
        speaker.say("Om nom nom…")
        scope.launch {
            delay(1300)
            eating = null
            speaker.say(food.quip)
            if (food.fx != null) {
                fx = food.fx
                delay(if (food.fx == "glow") 6000 else 3800)
                fx = null
            }
        }
    }

    val note = when {
        needs.asleep -> "Asleep. Wake him in the Bedroom."
        needs.hunger < 20 -> "Too hungry to play! Feed him."
        needs.hunger < 25 -> "He looks bored. A snack would help."
        else -> "A game needs a tummy at least 20 full."
    }
    val face = if (eating != null) env.face.copy(mood = Mood.Scared) else env.face

    RoomFrame(
        env,
        scene = { reserve ->
            RoomScene(
                kind = RoomKind.Kitchen,
                room = env.roomOf("kitchen"),
                sky = env.clock.sky,
                clock = env.clock,
                look = env.look,
                face = face,
                asleep = needs.asleep,
                line = speaker.line,
                active = active,
                topInset = env.topInset,
                bottomReserve = reserve,
                preview = env.previewIn("kitchen"),
                eating = eating,
                fx = fx,
                onPoke = {
                    speaker.say(if (needs.asleep) "Zzz…" else if (stock.isNotEmpty()) "Tap something from the fridge, not me." else "The fridge is empty. Buy me something.")
                },
            )
        },
        tray = {
            NeedHud("🍔", "Hunger", needs.hunger.toFloat(), note)
            if (stock.isEmpty()) {
                IconText(
                    "Fridge is empty: buy some food",
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(Palette.Cream.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(vertical = 14.dp),
                )
            } else {
                LazyRow(
                    Modifier.fillMaxWidth().semantics { contentDescription = "Food in the fridge. Tap one to feed it to him." },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(stock, key = { it.id }) { f ->
                        StockItem(f.id, f.name, wallet.pantry[f.id] ?: 0, enabled = eating == null && !needs.asleep) {
                            fb.tick()
                            eat(f.id)
                        }
                    }
                }
            }
            PillRow {
                Pill("Buy food", { env.open(Panel.BuyFood) }, icon = "🛒", index = 0, modifier = Modifier.weight(1f))
                Pill("Decorate", { env.open(Panel.Decor("kitchen")) }, icon = "🛋️", kind = PillKind.House, index = 1, modifier = Modifier.weight(1f))
            }
        },
    )
}

@Composable
private fun StockItem(id: String, name: String, count: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(76.dp)
            .height(84.dp)
            .background(Palette.Cream.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Feed $name, $count in the fridge", onClick = onClick)
            .padding(6.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(48.dp)) { HouseArt.draw(this, id, Rect(0f, 0f, size.width, size.height), 0f, alpha = if (enabled) 1f else 0.6f) }
            IconText(name, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .background(Palette.Btn, CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconText("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ================================= bedroom ================================= */

@Composable
fun BedroomRoom(env: RoomEnv, active: Boolean, speaker: Speaker) {
    val needs = env.needs
    val fb = LocalFeedback.current
    val now = rememberNow(15_000)
    val filthy = !needs.asleep && needs.dirt >= Needs.FILTHY_AT
    val mins = if (needs.asleep) max(0, ceil(((Needs.wakesAt(needs) ?: now) - now) / 60_000.0).toInt()) else 0

    LaunchedEffect(env.quip) { env.quip?.takeIf { it.page == Page.Bedroom }?.let { speaker.say(it.text) } }

    fun toggleLamp() {
        if (needs.asleep) {
            if (env.store.wake()) {
                fb.sound("click")
                speaker.say(if (needs.energy > 90) "Good morning. I still hate you." else "Ugh. Five more minutes would have been nice.")
            }
            return
        }
        if (needs.energy >= 99) return speaker.say("I'm not tired. Nice try.")
        if (needs.dirt >= Needs.FILTHY_AT) return speaker.say("Ew, no. I'm not getting into bed like this. Clean me first!")
        if (env.store.sleep()) {
            fb.sound("click")
            speaker.say("Lights out. Do not press anything.")
        }
    }

    val text = if (needs.asleep) "Zzz…" else speaker.line.text.ifBlank { if (needs.energy < 40) "My eyes hurt. I need a nap." else "Not sleepy." }
    val note = when {
        filthy -> "Too dirty to sleep. Wash him first."
        needs.asleep -> "Sleeping. Energy +${Math.round(Needs.sleepEnergyPerHour / 60.0 * 10) / 10.0} a minute" + (if (mins > 0) ", wakes in about $mins min." else ".")
        needs.energy < 40 -> "Dark circles! Too sleepy to play."
        else -> null
    }

    RoomFrame(
        env,
        scene = { reserve ->
            RoomScene(
                kind = RoomKind.Bedroom,
                room = env.roomOf("bedroom"),
                sky = env.clock.sky,
                clock = env.clock,
                look = env.look,
                face = env.face,
                asleep = needs.asleep,
                line = speaker.line.copy(text = text),
                active = active,
                topInset = env.topInset,
                bottomReserve = reserve,
                preview = env.previewIn("bedroom"),
                lamp = LampButton(!needs.asleep, if (needs.asleep) "Turn on the lamp (wake him up)" else "Turn off the lamp", ::toggleLamp),
                onPoke = { speaker.say(if (needs.energy < 40) "Tap the lamp. I need a nap." else "Do not poke me. Tap the lamp if you must.") },
            )
        },
        tray = {
            NeedHud(if (needs.asleep) "💤" else "⚡", "Energy", needs.energy.toFloat(), note, dark = true)
            IconText(
                if (needs.asleep) "The lamp is off. Tap it to turn it on and wake him." else "💡 Tap the lamp to switch it off. He will go to sleep.",
                color = Color(0xFFE6E2FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            PillRow {
                Pill("Wake-up", { env.open(Panel.Wake) }, icon = "⏰", kind = PillKind.Alt, enabled = needs.asleep, index = 0, modifier = Modifier.weight(1f))
                Pill("Decorate", { env.open(Panel.Decor("bedroom")) }, icon = "🛋️", kind = PillKind.House, index = 1, modifier = Modifier.weight(1f))
                if (filthy) Pill("Bathroom", { env.go(Page.Bath) }, icon = "🛁", index = 2, modifier = Modifier.weight(1f))
            }
        },
    )
}
