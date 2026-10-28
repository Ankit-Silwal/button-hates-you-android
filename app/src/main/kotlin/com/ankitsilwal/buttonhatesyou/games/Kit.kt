package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LongSparseArray
import android.util.SparseArray
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Shared bits for the arcade games, ported from the web kit (src/adventure/kit.js
// and fx.js). Every game draws on the fixed 480x800 world with the Android
// canvas (drawContext.canvas.nativeCanvas), which is close to the HTML canvas
// the web version uses. Static art is painted once into bitmaps (Sprite).

internal const val W = WORLD_W
internal const val H = WORLD_H
internal const val TAU = (Math.PI * 2).toFloat()
internal const val PI_F = Math.PI.toFloat()
internal const val DEG = (180.0 / Math.PI).toFloat()

// Sprites are painted at this many pixels per world unit (a phone shows the
// world at roughly 2-2.5x), so cached art stays crisp.
internal const val RES = 2f

/* ---------- math ---------- */

internal fun rand(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)
internal fun chance(p: Float): Boolean = Random.nextFloat() < p
internal fun <T> pick(a: Array<T>): T = a[Random.nextInt(a.size)]

// NaN-safe clamp: a NaN collapses to the low end instead of spreading.
internal fun clamp(v: Float, a: Float, b: Float): Float = when {
    v.isNaN() -> a
    v < a -> a
    v > b -> b
    else -> v
}

internal fun hash(i: Float): Float {
    val s = sin(i * 127.1 + 311.7) * 43758.5453
    return (s - floor(s)).toFloat()
}

internal fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = kotlin.math.hypot(ax - bx, ay - by)

/* ---------- colour ---------- */

// "#rgb" or "#rrggbb" to an opaque ARGB int.
internal fun col(hex: String): Int {
    var h = hex.removePrefix("#")
    if (h.length == 3) h = "${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}"
    return (0xFF000000.toInt()) or h.toInt(16)
}

// `c` with alpha a (0..1).
internal fun rgba(c: Int, a: Float): Int = ((clamp(a, 0f, 1f) * 255f).roundToInt() shl 24) or (c and 0xFFFFFF)

// rgba(r, g, b, a) like CSS.
internal fun rgba(r: Int, g: Int, b: Int, a: Float): Int =
    ((clamp(a, 0f, 1f) * 255f).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

internal fun mix(a: Int, b: Int, k: Float): Int {
    val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * k).roundToInt()
    val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * k).roundToInt()
    val bl = ((a and 255) + ((b and 255) - (a and 255)) * k).roundToInt()
    return (0xFF000000.toInt()) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
}

internal fun lighten(c: Int, k: Float) = mix(c, 0xFFFFFFFF.toInt(), k)
internal fun darken(c: Int, k: Float) = mix(c, 0xFF000000.toInt(), k)

// The colour with its hue turned by `deg` degrees (keeps saturation and lightness).
internal fun hueShift(c: Int, deg: Float): Int {
    val hsv = FloatArray(3)
    // HSL like the web version
    val r = (c shr 16 and 255) / 255f
    val g = (c shr 8 and 255) / 255f
    val b = (c and 255) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    var h = 0f
    var s = 0f
    if (d > 0f) {
        s = d / (1f - kotlin.math.abs(2f * l - 1f))
        h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
    }
    hsv[0] = (((h + deg) % 360f) + 360f) % 360f
    // back from HSL
    val cc = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = cc * (1f - kotlin.math.abs((hsv[0] / 60f) % 2f - 1f))
    val m = l - cc / 2f
    val (r1, g1, b1) = when ((hsv[0] / 60f).toInt()) {
        0 -> Triple(cc, x, 0f)
        1 -> Triple(x, cc, 0f)
        2 -> Triple(0f, cc, x)
        3 -> Triple(0f, x, cc)
        4 -> Triple(x, 0f, cc)
        else -> Triple(cc, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF000000.toInt()) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

/* ---------- paints and gradients (for painting art once) ---------- */

internal fun fill(color: Int = 0xFF000000.toInt()) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

internal fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

internal fun lin(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Pair<Float, Int>): Shader =
    LinearGradient(
        x0, y0, x1, y1,
        IntArray(stops.size) { stops[it].second },
        FloatArray(stops.size) { stops[it].first },
        Shader.TileMode.CLAMP,
    )

// Like canvas createRadialGradient(cx, cy, r0, cx, cy, r): the inner radius
// is folded into the stop positions.
internal fun rad(cx: Float, cy: Float, r0: Float, r: Float, vararg stops: Pair<Float, Int>): Shader {
    val k = if (r > 0f) clamp(r0 / r, 0f, 0.99f) else 0f
    return RadialGradient(
        cx, cy, max(0.01f, r),
        IntArray(stops.size) { stops[it].second },
        FloatArray(stops.size) { k + stops[it].first * (1f - k) },
        Shader.TileMode.CLAMP,
    )
}

internal fun Paint.shade(s: Shader): Paint = apply { shader = s }

internal val ATOP = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
internal val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)
internal val DST_IN = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

/* ---------- sprites ---------- */

// A bitmap painted once, `w` x `h` world units big.
internal class Sprite(val bmp: Bitmap, val w: Float, val h: Float)

// Paints `w` x `h` world units into a new bitmap at `res` pixels per unit.
internal fun makeSprite(w: Float, h: Float, res: Float = RES, draw: (Canvas) -> Unit): Sprite {
    val bw = max(1, ceil(w * res).toInt())
    val bh = max(1, ceil(h * res).toInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(bw / w, bh / h)
    try {
        draw(c)
    } catch (_: Throwable) {
        // a broken painting is just left blank
    }
    return Sprite(bmp, w, h)
}

private val dst = RectF()
private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

// Draws a sprite with its top left at (x, y), stretched to w x h.
internal fun Canvas.sprite(s: Sprite, x: Float, y: Float, w: Float = s.w, h: Float = s.h, alpha: Float = 1f, add: Boolean = false) {
    if (alpha <= 0f || w <= 0f || h <= 0f || x.isNaN() || y.isNaN()) return
    dst.set(x, y, x + w, y + h)
    imgPaint.alpha = (min(1f, alpha) * 255f).toInt()
    imgPaint.xfermode = if (add) ADD else null
    drawBitmap(s.bmp, null, dst, imgPaint)
}

// Tiles a sprite across the frame, scrolled left by `off`.
internal fun Canvas.scrollTile(s: Sprite, off: Float, y: Float, h: Float = s.h) {
    val iw = s.w
    if (iw <= 1f || off.isNaN()) return
    var x = -(((off % iw) + iw) % iw)
    while (x < W) {
        sprite(s, x, y, iw + 0.5f, h)
        x += iw
    }
}

// A soft round light: white-hot core fading out through `color`.
private val glows = SparseArray<Sprite>()
internal fun glowSprite(color: Int): Sprite {
    val key = color or 0xFF000000.toInt()
    glows.get(key)?.let { return it }
    val s = makeSprite(128f, 128f, 1f) { c ->
        c.drawRect(
            0f, 0f, 128f, 128f,
            fill().shade(
                rad(
                    64f, 64f, 0f, 64f,
                    0f to rgba(lighten(key, 0.65f), 1f),
                    0.22f to rgba(key, 0.6f),
                    0.5f to rgba(key, 0.18f),
                    1f to rgba(key, 0f),
                ),
            ),
        )
    }
    glows.put(key, s)
    return s
}

// Additive glow of radius r at (x, y).
internal fun Canvas.glow(x: Float, y: Float, r: Float, color: Int, a: Float = 1f) {
    if (a <= 0f || r <= 0f) return
    sprite(glowSprite(color), x - r, y - r, r * 2f, r * 2f, a, add = true)
}

// The same light blended normally.
internal fun Canvas.softLight(x: Float, y: Float, r: Float, color: Int, a: Float = 1f) {
    if (a <= 0f || r <= 0f) return
    sprite(glowSprite(color), x - r, y - r, r * 2f, r * 2f, a)
}

// A soft dark ellipse: contact shadows.
private val shadowArt by lazy {
    makeSprite(64f, 32f, 1f) { c ->
        c.save()
        c.scale(1f, 0.5f)
        c.drawRect(
            0f, 0f, 64f, 64f,
            fill().shade(rad(32f, 32f, 0f, 32f, 0f to rgba(0, 0, 0, 0.85f), 0.5f to rgba(0, 0, 0, 0.4f), 1f to rgba(0, 0, 0, 0f))),
        )
        c.restore()
    }
}

internal fun Canvas.shadow(x: Float, y: Float, rx: Float, a: Float = 0.3f, ry: Float = rx * 0.3f) {
    if (a <= 0f || rx <= 0f) return
    sprite(shadowArt, x - rx, y - ry, rx * 2f, ry * 2f, a)
}

// Darkened corners over the whole frame (strength in steps of 0.05).
private val vignettes = LongSparseArray<Sprite>()
internal fun Canvas.vignette(a: Float, color: Int = 0xFF000000.toInt(), alpha: Float = 1f) {
    val k = (a * 20f).roundToInt()
    if (k <= 0) return
    val key = (k.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
    var s = vignettes.get(key)
    if (s == null) {
        val kk = k / 20f
        s = makeSprite(W, H, 0.5f) { c ->
            c.drawRect(
                0f, 0f, W, H,
                fill().shade(rad(W / 2f, H / 2f, H * 0.28f, H * 0.72f, 0f to rgba(color, 0f), 0.55f to rgba(color, kk * 0.35f), 1f to rgba(color, kk))),
            )
        }
        vignettes.put(key, s)
    }
    sprite(s, 0f, 0f, W, H, alpha)
}

// Soft light rays fanning from a point, drawn additively and slowly turning.
private val raysArt = SparseArray<Sprite>()
internal fun Canvas.rays(x: Float, y: Float, r: Float, color: Int, a: Float = 0.25f, turn: Float = 0f) {
    if (a <= 0f) return
    var s = raysArt.get(color)
    if (s == null) {
        s = makeSprite(256f, 256f, 1f) { c ->
            val p = fill().shade(rad(128f, 128f, 0f, 128f, 0f to rgba(color, 0.9f), 0.35f to rgba(color, 0.35f), 1f to rgba(color, 0f)))
            val oval = RectF(0f, 0f, 256f, 256f)
            for (i in 0 until 14) {
                val a0 = i / 14f * 360f
                val w = (0.06f + hash(i.toFloat()) * 0.1f) * DEG
                c.drawArc(oval, a0 - w, w * 2f, true, p)
            }
        }
        raysArt.put(color, s)
    }
    save()
    translate(x, y)
    rotate(turn * DEG)
    sprite(s, -r, -r, r * 2f, r * 2f, a, add = true)
    restore()
}

// A full-frame vertical gradient sprite (tiny, stretched).
internal fun skySprite(vararg stops: Int): Sprite = makeSprite(4f, 256f, 1f) { c ->
    val n = stops.size
    c.drawRect(0f, 0f, 4f, 256f, fill().shade(lin(0f, 0f, 0f, 256f, *Array(n) { (if (n > 1) it / (n - 1f) else 0f) to stops[it] })))
}

// The button blinks while it can't be hit (a few seconds after a Continue).
internal fun blinkAlpha(safe: Float, t: Float): Float = if (safe > 0f && floor(t * 12f).toInt() % 2 != 0) 0.35f else 1f
