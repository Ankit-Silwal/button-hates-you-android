package com.ankitsilwal.buttonhatesyou.ui.room

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.economy.Room
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import com.ankitsilwal.buttonhatesyou.ui.art.RoomThemes
import com.ankitsilwal.buttonhatesyou.ui.art.SceneArt
import com.ankitsilwal.buttonhatesyou.ui.common.Clock
import com.ankitsilwal.buttonhatesyou.ui.common.Sky
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Paints a room (src/hub/RoomScene.jsx + scene.css) onto a Canvas, back to
// front: wall, floor, light, the room's own props, the player's furniture,
// the time-of-day tint, then lamp light. The button is a composable on top.
object RoomPainter {

    // dust drifting through the light: [left %, delay s, duration s, size px]
    private val MOTES = arrayOf(
        floatArrayOf(18f, 0f, 14f, 4f), floatArrayOf(26f, 5f, 18f, 3f), floatArrayOf(34f, 2f, 16f, 5f), floatArrayOf(42f, 9f, 20f, 3f), floatArrayOf(58f, 4f, 17f, 4f),
        floatArrayOf(66f, 11f, 15f, 3f), floatArrayOf(72f, 1f, 19f, 5f), floatArrayOf(82f, 7f, 16f, 3f), floatArrayOf(12f, 13f, 21f, 3f), floatArrayOf(50f, 6f, 22f, 4f),
    )

    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    class Params(
        val g: SceneGeom,
        val room: Room?,
        val sky: Sky,
        val clock: Clock,
        val asleep: Boolean,
        val preview: String?,
        val tucked: Boolean,
        val t: Float,
    )

    fun DrawScope.paintBack(p: Params) {
        val g = p.g
        val kind = g.kind
        val defaults = when (kind) {
            RoomKind.Living -> House.DEFAULT_HOUSE
            RoomKind.Kitchen -> House.DEFAULT_ROOMS["kitchen"]
            RoomKind.Bedroom -> House.DEFAULT_ROOMS["bedroom"]
            RoomKind.Bath -> null
        }
        val room = p.room
        val themedWall = room != null && defaults != null && room.wall != defaults.wall && RoomThemes.hasWall(room.wall)
        val themedFloor = room != null && defaults != null && room.floor != defaults.floor && RoomThemes.hasFloor(room.floor)
        val wallR = Rect(0f, 0f, g.w, g.hz)
        val floorR = Rect(0f, g.hz, g.w, g.fullH)

        // the wall
        if (themedWall) RoomThemes.drawWall(this, room!!.wall, wallR, g.u, p.t) else defaultWall(g)
        // the floor
        if (themedFloor) RoomThemes.drawFloor(this, room!!.floor, floorR, g.u, p.t) else defaultFloor(g)

        // sunlight from the top left, and the soft spotlight where he stands
        if (kind != RoomKind.Bedroom) {
            val a = if (p.sky == Sky.Night) 0.1f else 0.75f
            val bx = -0.1f * g.w
            val bw = 0.7f * g.w
            val bh = g.hz * 1.2f
            val shaft = Path().apply {
                moveTo(bx + 0.08f * bw, 0f); lineTo(bx + 0.46f * bw, 0f); lineTo(bx + bw, bh); lineTo(bx + 0.42f * bw, bh); close()
            }
            drawPath(shaft, Brush.linearGradient(0f to Color(0x8CFFEEC8), 0.45f to Color(0x1FFFEEC8), 0.7f to Color.Transparent, start = Offset(bx, 0f), end = Offset(bx + bw, bh)), alpha = a)
            val gc = Offset(g.w / 2, g.hz - g.hs * 0.65f)
            val gr = g.hs * 1.7f
            val breathe = 0.9f + 0.1f * sin(p.t * PI.toFloat() / 6f)
            drawCircle(Brush.radialGradient(0f to Color(0xD9FFF6E4), 0.4f to Color(0x40FFECC8), 0.68f to Color.Transparent, 1f to Color.Transparent, center = gc, radius = gr), gr, gc, alpha = (if (p.sky == Sky.Night) 0.55f else 1f) * breathe)
        }

        // the room's own props (a piece the player put in the same spot replaces them)
        when (kind) {
            RoomKind.Living -> livingProps(p)
            RoomKind.Kitchen -> kitchenProps(p)
            RoomKind.Bedroom -> bedroomProps(p)
            RoomKind.Bath -> bathProps(p)
        }

        // dust in the air
        if (kind != RoomKind.Bedroom || !p.asleep) {
            val mh = g.hz + g.hs * 0.6f
            MOTES.forEach { m ->
                val dur = m[2]
                val ph = (((p.t + dur - m[1]) % dur) / dur)
                val y = mh * 0.94f - ph * mh * 0.8f
                val x = m[0] / 100f * g.w + sin(ph * 6.28f + m[0]) * 12f * g.dp
                val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f) * 0.9f
                val r = m[3] * g.dp / 2
                drawCircle(Color(0x66FFECBE), r * 2.4f, Offset(x, y), alpha = a)
                drawCircle(Color(0xF2FFFAEB), r, Offset(x, y), alpha = a)
            }
        }

        // what the player decorated
        val bedId = g.place["bed"]
        if (kind == RoomKind.Bedroom && bedId != null) {
            g.bedRect?.let { r ->
                HouseArt.draw(this, bedId, r, p.t)
                if (p.preview == "bed") previewMark(r, g)
            }
        }
        val decor = g.place.filterKeys { it != "bed" && it != "nightstand" }
        // the rug first: it lies on the floor
        decor["rug"]?.let { id -> g.spotRect("rug", id)?.let { r -> HouseArt.draw(this, id, r, p.t); if (p.preview == "rug") previewMark(r, g) } }
        for ((spot, id) in decor) {
            if (spot == "rug") continue
            val r = g.spotRect(spot, id) ?: continue
            HouseArt.draw(this, id, r, p.t)
            if (p.preview == spot) previewMark(r, g)
        }

        // the time of day lays a tint over the room
        nightTint(p)
        // the vignette
        drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0x336E4628), center = Offset(g.w / 2, g.fullH * 0.45f), radius = max(g.w, g.fullH) * 0.62f))

        // lamp light glows through the dark
        for ((spot, id) in decor) {
            val glow = HouseArt.info(id)?.glow ?: continue
            val r = g.spotRect(spot, id) ?: continue
            glowAt(r, glow.x, glow.y, glow.r * g.u, glow.color, if (p.asleep) 1f else 0.45f, p.t)
        }
        if (kind == RoomKind.Bedroom) {
            val ns = g.place["nightstand"]
            val nr = g.nsRect
            if (ns != null && nr != null) {
                if (!p.asleep) HouseArt.info(ns)?.glow?.let { glowAt(nr, it.x, it.y, it.r * g.u, it.color, 0.85f, p.t) }
                // the lamp itself sits above the dark (it is the light switch)
                HouseArt.draw(this, ns, nr, p.t, alpha = 1f)
                if (p.asleep) drawRect(Color(0x73000020), nr.topLeft, nr.size)
                if (p.preview == "nightstand") previewMark(nr, g)
            }
        }

        if (p.preview == "wall" || p.preview == "floor") {
            drawRect(Color(0xFFF0C36D), style = Stroke(3 * g.dp), alpha = 0.6f + 0.4f * sin(p.t * 2.2f))
        }
    }

    /** His contact shadow on the floor, under [hero] (his body rect). */
    fun DrawScope.contactShadow(hero: Rect, dp: Float) {
        val c = Offset(hero.center.x, hero.top + hero.height * 1.16f)
        val rx = hero.width * 0.55f
        val ry = min(hero.height * 0.08f, 17 * dp)
        withTransform({ scale(1f, ry / rx, c) }) {
            drawCircle(Brush.radialGradient(0f to Color(0x61462814), 0.6f to Color(0x1F462814), 1f to Color.Transparent, center = c, radius = rx), rx, c)
        }
    }

    /* ------------------------------- walls ------------------------------- */

    private fun DrawScope.dots(r: Rect, step: Float, radius: Float, color: Color) {
        val pts = ArrayList<Offset>()
        var y = r.top + step / 2
        while (y < r.bottom) {
            var x = r.left + step / 2
            while (x < r.right) { pts.add(Offset(x, y)); x += step }
            y += step
        }
        drawPoints(pts, PointMode.Points, color, strokeWidth = radius * 2, cap = StrokeCap.Round)
    }

    private fun DrawScope.gridLines(r: Rect, dx: Float?, dy: Float?, color: Color, width: Float) {
        if (dy != null && dy > 2f) {
            var y = r.top + dy
            while (y < r.bottom) { drawRect(color, Offset(r.left, y - width), Size(r.width, width)); y += dy }
        }
        if (dx != null && dx > 2f) {
            var x = r.left + dx
            while (x < r.right) { drawRect(color, Offset(x - width, r.top), Size(width, r.height)); x += dx }
        }
    }

    private fun DrawScope.wainscot(g: SceneGeom, h: Float, band1: Color, band2: Color, bg: Brush, lineEvery: Float, line: Color, horizontal: Boolean = false) {
        val r = Rect(0f, g.hz - h, g.w, g.hz)
        drawRect(bg, r.topLeft, r.size)
        clipRect(r.left, r.top, r.right, r.bottom) {
            gridLines(r, lineEvery, if (horizontal) lineEvery else null, line, 2 * g.dp)
        }
        drawRect(Color(0x1F966946), Offset(0f, r.top - g.dp), Size(g.w, g.dp))
        drawRect(band1, r.topLeft, Size(g.w, 3 * g.dp))
        drawRect(band2, Offset(0f, r.top + 3 * g.dp), Size(g.w, 3 * g.dp))
    }

    private fun DrawScope.defaultWall(g: SceneGeom) {
        val wr = Rect(0f, 0f, g.w, g.hz)
        val dp = g.dp
        val tile = (g.hs * 0.26f).coerceIn(24 * dp, 52 * dp)
        fun topLight(c: Color, rw: Float = 0.9f) {
            val center = Offset(g.w / 2, -0.1f * g.hz)
            withTransform({ scale(1f, 0.7f * g.hz / (rw * g.w), center) }) {
                drawCircle(Brush.radialGradient(0f to c, 0.7f to c.copy(alpha = 0f), center = center, radius = rw * g.w), rw * g.w, center)
            }
        }
        when (g.kind) {
            RoomKind.Living -> {
                drawRect(Brush.verticalGradient(0f to Color(0xFFF8EDDC), 0.55f to Color(0xFFF4E7D4), 1f to Color(0xFFECD9C0), startY = 0f, endY = g.hz), Offset.Zero, wr.size)
                dots(wr, 26 * dp, 1.2f * dp, Color(0x17966946))
                topLight(Color(0xF2FFFCF4))
                val wh = min(g.hz * 0.34f, g.hs * 0.95f)
                wainscot(g, wh, Color(0xFFFFFAF1), Color(0xFFDCC5A8), Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x14A07350)), g.hz - wh, g.hz), g.w / 8f, Color(0x14966946))
            }
            RoomKind.Kitchen -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF6FAF4), Color(0xFFDCEBE4)), 0f, g.hz), Offset.Zero, wr.size)
                gridLines(wr, tile, tile * 0.5f, Color(0x4D6E9694), dp)
                topLight(Color(0xCCFFFFFF))
                val wh = min(g.hz * 0.30f, g.hs * 0.8f)
                wainscot(g, wh, Color(0xFFFFFAF1), Color(0xFFCDB99A), Brush.verticalGradient(listOf(Color(0xFF9CC4B3), Color(0xFF7BA897)), g.hz - wh, g.hz), tile, Color(0x2E1E463C))
            }
            RoomKind.Bedroom -> {
                drawRect(Brush.verticalGradient(0f to Color(0xFF343A86), 0.7f to Color(0xFF232963), 1f to Color(0xFF1C2152), startY = 0f, endY = g.hz), Offset.Zero, wr.size)
                dots(wr, 30 * dp, 1.2f * dp, Color(0x12FFFFFF))
                topLight(Color(0x47969FFF), 0.8f)
                val wh = min(g.hz * 0.34f, g.hs * 0.95f)
                wainscot(g, wh, Color(0xFF8A8FE0), Color(0xFF4B4F9C), Brush.verticalGradient(listOf(Color(0xFF2C3175), Color(0xFF20255C)), g.hz - wh, g.hz), g.w / 8f, Color(0x0FFFFFFF))
            }
            RoomKind.Bath -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF1FBFC), Color(0xFFCDEAEE)), 0f, g.hz), Offset.Zero, wr.size)
                gridLines(wr, tile, tile, Color(0x4246828C), dp)
                topLight(Color(0xD9FFFFFF))
                val wh = min(g.hz * 0.32f, g.hs * 0.85f)
                wainscot(g, wh, Color(0xFFFFFFFF), Color(0xFF9FC9D2), Brush.verticalGradient(listOf(Color(0xFF7CC0CF), Color(0xFF5AA5B6)), g.hz - wh, g.hz), tile, Color(0x33145064), horizontal = true)
            }
        }
    }

    /* ------------------------------- floors ------------------------------- */

    /** A floor in perspective: lines converge on a point above the middle of the wall. */
    class Persp(val r: Rect, val k: Float = 2.4f) {
        val cx = r.center.x
        val horizon = r.top - r.height / (k - 1f)
        val span = r.top - horizon
        /** Screen y at depth d (1 = the wall, 1/k = the bottom of the screen). */
        fun y(d: Float) = horizon + span / d
        fun x(xTop: Float, d: Float) = cx + (xTop - cx) / d
        val dBottom = 1f / k
    }

    fun DrawScope.defaultFloor(g: SceneGeom) {
        val r = Rect(0f, g.hz, g.w, g.fullH)
        val dp = g.dp
        val pp = Persp(r)
        clipRect(r.left, r.top, r.right, r.bottom) {
            when (g.kind) {
                RoomKind.Living, RoomKind.Bedroom -> {
                    val bed = g.kind == RoomKind.Bedroom
                    val base = if (bed) listOf(Color(0xFF7A5A4A), Color(0xFF5F4438), Color(0xFF523A30)) else listOf(Color(0xFFD9B994), Color(0xFFC79F76), Color(0xFFB98E66))
                    drawRect(Brush.verticalGradient(0f to base[0], 0.6f to base[1], 1f to base[2], startY = r.top, endY = r.bottom), r.topLeft, r.size)
                    planks(pp, 40 * dp, if (bed) Color(0x59140804) else Color(0x386E4628), Color.White.copy(alpha = if (bed) 0.04f else 0.05f), dp)
                }
                RoomKind.Kitchen -> {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFE9E2CF), Color(0xFFD7CDB4)), r.top, r.bottom), r.topLeft, r.size)
                    checker(pp, 30 * dp, Color(0xFFF6F1E6), Color(0xFFA9C4B8))
                }
                RoomKind.Bath -> {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFD7EEF1), Color(0xFFBFE0E5)), r.top, r.bottom), r.topLeft, r.size)
                    tileLines(pp, 30 * dp, Color(0x593C8291), 1.5f * dp)
                }
            }
            skirting(r, dp)
        }
    }

    /** Long boards running away from you: converging lines. */
    fun DrawScope.planks(pp: Persp, every: Float, line: Color, sheen: Color, dp: Float) {
        val r = pp.r
        val n = (r.width * 2.2f / every).toInt() + 2
        for (i in -n..n) {
            val xt = pp.cx + i * every
            val xb = pp.x(xt, pp.dBottom)
            if (i % 2 == 0) {
                val xt2 = xt + every
                val p = Path().apply { moveTo(xt, r.top); lineTo(xt2, r.top); lineTo(pp.x(xt2, pp.dBottom), r.bottom); lineTo(xb, r.bottom); close() }
                drawPath(p, sheen)
            }
            drawLine(line, Offset(xt, r.top), Offset(xb, r.bottom), strokeWidth = 1.6f * dp)
        }
    }

    /** Checker tiles in perspective. */
    fun DrawScope.checker(pp: Persp, tile: Float, a: Color, b: Color) {
        val r = pp.r
        val dd = tile / pp.span * 0.55f
        val cols = (r.width * 2.6f / tile).toInt() + 2
        var d = 1f
        var row = 0
        while (d > pp.dBottom - dd) {
            val d2 = d - dd
            val y1 = pp.y(d); val y2 = pp.y(max(0.05f, d2))
            for (i in -cols..cols) {
                val x0 = pp.cx + i * tile
                val x1 = x0 + tile
                val p = Path().apply { moveTo(pp.x(x0, d), y1); lineTo(pp.x(x1, d), y1); lineTo(pp.x(x1, d2), y2); lineTo(pp.x(x0, d2), y2); close() }
                drawPath(p, if ((i + row) % 2 == 0) a else b)
            }
            d = d2
            row++
            if (row > 200) break
        }
    }

    /** Square tile grout lines in perspective. */
    fun DrawScope.tileLines(pp: Persp, tile: Float, line: Color, width: Float) {
        val r = pp.r
        val cols = (r.width * 2.6f / tile).toInt() + 2
        for (i in -cols..cols) {
            val x0 = pp.cx + i * tile
            drawLine(line, Offset(x0, r.top), Offset(pp.x(x0, pp.dBottom), r.bottom), width)
        }
        val dd = tile / pp.span * 0.55f
        var d = 1f - dd
        var n = 0
        while (d > pp.dBottom && n < 200) {
            val y = pp.y(d)
            drawLine(line, Offset(r.left, y), Offset(r.right, y), width)
            d -= dd
            n++
        }
    }

    fun DrawScope.skirting(r: Rect, dp: Float) {
        drawRect(Brush.verticalGradient(listOf(Color(0x59603718), Color.Transparent), r.top, r.top + 22 * dp), r.topLeft, Size(r.width, 22 * dp))
        drawRect(Color(0xFFFFF6E8), r.topLeft, Size(r.width, 2 * dp))
        drawRect(Color(0xFFE6CFB1), Offset(r.left, r.top + 2 * dp), Size(r.width, 7 * dp))
        drawRect(Color(0x40784F2D), Offset(r.left, r.top + 9 * dp), Size(r.width, 2 * dp))
        val c = Offset(r.center.x, r.top)
        withTransform({ scale(1f, 0.7f * r.height / (0.6f * r.width), c) }) {
            drawCircle(Brush.radialGradient(0f to Color(0x59FFF0D7), 0.7f to Color.Transparent, center = c, radius = 0.6f * r.width), 0.6f * r.width, c)
        }
    }

    /* ------------------------------- props ------------------------------- */

    private fun DrawScope.rug(g: SceneGeom, w: Float, h: Float, rings: List<Pair<Float, Color>>) {
        val c = Offset(g.hx, g.hz - g.hs * 0.1f + h / 2)
        val rx = w / 2
        drawOval(Color(0x52603718), Offset(c.x - rx, c.y - h / 2 + 5 * g.dp), Size(w, h))
        withTransform({ scale(1f, h / w, c) }) {
            val stops = ArrayList<Pair<Float, Color>>()
            for (i in rings.indices) {
                val (at, col) = rings[i]
                val start = if (i == 0) 0f else rings[i - 1].first + 0.005f
                stops.add(start to col); stops.add(at to col)
            }
            drawCircle(Brush.radialGradient(*stops.toTypedArray(), center = c, radius = rx), rx, c, alpha = 0.95f)
        }
    }

    private fun DrawScope.livingProps(p: Params) {
        val g = p.g
        val t = p.t
        if (!g.has("rug")) rug(g, g.hs * 3.1f, g.hs * 0.62f, listOf(0.52f to Color(0xFFF5E1B8), 0.60f to Color(0xFFE2674F), 0.68f to Color(0xFFF5E1B8), 1f to Color(0xFF6FA3A8)))
        // the window, left
        val wh = g.hs * 1.1f
        val wy = max(g.top, g.hz - g.hs * 2f)
        val wx = (g.w * 0.06f).coerceIn(14 * g.dp, 120 * g.dp)
        SceneArt.window(this, Rect(wx, wy, wx + wh * 0.8f, wy + wh), p.sky, p.clock, SceneArt.Dress.Curtain, t)
        // picture frame between window and clock
        if (!g.has("wall-left")) {
            val fw = g.hs * 0.62f
            val fx = g.w * 0.52f - fw / 2
            SceneArt.frame(this, Rect(fx, wy + 6 * g.dp, fx + fw, wy + 6 * g.dp + fw * 64f / 80f))
        }
        if (!g.has("wall-right")) {
            val cw = g.hs * 0.42f
            val cx = g.w - (g.w * 0.05f).coerceIn(12 * g.dp, 90 * g.dp) - cw
            SceneArt.clock(this, Rect(cx, wy, cx + cw, wy + cw), p.clock)
            val sw = g.hs * 0.95f
            val sx = g.w - (g.w * 0.04f).coerceIn(10 * g.dp, 80 * g.dp) - sw
            val sy = max(wy + cw + 14 * g.dp, g.hz - g.hs * 1.4f)
            SceneArt.shelf(this, Rect(sx, sy, sx + sw, sy + sw * 64f / 120f))
        }
        val lampOn = !g.has("corner")
        // the floor lamp (beside the plant) and its warm pool
        val lw = g.hs * 0.28f
        val plantW = g.hs * 0.5f
        val plantX = (g.w * 0.02f).coerceIn(6 * g.dp, 60 * g.dp)
        if (lampOn) {
            val lx = plantX + plantW * 0.8f
            val lh = lw * 3f
            val lb = g.hz + g.hs * 0.14f
            val gc = Offset(lx + lw / 2, lb - lh * 0.85f)
            val gr = g.hs * 1.6f
            drawCircle(Brush.radialGradient(0f to Color(if (p.sky == Sky.Night) 0x8CFFC878 else 0x47FFC878), 0.45f to Color(if (p.sky == Sky.Night) 0x24FFC878 else 0x14FFC878), 0.7f to Color.Transparent, center = gc, radius = gr), gr, gc)
            SceneArt.floorLamp(this, Rect(lx, lb - lh, lx + lw, lb))
        }
        if (!g.has("floor-left") && !g.has("corner")) {
            val ph = plantW * 1.5f
            val pb = g.hz + g.hs * 0.17f
            SceneArt.plant(this, Rect(plantX, pb - ph, plantX + plantW, pb), t)
        }
        if (!g.has("floor-right")) {
            val sw = min(g.hs * 1.25f, g.w * 0.36f)
            val sx = g.w - (g.w * 0.025f).coerceIn(6 * g.dp, 50 * g.dp) - sw
            val sb = g.hz + g.hs * 0.2f
            SceneArt.sofa(this, Rect(sx, sb - sw * 0.5f, sx + sw, sb))
        }
    }

    private fun DrawScope.kitchenProps(p: Params) {
        val g = p.g
        val t = p.t
        if (!g.has("rug")) rug(g, g.hs * 3.1f, g.hs * 0.62f, listOf(0.52f to Color(0xFFFBEED3), 0.60f to Color(0xFF8FB8A6), 0.68f to Color(0xFFFBEED3), 1f to Color(0xFFD98C6F)))
        val wh = g.hs * 0.85f
        val ww = wh * 1.05f
        val wcx = g.cs + g.cw * 0.42f
        val wy = max(g.top, g.hz - g.hs * 1.8f)
        SceneArt.window(this, Rect(wcx - ww / 2, wy, wcx + ww / 2, wy + wh), p.sky, p.clock, SceneArt.Dress.Valance, t)
        if (!g.has("wall-right") && !g.narrow) {
            val sw = g.hs * 0.8f
            val sx = g.cs + g.cw * 0.84f - sw / 2
            val sy = max(g.top, g.hz - g.hs * 1.55f)
            SceneArt.wallShelf(this, Rect(sx, sy, sx + sw, sy + sw * 64f / 120f))
        }
        if (!g.has("ceiling")) {
            val pw = g.hs * 0.4f
            val ph = pw * 2f
            val px = g.w * 0.66f - pw / 2
            val top = min(0f, g.heroTop - g.hs * 0.5f - ph).coerceAtLeast(-ph * 0.35f)
            val gc = Offset(g.w * 0.66f, top + ph * 0.82f)
            val gr = g.hs * 1.6f
            drawCircle(Brush.radialGradient(0f to Color(if (p.sky == Sky.Night) 0x8CFFC878 else 0x47FFC878), 0.45f to Color(0x14FFC878), 0.7f to Color.Transparent, center = gc, radius = gr), gr, gc)
            SceneArt.pendant(this, Rect(px, top, px + pw, top + ph), t)
        }
        val fb = g.hz + g.hs * 0.15f
        SceneArt.fridge(this, Rect(g.cl, fb - g.fh, g.cl + g.fw, fb))
        // the counter and its sink
        val ch = g.hs * 0.62f
        val counter = Rect(g.cs, fb - ch, g.cs + g.cw, fb)
        val sinkW = g.hs * 0.7f
        val sinkX = g.cs + g.cw * 0.24f
        SceneArt.sink(this, Rect(sinkX, counter.top - sinkW * 44f / 70f + 3 * g.dp, sinkX + sinkW, counter.top + 3 * g.dp))
        SceneArt.counter(this, counter, if (g.narrow) 3 else 6)
        // the hood over the stove
        val sx = g.w - g.sr - g.sw
        SceneArt.hood(this, Rect(sx, g.hz - g.hs * 1.1f - g.sw * 50f / 84f, sx + g.sw, g.hz - g.hs * 1.1f))
        val sh = g.sw / SceneArt.STOVE_ASPECT
        SceneArt.stove(this, Rect(sx, fb - sh, sx + g.sw, fb), t)
    }

    private fun DrawScope.bedroomProps(p: Params) {
        val g = p.g
        val t = p.t
        if (!g.has("rug")) rug(g, g.hs * 2.6f, g.hs * 0.5f, listOf(0.52f to Color(0xFFB8A4E8), 0.62f to Color(0xFF8B74E0), 0.70f to Color(0xFFB8A4E8), 1f to Color(0xFF5E4AA8)))
        if (!g.has("ceiling")) {
            val lw = min(g.w, g.hs * 5f)
            val y = g.top - 30 * g.dp
            SceneArt.stringLights(this, Rect(g.w / 2 - lw / 2, y, g.w / 2 + lw / 2, y + g.hs * 0.4f), t)
        }
        val wh = g.hs * 0.9f
        val ww = wh * 0.8f
        val wcx = g.w * 0.66f
        val wy = max(g.top, g.hz - g.hs * 2.25f)
        SceneArt.window(this, Rect(wcx - ww / 2, wy, wcx + ww / 2, wy + wh), if (p.asleep) Sky.Night else p.sky, p.clock, SceneArt.Dress.Night, t)
        if (!g.narrow) {
            if (!g.has("wall-left")) {
                val pw = g.hs * 0.8f
                val cx = g.nsRect?.center?.x ?: (g.w * 0.3f)
                val py = max(g.top, g.hz - g.hs * 2.05f)
                SceneArt.poster(this, Rect(cx - pw / 2, py, cx + pw / 2, py + pw * 1.25f))
            }
            if (!g.has("floor-right")) {
                val h = min(g.hs * 1.95f, g.hz - g.top)
                val w = h * 100f / 190f
                val x = g.w - max(8 * g.dp, g.w * 0.02f) - w
                val b = g.hz + g.hs * 0.17f
                SceneArt.wardrobe(this, Rect(x, b - h, x + w, b))
            }
        }
    }

    fun DrawScope.bathProps(p: Params) {
        val g = p.g
        val t = p.t
        // the bath mat
        val mw = g.hs * 1.9f
        val mh = g.hs * 0.4f
        val mc = Offset(g.w * 0.42f, g.hz - g.hs * 0.02f + mh / 2)
        drawOval(Color(0x4D28646E), Offset(mc.x - mw / 2, mc.y - mh / 2 + 4 * g.dp), Size(mw, mh))
        val mat = Path().apply { addOval(Rect(mc.x - mw / 2, mc.y - mh / 2, mc.x + mw / 2, mc.y + mh / 2)) }
        clipPath(mat) {
            val s = 14 * g.dp
            var x = mc.x - mw / 2
            var i = 0
            while (x < mc.x + mw / 2) { drawRect(if (i % 2 == 0) Color(0xFFF3A6A0) else Color(0xFFF9CFC9), Offset(x, mc.y - mh / 2), Size(s, mh)); x += s; i++ }
        }
        // vanity, mirror, towels
        val vw = g.hs * 1.3f
        val vh = vw * 84f / 120f
        val vb = g.hz + g.hs * 0.15f
        val mrw = g.hs * 0.62f
        val mcx = g.vx + g.hs * 0.65f
        val mb = g.hz - g.hs * 0.95f
        SceneArt.mirror(this, Rect(mcx - mrw / 2, mb - mrw * 1.25f, mcx + mrw / 2, mb))
        if (!g.narrow) {
            val tw = g.hs * 0.8f
            val tx = g.vx + g.hs * 1.75f
            SceneArt.towels(this, Rect(tx, mb - tw * 0.9f, tx + tw, mb))
        } else {
            // on a phone the towels hang on the wall over the shower's left
            val tw = g.hs * 0.55f
            val tx = g.shower.left - tw * 0.2f
            val ty = g.shower.top + g.shower.height * 0.06f - tw * 0.9f
            if (ty > g.top) SceneArt.towels(this, Rect(tx, ty, tx + tw, ty + tw * 0.9f))
        }
        SceneArt.vanity(this, Rect(g.vx, vb - vh, g.vx + vw, vb))
        // the duck
        val dw = g.hs * 0.34f
        val dx = g.w * 0.42f + g.hs * 0.8f - dw / 2
        val dy = g.hz + g.hs * 0.02f
        SceneArt.duck(this, Rect(dx, dy, dx + dw, dy + dw * 50f / 60f), t)
        // the shower stall
        SceneArt.showerBack(this, g.shower)
    }

    /* ---------------------------- tints and glows ---------------------------- */

    private fun DrawScope.nightTint(p: Params) {
        val g = p.g
        if (g.kind == RoomKind.Bedroom) {
            if (p.asleep) {
                val c = Offset(g.w * 0.35f, g.h * 0.7f)
                drawRect(Color(0xDB060822))
                drawCircle(Brush.radialGradient(0f to Color(0x80141850), 1f to Color.Transparent, center = c, radius = max(g.w, g.h) * 0.5f), max(g.w, g.h) * 0.5f, c)
                // moonlight from the window
                val wcx = g.w * 0.66f
                val top = g.hz - g.hs * 1.5f
                val bw = g.hs * 3.4f
                val bh = g.hs * 2.3f
                val beam = Path().apply { moveTo(wcx - bw * 0.2f, top); lineTo(wcx + bw * 0.2f, top); lineTo(wcx + bw / 2, top + bh); lineTo(wcx - bw / 2, top + bh); close() }
                drawPath(beam, Brush.verticalGradient(0f to Color(0x38A0B9FF), 0.8f to Color(0x0F8CA0F0), 1f to Color.Transparent, startY = top, endY = top + bh))
            } else {
                drawRect(Color(0x4D222464))
                // the warm light of the bedside lamp
                g.nsRect?.let { r ->
                    val c = Offset(r.center.x, g.hz - g.hs * 0.7f)
                    val rad = g.hs * 2.6f
                    drawCircle(Brush.radialGradient(0f to Color(0x80FFC46E), 0.6f to Color(0x24FFA050), 1f to Color.Transparent, center = c, radius = rad), rad, c)
                }
            }
            return
        }
        when (p.sky) {
            Sky.Night -> {
                drawRect(Color(0x57181E5A))
                val c = Offset(g.w / 2, g.h * 0.6f)
                val rad = max(g.w, g.h) * 0.6f
                drawCircle(Brush.radialGradient(0.3f to Color.Transparent, 1f to Color(0x660E123C), center = c, radius = rad), rad, c)
            }
            Sky.Dusk -> drawRect(Color(0x29FF8246))
            Sky.Dawn -> drawRect(Color(0x24FFBE96))
            Sky.Day -> {}
        }
    }

    private fun DrawScope.glowAt(r: Rect, xPct: Float, yPct: Float, radius: Float, color: Color, alpha: Float, t: Float) {
        val c = Offset(r.left + xPct / 100f * r.width, r.top + yPct / 100f * r.height)
        val rad = radius / 2
        val breathe = 0.85f + 0.15f * sin(t * PI.toFloat() / 3f)
        drawCircle(Brush.radialGradient(0f to color.copy(alpha = 0.75f), 0.4f to color.copy(alpha = 0.22f), 0.7f to Color.Transparent, 1f to Color.Transparent, center = c, radius = rad), rad, c, alpha = alpha * breathe)
    }

    /** The dashed outline and "PREVIEW" tag around a piece being tried. */
    private fun DrawScope.previewMark(r: Rect, g: SceneGeom) {
        val pad = 8 * g.dp
        val rr = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        drawRoundRect(Color(0x1FF0C36D), rr.topLeft, rr.size, CornerRadius(12 * g.dp))
        drawRoundRect(Color(0xFFF0C36D), rr.topLeft, rr.size, CornerRadius(12 * g.dp), style = Stroke(2 * g.dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * g.dp, 4 * g.dp))))
        val label = "PREVIEW"
        tagPaint.textSize = 11 * g.dp
        val tw = tagPaint.measureText(label) + 20 * g.dp
        val th = 20 * g.dp
        val y = if (rr.top - th - 4 * g.dp > 0) rr.top - th - 4 * g.dp else rr.bottom + 4 * g.dp
        drawRoundRect(Color(0xFF3B2A20), Offset(r.center.x - tw / 2, y), Size(tw, th), CornerRadius(th / 2))
        tagPaint.color = Color(0xFFFFE9B8).toArgb()
        drawContext.canvas.nativeCanvas.drawText(label, r.center.x, y + th * 0.68f, tagPaint)
    }
}
