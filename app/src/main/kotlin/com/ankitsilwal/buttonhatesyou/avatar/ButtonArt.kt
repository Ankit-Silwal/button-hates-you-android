package com.ankitsilwal.buttonhatesyou.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// The button, drawn the way the web version's .char is built in CSS
// (src/app/globals.css + src/app/avatar.css + src/hub/hub-panes.css).
//
// Everything below is drawn in a 100x100 "unit box": the body is the circle
// (0,0)-(100,100), exactly the web's --s box, so CSS percentages port 1:1.
// Hats, ears and legs reach outside the box (up to about -46 and 122), like
// on the web.

/**
 * Draws the button centered on [center] with a body [size] px wide (the body
 * circle; hats, ears and legs reach about 0.46 * size above and 0.22 * size
 * below it). Safe for games (DrawScope) and the Compose UI.
 *
 * @param t seconds, drives blinking, googly eyes, the fly and stink, tears.
 * @param squash 0 = round, >0 squashed flat (pressed), <0 stretched tall.
 * @param legs little legs with shoes under the body.
 * @param blink blink every few seconds (driven by [t]).
 * @param flash draw the body white (hit flash).
 * @param tears tears running down from the eyes.
 * @param facing -1..1, turns the face (eyes, brows, mouth) left or right.
 */
fun DrawScope.drawButton(
    center: Offset,
    size: Float,
    look: Look,
    face: Face = Face(),
    t: Float = 0f,
    squash: Float = 0f,
    legs: Boolean = false,
    blink: Boolean = true,
    flash: Boolean = false,
    tears: Boolean = false,
    facing: Float = 0f,
) {
    if (size <= 0f) return
    val k = size / 100f
    withTransform({
        translate(center.x, center.y)
        if (squash != 0f) scale(1f + squash, 1f - squash, pivot = Offset(0f, size * 0.55f))
        scale(k, k, pivot = Offset.Zero)
        translate(-50f, -50f)
    }) {
        ButtonPainter.draw(this, look, face, t, legs, blink, flash, tears, facing.coerceIn(-1f, 1f))
    }
}

internal val INK = Color(0xFF2B1D17)
internal val MOUTH_IN = Color(0xFF3B1C17)

internal fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).roundToInt().coerceIn(0, 255))

internal fun hex(v: Long) = Color(v)

/** A CSS `linear-gradient(<deg>, ...)` over [box], as a Compose brush. */
internal fun cssLinear(deg: Float, vararg stops: Pair<Float, Color>, box: Rect = UNIT): Brush {
    val rad = Math.toRadians(deg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -kotlin.math.cos(rad).toFloat()
    val len = abs(box.width * dx) + abs(box.height * dy)
    val c = box.center
    val h = Offset(dx, dy) * (len / 2f)
    return Brush.linearGradient(*stops, start = c - h, end = c + h)
}

internal val UNIT = Rect(0f, 0f, 100f, 100f)

/** Shapes that never change, in unit space: built once. */
internal object Shapes {
    val body: Path by lazy { Path().apply { addOval(UNIT) } }

    // the eye: almond, rounder on top (border-radius 55% 55% 50% 50% / 62% 62% 45% 45%,
    // scaled like CSS does), 14 x 15 centered on 0,0
    val eye: Path by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    -7f, -7.5f, 7f, 7.5f,
                    topLeftCornerRadius = CornerRadius(7f, 8.45f),
                    topRightCornerRadius = CornerRadius(7f, 8.45f),
                    bottomRightCornerRadius = CornerRadius(6.36f, 6.14f),
                    bottomLeftCornerRadius = CornerRadius(6.36f, 6.14f),
                ),
            )
        }
    }

    val heart: Path by lazy {
        // unit heart about 16 wide, centered on 0,0
        Path().apply {
            moveTo(0f, 6.8f)
            cubicTo(-2.5f, 4.6f, -8f, 1.2f, -8f, -2.6f)
            cubicTo(-8f, -5.6f, -5.6f, -7.4f, -3.6f, -7.4f)
            cubicTo(-1.8f, -7.4f, -0.5f, -6.4f, 0f, -4.8f)
            cubicTo(0.5f, -6.4f, 1.8f, -7.4f, 3.6f, -7.4f)
            cubicTo(5.6f, -7.4f, 8f, -5.6f, 8f, -2.6f)
            cubicTo(8f, 1.2f, 2.5f, 4.6f, 0f, 6.8f)
            close()
        }
    }

    val star: Path by lazy {
        Path().apply {
            for (i in 0 until 10) {
                val a = -Math.PI / 2 + i * Math.PI / 5
                val r = if (i % 2 == 1) 3.9f else 8.6f
                val x = (kotlin.math.cos(a) * r).toFloat()
                val y = (kotlin.math.sin(a) * r).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}

// How each mood sets the face (from the [data-mood] rules).
private enum class MouthKind { Smirk, Smug, Flat, Frown, O, SmallO, Smile, Wobble }
private enum class EyeKind { Open, Closed, Sleep, Happy }

private class MoodFace(
    val lid: Float, // how far the upper lid comes down, 0..1
    val browAlpha: Float,
    val browL: Pair<Float, Float>, // dy, rotation
    val browR: Pair<Float, Float>,
    val mouth: MouthKind,
    val mouthY: Float = 60f,
    val mouthRot: Float = 0f,
    val eye: EyeKind = EyeKind.Open,
    val pupil: Offset = Offset.Zero,
    val blush: Float = 0.16f,
)

private val CALM_L = 0f to -3f
private val CALM_R = -1.2f to 5f

private val MOODS: Map<Mood, MoodFace> = mapOf(
    Mood.Calm to MoodFace(0.18f, 0.5f, CALM_L, CALM_R, MouthKind.Smirk),
    Mood.Smug to MoodFace(0.32f, 0.8f, CALM_L, CALM_R, MouthKind.Smug),
    Mood.Annoyed to MoodFace(0.45f, 1f, CALM_L, CALM_R, MouthKind.Flat, mouthY = 64f),
    Mood.Tired to MoodFace(0.45f, 0.5f, CALM_L, CALM_R, MouthKind.Flat, mouthY = 64f),
    Mood.Angry to MoodFace(0.12f, 1f, 2f to 22f, 2f to -22f, MouthKind.Frown, mouthY = 64f, blush = 0f),
    Mood.Sad to MoodFace(0.18f, 1f, 0f to -16f, 0f to 16f, MouthKind.Frown, mouthY = 64f),
    Mood.Scared to MoodFace(0f, 0.9f, -2f to -10f, -2f to 10f, MouthKind.O),
    Mood.Bored to MoodFace(0.4f, 1f, -1f to -6f, 2f to 4f, MouthKind.Flat, mouthY = 65f, mouthRot = -4f, pupil = Offset(2.9f, 0.3f)),
    Mood.Sleep to MoodFace(1f, 0.45f, 0.5f to -3f, 0.5f to 3f, MouthKind.SmallO, mouthY = 64f, eye = EyeKind.Sleep),
    Mood.Happy to MoodFace(0f, 0.7f, -1.6f to -7f, -1.6f to 7f, MouthKind.Smile, eye = EyeKind.Happy, blush = 0.26f),
    Mood.Worried to MoodFace(0f, 1f, -1.5f to -14f, -1.5f to 14f, MouthKind.Wobble, mouthY = 64f, pupil = Offset(0f, -0.6f)),
    Mood.Closed to MoodFace(1f, 0.5f, CALM_L, CALM_R, MouthKind.Flat, mouthY = 64f, eye = EyeKind.Closed),
)

private object ButtonPainter {

    fun draw(
        s: DrawScope,
        look: Look,
        face: Face,
        t: Float,
        legs: Boolean,
        blink: Boolean,
        flash: Boolean,
        tears: Boolean,
        facing: Float,
    ) = with(s) {
        val c = if (flash) Color.White else hex(look.color)
        val cd = if (flash) Color(0xFFDDDDDD) else hex(look.dark)
        val fx = facing * 5f
        val m = MOODS[face.mood] ?: MOODS.getValue(Mood.Calm)

        groundShadow(legs)
        if (legs) legs()
        Wardrobe.earsBehind(this, look.ears, c, cd)
        body(c, cd, Wardrobe.baseColor(look.outfit) ?: cd, flash)
        if (!flash) dirt(face)
        face(look, face, m, c, t, blink, tears, fx)
        Wardrobe.earsFront(this, look.ears)
        Wardrobe.outfit(this, look.outfit)
        Wardrobe.neck(this, look.scarf)
        Wardrobe.eyesOver(this, look.eyes, fx, t)
        Wardrobe.hat(this, look.hat)
        if (face.dirty >= 2) flyAndStink(t)
    }

    // ---- body ---------------------------------------------------------------

    private fun DrawScope.groundShadow(legs: Boolean) {
        val y = if (legs) 120f else 107f
        softOval(Offset(50f, y), 44f, 8f, rgba(40, 20, 10, 0.26f))
    }

    private fun DrawScope.legs() {
        for (x in floatArrayOf(36.5f, 63.5f)) {
            drawRoundRect(INK, Offset(x - 3.5f, 90f), Size(7f, 26f), CornerRadius(3f))
            // shoe
            drawOval(INK, Offset(x - 6.3f + 1f, 111.7f), Size(12.6f, 8.3f))
            drawOval(Color.White.copy(alpha = 0.16f), Offset(x - 3.2f, 112.8f), Size(5f, 2f))
        }
    }

    private fun DrawScope.body(c: Color, cd: Color, base: Color, flash: Boolean) {
        // the darker base under the button
        drawCircle(base, 50f, Offset(50f, 55f))
        if (flash) {
            drawCircle(Color.White, 50f, Offset(50f, 50f))
            return
        }
        val lit = lerp(c, Color.White, 0.13f)
        val deep = lerp(c, cd, 0.55f)
        val mid = Offset(50f, 50f)
        drawCircle(Brush.radialGradient(0f to lit, 0.52f to c, 1f to deep, center = Offset(40f, 30f), radius = 92f), 50f, mid)
        // a soft glow of bounce light at the bottom
        val glow = lerp(c, Color.White, 0.12f)
        drawCircle(
            Brush.radialGradient(0f to glow, 0.39f to glow.copy(alpha = 0.7f), 1f to glow.copy(alpha = 0f), center = Offset(50f, 120f), radius = 60f),
            50f, mid,
        )
        // inner shading: dark along the bottom edge, lit along the top
        drawCircle(
            Brush.radialGradient(0.74f to Color.Transparent, 1f to rgba(20, 10, 5, 0.26f), center = Offset(50f, 44f), radius = 56f),
            50f, mid,
        )
        drawCircle(
            Brush.radialGradient(0.86f to Color.Transparent, 1f to Color.White.copy(alpha = 0.22f), center = Offset(50f, 53f), radius = 52f),
            50f, mid,
        )
        // thin lit rim
        drawCircle(Color.White.copy(alpha = 0.12f), 49.4f, mid, style = Stroke(1.2f))
        // keycap bevel ring
        drawOval(BEVEL, Offset(7f, 7f), Size(86f, 84f), style = Stroke(1.1f))
        // soft specular, and a small crisp glint
        rotate(-24f, Offset(39f, 16f)) {
            scale(1f, 16f / 34f, Offset(39f, 16f)) {
                drawCircle(SPECULAR, 17f, Offset(39f, 16f))
            }
        }
        rotate(-40f, Offset(26f, 22f)) {
            drawOval(Color.White.copy(alpha = 0.34f), Offset(22.5f, 20.6f), Size(7f, 2.8f))
        }
    }

    private val BEVEL = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.12f),
        0.55f to Color.White.copy(alpha = 0.06f),
        0.85f to Color.Black.copy(alpha = 0.12f),
        startY = 7f, endY = 91f,
    )
    private val SPECULAR = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.36f),
        0.55f to Color.White.copy(alpha = 0.09f),
        0.75f to Color.Transparent,
        center = Offset(39f, 16f), radius = 24f,
    )

    // ---- dirt (hub-panes.css [data-dirty]) ------------------------------------

    private class Smudge(val x: Float, val y: Float, val rIn: Float, val rOut: Float, val color: Color)

    private val SMUDGES = listOf(
        Smudge(24f, 62f, 7f, 9f, rgba(70, 48, 25, 0.5f)),
        Smudge(70f, 70f, 9f, 11f, rgba(58, 40, 20, 0.55f)),
        Smudge(48f, 86f, 8f, 10f, rgba(80, 60, 30, 0.45f)),
        Smudge(78f, 36f, 5f, 7f, rgba(70, 48, 25, 0.4f)),
        Smudge(30f, 30f, 4f, 6f, rgba(90, 70, 40, 0.35f)),
        // a few specks so it reads as grime, not spots
        Smudge(63f, 20f, 1.6f, 2.6f, rgba(70, 48, 25, 0.45f)),
        Smudge(16f, 44f, 1.8f, 2.8f, rgba(70, 48, 25, 0.4f)),
        Smudge(85f, 58f, 1.5f, 2.5f, rgba(70, 48, 25, 0.45f)),
    )
    private val SMUDGE_BRUSHES by lazy {
        SMUDGES.map {
            Brush.radialGradient(
                0f to it.color, (it.rIn / it.rOut) to it.color.copy(alpha = it.color.alpha * 0.85f), 1f to Color.Transparent,
                center = Offset(it.x, it.y), radius = it.rOut,
            )
        }
    }
    private val GRIME_BOTTOM = Brush.radialGradient(
        0f to rgba(60, 42, 22, 0.35f), 1f to Color.Transparent, center = Offset(50f, 100f), radius = 60f,
    )

    private fun DrawScope.dirt(face: Face) {
        val g = face.grime.coerceIn(0f, 1f)
        if (face.dirty <= 0 && g < 0.02f) return
        val a = if (face.dirty > 0) (g * 1.3f).coerceIn(0.25f, 0.95f) else (g * 1.3f).coerceAtMost(0.95f)
        clipPath(Shapes.body) {
            drawRect(GRIME_BOTTOM, Offset.Zero, Size(100f, 100f), alpha = a)
            SMUDGES.forEachIndexed { i, sm ->
                drawCircle(SMUDGE_BRUSHES[i], sm.rOut, Offset(sm.x, sm.y), alpha = a)
            }
        }
    }

    // ---- the face ---------------------------------------------------------------

    private fun blinkScale(t: Float): Float {
        val p = (t % 2.8f) / 2.8f
        if (p < 0.92f) return 1f
        val tri = (1f - abs((p - 0.955f) / 0.035f)).coerceIn(0f, 1f)
        return 1f - 0.9f * tri
    }

    private fun DrawScope.face(look: Look, face: Face, m: MoodFace, c: Color, t: Float, blink: Boolean, tears: Boolean, fx: Float) {
        val eyes = look.eyes
        val hidesEyes = eyes in Wardrobe.EYES_COVER
        val sy = if (blink && m.eye == EyeKind.Open && eyes != "googly") blinkScale(t) else 1f
        val lidBoost = when (eyes) {
            "sleepy" -> 0.48f
            "chill" -> 0.4f
            else -> 0f
        }
        var lid = maxOf(m.lid, lidBoost)
        if (face.sleepy) lid = maxOf(lid, 0.22f)
        val lidColor = lerp(c, Color.White, 0.1f)

        // blush first, under everything
        if (m.blush > 0f) {
            softOval(Offset(28.5f + fx, 52.5f), 6.5f, 3.4f, rgba(255, 110, 130, m.blush))
            softOval(Offset(71.5f + fx, 52.5f), 6.5f, 3.4f, rgba(255, 110, 130, m.blush))
        }

        if (!hidesEyes) {
            when (eyes) {
                "googly" -> googly(t, fx, face.sleepy)
                "hearts", "stars" -> Unit // drawn by Wardrobe.eyesOver
                else -> for (side in 0..1) {
                    val ex = (if (side == 0) 35.5f else 64.5f) + fx
                    eye(Offset(ex, 39.5f), m, lid, lidColor, face.sleepy, sy, facingShift = fx * 0.3f)
                }
            }
        }
        brows(m, fx)
        mouth(m, fx)
        if (tears) tears(t, fx)
    }

    private fun DrawScope.eye(at: Offset, m: MoodFace, lid: Float, lidColor: Color, sleepy: Boolean, sy: Float, facingShift: Float) {
        translate(at.x, at.y) {
            if (sleepy) darkCircles()
            when (m.eye) {
                EyeKind.Sleep -> {
                    val p = Path().apply { moveTo(-6f, -0.5f); quadraticTo(0f, 4.8f, 6f, -0.5f) }
                    drawPath(p, INK, style = Stroke(2.3f, cap = StrokeCap.Round))
                    return@translate
                }
                EyeKind.Closed -> {
                    val p = Path().apply { moveTo(-6.2f, 0.6f); quadraticTo(0f, 2.6f, 6.2f, 0.6f) }
                    drawPath(p, INK, style = Stroke(2.3f, cap = StrokeCap.Round))
                    return@translate
                }
                EyeKind.Happy -> {
                    val p = Path().apply { moveTo(-6f, 2.8f); quadraticTo(0f, -5.6f, 6f, 2.8f) }
                    drawPath(p, INK, style = Stroke(2.6f, cap = StrokeCap.Round))
                    return@translate
                }
                EyeKind.Open -> Unit
            }
            scale(1f, sy, Offset.Zero) {
                drawPath(Shapes.eye, EYE_WHITE)
                clipPath(Shapes.eye) {
                    val pc = Offset(facingShift + m.pupil.x, 1.5f + m.pupil.y)
                    drawOval(INK, Offset(pc.x - 3.22f, pc.y - 3.6f), Size(6.44f, 7.2f))
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.05f, Offset(pc.x - 1.2f, pc.y - 1.3f))
                    if (lid > 0.001f) {
                        val ly = -7.5f + 15f * lid
                        drawRect(lidColor, Offset(-8f, -8f), Size(16f, ly + 8f))
                        drawLine(INK.copy(alpha = 0.55f), Offset(-8f, ly), Offset(8f, ly), strokeWidth = 1.1f)
                    }
                }
            }
        }
    }

    private val EYE_WHITE = Brush.verticalGradient(0f to Color(0xFFEDE5DE), 0.45f to Color.White, startY = -7.5f, endY = 7.5f)

    private fun DrawScope.darkCircles() {
        withTransform({ translate(0f, 4.4f); scale(1.5f, 1.35f, Offset.Zero) }) {
            drawPath(Shapes.eye, rgba(86, 54, 120, 0.2f))
        }
        withTransform({ translate(0f, 2.8f); scale(1.2f, 1.18f, Offset.Zero) }) {
            drawPath(Shapes.eye, rgba(86, 54, 120, 0.55f))
        }
    }

    private fun DrawScope.googly(t: Float, fx: Float, sleepy: Boolean) {
        for (side in 0..1) {
            val cx = (if (side == 0) 31.5f else 68.5f) + fx
            val cy = 43f
            if (sleepy) {
                drawCircle(rgba(86, 54, 120, 0.5f), 13.5f, Offset(cx, cy + 2.6f))
            }
            drawCircle(INK, 13f, Offset(cx, cy))
            drawCircle(Color.White, 11f, Offset(cx, cy))
            // the pupil rattles around (googly keyframes, the second eye out of step)
            val ph = t / 1.6f * Math.PI.toFloat() + if (side == 1) 0.7f / 1.6f * Math.PI.toFloat() else 0f
            val p = (1f - kotlin.math.cos(ph)) / 2f
            val wob = sin(t * 9f + side) * 0.5f
            val dx = -1.85f + 4.15f * p + wob
            val dy = -2.3f + 3.7f * p
            drawCircle(INK, 4.62f, Offset(cx - 0.44f + dx, cy + 3.7f + dy - 2f))
            drawCircle(Color.White.copy(alpha = 0.85f), 1f, Offset(cx - 1.8f + dx, cy + 0.4f + dy))
        }
    }

    private fun DrawScope.brows(m: MoodFace, fx: Float) {
        if (m.browAlpha <= 0f) return
        val col = INK.copy(alpha = m.browAlpha)
        for (side in 0..1) {
            val (dy, rot) = if (side == 0) m.browL else m.browR
            val cx = (if (side == 0) 36.5f else 63.5f) + fx
            val cy = 23.6f + dy
            rotate(rot, Offset(cx, cy)) {
                drawRoundRect(col, Offset(cx - 7.5f, cy - 1.6f), Size(15f, 3.2f), CornerRadius(1.6f))
            }
        }
    }

    private fun DrawScope.mouth(m: MoodFace, fx: Float) {
        val x = 50f + fx
        val y = m.mouthY
        when (m.mouth) {
            MouthKind.Smirk -> rotate(-5f, Offset(x, y + 3.5f)) {
                val p = Path().apply {
                    moveTo(x - 8.5f, y + 0.8f)
                    quadraticTo(x - 6f, y + 6.6f, x, y + 6.3f)
                    quadraticTo(x + 7f, y + 6f, x + 8.5f, y)
                }
                drawPath(p, INK, style = Stroke(3f, cap = StrokeCap.Round))
            }
            MouthKind.Smug -> {
                val cx = x + 4.4f
                rotate(-12f, Offset(cx, y + 3.5f)) {
                    val p = Path().apply {
                        moveTo(cx - 9.5f, y + 1f)
                        quadraticTo(cx - 6f, y + 6.6f, cx + 1f, y + 6.2f)
                        quadraticTo(cx + 8f, y + 5.8f, cx + 9.5f, y)
                    }
                    drawPath(p, INK, style = Stroke(3f, cap = StrokeCap.Round))
                }
            }
            MouthKind.Flat -> rotate(m.mouthRot, Offset(x, y + 2f)) {
                drawRoundRect(INK, Offset(x - 10f, y), Size(20f, 4f), CornerRadius(2f))
            }
            MouthKind.Frown -> {
                val p = Path().apply {
                    moveTo(x - 8.2f, y + 6.5f)
                    quadraticTo(x - 7.6f, y + 1.7f, x, y + 1.7f)
                    quadraticTo(x + 7.6f, y + 1.7f, x + 8.2f, y + 6.5f)
                }
                drawPath(p, INK, style = Stroke(3.4f, cap = StrokeCap.Round))
            }
            MouthKind.O -> {
                drawOval(MOUTH_IN, Offset(x - 7f, y), Size(14f, 17f))
                drawOval(INK, Offset(x - 7f, y), Size(14f, 17f), style = Stroke(1.6f))
                drawOval(rgba(210, 100, 110, 0.7f), Offset(x - 4f, y + 10f), Size(8f, 5f))
            }
            MouthKind.SmallO -> drawOval(INK, Offset(x - 5f, y), Size(10f, 7f))
            MouthKind.Smile -> {
                val p = Path().apply {
                    moveTo(x - 9.5f, y + 0.8f)
                    quadraticTo(x, y + 2.6f, x + 9.5f, y + 0.8f)
                    quadraticTo(x + 8.6f, y + 11.5f, x, y + 11.5f)
                    quadraticTo(x - 8.6f, y + 11.5f, x - 9.5f, y + 0.8f)
                    close()
                }
                drawPath(p, MOUTH_IN)
                clipPath(p) { drawOval(Color(0xFFE0707A), Offset(x - 6f, y + 6.5f), Size(12f, 9f)) }
                drawPath(p, INK, style = Stroke(1.6f))
            }
            MouthKind.Wobble -> {
                val p = Path().apply {
                    moveTo(x - 7.5f, y + 3f)
                    quadraticTo(x - 3.75f, y - 0.2f, x, y + 3f)
                    quadraticTo(x + 3.75f, y + 6.2f, x + 7.5f, y + 3f)
                }
                drawPath(p, INK, style = Stroke(2.6f, cap = StrokeCap.Round))
            }
        }
    }

    private fun DrawScope.tears(t: Float, fx: Float) {
        val col = Color(0xFF8FB8D8)
        for (side in 0..1) {
            val ex = (if (side == 0) 33f else 67f) + fx + (if (side == 0) -2f else 2f)
            for (i in 0..1) {
                val p = ((t * 0.9f + i * 0.5f + side * 0.27f) % 1f)
                val y = 47f + p * 22f
                val a = if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f * 0.8f
                val drop = Path().apply {
                    moveTo(ex, y - 4.2f)
                    quadraticTo(ex + 2.8f, y - 0.4f, ex + 2.6f, y + 0.8f)
                    quadraticTo(ex + 2.2f, y + 3.2f, ex, y + 3.2f)
                    quadraticTo(ex - 2.2f, y + 3.2f, ex - 2.6f, y + 0.8f)
                    quadraticTo(ex - 2.8f, y - 0.4f, ex, y - 4.2f)
                    close()
                }
                drawPath(drop, col, alpha = a)
                drawCircle(Color.White, 0.8f, Offset(ex - 0.9f, y + 0.4f), alpha = a * 0.8f)
            }
        }
    }

    // ---- filthy: a fly buzzing round and stink rising --------------------------

    private fun DrawScope.flyAndStink(t: Float) {
        val stink = Color(0xFF8FB05A)
        for (i in 0..2) {
            val p = ((t / 3f) + i / 3f) % 1f
            val a = if (p < 0.4f) p / 0.4f * 0.8f else 0.8f * (1f - (p - 0.4f) / 0.6f)
            val bx = floatArrayOf(14f, 86f, 26f)[i]
            val by = 18f - p * 34f
            val w = Path().apply {
                moveTo(bx, by + 10f)
                quadraticTo(bx - 3.2f, by + 7.5f, bx, by + 5f)
                quadraticTo(bx + 3.2f, by + 2.5f, bx, by)
                quadraticTo(bx - 3.2f, by - 2.5f, bx, by - 5f)
            }
            rotate(if (i == 1) 10f else -8f + p * 10f, Offset(bx, by)) {
                drawPath(w, stink, alpha = a, style = Stroke(2.2f, cap = StrokeCap.Round))
            }
        }
        // the fly: a wandering loop round the top of the head, with jitter
        val x = 50f + 32f * sin(t * 2.4f) + 3f * sin(t * 11f)
        val y = -4f + 9f * sin(t * 3.7f + 1f) + 2f * kotlin.math.cos(t * 13f)
        val dir = kotlin.math.cos(t * 2.4f) // which way it is heading
        val flap = 0.55f + 0.45f * abs(sin(t * 70f))
        translate(x, y) {
            scale(if (dir < 0) -1f else 1f, 1f, Offset.Zero) {
                drawOval(rgba(220, 235, 245, 0.75f), Offset(-3.6f, -4.6f * flap - 0.6f), Size(4.6f, 4.6f * flap))
                drawOval(rgba(220, 235, 245, 0.6f), Offset(-1.2f, -4.2f * flap - 0.6f), Size(4.2f, 4.2f * flap))
                drawOval(Color(0xFF1F1B1D), Offset(-3.6f, -1.6f), Size(6.4f, 3.6f))
                drawCircle(Color(0xFF1F1B1D), 1.6f, Offset(3.2f, -0.2f))
                drawCircle(Color(0xFFB0372F), 0.6f, Offset(3.7f, -0.6f))
            }
        }
    }
}

/** A soft-edged oval (radial fade), for shadows, blush and glows. */
internal fun DrawScope.softOval(center: Offset, rx: Float, ry: Float, color: Color) {
    if (rx <= 0f || ry <= 0f) return
    scale(1f, ry / rx, center) {
        drawCircle(
            Brush.radialGradient(0f to color, 0.45f to color.copy(alpha = color.alpha * 0.75f), 1f to color.copy(alpha = 0f), center = center, radius = rx),
            rx, center,
        )
    }
}
