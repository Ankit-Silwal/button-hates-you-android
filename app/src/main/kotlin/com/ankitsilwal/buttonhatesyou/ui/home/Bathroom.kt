package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.ui.Lines
import com.ankitsilwal.buttonhatesyou.ui.art.SceneArt
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Meter
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.PillRow
import com.ankitsilwal.buttonhatesyou.ui.room.RoomKind
import com.ankitsilwal.buttonhatesyou.ui.room.RoomPainter.contactShadow
import com.ankitsilwal.buttonhatesyou.ui.room.RoomScene
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val SHOWER_MS = 2800L
private const val SCRUB_DP = 1500f // rubbing this far covers him in lather

// foam bubbles on him: [left %, top %, size % of his width]
private val FOAM = arrayOf(
    floatArrayOf(18f, 18f, 26f), floatArrayOf(52f, 8f, 30f), floatArrayOf(78f, 24f, 24f), floatArrayOf(30f, 44f, 30f), floatArrayOf(66f, 46f, 28f), floatArrayOf(10f, 60f, 22f), floatArrayOf(44f, 64f, 32f), floatArrayOf(82f, 62f, 24f),
    floatArrayOf(24f, 80f, 26f), floatArrayOf(60f, 82f, 28f), floatArrayOf(38f, 28f, 22f), floatArrayOf(70f, 12f, 20f), floatArrayOf(50f, 40f, 24f), floatArrayOf(14f, 38f, 20f), floatArrayOf(88f, 44f, 18f), floatArrayOf(34f, 92f, 22f),
)

private enum class Phase { Idle, Lather, Shower, Clean }

/**
 * The bathroom (src/hub/BathPane.jsx): drag the soap over him to scrub, then
 * drag him into the shower (or tap the shower pill) to rinse. Washing is what
 * lets him go to bed again.
 */
@Composable
fun BathRoom(env: RoomEnv, active: Boolean, speaker: Speaker) {
    val needs = env.needs
    val fb = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scrub by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var soapAt by remember { mutableStateOf<Offset?>(null) } // the soap's centre while dragged
    var dragging by remember { mutableStateOf(false) }
    var talk by remember { mutableIntStateOf(0) }
    var showerStart by remember { mutableStateOf(0L) }
    val dirty = needs.dirt >= 5
    val face = env.face

    LaunchedEffect(active) {
        if (!active || phase != Phase.Idle) return@LaunchedEffect
        speaker.say(
            when {
                needs.asleep -> "Zzz…"
                face.dirty > 0 -> Lines.pick(Lines.DIRTY_LINES)
                needs.dirt >= 5 -> "A little dusty. A wash never hurt."
                else -> Lines.pick(Lines.CLEAN_LINES)
            },
        )
    }

    fun lather(amount: Float) {
        val before = scrub
        scrub = (scrub + amount).coerceAtMost(1f)
        if (scrub >= 1f && before < 1f) {
            phase = Phase.Lather
            fb.sound("pop")
            speaker.say(Lines.pick(Lines.LATHER_LINES))
        }
    }

    fun canScrub(): Boolean {
        if (needs.asleep) { speaker.say(Lines.ASLEEP_HINT); return false }
        if (!dirty && phase == Phase.Idle) { speaker.say(Lines.pick(Lines.CLEAN_LINES)); return false }
        return phase == Phase.Idle
    }

    fun scrubTap() {
        if (canScrub()) {
            lather(0.18f)
            fb.tick()
            speaker.say(Lines.pick(Lines.SCRUB_LINES))
        }
    }

    fun startShower() {
        if (needs.asleep) return speaker.say(Lines.ASLEEP_HINT)
        if (phase != Phase.Lather) return speaker.say(if (scrub > 0) "Not foamy enough yet. Keep scrubbing!" else "Water alone will not cut it. Soap first!")
        phase = Phase.Shower
        showerStart = System.currentTimeMillis()
        fb.sound("whoosh")
        speaker.say("AAH. Cold. No, hot. NOW COLD.")
        scope.launch {
            delay(SHOWER_MS)
            if (!env.store.wash()) {
                phase = Phase.Idle
                scrub = 0f
                speaker.say("Something went wrong. Try again.")
                return@launch
            }
            phase = Phase.Clean
            scrub = 0f
            fb.sound("win")
            speaker.say(Lines.pick(Lines.DONE_LINES))
            delay(3200)
            phase = Phase.Idle
        }
    }

    val showering = phase == Phase.Shower
    val clean = phase == Phase.Clean
    val grimy = phase == Phase.Idle
    val grime = if (grimy) (needs.dirt.toFloat() / 100f) * (1f - scrub) else 0f
    val level = if (grimy && grime * 100 >= Needs.DIRTY_AT * (1 - scrub) && face.dirty > 0) face.dirty else 0
    val heroFace = face.copy(
        mood = when {
            showering -> Mood.Scared
            clean -> Mood.Smug
            needs.asleep -> Mood.Sleep
            else -> face.mood
        },
        sleepy = face.sleepy && !clean,
        dirty = if (clean || showering) 0 else level,
        grime = grime,
    )
    val lathered = if (phase == Phase.Lather || showering) 1f else if (clean) 0f else scrub
    val foamN = if (phase == Phase.Lather || showering) FOAM.size else ceil(scrub * FOAM.size).toInt()

    val note = when {
        needs.asleep -> "He is asleep. Wake him up first."
        phase == Phase.Lather -> "Drag him into the shower (or press the shower button)."
        needs.dirt >= 75 -> "Filthy! He will refuse to sleep until he is washed."
        face.dirty > 0 -> "Grubby from all the games. Drag the soap over him."
        else -> "Drag the soap over him to scrub, then put him in the shower."
    }

    val latherState = rememberUpdatedState(::lather)
    val canScrubState = rememberUpdatedState(::canScrub)
    val startShowerState = rememberUpdatedState(::startShower)
    val phaseState = rememberUpdatedState(phase)

    RoomFrame(
        env,
        scene = { reserve ->
            RoomScene(
                kind = RoomKind.Bath,
                room = null,
                sky = env.clock.sky,
                clock = env.clock,
                look = env.look,
                face = heroFace,
                asleep = needs.asleep,
                line = speaker.line,
                active = active,
                topInset = env.topInset,
                bottomReserve = reserve,
                onPoke = {},
                hero = { g, time ->
                    // he walks into the shower, or follows your finger
                    val walk = remember { Animatable(g.hx) }
                    val lift = remember { Animatable(0f) }
                    LaunchedEffect(phase, dragging, g) {
                        if (dragging) return@LaunchedEffect
                        val target = if (phase == Phase.Shower || phase == Phase.Clean) g.shower.center.x else g.hx
                        launch { lift.animateTo(0f, tween(400)) }
                        walk.animateTo(target, tween(900))
                    }
                    val hero = Rect(walk.value - g.hs / 2, g.heroTop + lift.value, walk.value + g.hs / 2, g.heroTop + g.hs + lift.value)
                    val sizeDp = with(density) { g.hs.toDp() }
                    Canvas(Modifier.fillMaxSize()) { contactShadow(hero, density.density) }
                    Box(
                        Modifier
                            .offset { IntOffset(hero.left.roundToInt(), hero.top.roundToInt()) }
                            .size(sizeDp)
                            .semantics { contentDescription = "The button. Drag him into the shower when he is foamy." }
                            .pointerInput(g) {
                                detectDragGestures(
                                    onDragStart = {
                                        if (phaseState.value == Phase.Lather) dragging = true
                                        else if (phaseState.value == Phase.Idle) speaker.say("Soap first. Drag the soap over me.")
                                    },
                                    onDrag = { ch, d ->
                                        if (dragging) {
                                            ch.consume()
                                            scope.launch {
                                                walk.snapTo(walk.value + d.x)
                                                lift.snapTo(lift.value + d.y)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragging) {
                                            val c = Offset(walk.value, g.heroTop + g.hs / 2 + lift.value)
                                            val inShower = g.shower.inflate(g.hs * 0.2f).contains(c)
                                            dragging = false
                                            if (inShower) startShowerState.value()
                                            else speaker.say("Drag me into the shower, over there →")
                                        }
                                    },
                                    onDragCancel = { dragging = false },
                                )
                            },
                    ) {
                        Avatar(look = env.look, face = heroFace, size = sizeDp, legs = true, onPoke = {
                            speaker.say(
                                when {
                                    needs.asleep -> Lines.ASLEEP_POKE
                                    phase == Phase.Lather -> "Drag me into the shower, over there →"
                                    dirty -> "Soap. Not fingers. Soap."
                                    else -> Lines.pick(Lines.CLEAN_LINES)
                                },
                            )
                        })
                        // the lather on him
                        Canvas(Modifier.fillMaxSize()) {
                            val rinse = if (showering) ((System.currentTimeMillis() - showerStart) / SHOWER_MS.toFloat()).coerceIn(0f, 1f) else 0f
                            time.floatValue // redraw with the clock
                            for (i in 0 until foamN.coerceAtMost(FOAM.size)) {
                                val f = FOAM[i]
                                val r = size.width * f[2] / 200f
                                val c = Offset(size.width * f[0] / 100f, size.height * f[1] / 100f + rinse * size.height * 0.5f)
                                val a = (1f - rinse * 1.3f).coerceIn(0f, 1f)
                                drawCircle(Color.White, r, c, alpha = 0.92f * a)
                                drawCircle(Color(0xFFBFE4EE), r, c, style = Stroke(1.5f), alpha = a)
                                drawCircle(Color.White, r * 0.3f, Offset(c.x - r * 0.35f, c.y - r * 0.35f), alpha = a)
                            }
                            if (clean) {
                                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    textSize = size.width * 0.18f
                                    color = Color(0xFFFFE36B).toArgb()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                val tt = time.floatValue
                                val cv = drawContext.canvas.nativeCanvas
                                cv.drawText("✦", size.width * 0.1f, size.height * (0.2f + 0.05f * kotlin.math.sin(tt * 3f)), p)
                                cv.drawText("✦", size.width * 0.92f, size.height * (0.15f + 0.05f * kotlin.math.sin(tt * 3f + 1f)), p)
                                cv.drawText("✧", size.width * 0.85f, size.height * (0.7f + 0.05f * kotlin.math.sin(tt * 3f + 2f)), p)
                            }
                        }
                    }
                },
                front = { g, t -> SceneArt.showerFront(this, g.shower, showering, t) },
                extras = { g ->
                    // the soap on the vanity, or in your fingers
                    val sw = g.hs * 0.42f
                    val sh = sw * 40f / 60f
                    val rest = Offset(g.vx + g.hs * 0.3f, g.hz - g.hs * 0.76f - sh * 0.3f)
                    val at = soapAt ?: rest
                    val touch = with(density) { maxOf(sw, 56.dp.toPx()) }
                    val held = soapAt != null
                    var last by remember { mutableStateOf<Offset?>(null) }
                    Box(
                        Modifier
                            .offset { IntOffset((at.x - touch / 2).roundToInt(), (at.y - touch / 2).roundToInt()) }
                            .size(with(density) { touch.toDp() })
                            .semantics { contentDescription = "Soap. Drag it over the button to scrub him." }
                            .pointerInput(g) {
                                detectDragGestures(
                                    onDragStart = { o ->
                                        if (needs.asleep) {
                                            speaker.say(Lines.ASLEEP_HINT)
                                        }
                                        soapAt = Offset(at.x - touch / 2 + o.x, at.y - touch / 2 + o.y)
                                        last = soapAt
                                    },
                                    onDrag = { ch, d ->
                                        ch.consume()
                                        val now = (soapAt ?: rest) + d
                                        soapAt = now
                                        val from = last
                                        last = now
                                        val heroR = g.hero
                                        if (from != null && heroR.contains(now) && canScrubState.value()) {
                                            latherState.value(hypot(now.x - from.x, now.y - from.y) / (SCRUB_DP * density.density))
                                            talk++
                                            if (talk % 12 == 0) fb.tick()
                                            if (talk % 40 == 0) speaker.say(Lines.pick(Lines.SCRUB_LINES))
                                        }
                                    },
                                    onDragEnd = { soapAt = null; last = null },
                                    onDragCancel = { soapAt = null; last = null },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(
                            Modifier
                                .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
                                .graphicsLayer { scaleX = if (held) 1.15f else 1f; scaleY = if (held) 1.15f else 1f; rotationZ = if (held) -8f else 0f },
                        ) { SceneArt.soap(this, Rect(0f, 0f, size.width, size.height), held) }
                    }
                },
            )
        },
        tray = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Step("1 · Scrub", on = phase == Phase.Idle, done = phase != Phase.Idle)
                Step("2 · Shower", on = phase == Phase.Lather, done = showering || clean)
                Row(
                    Modifier.weight(1f).background(Palette.Cream.copy(alpha = 0.85f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🫧", fontSize = 14.sp)
                    Meter(lathered * 100f, Color(0xFF6FC3DD), Modifier.weight(1f), height = 9.dp)
                    Text("${(100 - needs.dirt).roundToInt()}% clean", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            PillRow {
                Pill("Scrub", ::scrubTap, icon = "🧼", kind = PillKind.Alt, index = 0, modifier = Modifier.weight(1f))
                Pill("Shower", ::startShower, icon = "🚿", enabled = phase == Phase.Lather, index = 1, modifier = Modifier.weight(1.2f))
            }
        },
    )
}

@Composable
private fun Step(text: String, on: Boolean, done: Boolean) {
    val (bg, fg) = when {
        on -> Palette.Mustard to Palette.Ink
        done -> Color(0xFFBFE5C3) to Color(0xFF2F6B3A)
        else -> Palette.Cream.copy(alpha = 0.8f) to Palette.InkSoft
    }
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp))
}
