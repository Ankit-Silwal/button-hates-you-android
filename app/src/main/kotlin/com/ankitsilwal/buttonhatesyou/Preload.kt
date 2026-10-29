package com.ankitsilwal.buttonhatesyou

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.games.Arcade
import com.ankitsilwal.buttonhatesyou.games.GameContext
import com.ankitsilwal.buttonhatesyou.games.Haptics
import com.ankitsilwal.buttonhatesyou.games.Sfx
import com.ankitsilwal.buttonhatesyou.games.SynthSfx
import com.ankitsilwal.buttonhatesyou.games.WORLD_H
import com.ankitsilwal.buttonhatesyou.games.WORLD_W
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import com.ankitsilwal.buttonhatesyou.ui.art.SceneArt
import com.ankitsilwal.buttonhatesyou.ui.art.gameIcon

/**
 * Loads what the app would otherwise load on first use, while the splash
 * screen is up: the save file (read and replayed), the synthesized sounds,
 * and one off-screen frame of every room prop, house item and game (kept as
 * the game's picture in [thumbs]), which
 * parses their SVG paths into the shared caches, builds the games' sprites and
 * gets their code loaded and warm. After that, rooms and games open without
 * a first-frame hitch.
 *
 * The drawing shares paints and caches with the UI, so it all runs before the
 * UI's first frame: [ready] turns true when it's done (or failed; warming up
 * is only ever an optimisation).
 */
object Preload {
    @Volatile
    var ready = false
        private set

    private var started = false

    /** A still of each game (its id → a portrait picture), drawn during the warm-up. */
    @Volatile
    var thumbs: Map<String, ImageBitmap> = emptyMap()
        private set

    // thumbnails crop the 480x800 world to a 3:4 below its HUD and keep it at this width
    private const val THUMB_W = 300
    private const val THUMB_H = 400
    private const val SHOT_TOP = 130f // below the score and the hint text
    private val TOUCH_TO_START = setOf("slice")
    private var waiting: (() -> Unit)? = null // the latest activity's callback

    /** Starts the warm-up once; [onReady] runs on its thread when done (now, if it already is). */
    fun start(context: Context, onReady: () -> Unit) {
        synchronized(this) {
            if (!ready) {
                waiting = onReady
                if (started) return
                started = true
            }
        }
        if (ready) return onReady()
        val app = context.applicationContext
        Thread({
            val t0 = SystemClock.elapsedRealtime()
            var look = Look()
            step("profile") {
                val store = ProfileStore.get(app)
                look = Catalog.lookOf(store.wallet.value.equipped)
                Achievements.statsOf(store.wallet.value)
                store.currentNeeds()
            }
            step("sounds") { SynthSfx.shared(app) }
            val bmp = ImageBitmap(WORLD_W.toInt(), WORLD_H.toInt())
            val scope = CanvasDrawScope()
            val canvas = Canvas(bmp)
            fun frame(block: DrawScope.() -> Unit) =
                scope.draw(Density(app.resources.displayMetrics.density), LayoutDirection.Ltr, canvas, Size(WORLD_W, WORLD_H), block)

            step("rooms") { frame { sceneProps(this) } }
            step("house") {
                frame {
                    val r = Rect(0f, 0f, 240f, 240f)
                    (House.HOUSE_ITEMS.map { it.id } + Catalog.ITEMS.map { it.id })
                        .filter { HouseArt.has(it) }
                        .forEach { HouseArt.draw(this, it, r) }
                }
            }
            step("games") {
                val quiet = Quiet
                val shots = HashMap<String, ImageBitmap>()
                val k = THUMB_W / WORLD_W
                val top = SHOT_TOP.coerceAtMost(WORLD_H - THUMB_H / k)
                for (spec in Arcade.games + Arcade.pleaseDont) {
                    step("game ${spec.id}") {
                        val game = spec.create(GameContext(look, quiet, quiet))
                        // games that wait for a touch get one, so their picture isn't an empty title
                        if (spec.id in TOUCH_TO_START) {
                            game.down(12f, WORLD_H - 12f)
                            game.up(12f, WORLD_H - 12f)
                        }
                        repeat(if (spec.id in TOUCH_TO_START) 70 else 30) { game.update(1f / 60f) }
                        frame { gameIcon(spec.id) }
                        // the still: the middle of the world, scaled down
                        val shot = ImageBitmap(THUMB_W, THUMB_H)
                        scope.draw(Density(app.resources.displayMetrics.density), LayoutDirection.Ltr, Canvas(shot), Size(THUMB_W.toFloat(), THUMB_H.toFloat())) {
                            withTransform({
                                scale(k, k, Offset.Zero)
                                translate(0f, -top)
                            }) { with(game) { draw() } }
                        }
                        shot.prepareToDraw()
                        shots[spec.id] = shot
                    }
                }
                thumbs = shots
            }
            Log.i("Preload", "warmed up in ${SystemClock.elapsedRealtime() - t0} ms")
            val done = synchronized(this) {
                ready = true
                waiting.also { waiting = null }
            }
            done?.invoke()
        }, "Preload").apply { priority = Thread.MAX_PRIORITY }.start()
    }

    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.w("Preload", "skipped $name", e)
        }
    }

    private fun sceneProps(ds: DrawScope) {
        val r = Rect(0f, 0f, 200f, 200f)
        with(SceneArt) {
            frame(ds, r); clock(ds, r, com.ankitsilwal.buttonhatesyou.ui.common.Clock(10f, 0)); shelf(ds, r); plant(ds, r, 0f)
            sofa(ds, r); floorLamp(ds, r); fridge(ds, r); sink(ds, r); counter(ds, r, 2); stove(ds, r, 0f)
            hood(ds, r); wallShelf(ds, r); table(ds, r); pendant(ds, r, 0f); stringLights(ds, r, 0f)
            wardrobe(ds, r); poster(ds, r); mirror(ds, r); vanity(ds, r); towels(ds, r); duck(ds, r, 0f)
            showerBack(ds, r); showerFront(ds, r, false, 0f); soap(ds, r, false)
        }
    }

    private object Quiet : Sfx, Haptics {
        override fun play(name: String) {}
        override fun tap() {}
        override fun thud() {}
    }
}
