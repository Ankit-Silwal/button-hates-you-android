package com.ankitsilwal.buttonhatesyou.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The app's icons, drawn instead of emoji. The data (catalog, achievements,
// wake options) still names its icons with emoji, which stay the stable ids;
// AppIcon and IconText look them up here and draw a duotone line icon (a soft
// tinted fill under a 1.9-unit stroke, on a 24x24 grid), or, for the money, a
// coloured coin and gem. Anything not listed falls back to plain text.

private enum class K { Fill, Line, Both, Solid }

private class Layer(val k: K, val d: String)

private fun f(d: String) = Layer(K.Fill, d)
private fun l(d: String) = Layer(K.Line, d)
private fun b(d: String) = Layer(K.Both, d)
private fun s(d: String) = Layer(K.Solid, d)

/** A circle as an SVG path. */
private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 z"

/** A gear: [n] teeth round a hub. */
private fun gear(n: Int = 8): String {
    val sb = StringBuilder()
    val ro = 10f
    val ri = 7.6f
    for (i in 0 until n * 4) {
        val a = 2 * PI * i / (n * 4) - PI / 2
        val r = if ((i / 2) % 2 == 0) ro else ri
        val x = 12 + r * cos(a)
        val y = 12 + r * sin(a)
        sb.append(if (i == 0) "M" else "L").append(x.toFloat()).append(' ').append(y.toFloat()).append(' ')
    }
    return sb.append("z").toString()
}

private class Glyph(val color: Color, val layers: List<Layer>)

private val GLYPHS: Map<String, Glyph> = mapOf(
    "bolt" to Glyph(Color(0xFFF0A81C), listOf(b("M13 2 L4 14 h7 l-1 8 l9 -12 h-7 z"))),
    "food" to Glyph(Color(0xFFD9822B), listOf(
        b("M4 11 a8 6.5 0 0 1 16 0 z"),
        l("M3.5 14.5 h17"),
        b("M4.5 17.5 h15 a3 3 0 0 1 -3 3 h-9 a3 3 0 0 1 -3 -3 z"),
        s(c(9f, 7.5f, 0.7f)), s(c(13f, 6.5f, 0.7f)), s(c(15.5f, 8.5f, 0.7f)),
    )),
    "sleep" to Glyph(Color(0xFF6F63D3), listOf(
        b("M20 14.5 A8.5 8.5 0 1 1 9.5 4 a6.5 6.5 0 0 0 10.5 10.5 z"),
        l("M15 2.5 h4.5 l-4.5 4.5 h4.5"),
    )),
    "bubbles" to Glyph(Color(0xFF3FA7D6), listOf(b(c(9f, 14f, 5.5f)), l(c(17.5f, 6.5f, 3f)), l(c(18.5f, 16.5f, 1.8f)), s(c(7f, 12f, 1.1f)))),
    "pan" to Glyph(Color(0xFF5A6273), listOf(b(c(10f, 13f, 7f)), l("M15.5 8 l5.5 -5"), s(c(10f, 13f, 2.2f)))),
    "bed" to Glyph(Color(0xFF4F7FD0), listOf(
        l("M3 5 v15 M21 20 v-4.5 M3 16 h18"),
        b("M10 16 V10.5 h8 a3 3 0 0 1 3 3 V16 z"),
        b(c(6.5f, 12.5f, 2.2f)),
    )),
    "bath" to Glyph(Color(0xFF3FA7D6), listOf(
        b("M2.5 12 h19 v2 a5.5 5.5 0 0 1 -5.5 5.5 h-8 a5.5 5.5 0 0 1 -5.5 -5.5 z"),
        l("M6.5 19.5 l-1 2 M17.5 19.5 l1 2 M5.5 12 V6 a2.2 2.2 0 0 1 4.4 0"),
    )),
    "sofa" to Glyph(Color(0xFF3C8C8A), listOf(
        l("M4.5 11 V8 a3 3 0 0 1 3 -3 h9 a3 3 0 0 1 3 3 v3"),
        b("M2 13.5 a2 2 0 0 1 4 0 v1 h12 v-1 a2 2 0 0 1 4 0 V18 H2 z"),
        l("M5 18 v2 M19 18 v2"),
    )),
    "gamepad" to Glyph(Color(0xFF5B5FD6), listOf(
        b("M7 7 h10 a5 5 0 0 1 4.6 7 l-1.3 3.2 a2.5 2.5 0 0 1 -4 0.9 L14 16 h-4 l-2.3 2.1 a2.5 2.5 0 0 1 -4 -0.9 L2.4 14 A5 5 0 0 1 7 7 z"),
        l("M8 9.8 v4.4 M5.8 12 h4.4"),
        s(c(15.5f, 10.8f, 1.1f)), s(c(17.8f, 13.2f, 1.1f)),
    )),
    "shirt" to Glyph(Color(0xFF3F8FD6), listOf(b("M8.5 3 L3 6 l2 5 l2.2 -1 V21 h9.6 V10 l2.2 1 l2 -5 L15.5 3 a3.5 3.5 0 0 1 -7 0 z"))),
    "cart" to Glyph(Color(0xFF3C8C8A), listOf(
        b("M6 6.5 h15 l-2 8 H7.6 z"),
        l("M2 3 h3 l2.6 11.5 h11"),
        b(c(9f, 19.5f, 1.7f)), b(c(17f, 19.5f, 1.7f)),
    )),
    "bulb" to Glyph(Color(0xFFF0A81C), listOf(
        b("M9 17 h6 v-1.5 c0 -1.6 3 -3 3 -7 a6 6 0 0 0 -12 0 c0 4 3 5.4 3 7 z"),
        l("M10 20.5 h4"),
    )),
    "soap" to Glyph(Color(0xFFE07BA6), listOf(
        b("M4 11 h16 a2 2 0 0 1 2 2 v4 a3 3 0 0 1 -3 3 H5 a3 3 0 0 1 -3 -3 v-4 a2 2 0 0 1 2 -2 z"),
        l(c(8.5f, 6f, 2.3f)), l(c(14.5f, 4.8f, 1.5f)), l(c(13f, 8.5f, 1f)),
    )),
    "shower" to Glyph(Color(0xFF3FA7D6), listOf(
        l("M5 21 V7 a4 4 0 0 1 8 0 v1.5"),
        b("M9 8.5 h8 l-1.2 3 h-5.6 z"),
        l("M10.5 14.5 v1.4 M13 14.5 v1.4 M15.5 14.5 v1.4 M11.8 18.5 v1.4 M14.4 18.5 v1.4"),
    )),
    "trophy" to Glyph(Color(0xFFE09A12), listOf(
        b("M7 3 h10 v5.5 a5 5 0 0 1 -10 0 z"),
        l("M7 5 H4 v1 a3.5 3.5 0 0 0 3.5 3.5 M17 5 h3 v1 a3.5 3.5 0 0 1 -3.5 3.5 M12 13.5 v3.5"),
        b("M8 21 h8 v-2 a2 2 0 0 0 -2 -2 h-4 a2 2 0 0 0 -2 2 z"),
    )),
    "gift" to Glyph(Color(0xFFE0574A), listOf(
        b("M4 11 h16 v9 a1 1 0 0 1 -1 1 H5 a1 1 0 0 1 -1 -1 z"),
        b("M3 7 h18 v4 H3 z"),
        l("M12 7 v14"),
        l("M12 7 c-1 -3.2 -5 -4.2 -5 -1.6 C7 6.8 10 7 12 7 c2 0 5 -0.2 5 -1.6 c0 -2.6 -4 -1.6 -5 1.6"),
    )),
    "gear" to Glyph(Color(0xFF6F7887), listOf(b(gear()), l(c(12f, 12f, 3.2f)))),
    "speaker" to Glyph(Color(0xFF5A6273), listOf(b("M3.5 9 h4 l5.5 -4.5 v15 L7.5 15 h-4 z"), l("M16.5 9 a4.2 4.2 0 0 1 0 6 M19 6.5 a7.8 7.8 0 0 1 0 11"))),
    "vibrate" to Glyph(Color(0xFF5A6273), listOf(
        b("M8.5 3 h7 a1.5 1.5 0 0 1 1.5 1.5 v15 a1.5 1.5 0 0 1 -1.5 1.5 h-7 a1.5 1.5 0 0 1 -1.5 -1.5 v-15 a1.5 1.5 0 0 1 1.5 -1.5 z"),
        l("M3.5 8.5 v7 M20.5 8.5 v7 M11 18 h2"),
    )),
    "trash" to Glyph(Color(0xFFC0453A), listOf(
        l("M3 6 h18 M9 6 V3.8 h6 V6"),
        b("M5 6 l1.2 14 a1.5 1.5 0 0 0 1.5 1.4 h8.6 a1.5 1.5 0 0 0 1.5 -1.4 L19 6 z"),
        l("M10 10 v7 M14 10 v7"),
    )),
    "flame" to Glyph(Color(0xFFE8622C), listOf(b("M12 22 a7 7 0 0 1 -7 -7 c0 -4 3 -6 4 -10.5 c2 2 2.4 4 2.2 5.5 c1.2 -1 2.2 -2.2 2.3 -4.5 c3 2 5.5 5.5 5.5 9.5 a7 7 0 0 1 -7 7 z"))),
    "cookie" to Glyph(Color(0xFFB9772E), listOf(
        b(c(12f, 12f, 9f)),
        s(c(8.5f, 9f, 1.3f)), s(c(14.5f, 7.8f, 1.1f)), s(c(15.5f, 14.2f, 1.4f)), s(c(9.5f, 15.5f, 1.1f)), s(c(12.3f, 11.8f, 0.9f)),
    )),
    "palette" to Glyph(Color(0xFFD9573F), listOf(
        b("M12 3 a9 9 0 1 0 0 18 c1.5 0 2 -1 2 -2 c0 -1.5 -1.5 -2 -1 -3.5 c0.4 -1 1.5 -1.5 3 -1.5 h2 a3 3 0 0 0 3 -3 C21 6.5 17 3 12 3 z"),
        s(c(7.5f, 11f, 1.4f)), s(c(10f, 7f, 1.4f)), s(c(15f, 7f, 1.4f)),
    )),
    "eye" to Glyph(Color(0xFF4A3428), listOf(b("M2 12 c2.5 -5 7.5 -7 10 -7 s7.5 2 10 7 c-2.5 5 -7.5 7 -10 7 s-7.5 -2 -10 -7 z"), s(c(12f, 12f, 3.2f)))),
    "headphones" to Glyph(Color(0xFF5B5FD6), listOf(
        l("M4 15 v-3 a8 8 0 0 1 16 0 v3"),
        b("M3 14 h3.8 v6.5 H4.5 a1.5 1.5 0 0 1 -1.5 -1.5 z"),
        b("M21 14 h-3.8 v6.5 h2.3 a1.5 1.5 0 0 0 1.5 -1.5 z"),
    )),
    "scarf" to Glyph(Color(0xFFD9573F), listOf(b("M4.5 5.5 c3.5 2.2 11.5 2.2 15 0 v4.5 c-3.5 2.2 -11.5 2.2 -15 0 z"), b("M12.5 11.5 l2 9.5 h3.2 l-1.8 -10.2"))),
    "cap" to Glyph(Color(0xFFD9573F), listOf(b("M4 15 a8 8 0 0 1 16 0 z"), l("M4 15 h17 a1.5 1.5 0 0 1 0 3 h-9"), s(c(12f, 6.6f, 1.1f)))),
    "tophat" to Glyph(Color(0xFF3B2A20), listOf(b("M7 3.5 h10 v13 H7 z"), l("M7 13 h10 M2.5 17 c3 2.4 16 2.4 19 0"))),
    "frame" to Glyph(Color(0xFF8A5A2C), listOf(b("M3 4 h18 v16 H3 z"), f("M6 16.5 l4 -5 l3 3 l2 -2 l3 4 z"), l("M6 16.5 l4 -5 l3 3 l2 -2 l3 4 z"), l(c(15.5f, 8.2f, 1.5f)))),
    "planks" to Glyph(Color(0xFF9C6A3C), listOf(b("M3 5 h18 v14 H3 z"), l("M3 9.7 h18 M3 14.3 h18 M9 5 v4.7 M15 9.7 v4.6 M7 14.3 v4.7"))),
    "drops" to Glyph(Color(0xFF3FA7D6), listOf(
        b("M9 3 c3 4 5 7 5 10 a5 5 0 0 1 -10 0 c0 -3 2 -6 5 -10 z"),
        b("M18 10 c1.5 2 2.5 3.5 2.5 5 a2.5 2.5 0 0 1 -5 0 c0 -1.5 1 -3 2.5 -5 z"),
    )),
    "coffee" to Glyph(Color(0xFF8A5A2C), listOf(
        b("M4 9 h12 v6 a5 5 0 0 1 -5 5 H9 a5 5 0 0 1 -5 -5 z"),
        l("M16 11 h1.5 a2.5 2.5 0 0 1 0 5 H16 M8 2.8 c-1 1.5 1 2 0 3.5 M12 2.8 c-1 1.5 1 2 0 3.5"),
    )),
    "drum" to Glyph(Color(0xFFD9573F), listOf(
        b("M4 9.5 v7.5 c0 2 3.6 3.5 8 3.5 s8 -1.5 8 -3.5 V9.5"),
        b("M4 9.5 a8 3.2 0 1 0 16 0 a8 3.2 0 1 0 -16 0 z"),
        l("M9.5 7 L5.5 2 M14.5 7 l4 -5"),
    )),
    "alarm" to Glyph(Color(0xFFD9573F), listOf(
        b(c(12f, 13f, 7.5f)),
        l("M12 9.5 V13 l2.6 1.6 M3.5 6 l3 -3 M20.5 6 l-3 -3 M7 20 l-1.5 1.5 M17 20 l1.5 1.5"),
    )),
    "books" to Glyph(Color(0xFF4F7FD0), listOf(b("M4 4 h4 v16 H4 z"), b("M8 4 h4 v16 H8 z"), b("M13.5 5 l3.8 -1 l4.2 15.5 l-3.8 1 z"))),
    "calendar" to Glyph(Color(0xFFD9573F), listOf(
        b("M3 6 a2 2 0 0 1 2 -2 h14 a2 2 0 0 1 2 2 v13 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 z"),
        l("M3 10 h18 M8 2 v4 M16 2 v4"),
        s(c(8f, 14.5f, 1f)), s(c(12f, 14.5f, 1f)), s(c(16f, 14.5f, 1f)), s(c(8f, 18f, 1f)),
    )),
    "ring" to Glyph(Color(0xFFC9A227), listOf(l(c(12f, 15f, 6f)), b("M9 3.5 h6 l2 3 l-5 4 l-5 -4 z"))),
    "sword" to Glyph(Color(0xFF6F7887), listOf(b("M20.5 3.5 v4 l-10 10 l-4 -4 l10 -10 z"), l("M4.5 13 l6.5 6.5 M7.6 16.4 l-4.1 4.1"))),
    "keyboard" to Glyph(Color(0xFF5B5FD6), listOf(
        b("M2 6 h20 a1 1 0 0 1 1 1 v10 a1 1 0 0 1 -1 1 H2 a1 1 0 0 1 -1 -1 V7 a1 1 0 0 1 1 -1 z"),
        l("M5.5 10 h0.1 M9.5 10 h0.1 M13.5 10 h0.1 M17.5 10 h0.1 M8 14 h8"),
    )),
    "play" to Glyph(Color(0xFFE2674F), listOf(b("M7 4 l13 8 l-13 8 z"))),
    "restart" to Glyph(Color(0xFF5A6273), listOf(l("M20 12 a8 8 0 1 1 -2.4 -5.7"), s("M21 3 v6.5 h-6.5 z"))),
)

/** The emoji the data uses → the icon drawn for it. */
private val BY_EMOJI: Map<String, String> = mapOf(
    "⚡" to "bolt", "🍔" to "food", "💤" to "sleep", "🫧" to "bubbles", "🍳" to "pan", "🛏️" to "bed", "🛏" to "bed",
    "🛁" to "bath", "🛋️" to "sofa", "🛋" to "sofa", "🎮" to "gamepad", "👕" to "shirt", "🛒" to "cart", "💡" to "bulb",
    "🧼" to "soap", "🚿" to "shower", "🏆" to "trophy", "🎁" to "gift", "⚙️" to "gear", "⚙" to "gear", "🔊" to "speaker",
    "📳" to "vibrate", "🗑️" to "trash", "🗑" to "trash", "🔥" to "flame", "🍪" to "cookie", "🎨" to "palette", "👀" to "eye",
    "🎧" to "headphones", "🧣" to "scarf", "🧢" to "cap", "🎩" to "tophat", "🖼️" to "frame", "🖼" to "frame", "🪵" to "planks",
    "💦" to "drops", "☕" to "coffee", "🥁" to "drum", "⏰" to "alarm", "📚" to "books", "📅" to "calendar", "💍" to "ring",
    "🗡️" to "sword", "🗡" to "sword", "⌨️" to "keyboard", "⌨" to "keyboard", "▶" to "play", "↻" to "restart",
)

private const val COIN = "🪙"
private const val GEM = "💎"

/** Whether [glyph] has a drawn icon (the money included). */
fun hasIcon(glyph: String): Boolean = glyph == COIN || glyph == GEM || BY_EMOJI.containsKey(glyph)

/** The colour an icon is drawn in when no tint is given. */
fun iconColor(glyph: String): Color = BY_EMOJI[glyph]?.let { GLYPHS[it]?.color } ?: Color(0xFF4A3428)

/** Draws [glyph]'s icon to fill this (square) scope; [tint] overrides its colour (not the money's). */
fun DrawScope.drawIcon(glyph: String, tint: Color? = null) {
    when (glyph) {
        COIN -> return drawCoin()
        GEM -> return drawGem()
    }
    val g = BY_EMOJI[glyph]?.let { GLYPHS[it] } ?: return
    val color = tint ?: g.color
    val u = minOf(size.width, size.height) / 24f
    val ox = (size.width - 24f * u) / 2
    val oy = (size.height - 24f * u) / 2
    val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    withTransform({
        translate(ox, oy)
        scale(u, u, Offset.Zero)
    }) {
        for (layer in g.layers) {
            val p = svgPath(layer.d)
            when (layer.k) {
                K.Fill -> drawPath(p, color.copy(alpha = color.alpha * 0.22f))
                K.Line -> drawPath(p, color, style = stroke)
                K.Both -> {
                    drawPath(p, color.copy(alpha = color.alpha * 0.22f))
                    drawPath(p, color, style = stroke)
                }
                K.Solid -> drawPath(p, color)
            }
        }
    }
}

/** A gold coin: a rimmed disc with a button stamped on it and a shine. */
private fun DrawScope.drawCoin() {
    val r = minOf(size.width, size.height) / 2 * 0.94f
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Color(0xFFB57A0E), r, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE58A), Color(0xFFF5B92E), Color(0xFFD99413)), Offset(c.x - r * 0.35f, c.y - r * 0.4f), r * 1.6f), r * 0.86f, c)
    drawCircle(Color(0xFFB57A0E).copy(alpha = 0.55f), r * 0.66f, c, style = Stroke(r * 0.08f))
    // the stamp: a little four-hole button
    val h = r * 0.13f
    for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        drawCircle(Color(0xFFB57A0E).copy(alpha = 0.75f), h * 0.75f, Offset(c.x + dx * h * 1.3f, c.y + dy * h * 1.3f))
    }
    drawArc(Color.White.copy(alpha = 0.55f), 200f, 70f, false, Offset(c.x - r * 0.72f, c.y - r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
}

/** A cut diamond: a light crown over a pavilion in two shades, with a glint. */
private fun DrawScope.drawGem() {
    val u = minOf(size.width, size.height) / 24f
    val ox = (size.width - 24f * u) / 2
    val oy = (size.height - 24f * u) / 2
    withTransform({
        translate(ox, oy)
        scale(u, u, Offset.Zero)
    }) {
        drawPath(svgPath("M2 9 L6.5 3.5 h11 L22 9 L12 21.5 z"), Color(0xFF2A86C8))
        drawPath(svgPath("M2 9 h20 L17.5 3.5 h-11 z"), Color(0xFF8FDCFF))
        drawPath(svgPath("M7.5 9 L12 21.5 L16.5 9 z"), Color(0xFF4FB6E8))
        drawPath(svgPath("M2 9 L12 21.5 L7.5 9 z"), Color(0xFF3AA0DC))
        drawPath(svgPath("M9.5 3.5 L7.5 9 h9 l-2 -5.5 z"), Color(0xFFC4EEFF))
        drawPath(svgPath("M2 9 L6.5 3.5 h11 L22 9 L12 21.5 z"), Color(0xFF1C6FA8), style = Stroke(1.1f, join = StrokeJoin.Round))
        drawPath(svgPath("M5.8 6.5 l1.8 -2"), Color.White.copy(alpha = 0.85f), style = Stroke(1.2f, cap = StrokeCap.Round))
    }
}

/** [glyph] as a drawn icon [size] square, or as text when it has none. */
@Composable
fun AppIcon(glyph: String, size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    if (hasIcon(glyph)) Canvas(modifier.size(size)) { drawIcon(glyph, tint) }
    else Text(glyph, modifier = modifier, fontSize = (size.value * 0.85f).sp)
}

/**
 * Text whose icon emoji (🪙 💎 ⚡ 🍔 …) are drawn as the app's icons, inline,
 * a little taller than the letters. Otherwise a plain [Text].
 */
@Composable
fun IconText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current,
    iconTint: Color? = null,
) {
    val (annotated, used) = remember(text) { splitIcons(text) }
    if (used.isEmpty()) {
        Text(text, modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, letterSpacing = letterSpacing, textAlign = textAlign, lineHeight = lineHeight, maxLines = maxLines, overflow = overflow, style = style)
        return
    }
    val inline = used.associateWith { glyph ->
        InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
            Canvas(Modifier.fillMaxSize()) { drawIcon(glyph, iconTint) }
        }
    }
    Text(annotated, modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, letterSpacing = letterSpacing, textAlign = textAlign, lineHeight = lineHeight, maxLines = maxLines, overflow = overflow, inlineContent = inline, style = style)
}

/** [text] with every known icon emoji swapped for an inline placeholder, and the glyphs used. */
private fun splitIcons(text: String): Pair<AnnotatedString, Set<String>> {
    val keys = (BY_EMOJI.keys + COIN + GEM).sortedByDescending { it.length }
    val used = LinkedHashSet<String>()
    val out = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val k = keys.firstOrNull { text.startsWith(it, i) }
            if (k != null) {
                appendInlineContent(k, k)
                used += k
                i += k.length
                // drop a trailing variation selector
                if (i < text.length && text[i] == '️') i++
            } else {
                append(text[i])
                i++
            }
        }
    }
    return out to used
}
