package com.ankitsilwal.buttonhatesyou.ui.art

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill as FillStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.max

// A tiny SVG-like drawing kit, so the web's hand-drawn SVG art (src/hub/scene-art.jsx,
// src/house/art.jsx) ports almost line for line. Coordinates are in viewBox
// units; drawSvg() maps the viewBox onto a destination rect.
//
//   drawSvg(200f, 92f, rect) {
//       rect(6f, 52f, 188f, 30f, lin(0f to c("#cfc4b0"), 1f to c("#a99c86")), rx = 10f)
//       path("M18 38 q2 -14 22 -14 h26 z", c("#fff"))
//       circle(30f, 30f, 3f, c("#e2674f"))
//   }
//
// Gradients use SVG's default objectBoundingBox units (0..1 over the shape's bounds).

/* ------------------------------- paints -------------------------------- */

sealed interface Fill

data class Solid(val color: Color) : Fill

class Lin(val stops: Array<Pair<Float, Color>>, val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Fill

class Rad(val stops: Array<Pair<Float, Color>>, val cx: Float, val cy: Float, val r: Float) : Fill

private val colorCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

/** "#e2674f" / "#fff" / "#e2674f80" as a Color, times [a]. */
fun col(hex: String, a: Float = 1f): Color {
    val base = colorCache.getOrPut(hex) {
        val h = hex.removePrefix("#")
        val full = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("") + "ff"
            4 -> h.map { "$it$it" }.joinToString("")
            6 -> h + "ff"
            else -> h
        }
        val v = full.toLong(16)
        Color(((v shr 24) and 0xff).toInt(), ((v shr 16) and 0xff).toInt(), ((v shr 8) and 0xff).toInt(), (v and 0xff).toInt())
    }
    return if (a >= 1f) base else base.copy(alpha = base.alpha * a.coerceIn(0f, 1f))
}

/** A solid fill from hex (opacity [a]). */
fun c(hex: String, a: Float = 1f): Fill = Solid(col(hex, a))

fun c(color: Color): Fill = Solid(color)

/** A linear gradient; default top to bottom like SVG's x2=0 y2=1 in the web art. */
fun lin(vararg stops: Pair<Float, Color>, x1: Float = 0f, y1: Float = 0f, x2: Float = 0f, y2: Float = 1f): Fill =
    Lin(arrayOf(*stops), x1, y1, x2, y2)

/** A radial gradient (cx, cy, r as fractions of the shape's bounds). */
fun rad(vararg stops: Pair<Float, Color>, cx: Float = 0.5f, cy: Float = 0.5f, r: Float = 0.5f): Fill =
    Rad(arrayOf(*stops), cx, cy, r)

/* ------------------------------- paths -------------------------------- */

private val pathCache = java.util.concurrent.ConcurrentHashMap<String, Path>()
private val boundsCache = java.util.concurrent.ConcurrentHashMap<String, Rect>()

/** An SVG path string as a (cached, shared, never mutate it) Path. */
fun svgPath(d: String): Path = pathCache.getOrPut(d) {
    try {
        PathParser().parsePathString(d).toPath()
    } catch (e: Throwable) {
        Path()
    }
}

private fun boundsOf(d: String): Rect = boundsCache.getOrPut(d) { svgPath(d).getBounds() }

/* ------------------------------- drawing -------------------------------- */

/**
 * Draws [block] with a viewBox (vbX, vbY, vbW, vbH) stretched over [dst].
 * [t] is a clock in seconds for animated bits.
 */
fun DrawScope.drawSvg(
    vbW: Float,
    vbH: Float,
    dst: Rect,
    t: Float = 0f,
    alpha: Float = 1f,
    vbX: Float = 0f,
    vbY: Float = 0f,
    block: Svg.() -> Unit,
) {
    if (dst.width <= 0f || dst.height <= 0f || vbW <= 0f || vbH <= 0f) return
    withTransform({
        translate(dst.left, dst.top)
        scale(dst.width / vbW, dst.height / vbH, Offset.Zero)
        translate(-vbX, -vbY)
    }) {
        Svg(this, t, alpha).block()
    }
}

/** The SVG-ish painter. All numbers are in viewBox units. */
class Svg(val ds: DrawScope, val t: Float, private var alpha: Float = 1f) {

    private fun brush(f: Fill, b: Rect): Brush = when (f) {
        is Solid -> SolidColor(f.color)
        is Lin -> Brush.linearGradient(
            colorStops = f.stops,
            start = Offset(b.left + f.x1 * b.width, b.top + f.y1 * b.height),
            end = Offset(b.left + f.x2 * b.width, b.top + f.y2 * b.height),
        )
        is Rad -> Brush.radialGradient(
            colorStops = f.stops,
            center = Offset(b.left + f.cx * b.width, b.top + f.cy * b.height),
            radius = max(0.01f, f.r * max(b.width, b.height)),
        )
    }

    private fun strokeStyle(sw: Float, cap: StrokeCap, join: StrokeJoin, dash: FloatArray?) =
        Stroke(width = sw, cap = cap, join = join, pathEffect = dash?.let { PathEffect.dashPathEffect(it) })

    fun rect(
        x: Float, y: Float, w: Float, h: Float,
        fill: Fill? = null, rx: Float = 0f, ry: Float = rx,
        stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f, dash: FloatArray? = null,
    ) {
        val b = Rect(x, y, x + w, y + h)
        val a = this.alpha * alpha
        val r = CornerRadius(rx.coerceAtMost(w / 2), ry.coerceAtMost(h / 2))
        if (fill != null) ds.drawRoundRect(brush(fill, b), Offset(x, y), Size(w, h), r, alpha = a)
        if (stroke != null) ds.drawRoundRect(brush(stroke, b), Offset(x, y), Size(w, h), r, alpha = a, style = strokeStyle(sw, StrokeCap.Butt, StrokeJoin.Miter, dash))
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Fill? = null, stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f, dash: FloatArray? = null) {
        val b = Rect(cx - r, cy - r, cx + r, cy + r)
        val a = this.alpha * alpha
        if (fill != null) ds.drawCircle(brush(fill, b), r, Offset(cx, cy), alpha = a)
        if (stroke != null) ds.drawCircle(brush(stroke, b), r, Offset(cx, cy), alpha = a, style = strokeStyle(sw, StrokeCap.Butt, StrokeJoin.Miter, dash))
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: Fill? = null, stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f) {
        val b = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
        val a = this.alpha * alpha
        if (fill != null) ds.drawOval(brush(fill, b), b.topLeft, b.size, alpha = a)
        if (stroke != null) ds.drawOval(brush(stroke, b), b.topLeft, b.size, alpha = a, style = Stroke(sw))
    }

    /** An SVG path. Fill and/or stroke; [cap]/[join] like strokeLinecap/strokeLinejoin. */
    fun path(
        d: String, fill: Fill? = null,
        stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f,
        cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter, dash: FloatArray? = null,
    ) {
        val p = svgPath(d)
        val b = boundsOf(d)
        val a = this.alpha * alpha
        if (fill != null) ds.drawPath(p, brush(fill, b), alpha = a, style = FillStyle)
        if (stroke != null) ds.drawPath(p, brush(stroke, b), alpha = a, style = strokeStyle(sw, cap, join, dash))
    }

    /** A Path built in code (for things that move with [t]). */
    fun path(p: Path, fill: Fill? = null, stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f, cap: StrokeCap = StrokeCap.Butt) {
        val b = p.getBounds()
        val a = this.alpha * alpha
        if (fill != null) ds.drawPath(p, brush(fill, b), alpha = a, style = FillStyle)
        if (stroke != null) ds.drawPath(p, brush(stroke, b), alpha = a, style = Stroke(sw, cap = cap))
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, stroke: Fill, sw: Float = 1f, alpha: Float = 1f, cap: StrokeCap = StrokeCap.Butt) {
        val b = Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 0.01f, maxOf(y1, y2) + 0.01f)
        ds.drawLine(brush(stroke, b), Offset(x1, y1), Offset(x2, y2), strokeWidth = sw, cap = cap, alpha = this.alpha * alpha)
    }

    /** `<polygon points="x,y x,y ...">` / polyline. */
    fun polygon(points: FloatArray, fill: Fill? = null, stroke: Fill? = null, sw: Float = 1f, alpha: Float = 1f, close: Boolean = true) {
        if (points.size < 4) return
        val p = Path().apply {
            moveTo(points[0], points[1])
            var i = 2
            while (i + 1 < points.size) {
                lineTo(points[i], points[i + 1]); i += 2
            }
            if (close) close()
        }
        path(p, fill, stroke, sw, alpha, StrokeCap.Round)
    }

    /** Text (bold, centred on x when [center]). [size] in viewBox units. */
    fun text(x: Float, y: Float, s: String, size: Float, color: Color, bold: Boolean = true, center: Boolean = true, alpha: Float = 1f) {
        val paint = textPaint
        paint.textSize = size
        paint.color = color.copy(alpha = color.alpha * this.alpha * alpha).toArgb()
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        ds.drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
    }

    /** A group: translate, then rotate [deg] about (px, py), then scale about (px, py); opacity multiplies. */
    fun g(
        tx: Float = 0f, ty: Float = 0f, deg: Float = 0f, px: Float = 0f, py: Float = 0f,
        sx: Float = 1f, sy: Float = sx, alpha: Float = 1f, block: Svg.() -> Unit,
    ) {
        val before = this.alpha
        this.alpha = before * alpha
        ds.withTransform({
            if (tx != 0f || ty != 0f) translate(tx, ty)
            if (deg != 0f) rotate(deg, Offset(px, py))
            if (sx != 1f || sy != 1f) scale(sx, sy, Offset(px, py))
        }) {
            block()
        }
        this.alpha = before
    }

    /** Only draw inside a rect. */
    fun clip(x: Float, y: Float, w: Float, h: Float, block: Svg.() -> Unit) {
        ds.clipRect(x, y, x + w, y + h) { block() }
    }

    /** Only draw inside an SVG path. */
    fun clip(d: String, block: Svg.() -> Unit) {
        ds.clipPath(svgPath(d)) { block() }
    }

    companion object {
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    }
}

/** `[0 to "#fff", 1 to "#000"]`-style stops with optional opacity: stop(0f, "#fff", .5f). */
fun stop(at: Float, hex: String, a: Float = 1f): Pair<Float, Color> = at to col(hex, a)
