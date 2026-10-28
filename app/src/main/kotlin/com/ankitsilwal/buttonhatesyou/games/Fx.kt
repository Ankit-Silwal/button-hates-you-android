package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal const val WHITE = 0xFFFFFFFF.toInt()
internal const val INK = 0xFF2B1D17.toInt()

/* ---------- text ---------- */

// Bold, centred text drawn straight on the canvas (no allocation per call).
internal object Txt {
    private val face: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val heavy: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textAlign = Paint.Align.CENTER
    }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fm = Paint.FontMetrics()

    fun width(s: String, size: Float, black: Boolean = false): Float {
        fillP.textSize = size
        fillP.typeface = if (black) heavy else face
        return fillP.measureText(s)
    }

    // Draws `s` with its middle at y (like textBaseline = 'middle').
    fun draw(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = WHITE,
        outline: Int = 0,
        outlineW: Float = 0f,
        align: Paint.Align = Paint.Align.CENTER,
        black: Boolean = false,
        alpha: Float = 1f,
    ) {
        if (s.isEmpty() || size <= 0f || alpha <= 0f || x.isNaN() || y.isNaN()) return
        val tf = if (black) heavy else face
        fillP.textSize = size
        fillP.typeface = tf
        fillP.textAlign = align
        fillP.getFontMetrics(fm)
        val base = y - (fm.ascent + fm.descent) / 2f
        val a = clamp(alpha, 0f, 1f)
        if (outlineW > 0f && outline ushr 24 != 0) {
            strokeP.textSize = size
            strokeP.typeface = tf
            strokeP.textAlign = align
            strokeP.strokeWidth = outlineW
            strokeP.color = outline
            strokeP.alpha = ((outline ushr 24) * a).toInt()
            c.drawText(s, x, base, strokeP)
        }
        fillP.color = color
        fillP.alpha = ((color ushr 24) * a).toInt()
        c.drawText(s, x, base, fillP)
    }
}

/* ---------- score HUD ---------- */

private var lastScore = Int.MIN_VALUE
private var lastScoreText = ""
private val HALO = col("#1a0b24")
private val SCORE_OUTLINE = rgba(30, 14, 40, 0.28f)
private val SUB_COLOR = rgba(255, 255, 255, 0.92f)

// The score in the top middle (like the web drawScore), with an optional line under it.
internal fun Canvas.drawScore(score: Int, sub: String? = null) {
    if (score != lastScore) {
        lastScore = score
        lastScoreText = score.toString()
    }
    drawScore(lastScoreText, sub)
}

internal fun Canvas.drawScore(score: String, sub: String? = null) {
    softLight(W / 2f, 92f, 90f, HALO, 0.18f)
    Txt.draw(this, score, W / 2f, 95f, 46f, SCORE_OUTLINE, SCORE_OUTLINE, 6f, black = true)
    Txt.draw(this, score, W / 2f, 92f, 46f, WHITE, black = true)
    if (sub != null) {
        Txt.draw(this, sub, W / 2f, 127f, 15f, SCORE_OUTLINE, SCORE_OUTLINE, 4f)
        Txt.draw(this, sub, W / 2f, 126f, 15f, SUB_COLOR)
    }
}

/* ---------- speech ---------- */

private val bubbleFill = fill(WHITE)
private val bubbleLine = stroke(rgba(40, 20, 50, 0.14f), 1.5f)
private val bubblePath = Path()
private val bubbleRect = RectF()

// A speech bubble whose tail points down at (x, y).
internal fun Canvas.bubble(x: Float, y: Float, str: String, size: Float = 15f, bg: Int = WHITE, color: Int = INK) {
    if (x.isNaN() || y.isNaN()) return
    val tw = Txt.width(str, size)
    val w = tw + 20f
    val h = size + 14f
    val bx = clamp(x - w / 2f, 6f, W - w - 6f)
    val by = y - h - 10f
    shadow(bx + w / 2f, by + h + 4f, w * 0.55f, 0.22f, 7f)
    val tx = clamp(x, bx + 12f, bx + w - 12f)
    bubblePath.reset()
    bubbleRect.set(bx, by, bx + w, by + h)
    bubblePath.addRoundRect(bubbleRect, 11f, 11f, Path.Direction.CW)
    bubblePath.moveTo(tx - 7f, by + h - 1f)
    bubblePath.lineTo(x, y)
    bubblePath.lineTo(tx + 7f, by + h - 1f)
    bubblePath.close()
    bubbleFill.color = bg
    drawPath(bubblePath, bubbleFill)
    bubbleRect.set(bx + 0.75f, by + 0.75f, bx + w - 0.75f, by + h - 0.75f)
    drawRoundRect(bubbleRect, 10.5f, 10.5f, bubbleLine)
    Txt.draw(this, str, bx + w / 2f, by + h / 2f + 1f, size, color)
}

// Something the button says now and then: say(), update(dt), then draw above its head.
internal class Mouth {
    var line: String? = null
        private set
    var left = 0f
        private set

    fun say(str: String, sec: Float = 1.8f) {
        line = str
        left = sec
    }

    // Says one of `lines`, but not too often.
    fun maybe(lines: Array<String>, chance: Float = 0.35f, sec: Float = 1.8f) {
        if (left <= 0f && Random.nextFloat() < chance) say(pick(lines), sec)
    }

    fun update(dt: Float) {
        left -= dt
        if (left <= 0f) line = null
    }

    fun draw(c: Canvas, x: Float, y: Float) {
        line?.let { c.bubble(x, y, it) }
    }
}

/* ---------- particles ---------- */

// Glowing sparks, shockwave rings, motes and floating words in one pooled
// system (no allocation per particle). Ports both the web kit's Sparks
// (sparks/float) and fx.js's Fx (burst/mote/ring).
internal class Fx(private val max: Int = 320) {
    private companion object {
        const val DEAD = 0
        const val SPARK = 1 // kit spark: gravity, glow halo, streak, white core
        const val KRING = 2 // kit burst's quick shockwave
        const val TEXT = 3 // a floating word
        const val EMBER = 4 // fx burst: additive streaked glow
        const val MOTE = 5 // fx mote: additive slow glow
        const val DUST = 6 // fx mote with add = false
        const val RING = 7 // fx ring
    }

    private val kind = IntArray(max)
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val r = FloatArray(max)
    private val g = FloatArray(max) // gravity, or ring width
    private val drag = FloatArray(max)
    private val color = IntArray(max)
    private val str = arrayOfNulls<String>(max)
    private var cursor = 0
    private val line = stroke(WHITE, 1f)
    private val dot = fill(WHITE)

    private fun slot(): Int {
        for (k in 0 until max) {
            val i = (cursor + k) % max
            if (kind[i] == DEAD) {
                cursor = (i + 1) % max
                return i
            }
        }
        val i = cursor
        cursor = (cursor + 1) % max
        return i
    }

    private fun set(i: Int, k: Int, px: Float, py: Float, pvx: Float, pvy: Float, l: Float, c: Int) {
        kind[i] = k
        x[i] = px
        y[i] = py
        vx[i] = pvx
        vy[i] = pvy
        life[i] = l
        maxLife[i] = l
        color[i] = c
        str[i] = null
    }

    fun clear() {
        kind.fill(DEAD)
        str.fill(null)
    }

    // The kit's Sparks.burst: n sparks and (for n >= 5) a quick ring.
    fun sparks(px: Float, py: Float, c: Int, n: Int = 10, speed: Float = 220f) {
        if (px.isNaN() || py.isNaN()) return
        if (n >= 5) {
            val i = slot()
            set(i, KRING, px, py, 0f, 0f, 0.32f, c)
            r[i] = 10f + speed * 0.12f
        }
        for (k in 0 until n) {
            val a = Random.nextFloat() * TAU
            val v = speed * (0.4f + Random.nextFloat() * 0.6f)
            val i = slot()
            set(i, SPARK, px, py, cos(a) * v, sin(a) * v, 0.5f + Random.nextFloat() * 0.3f, c)
            r[i] = 2f + Random.nextFloat() * 3f
        }
    }

    // A number or word that floats up and fades.
    fun float(px: Float, py: Float, s: String, c: Int = WHITE) {
        if (px.isNaN() || py.isNaN()) return
        val i = slot()
        set(i, TEXT, px, py, 0f, -70f, 0.9f, c)
        str[i] = s
    }

    // fx.js burst: n additive glowing embers.
    fun burst(
        px: Float, py: Float, c: Int, n: Int = 12, speed: Float = 260f,
        gravity: Float = 500f, size: Float = 3f, life: Float = 0.6f, drag: Float = 2.2f, up: Float = 0f,
    ) {
        if (px.isNaN() || py.isNaN()) return
        for (k in 0 until n) {
            val a = Random.nextFloat() * TAU
            val v = speed * (0.35f + Random.nextFloat() * 0.65f)
            val i = slot()
            set(i, EMBER, px, py, cos(a) * v, sin(a) * v - up, life * (0.6f + Random.nextFloat() * 0.6f), c)
            g[i] = gravity
            this.drag[i] = drag
            r[i] = size * (0.6f + Random.nextFloat() * 0.8f)
        }
    }

    // Slow floating motes (pollen); add = false makes a soft dust puff.
    fun mote(px: Float, py: Float, c: Int, mvx: Float = 0f, mvy: Float = -20f, mr: Float = 2f, life: Float = 2f, add: Boolean = true, drag: Float = 0f) {
        val i = slot()
        set(i, if (add) MOTE else DUST, px, py, mvx, mvy, life, c)
        g[i] = 0f
        this.drag[i] = drag
        r[i] = mr
    }

    // An expanding shockwave ring.
    fun ring(px: Float, py: Float, c: Int, radius: Float = 60f, life: Float = 0.35f, width: Float = 6f) {
        val i = slot()
        set(i, RING, px, py, 0f, 0f, life, c)
        r[i] = radius
        g[i] = width
    }

    fun update(dt: Float) {
        for (i in 0 until max) {
            val k = kind[i]
            if (k == DEAD) continue
            when (k) {
                SPARK -> {
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt
                    vy[i] += 400f * dt
                    vx[i] *= 1f - min(1f, dt * 1.5f)
                }
                TEXT -> {
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt
                    vy[i] *= 1f - min(1f, dt * 2.2f)
                }
                EMBER, MOTE, DUST -> {
                    val d = max(0f, 1f - drag[i] * dt)
                    vx[i] *= d
                    vy[i] = vy[i] * d + g[i] * dt
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt
                }
            }
            life[i] -= dt
            if (life[i] <= 0f || x[i].isNaN() || y[i].isNaN()) {
                kind[i] = DEAD
                str[i] = null
            }
        }
    }

    fun draw(c: Canvas) {
        // shapes first, then words on top
        for (i in 0 until max) {
            val k = kind[i]
            if (k == DEAD || k == TEXT) continue
            val kk = life[i] / maxLife[i]
            val px = x[i]
            val py = y[i]
            when (k) {
                SPARK -> {
                    val a = clamp(life[i] * 2f, 0f, 1f)
                    val gr = r[i] * 4f
                    c.sprite(glowSprite(color[i]), px - gr, py - gr, gr * 2f, gr * 2f, a * 0.6f)
                    line.color = color[i]
                    line.alpha = (a * 255f).toInt()
                    line.strokeWidth = r[i] * (0.6f + 0.6f * kk) * 1.6f
                    c.drawLine(px - vx[i] * 0.03f, py - vy[i] * 0.03f, px, py + 0.01f, line)
                    if (kk > 0.55f) {
                        dot.color = WHITE
                        dot.alpha = (clamp((kk - 0.55f) * 2f, 0f, 1f) * 255f).toInt()
                        c.drawCircle(px, py, r[i] * 0.45f, dot)
                    }
                }
                KRING -> {
                    val rr = r[i] * (1f - kk * kk) + 4f
                    line.color = color[i]
                    line.alpha = (kk * 0.85f * 255f).toInt()
                    line.strokeWidth = 1f + kk * 5f
                    line.style = Paint.Style.STROKE
                    c.drawCircle(px, py, rr, line)
                }
                EMBER, MOTE -> {
                    val rr = r[i] * (0.5f + kk * 0.5f)
                    val a = min(1f, kk * 1.6f)
                    c.sprite(glowSprite(color[i]), px - rr * 4f, py - rr * 4f, rr * 8f, rr * 8f, a, add = true)
                    if (k == EMBER) {
                        line.color = color[i]
                        line.alpha = (a * 255f).toInt()
                        line.strokeWidth = rr
                        line.xfermode = ADD
                        c.drawLine(px, py, px - vx[i] * 0.03f, py - vy[i] * 0.03f + 0.01f, line)
                        line.xfermode = null
                    }
                }
                DUST -> {
                    val rr = r[i] * (2.2f - kk)
                    c.sprite(glowSprite(color[i]), px - rr * 2f, py - rr * 2f, rr * 4f, rr * 4f, kk * 0.7f)
                }
                RING -> {
                    val p = 1f - kk
                    line.color = color[i]
                    line.alpha = ((1f - p) * 0.9f * 255f).toInt()
                    line.strokeWidth = g[i] * (1f - p) + 1f
                    line.xfermode = ADD
                    c.drawCircle(px, py, r[i] * (0.25f + p * 0.75f), line)
                    line.xfermode = null
                }
            }
        }
        for (i in 0 until max) {
            if (kind[i] != TEXT) continue
            val s = str[i] ?: continue
            val a = clamp(life[i] * 2f, 0f, 1f)
            val age = maxLife[i] - life[i]
            val sc = if (age < 0.12f) 1.5f - age * 4.2f else 1f
            val size = 20f * sc
            Txt.draw(c, s, x[i], y[i] + 1.5f, size, rgba(30, 14, 40, 0.38f), rgba(30, 14, 40, 0.38f), 5f, alpha = a)
            Txt.draw(c, s, x[i], y[i], size, color[i], alpha = a)
        }
    }
}

/* ---------- the button ---------- */

private val REVIVED = arrayOf("You PAID for this?", "Diamonds. On me. Why.", "I was finally free.", "Round two. Ugh.", "Back again. Great.")

// A paid Continue brings the button back: a burst, a floating "Continue!" and a grumble.
internal fun reviveFx(fx: Fx, mouth: Mouth, ctx: GameContext, x: Float, y: Float, color: Int = WHITE) {
    fx.sparks(x, y, col("#8fe9ff"), 20, 300f)
    fx.sparks(x, y, color, 10, 200f)
    fx.float(x, y + 36f, "Continue!", col("#aef3ff"))
    mouth.say(pick(REVIVED), 2.2f)
    ctx.sfx.play("win")
    ctx.haptics.thud()
}

// The button at (x, y), `size` wide, optionally turned and faded. Everything
// goes through drawBobby (BobbyStub.kt), so the real renderer swaps in at one place.
internal fun DrawScope.bobby(
    c: Canvas,
    x: Float,
    y: Float,
    size: Float,
    look: Look,
    mood: Mood = Mood.Calm,
    t: Float = 0f,
    squash: Float = 0f,
    flash: Boolean = false,
    tears: Boolean = false,
    angle: Float = 0f,
    alpha: Float = 1f,
) {
    if (x.isNaN() || y.isNaN() || !(size > 0f) || alpha <= 0f) return
    val layered = alpha < 0.995f
    if (layered) {
        val m = size * 1.6f
        c.saveLayerAlpha(x - m, y - m, x + m, y + m, (alpha * 255f).toInt())
    } else {
        c.save()
    }
    if (angle != 0f && !angle.isNaN()) c.rotate(angle * DEG, x, y)
    drawBobby(Offset(x, y), size, look, mood, t, clamp(squash, -0.5f, 0.5f), flash, tears)
    c.restore()
}

// A heart centred on (x, y), `s` its half width.
private val heartPath = Path()
private val heartFill = fill(WHITE)
private val heartLine = stroke(WHITE, 2f)
internal fun Canvas.heart(x: Float, y: Float, s: Float, color: Int, outline: Int = 0) {
    heartPath.reset()
    heartPath.moveTo(x, y + s * 0.9f)
    heartPath.cubicTo(x - s * 1.4f, y - s * 0.1f, x - s * 0.8f, y - s * 1.3f, x, y - s * 0.45f)
    heartPath.cubicTo(x + s * 0.8f, y - s * 1.3f, x + s * 1.4f, y - s * 0.1f, x, y + s * 0.9f)
    heartPath.close()
    if (outline != 0) {
        heartLine.color = outline
        heartLine.strokeWidth = max(1.5f, s * 0.22f)
        drawPath(heartPath, heartLine)
    }
    heartFill.color = color
    drawPath(heartPath, heartFill)
}

// A five-pointed star.
private val starPath = Path()
internal fun Canvas.star(x: Float, y: Float, r: Float, paint: Paint, turn: Float = -PI_F / 2f) {
    starPath.reset()
    for (i in 0 until 10) {
        val a = turn + i * PI_F / 5f
        val rad = if (i % 2 == 1) r * 0.45f else r
        val px = x + cos(a) * rad
        val py = y + sin(a) * rad
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()
    drawPath(starPath, paint)
}

// Random screen-shake offset for a shake amount.
internal fun shakeBy(amount: Float): Float = if (amount > 0f) (Random.nextFloat() * 2f - 1f) * amount else 0f

internal fun absf(v: Float) = abs(v)
