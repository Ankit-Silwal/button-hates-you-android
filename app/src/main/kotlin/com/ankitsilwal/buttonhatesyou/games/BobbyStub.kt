package com.ankitsilwal.buttonhatesyou.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.avatar.drawButton

// Every game draws the button through this one function, wearing the outfit
// the player bought (the same renderer as the rooms).
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
    drawButton(center, size, look, Face(mood = mood), t = t, squash = clamp(squash, -0.5f, 0.5f), flash = flash, tears = tears)
}
