package com.ankitsilwal.buttonhatesyou.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood

// TEMPORARY stand-in for avatar.drawButton: a rounded button with eyes and a
// mouth per mood. Every game draws the button only through this function, so
// the lead can replace the body with avatar.drawButton(...) in one place.
// Drawn in a 40-unit box scaled to `size`, so the strokes below are shared.

private val LINE = Stroke(width = 2.2f, cap = StrokeCap.Round)
private val THIN = Stroke(width = 1.6f, cap = StrokeCap.Round)
private val INK_C = Color(0xFF2B1D17)
private val MOUTH_C = Color(0xFF5A1F1A)
private val SHEEN = Color(0x55FFFFFF)
private val TEAR = Color(0xFF7CC8FF)
private val EYE_W = Color.White

internal fun DrawScope.drawBobby(
    center: Offset,
    size: Float,
    look: Look,
    mood: Mood = Mood.Calm,
    t: Float = 0f,
    squash: Float = 0f,
    flash: Boolean = false,
    tears: Boolean = false,
) {
    if (!(size > 0f) || center.x.isNaN() || center.y.isNaN()) return
    val k = size / 40f
    val sq = clamp(squash, -0.5f, 0.5f)
    withTransform({
        translate(center.x, center.y)
        scale(k * (1f + sq), k * (1f - sq), Offset.Zero)
    }) {
        val body = if (flash) Color.White else Color(look.color)
        val dark = if (flash) Color(0xFFE8E8E8) else Color(look.dark)
        // the base, then the face plate
        drawRoundRect(dark, Offset(-20f, -13f), Size(40f, 32f), CornerRadius(14f, 14f))
        drawRoundRect(body, Offset(-20f, -20f), Size(40f, 34f), CornerRadius(15f, 15f))
        drawOval(SHEEN, Offset(-14f, -18f), Size(15f, 5f))

        val blink = mood == Mood.Closed || mood == Mood.Sleep || (t * 0.7f) % 4f > 3.86f
        val ey = -5f
        for (side in -1..1 step 2) {
            val ex = side * 7.5f
            when {
                blink -> drawLine(INK_C, Offset(ex - 3.5f, ey), Offset(ex + 3.5f, ey), 2.2f, StrokeCap.Round)
                mood == Mood.Happy -> drawArc(INK_C, 200f, 140f, false, Offset(ex - 4f, ey - 2f), Size(8f, 7f), style = LINE)
                else -> {
                    val big = mood == Mood.Scared
                    val rh = if (big) 5.4f else 4.3f
                    drawOval(EYE_W, Offset(ex - 4.2f, ey - rh), Size(8.4f, rh * 2f))
                    drawCircle(INK_C, if (big) 1.8f else 2.2f, Offset(ex, ey + 0.6f))
                    when (mood) {
                        Mood.Angry, Mood.Annoyed -> drawLine(INK_C, Offset(ex - side * 4.5f, ey - 8f), Offset(ex + side * 4.5f, ey - 5.6f), 2f, StrokeCap.Round)
                        Mood.Sad, Mood.Worried, Mood.Scared -> drawLine(INK_C, Offset(ex - side * 4.5f, ey - 5.6f), Offset(ex + side * 4.5f, ey - 8.2f), 2f, StrokeCap.Round)
                        Mood.Smug, Mood.Tired, Mood.Bored -> drawRect(body, Offset(ex - 4.6f, ey - rh - 0.4f), Size(9.2f, rh * 0.9f))
                        else -> {}
                    }
                }
            }
            if (tears) drawOval(TEAR, Offset(ex - 1.6f + side * 1.5f, ey + 4.5f + (t * 1.7f % 1f) * 5f), Size(3.2f, 4.6f))
        }

        val my = 6.5f
        when (mood) {
            Mood.Happy -> drawArc(MOUTH_C, 10f, 160f, true, Offset(-5f, my - 4f), Size(10f, 8f))
            Mood.Calm -> drawArc(MOUTH_C, 20f, 140f, false, Offset(-4f, my - 4f), Size(8f, 6f), style = THIN)
            Mood.Sad, Mood.Worried, Mood.Tired -> drawArc(MOUTH_C, 200f, 140f, false, Offset(-4f, my), Size(8f, 6f), style = THIN)
            Mood.Scared -> drawOval(MOUTH_C, Offset(-3.6f, my - 2.6f), Size(7.2f, 8f))
            Mood.Smug -> drawLine(MOUTH_C, Offset(-4f, my + 1f), Offset(4.5f, my - 1.2f), 1.8f, StrokeCap.Round)
            Mood.Angry, Mood.Annoyed -> drawArc(MOUTH_C, 210f, 120f, false, Offset(-4.5f, my + 0.5f), Size(9f, 6f), style = LINE)
            else -> drawLine(MOUTH_C, Offset(-3.5f, my + 0.5f), Offset(3.5f, my + 0.5f), 1.8f, StrokeCap.Round)
        }
    }
}
