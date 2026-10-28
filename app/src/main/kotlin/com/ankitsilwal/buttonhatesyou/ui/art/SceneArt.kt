package com.ankitsilwal.buttonhatesyou.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import com.ankitsilwal.buttonhatesyou.ui.common.Clock
import com.ankitsilwal.buttonhatesyou.ui.common.Sky
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The hand-drawn props of the four rooms (a port of src/hub/scene-art.jsx and
// the matching rules of src/hub/scene.css). Each draws into a rect in px.
object SceneArt {

    // [x %, y %, radius] of the stars in a night window
    private val STARS = arrayOf(floatArrayOf(12f, 18f, 2f), floatArrayOf(30f, 52f, 1.6f), floatArrayOf(48f, 14f, 2.2f), floatArrayOf(66f, 40f, 1.6f), floatArrayOf(82f, 20f, 2f), floatArrayOf(90f, 58f, 1.4f), floatArrayOf(20f, 70f, 1.4f), floatArrayOf(58f, 76f, 1.8f))

    enum class Dress { Curtain, Valance, Night }

    /* ------------------------------- the window ------------------------------ */

    /** A window onto the real time of day. [r] is the glass; frame and curtains spill around it. */
    fun window(ds: DrawScope, r: Rect, sky: Sky, clock: Clock, dress: Dress, t: Float) = with(ds) {
        val px = r.width / 100f
        val frame = Color(0xFFFFFAF0)
        // shadow, frame rings
        drawRoundRect(Color(0x33000000), Offset(r.left - 8 * px / 1.6f, r.top - 4f), Size(r.width + 16 * px / 1.6f, r.height + 22f), CornerRadius(16f))
        val ring = max(4f, r.width * 0.06f)
        drawRoundRect(Color(0xFFD8BC98), Offset(r.left - ring - 2f, r.top - ring - 2f), Size(r.width + 2 * ring + 4f, r.height + 2 * ring + 4f), CornerRadius(14f))
        drawRoundRect(frame, Offset(r.left - ring, r.top - ring), Size(r.width + 2 * ring, r.height + 2 * ring), CornerRadius(12f))
        val glass = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(8f))) }
        clipPath(glass) {
            val skyBrush = when (sky) {
                Sky.Day -> Brush.verticalGradient(listOf(Color(0xFF8FD0FF), Color(0xFFD8F0FF)), r.top, r.bottom)
                Sky.Dawn -> Brush.verticalGradient(0f to Color(0xFF8FA8E8), 0.78f to Color(0xFFFFC8A0), 1f to Color(0xFFFFE3B8), startY = r.top, endY = r.bottom)
                Sky.Dusk -> Brush.verticalGradient(0f to Color(0xFF5F6FC4), 0.7f to Color(0xFFF08E7A), 1f to Color(0xFFFFC78A), startY = r.top, endY = r.bottom)
                Sky.Night -> Brush.verticalGradient(listOf(Color(0xFF121A46), Color(0xFF2B3A7A)), r.top, r.bottom)
            }
            drawRect(skyBrush, r.topLeft, r.size)
            if (sky == Sky.Night) {
                STARS.forEachIndexed { i, s ->
                    val tw = 0.55f + 0.45f * sin(t * 2.1f + i * 0.9f)
                    drawCircle(Color.White.copy(alpha = tw), s[2] * px * 0.9f, Offset(r.left + s[0] / 100f * r.width, r.top + s[1] / 100f * r.height))
                }
                // the moon
                val mr = r.width * 0.12f
                val mc = Offset(r.left + r.width * 0.74f, r.top + r.height * 0.16f + mr)
                drawCircle(Brush.radialGradient(listOf(Color(0x73FFFAD2), Color.Transparent), mc, mr * 2.4f), mr * 2.4f, mc)
                drawCircle(Color(0xFFFDF6D8), mr, mc)
                drawCircle(Color(0x66A09678), mr * 0.9f, Offset(mc.x + mr * 0.35f, mc.y + mr * 0.12f))
                drawCircle(Color(0xFFFDF6D8), mr * 0.82f, Offset(mc.x - mr * 0.1f, mc.y - mr * 0.05f))
            } else {
                val h = clock.hours
                val p = ((h - 6f) / 13f).coerceIn(0f, 1f)
                val sx = min(88f, max(12f, (h - 6f) / 13f * 76f + 12f))
                val sy = 70f - sin(p * PI.toFloat()) * 52f
                val sr = r.width * 0.13f
                val sc = Offset(r.left + sx / 100f * r.width, r.top + sy / 100f * r.height)
                drawCircle(Brush.radialGradient(listOf(Color(0xB3FFE178), Color(0x00FFE178)), sc, sr * 2.6f), sr * 2.6f, sc)
                drawCircle(Color(0x59FFF3A8), sr * 1.3f, sc)
                drawCircle(Color(0xFFFFF3A8), sr, sc)
            }
            // clouds drift across
            val cloudA = when (sky) {
                Sky.Night -> 0.12f
                else -> 0.92f
            }
            val cloudC = if (sky == Sky.Dusk) Color(0xFFFFD7C8) else Color.White
            fun cloud(x: Float, y: Float, w: Float, a: Float) {
                val ch = r.height * 0.12f
                val cx = r.left + x * r.width
                val cy = r.top + y * r.height
                val c = cloudC.copy(alpha = cloudA * a)
                drawRoundRect(c, Offset(cx, cy), Size(w * r.width, ch), CornerRadius(ch / 2))
                drawCircle(c, ch * 0.6f, Offset(cx + w * r.width * 0.62f, cy))
                drawCircle(c, ch * 0.45f, Offset(cx + w * r.width * 0.3f, cy + ch * 0.1f))
            }
            val d1 = ((t / 40f) % 1f) * 1.6f - 0.5f
            val d2 = (((t + 20f) / 56f) % 1f) * 1.6f - 0.5f
            cloud(0.10f + d1, 0.22f, 0.34f, 1f)
            cloud(0.60f + d2 - 0.6f, 0.44f, 0.26f, 0.85f)
            // hills
            val hillsDim = when (sky) { Sky.Night -> 0.3f; Sky.Dusk -> 0.75f; else -> 1f }
            fun dim(c: Color) = Color(c.red * hillsDim, c.green * hillsDim, c.blue * min(1f, hillsDim * 1.2f), 1f)
            val hb = r.bottom + r.height * 0.02f
            drawOval(dim(Color(0xFF6FAE6D)), Offset(r.left - r.width * 0.4f, hb - r.height * 0.3f), Size(r.width * 1.3f, r.height * 0.6f))
            drawOval(dim(Color(0xFF5B9A63)), Offset(r.left + r.width * 0.25f, hb - r.height * 0.27f), Size(r.width * 1.3f, r.height * 0.54f))
        }
        // the bars
        val bar = max(3f, r.width * 0.05f)
        drawRect(Color(0xFFD8BC98), Offset(r.center.x - bar / 2 - 1, r.top), Size(bar + 2, r.height))
        drawRect(frame, Offset(r.center.x - bar / 2, r.top), Size(bar, r.height))
        drawRect(Color(0xFFD8BC98), Offset(r.left, r.top + r.height * 0.52f - 1), Size(r.width, bar + 2))
        drawRect(frame, Offset(r.left, r.top + r.height * 0.52f), Size(r.width, bar))
        // curtains / valance
        when (dress) {
            Dress.Curtain, Dress.Night -> {
                val a = if (dress == Dress.Night) Color(0xFF6A5AA8) else Color(0xFFE2674F)
                val b = if (dress == Dress.Night) Color(0xFF594A96) else Color(0xFFD4573F)
                val cw = r.width * 0.24f
                val top = r.top - ring - 10f
                val bottom = r.bottom - r.height * 0.08f
                for (side in 0..1) {
                    val x0 = if (side == 0) r.left - r.width * 0.16f else r.right + r.width * 0.16f - cw
                    val path = Path().apply {
                        moveTo(x0, top)
                        lineTo(x0 + cw, top)
                        if (side == 0) {
                            lineTo(x0 + cw, bottom - cw * 0.5f)
                            quadraticTo(x0 + cw, bottom, x0 + cw * 0.4f, bottom)
                            lineTo(x0, bottom)
                        } else {
                            lineTo(x0 + cw, bottom)
                            lineTo(x0 + cw * 0.6f, bottom)
                            quadraticTo(x0, bottom, x0, bottom - cw * 0.5f)
                        }
                        close()
                    }
                    drawPath(path, Color(0x40000000), alpha = 1f)
                    clipPath(path) {
                        drawRect(a, Offset(x0, top), Size(cw, bottom - top))
                        var sx = x0 + cw * 0.18f
                        while (sx < x0 + cw) {
                            drawRect(b, Offset(sx, top), Size(cw * 0.15f, bottom - top))
                            sx += cw * 0.36f
                        }
                    }
                }
            }
            Dress.Valance -> {
                val vl = r.left - r.width * 0.1f
                val vw = r.width * 1.2f
                val vt = r.top - ring - 8f
                val vh = r.height * 0.18f + 8f
                val p = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(vl, vt, vl + vw, vt + vh, CornerRadius(14f))) }
                clipPath(p) {
                    val stripe = vw / 10f
                    for (i in 0 until 10) drawRect(if (i % 2 == 0) Color(0xFFE2674F) else Color(0xFFFFFAF2), Offset(vl + i * stripe, vt), Size(stripe, vh))
                }
            }
        }
        // the sill
        val sl = r.left - r.width * 0.14f
        drawRoundRect(Color(0x33000000), Offset(sl, r.bottom + ring + 4f), Size(r.width * 1.28f, 12f), CornerRadius(5f))
        drawRoundRect(Color(0xFFE6CFB1), Offset(sl, r.bottom + ring), Size(r.width * 1.28f, 10f), CornerRadius(4f))
    }

    /* ------------------------------ living room ------------------------------ */

    fun frame(ds: DrawScope, r: Rect) = ds.drawSvg(80f, 64f, r) {
        rect(2f, 2f, 76f, 60f, c("#b8854f"), rx = 5f)
        rect(2f, 2f, 76f, 60f, stroke = c("#8f6335"), sw = 2f, rx = 5f)
        rect(8f, 8f, 64f, 48f, c("#fbeed3"), rx = 2f)
        rect(8f, 8f, 64f, 30f, c("#ffd9a0"))
        circle(52f, 24f, 8f, c("#ff9a63"))
        path("M8 56 V40 l16 -14 14 12 12 -16 22 20 V56z", c("#7aa580"))
        path("M8 56 V46 l18 -8 16 8 12 -6 18 8 V56z", c("#5d8b6a"))
    }

    fun clock(ds: DrawScope, r: Rect, clock: Clock) = ds.drawSvg(60f, 60f, r) {
        val minDeg = clock.minute * 6f
        val hourDeg = ((clock.hours.toInt() % 12) + clock.minute / 60f) * 30f
        circle(30f, 30f, 27f, c("#b8854f"))
        circle(30f, 30f, 22f, c("#fff8ea"))
        for (a in listOf(0f, 90f, 180f, 270f)) g(deg = a, px = 30f, py = 30f) { rect(29f, 10f, 2f, 5f, c("#8a6f5e"), rx = 1f) }
        g(deg = hourDeg, px = 30f, py = 30f) { line(30f, 30f, 30f, 17f, c("#4a3428"), 3f, cap = StrokeCap.Round) }
        g(deg = minDeg, px = 30f, py = 30f) { line(30f, 30f, 30f, 11f, c("#4a3428"), 2f, cap = StrokeCap.Round) }
        circle(30f, 30f, 2.6f, c("#e2674f"))
    }

    fun shelf(ds: DrawScope, r: Rect) = ds.drawSvg(120f, 64f, r) {
        rect(0f, 52f, 120f, 8f, c("#b8854f"), rx = 3f)
        rect(6f, 56f, 108f, 4f, c("#8f6335", .5f), rx = 2f)
        rect(8f, 22f, 12f, 30f, c("#e2674f"), rx = 2f)
        rect(21f, 14f, 10f, 38f, c("#6f9a6e"), rx = 2f)
        rect(32f, 26f, 13f, 26f, c("#f0c36d"), rx = 2f)
        g(deg = 8f, px = 50f, py = 52f) { rect(46f, 20f, 9f, 32f, c("#7a8fd0"), rx = 2f) }
        path("M78 52 V46 q-9 -2 -9 -12 h24 q0 10 -9 12 V52z", c("#f0c36d"))
        path("M69 36 q-8 0 -6 8 q2 3 7 2 M93 36 q8 0 6 8 q-2 3 -7 2", stroke = c("#f0c36d"), sw = 2.4f)
        rect(72f, 50f, 18f, 3f, c("#b8854f"), rx = 1f)
        circle(106f, 38f, 9f, c("#fffaf2"))
        path("M99 40 q7 8 14 0", stroke = c("#e2674f"), sw = 2f, cap = StrokeCap.Round)
        circle(103f, 35f, 1.4f, c("#4a3428"))
        circle(109f, 35f, 1.4f, c("#4a3428"))
    }

    fun plant(ds: DrawScope, r: Rect, t: Float) = ds.drawSvg(80f, 120f, r) {
        g(deg = sin(t * 0.8f) * 1.5f, px = 40f, py = 80f) {
            path("M40 78 C 20 66 8 40 18 10 C 34 28 40 52 40 78z", c("#5f9a62"))
            path("M40 78 C 60 62 74 36 62 6 C 46 26 40 50 40 78z", c("#7bb67a"))
            path("M40 80 C 34 56 38 34 42 12 C 50 36 48 60 40 80z", c("#4c8754"))
            path("M40 78 L40 24", stroke = c("#3d7447"), sw = 1.5f)
        }
        path("M14 82 h52 l-7 34 a6 6 0 0 1 -6 4 h-26 a6 6 0 0 1 -6 -4z", c("#d9774f"))
        rect(10f, 76f, 60f, 10f, c("#e98c63"), rx = 5f)
        path("M18 90 h44", stroke = c("#ffffff", .18f), sw = 3f, cap = StrokeCap.Round)
    }

    fun sofa(ds: DrawScope, r: Rect) = ds.drawSvg(220f, 110f, r) {
        ellipse(110f, 106f, 104f, 6f, c("#2a1608", .18f))
        rect(14f, 28f, 192f, 58f, c("#4f8f9a"), rx = 22f)
        rect(0f, 52f, 40f, 52f, c("#3e7a85"), rx = 16f)
        rect(180f, 52f, 40f, 52f, c("#3e7a85"), rx = 16f)
        rect(34f, 52f, 152f, 40f, c("#6bb0b9"), rx = 14f)
        path("M110 54 V90", stroke = c("#4f8f9a"), sw = 3f)
        rect(40f, 98f, 10f, 10f, c("#7a4f2b"), rx = 3f)
        rect(170f, 98f, 10f, 10f, c("#7a4f2b"), rx = 3f)
        g(deg = -10f, px = 75f, py = 55f) { rect(58f, 40f, 34f, 30f, c("#f0c36d"), rx = 9f) }
        path("M64 50 l22 -6 M62 58 l22 -6", stroke = c("#ffffff", .5f), sw = 2f)
    }

    fun floorLamp(ds: DrawScope, r: Rect) = ds.drawSvg(50f, 150f, r) {
        path("M10 6 h30 l8 30 h-46z", c("#ffd98f"))
        path("M14 8 l-4 26", stroke = c("#ffffff", .5f), sw = 4f, cap = StrokeCap.Round)
        rect(23f, 36f, 4f, 102f, c("#7a4f2b"))
        ellipse(25f, 140f, 17f, 6f, c("#7a4f2b"))
    }

    /* --------------------------------- kitchen -------------------------------- */

    fun fridge(ds: DrawScope, r: Rect) = ds.drawSvg(60f, 140f, r) {
        rect(3f, 3f, 54f, 134f, c("#cfe6e4"), rx = 8f)
        rect(3f, 3f, 54f, 134f, stroke = c("#ffffff", .5f), sw = 1.5f, rx = 8f)
        path("M3 46 H57", stroke = c("#8fb3b0"), sw = 2f)
        rect(44f, 14f, 4f, 26f, c("#f4f1e8"), rx = 2f)
        rect(44f, 56f, 4f, 46f, c("#f4f1e8"), rx = 2f)
        g(deg = -5f, px = 19f, py = 69f) { rect(12f, 62f, 14f, 14f, c("#fff8e6")) }
        circle(19f, 60f, 3f, c("#e2674f"))
        rect(14f, 84f, 9f, 9f, c("#5b8fd6"), rx = 2f)
        path("M26 82 l4 -2 4 2 -1 5h-6z", c("#ffd36b"))
        path("M10 8 q-2 60 0 120", stroke = c("#ffffff", .45f), sw = 4f, cap = StrokeCap.Round)
    }

    fun sink(ds: DrawScope, r: Rect) = ds.drawSvg(70f, 44f, r) {
        path("M6 34 q0 -6 6 -6 h46 q6 0 6 6 v4 h-58z", c("#aab3c0"))
        path("M10 34 h50", stroke = c("#ffffff", .5f), sw = 1.5f)
        path("M44 28 V14 q0 -8 -8 -8 h-6", stroke = c("#c9d1db"), sw = 4.5f, cap = StrokeCap.Round)
        rect(26f, 4f, 8f, 6f, c("#aab3c0"), rx = 2f)
        circle(44f, 28f, 3f, c("#8e97a4"))
    }

    /** The run of cabinets ([doors] of them) with its worktop. */
    fun counter(ds: DrawScope, r: Rect, doors: Int) = with(ds) {
        val top = r.height * 0.24f
        drawRoundRect(Color(0x40000000), Offset(r.left, r.top + 4f), Size(r.width, r.height), CornerRadius(6f))
        // doors
        drawRoundRect(Color(0xFF5D8C7C), Offset(r.left, r.top + top), Size(r.width, r.height * 0.68f), CornerRadius(4f))
        val gap = 4f
        val n = max(1, doors)
        val dw = (r.width - gap * (n + 1)) / n
        for (i in 0 until n) {
            val x = r.left + gap + i * (dw + gap)
            val y = r.top + top + gap
            val h = r.height * 0.68f - gap
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8FBFAE), Color(0xFF6FA291)), y, y + h), Offset(x, y), Size(dw, h), CornerRadius(5f))
            drawRect(Color(0x47FFFFFF), Offset(x + 3f, y), Size(dw - 6f, 2f))
            drawRoundRect(Color(0xFFF3D48C), Offset(x + dw * 0.39f, y + h * 0.1f), Size(dw * 0.22f, 4f), CornerRadius(4f))
        }
        drawRoundRect(Color(0xFF3F5F55), Offset(r.left, r.bottom - r.height * 0.08f), Size(r.width, r.height * 0.08f), CornerRadius(4f))
        // worktop
        val wl = r.left - r.width * 0.015f
        drawRoundRect(Color(0xFFB88F55), Offset(wl, r.top + 3f), Size(r.width * 1.03f, top), CornerRadius(6f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF4E3C0), Color(0xFFD9B985)), r.top, r.top + top), Offset(wl, r.top), Size(r.width * 1.03f, top), CornerRadius(6f))
    }

    fun stove(ds: DrawScope, r: Rect, t: Float) = ds.drawSvg(84f, 92f, r, vbY = -22f) {
        rect(3f, 14f, 78f, 52f, c("#cfd6dd"), rx = 6f)
        rect(3f, 10f, 78f, 8f, c("#8e97a4"), rx = 3f)
        rect(12f, 30f, 60f, 30f, c("#4b5560"), rx = 4f)
        rect(16f, 34f, 52f, 22f, c("#7e8b97", .55f), rx = 3f)
        path("M20 38 l12 -2", stroke = c("#ffffff", .5f), sw = 2f, cap = StrokeCap.Round)
        rect(22f, 24f, 40f, 3f, c("#f4f1e8"), rx = 1.5f)
        for (x in listOf(18f, 34f, 50f, 66f)) circle(x, 21f, 2.4f, c("#4b5560"))
        path("M44 10 h22 l-2 -10 h-18z", c("#e2674f"))
        rect(62f, 2f, 8f, 3f, c("#4b5560"), rx = 1.5f)
        val s = (t / 3f) % 1f
        g(ty = -s * 6f, alpha = sin(s * PI.toFloat()) * 0.9f) {
            path("M50 -2 q-3 -5 0 -9 q3 -4 0 -8", stroke = c("#ffffff", .7f), sw = 2f, cap = StrokeCap.Round)
            path("M57 -2 q-3 -5 0 -9 q3 -4 0 -8", stroke = c("#ffffff", .7f), sw = 2f, cap = StrokeCap.Round)
        }
    }

    /** Stove art's height for a width (it has steam above its 84x70 box). */
    const val STOVE_ASPECT = 84f / 92f

    fun hood(ds: DrawScope, r: Rect) = ds.drawSvg(84f, 50f, r) {
        rect(32f, 0f, 20f, 22f, c("#aab3c0"))
        rect(32f, 0f, 20f, 22f, stroke = c("#ffffff", .4f))
        path("M4 44 L24 20 H60 L80 44 Z", c("#cfd6dd"))
        path("M4 44 L24 20 H60 L80 44 Z", stroke = c("#ffffff", .5f), sw = 1.5f)
        rect(2f, 42f, 80f, 6f, c("#8e97a4"), rx = 3f)
        rect(28f, 44f, 28f, 2f, c("#ffe9a8"), rx = 1f)
    }

    fun wallShelf(ds: DrawScope, r: Rect) = ds.drawSvg(120f, 64f, r) {
        rect(0f, 52f, 120f, 8f, c("#b8854f"), rx = 3f)
        rect(6f, 56f, 108f, 4f, c("#8f6335", .5f), rx = 2f)
        rect(10f, 26f, 18f, 26f, c("#fffaf2"), rx = 4f, stroke = c("#d8bc98"), sw = 1.5f)
        rect(9f, 22f, 20f, 6f, c("#e2674f"), rx = 3f)
        rect(34f, 20f, 18f, 32f, c("#f4e0b0"), rx = 4f, stroke = c("#d8bc98"), sw = 1.5f)
        rect(33f, 16f, 20f, 6f, c("#6f9a6e"), rx = 3f)
        rect(58f, 30f, 16f, 22f, c("#fffaf2"), rx = 4f, stroke = c("#d8bc98"), sw = 1.5f)
        rect(57f, 26f, 18f, 6f, c("#7a8fd0"), rx = 3f)
        path("M88 52 v-14 h20 v14z", c("#d9774f"))
        path("M96 38 q-6 -10 -2 -18 q4 8 4 18 q6 -12 14 -14 q-4 8 -8 14z", c("#5f9a62"))
    }

    fun table(ds: DrawScope, r: Rect) = ds.drawSvg(150f, 110f, r) {
        ellipse(75f, 102f, 64f, 6f, c("#2a1608", .18f))
        rect(22f, 76f, 22f, 6f, c("#e2674f"), rx = 3f)
        rect(30f, 82f, 5f, 22f, c("#7a4f2b"), rx = 2f)
        rect(106f, 76f, 22f, 6f, c("#e2674f"), rx = 3f)
        rect(114f, 82f, 5f, 22f, c("#7a4f2b"), rx = 2f)
        rect(70f, 46f, 8f, 56f, c("#7a4f2b"), rx = 3f)
        ellipse(75f, 102f, 22f, 4f, c("#7a4f2b"))
        ellipse(75f, 46f, 58f, 12f, c("#c58a55"))
        ellipse(75f, 43f, 58f, 12f, c("#e2b783"))
        path("M20 43 a55 10 0 0 1 110 0 a55 10 0 0 1 -110 0z", c("#fffaf2", .5f))
        path("M47 38 l8 -3 8 3 -8 3z M87 40 l8 -3 8 3 -8 3z", c("#e2674f", .55f))
        path("M68 34 q0 -10 7 -10 q7 0 7 10z", c("#f0c36d"))
        circle(75f, 22f, 3.4f, c("#e2674f"))
    }

    /** A pendant on a cord; [cord] is extra cord length in viewBox units above the shade. */
    fun pendant(ds: DrawScope, r: Rect, t: Float) = ds.drawSvg(60f, 120f, r) {
        rect(29f, 0f, 2f, 62f, c("#4a3428"))
        path("M8 90 Q10 62 30 60 Q50 62 52 90z", c("#e2674f"))
        path("M14 84 Q16 68 28 64", stroke = c("#ffffff", .5f), sw = 3f, cap = StrokeCap.Round)
        rect(6f, 88f, 48f, 5f, c("#b84a36"), rx = 2.5f)
        ellipse(30f, 98f, 9f, 7f, c("#fff4cf", 0.85f + 0.15f * sin(t * 1.6f)))
    }

    /* --------------------------------- bedroom -------------------------------- */

    fun stringLights(ds: DrawScope, r: Rect, t: Float) = ds.drawSvg(400f, 44f, r) {
        path("M0 4 Q50 36 100 8 T200 8 T300 8 T400 4", stroke = c("#2d2a4a"), sw = 1.6f)
        val pts = arrayOf(24f to 20f, 62f to 25f, 100f to 8f, 140f to 20f, 178f to 17f, 220f to 14f, 258f to 24f, 300f to 8f, 340f to 21f, 378f to 12f)
        val cols = arrayOf("#ffd36b", "#ff9a8a", "#ffe9a8", "#9fd8ff")
        pts.forEachIndexed { i, (x, y) ->
            val a = 0.6f + 0.4f * sin(t * 2.6f + i * 0.35f * 2.6f)
            circle(x, y + 5f, 9f, c("#ffd36b", .22f * a))
            circle(x, y + 5f, 3.6f, c(cols[i % 4], a))
        }
    }

    fun wardrobe(ds: DrawScope, r: Rect) = ds.drawSvg(100f, 190f, r) {
        ellipse(50f, 184f, 44f, 5f, c("#0a0820", .3f))
        rect(6f, 6f, 88f, 172f, c("#7a5a98"), rx = 6f)
        rect(6f, 6f, 88f, 14f, c("#5f4380"), rx = 6f)
        rect(12f, 26f, 36f, 146f, c("#8f6fb0"), rx = 3f)
        rect(52f, 26f, 36f, 146f, c("#8f6fb0"), rx = 3f)
        rect(18f, 34f, 24f, 64f, c("#c9d8ff", .45f), rx = 2f)
        path("M22 40 l8 -2", stroke = c("#ffffff", .7f), sw = 2f, cap = StrokeCap.Round)
        rect(42f, 92f, 3f, 18f, c("#f3d48c"), rx = 1.5f)
        rect(55f, 92f, 3f, 18f, c("#f3d48c"), rx = 1.5f)
        path("M58 120 h24 v10 h-24z M58 138 h24 v10 h-24z", c("#6f4f8d"))
        rect(14f, 178f, 10f, 8f, c("#4a3428"), rx = 2f)
        rect(76f, 178f, 10f, 8f, c("#4a3428"), rx = 2f)
    }

    fun poster(ds: DrawScope, r: Rect) = ds.drawSvg(80f, 100f, r) {
        rect(2f, 2f, 76f, 96f, c("#f4eee0"), rx = 4f)
        rect(8f, 8f, 64f, 84f, c("#26307a"), rx = 2f)
        circle(52f, 30f, 12f, c("#fdf6d8"))
        circle(57f, 27f, 11f, c("#26307a"))
        path("M8 92 V68 l16 -16 12 12 14 -22 22 28 V92z", c("#3f4fa8"))
        path("M8 92 V78 l18 -10 16 10 12 -8 18 8 V92z", c("#2c3a8a"))
        for ((x, y) in listOf(18f to 18f, 30f to 40f, 40f to 14f, 20f to 52f, 64f to 54f)) circle(x, y, 1.3f, c("#ffffff"))
    }

    /* -------------------------------- bathroom -------------------------------- */

    fun mirror(ds: DrawScope, r: Rect) = ds.drawSvg(80f, 100f, r) {
        path("M6 96 V40 a34 34 0 0 1 68 0 V96z", c("#e6cfb1"))
        path("M12 90 V40 a28 28 0 0 1 56 0 V90z", c("#e9f8fb"))
        path("M12 90 V40 a28 28 0 0 1 56 0 V90z", c("#bfe4ee", .55f))
        path("M22 70 L50 24", stroke = c("#ffffff", .7f), sw = 6f, cap = StrokeCap.Round)
        path("M34 80 L56 44", stroke = c("#ffffff", .5f), sw = 3f, cap = StrokeCap.Round)
    }

    fun vanity(ds: DrawScope, r: Rect) = ds.drawSvg(120f, 84f, r) {
        rect(6f, 28f, 108f, 52f, c("#f4f1e8"), rx = 5f)
        rect(6f, 28f, 108f, 52f, stroke = c("#c9bfa8"), sw = 1.5f, rx = 5f)
        rect(12f, 36f, 45f, 38f, c("#e8e2d2"), rx = 3f)
        rect(63f, 36f, 45f, 38f, c("#e8e2d2"), rx = 3f)
        circle(50f, 55f, 2.6f, c("#c58a55"))
        circle(70f, 55f, 2.6f, c("#c58a55"))
        rect(0f, 20f, 120f, 12f, c("#fffaf2"), rx = 6f)
        path("M30 20 q0 -8 12 -8 h36 q12 0 12 8z", c("#cfeaf2"))
        path("M76 16 V4 q0 -4 -5 -4 h-6", stroke = c("#aab3c0"), sw = 4f, cap = StrokeCap.Round)
        rect(9f, 82f, 10f, 2f, c("#8a6f5e"), rx = 1f)
        rect(101f, 82f, 10f, 2f, c("#8a6f5e"), rx = 1f)
    }

    fun towels(ds: DrawScope, r: Rect) = ds.drawSvg(100f, 90f, r) {
        rect(4f, 6f, 92f, 5f, c("#b8854f"), rx = 2.5f)
        rect(4f, 2f, 5f, 14f, c("#8f6335"), rx = 2.5f)
        rect(91f, 2f, 5f, 14f, c("#8f6335"), rx = 2.5f)
        path("M14 8 h34 v64 q-17 6 -34 0z", c("#ffffff"))
        path("M14 56 h34 v6 h-34z M14 64 h34 v3 h-34z", c("#f0c36d"))
        path("M54 8 h34 v52 q-17 6 -34 0z", c("#ffffff"))
        path("M54 44 h34 v6 h-34z M54 52 h34 v3 h-34z", c("#e2674f"))
    }

    fun duck(ds: DrawScope, r: Rect, t: Float) = ds.drawSvg(60f, 50f, r) {
        ellipse(30f, 44f, 22f, 4f, c("#2a1608", .15f))
        g(deg = sin(t * 2.1f) * 6f, px = 30f, py = 44f) {
            path("M8 30 q0 14 22 14 q22 0 22 -14 q-8 6 -16 -2 q-12 8 -28 2z", c("#ffd23f"))
            path("M44 26 q6 -2 8 -6 q-6 0 -8 2z", c("#ffd23f"))
            circle(38f, 18f, 11f, c("#ffd23f"))
            path("M47 18 q8 0 9 3 q-5 3 -10 1z", c("#ff8a3d"))
            circle(41f, 15f, 1.8f, c("#4a3428"))
            path("M12 32 q8 6 20 2", stroke = c("#e8b81e"), sw = 2.4f, cap = StrokeCap.Round)
        }
    }

    /** The shower stall behind him: tray, tiles, pipe and head. */
    fun showerBack(ds: DrawScope, r: Rect) = with(ds) {
        val top = r.top + r.height * 0.08f
        drawRoundRect(Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x80A0D7E1)), top, r.bottom), Offset(r.left, top), Size(r.width, r.bottom - top), CornerRadius(14f))
        // pipe
        val pw = max(5f, r.width * 0.035f)
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFC9D2D6), Color(0xFFF4F8F9), Color(0xFFAAB5BA)), r.right - r.width * 0.18f - pw, r.right - r.width * 0.18f), Offset(r.right - r.width * 0.18f - pw, r.top), Size(pw, r.height * 0.16f))
        // head
        val hw = r.width * 0.56f
        val hx = r.right - r.width * 0.08f - hw
        val hy = r.top + r.height * 0.12f
        drawRoundRect(Color(0x592864_6E), Offset(hx, hy + 3f), Size(hw, r.height * 0.07f), CornerRadius(99f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF4F8F9), Color(0xFFAAB5BA)), hy, hy + r.height * 0.07f), Offset(hx, hy), Size(hw, r.height * 0.07f), CornerRadius(99f))
        // the puddle
        drawOval(Brush.radialGradient(listOf(Color(0x804FB4D6), Color.Transparent), Offset(r.center.x, r.bottom - r.height * 0.06f), r.width * 0.45f), Offset(r.left + r.width * 0.06f, r.bottom - r.height * 0.09f), Size(r.width * 0.88f, r.height * 0.06f))
        // frame
        drawRoundRect(Color(0xBFFFFFFF), Offset(r.left, top), Size(r.width, r.bottom - top), CornerRadius(14f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
    }

    /** The water (when [on]) and the glass, drawn over him. */
    fun showerFront(ds: DrawScope, r: Rect, on: Boolean, t: Float) = with(ds) {
        val top = r.top + r.height * 0.08f
        if (on) {
            val wl = r.left + r.width * 0.14f
            val wr = r.right - r.width * 0.08f
            val wt = r.top + r.height * 0.21f
            val wb = r.bottom - r.height * 0.08f
            clipPath(Path().apply { addRect(Rect(wl, wt, wr, wb)) }) {
                for (i in 0 until 14) {
                    val x = wl + i * 0.07f * (wr - wl) / 0.98f
                    val ph = ((t / 0.55f) + i * 0.09f / 0.55f) % 1f
                    val len = (wb - wt) * 0.16f
                    val y = wt - len + ph * (wb - wt + len)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0x0078C8EB), Color(0xFF7CC9EA)), y, y + len), Offset(x, y), Size(3f, len), CornerRadius(3f))
                }
            }
        }
        drawRoundRect(
            Brush.linearGradient(0f to Color(0x66FFFFFF), 0.6f to Color(0x1ABEE6F0), 1f to Color(0x40FFFFFF), start = Offset(r.left, top), end = Offset(r.right, r.bottom)),
            Offset(r.left, top), Size(r.width, r.bottom - top), CornerRadius(14f),
        )
    }

    /** A bar of soap. */
    fun soap(ds: DrawScope, r: Rect, held: Boolean) = ds.drawSvg(60f, 40f, r) {
        ellipse(30f, 36f, 24f, 4f, c("#2a1608", if (held) .1f else .2f))
        rect(4f, 6f, 52f, 28f, lin(stop(0f, "#ffd3e6"), stop(1f, "#f39ac0")), rx = 12f)
        rect(10f, 10f, 30f, 6f, c("#ffffff", .6f), rx = 3f)
        circle(48f, 4f, 4f, c("#ffffff", .9f), stroke = c("#bfe4ee"), sw = 1f)
        circle(54f, 12f, 2.6f, c("#ffffff", .9f), stroke = c("#bfe4ee"), sw = 1f)
    }
}

