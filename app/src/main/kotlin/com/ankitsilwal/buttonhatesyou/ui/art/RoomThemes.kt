package com.ankitsilwal.buttonhatesyou.ui.art

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// The wallpapers and floors of src/house/room.css ([data-wall='wall-...'] and
// [data-floor='floor-...']) as Canvas drawing. Each CSS background layer is one
// shader: repeating-linear-gradients become REPEAT LinearGradients with hard stops,
// sized radial/conic/SVG-tile layers become small cached BitmapShader tiles. The
// floor is drawn on a plane tilted like `.plane { transform: rotateX(64deg) }`
// under `perspective: 40cqw` (a real perspective Matrix), so boards and tiles
// shrink toward the top edge and their lines meet toward a vanishing point.
//
// Everything is built once per (theme, size, unit) and cached; a frame only
// draws the prepared layers.

object RoomThemes {

    private val WALLS = setOf(
        "wall-cozy", "wall-mint", "wall-candy", "wall-beach", "wall-city", "wall-gamer", "wall-space",
        "wall-loft", "wall-studio", "wall-graffiti",
        "wall-kitchen-tile", "wall-kitchen-sage", "wall-kitchen-sun", "wall-kitchen-noir",
        "wall-bedroom-blue", "wall-bedroom-plum", "wall-bedroom-forest", "wall-bedroom-galaxy",
    )
    private val FLOORS = setOf(
        "floor-oak", "floor-carpet", "floor-checker", "floor-bamboo", "floor-marble", "floor-neon",
        "floor-concrete", "floor-walnut", "floor-kitchen-checker", "floor-kitchen-terrazzo",
        "floor-kitchen-gold", "floor-bedroom-rug",
    )

    /** --skirt per wallpaper. */
    private val SKIRT = mapOf(
        "wall-cozy" to "#f7efe2", "wall-mint" to "#ffffff", "wall-candy" to "#fff3f8", "wall-beach" to "#fbf6ea",
        "wall-city" to "#2b3157", "wall-gamer" to "#15151c", "wall-space" to "#3a4256", "wall-loft" to "#2b2e35",
        "wall-studio" to "#10161a", "wall-graffiti" to "#4d5057", "wall-kitchen-tile" to "#cfd5d8",
        "wall-kitchen-sage" to "#e8efe9", "wall-kitchen-sun" to "#fff6dc", "wall-kitchen-noir" to "#15171c",
        "wall-bedroom-blue" to "#1b2347", "wall-bedroom-plum" to "#2d1633", "wall-bedroom-forest" to "#142a20",
        "wall-bedroom-galaxy" to "#140f2e",
    )

    fun hasWall(id: String): Boolean = id in WALLS

    fun hasFloor(id: String): Boolean = id in FLOORS

    /** Wallpaper [id] over [r] (the wall above the floor), with the skirting band at its bottom. */
    fun drawWall(scope: DrawScope, id: String, r: Rect, u: Float, t: Float = 0f) {
        if (!hasWall(id) || !ok(r)) return
        val uu = unit(u, r.width)
        val tt = if (t.isFinite()) t else 0f
        val cv = scope.drawContext.canvas.nativeCanvas
        val save = cv.save()
        try {
            cv.clipRect(r.left, r.top, r.right, r.bottom)
            cv.translate(r.left, r.top)
            val p = prep(K_WALL, id, r.width, r.height, uu)
            drawLayers(cv, p.base, tt)
            if (id == "wall-graffiti") mural(scope, r.width, r.height)
            drawLayers(cv, p.top, tt)
        } catch (_: Throwable) {
        } finally {
            cv.restoreToCount(save)
        }
    }

    /** Floor [id] over [r] (below the wall), in perspective, with the skirting strip and top shadow. */
    fun drawFloor(scope: DrawScope, id: String, r: Rect, u: Float, t: Float = 0f) {
        if (!hasFloor(id) || !ok(r)) return
        val uu = unit(u, r.width)
        val tt = if (t.isFinite()) t else 0f
        val cv = scope.drawContext.canvas.nativeCanvas
        val save = cv.save()
        try {
            cv.clipRect(r.left, r.top, r.right, r.bottom)
            cv.translate(r.left, r.top)
            val p = prep(K_FLOOR, id, r.width, r.height, uu)
            val s2 = cv.save()
            cv.concat(p.matrix)
            drawLayers(cv, p.base, tt)
            cv.restoreToCount(s2)
            drawLayers(cv, p.top, tt)
        } catch (_: Throwable) {
        } finally {
            cv.restoreToCount(save)
        }
    }

    /** A small square swatch of a wall or floor theme for shop tiles. */
    fun drawSwatch(scope: DrawScope, id: String, r: Rect) {
        if (!ok(r)) return
        if (hasWall(id)) {
            drawWall(scope, id, r, r.width / 28f, 0f)
            return
        }
        if (!hasFloor(id)) return
        val cv = scope.drawContext.canvas.nativeCanvas
        val save = cv.save()
        try {
            cv.clipRect(r.left, r.top, r.right, r.bottom)
            cv.translate(r.left, r.top)
            val p = prep(K_SWATCH, id, r.width, r.height, r.width / 24f)
            drawLayers(cv, p.base, 0f)
            drawLayers(cv, p.top, 0f)
        } catch (_: Throwable) {
        } finally {
            cv.restoreToCount(save)
        }
    }

    /* ============================== the cache ============================== */

    private const val K_WALL = 0
    private const val K_FLOOR = 1
    private const val K_SWATCH = 2

    private const val A_NONE = 0
    private const val A_RGB = 1 // hsRgb: the gradient slides along (period = its length)
    private const val A_LOW = 2 // hsLow: opacity 1 <-> .7, 2.4s alternate
    private const val A_TW1 = 3 // galaxy stars twinkling
    private const val A_TW2 = 4

    private class Layer(
        val paint: Paint,
        val l: Float, val t: Float, val r: Float, val b: Float,
        val rx: Float = 0f, val anim: Int = A_NONE, val period: Float = 0f,
    ) {
        val baseAlpha = paint.alpha
    }

    private class Prep(val kind: Int, val id: String, val w: Float, val h: Float, val u: Float) {
        val base = ArrayList<Layer>(10)
        val top = ArrayList<Layer>(8)
        val matrix = Matrix()
    }

    private val cache = ArrayList<Prep>()
    private const val CACHE_MAX = 14

    private fun prep(kind: Int, id: String, w: Float, h: Float, u: Float): Prep {
        for (i in cache.indices) {
            val p = cache[i]
            if (p.kind == kind && p.w == w && p.h == h && p.u == u && p.id == id) {
                if (i > 0) {
                    cache.removeAt(i); cache.add(0, p)
                }
                return p
            }
        }
        val p = Prep(kind, id, w, h, u)
        try {
            build(p)
        } catch (_: Throwable) {
            p.base.clear(); p.top.clear()
        }
        cache.add(0, p)
        while (cache.size > CACHE_MAX) cache.removeAt(cache.size - 1)
        return p
    }

    private fun drawLayers(cv: Canvas, ls: List<Layer>, t: Float) {
        for (i in ls.indices) {
            val L = ls[i]
            when (L.anim) {
                A_RGB -> {
                    val off = L.period * frac(t / 6f)
                    val s = cv.save()
                    cv.translate(off, 0f)
                    cv.drawRect(L.l - off, L.t, L.r - off, L.b, L.paint)
                    cv.restoreToCount(s)
                    continue
                }
                A_LOW -> L.paint.alpha = (L.baseAlpha * (1f - 0.3f * alt(t, 2.4f))).roundToInt()
                A_TW1 -> L.paint.alpha = (L.baseAlpha * (0.3f + 0.7f * alt(t, 1.6f))).roundToInt()
                A_TW2 -> L.paint.alpha = (L.baseAlpha * (0.3f + 0.7f * alt(t, 2.3f, 1.1f))).roundToInt()
            }
            if (L.rx > 0f) cv.drawRoundRect(L.l, L.t, L.r, L.b, L.rx, L.rx, L.paint)
            else cv.drawRect(L.l, L.t, L.r, L.b, L.paint)
        }
    }

    private fun build(p: Prep) {
        val w = p.w
        val h = p.h
        val u = p.u
        when (p.kind) {
            K_WALL -> {
                wallBase(p.id, w, h, u, p.base)
                wallTop(p.id, w, h, u, p.top)
            }
            K_FLOOR -> {
                // .plane: left/right -80%, rotateX(64deg) about its top edge, perspective 40cqw
                // from the top centre. A plane point (X from the centre, Y down the plane) lands at
                // k = d / (d - Y sin), screen (cx + X k, Y cos k). Ymax is where it meets the bottom.
                val d = 40f * u
                val cs = cos(Math.toRadians(64.0)).toFloat()
                val sn = sin(Math.toRadians(64.0)).toFloat()
                val yMax = h * d / (cs * d + sn * h)
                val kB = d / (d - sn * yMax)
                val pw = 2.6f * w
                val half = 1.3f * w
                val cx = w / 2f
                val src = floatArrayOf(0f, 0f, pw, 0f, pw, yMax, 0f, yMax)
                val dst = floatArrayOf(cx - half, 0f, cx + half, 0f, cx + half * kB, h, cx - half * kB, h)
                if (!p.matrix.setPolyToPoly(src, 0, dst, 0, 4)) p.matrix.reset()
                floorBase(p.id, pw, yMax, 3.3f * h, u, p.base)
                floorTop(w, h, u, p.top)
            }
            K_SWATCH -> {
                floorBase(p.id, w, h, 3.3f * h, u, p.base)
                p.top.add(Layer(shaderPaint(radial(w / 2f, 0f, 0.8f * w, 0.9f * h, intArrayOf(rgba(255, 240, 215, 0.3f), rgba(255, 240, 215, 0f)), floatArrayOf(0f, 0.7f))), 0f, 0f, w, h))
            }
        }
    }

    /* ============================== wallpapers ============================== */

    private fun wallBase(id: String, w: Float, h: Float, u: Float, out: MutableList<Layer>) {
        fun full(s: Shader) = out.add(Layer(shaderPaint(s), 0f, 0f, w, h))
        fun solid(hex: String) = out.add(Layer(colorPaint(argb(hex)), 0f, 0f, w, h))
        fun down(a: String, b: String) = full(LinearGradient(0f, 0f, 0f, h, argb(a), argb(b), Shader.TileMode.CLAMP))

        when (id) {
            "wall-cozy" -> {
                down("#f8e9d3", "#efd7b8")
                full(dots(6 * u, 6 * u, 0f, 0f, 0.5f, 0.5f, rgba(196, 140, 90, 0.14f), 0.35f * u, 0.45f * u))
                full(stripesX(6 * u, rgba(255, 255, 255, 0.28f), 0.4f * u))
            }
            "wall-mint" -> {
                solid("#cdebdc")
                full(bandsX(6 * u, argb("#cdebdc"), argb("#bde2cf")))
            }
            "wall-candy" -> {
                down("#ffc9de", "#f7a9c8")
                full(dots(5 * u, 5 * u, 2.5f * u, 2.5f * u, 0.5f, 0.5f, rgba(255, 255, 255, 0.6f), 0.45f * u, 0.55f * u))
                full(dots(5 * u, 5 * u, 0f, 0f, 0.5f, 0.5f, argb("#ffffff"), 0.7f * u, 0.8f * u))
            }
            "wall-beach" -> {
                down("#d7f0f2", "#b6dfe4")
                full(stripesY(4.6f * u, rgba(40, 90, 110, 0.18f), 0.2f * u, atEnd = true))
            }
            "wall-city" -> {
                down("#2c3561", "#1e2546")
                full(stripesX(9 * u, rgba(255, 255, 255, 0.03f), 0.3f * u))
            }
            "wall-gamer" -> {
                solid("#22222c")
                // conic-gradient(from 45deg, ...) in a 4.4cqw tile: four triangles meeting in the middle
                full(tile(4.4f * u, 4.4f * u, 0f, 0f) { c, tw, th ->
                    c.drawColor(argb("#22222c"))
                    tri(c, tw / 2, th / 2, tw, 0f, tw, th, argb("#2f2f3b"))
                    tri(c, tw / 2, th / 2, tw, th, 0f, th, argb("#22222c"))
                    tri(c, tw / 2, th / 2, 0f, th, 0f, 0f, argb("#2a2a35"))
                    tri(c, tw / 2, th / 2, 0f, 0f, tw, 0f, argb("#1b1b23"))
                })
            }
            "wall-space" -> {
                down("#4b556d", "#333b50")
                full(stripesY(10 * u, rgba(0, 0, 0, 0.35f), 0.25f * u))
                full(stripesX(12 * u, rgba(0, 0, 0, 0.35f), 0.25f * u))
                full(dots(12 * u, 10 * u, u, u, 0.5f, 0.5f, argb("#9aa6bf"), 0.25f * u, 0.35f * u))
            }
            "wall-loft" -> {
                solid("#8a4a37")
                full(tile(12 * u, 4.8f * u, 0f, 0f) { c, tw, th ->
                    c.scale(tw / 40f, th / 16f)
                    val pt = Paint(Paint.ANTI_ALIAS_FLAG)
                    fun rr(x: Float, y: Float, rw: Float, rh: Float, col: Int) {
                        pt.color = col; c.drawRect(x, y, x + rw, y + rh, pt)
                    }
                    rr(0f, 0f, 40f, 16f, argb("#6e3a2c"))
                    rr(1f, 1f, 18f, 6f, argb("#a65a41"))
                    rr(21f, 1f, 18f, 6f, argb("#984f38"))
                    rr(-9f, 9f, 18f, 6f, argb("#b0654a"))
                    rr(11f, 9f, 18f, 6f, argb("#9a5139"))
                    rr(31f, 9f, 18f, 6f, argb("#a65a41"))
                    rr(1f, 1f, 18f, 1.5f, rgba(255, 255, 255, 0.08f))
                    rr(21f, 1f, 18f, 1.5f, rgba(255, 255, 255, 0.08f))
                })
                full(radial(w * 0.5f, h * 0.3f, 0.7f * w, 0.9f * h, intArrayOf(rgba(255, 220, 190, 0.18f), rgba(255, 220, 190, 0f)), floatArrayOf(0f, 0.7f)))
                // the steel beam
                out.add(Layer(shaderPaint(LinearGradient(0f, 7.4f * u, 0f, 10.4f * u,
                    intArrayOf(argb("#2c3037"), argb("#8d939d"), argb("#3a3f47"), argb("#1c1f24")),
                    floatArrayOf(0f, 0.8f / 3f, 2.2f / 3f, 1f), Shader.TileMode.CLAMP)), 0f, 7.4f * u, w, 10.4f * u))
                loftWindow(w, h, u, out)
            }
            "wall-studio" -> {
                down("#1b3a40", "#101a1e")
                full(tile(7 * u, 7 * u, 0f, 0f) { c, tw, th ->
                    c.scale(tw / 24f, th / 24f)
                    c.drawColor(argb("#161e22"))
                    val a = argb("#323f45")
                    val b = argb("#1d272c")
                    tri(c, 1f, 1f, 11f, 1f, 11f, 11f, a)
                    tri(c, 1f, 1f, 1f, 11f, 11f, 11f, b)
                    tri(c, 13f, 1f, 23f, 1f, 23f, 11f, b)
                    tri(c, 23f, 1f, 23f, 11f, 13f, 11f, a)
                    tri(c, 1f, 13f, 11f, 13f, 11f, 23f, b)
                    tri(c, 11f, 13f, 11f, 23f, 1f, 23f, a)
                    tri(c, 13f, 13f, 23f, 13f, 23f, 23f, a)
                    tri(c, 13f, 13f, 13f, 23f, 23f, 23f, b)
                })
                full(radial(w * 0.5f, h * 0.2f, 0.6f * w, 0.8f * h, intArrayOf(rgba(90, 200, 200, 0.16f), rgba(90, 200, 200, 0f)), floatArrayOf(0f, 0.7f)))
                // ::before, the red "on air" light
                val x1 = w - 0.05f * w
                val x0 = x1 - 7 * u
                val y0 = 0.07f * h
                val y1 = y0 + 2.4f * u
                val g = 2.6f * u
                out.add(Layer(shaderPaint(radial((x0 + x1) / 2, (y0 + y1) / 2, 3.5f * u + g, 1.2f * u + g,
                    intArrayOf(rgba(255, 59, 77, 0.55f), rgba(255, 59, 77, 0.4f), rgba(255, 59, 77, 0f)), floatArrayOf(0f, 0.45f, 1f))),
                    x0 - g, y0 - g, x1 + g, y1 + g, anim = A_LOW))
                out.add(Layer(colorPaint(argb("#ff3b4d")), x0, y0, x1, y1, rx = 0.5f * u, anim = A_LOW))
                out.add(Layer(colorPaint(rgba(255, 255, 255, 0.4f)), x0 + 0.4f * u, y0, x1 - 0.4f * u, y0 + 0.2f * u, anim = A_LOW))
            }
            "wall-graffiti" -> {
                down("#a9acb2", "#8a8d94")
                full(dots(2.9f * u, 4.3f * u, 0f, 0f, 0.7f, 0.6f, rgba(255, 255, 255, 0.06f), 0.3f * u, 0.5f * u))
                full(dots(3.7f * u, 3.1f * u, 0f, 0f, 0.2f, 0.3f, rgba(0, 0, 0, 0.07f), 0.4f * u, 0.6f * u))
            }
            "wall-kitchen-tile" -> {
                solid("#eef0ef")
                full(kitchenTile(u, "#f7f8f6", "#ffffff", "#fdfdfc", 1, 0))
            }
            "wall-kitchen-sage" -> {
                solid("#aecbb6")
                full(kitchenTile(u, "#e3ece5", "#aecbb6", "#a6c5af", 2, rgba(255, 255, 255, 0.4f)))
            }
            "wall-kitchen-sun" -> {
                solid("#fbe8a6")
                full(kitchenTile(u, "#f6dfa0", "#fbe8a6", "#f9e199", 0, 0))
            }
            "wall-kitchen-noir" -> {
                solid("#1c1f25")
                full(kitchenTile(u, "#0e1014", "#262a31", "#202329", 2, rgba(255, 255, 255, 0.12f)))
                full(radial(w * 0.5f, h * 0.2f, 0.7f * w, 0.9f * h, intArrayOf(rgba(255, 255, 255, 0.07f), rgba(255, 255, 255, 0f)), floatArrayOf(0f, 0.7f)))
            }
            "wall-bedroom-blue" -> {
                down("#2c3a73", "#1d2750")
                full(radial(w * 0.5f, 0f, 0.7f * w, 0.8f * h, intArrayOf(rgba(120, 140, 255, 0.18f), rgba(120, 140, 255, 0f)), floatArrayOf(0f, 0.7f)))
                full(dots(7 * u, 8 * u, 7 * u, 6 * u, 0.5f, 0.5f, rgba(255, 255, 255, 0.35f), 0.1f * u, 0.2f * u))
                full(dots(11 * u, 9 * u, 2 * u, 3 * u, 0.5f, 0.5f, rgba(255, 255, 255, 0.55f), 0.14f * u, 0.24f * u))
            }
            "wall-bedroom-plum" -> {
                down("#6b3a72", "#44244b")
                full(stripesX(6 * u, rgba(0, 0, 0, 0.12f), 0.3f * u))
                full(dots(6 * u, 6 * u, 0f, 0f, 0.5f, 0.5f, rgba(255, 255, 255, 0.07f), 1f * u, 1.1f * u))
            }
            "wall-bedroom-forest" -> {
                down("#38694f", "#234536")
                full(bandsX(5 * u, rgba(255, 255, 255, 0.05f), rgba(0, 0, 0, 0.1f)))
            }
            "wall-bedroom-galaxy" -> {
                down("#1c1245", "#0b0a24")
                full(radial(w * 0.78f, h * 0.55f, 0.55f * w, 0.6f * h, intArrayOf(rgba(80, 200, 255, 0.35f), rgba(80, 200, 255, 0f)), floatArrayOf(0f, 0.7f)))
                full(radial(w * 0.25f, h * 0.35f, 0.5f * w, 0.6f * h, intArrayOf(rgba(255, 90, 200, 0.4f), rgba(255, 90, 200, 0f)), floatArrayOf(0f, 0.7f)))
                out.add(Layer(shaderPaint(dots(9 * u, 7 * u, 4 * u, 5 * u, 0.5f, 0.5f, rgba(255, 255, 255, 0.7f), 0.1f * u, 0.2f * u)), 0f, 0f, w, h, anim = A_TW2))
                out.add(Layer(shaderPaint(dots(7 * u, 6 * u, u, 2 * u, 0.5f, 0.5f, argb("#ffffff"), 0.14f * u, 0.24f * u)), 0f, 0f, w, h, anim = A_TW1))
            }
        }
    }

    /** `.hs-wall::after`: side shading, a lit top, the skirting board. Plus the gamer RGB strip. */
    private fun wallTop(id: String, w: Float, h: Float, u: Float, out: MutableList<Layer>) {
        val side = rgba(40, 20, 10, 0.16f)
        val side0 = rgba(40, 20, 10, 0f)
        out.add(Layer(shaderPaint(LinearGradient(0f, 0f, w, 0f, intArrayOf(side, side0, side0, side), floatArrayOf(0f, 0.18f, 0.82f, 1f), Shader.TileMode.CLAMP)), 0f, 0f, w, h))
        out.add(Layer(shaderPaint(radial(w * 0.5f, -0.1f * h, 0.9f * w, 0.6f * h, intArrayOf(rgba(255, 255, 255, 0.3f), rgba(255, 255, 255, 0f)), floatArrayOf(0f, 0.7f))), 0f, 0f, w, h))
        val sk = h - 3.2f * u
        out.add(Layer(colorPaint(argb(SKIRT[id] ?: "#f4ebdc")), 0f, sk, w, h - 0.5f * u))
        out.add(Layer(colorPaint(rgba(0, 0, 0, 0.18f)), 0f, h - 0.5f * u, w, h))
        if (id == "wall-gamer") {
            // ::before: a 0.6cqw RGB strip just above the skirting, glowing, sliding (hsRgb reverse)
            val y1 = h - 3.2f * u
            val y0 = y1 - 0.6f * u
            val gl = 2.4f * u
            val c = rgba(61, 196, 255, 0.5f)
            val c0 = rgba(61, 196, 255, 0f)
            out.add(Layer(shaderPaint(LinearGradient(0f, y0 - gl, 0f, y1 + gl, intArrayOf(c0, c, c, c0),
                floatArrayOf(0f, gl / (2 * gl + 0.6f * u), (gl + 0.6f * u) / (2 * gl + 0.6f * u), 1f), Shader.TileMode.CLAMP)),
                0f, y0 - gl, w, y1 + gl))
            val period = 2f * w
            val cols = intArrayOf(argb("#3dc4ff"), argb("#b13dff"), argb("#ff3d6e"), argb("#ffb13d"), argb("#3dff8b"), argb("#3dc4ff"))
            out.add(Layer(shaderPaint(LinearGradient(0f, 0f, period, 0f, cols, null, Shader.TileMode.REPEAT)), 0f, y0, w, y1, anim = A_RGB, period = period))
        }
    }

    /** wall-loft::before: a big steel-framed window with a sunset behind it. */
    private fun loftWindow(w: Float, h: Float, u: Float, out: MutableList<Layer>) {
        val x0 = 0.38f * w
        val y0 = 0.14f * h
        val x1 = x0 + 0.24f * w
        val y1 = y0 + 0.56f * h
        val bw = 0.9f * u
        // box-shadow 0 .6cqw 1.6cqw rgba(0,0,0,.45), roughly
        for (k in 3 downTo 1) {
            val e = k * 0.4f * u
            out.add(Layer(colorPaint(rgba(0, 0, 0, 0.13f + 0.05f * (3 - k))), x0 - e, y0 + 0.6f * u - e, x1 + e, y1 + 0.6f * u + e, rx = e))
        }
        out.add(Layer(shaderPaint(LinearGradient(0f, y0, 0f, y1, intArrayOf(argb("#5b5a9a"), argb("#c9708a"), argb("#f2a76b")), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)), x0, y0, x1, y1))
        val px0 = x0 + bw
        val py0 = y0 + bw
        val px1 = x1 - bw
        val py1 = y1 - bw
        val bar = argb("#1f2328")
        val mx = (px0 + px1) / 2f
        out.add(Layer(colorPaint(bar), mx - 0.25f * u, py0, mx + 0.25f * u, py1))
        for (f in floatArrayOf(0.33f, 0.66f)) {
            val y = py1 - f * (py1 - py0)
            out.add(Layer(colorPaint(bar), px0, y - 0.25f * u, px1, y + 0.25f * u))
        }
        val inner = strokePaint(argb("#4a505a"), 0.3f * u)
        out.add(Layer(inner, px0 + 0.15f * u, py0 + 0.15f * u, px1 - 0.15f * u, py1 - 0.15f * u))
        out.add(Layer(strokePaint(bar, bw), x0 + bw / 2, y0 + bw / 2, x1 - bw / 2, y1 - bw / 2))
    }

    /** wall-graffiti::before: the mural (its SVG, stretched over inset 4% 4% 8% 4%). */
    private fun mural(scope: DrawScope, w: Float, h: Float) {
        scope.drawSvg(400f, 190f, Rect(0.04f * w, 0.04f * h, 0.96f * w, 0.92f * h)) {
            circle(110f, 90f, 62f, c("#ff6aa8"))
            circle(110f, 90f, 42f, c("#ffd36b"))
            circle(110f, 90f, 20f, c("#3fb9c9"))
            path("M0 150 Q70 110 140 150 T290 140 T400 120 V190 H0z", c("#2d3a8c"), alpha = 0.9f)
            path("M0 168 Q80 138 160 168 T320 160 T400 150 V190 H0z", c("#40c9a2"))
            path("M230 30 l40 -20 40 20 -40 60z", c("#9775fa"))
            path("M270 10 l0 80", stroke = c("#fff", 0.5f), sw = 3f)
            g(deg = 12f, px = 348f, py = 68f) {
                rect(320f, 40f, 56f, 56f, c("#ffd36b"), rx = 8f)
                path("M328 74 l18 -22 18 22z", c("#e2674f"))
            }
            path("M56 40 q20 -26 50 -20", stroke = c("#111", 0.55f), sw = 5f, cap = StrokeCap.Round)
            path("M200 120 q20 12 40 0", stroke = c("#111", 0.55f), sw = 5f, cap = StrokeCap.Round)
            rect(90f, 150f, 3f, 26f, c("#ff6aa8"), rx = 1.5f)
            rect(250f, 140f, 3f, 20f, c("#ff6aa8"), rx = 1.5f)
        }
    }

    /** The 80 x 76 tile SVG of the kitchen walls (two rows of offset tiles), 8cqw x 7.6cqw. */
    private fun kitchenTile(u: Float, base: String, c1: String, c2: String, extra: Int, extraColor: Int): Shader =
        tile(8 * u, 7.6f * u, 0f, 0f) { c, tw, th ->
            c.scale(tw / 80f, th / 76f)
            val pt = Paint(Paint.ANTI_ALIAS_FLAG)
            pt.color = argb(base)
            c.drawRect(0f, 0f, 80f, 76f, pt)
            fun rr(x: Float, y: Float, col: String) {
                pt.color = argb(col); c.drawRoundRect(x, y, x + 38f, y + 36f, 2f, 2f, pt)
            }
            rr(1f, 1f, c1)
            rr(41f, 1f, c2)
            rr(-19f, 39f, c2)
            rr(21f, 39f, c1)
            rr(61f, 39f, c2)
            pt.style = Paint.Style.STROKE
            pt.strokeWidth = 2f
            when (extra) {
                1 -> {
                    pt.color = argb("#c9ced3")
                    c.drawLine(0f, 0f, 80f, 0f, pt)
                    c.drawLine(0f, 38f, 80f, 38f, pt)
                    c.drawLine(0f, 0f, 0f, 38f, pt)
                    c.drawLine(40f, 38f, 40f, 76f, pt)
                }
                2 -> {
                    pt.color = extraColor
                    c.drawLine(1f, 3f, 39f, 3f, pt)
                    c.drawLine(41f, 3f, 79f, 3f, pt)
                }
            }
        }

    /* ============================== floors ============================== */

    /** The floor's background on a [w] x [h] plane; [gh] is the plane's full CSS height (330%). */
    private fun floorBase(id: String, w: Float, h: Float, gh: Float, u: Float, out: MutableList<Layer>) {
        fun full(s: Shader) = out.add(Layer(shaderPaint(s), 0f, 0f, w, h))
        fun down(a: String, b: String) = full(LinearGradient(0f, 0f, 0f, gh, argb(a), argb(b), Shader.TileMode.CLAMP))

        when (id) {
            "floor-oak" -> planks(out, w, h, u, arrayOf("#cf9862", "#c48b55", "#d7a36d", "#be844f"), 9f, 30f, rgba(80, 40, 15, 0.25f), rgba(80, 40, 15, 0.35f))
            "floor-walnut" -> planks(out, w, h, u, arrayOf("#5a3a28", "#4c2f20", "#63402c", "#543524"), 8f, 26f, rgba(10, 4, 0, 0.4f), rgba(10, 4, 0, 0.55f))
            "floor-carpet" -> {
                down("#5f9e9a", "#4d8a86")
                full(dots(1.4f * u, 1.4f * u, 0f, 0f, 0.5f, 0.5f, rgba(255, 255, 255, 0.12f), 0.2f * u, 0.3f * u))
            }
            "floor-bedroom-rug" -> {
                down("#7b4a86", "#623a6e")
                full(dots(1.4f * u, 1.4f * u, 0f, 0f, 0.5f, 0.5f, rgba(255, 255, 255, 0.1f), 0.2f * u, 0.3f * u))
            }
            "floor-checker" -> full(checker(14 * u, argb("#f4efe6"), argb("#2e2a33")))
            "floor-kitchen-checker" -> full(checker(14 * u, argb("#f8f6f0"), argb("#6aa8b4")))
            "floor-bamboo" -> {
                full(LinearGradient(0f, 0f, w, 0f, argb("#d9cf8f"), argb("#cbbf7a"), Shader.TileMode.CLAMP))
                full(stripesY(48 * u, argb("#4f5a2c"), 0.8f * u))
                full(stripesX(24 * u, argb("#4f5a2c"), 0.8f * u))
                full(stripesX(1.2f * u, rgba(90, 80, 30, 0.3f), 0.2f * u))
            }
            "floor-marble" -> {
                down("#f6f3ee", "#e6e0d8")
                val v35 = rgba(150, 140, 135, 0.35f)
                val v25 = rgba(150, 140, 135, 0.25f)
                val v0 = rgba(150, 140, 135, 0f)
                full(gradTile(20 * u, 20 * u, 115f, intArrayOf(v0, v0, v35, v0, v0, v25, v0, v0), floatArrayOf(0f, 0.4f, 0.41f, 0.43f, 0.7f, 0.71f, 0.72f, 1f), 256))
                val line = rgba(160, 150, 140, 0.5f)
                full(stripesY(20 * u, line, 0.15f * u))
                full(stripesX(20 * u, line, 0.15f * u))
            }
            "floor-neon" -> {
                down("#1c0f33", "#2a1250")
                // drop-shadow(0 0 .6cqw rgba(255,79,216,.7)) around every line
                val gw = rgba(255, 79, 216, 0.38f)
                full(glowLines(8 * u, 0.3f * u, 1.1f * u, gw, vertical = true))
                full(glowLines(8 * u, 0.3f * u, 1.1f * u, gw, vertical = false))
                full(stripesY(8 * u, argb("#4fe3ff"), 0.3f * u))
                full(stripesX(8 * u, argb("#ff4fd8"), 0.3f * u))
            }
            "floor-concrete" -> {
                down("#9a9da4", "#81848c")
                full(dots(2.3f * u, 2.7f * u, 0f, 0f, 0.5f, 0.5f, rgba(0, 0, 0, 0.05f), 0.2f * u, 0.3f * u))
                val s0 = rgba(255, 255, 255, 0f)
                full(gradTile(52 * u, 80 * u, 115f, intArrayOf(s0, s0, rgba(255, 255, 255, 0.22f), s0, s0), floatArrayOf(0f, 0.3f, 0.42f, 0.55f, 1f), 128))
                val line = rgba(40, 42, 48, 0.55f)
                full(stripesY(40 * u, line, 0.25f * u))
                full(stripesX(26 * u, line, 0.25f * u))
            }
            "floor-kitchen-terrazzo" -> {
                down("#f1ebe0", "#e5ddcf")
                full(dots(11 * u, 9 * u, 1 * u, 5 * u, 0.5f, 0.5f, argb("#e8c36a"), 0.35f * u, 0.45f * u))
                full(dots(5 * u, 8 * u, 5 * u, 4 * u, 0.5f, 0.5f, argb("#3b3f4a"), 0.3f * u, 0.4f * u))
                full(dots(9 * u, 7 * u, 3 * u, 2 * u, 0.5f, 0.5f, argb("#6fa5a8"), 0.4f * u, 0.5f * u))
                full(dots(7 * u, 6 * u, 0f, 0f, 0.5f, 0.5f, argb("#d98f7a"), 0.5f * u, 0.6f * u))
            }
            "floor-kitchen-gold" -> {
                down("#f3d27a", "#c99a3c")
                val w45 = rgba(255, 255, 255, 0.45f)
                val w0 = rgba(255, 255, 255, 0f)
                full(gradTile(10 * u, 10 * u, 135f, intArrayOf(w45, w45, w0, w0), floatArrayOf(0f, 0.12f, 0.4f, 1f), 256))
                val line = rgba(90, 60, 10, 0.55f)
                full(stripesY(10 * u, line, 0.3f * u))
                full(stripesX(10 * u, line, 0.3f * u))
            }
        }
    }

    /** Boards: four shades across, a seam every board, a butt joint every [rowLen]. */
    private fun planks(out: MutableList<Layer>, w: Float, h: Float, u: Float, cols: Array<String>, board: Float, rowLen: Float, rowC: Int, seamC: Int) {
        val c = IntArray(8) { argb(cols[it / 2]) }
        val pos = floatArrayOf(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1f)
        out.add(Layer(shaderPaint(LinearGradient(0f, 0f, 4 * board * u, 0f, c, pos, Shader.TileMode.REPEAT)), 0f, 0f, w, h))
        out.add(Layer(shaderPaint(stripesY((rowLen + 0.3f) * u, rowC, 0.3f * u, atEnd = true)), 0f, 0f, w, h))
        out.add(Layer(shaderPaint(stripesX(board * u, seamC, 0.25f * u)), 0f, 0f, w, h))
    }

    /** `.rs-floor::after` and its inset top shadow: skirting strip, a soft sheen. */
    private fun floorTop(w: Float, h: Float, u: Float, out: MutableList<Layer>) {
        val px = 0.22f * u // one CSS px of the web scene, in this room's units
        val sh = rgba(90, 55, 30, 0.3f)
        out.add(Layer(shaderPaint(LinearGradient(0f, 0f, 0f, 16 * px, intArrayOf(sh, rgba(90, 55, 30, 0.14f), rgba(90, 55, 30, 0f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)), 0f, 0f, w, 16 * px))
        out.add(Layer(shaderPaint(radial(w / 2f, 0f, 0.6f * w, 0.7f * h, intArrayOf(rgba(255, 240, 215, 0.35f), rgba(255, 240, 215, 0f)), floatArrayOf(0f, 0.7f))), 0f, 0f, w, h))
        out.add(Layer(colorPaint(argb("#fff6e8")), 0f, 0f, w, 2 * px))
        out.add(Layer(colorPaint(argb("#e6cfb1")), 0f, 2 * px, w, 9 * px))
        out.add(Layer(colorPaint(rgba(120, 80, 45, 0.25f)), 0f, 9 * px, w, 11 * px))
    }

    /* ============================== shader kit ============================== */

    private fun ok(r: Rect): Boolean =
        r.width.isFinite() && r.height.isFinite() && r.left.isFinite() && r.top.isFinite() && r.width >= 1f && r.height >= 1f

    private fun unit(u: Float, w: Float): Float = if (u.isFinite() && u > 0.01f) u else max(0.01f, w / 100f)

    private fun argb(hex: String, a: Float = 1f): Int = col(hex, a).toArgb()

    private fun rgba(r: Int, g: Int, b: Int, a: Float): Int = android.graphics.Color.argb((a.coerceIn(0f, 1f) * 255f).roundToInt(), r, g, b)

    private fun clear(c: Int): Int = c and 0x00ffffff

    private fun shaderPaint(s: Shader) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { shader = s }

    private fun colorPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }

    private fun strokePaint(c: Int, sw: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c; style = Paint.Style.STROKE; strokeWidth = sw
    }

    /** repeating-linear-gradient(90deg, [c] 0 [lineW], transparent [lineW] [period]). */
    private fun stripesX(period: Float, c: Int, lineW: Float): Shader {
        val f = (lineW / period).coerceIn(0f, 1f)
        return LinearGradient(0f, 0f, max(period, 0.5f), 0f, intArrayOf(c, c, clear(c), clear(c)), floatArrayOf(0f, f, f, 1f), Shader.TileMode.REPEAT)
    }

    /** The same going down; [atEnd] puts the line at the end of each period instead. */
    private fun stripesY(period: Float, c: Int, lineW: Float, atEnd: Boolean = false): Shader {
        val p = max(period, 0.5f)
        return if (atEnd) {
            val f = (1f - lineW / p).coerceIn(0f, 1f)
            LinearGradient(0f, 0f, 0f, p, intArrayOf(clear(c), clear(c), c, c), floatArrayOf(0f, f, f, 1f), Shader.TileMode.REPEAT)
        } else {
            val f = (lineW / p).coerceIn(0f, 1f)
            LinearGradient(0f, 0f, 0f, p, intArrayOf(c, c, clear(c), clear(c)), floatArrayOf(0f, f, f, 1f), Shader.TileMode.REPEAT)
        }
    }

    /** repeating-linear-gradient(90deg, a 0 half, b half period). */
    private fun bandsX(period: Float, a: Int, b: Int): Shader =
        LinearGradient(0f, 0f, max(period, 0.5f), 0f, intArrayOf(a, a, b, b), floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.REPEAT)

    /** A soft glow either side of lines [lineW] wide every [period]. */
    private fun glowLines(period: Float, lineW: Float, glow: Float, c: Int, vertical: Boolean): Shader {
        val p = max(period, 0.5f)
        val a = (lineW / p).coerceIn(0f, 1f)
        val b = ((lineW + glow) / p).coerceIn(a, 1f)
        val z = (1f - glow / p).coerceIn(b, 1f)
        val cols = intArrayOf(c, c, clear(c), clear(c), c)
        val pos = floatArrayOf(0f, a, b, z, 1f)
        return if (vertical) LinearGradient(0f, 0f, p, 0f, cols, pos, Shader.TileMode.REPEAT)
        else LinearGradient(0f, 0f, 0f, p, cols, pos, Shader.TileMode.REPEAT)
    }

    /** radial-gradient(rx ry at cx cy, ...): a circle gradient squashed into an ellipse. */
    private fun radial(cx: Float, cy: Float, rx: Float, ry: Float, colors: IntArray, pos: FloatArray): Shader {
        val r = max(rx, 0.01f)
        // the last colour carries on past its stop
        val cs = colors + colors.last()
        val ps = pos + 1f
        val g = RadialGradient(cx, cy, r, cs, ps, Shader.TileMode.CLAMP)
        if (abs(ry - rx) > 0.001f && ry > 0f) {
            g.setLocalMatrix(Matrix().apply { setScale(1f, ry / r, cx, cy) })
        }
        return g
    }

    /** A CSS linear-gradient at [deg] over a w x h box (0deg = up, 90deg = right). */
    private fun cssLinear(w: Float, h: Float, deg: Float, colors: IntArray, pos: FloatArray): Shader {
        val a = Math.toRadians(deg.toDouble())
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val len = abs(w * dx) + abs(h * dy)
        val cx = w / 2f
        val cy = h / 2f
        return LinearGradient(cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f, colors, pos, Shader.TileMode.CLAMP)
    }

    /** A tile of [tw] x [th] px (drawn by [draw] at that size), repeated from (ox, oy). */
    private fun tile(tw0: Float, th0: Float, ox: Float, oy: Float, maxDim: Int = 512, draw: (Canvas, Float, Float) -> Unit): Shader {
        val tw = max(tw0, 0.5f)
        val th = max(th0, 0.5f)
        val bw = ceil(tw).toInt().coerceIn(2, maxDim)
        val bh = ceil(th).toInt().coerceIn(2, maxDim)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(bw / tw, bh / th)
        draw(c, tw, th)
        val s = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        s.setLocalMatrix(Matrix().apply { setScale(tw / bw, th / bh); postTranslate(ox, oy) })
        return s
    }

    /** radial-gradient(circle at fx fy, [c] 0 [r1], transparent [r2]) / tw th, at (ox, oy). */
    private fun dots(tw: Float, th: Float, ox: Float, oy: Float, fx: Float, fy: Float, c: Int, r1: Float, r2: Float): Shader =
        tile(tw, th, ox, oy) { cv, w, h ->
            val cx = fx * w
            val cy = fy * h
            val rr = max(r2, 0.05f)
            val pt = Paint(Paint.ANTI_ALIAS_FLAG)
            pt.shader = RadialGradient(cx, cy, rr, intArrayOf(c, c, clear(c)), floatArrayOf(0f, (r1 / rr).coerceIn(0f, 1f), 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, rr, pt)
        }

    /** conic-gradient(a 0 25%, b 0 50%, a 0 75%, b 0) / size size: a 2 x 2 checker. */
    private fun checker(size: Float, a: Int, b: Int): Shader = tile(size, size, 0f, 0f) { c, w, h ->
        val pt = Paint()
        pt.color = a
        c.drawRect(w / 2, 0f, w, h / 2, pt)
        c.drawRect(0f, h / 2, w / 2, h, pt)
        pt.color = b
        c.drawRect(w / 2, h / 2, w, h, pt)
        c.drawRect(0f, 0f, w / 2, h / 2, pt)
    }

    /** A linear-gradient at [deg] inside one tile, repeated (marble veins, sheens). */
    private fun gradTile(tw: Float, th: Float, deg: Float, colors: IntArray, pos: FloatArray, maxDim: Int): Shader =
        tile(tw, th, 0f, 0f, maxDim) { c, w, h ->
            val pt = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            pt.shader = cssLinear(w, h, deg, colors, pos)
            c.drawRect(0f, 0f, w, h, pt)
        }

    private val triPath = Path()
    private val triPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        triPath.rewind()
        triPath.moveTo(x1, y1)
        triPath.lineTo(x2, y2)
        triPath.lineTo(x3, y3)
        triPath.close()
        triPaint.color = color
        c.drawPath(triPath, triPaint)
    }
}
