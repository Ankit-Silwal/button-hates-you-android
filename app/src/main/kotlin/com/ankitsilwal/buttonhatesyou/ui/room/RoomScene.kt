package com.ankitsilwal.buttonhatesyou.ui.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.economy.Room
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import com.ankitsilwal.buttonhatesyou.ui.common.Clock
import com.ankitsilwal.buttonhatesyou.ui.common.Line
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Sky
import com.ankitsilwal.buttonhatesyou.ui.common.SpeechBubble
import com.ankitsilwal.buttonhatesyou.ui.room.RoomPainter.contactShadow
import com.ankitsilwal.buttonhatesyou.ui.room.RoomPainter.paintBack
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** The bedside lamp as a real button: tap to sleep / wake. */
class LampButton(val on: Boolean, val label: String, val onToggle: () -> Unit)

/** What he is eating right now (a FOODS id); [n] restarts the animation. */
data class Eating(val id: String, val n: Long)

/**
 * The one chassis every room stands on (src/hub/RoomScene.jsx): the room
 * painted by [RoomPainter], the button standing on the floor with his contact
 * shadow, his speech bubble, and whatever the room adds.
 *
 * [topInset] is how much of the top is under the top bar. [active] runs the
 * ambient animation (only the room in view needs it).
 */
@Composable
fun RoomScene(
    kind: RoomKind,
    room: Room?,
    sky: Sky,
    clock: Clock,
    look: Look,
    face: Face,
    asleep: Boolean,
    line: Line,
    active: Boolean,
    topInset: Dp,
    onPoke: () -> Unit,
    modifier: Modifier = Modifier,
    bottomReserve: Dp = 0.dp,
    preview: String? = null,
    eating: Eating? = null,
    fx: String? = null,
    lamp: LampButton? = null,
    bathInShower: Boolean = false,
    hero: (@Composable BoxScope.(SceneGeom, MutableFloatState) -> Unit)? = null,
    front: (DrawScope.(SceneGeom, Float) -> Unit)? = null,
    extras: @Composable BoxScope.(SceneGeom) -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        val start = withFrameNanos { it } - (time.floatValue * 1e9f).toLong()
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val density = LocalDensity.current
    val fb = LocalFeedback.current

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val dp = density.density
        val reservePx = with(density) { bottomReserve.toPx() }
        val g = remember(kind, wPx, hPx, topInset, reservePx, room, bathInShower) {
            SceneGeom(kind, wPx, hPx - reservePx, dp, with(density) { topInset.toPx() }, room, bathInShower, fullH = hPx)
        }
        val bedId = g.place["bed"]
        val tucked = kind == RoomKind.Bedroom && asleep && bedId != null
        val heroRect = if (tucked) g.tuckedHero(bedId!!) ?: g.hero else g.hero

        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            paintBack(RoomPainter.Params(g, room, sky, clock, asleep, preview, tucked, t))
            if (!tucked && hero == null) contactShadow(heroRect, dp)
        }

        // the button
        if (hero != null) {
            hero(g, time)
        } else {
            val sizeDp = with(density) { heroRect.width.toDp() }
            Box(
                Modifier
                    .offset { IntOffset(heroRect.left.roundToInt(), heroRect.top.roundToInt()) }
                    .size(sizeDp)
                    .graphicsLayer {
                        val t = time.floatValue
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        when {
                            tucked -> {
                                val b = sin(t * 2 * PI.toFloat() / 4.5f)
                                scaleY = 1f + 0.03f * b
                                scaleX = 1f - 0.015f * b
                            }
                            eating != null -> {
                                val c = abs(sin(t * PI.toFloat() / 0.26f))
                                scaleY = 1f - 0.08f * c
                                scaleX = 1f + 0.05f * c
                            }
                            fx == "sugar" -> {
                                translationX = sin(t * 2 * PI.toFloat() / 0.3f) * 3 * dp
                                rotationZ = sin(t * 2 * PI.toFloat() / 0.3f) * 2f
                            }
                            fx == "hiccup" -> {
                                val ph = (t % 1.1f) / 1.1f
                                translationY = if (ph < 0.2f) -sin(ph / 0.2f * PI.toFloat()) * 12 * dp else 0f
                            }
                            fx == "glitch" -> {
                                val s = ((t / 0.175f).toInt() % 3)
                                translationX = (s - 1) * 3 * dp
                                alpha = if (s == 2) 0.85f else 1f
                            }
                        }
                    },
            ) {
                Avatar(
                    look = look,
                    face = if (tucked) face.copy(mood = Mood.Sleep) else face,
                    size = sizeDp,
                    legs = !tucked,
                    onPoke = {
                        fb.tick()
                        onPoke()
                    },
                )
            }
        }

        // in front of him: his blanket, the food he eats, fx, the zZz, a room's own layer
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            if (tucked) blanket(heroRect, bedId!!)
            if (fx == "glow") {
                val c = heroRect.center
                val r = heroRect.width * 0.9f
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFF27A), Color.Transparent), c, r), r, c, alpha = 0.6f + 0.4f * sin(t * 4f))
            }
            if (fx == "sugar") sparks(heroRect, t)
            if (fx == "hiccup") hicTag(heroRect, t, dp)
            if (eating != null) bite(heroRect, eating, dp)
            if (asleep) zzz(heroRect, t, dp, kind == RoomKind.Bedroom)
            if (lamp != null) g.nsRect?.let { lampRing(it, t, lamp.on, dp) }
            front?.invoke(this, g, t)
        }

        // the bedside lamp is the light switch
        if (lamp != null) {
            g.nsRect?.let { r ->
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                        .semantics { contentDescription = lamp.label }
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                            fb.tick()
                            lamp.onToggle()
                        },
                )
            }
        }

        SpeechBubble(
            line,
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 8.dp)
                .fillMaxWidth()
                .wrapContentWidth()
                .padding(horizontal = 16.dp),
            dark = kind == RoomKind.Bedroom,
        )

        extras(g)
    }
}

/* ------------------------------ front layer ------------------------------ */

private val tagPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    textAlign = android.graphics.Paint.Align.CENTER
}

private fun DrawScope.blanket(hero: Rect, bed: String) {
    val (a, b) = when (bed) {
        "bed-cozy" -> Color(0xFFE98A6C) to Color(0xFFC25D44)
        "bed-cloud" -> Color(0xFFB9A8F5) to Color(0xFF8B74E0)
        "bed-loft" -> Color(0xFF4A8A96) to Color(0xFF2F6370)
        else -> Color(0xFF8A9CC0) to Color(0xFF64769A)
    }
    val l = hero.left - hero.width * 0.4f
    val r = hero.right + hero.width * 0.9f
    val bottom = hero.bottom + hero.height * 0.18f
    val top = bottom - hero.height * 0.52f
    val p = Path().apply {
        moveTo(l, bottom)
        lineTo(l, top + hero.height * 0.25f)
        cubicTo(l, top, l + (r - l) * 0.2f, top, l + (r - l) * 0.4f, top)
        lineTo(l + (r - l) * 0.6f, top)
        cubicTo(r - (r - l) * 0.2f, top, r, top, r, top + hero.height * 0.25f)
        lineTo(r, bottom)
        close()
    }
    drawPath(p, Color(0x33000000))
    drawPath(p, Brush.verticalGradient(listOf(a, b), top, bottom))
    drawLine(Color(0x40FFFFFF), Offset(l + (r - l) * 0.2f, top + 3f), Offset(r - (r - l) * 0.2f, top + 3f), 3f)
}

private fun DrawScope.sparks(hero: Rect, t: Float) {
    for (i in 0 until 12) {
        val a = i * 0.52f + t * 2f
        val d = hero.width * (0.55f + 0.25f * sin(t * 3f + i))
        val c = Offset(hero.center.x + kotlin.math.cos(a) * d, hero.center.y + sin(a) * d * 0.8f)
        drawCircle(if (i % 2 == 0) Color(0xFFFFE36B) else Color(0xFFFF8FD0), 3f + 2f * abs(sin(t * 5f + i)), c)
    }
}

private fun DrawScope.hicTag(hero: Rect, t: Float, dp: Float) {
    val ph = (t % 1.1f) / 1.1f
    if (ph > 0.6f) return
    val x = hero.right + hero.width * 0.15f
    val y = hero.top - ph * 16 * dp
    tagPaint.textSize = 14 * dp
    val w = tagPaint.measureText("hic!") + 16 * dp
    drawRoundRect(Color(0xFF7C6BD6), Offset(x - w / 2, y - 18 * dp), Size(w, 22 * dp), CornerRadius(11 * dp), alpha = 1f - ph)
    tagPaint.color = Color.White.toArgb()
    drawContext.canvas.nativeCanvas.drawText("hic!", x, y - 2.5f * dp, tagPaint)
}

/** The snack flies to his mouth and shrinks, bite by bite (1.3 s). */
private fun DrawScope.bite(hero: Rect, e: Eating, dp: Float) {
    val ms = (System.currentTimeMillis() - e.n).coerceAtLeast(0)
    val p = (ms / 1300f).coerceIn(0f, 1f)
    if (p >= 1f) return
    val size = hero.width * 0.42f * (1f - 0.75f * p)
    val cx = hero.center.x
    val cy = hero.top + hero.height * (0.66f - 0.08f * sin(p * PI.toFloat()))
    val r = Rect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    HouseArt.draw(this, e.id, r, 0f, alpha = (1.4f - p).coerceIn(0f, 1f))
    // crumbs
    for (i in 0 until 6) {
        val fall = p * hero.height * 0.4f
        drawCircle(if (i % 2 == 0) Color(0xFFC98B45) else Color(0xFFF1C27D), 2.2f * dp, Offset(cx + (i - 2.5f) * 7 * dp, cy + size / 2 + fall * (0.5f + i % 3 * 0.25f)), alpha = (p * 2f).coerceAtMost(1f) * (1f - p))
    }
}

private fun DrawScope.zzz(hero: Rect, t: Float, dp: Float, bedroom: Boolean) {
    val col = if (bedroom) Color(0xFFD8D2FF) else Color(0xFF6B5BD6)
    for (i in 0 until 3) {
        val ph = ((t + i * 0.9f) % 3f) / 3f
        val a = sin(ph * PI.toFloat())
        tagPaint.textSize = (16 + i * 7) * dp
        tagPaint.color = col.copy(alpha = a).toArgb()
        drawContext.canvas.nativeCanvas.drawText(if (i == 2) "Z" else "z", hero.left + hero.width * (0.75f + i * 0.12f), hero.top - ph * 26 * dp + i * 2 * dp, tagPaint)
    }
}

private fun DrawScope.lampRing(r: Rect, t: Float, on: Boolean, dp: Float) {
    val ph = ((t - 1.5f).coerceAtLeast(0f) % 2.6f) / 2.6f
    if (ph > 0.7f) return
    val c = Offset(r.center.x, r.top + r.width * 0.41f)
    val rad = r.width * 0.39f * (0.8f + ph * 0.6f)
    drawCircle(if (on) Color(0xD9FFE296) else Color(0xA6BEC8FF), rad, c, style = Stroke(3 * dp), alpha = 1f - ph / 0.7f)
}
