package com.ankitsilwal.buttonhatesyou.games

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ankitsilwal.buttonhatesyou.avatar.Look

// Every arcade game draws on a fixed 480x800 portrait world that the host
// scales to fit the screen (letterboxed), like the web version.
const val WORLD_W = 480f
const val WORLD_H = 800f

// One run of a mini-game. The host calls update/draw every frame and feeds it
// touches in world coordinates. When `over` turns true the run ends and
// `score` is what counts (`coins` for games that pay coins picked up).
interface Game {
    val score: Int
    val over: Boolean
    val coins: Int get() = 0

    fun update(dt: Float)
    fun DrawScope.draw()

    fun down(x: Float, y: Float) {}
    fun move(x: Float, y: Float) {}
    fun up(x: Float, y: Float) {}

    // A paid Continue: put the game back in a fair, playable state at the
    // same score. Games that return false never offer one.
    val canRevive: Boolean get() = false
    fun revive() {}
}

// What a game can use from the host: the outfit, sound and vibration.
class GameContext(
    val look: Look,
    val sfx: Sfx,
    val haptics: Haptics,
)

interface Sfx {
    fun play(name: String) // click, hit, pop, win, bad, sob, tick, jump, coin, whoosh
}

interface Haptics {
    fun tap() // a light tick
    fun thud() // a heavy hit
}

// How a game is listed and created. `id` matches the web version's game ids
// (flap, pop, catch, simon, hop, whack, run, stack, slice, redlight, spam,
// fizz, pong, tightrope) so payouts and achievements line up.
class GameSpec(
    val id: String,
    val create: (GameContext) -> Game,
)
