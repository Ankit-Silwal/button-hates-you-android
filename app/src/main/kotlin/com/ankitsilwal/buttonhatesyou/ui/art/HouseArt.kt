package com.ankitsilwal.buttonhatesyou.ui.art

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// Every piece of furniture and every food of the web's src/house/art.jsx, ported
// close to line for line onto the Svg kit (same viewBoxes, same coordinates, the
// same path strings). The CSS animations of src/hub/scene.css (hsLava, hsFish,
// hsBubble, hsHue, hsScan, hsBob, hsTwinkle, hsFacets, hsSparkle, hsTurn, hsNeon)
// are driven by the `t` clock.

/** A light a piece throws into the room. x, y: % of the art box; r: in room units. */
data class ArtGlow(val x: Float, val y: Float, val r: Float, val color: Color)

/** w: width in room units (as in art.jsx); vbW x vbH: the viewBox. */
class ArtInfo(val w: Float, val vbW: Float, val vbH: Float, val glow: ArtGlow?)

object HouseArt {

    private class Item(val info: ArtInfo, val draw: Svg.() -> Unit)

    private val items = HashMap<String, Item>(96)

    /** Foods have no room width in art.jsx; this is what they get. */
    private const val FOOD_W = 10f

    fun info(id: String): ArtInfo? = items[id]?.info

    fun has(id: String): Boolean = items.containsKey(id)

    /** Draws item [id] filling [dst] (keep the vbW:vbH aspect). [t] = seconds. */
    fun draw(scope: DrawScope, id: String, dst: Rect, t: Float = 0f, alpha: Float = 1f) {
        val item = items[id] ?: return
        val a = if (alpha.isNaN()) 1f else alpha.coerceIn(0f, 1f)
        if (a <= 0f) return
        if (!dst.width.isFinite() || !dst.height.isFinite() || !dst.left.isFinite() || !dst.top.isFinite()) return
        val tt = if (t.isFinite()) t else 0f
        drawAlpha = a
        try {
            scope.drawSvg(item.info.vbW, item.info.vbH, dst, tt, a) { item.draw(this) }
        } catch (_: Throwable) {
        }
    }

    private fun reg(id: String, w: Float, vbW: Float, vbH: Float, glow: ArtGlow? = null, draw: Svg.() -> Unit) {
        items[id] = Item(ArtInfo(w, vbW, vbH, glow), draw)
    }

    private fun glow(x: Float, y: Float, r: Float, hex: String) = ArtGlow(x, y, r, col(hex))

    /* ============================== beds ============================== */

    private fun beds() {
        run {
            val m = lin(s(0f, "#f3efe6"), s(1f, "#d9d0bf"))
            val side = lin(s(0f, "#cfc4b0"), s(1f, "#a99c86"))
            val bl = lin(s(0f, "#9fb0c8"), s(1f, "#6f81a0"))
            val pil = rad(s(0f, "#ffffff"), s(1f, "#dcd6ca"), cx = 0.4f, cy = 0.35f, r = 0.7f)
            val sh = shadowFill(0.35f)
            val tickClip = rrD(6f, 44f, 188f, 26f, 12f)
            val tick = c("#b9c6d8")
            reg("bed-basic", 36f, 200f, 92f) {
                shadow(100f, 84f, 98f, 9f, sh)
                rect(6f, 52f, 188f, 30f, side, rx = 10f)
                rect(6f, 44f, 188f, 26f, m, rx = 12f)
                clip(tickClip) {
                    var x = 0f
                    while (x < 200f) {
                        rect(x, 44f, 3f, 26f, tick, alpha = 0.45f); x += 10f
                    }
                }
                st("M10 70 h180", "#8f826e", 1.5f, a = 0.6f, dash = D34)
                path("M18 38 q2 -14 22 -14 h26 q16 0 16 12 q0 12 -14 12 h-32 q-18 0 -18 -10z", pil)
                st("M30 32 q10 4 20 0", "#c8c0b0", 1.5f)
                path("M92 42 q30 -10 60 -4 q30 6 38 10 l0 16 q-50 8 -100 0z", bl)
                st("M104 50 q24 -6 50 2 M120 58 q20 -4 40 2", "#56688a", 1.6f, a = 0.6f)
            }
        }
        run {
            val wood = lin(s(0f, "#a8724a"), s(1f, "#6d4428"))
            val woodL = lin(s(0f, "#c08a5c"), s(1f, "#8a5a36"), x2 = 1f, y2 = 0f)
            val sheet = lin(s(0f, "#fffdf8"), s(1f, "#e7e0d3"))
            val quilt = lin(s(0f, "#e98a6c"), s(1f, "#c25d44"))
            val pil = rad(s(0f, "#ffffff"), s(1f, "#e6dfd2"), cx = 0.4f, cy = 0.3f, r = 0.75f)
            val gl = gloss()
            val sh = shadowFill(0.35f)
            val q = "M70 70 q60 -14 132 -4 v26 h-132z"
            // the quilting pattern: an 18 x 18 dashed grid turned 45 degrees
            val grid = buildString {
                for (k in 4..12) append("M${k * 18} -120 V30 ")
                for (k in -7..1) append("M80 ${k * 18} H230 ")
            }
            val qs = c("#fff", 0.28f)
            reg("bed-cozy", 38f, 220f, 130f) {
                shadow(112f, 124f, 110f, 8f, sh)
                path("M8 120 V36 q0 -30 34 -30 h4 q34 0 34 30 V120z", woodL)
                fp("M18 116 V40 q0 -22 26 -22 q26 0 26 22 V116z", "#5d3920", 0.25f)
                st("M14 30 q4 -18 30 -20", "#e2b38a", 3f, a = 0.6f, round = true)
                rect(8f, 88f, 206f, 22f, wood, rx = 6f)
                rect(200f, 70f, 14f, 52f, wood, rx = 5f)
                r(12f, 108f, 10f, 14f, "#5a3820", rx = 2f)
                r(196f, 108f, 10f, 14f, "#5a3820", rx = 2f)
                rect(20f, 66f, 180f, 26f, sheet, rx = 10f)
                path("M28 64 q0 -20 22 -20 h22 q20 0 20 14 q0 10 -14 12 h-36 q-14 0 -14 -6z", pil)
                path(q, quilt)
                clip(q) { g(deg = 45f) { path(grid, stroke = qs, sw = 1.2f, dash = D33) } }
                path("M70 70 q60 -14 132 -4 v6 q-70 -8 -132 6z", gl)
                st("M70 92 h132", "#8f3a28", 2f, a = 0.5f)
            }
        }
        run {
            val cl = rad(s(0f, "#ffffff"), s(0.7f, "#eae6ff"), s(1f, "#c9c0f2"), cx = 0.4f, cy = 0.3f, r = 0.75f)
            val gw = rad(s(0f, "#c6b8ff", 0.7f), s(1f, "#c6b8ff", 0f))
            val blank = lin(s(0f, "#b9a8f5"), s(1f, "#8b74e0"))
            reg("bed-cloud", 38f, 220f, 130f) {
                radEllipse(110f, 96f, 112f, 40f, gw)
                circle(40f, 72f, 30f, cl)
                circle(80f, 60f, 36f, cl)
                circle(130f, 62f, 34f, cl)
                circle(176f, 74f, 30f, cl)
                rect(16f, 70f, 190f, 40f, cl, rx = 20f)
                st("M20 96 q90 18 184 0", "#b6a9ec", 3f, a = 0.6f)
                path("M100 80 q50 -14 100 -2 q8 14 -4 26 q-50 8 -96 0z", blank, alpha = 0.9f)
                fp("M60 22 l3 7 7 3 -7 3 -3 7 -3 -7 -7 -3 7 -3z", "#fff6b0")
                fp("M170 30 l2 5 5 2 -5 2 -2 5 -2 -5 -5 -2 5 -2z", "#fff6b0")
                fp("M116 12 l2 4 4 2 -4 2 -2 4 -2 -4 -4 -2 4 -2z", "#fff6b0")
            }
        }
    }

    /* ============================== lights ============================== */

    private fun lights() {
        run {
            val brass = lin(s(0f, "#f3d48c"), s(0.5f, "#b88a3a"), s(1f, "#7a5520"), x2 = 1f, y2 = 0f)
            val shade = lin(s(0f, "#3e4a44"), s(1f, "#1f2723"))
            val bulb = rad(s(0f, "#fffbe8"), s(0.4f, "#ffe2a0"), s(1f, "#ffbf5a", 0f), cx = 0.5f, cy = 0.2f, r = 0.8f)
            val marble = rad(s(0f, "#ffffff"), s(1f, "#c9c4bc"), cx = 0.35f, cy = 0.3f, r = 0.8f)
            val sh = shadowFill(0.35f)
            reg("lamp-arc", 15f, 90f, 230f, glow(26f, 26f, 40f, "#ffd58a")) {
                shadow(66f, 224f, 24f, 5f, sh)
                path("M66 218 C66 120 60 40 22 30", stroke = brass, sw = 3.5f, cap = StrokeCap.Round)
                radEllipse(22f, 58f, 22f, 22f, bulb)
                path("M2 40 q20 -30 40 0 z", shade)
                st("M6 36 q16 -20 32 0", "#7d8c84", 1.5f, a = 0.6f)
                ellipse(22f, 40f, 20f, 4f, c("#ffe7b0"))
                radEllipse(66f, 218f, 20f, 7f, marble)
                fp("M46 218 v4 q20 8 40 0 v-4", "#b3aca2")
            }
        }
        run {
            val metal = lin(s(0f, "#d9dde4"), s(0.45f, "#8b93a1"), s(1f, "#4b5260"), x2 = 1f, y2 = 0f)
            val liq = lin(s(0f, "#ffb35c"), s(1f, "#e2412f"))
            val glass = lin(s(0f, "#fff", 0.4f), s(0.3f, "#fff", 0.05f), s(1f, "#fff", 0f), x2 = 1f, y2 = 0f)
            val blob = c("#ffe08a")
            val sh = shadowFill(0.35f)
            val g = "M16 22 h18 l8 66 h-34z"
            reg("lamp-lava", 7f, 50f, 140f, glow(50f, 45f, 30f, "#ff7a52")) {
                shadow(25f, 135f, 18f, 4f, sh)
                path("M18 8 h14 l3 14 h-20z", metal)
                path(g, liq)
                clip(g) {
                    lava(22f, 70f, 7f, 9f, 7f, 0f, blob)
                    lava(30f, 50f, 5f, 7f, 9f, -3f, blob)
                    lava(24f, 34f, 4f, 5f, 6f, -5f, blob)
                }
                path(g, glass)
                path("M8 88 h34 l-6 44 h-22z", metal)
                st("M10 92 h30", "#fff", 1.5f, a = 0.4f)
            }
        }
        run {
            val shade = lin(s(0f, "#3f7a63"), s(0.5f, "#2a5a48"), s(1f, "#173a2e"), x2 = 1f, y2 = 0f)
            val bulb = rad(s(0f, "#fffdf0"), s(0.5f, "#ffe6a6"), s(1f, "#ffcf6b", 0f), cx = 0.5f, cy = 0.3f, r = 0.7f)
            reg("lamp-pendant", 11f, 80f, 150f, glow(50f, 92f, 46f, "#ffd88f")) {
                st("M40 0 V96", "#2b2b2b", 1.6f)
                r(35f, 88f, 10f, 14f, "#b88a3a", rx = 2f)
                radEllipse(40f, 130f, 16f, 14f, bulb)
                path("M8 130 Q10 98 40 98 Q70 98 72 130z", shade)
                st("M16 124 Q20 104 40 102", "#fff", 2.5f, a = 0.35f, round = true)
                ellipse(40f, 130f, 32f, 4f, c("#ffefc4"))
            }
        }
        run {
            val cols = arrayOf("#ffd46b", "#ff8fa3", "#8fd3ff", "#b6f28c", "#ffd46b", "#c9a2ff")
            val n = 17
            val bx = FloatArray(n)
            val by = FloatArray(n)
            val bd = FloatArray(n)
            val bc = arrayOfNulls<Fill>(n)
            for (i in 0 until n) {
                val x = 12f + i * 23.5f
                val tt = x / 400f
                bx[i] = x
                by[i] = 6f + sin(tt * PI * 3).pow(2).toFloat() * 26f
                bd[i] = (i % 5) * 0.4f
                bc[i] = c(cols[i % cols.size])
            }
            // like the web: "M0 2 M12 .. L.." (the first move breaks the line)
            val wire = buildString {
                append("M0 2 ")
                for (i in 0 until n) append(if (i == 0) "M" else "L").append(nf(bx[i])).append(' ').append(nf(by[i] - 4f)).append(' ')
                append("L400 2")
            }
            val cap = c("#5b5a52")
            val white = c("#fff")
            reg("lights-fairy", 100f, 400f, 50f) {
                st(wire, "#3c3a35", 1.2f)
                for (i in 0 until n) {
                    val x = bx[i]
                    val y = by[i]
                    val f = bc[i]!!
                    g(alpha = twinkle(t, bd[i])) {
                        circle(x, y + 3f, 9f, f, alpha = 0.25f)
                        rect(x - 1.8f, y - 4f, 3.6f, 3f, cap)
                        ellipse(x, y + 3f, 3.4f, 4.6f, f)
                        ellipse(x - 1f, y + 1.6f, 1f, 1.6f, white, alpha = 0.8f)
                    }
                }
            }
        }
        run {
            val ball = rad(s(0f, "#ffffff"), s(0.35f, "#c9d0de"), s(0.8f, "#6b7488"), s(1f, "#3b4152"), cx = 0.35f, cy = 0.3f, r = 0.75f)
            val clipD = circleD(40f, 88f, 30f)
            val hLines = buildString { for (i in 0 until 9) append("M0 ${60 + i * 7} H80 ") }
            val vLines = buildString {
                for (i in 0 until 12) append("M${40 + (i - 6) * 7} 56 Q${40 + (i - 6) * 9} 88 ${40 + (i - 6) * 7} 120 ")
            }
            val facet = c("#2e3340", 0.35f)
            reg("disco-ball", 11f, 80f, 130f, glow(50f, 72f, 40f, "#cfd8ff")) {
                st("M40 0 V58", "#8d8d8d", 1.4f)
                r(36f, 54f, 8f, 5f, "#a9a9a9", rx = 1.5f)
                circle(40f, 88f, 30f, ball)
                clip(clipD) {
                    g(tx = 7f * frac(t / 3f)) {
                        path(hLines, stroke = facet, sw = 0.8f)
                        path(vLines, stroke = facet, sw = 0.8f)
                        r(22f, 70f, 6f, 5f, "#fff", op = 0.9f)
                        r(29f, 63f, 6f, 5f, "#fff", op = 0.7f)
                        r(50f, 98f, 6f, 5f, "#bfe4ff", op = 0.7f)
                        r(36f, 105f, 6f, 5f, "#ffd1f0", op = 0.6f)
                    }
                }
                val k = ease(tri(t / 1.6f))
                g(px = 22f, py = 72f, sx = 0.3f + k, alpha = 0.2f + 0.8f * k) {
                    fp("M22 66 l1.6 4.4 4.4 1.6 -4.4 1.6 -1.6 4.4 -1.6 -4.4 -4.4 -1.6 4.4 -1.6z", "#fff")
                }
            }
        }
    }

    /* ======================= plants and things on the floor ======================= */

    private fun floorThings() {
        run {
            val pot = lin(s(0f, "#ffffff"), s(0.6f, "#e4e0d8"), s(1f, "#b9b3a8"), x2 = 1f, y2 = 0f)
            val leaf = lin(s(0f, "#6fbf73"), s(1f, "#2f7a45"), x2 = 1f, y2 = 1f)
            val leaf2 = lin(s(0f, "#5aa865"), s(1f, "#235f36"), x1 = 1f, y1 = 0f, x2 = 0f, y2 = 1f)
            val sh = shadowFill(0.35f)
            reg("plant-monstera", 14f, 120f, 180f) {
                shadow(60f, 174f, 36f, 6f, sh)
                st("M58 130 Q50 90 24 62", "#3f7b45", 2.4f, round = true)
                st("M60 130 Q64 80 86 48", "#3f7b45", 2.4f, round = true)
                st("M60 130 Q60 70 56 30", "#3f7b45", 2.4f, round = true)
                st("M62 130 Q80 100 104 92", "#3f7b45", 2.4f, round = true)
                path("M24 62 C0 60 -2 88 14 100 C28 96 40 84 24 62z M12 76 l8 4 M10 86 l10 2", leaf2, stroke = c("#1f4f2c"), sw = 0.8f)
                path("M86 48 C112 40 120 70 106 86 C92 86 78 72 86 48z", leaf)
                path("M56 30 C30 18 26 50 44 64 C60 66 72 50 56 30z", leaf)
                path("M104 92 C118 96 118 116 104 120 C94 114 92 100 104 92z", leaf2)
                st("M86 48 L102 78 M56 30 L46 60 M24 62 L14 96", "#1f5a30", 1.2f, a = 0.7f)
                g(deg = 30f, px = 98f, py = 62f) { ellipse(98f, 62f, 3f, 6f, c("#f5f0e6"), alpha = 0.9f) }
                g(deg = -20f, px = 46f, py = 44f) { ellipse(46f, 44f, 3f, 6f, c("#f5f0e6"), alpha = 0.9f) }
                path("M30 128 h60 l-8 46 h-44z", pot)
                r(27f, 124f, 66f, 10f, "#f4f1ea", rx = 4f)
                st("M38 140 l3 30", "#fff", 3f, a = 0.7f, round = true)
            }
        }
        run {
            val pot = lin(s(0f, "#e58a5e"), s(1f, "#a64f2e"), x2 = 1f, y2 = 0f)
            val cf = lin(s(0f, "#8fd38a"), s(0.6f, "#4f9e57"), s(1f, "#2f6e3a"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            val spines = buildString {
                val pts = intArrayOf(24, 30, 44, 30, 26, 52, 46, 50, 34, 66, 24, 72, 46, 70, 6, 36, 62, 30)
                var i = 0
                while (i < pts.size) {
                    val x = pts[i]
                    val y = pts[i + 1]
                    append("M$x $y l2 -3 M$x $y l-2 -3 "); i += 2
                }
            }
            reg("plant-cactus", 8f, 70f, 120f) {
                shadow(35f, 115f, 24f, 4f, sh)
                path("M14 56 q-12 0 -12 -14 v-10 q0 -6 6 -6 q6 0 6 6 v8 q0 4 4 4z", cf)
                path("M54 48 q12 0 12 -12 v-6 q0 -6 -6 -6 q-6 0 -6 6 v6 q0 2 -4 2z", cf)
                rect(20f, 16f, 30f, 66f, cf, rx = 15f)
                st(spines, "#fff8d8", 1f)
                st("M27 34 l6 2 M43 34 l-6 2", "#1d3a20", 2f, round = true)
                ci(30f, 40f, 1.8f, "#1d3a20")
                ci(40f, 40f, 1.8f, "#1d3a20")
                st("M31 48 q4 -3 8 0", "#1d3a20", 1.8f, round = true)
                ci(35f, 14f, 5f, "#ff7aa8")
                ci(35f, 14f, 2f, "#ffe36b")
                path("M14 82 h42 l-5 32 h-32z", pot)
                r(12f, 78f, 46f, 8f, "#ef9c70", rx = 3f)
            }
        }
        run {
            val wood = lin(s(0f, "#b07a4c"), s(1f, "#6e4628"))
            val water = lin(s(0f, "#9fe0ff", 0.75f), s(1f, "#2f8fc9", 0.85f))
            val glass = rad(s(0f, "#ffffff", 0.5f), s(0.5f, "#ffffff", 0.05f), s(1f, "#bfe8ff", 0.3f), cx = 0.35f, cy = 0.3f, r = 0.7f)
            val sh = shadowFill(0.35f)
            val bowl = circleD(50f, 52f, 34f)
            val rim = c("#d8f1ff", 0.8f)
            val bubble = c("#fff")
            reg("fish-bowl", 11f, 100f, 130f) {
                shadow(50f, 126f, 34f, 5f, sh)
                rect(22f, 84f, 56f, 8f, wood, rx = 3f)
                st("M28 92 l-4 32 M72 92 l4 32 M50 92 v32", "#6e4628", 4f, round = true)
                clip(bowl) {
                    rect(10f, 30f, 80f, 60f, water)
                    ci(34f, 84f, 5f, "#e4d6b8")
                    ci(44f, 86f, 5f, "#c9b48e")
                    ci(56f, 85f, 6f, "#efe3c8")
                    ci(68f, 83f, 5f, "#b8a07a")
                    fp("M62 82 q-4 -14 2 -26 q-8 12 -6 26z", "#3f9a55")
                    // hsFish 6s: swim right, turn, swim back, turn
                    val p = frac(t / 6f)
                    val dx: Float
                    val flip: Float
                    when {
                        p < 0.45f -> { dx = -10f + 22f * ease(p / 0.45f); flip = 1f }
                        p < 0.5f -> { dx = 12f; flip = 1f - 2f * ease((p - 0.45f) / 0.05f) }
                        p < 0.95f -> { dx = 12f - 22f * ease((p - 0.5f) / 0.45f); flip = -1f }
                        else -> { dx = -10f; flip = -1f + 2f * ease((p - 0.95f) / 0.05f) }
                    }
                    g(tx = dx, px = 44f, py = 54f, sx = flip, sy = 1f) {
                        fp("M38 54 q10 -10 20 0 q-10 10 -20 0z", "#ff8a3d")
                        fp("M38 54 l-8 -6 v12z", "#ff6a2a")
                        ci(52f, 52f, 1.6f, "#1a1a1a")
                        st("M44 50 q4 -2 6 0", "#ffd2a8", 1.2f)
                    }
                    // hsBubble 2.6s ease-in: rise, fade in then out
                    val q = frac(t / 2.6f)
                    val by = 20f - 34f * (q * q)
                    val ba = if (q < 0.3f) ease(q / 0.3f) else 1f - ease((q - 0.3f) / 0.7f)
                    circle(58f, 44f + by, 1.8f, stroke = bubble, sw = 0.8f, alpha = ba)
                }
                circle(50f, 52f, 34f, glass, stroke = rim, sw = 1.4f)
                ellipse(50f, 20f, 18f, 3.5f, stroke = c("#e8f7ff"), sw = 2f)
                st("M26 40 q4 -10 14 -14", "#fff", 3f, a = 0.8f, round = true)
            }
        }
        run {
            val b = rad(s(0f, "#ffb1a0"), s(0.55f, "#e0654c"), s(1f, "#9c3524"), cx = 0.38f, cy = 0.3f, r = 0.8f)
            val sh = shadowFill(0.4f)
            reg("beanbag", 20f, 160f, 110f) {
                shadow(80f, 102f, 76f, 8f, sh)
                path("M14 96 C0 70 18 30 56 22 C92 14 130 24 146 56 C158 80 150 98 132 100 C96 106 46 106 14 96z", b)
                fp("M40 60 C60 50 96 50 120 62 C110 76 60 78 40 60z", "#8f2e1f", 0.35f)
                st("M44 34 q30 -14 64 -4", "#fff", 5f, a = 0.45f, round = true)
                st("M30 88 q20 6 44 4 M96 90 q20 2 36 -6", "#7d2617", 2f, a = 0.4f, round = true)
            }
        }
        run {
            val wood = lin(s(0f, "#c58b55"), s(1f, "#6a4124"), x2 = 1f, y2 = 1f)
            val scr = rad(s(0f, "#c6f0ff"), s(0.6f, "#5aa6d6"), s(1f, "#1d3d5c"), cx = 0.4f, cy = 0.35f, r = 0.8f)
            val gl = gloss()
            val sh = shadowFill(0.35f)
            val screen = rrD(18f, 38f, 70f, 56f, 12f)
            val bars = arrayOf("#ff6b6b", "#ffd36b", "#7be07b", "#6bd3ff", "#9d7bff", "#ff7bd5").map { col(it) }
            val trim = c("#e2ad7a", 0.5f)
            reg("tv-retro", 17f, 130f, 140f, glow(50f, 42f, 34f, "#9fd8ff")) {
                shadow(65f, 134f, 56f, 6f, sh)
                st("M50 26 L34 2 M62 26 L86 6", "#4a4a4a", 2f, round = true)
                ci(34f, 2f, 2.5f, "#d64545")
                ci(86f, 6f, 2.5f, "#d64545")
                rect(6f, 26f, 118f, 80f, wood, rx = 14f)
                rect(10f, 30f, 110f, 72f, stroke = trim, sw = 1.5f, rx = 11f)
                r(14f, 34f, 78f, 64f, "#2b2420", rx = 14f)
                rect(18f, 38f, 70f, 56f, scr, rx = 12f)
                clip(screen) {
                    val h = 360f * frac(t / 4f)
                    for (i in bars.indices) rect(18f + i * 11.7f, 38f, 11.7f, 56f, c(hue(bars[i], h)), alpha = 0.55f)
                    ci(53f, 66f - 3f * alt(t, 1.6f), 14f, "#e2674f")
                    ci(48f, 63f, 2.2f, "#2b1d17")
                    ci(58f, 63f, 2.2f, "#2b1d17")
                    st("M47 71 q6 4 12 0", "#2b1d17", 1.8f)
                    r(18f, 38f + 56f * frac(t / 2.2f), 70f, 6f, "#fff", op = 0.15f)
                }
                rect(18f, 38f, 70f, 56f, gl, rx = 12f, alpha = 0.5f)
                ci(106f, 48f, 6f, "#3a2a20")
                ci(106f, 48f, 3f, "#d9b88f")
                ci(106f, 66f, 6f, "#3a2a20")
                ci(106f, 66f, 3f, "#d9b88f")
                st("M100 80 h12 M100 84 h12 M100 88 h12", "#3a2a20", 1.5f)
                st("M22 106 l-6 24 M108 106 l6 24", "#4a2e18", 5f, round = true)
            }
        }
    }

    /* ============================== rugs ============================== */

    private fun rugs() {
        run {
            val rr = rad(
                s(0f, "#f6d7a8"), s(0.45f, "#e9b77a"), s(0.5f, "#c86f4f"), s(0.58f, "#f1d2a2"),
                s(0.86f, "#d9a06a"), s(0.9f, "#7f5a8f"), s(1f, "#6a4a7a"),
            )
            val dashed = ellipseD(150f, 40f, 100f, 23f)
            reg("rug-round", 46f, 300f, 80f) {
                ellipse(150f, 44f, 146f, 34f, c("#2a1608"), alpha = 0.18f)
                radEllipse(150f, 40f, 144f, 34f, rr)
                path(dashed, stroke = c("#fff", 0.35f), sw = 2f, dash = D46)
                ellipse(150f, 40f, 40f, 9f, stroke = c("#a5523a", 0.5f), sw = 2f)
            }
        }
        run {
            val sheen = lin(s(0f, "#fff", 0.35f), s(0.5f, "#fff", 0f), s(1f, "#000", 0.12f))
            val cols = arrayOf("#ff6b6b", "#ffa94d", "#ffd43b", "#69db7c", "#4dabf7", "#9775fa").map { c(it) }
            val rings = Array(6) { i -> ellipseD(150f, 40f, 144f - i * 22f, 34f - i * 5.2f) }
            val edge = c("#fff", 0.25f)
            reg("rug-rainbow", 48f, 300f, 80f) {
                ellipse(150f, 44f, 148f, 34f, c("#2a1608"), alpha = 0.18f)
                for (i in 0 until 6) {
                    ellipse(150f, 40f, 144f - i * 22f, 34f - i * 5.2f, cols[i])
                    path(rings[i], stroke = edge, sw = 3f, dash = D13)
                }
                ellipse(150f, 40f, 144f, 34f, sheen)
            }
        }
        run {
            val rr = rad(s(0f, "#f1ece6"), s(0.7f, "#d8d0cc"), s(1f, "#b9aeb4"), cx = 0.45f, cy = 0.4f, r = 0.65f)
            val sheen = lin(s(0f, "#fff", 0.35f), s(0.5f, "#fff", 0f), s(1f, "#000", 0.1f))
            val tufts = buildString {
                for (i in 0 until 46) {
                    val a = i / 46.0 * PI * 2
                    append("M").append(nf((150 + cos(a) * 140).toFloat())).append(' ').append(nf((40 + sin(a) * 32).toFloat()))
                    append(" l").append(nf((cos(a) * 6).toFloat())).append(' ').append(nf((sin(a) * 3).toFloat())).append(' ')
                }
            }
            val curls = buildString {
                for (i in 0 until 70) append("M${30 + (i * 53) % 240} ${18 + (i * 29) % 44} q3 -3 5 0 ")
            }
            reg("rug-shag", 48f, 300f, 80f) {
                ellipse(150f, 46f, 148f, 33f, c("#2a1608"), alpha = 0.2f)
                radEllipse(150f, 40f, 142f, 33f, rr)
                st(tufts, "#cbbfc4", 2.6f, round = true)
                st(curls, "#a99fb6", 1.6f, a = 0.55f, round = true)
                ellipse(150f, 40f, 142f, 33f, sheen)
            }
        }
    }

    /* ============================== on the walls ============================== */

    private fun wallThings() {
        run {
            val p = lin(s(0f, "#2a2350"), s(1f, "#141029"))
            val sun = rad(s(0f, "#ffcf5a"), s(0.6f, "#ff6a6a"), s(1f, "#ff6a6a", 0f))
            reg("poster-band", 13f, 90f, 124f) {
                r(6f, 8f, 80f, 112f, "#000", rx = 2f, op = 0.18f)
                rect(3f, 4f, 80f, 112f, p, rx = 2f)
                circle(43f, 56f, 30f, sun)
                ci(43f, 60f, 16f, "#e2674f")
                ci(38f, 56f, 2.5f, "#fff")
                ci(48f, 56f, 2.5f, "#fff")
                st("M37 65 q6 -4 12 0", "#2b1d17", 2f)
                st("M60 30 l-8 14 h6 l-6 14", "#ffe36b", 2.5f, join = StrokeJoin.Round)
                label(43f, 98f, "THE CLICKS", 13f, "#ffe9d6", spacing = 1f, maxW = 76f)
                label(43f, 109f, "NO-PRESS WORLD TOUR", 5.5f, "#ff9ab0", bold = false, spacing = 1f, maxW = 76f)
                g(deg = -4f, px = 43f, py = 4f) { r(31f, 0f, 24f, 8f, "#f4ecd0", rx = 1f, op = 0.85f) }
            }
        }
        run {
            val rim = lin(s(0f, "#f3d48c"), s(1f, "#8a6224"), x2 = 1f, y2 = 1f)
            val face = rad(s(0f, "#ffffff"), s(1f, "#efe6d6"), cx = 0.4f, cy = 0.35f, r = 0.7f)
            val tick = c("#6b5446")
            reg("clock-wall", 10f, 90f, 94f) {
                ci(46f, 50f, 42f, "#000", 0.15f)
                circle(44f, 46f, 42f, rim)
                circle(44f, 46f, 35f, face)
                for (i in 0 until 12) g(deg = i * 30f, px = 44f, py = 46f) { rect(43f, 14f, 2f, if (i % 3 != 0) 4f else 7f, tick, rx = 1f) }
                // the hands show the real time
                val s = localSeconds()
                g(deg = (s / 43200.0 * 360.0).toFloat(), px = 44f, py = 46f) { r(42.5f, 26f, 3f, 22f, "#3a2a20", rx = 1.5f) }
                g(deg = ((s % 3600.0) / 3600.0 * 360.0).toFloat(), px = 44f, py = 46f) { r(43f, 18f, 2f, 30f, "#3a2a20", rx = 1f) }
                g(deg = (floor(s % 60.0) * 6.0).toFloat(), px = 44f, py = 46f) { r(43.5f, 16f, 1f, 36f, "#e2674f") }
                ci(44f, 46f, 3f, "#e2674f")
                st("M18 34 q8 -18 26 -22", "#fff", 3f, a = 0.7f, round = true)
            }
        }
        run {
            val wood = lin(s(0f, "#c08a5c"), s(1f, "#7a4c2c"))
            val pot = lin(s(0f, "#f6f3ec"), s(1f, "#bdb6aa"), x2 = 1f, y2 = 0f)
            val books = arrayOf(floatArrayOf(14f, 30f, 12f), floatArrayOf(27f, 26f, 10f), floatArrayOf(38f, 34f, 13f), floatArrayOf(52f, 28f, 9f))
            val bookCols = arrayOf("#e2674f", "#4f7bd5", "#f2c14e", "#7cc6a4")
            reg("shelf-books", 17f, 140f, 90f) {
                r(6f, 70f, 130f, 10f, "#000", rx = 2f, op = 0.14f)
                for (i in books.indices) {
                    val (x, h, w) = books[i]
                    r(x, 68f - h, w, h, bookCols[i], rx = 1.5f)
                    r(x, 68f - h, w * 0.3f, h, "#fff", op = 0.2f)
                    r(x + 2f, 72f - h, w - 4f, 2f, "#fff", op = 0.5f)
                }
                g(deg = 14f, px = 72f, py = 68f) { r(62f, 42f, 10f, 28f, "#a58bd6", rx = 1.5f) }
                path("M96 68 h18 l-2 -12 h-14z", pot)
                fp("M105 56 q-10 -14 -2 -22 q4 10 2 22 q6 -14 14 -16 q-2 12 -14 16", "#5aa865")
                fp("M124 68 v-10 h-6 v-4 h16 v4 h-6 v10z", "#e6b422")
                ci(126f, 46f, 7f, "#f2c94c")
                rect(2f, 66f, 136f, 7f, wood, rx = 2f)
                st("M20 73 v8 l8 -8 M118 73 v8 l-8 -8", "#6b6b6b", 2.5f)
            }
        }
        run {
            val gold = lin(s(0f, "#ffe7a3"), s(0.4f, "#d4a43a"), s(0.7f, "#9b6c14"), s(1f, "#e7c167"), x2 = 1f, y2 = 1f)
            val bg = rad(s(0f, "#4f6b5a"), s(1f, "#1d2a24"), cx = 0.5f, cy = 0.4f, r = 0.7f)
            val body = rad(s(0f, "#fff", 0.5f), s(0.4f, "#fff", 0f), s(1f, "#000", 0.25f), cx = 0.38f, cy = 0.3f, r = 0.75f)
            reg("art-portrait", 14f, 100f, 124f) {
                r(6f, 8f, 90f, 114f, "#000", rx = 4f, op = 0.2f)
                rect(2f, 2f, 90f, 114f, gold, rx = 4f)
                r(9f, 9f, 76f, 100f, "#7a5414", rx = 2f)
                rect(12f, 12f, 70f, 94f, bg)
                ellipse(47f, 98f, 34f, 10f, c("#1a1410"), alpha = 0.5f)
                ci(47f, 66f, 24f, "#e2674f")
                circle(47f, 66f, 24f, body)
                ci(40f, 60f, 4f, "#fff")
                ci(54f, 60f, 4f, "#fff")
                ci(41f, 61f, 2f, "#2b1d17")
                ci(55f, 61f, 2f, "#2b1d17")
                st("M40 73 q7 -3 14 0", "#2b1d17", 2.2f, round = true)
                path("M30 44 l6 -10 6 6 5 -10 5 10 6 -6 6 10z", c("#f2c94c"), stroke = c("#9b6c14"), sw = 1f)
                r(34f, 110f, 26f, 5f, "#f6e2a8", rx = 1f)
            }
        }
        run {
            reg("neon-sign", 20f, 170f, 80f) {
                rect(4f, 6f, 162f, 68f, c("#ffffff"), rx = 12f, stroke = c("#ffffff", 0.35f), alpha = 0.12f)
                val a = neon(t)
                // feGaussianBlur glow, faked with wide faint strokes under the tube
                neonText(85f, 36f, "DON'T", 24f, "#ff5fa8", 2.6f, 1f, a)
                neonText(85f, 62f, "PRESS", 22f, "#5fe3ff", 2.4f, 3f, a)
                ci(14f, 16f, 2f, "#ccc")
                ci(156f, 16f, 2f, "#ccc")
            }
        }
    }

    /* ============================== teen room ============================== */

    private fun teen() {
        run {
            val steel = lin(s(0f, "#5d6776"), s(0.5f, "#3d4552"), s(1f, "#262c36"), x2 = 1f, y2 = 0f)
            val mat = lin(s(0f, "#eef1f4"), s(1f, "#c9d0d8"))
            val quilt = lin(s(0f, "#4a8a96"), s(1f, "#2f6370"))
            val desk = lin(s(0f, "#b8946a"), s(1f, "#7c5c3a"))
            val pil = rad(s(0f, "#ffffff"), s(1f, "#d3d8e0"), cx = 0.4f, cy = 0.3f, r = 0.75f)
            val bag = rad(s(0f, "#9a8cd6"), s(0.6f, "#6b5bb0"), s(1f, "#43397c"), cx = 0.38f, cy = 0.3f, r = 0.8f)
            val scr = lin(s(0f, "#6fe3ff"), s(0.6f, "#5a6cff"), s(1f, "#b04fff"), x2 = 1f, y2 = 1f)
            val gl = gloss()
            val sh = shadowFill(0.35f)
            reg("bed-loft", 38f, 220f, 170f) {
                shadow(106f, 164f, 104f, 6f, sh)
                rect(8f, 46f, 9f, 116f, steel, rx = 2f)
                rect(190f, 46f, 9f, 116f, steel, rx = 2f)
                rect(8f, 46f, 191f, 6f, steel, rx = 2f)
                r(8f, 46f, 191f, 2f, "#fff", op = 0.18f)
                rect(8f, 94f, 191f, 8f, steel, rx = 2f)
                rect(18f, 72f, 170f, 24f, mat, rx = 8f)
                path("M22 66 q0 -12 14 -12 h26 q12 0 12 10 q0 10 -12 10 h-26 q-14 0 -14 -8z", pil)
                path("M84 76 q40 -12 100 -2 v22 h-100z", quilt)
                path("M84 76 q40 -12 100 -2 v5 q-50 -8 -100 4z", gl)
                st("M96 90 h86", "#1f4a54", 1.5f, a = 0.5f)
                r(199f, 98f, 5f, 64f, "#2e3540", rx = 2f)
                r(212f, 98f, 5f, 64f, "#2e3540", rx = 2f)
                for (y in intArrayOf(108, 122, 136, 150)) r(203f, y.toFloat(), 10f, 4f, "#8a95a6", rx = 1.5f)
                rect(26f, 126f, 116f, 6f, desk, rx = 2f)
                r(26f, 126f, 116f, 2f, "#fff", op = 0.25f)
                r(32f, 132f, 5f, 30f, "#3d4552")
                r(132f, 132f, 5f, 30f, "#3d4552")
                r(52f, 104f, 46f, 22f, "#1d2129", rx = 3f)
                rect(55f, 107f, 40f, 16f, scr, rx = 2f)
                r(70f, 124f, 10f, 3f, "#1d2129")
                r(106f, 120f, 18f, 6f, "#e2674f", rx = 2f)
                path("M148 162 C144 140 152 128 176 126 C192 128 194 150 190 162z", bag)
                st("M156 134 q12 -6 24 0", "#fff", 3f, a = 0.4f, round = true)
            }
        }
        run {
            val cov = lin(s(0f, "#2d3a6b"), s(0.55f, "#6a3f86"), s(1f, "#d6587a"), x2 = 1f, y2 = 1f)
            val sun = rad(s(0f, "#ffe29a"), s(0.7f, "#ff9a6b"), s(1f, "#ff9a6b", 0f))
            val rec = rad(s(0f, "#2a2a2f"), s(1f, "#08080b"))
            reg("poster-vinyl", 12f, 90f, 90f) {
                r(9f, 9f, 78f, 78f, "#000", rx = 2f, op = 0.22f)
                rect(6f, 6f, 78f, 78f, cov, rx = 2f)
                circle(45f, 38f, 24f, sun)
                st("M6 54 H84 M6 60 H84 M6 67 H84", "#fff", 1.5f, a = 0.28f)
                fp("M6 48 L30 40 L48 50 L66 36 L84 46 V54 H6z", "#1c1838", 0.8f)
                label(45f, 77f, "NIGHT DRIVE", 9f, "#ffe9ef", spacing = 2f, maxW = 74f)
                circle(72f, 18f, 7f, rec)
                ci(72f, 18f, 2.2f, "#d6587a")
                rect(6f, 6f, 78f, 78f, stroke = c("#fff", 0.25f), rx = 2f)
            }
        }
        run {
            val shots = arrayOf(
                Shot(10f, 24f, -8f, "#7fb6c9", "#d6a3c4"),
                Shot(46f, 34f, 5f, "#e6b46a", "#b5806a"),
                Shot(84f, 26f, -4f, "#8fb59a", "#4f7d8c"),
                Shot(120f, 36f, 7f, "#a999d6", "#e08aa0"),
            )
            val bulbs = floatArrayOf(12f, 15f, 34f, 22f, 58f, 24f, 80f, 14f, 104f, 22f, 128f, 24f, 150f, 12f)
            val halo = rad(s(0f, "#ffd98a", 0.5f), s(0.55f, "#ffd98a", 0.22f), s(1f, "#ffd98a", 0f))
            reg("photo-wall", 20f, 160f, 110f) {
                st("M2 6 Q40 30 80 14 Q120 30 158 8", "#3a3a42", 1.2f)
                var i = 0
                while (i < bulbs.size) {
                    val x = bulbs[i]
                    val y = bulbs[i + 1]
                    g(alpha = twinkle(t, i / 2 * 0.3f)) {
                        circle(x, y + 2f, 9f, halo)
                        ci(x, y + 2f, 2.2f, "#ffe7a8")
                    }
                    i += 2
                }
                for (sh in shots) {
                    val x = sh.x
                    val y = sh.y
                    g(deg = sh.r, px = x + 14f, py = y) {
                        r(x + 2f, y + 3f, 28f, 36f, "#000", op = 0.2f)
                        r(x, y, 28f, 36f, "#f7f4ee")
                        r(x + 3f, y + 3f, 22f, 22f, sh.c1)
                        fp(sh.d, sh.c2)
                        ci(x + 19f, y + 9f, 3f, "#fff", 0.7f)
                        r(x + 11f, y - 3f, 6f, 8f, "#c99a5c", rx = 1f)
                    }
                }
            }
        }
        run {
            val fills = arrayOf(
                lin(s(0f, "#e2674f"), s(1f, "#a53a28"), x2 = 1f, y2 = 0f),
                lin(s(0f, "#3f8f9c"), s(1f, "#27606b"), x2 = 1f, y2 = 0f),
                lin(s(0f, "#2d2f3b"), s(1f, "#15161d"), x2 = 1f, y2 = 0f),
            )
            val xs = floatArrayOf(10f, 48f, 86f)
            val rs = floatArrayOf(-6f, 0f, 6f)
            reg("skate-decks", 15f, 120f, 120f) {
                for (i in 0 until 3) {
                    val x = xs[i]
                    g(deg = rs[i], px = x + 12f, py = 60f) {
                        r(x + 3f, 11f, 24f, 102f, "#000", rx = 12f, op = 0.2f)
                        rect(x, 8f, 24f, 102f, fills[i], rx = 12f)
                        r(x, 8f, 6f, 102f, "#fff", rx = 3f, op = 0.18f)
                        ci(x + 6f, 24f, 1.6f, "#8a95a6")
                        ci(x + 18f, 24f, 1.6f, "#8a95a6")
                        ci(x + 6f, 94f, 1.6f, "#8a95a6")
                        ci(x + 18f, 94f, 1.6f, "#8a95a6")
                    }
                }
                ci(22f, 52f, 7f, "#ffd36b")
                st("M14 66 L30 58 M14 74 L30 66", "#fff", 2.5f, a = 0.8f)
                fp("M54 46 l12 18 l12 -18 z", "#ffd36b")
                circle(66f, 76f, 6f, stroke = c("#9fe0e8"), sw = 2.5f)
                st("M94 46 h20 M94 54 h20 M94 62 h14", "#ff5fa8", 3f, round = true)
                ci(104f, 82f, 5f, "#5fe3ff")
            }
        }
        run {
            val l = lin(s(0f, "#ff4fd8"), s(0.5f, "#a56bff"), s(1f, "#4fe3ff"), x2 = 1f, y2 = 0f)
            val halo = lin(s(0f, "#c06bff", 0.55f), s(1f, "#c06bff", 0f))
            val dot = c("#fff")
            reg("led-strip", 100f, 400f, 30f, glow(50f, 60f, 70f, "#d96bff")) {
                rect(0f, 6f, 400f, 24f, halo)
                r(0f, 0f, 400f, 7f, "#20232b")
                rect(6f, 2f, 388f, 3.6f, l, rx = 1.8f)
                g(alpha = 0.8f) { for (i in 0 until 33) circle(10f + i * 12f, 3.8f, 0.9f, dot) }
                r(6f, 2f, 388f, 1.2f, "#fff", op = 0.4f)
            }
        }
        run {
            val leaf = lin(s(0f, "#4f9a6a"), s(0.6f, "#2d6b47"), s(1f, "#1b4430"), x2 = 1f, y2 = 0f)
            val pot = lin(s(0f, "#6a7280"), s(0.5f, "#3f4651"), s(1f, "#262b33"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            val leaves = arrayOf(
                "M40 120 C26 90 24 50 30 14 C42 44 46 90 46 120z",
                "M44 120 C52 80 62 50 66 26 C70 56 62 96 52 122z",
                "M38 122 C24 100 8 76 6 52 C20 70 34 96 44 122z",
                "M46 120 C58 98 70 90 76 70 C76 96 64 112 52 124z",
                "M40 120 C36 84 40 48 44 4 C52 40 52 86 48 120z",
            )
            val edge = c("#d9d46a", 0.85f)
            val band = c("#18382a", 0.35f)
            reg("plant-snake", 10f, 80f, 170f) {
                shadow(40f, 165f, 28f, 5f, sh)
                for (d in leaves) {
                    path(d, leaf, stroke = edge, sw = 1.6f)
                    path(d, stroke = band, sw = 0.8f, dash = D56)
                }
                path("M22 122 h40 l-5 40 q-15 4 -30 0z", pot)
                r(20f, 118f, 44f, 8f, "#59616e", rx = 3f)
                st("M28 130 l2 26", "#fff", 3f, a = 0.25f, round = true)
                st("M26 162 l-4 4 M58 162 l4 4", "#b88a3a", 3f, round = true)
            }
        }
        run {
            val body = lin(s(0f, "#bfe0d6"), s(0.55f, "#9ccbbd"), s(1f, "#6f9f94"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            reg("mini-fridge", 14f, 100f, 150f) {
                shadow(50f, 144f, 42f, 5f, sh)
                rect(10f, 6f, 80f, 132f, body, rx = 12f)
                rect(10f, 6f, 80f, 132f, stroke = c("#fff", 0.4f), sw = 1.5f, rx = 12f)
                st("M10 48 H90", "#5f8a80", 2f)
                r(76f, 16f, 5f, 24f, "#e6e2d6", rx = 2.5f)
                r(76f, 58f, 5f, 40f, "#e6e2d6", rx = 2.5f)
                ci(24f, 28f, 5.5f, "#7cffb0", 0.3f)
                ci(24f, 28f, 2.6f, "#7cffb0")
                g(deg = -4f, px = 43f, py = 69f) {
                    r(32f, 56f, 22f, 26f, "#fff8e6")
                    st("M36 62 h14 M36 68 h10 M36 74 h12", "#e2674f", 1.4f)
                }
                ci(30f, 54f, 3.2f, "#e2674f")
                r(26f, 92f, 12f, 9f, "#5b8fd6", rx = 2f)
                fp("M54 92 l5 -3 5 3 -2 6 h-6z", "#ffd36b")
                ci(44f, 116f, 4f, "#9775fa")
                st("M16 12 q-2 60 0 120", "#fff", 4f, a = 0.4f, round = true)
                st("M18 138 l-2 6 M82 138 l2 6", "#3b3f46", 5f, round = true)
            }
        }
        run {
            val wood = lin(s(0f, "#b98458"), s(1f, "#6c4528"), x2 = 1f, y2 = 1f)
            val deck = lin(s(0f, "#4a4f5a"), s(1f, "#2b2f38"))
            val top = lin(s(0f, "#656b78"), s(1f, "#3a3f49"))
            val sh = shadowFill(0.35f)
            val groove = c("#2a2b33")
            reg("record-player", 20f, 160f, 110f) {
                shadow(80f, 104f, 66f, 5f, sh)
                st("M24 70 l-6 34 M136 70 l6 34", "#4e3320", 5f, round = true)
                rect(14f, 62f, 132f, 14f, wood, rx = 4f)
                r(14f, 62f, 132f, 3f, "#fff", op = 0.2f)
                r(30f, 80f, 14f, 22f, "#e2674f")
                r(46f, 82f, 12f, 20f, "#4f7bd5")
                r(60f, 78f, 14f, 24f, "#f2c14e")
                r(76f, 82f, 12f, 20f, "#7cc6a4")
                r(30f, 80f, 58f, 4f, "#000", op = 0.15f)
                path("M18 62 l8 -30 h108 l8 30z", deck)
                st("M26 32 h108", "#fff", 1.5f, a = 0.25f)
                path("M18 62 h124 l-1 -4 h-122z", top)
                g(tx = 66f, ty = 47f, sx = 1f, sy = 0.34f) {
                    ci(0f, 0f, 35f, "#15161a")
                    circle(0f, 0f, 36f, stroke = c("#fff", 0.15f))
                    g(deg = 360f * frac(t / 3.2f)) {
                        ci(0f, 0f, 30f, "#0b0b0e")
                        circle(0f, 0f, 24f, stroke = groove, sw = 1f)
                        circle(0f, 0f, 18f, stroke = groove, sw = 1f)
                        ci(0f, 0f, 11f, "#d6587a")
                        r(-2f, -11f, 4f, 9f, "#ffe9ef", op = 0.8f)
                        ci(0f, 0f, 1.5f, "#0b0b0e")
                        st("M-26 -8 q10 -16 28 -20", "#fff", 3f, a = 0.2f)
                    }
                }
                ci(122f, 38f, 5f, "#8a95a6")
                st("M122 38 L96 52 L90 50", "#d8dde6", 2.4f, round = true, join = StrokeJoin.Round)
                g(deg = -24f, px = 90f, py = 50f) { r(86f, 48f, 8f, 5f, "#2b2f38", rx = 1f) }
                circle(30f, 54f, 3.4f, c("#2b2f38"), stroke = c("#8a95a6"))
                ci(30f, 44f, 2f, "#7cffb0")
            }
        }
        run {
            val body = lin(s(0f, "#4fb3c4"), s(0.6f, "#2b6f86"), s(1f, "#17394b"), x2 = 1f, y2 = 1f)
            val neck = lin(s(0f, "#c9a06a"), s(1f, "#8a6238"), x2 = 1f, y2 = 0f)
            val amp = lin(s(0f, "#3a3f4a"), s(1f, "#1e2128"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            reg("guitar-stand", 17f, 140f, 170f) {
                shadow(70f, 165f, 62f, 5f, sh)
                rect(76f, 116f, 58f, 48f, amp, rx = 4f)
                rect(76f, 116f, 58f, 48f, stroke = c("#fff", 0.2f), rx = 4f)
                r(82f, 130f, 46f, 28f, "#8d7a5a", rx = 2f)
                st("M82 134 h46 M82 138 h46 M82 142 h46 M82 146 h46 M82 150 h46 M82 154 h46", "#5a4a32", 0.8f)
                for (x in intArrayOf(88, 100, 112, 124)) ci(x.toFloat(), 123f, 2.4f, "#c9ced8")
                st("M30 156 L14 166 M30 156 L48 166 M30 156 L40 138", "#2b2f38", 3f, round = true)
                g(deg = -6f, px = 46f, py = 90f) {
                    rect(43f, 14f, 7f, 76f, neck)
                    st("M43 26 h7 M43 38 h7 M43 50 h7 M43 62 h7 M43 74 h7", "#e8e2d0", 0.8f)
                    fp("M42 14 l-1 -12 q5 -3 10 0 l-1 12z", "#2b2f38")
                    ci(40f, 6f, 1.3f, "#c9ced8")
                    ci(40f, 10f, 1.3f, "#c9ced8")
                    ci(53f, 6f, 1.3f, "#c9ced8")
                    ci(53f, 10f, 1.3f, "#c9ced8")
                    ellipse(46f, 98f, 20f, 16f, body)
                    ellipse(46f, 124f, 26f, 26f, body)
                    g(deg = -20f, px = 38f, py = 92f) { ellipse(38f, 92f, 10f, 6f, c("#fff"), alpha = 0.22f) }
                    fp("M34 112 q12 -6 24 0 q4 16 -4 28 q-12 6 -22 0 q-6 -12 2 -28z", "#f4f1ea", 0.85f)
                    r(36f, 106f, 20f, 4f, "#1d2129", rx = 1.5f)
                    r(36f, 118f, 20f, 4f, "#1d2129", rx = 1.5f)
                    ci(58f, 136f, 2.4f, "#c9ced8")
                    ci(52f, 144f, 2.4f, "#c9ced8")
                }
            }
        }
        run {
            val blk = lin(s(0f, "#3a3d48"), s(0.5f, "#22242c"), s(1f, "#14151a"), x2 = 1f, y2 = 0f)
            val acc = lin(s(0f, "#ff4f8b"), s(1f, "#b81f58"))
            val gl = gloss()
            val sh = shadowFill(0.35f)
            reg("chair-gaming", 14f, 100f, 170f) {
                shadow(50f, 165f, 38f, 5f, sh)
                st("M50 140 L12 158 M50 140 L88 158 M50 140 V160 M50 140 L36 162 M50 140 L64 162", "#2b2f38", 4.5f, round = true)
                ci(12f, 160f, 4.5f, "#14151a")
                ci(88f, 160f, 4.5f, "#14151a")
                ci(36f, 164f, 4f, "#14151a")
                ci(64f, 164f, 4f, "#14151a")
                r(44f, 114f, 12f, 30f, "#8a95a6", rx = 3f)
                r(46f, 114f, 3f, 30f, "#fff", op = 0.35f)
                path("M20 8 q30 -8 60 0 l8 30 l-4 54 h-68 l-4 -54z", blk)
                path("M12 38 l8 -30 q-6 20 0 56 l4 28 h-12z M88 38 l-8 -30 q6 20 0 56 l-4 28 h12z", acc)
                r(34f, 40f, 32f, 48f, "#2d303b", rx = 6f)
                st("M50 42 v44", "#ff4f8b", 3f)
                st("M40 46 v38 M60 46 v38", "#14151a", 1.5f)
                r(32f, 10f, 36f, 22f, "#2d303b", rx = 9f)
                rect(32f, 10f, 36f, 22f, gl, rx = 9f, alpha = 0.35f)
                st("M38 18 h24", "#ff4f8b", 2.5f, round = true)
                path("M14 98 q-2 16 6 18 h60 q8 -2 6 -18 q-36 8 -72 0z", blk)
                st("M16 112 q34 8 68 0", "#ff4f8b", 2.5f)
                r(6f, 84f, 10f, 8f, "#2d303b", rx = 3f)
                r(84f, 84f, 10f, 8f, "#2d303b", rx = 3f)
                st("M10 92 v6 M90 92 v6", "#8a95a6", 3f)
            }
        }
        run {
            val top = lin(s(0f, "#4a4f5c"), s(1f, "#262a33"))
            val s1 = lin(s(0f, "#5fe3ff"), s(0.55f, "#5a6cff"), s(1f, "#c04fff"), x2 = 1f, y2 = 1f)
            val s2 = lin(s(0f, "#ff6aa8"), s(0.5f, "#8a5cff"), s(1f, "#3a7bff"), x1 = 0f, y1 = 1f, x2 = 1f, y2 = 0f)
            val tw = lin(s(0f, "#2e323d"), s(1f, "#14161c"), x2 = 1f, y2 = 0f)
            val under = lin(s(0f, "#c06bff", 0.55f), s(1f, "#c06bff", 0f))
            val gl = gloss()
            val sh = shadowFill(0.35f)
            val cyan = col("#5fe3ff")
            val violet = col("#b04fff")
            val pink = col("#ff4fd8")
            val aqua = col("#4fe3ff")
            reg("desk-gaming", 30f, 240f, 150f, glow(40f, 32f, 38f, "#7a8cff")) {
                val h = 360f * frac(t / 5f)
                shadow(120f, 146f, 112f, 5f, sh)
                rect(6f, 100f, 228f, 22f, under)
                r(14f, 100f, 6f, 46f, "#2b2f38")
                r(220f, 100f, 6f, 46f, "#2b2f38")
                rect(170f, 112f, 42f, 34f, tw, rx = 3f)
                r(174f, 116f, 26f, 26f, "#0d0e12", rx = 2f)
                circle(187f, 129f, 9f, stroke = c(hue(cyan, h)), sw = 2.4f)
                ci(187f, 129f, 3f, "#ff4fd8")
                path("M205 118 v22", stroke = c(hue(violet, h)), sw = 2f)
                rect(2f, 92f, 236f, 9f, top, rx = 3f)
                r(2f, 92f, 236f, 2f, "#fff", op = 0.2f)
                r(12f, 14f, 86f, 56f, "#14161c", rx = 4f)
                rect(16f, 18f, 78f, 46f, s1, rx = 2f)
                fp("M16 56 L34 40 L48 52 L64 34 L94 56 V64 H16z", "#14163a", 0.55f)
                ci(76f, 30f, 7f, "#ffe29a", 0.9f)
                rect(16f, 18f, 78f, 46f, gl, rx = 2f, alpha = 0.3f)
                st("M55 70 v18", "#1d2129", 5f)
                r(42f, 86f, 28f, 4f, "#1d2129", rx = 2f)
                r(104f, 14f, 86f, 56f, "#14161c", rx = 4f)
                rect(108f, 18f, 78f, 46f, s2, rx = 2f)
                g(alpha = 0.6f) {
                    r(114f, 24f, 26f, 4f, "#0d0e12", rx = 1f)
                    r(114f, 32f, 40f, 4f, "#0d0e12", rx = 1f)
                    r(114f, 40f, 32f, 4f, "#0d0e12", rx = 1f)
                }
                rect(108f, 18f, 78f, 46f, gl, rx = 2f, alpha = 0.3f)
                st("M147 70 v18", "#1d2129", 5f)
                r(134f, 86f, 28f, 4f, "#1d2129", rx = 2f)
                r(52f, 84f, 76f, 8f, "#15171d", rx = 2f)
                rect(54f, 86f, 72f, 4f, c(hue(pink, h)), rx = 1f, alpha = 0.9f)
                rect(54f, 86f, 36f, 4f, c(hue(aqua, h)), rx = 1f, alpha = 0.9f)
                ellipse(148f, 92f, 7f, 3f, c("#15171d"))
                r(204f, 78f, 14f, 14f, "#e2674f", rx = 2f)
                st("M208 78 v-5 M214 78 v-7", "#ffd36b", 1.6f)
            }
        }
    }

    /* ============================== bedroom ============================== */

    private fun bedroom() {
        run {
            val wood = lin(s(0f, "#c58a55"), s(1f, "#8f5a33"), x2 = 1f, y2 = 0f)
            val shade = lin(s(0f, "#ffe6a8"), s(0.5f, "#ffd27a"), s(1f, "#e8a94f"), x2 = 1f, y2 = 0f)
            val bulb = rad(s(0f, "#fffbe6"), s(0.6f, "#ffe39a", 0.7f), s(1f, "#ffc46b", 0f))
            val sh = shadowFill(0.35f)
            reg("lamp-bedside", 10f, 100f, 130f, glow(50f, 24f, 44f, "#ffc46b")) {
                shadow(50f, 124f, 44f, 5f, sh)
                rect(12f, 76f, 76f, 46f, wood, rx = 5f)
                r(6f, 68f, 88f, 10f, "#d9a06b", rx = 5f)
                r(6f, 68f, 88f, 3f, "#fff", rx = 1.5f, op = 0.35f)
                r(20f, 86f, 60f, 26f, "#7a4a2a", rx = 4f, op = 0.5f)
                r(22f, 88f, 56f, 22f, "#b3774a", rx = 3f)
                ci(50f, 99f, 3f, "#f3d48c")
                ellipse(50f, 66f, 15f, 4f, c("#4b4540"))
                r(48f, 38f, 4f, 28f, "#4b4540")
                radEllipse(50f, 36f, 30f, 22f, bulb)
                path("M28 40 L38 6 H62 L72 40 Z", shade)
                st("M34 36 L41 10", "#fff", 3f, a = 0.5f, round = true)
                ellipse(50f, 40f, 22f, 4.5f, c("#fff4cf"))
                ellipse(50f, 40f, 22f, 4.5f, c("#caa05a"), alpha = 0.5f)
            }
        }
        run {
            val body = lin(s(0f, "#ffffff"), s(1f, "#d6dce4"), x2 = 1f, y2 = 0f)
            val shade = lin(s(0f, "#7fe0d4"), s(1f, "#2fa89c"), x2 = 1f, y2 = 0f)
            val bulb = rad(s(0f, "#f0ffff"), s(0.6f, "#bff4ff", 0.7f), s(1f, "#9fe3ff", 0f))
            val sh = shadowFill(0.35f)
            reg("nightstand-modern", 10f, 100f, 130f, glow(50f, 24f, 44f, "#9fe3ff")) {
                shadow(50f, 124f, 42f, 5f, sh)
                rect(14f, 70f, 72f, 50f, body, rx = 6f)
                r(20f, 78f, 60f, 18f, "#aab4c2", rx = 3f, op = 0.55f)
                r(20f, 100f, 60f, 14f, "#2b3342", rx = 3f)
                r(26f, 103f, 22f, 8f, "#ff7a8a", rx = 1.5f)
                r(50f, 104f, 24f, 7f, "#ffd36b", rx = 1.5f)
                r(14f, 66f, 72f, 8f, "#2b3342", rx = 4f)
                st("M18 120 l-2 6 M82 120 l2 6", "#2b3342", 4f, round = true)
                st("M50 66 C 50 50 56 40 62 30", "#2b3342", 3.5f, round = true)
                radEllipse(64f, 26f, 28f, 20f, bulb)
                path("M48 32 Q54 6 78 10 Q80 26 76 34 Z", shade)
                st("M54 26 Q58 14 68 13", "#fff", 2.5f, a = 0.5f, round = true)
                ellipse(62f, 34f, 14f, 3f, c("#eaffff"))
            }
        }
        run {
            val wood = lin(s(0f, "#7c5a43"), s(0.5f, "#5d4130"), s(1f, "#43301f"), x2 = 1f, y2 = 0f)
            val mir = lin(s(0f, "#dff2ff"), s(0.5f, "#a9cfe6"), s(1f, "#7fa7c4"), x2 = 1f, y2 = 1f)
            val sh = shadowFill(0.35f)
            reg("wardrobe-tall", 12f, 110f, 240f) {
                shadow(55f, 234f, 50f, 5f, sh)
                rect(6f, 6f, 98f, 222f, wood, rx = 6f)
                r(2f, 2f, 106f, 10f, "#3a281a", rx = 4f)
                r(14f, 20f, 38f, 196f, "#000", rx = 3f, op = 0.18f)
                r(58f, 20f, 38f, 196f, "#000", rx = 3f, op = 0.18f)
                rect(17f, 23f, 32f, 190f, mir, rx = 2f)
                st("M22 30 L44 90 M22 60 L30 80", "#fff", 4f, a = 0.55f, round = true)
                r(61f, 23f, 32f, 190f, "#6b4c37", rx = 2f)
                st("M61 120 h32", "#000", 1.5f, a = 0.2f)
                fp("M86 126 q8 4 4 18 q-6 2 -8 -4z", "#ff7aa8")
                r(52f, 108f, 3f, 26f, "#f3d48c", rx = 1.5f)
                r(57f, 108f, 3f, 26f, "#f3d48c", rx = 1.5f)
                st("M14 228 v8 M96 228 v8", "#2c1f14", 6f, round = true)
            }
        }
        run {
            val top = lin(s(0f, "#e6c79c"), s(1f, "#c79f6d"))
            val side = lin(s(0f, "#b88457"), s(1f, "#8a5d3a"), x2 = 1f, y2 = 0f)
            val scr = lin(s(0f, "#8fe3ff"), s(1f, "#4f8be0"))
            val sh = shadowFill(0.35f)
            reg("desk-study", 20f, 200f, 140f) {
                shadow(100f, 134f, 92f, 6f, sh)
                rect(12f, 62f, 176f, 10f, top, rx = 4f)
                r(12f, 62f, 176f, 3f, "#fff", rx = 1.5f, op = 0.4f)
                rect(18f, 72f, 8f, 58f, side, rx = 2f)
                rect(126f, 72f, 56f, 58f, side, rx = 3f)
                r(132f, 78f, 44f, 22f, "#a06f45", rx = 2f)
                r(132f, 104f, 44f, 22f, "#a06f45", rx = 2f)
                ci(154f, 89f, 2.4f, "#f3d48c")
                ci(154f, 115f, 2.4f, "#f3d48c")
                fp("M34 62 l6 -34 h54 l6 34z", "#2b3342")
                rect(42f, 32f, 48f, 26f, scr, rx = 2f)
                st("M46 38 h20 M46 44 h30 M46 50 h14", "#fff", 2f, a = 0.75f, round = true)
                fp("M30 62 h78 l-4 -6 h-70z", "#1f2530")
                r(112f, 46f, 34f, 16f, "#e2674f", rx = 2f)
                r(114f, 38f, 30f, 10f, "#5b8fd6", rx = 2f)
                r(116f, 31f, 26f, 8f, "#ffd36b", rx = 2f)
                st("M160 62 v-26 l14 -14", "#4b4540", 3f, round = true)
                fp("M168 22 l16 10 l-6 6z", "#7fd6a0")
                r(150f, 50f, 12f, 12f, "#fffaf2", rx = 3f)
                st("M162 53 q6 2 0 6", "#fffaf2", 2f)
            }
        }
        run {
            val n = 16
            val bx = FloatArray(n)
            val by = FloatArray(n)
            val bd = FloatArray(n)
            for (k in 0 until 2) for (i in 0 until 8) {
                val j = k * 8 + i
                val tt = (i + 0.5f) / 8f
                bx[j] = k * 200f + 8f + tt * 184f
                by[j] = 6f + sin(tt * PI).toFloat() * 30f
                bd[j] = (i % 4) * 0.5f
            }
            reg("string-lights", 100f, 400f, 60f) {
                st("M8 4 Q104 52 200 4 Q296 52 392 4", "#3a3530", 1.3f)
                for (j in 0 until n) {
                    val x = bx[j]
                    val y = by[j]
                    g(alpha = twinkle(t, bd[j])) {
                        ci(x, y + 7f, 10f, "#ffd98f", 0.28f)
                        r(x - 1.8f, y, 3.6f, 3.4f, "#5b5a52")
                        ci(x, y + 7f, 4.6f, "#fff0c2")
                        ci(x - 1.4f, y + 5.6f, 1.3f, "#fff", 0.9f)
                    }
                }
            }
        }
        run {
            val glass = lin(s(0f, "#e8f6ff"), s(0.5f, "#b7d6ea"), s(1f, "#86aac4"), x2 = 1f, y2 = 1f)
            val frame = lin(s(0f, "#f3d48c"), s(0.5f, "#c79a45"), s(1f, "#8a6425"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            reg("floor-mirror", 8f, 80f, 220f) {
                shadow(40f, 212f, 34f, 5f, sh)
                st("M14 212 L30 190 M66 212 L50 190", "#5a4328", 5f, round = true)
                rect(8f, 6f, 64f, 192f, frame, rx = 30f)
                rect(14f, 12f, 52f, 180f, glass, rx = 26f)
                st("M24 40 L44 120 M24 76 L34 104", "#fff", 5f, a = 0.6f, round = true)
                st("M56 150 q-4 20 -12 30", "#fff", 3f, a = 0.3f, round = true)
            }
        }
    }

    /* ============================== kitchen ============================== */

    private fun kitchen() {
        run {
            val top = lin(s(0f, "#f0d3a8"), s(1f, "#d3a874"))
            val leg = lin(s(0f, "#c58a55"), s(1f, "#8f5a33"), x2 = 1f, y2 = 0f)
            val seat = lin(s(0f, "#ff8f78"), s(1f, "#d6523d"))
            val sh = shadowFill(0.35f)
            reg("kitchen-table", 24f, 240f, 150f) {
                shadow(120f, 142f, 108f, 7f, sh)
                path("M112 76 h16 l4 56 h-24z", leg)
                ellipse(120f, 134f, 30f, 6f, c("#8f5a33"))
                ellipse(120f, 72f, 84f, 18f, c("#a77544"))
                ellipse(120f, 66f, 84f, 18f, top)
                ellipse(96f, 62f, 40f, 6f, c("#fff"), alpha = 0.2f)
                r(104f, 48f, 16f, 12f, "#fffaf2", rx = 3f)
                st("M120 52 q5 1 0 6", "#fffaf2", 2f)
                st("M30 118 l-4 24 M70 118 l4 24", "#4b4540", 4f, round = true)
                ellipse(50f, 114f, 26f, 9f, seat)
                ellipse(50f, 110f, 26f, 9f, c("#ff9f8a"))
                st("M170 118 l-4 24 M210 118 l4 24", "#4b4540", 4f, round = true)
                ellipse(190f, 114f, 26f, 9f, seat)
                ellipse(190f, 110f, 26f, 9f, c("#ff9f8a"))
            }
        }
        run {
            val steel = lin(s(0f, "#eef1f5"), s(0.5f, "#c4cad3"), s(1f, "#8e97a4"), x2 = 1f, y2 = 0f)
            val glass = lin(s(0f, "#2b3342"), s(1f, "#12161f"))
            val sh = shadowFill(0.35f)
            reg("stove-range", 13f, 110f, 130f) {
                shadow(55f, 124f, 48f, 5f, sh)
                rect(8f, 22f, 94f, 100f, steel, rx = 6f)
                fp("M6 22 l8 -14 h82 l8 14z", "#3a404a")
                for (x in floatArrayOf(32f, 78f)) {
                    ellipse(x, 15f, 13f, 3.6f, c("#1b1f27"))
                    ellipse(x, 15f, 8f, 2.2f, c("#ff7a3a"), alpha = twinkle(t))
                }
                for (x in intArrayOf(22, 42, 62, 82)) ci(x + 4f, 34f, 4.4f, "#2b3342")
                r(16f, 46f, 78f, 66f, "#9aa3b0", rx = 5f)
                rect(20f, 50f, 70f, 8f, steel, rx = 2f)
                rect(22f, 62f, 66f, 40f, glass, rx = 4f)
                st("M28 68 L44 96", "#fff", 5f, a = 0.3f, round = true)
                st("M14 122 v4 M96 122 v4", "#4b4540", 5f, round = true)
            }
        }
        run {
            val body = lin(s(0f, "#4a4f5c"), s(1f, "#1f232b"), x2 = 1f, y2 = 0f)
            val cof = lin(s(0f, "#8a5a3a"), s(1f, "#4a2a18"))
            val sh = shadowFill(0.35f)
            reg("coffee-machine", 8f, 80f, 100f) {
                shadow(40f, 96f, 34f, 4f, sh)
                rect(10f, 6f, 60f, 22f, body, rx = 8f)
                rect(10f, 20f, 12f, 70f, body, rx = 3f)
                r(10f, 84f, 60f, 9f, "#15181e", rx = 3f)
                r(30f, 28f, 22f, 10f, "#8e97a4", rx = 2f)
                st("M36 38 v8 M46 38 v8", "#aab3c0", 3f)
                r(30f, 58f, 24f, 26f, "#fff", rx = 3f, op = 0.85f)
                rect(31f, 68f, 22f, 15f, cof, rx = 2f)
                st("M54 62 q8 2 6 12 q-2 4 -6 4", "#fff", 3f)
                ci(58f, 16f, 3f, "#ff5a4f")
                ci(48f, 16f, 3f, "#7cffb0", twinkle(t))
            }
        }
        run {
            val chr = lin(s(0f, "#f4f6f9"), s(0.45f, "#b9c0ca"), s(1f, "#7e8794"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            reg("toaster", 7f, 80f, 56f) {
                shadow(40f, 52f, 34f, 4f, sh)
                fp("M24 16 q0 -10 6 -10 h4 v14z M46 16 q0 -10 6 -10 h4 v14z", "#e8b866")
                rect(8f, 14f, 64f, 34f, chr, rx = 14f)
                r(16f, 14f, 48f, 5f, "#262b34", rx = 2.5f)
                st("M14 24 q4 -4 14 -4", "#fff", 3f, a = 0.7f, round = true)
                r(70f, 26f, 6f, 12f, "#ff5a4f", rx = 3f)
                ci(22f, 40f, 3f, "#262b34")
            }
        }
        run {
            val bowl = lin(s(0f, "#7fc8d6"), s(1f, "#3f8fa3"))
            val ap = rad(s(0f, "#ff9a8a"), s(1f, "#c8362a"), cx = 0.35f, cy = 0.3f, r = 0.8f)
            val sh = shadowFill(0.35f)
            reg("fruit-bowl", 8f, 100f, 60f) {
                shadow(50f, 56f, 40f, 4f, sh)
                circle(34f, 22f, 13f, ap)
                st("M34 9 q2 -6 8 -6", "#4b7a3a", 2.4f, round = true)
                ci(62f, 24f, 12f, "#ffb347")
                ci(58f, 19f, 3f, "#fff", 0.4f)
                fp("M40 24 q20 -22 44 -6 q-18 -4 -40 14z", "#ffd84a")
                path("M10 28 h80 q-4 24 -20 26 h-40 q-16 -2 -20 -26z", bowl)
                st("M16 34 q10 14 24 16", "#fff", 3f, a = 0.45f, round = true)
                r(8f, 26f, 84f, 5f, "#a8dce6", rx = 2.5f)
            }
        }
        run {
            val wood = lin(s(0f, "#d9a06b"), s(1f, "#a8703f"))
            val xs = floatArrayOf(14f, 42f, 70f, 98f, 126f)
            val cs = arrayOf("#e2674f", "#ffd36b", "#7fd6a0", "#b58cf0", "#ff9f5a")
            val shine = Array(5) { i -> "M${n0(xs[i] + 5f)} ${26 + (i % 2) * 4} v18" }
            reg("wall-shelf-spices", 14f, 160f, 80f) {
                for (i in 0 until 5) {
                    val x = xs[i]
                    val yo = (i % 2) * 4f
                    r(x, 20f + yo, 22f, 36f - yo, "#fffaf2", rx = 4f, op = 0.9f)
                    r(x + 2f, 30f + yo, 18f, 16f, cs[i], rx = 2f)
                    r(x - 1f, 14f + yo, 24f, 8f, "#4b4540", rx = 3f)
                    st(shine[i], "#fff", 2.4f, a = 0.5f, round = true)
                }
                rect(2f, 56f, 156f, 10f, wood, rx = 3f)
                r(2f, 56f, 156f, 3f, "#fff", rx = 1.5f, op = 0.35f)
                st("M14 66 v8 h8 M138 66 v8 h8", "#6b4a2a", 4f, round = true)
            }
        }
        run {
            val glass = rad(s(0f, "#fff2cf"), s(0.5f, "#ffc46b"), s(1f, "#e08a3a"), cx = 0.4f, cy = 0.35f, r = 0.8f)
            reg("pendant-kitchen", 10f, 80f, 150f, glow(50f, 82f, 44f, "#ffcf8a")) {
                st("M40 0 V78", "#2b2b2b", 1.6f)
                r(34f, 70f, 12f, 14f, "#2b3342", rx = 3f)
                ci(40f, 112f, 28f, "#fff0c2", 0.22f)
                path("M18 112 a22 22 0 0 1 44 0 a22 22 0 0 1 -44 0z", glass)
                st("M26 100 q4 -10 14 -12", "#fff", 3.4f, a = 0.75f, round = true)
                fp("M36 82 h8 l2 8 h-12z", "#2b3342")
            }
        }
        run {
            val top = lin(s(0f, "#e9cfa6"), s(1f, "#c79f6d"))
            val body = lin(s(0f, "#4f8f9a"), s(1f, "#2f6370"), x2 = 1f, y2 = 0f)
            val sh = shadowFill(0.35f)
            reg("kitchen-island", 24f, 240f, 130f) {
                shadow(120f, 124f, 112f, 6f, sh)
                rect(14f, 40f, 212f, 76f, body, rx = 6f)
                r(6f, 28f, 228f, 16f, "#a77544", rx = 5f)
                rect(6f, 24f, 228f, 14f, top, rx = 5f)
                r(6f, 24f, 228f, 3f, "#fff", rx = 1.5f, op = 0.4f)
                for (i in 0 until 3) {
                    r(24f + i * 68f, 52f, 60f, 56f, "#000", rx = 4f, op = 0.14f)
                    r(26f + i * 68f, 54f, 56f, 52f, "#5ea0ac", rx = 3f)
                    r(46f + i * 68f, 60f, 16f, 3f, "#f3d48c", rx = 1.5f)
                }
                r(150f, 14f, 40f, 10f, "#8f5a33", rx = 3f)
                fp("M60 24 q4 -16 18 -16 q14 0 16 16z", "#fffaf2")
                st("M70 12 q2 -8 8 -9", "#4b7a3a", 2.4f, round = true)
                st("M20 116 v8 M220 116 v8", "#1f3a42", 6f, round = true)
            }
        }
    }

    /* ============================== food ============================== */

    private fun foods() {
        run {
            val cf = rad(s(0f, "#f1c27d"), s(0.8f, "#c98b45"), s(1f, "#9c6428"), cx = 0.4f, cy = 0.35f, r = 0.7f)
            val chips = buildString {
                val p = intArrayOf(22, 36, 36, 32, 42, 44, 28, 48, 44, 30)
                var i = 0
                while (i < p.size) {
                    append("M${p[i]} ${p[i + 1]} q3 -3 5 0 q-1 4 -5 0z "); i += 2
                }
            }
            reg("food-cookie", FOOD_W, 64f, 64f) {
                ellipse(32f, 58f, 22f, 3.5f, c("#2a1608"), alpha = 0.2f)
                circle(32f, 34f, 24f, cf)
                rect(14f, 16f, 36f, 9f, c("#f5f7fb"), rx = 3f, stroke = c("#c9d1de"))
                ci(19f, 20.5f, 1.6f, "#ff6b6b")
                ci(24f, 20.5f, 1.6f, "#ffd43b")
                ci(29f, 20.5f, 1.6f, "#69db7c")
                fp(chips, "#4a2a17")
            }
        }
        run {
            val b = lin(s(0f, "#ffffff"), s(1f, "#c8d0dc"), x2 = 1f, y2 = 0f)
            val px = arrayOf(
                "#ff6b6b" to (16f to 22f), "#ffd43b" to (22f to 18f), "#69db7c" to (28f to 20f),
                "#4dabf7" to (34f to 16f), "#9775fa" to (40f to 20f), "#ff8fd0" to (44f to 24f),
                "#4dabf7" to (20f to 26f), "#ff6b6b" to (30f to 25f), "#ffd43b" to (38f to 26f),
            ).map { Triple(c(it.first), it.second.first, it.second.second) }
            val edge = c("#fff", 0.4f)
            reg("food-pixels", FOOD_W, 64f, 64f) {
                ellipse(32f, 58f, 22f, 3.5f, c("#2a1608"), alpha = 0.2f)
                for ((f, x, y) in px) rect(x, y, 6f, 6f, f, stroke = edge)
                path("M8 30 h48 q-2 22 -24 24 q-22 -2 -24 -24z", b)
                st("M8 30 h48", "#9aa6b8", 2f)
                st("M14 36 q4 10 12 14", "#fff", 2.5f, round = true)
            }
        }
        run {
            val bag = lin(s(0f, "#7bd3ff"), s(1f, "#2c86c9"), x2 = 1f, y2 = 0f)
            val ring = c("#9fd8f5")
            reg("food-bubble", FOOD_W, 64f, 64f) {
                ellipse(32f, 59f, 20f, 3f, c("#2a1608"), alpha = 0.2f)
                path("M14 10 h36 l-2 6 4 40 h-40 l4 -40z", bag)
                st("M14 10 l2 3 2 -3 2 3 2 -3 2 3 2 -3 2 3 2 -3 2 3 2 -3 2 3 2 -3 2 3 2 -3 2 3 2 -3", "#bfe9ff", 1f)
                text(32f, 36f, "POP!", 8f, Color.White)
                for (k in 0 until 4) {
                    val x = BUB_X[k]
                    val y = BUB_Y[k]
                    r(x - 5f, y, 10f, 10f, "#e8f7ff", rx = 2f, op = 0.85f)
                    circle(x, y + 5f, 3f, c("#fff"), stroke = ring)
                }
                st("M20 20 v28", "#fff", 3f, a = 0.4f, round = true)
            }
        }
        run {
            val bread = lin(s(0f, "#f6c77f"), s(1f, "#c9873e"))
            val metal = lin(s(0f, "#f2f4f8"), s(1f, "#9aa3b2"))
            reg("food-usb", FOOD_W, 64f, 64f) {
                ellipse(30f, 58f, 24f, 3.5f, c("#2a1608"), alpha = 0.2f)
                path("M40 26 h16 v12 h-16z", metal)
                r(45f, 29f, 3f, 3f, "#5b6475")
                r(50f, 29f, 3f, 3f, "#5b6475")
                path("M6 40 q0 -6 6 -6 h30 q6 0 6 6 v4 h-42z", bread)
                fp("M6 36 h42 l-2 4 h-38z", "#7cc65a")
                r(8f, 30f, 38f, 5f, "#2d6bd6", rx = 2f)
                fp("M8 30 h38 v-2 h-38z", "#ff6b5a")
                path("M6 30 q0 -12 20 -12 q22 0 22 12z", bread)
                st("M14 24 q8 -4 16 -2", "#fff", 2f, a = 0.6f, round = true)
                ellipse(20f, 22f, 1.2f, 0.7f, c("#fff3d6"))
                ellipse(30f, 21f, 1.2f, 0.7f, c("#fff3d6"))
                ellipse(38f, 23f, 1.2f, 0.7f, c("#fff3d6"))
            }
        }
        run {
            val b = lin(s(0f, "#ffd8a8"), s(1f, "#e8945a"), x2 = 1f, y2 = 0f)
            val tacks = arrayOf(
                Triple("#ff6b6b", 20, 27), Triple("#4dabf7", 30, 25), Triple("#ffd43b", 40, 28),
                Triple("#69db7c", 26, 31), Triple("#ff6b6b", 36, 32), Triple("#9775fa", 44, 30),
            )
            val pins = tacks.map { "M${it.second} ${it.third} l2 -6" }
            val heads = tacks.map { c(it.first) }
            reg("food-tacks", FOOD_W, 64f, 64f) {
                ellipse(32f, 58f, 22f, 3.5f, c("#2a1608"), alpha = 0.2f)
                ellipse(32f, 30f, 24f, 7f, c("#fbfbfb"))
                for (i in tacks.indices) {
                    val x = tacks[i].second.toFloat()
                    val y = tacks[i].third.toFloat()
                    st(pins[i], "#9aa3b2", 1.2f)
                    circle(x, y, 3f, heads[i])
                    ci(x - 1f, y - 1f, 1f, "#fff", 0.7f)
                }
                path("M8 30 q2 24 24 24 q22 0 24 -24 q-10 7 -24 7 q-14 0 -24 -7z", b)
                st("M14 38 q4 10 12 12", "#fff", 2.5f, a = 0.6f, round = true)
            }
        }
        run {
            val p = rad(s(0f, "#ffffff"), s(1f, "#d6dbe4"), cx = 0.45f, cy = 0.4f, r = 0.6f)
            reg("food-cables", FOOD_W, 64f, 64f) {
                ellipse(32f, 57f, 26f, 4f, c("#2a1608"), alpha = 0.2f)
                radEllipse(32f, 44f, 28f, 10f, p)
                st("M14 42 q6 -16 14 -4 q6 10 12 -6 q6 -12 10 6", "#ffd43b", 2.6f, round = true)
                st("M16 46 q10 -20 18 -2 q4 8 12 -8", "#ff6b6b", 2.6f, round = true)
                st("M20 40 q8 -12 14 2 q6 10 12 -4", "#4dabf7", 2.6f, round = true)
                st("M24 44 q4 -14 10 -6 q6 6 8 -4", "#f1f3f5", 2.6f, round = true)
                g(deg = 20f, px = 50f, py = 25f) { r(46f, 20f, 8f, 10f, "#495057", rx = 2f) }
                st("M47 18 l1 -4 M51 19 l1 -4", "#ced4da", 1.6f)
                ci(26f, 36f, 4f, "#d6452f")
                ci(25f, 35f, 1.3f, "#fff", 0.6f)
            }
        }
        run {
            val drink = lin(s(0f, "#c6f26b"), s(1f, "#4caf50"))
            val bat = lin(s(0f, "#3b3b3b"), s(0.5f, "#6b6b6b"), s(1f, "#2b2b2b"), x2 = 1f, y2 = 0f)
            val glass = lin(s(0f, "#fff", 0.5f), s(0.3f, "#fff", 0.1f), s(1f, "#fff", 0.25f), x2 = 1f, y2 = 0f)
            reg("food-battery", FOOD_W, 64f, 64f) {
                ellipse(32f, 59f, 16f, 3f, c("#2a1608"), alpha = 0.2f)
                g(deg = 14f, px = 40f, py = 20f) {
                    rect(36f, 2f, 9f, 30f, bat, rx = 2f)
                    r(36f, 2f, 9f, 10f, "#e6b422", rx = 2f)
                    r(39f, 0f, 3f, 3f, "#bbb")
                }
                path("M16 18 h32 l-4 40 h-24z", drink)
                st("M16 18 h32", "#e8ffd0", 3f)
                st("M27 30 l-3 8 h6 l-3 8", "#fff8b0", 2f, join = StrokeJoin.Round)
                path("M14 14 h36 l-5 46 h-26z", glass, stroke = c("#fff", 0.6f))
                ci(48f, 16f, 5f, "#ffd43b")
                ci(48f, 16f, 2.5f, "#fff3a8")
            }
        }
        run {
            val bun = lin(s(0f, "#f6c77f"), s(1f, "#c27a32"))
            val glue = lin(s(0f, "#ffffff", 0.95f), s(1f, "#d8e6f2", 0.95f))
            reg("food-gluedog", FOOD_W, 64f, 64f) {
                ellipse(32f, 56f, 28f, 4f, c("#2a1608"), alpha = 0.2f)
                path("M4 36 q0 -10 10 -10 h36 q10 0 10 10 q0 14 -28 14 q-28 0 -28 -14z", bun)
                rect(2f, 28f, 60f, 9f, glue, rx = 4.5f, stroke = c("#c3d2e0"))
                r(6f, 30f, 40f, 2.5f, "#fff", rx = 1.2f)
                fp("M20 37 q1 6 0 9 q-2 2 -2 0 q-1 -4 2 -9 M38 37 q1 4 0 6 q-2 1 -2 0 q0 -3 2 -6", "#f4f8fc")
                st("M8 34 q20 -6 48 0", "#ffd43b", 2f, dash = D43)
                st("M10 40 q10 4 22 4", "#fff", 2f, a = 0.4f, round = true)
            }
        }
        run {
            val w = lin(s(0f, "#ffe08a"), s(1f, "#d99a2b"), x2 = 1f, y2 = 1f)
            val grid = buildString { for (v in intArrayOf(16, 24, 32, 40, 48)) append("M8 $v H56 M$v 8 V56 ") }
            reg("food-floppy", FOOD_W, 64f, 64f) {
                ellipse(32f, 58f, 24f, 3.5f, c("#2a1608"), alpha = 0.2f)
                path("M8 8 h42 l6 6 v42 h-48z", w, stroke = c("#b07a1c"), sw = 1.2f)
                st(grid, "#b07a1c", 1.2f, a = 0.5f)
                rect(18f, 8f, 24f, 14f, c("#cfd6e0"), stroke = c("#8a95a6"))
                r(32f, 10f, 6f, 10f, "#6b7686")
                r(14f, 32f, 36f, 20f, "#fff7e0", rx = 2f)
                st("M18 38 h28 M18 44 h20", "#e2674f", 1.6f)
                st("M42 24 q6 0 6 6", "#fff", 2f, a = 0.7f)
                ellipse(40f, 30f, 4f, 2.5f, c("#fffbe8"), alpha = 0.8f)
            }
        }
    }

    private val BUB_X = floatArrayOf(20f, 28f, 36f, 44f)
    private val BUB_Y = floatArrayOf(6f, 4f, 7f, 5f)

    private class Shot(val x: Float, val y: Float, val r: Float, val c1: String, val c2: String) {
        val d = "M${n0(x + 3)} ${n0(y + 25)} L${n0(x + 11)} ${n0(y + 14)} L${n0(x + 17)} ${n0(y + 21)} L${n0(x + 21)} ${n0(y + 16)} L${n0(x + 25)} ${n0(y + 22)} V${n0(y + 25)}z"
    }

    /* ============================== helpers ============================== */

    private var drawAlpha = 1f

    private val D34 = floatArrayOf(3f, 4f)
    private val D33 = floatArrayOf(3f, 3f)
    private val D46 = floatArrayOf(4f, 6f)
    private val D13 = floatArrayOf(1f, 3f)
    private val D56 = floatArrayOf(5f, 6f)
    private val D43 = floatArrayOf(4f, 3f)

    private fun s(at: Float, hex: String, a: Float = 1f) = stop(at, hex, a)

    private fun gloss() = lin(s(0f, "#fff", 0.55f), s(0.5f, "#fff", 0.08f), s(1f, "#fff", 0f))

    private fun shadowFill(o: Float) = rad(s(0f, "#2a1608", o), s(0.6f, "#2a1608", o * 0.4f), s(1f, "#2a1608", 0f))

    /** A radial gradient on an ellipse, squashed like SVG's objectBoundingBox does. */
    private fun Svg.radEllipse(cx: Float, cy: Float, rx: Float, ry: Float, f: Fill) {
        if (rx <= 0f || ry <= 0f) return
        g(px = cx, py = cy, sx = 1f, sy = ry / rx) { circle(cx, cy, rx, f) }
    }

    /** The soft contact shadow on the floor. */
    private fun Svg.shadow(cx: Float, cy: Float, rx: Float, ry: Float, f: Fill) = radEllipse(cx, cy, rx, ry, f)

    /** A stroked (unfilled) path; [a] = strokeOpacity x opacity. */
    private fun Svg.st(
        d: String, hex: String, sw: Float, a: Float = 1f, round: Boolean = false,
        dash: FloatArray? = null, join: StrokeJoin = StrokeJoin.Miter,
    ) = path(d, stroke = c(hex, a), sw = sw, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = join, dash = dash)

    private fun Svg.fp(d: String, hex: String, op: Float = 1f) = path(d, c(hex), alpha = op)

    private fun Svg.r(x: Float, y: Float, w: Float, h: Float, hex: String, rx: Float = 0f, op: Float = 1f) =
        rect(x, y, w, h, c(hex), rx = rx, alpha = op)

    private fun Svg.ci(cx: Float, cy: Float, r: Float, hex: String, op: Float = 1f) = circle(cx, cy, r, c(hex), alpha = op)

    /** hsLava: translateY(0 -> -38) scale(1 -> .85, 1.15) about the view box origin, alternate. */
    private fun Svg.lava(cx: Float, cy: Float, rx: Float, ry: Float, dur: Float, delay: Float, f: Fill) {
        val p = alt(t, dur, delay)
        val sx = 1f - 0.15f * p
        val sy = 1f + 0.15f * p
        ellipse(cx * sx, cy * sy - 38f * p, rx * sx, ry * sy, f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Text with letter spacing (in viewBox units), squeezed to [maxW] if it would overflow. */
    private fun Svg.label(
        x: Float, y: Float, str: String, size: Float, hex: String, bold: Boolean = true,
        spacing: Float = 0f, maxW: Float = 0f, stroke: Float = 0f, a: Float = 1f,
    ) {
        val p = textPaint
        p.reset()
        p.isAntiAlias = true
        p.textSize = size
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textAlign = Paint.Align.CENTER
        p.letterSpacing = if (size > 0f) spacing / size else 0f
        val cc = col(hex)
        p.color = cc.copy(alpha = (cc.alpha * a * drawAlpha).coerceIn(0f, 1f)).toArgb()
        if (stroke > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.strokeJoin = Paint.Join.ROUND
            p.strokeCap = Paint.Cap.ROUND
        }
        if (maxW > 0f) {
            val w = p.measureText(str)
            if (w > maxW) p.textScaleX = maxW / w
        }
        ds.drawContext.canvas.nativeCanvas.drawText(str, x, y, p)
    }

    private fun Svg.neonText(x: Float, y: Float, str: String, size: Float, hex: String, sw: Float, spacing: Float, a: Float) {
        label(x, y, str, size, hex, spacing = spacing, maxW = 150f, stroke = sw * 4.5f, a = 0.12f * a)
        label(x, y, str, size, hex, spacing = spacing, maxW = 150f, stroke = sw * 2.4f, a = 0.3f * a)
        label(x, y, str, size, hex, spacing = spacing, maxW = 150f, stroke = sw, a = a)
    }

    /* the clock reads the real time; the zone offset is looked up once a minute */
    private var tzOffset = 0L
    private var tzAt = Long.MIN_VALUE

    private fun localSeconds(): Double {
        val now = System.currentTimeMillis()
        if (tzAt == Long.MIN_VALUE || abs(now - tzAt) > 60_000L) {
            tzOffset = try {
                TimeZone.getDefault().getOffset(now).toLong()
            } catch (_: Throwable) {
                0L
            }
            tzAt = now
        }
        val ms = Math.floorMod(now + tzOffset, 43_200_000L)
        return ms / 1000.0
    }

    /** hsNeon: dips to .35 at 20% and 63% of 5s. */
    private fun neon(t: Float): Float {
        val p = frac(t / 5f) * 100f
        return when {
            p in 18f..20f -> 1f - 0.65f * (p - 18f) / 2f
            p in 20f..22f -> 0.35f + 0.65f * (p - 20f) / 2f
            p in 62f..63f -> 1f - 0.65f * (p - 62f)
            p in 63f..64f -> 0.35f + 0.65f * (p - 63f)
            else -> 1f
        }
    }

    // last, so every helper value above is set before the drawings are built
    init {
        beds()
        lights()
        floorThings()
        rugs()
        wallThings()
        teen()
        bedroom()
        kitchen()
        foods()
    }
}

/* ------------------------------ shared bits ------------------------------ */

internal fun frac(x: Float): Float = x - floor(x)

internal fun ease(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 0 -> 1 -> 0 over one period (a triangle wave). */
internal fun tri(x: Float): Float {
    val p = frac(x) * 2f
    return if (p > 1f) 2f - p else p
}

/** CSS `infinite alternate` with ease-in-out: 0 -> 1 over [dur], back over [dur]. [delay] like animation-delay. */
internal fun alt(t: Float, dur: Float, delay: Float = 0f): Float = ease(tri((t - delay) / (2f * dur)))

/** hsTwinkle: opacity .55 <-> 1 every 1.8s, alternate. */
internal fun twinkle(t: Float, delay: Float = 0f): Float = 0.55f + 0.45f * alt(t, 1.8f, delay)

/** CSS filter: hue-rotate([deg]). */
internal fun hue(c: Color, deg: Float): Color {
    val rd = deg * (PI.toFloat() / 180f)
    val a = cos(rd)
    val b = sin(rd)
    val r = c.red
    val g = c.green
    val bl = c.blue
    val nr = (0.213f + a * 0.787f - b * 0.213f) * r + (0.715f - a * 0.715f - b * 0.715f) * g + (0.072f - a * 0.072f + b * 0.928f) * bl
    val ng = (0.213f - a * 0.213f + b * 0.143f) * r + (0.715f + a * 0.285f + b * 0.140f) * g + (0.072f - a * 0.072f - b * 0.283f) * bl
    val nb = (0.213f - a * 0.213f - b * 0.787f) * r + (0.715f - a * 0.715f + b * 0.715f) * g + (0.072f + a * 0.928f + b * 0.072f) * bl
    return Color(nr.coerceIn(0f, 1f), ng.coerceIn(0f, 1f), nb.coerceIn(0f, 1f), c.alpha)
}

/** A float for a path string (no scientific notation). */
internal fun nf(v: Float): String = if (abs(v) < 0.001f) "0" else v.toString()

/** Like [nf], but whole numbers without the ".0". */
internal fun n0(v: Float): String = if (v == floor(v) && abs(v) < 1e6f) v.toInt().toString() else nf(v)

/** A circle as a path string. */
internal fun circleD(cx: Float, cy: Float, r: Float): String = ellipseD(cx, cy, r, r)

/** An ellipse as a path string (two arcs). */
internal fun ellipseD(cx: Float, cy: Float, rx: Float, ry: Float): String =
    "M${nf(cx - rx)} ${nf(cy)} a${nf(rx)} ${nf(ry)} 0 1 0 ${nf(2 * rx)} 0 a${nf(rx)} ${nf(ry)} 0 1 0 ${nf(-2 * rx)} 0z"

/** A rounded rect as a path string. */
internal fun rrD(x: Float, y: Float, w: Float, h: Float, r0: Float): String {
    val r = minOf(r0, w / 2f, h / 2f)
    return "M${nf(x + r)} ${nf(y)} H${nf(x + w - r)} A${nf(r)} ${nf(r)} 0 0 1 ${nf(x + w)} ${nf(y + r)} V${nf(y + h - r)} " +
        "A${nf(r)} ${nf(r)} 0 0 1 ${nf(x + w - r)} ${nf(y + h)} H${nf(x + r)} A${nf(r)} ${nf(r)} 0 0 1 ${nf(x)} ${nf(y + h - r)} " +
        "V${nf(y + r)} A${nf(r)} ${nf(r)} 0 0 1 ${nf(x + r)} ${nf(y)} Z"
}
