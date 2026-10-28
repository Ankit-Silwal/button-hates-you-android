package com.ankitsilwal.buttonhatesyou.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * The button, alive: it blinks, bobs gently and (when filthy) has a fly.
 *
 * [size] is the body's diameter, like the web's `--s`. The layout box is
 * exactly [size] x [size]; hats and ears draw up to about 0.46 * size above it,
 * legs about 0.22 * size below and headphones a little to the sides, outside
 * the bounds (not clipped), so leave room around it.
 *
 * When [onPoke] is set, tapping squashes it briefly and calls [onPoke].
 */
@Composable
fun Avatar(
    look: Look,
    face: Face = Face(),
    size: Dp,
    modifier: Modifier = Modifier,
    legs: Boolean = true,
    onPoke: (() -> Unit)? = null,
) {
    // one frame clock per avatar; only the draw phase reads it
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    // so two avatars on screen never blink in step
    val seed = remember { Random.nextFloat() * 2.8f }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val poke by rememberUpdatedState(onPoke)

    val touch = if (onPoke != null) {
        Modifier
            .semantics {
                role = Role.Button
                onClick { poke?.invoke(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(1f, tween(70)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
                    },
                    onTap = { poke?.invoke() },
                )
            }
    } else {
        Modifier
    }

    Canvas(modifier.size(size).then(touch)) {
        val t = time.floatValue + seed
        val s = this.size.minDimension
        val p = press.value
        val breathe = sin(t * 2.2f)
        val sleeping = face.mood == Mood.Sleep
        val bob = if (sleeping) 0f else breathe * s * 0.012f
        drawButton(
            center = Offset(this.size.width / 2f, this.size.height / 2f + bob * (1f - p) + p * s * 0.05f),
            size = s,
            look = look,
            face = face,
            t = t,
            // breathing (slower and deeper when asleep), plus the press
            squash = p * 0.12f + (if (sleeping) sin(t * 1.3f) * 0.025f else breathe * 0.01f),
            legs = legs,
            blink = true,
        )
    }
}
