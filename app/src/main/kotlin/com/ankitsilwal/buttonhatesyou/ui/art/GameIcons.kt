package com.ankitsilwal.buttonhatesyou.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankitsilwal.buttonhatesyou.Preload

// Flat line icons for the games, drawn on a 24x24 grid in white on the game's
// tile colour. Used instead of emoji in the Games room and on a game's title card.

/** Each game's tile colour: deep enough for a white icon to read on it. */
fun gameTint(id: String): Color = when (id) {
    "please-dont" -> Color(0xFFD9483B)
    "button-run" -> Color(0xFF4F5BD5)
    "flap" -> Color(0xFF2F86D0)
    "whack" -> Color(0xFFC0632A)
    "stack" -> Color(0xFFC98F17)
    "pop" -> Color(0xFFB83C86)
    "simon" -> Color(0xFF6A49BF)
    "hop" -> Color(0xFF1E9480)
    "run" -> Color(0xFF4A8F2C)
    "slice" -> Color(0xFFC23B4A)
    else -> Color(0xFF5A6273)
}

/** A rounded tile with the game's icon on it. Fills its parent unless [modifier] sizes it. */
@Composable
fun GameIconTile(id: String, modifier: Modifier = Modifier, corner: Dp = 22.dp) {
    val tint = gameTint(id)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.14f), tint, lerp(tint, Color.Black, 0.12f))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
    ) {
        // the icon fills the middle ~56% at any tile size
        Canvas(Modifier.fillMaxSize()) { inset(size.minDimension * 0.22f) { gameIcon(id) } }
    }
}

/**
 * The game's picture: a real still of it (drawn by Preload, wearing the
 * player's look), cropped to fill. Falls back to the icon tile if there's none.
 */
@Composable
fun GameArt(id: String, modifier: Modifier = Modifier, corner: Dp = 22.dp) {
    val shot = Preload.thumbs[id] ?: return GameIconTile(id, modifier, corner)
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(gameTint(id)).border(1.dp, Color(0x1A000000), shape)) {
        Image(shot, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Draws [id]'s icon to fill this scope (square), in [color]. */
fun DrawScope.gameIcon(id: String, color: Color = Color.White) {
    val u = minOf(size.width, size.height) / 24f
    val ox = (size.width - 24f * u) / 2
    val oy = (size.height - 24f * u) / 2
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(build: Path.() -> Unit) = Path().apply(build)
    fun Path.m(x: Float, y: Float) = moveTo(ox + x * u, oy + y * u)
    fun Path.l(x: Float, y: Float) = lineTo(ox + x * u, oy + y * u)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(ox + cx * u, oy + cy * u, ox + x * u, oy + y * u)
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Boolean) =
        if (fill) drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        else drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = line)

    when (id) {
        // a big push button on its base
        "please-dont" -> {
            drawCircle(color, 9f * u, p(12f, 12f), style = line)
            drawCircle(color, 5.5f * u, p(12f, 12f))
        }
        // a keyboard key with a forward chevron
        "button-run" -> {
            rrect(3f, 5f, 18f, 14f, 3f, false)
            drawPath(path { m(9f, 8.5f); l(13.5f, 12f); l(9f, 15.5f) }, color, style = line)
            drawLine(color, p(14.5f, 15.5f), p(17f, 15.5f), line.width, StrokeCap.Round)
        }
        // a wing
        "flap" -> {
            drawPath(
                path { m(3f, 15f); q(8f, 4f, 21f, 5f); q(19f, 11f, 13f, 13f); q(17f, 14f, 18f, 15f); q(12f, 19f, 3f, 15f); close() },
                color, style = line,
            )
            drawLine(color, p(7f, 14.5f), p(15f, 9f), thin.width, StrokeCap.Round)
        }
        // a mallet
        "whack" -> {
            rrect(4f, 3.5f, 13f, 7f, 2f, true)
            drawLine(color, p(10.5f, 10.5f), p(10.5f, 20.5f), 2.6f * u, StrokeCap.Round)
            drawLine(color, p(6f, 20.5f), p(15f, 20.5f), line.width, StrokeCap.Round)
        }
        // three blocks, the top one sliding off
        "stack" -> {
            rrect(4f, 16f, 16f, 4.5f, 1.5f, true)
            rrect(5.5f, 10.5f, 13f, 4.5f, 1.5f, true)
            rrect(9f, 5f, 12f, 4.5f, 1.5f, false)
        }
        // a cluster of bubbles
        "pop" -> {
            drawCircle(color, 5f * u, p(9f, 14f), style = line)
            drawCircle(color, 3.5f * u, p(16.5f, 8f), style = line)
            drawCircle(color, 2.2f * u, p(17.5f, 16.5f))
            drawCircle(color, 1.2f * u, p(7.5f, 12.5f))
        }
        // four pads round a hub
        "simon" -> {
            rrect(3.5f, 3.5f, 7.5f, 7.5f, 2.5f, true)
            rrect(13f, 3.5f, 7.5f, 7.5f, 2.5f, false)
            rrect(3.5f, 13f, 7.5f, 7.5f, 2.5f, false)
            rrect(13f, 13f, 7.5f, 7.5f, 2.5f, true)
        }
        // a cloud with an up arrow
        "hop" -> {
            drawPath(
                path { m(6.5f, 20f); q(2.5f, 20f, 2.5f, 16.5f); q(2.5f, 13f, 6.5f, 13f); q(7.5f, 9f, 12f, 9f); q(16.5f, 9f, 17.5f, 13f); q(21.5f, 13f, 21.5f, 16.5f); q(21.5f, 20f, 17.5f, 20f); close() },
                color, style = line,
            )
            drawLine(color, p(12f, 6.5f), p(12f, 1.8f), line.width, StrokeCap.Round)
            drawPath(path { m(9.5f, 4.2f); l(12f, 1.8f); l(14.5f, 4.2f) }, color, style = line)
        }
        // speed lines behind a moving dot
        "run" -> {
            drawCircle(color, 4.5f * u, p(16f, 12f))
            drawLine(color, p(3f, 8f), p(9f, 8f), line.width, StrokeCap.Round)
            drawLine(color, p(1.5f, 12f), p(9f, 12f), line.width, StrokeCap.Round)
            drawLine(color, p(3f, 16f), p(9f, 16f), line.width, StrokeCap.Round)
        }
        // a blade and its cut
        "slice" -> {
            drawPath(path { m(4f, 20f); l(17f, 7f); q(21f, 3f, 20f, 3.5f); q(20.5f, 4f, 18f, 8.5f); l(6f, 20.5f); close() }, color)
            drawLine(color, p(3f, 10f), p(9f, 4f), thin.width, StrokeCap.Round)
            drawLine(color, p(14f, 21f), p(20f, 15f), thin.width, StrokeCap.Round)
        }
        // anything else: a plain play mark
        else -> drawPath(path { m(8f, 5f); l(19f, 12f); l(8f, 19f); close() }, color)
    }
}
