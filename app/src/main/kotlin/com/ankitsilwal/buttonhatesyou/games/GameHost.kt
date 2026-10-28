package com.ankitsilwal.buttonhatesyou.games

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ankitsilwal.buttonhatesyou.avatar.Look
import kotlinx.coroutines.flow.first
import kotlin.math.min

// How long a finished run plays out (the crash, the last words) before the
// Continue offer or the results, like the web shell.
private const val OVER_DELAY = 0.9f

// Per-run bookkeeping the frame loop and touch handler share.
private class HostRun {
    var overFor = 0f
    var played = 0f // seconds of unpaused play
    var reported = false
    var errors = 0
    var pointer: PointerId? = null
}

/**
 * Runs one arcade game full-screen: a frame loop (dt clamped to 1/30 s,
 * stopped while [paused] or the app is in the background), the 480x800 world
 * letterboxed to fit (scaled by the smaller side, centred, dark bars), touches
 * mapped to world coordinates, and the end of the run.
 *
 * When the game's `over` flips and has played out for a moment: if the game
 * [Game.canRevive] and [onContinueRequest] returns true, the game is revived
 * and keeps going; otherwise [onOver] is called once with the score, coins
 * and milliseconds of play.
 *
 * The outfit ([look]) is read once when the run starts; a new [spec] starts a new run.
 */
@Composable
fun GameHost(
    spec: GameSpec,
    look: Look,
    onOver: (score: Int, coins: Int, ms: Long) -> Unit,
    onContinueRequest: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    sfx: Sfx? = null,
    haptics: Haptics? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sound = sfx ?: remember { SynthSfx.shared(context) }
    val buzz = haptics ?: remember(view) { DeviceHaptics(view) }
    val game = remember(spec) { spec.create(GameContext(look, sound, buzz)) }
    val run = remember(game) { HostRun() }

    val life by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = rememberUpdatedState(!paused && life.isAtLeast(Lifecycle.State.RESUMED))
    val overCb = rememberUpdatedState(onOver)
    val continueCb = rememberUpdatedState(onContinueRequest)
    val frame = remember(game) { mutableLongStateOf(0L) }

    fun report() {
        if (run.reported) return
        run.reported = true
        val score = game.score.coerceAtLeast(0)
        val coins = game.coins.coerceAtLeast(0)
        overCb.value(score, coins, (run.played * 1000f).toLong())
    }

    fun step(dt: Float) {
        if (run.reported) return
        try {
            game.update(dt)
        } catch (e: Throwable) {
            Log.e("GameHost", "update failed in ${spec.id}", e)
            if (++run.errors > 3) return report()
        }
        run.played += dt
        if (!game.over) {
            run.overFor = 0f
            return
        }
        run.overFor += dt
        if (run.overFor < OVER_DELAY) return
        val ask = continueCb.value
        val revived = try {
            game.canRevive && ask != null && ask()
        } catch (e: Throwable) {
            Log.e("GameHost", "continue failed", e)
            false
        }
        if (revived) {
            run.overFor = 0f
            try {
                game.revive()
            } catch (e: Throwable) {
                Log.e("GameHost", "revive failed in ${spec.id}", e)
                report()
            }
        } else {
            report()
        }
    }

    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            if (!running.value) {
                snapshotFlow { running.value }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val raw = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                // timestamps can come out of order; never step backwards or too far
                val dt = if (raw.isNaN()) 0f else raw.coerceIn(0f, 1f / 30f)
                if (running.value) step(dt)
                frame.longValue = now
            }
        }
    }

    DisposableEffect(game) {
        onDispose { run.pointer = null }
    }

    val backdrop = remember {
        Brush.verticalGradient(listOf(Color(0xFF140A1E), Color(0xFF07040C)))
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(game) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val scale = min(w / W, h / H)
                        if (!(scale > 0f)) continue
                        val ox = (w - W * scale) / 2f
                        val oy = (h - H * scale) / 2f
                        val live = running.value && !run.reported
                        for (ch in event.changes) {
                            val wx = (ch.position.x - ox) / scale
                            val wy = (ch.position.y - oy) / scale
                            try {
                                if (ch.pressed && !ch.previousPressed) {
                                    // every new finger taps; the first one also drags
                                    if (run.pointer == null) run.pointer = ch.id
                                    if (live) game.down(wx, wy)
                                    ch.consume()
                                } else if (ch.id == run.pointer) {
                                    if (ch.pressed) {
                                        if (live && ch.position != ch.previousPosition) game.move(wx, wy)
                                    } else {
                                        run.pointer = null
                                        if (live) game.up(wx, wy)
                                    }
                                    ch.consume()
                                }
                            } catch (e: Throwable) {
                                Log.e("GameHost", "input failed in ${spec.id}", e)
                            }
                        }
                    }
                }
            },
    ) {
        frame.longValue // redraw every frame
        val k = min(size.width / W, size.height / H)
        drawRect(backdrop)
        if (!(k > 0f)) return@Canvas
        val ox = (size.width - W * k) / 2f
        val oy = (size.height - H * k) / 2f
        withTransform({
            translate(ox, oy)
            scale(k, k, Offset.Zero)
        }) {
            clipRect(0f, 0f, W, H) {
                val native = drawContext.canvas.nativeCanvas
                val saved = native.saveCount
                try {
                    with(game) { draw() }
                } catch (e: Throwable) {
                    Log.e("GameHost", "draw failed in ${spec.id}", e)
                }
                // a game that threw mid-frame may have left saves open
                if (native.saveCount > saved) native.restoreToCount(saved)
            }
        }
    }
}
