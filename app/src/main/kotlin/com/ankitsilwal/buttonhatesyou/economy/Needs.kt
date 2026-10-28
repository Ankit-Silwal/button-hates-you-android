package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// The button's needs (a port of src/economy/needs.js): hunger and energy,
// 0-100 (100 = full), and dirt, 0-100 (100 = filthy; it only goes up with time
// and games, and a wash resets it). Stored as the values at one moment (`at`,
// a ms timestamp); everything after that is computed from the clock, so they
// also drift while the app is closed.
@Serializable
data class NeedsState(
    val hunger: Double = 80.0,
    val energy: Double = 80.0,
    val dirt: Double = 0.0,
    val asleep: Boolean = false,
    val at: Long = 0, // 0: the clock hasn't started (a brand new player)
)

// How the button feels, for its lines (the web's moodOf).
enum class NeedsMood { Sleep, Sad, Hungry, Tired, Smug, Calm }

object Needs {
    const val hungerPerHour = 4.0 // awake: about a day from full to empty
    const val energyPerHour = 3.0 // awake, doing nothing
    const val sleepHungerFactor = 0.5 // asleep, he gets hungry at half speed
    const val sleepEnergyPerHour = 50.0 // asleep: empty to full in 2 hours
    const val floor = 15.0 // time alone never drags a need below this
    const val dirtPerHour = 1.5 // awake, just living
    const val sleepDirtFactor = 0.3 // asleep, he hardly gets dirtier

    // What each kind of game costs to start ("adventure", "story")...
    val PLAY_COST: Map<String, Double> = mapOf("adventure" to 2.0, "story" to 5.0)
    // ...and how much dirt it kicks up.
    val PLAY_DIRT: Map<String, Double> = mapOf("adventure" to 1.5, "story" to 3.0)
    // From here he looks grubby, and from FILTHY_AT he refuses to go to bed.
    const val DIRTY_AT = 40.0
    const val FILTHY_AT = 75.0
    // Playing also takes a full-enough tummy: below this he is too hungry to play.
    const val PLAY_MIN_HUNGER = 20.0
    // Tired eyes show from here down (dark circles under them).
    const val SLEEPY_BELOW = 40.0

    val DEFAULT_NEEDS = NeedsState()

    private const val H = 3_600_000.0

    private fun clamp(v: Double) = min(100.0, max(0.0, v))

    // Like JS Math.round(v * 100) / 100 (halves round up).
    internal fun round2(v: Double) = floor(v * 100 + 0.5) / 100

    // Decay stops at the floor, and never pulls up something already below it.
    private fun decay(v: Double, by: Double) = if (v <= floor) v else max(floor, v - by)

    fun normalize(n: NeedsState?): NeedsState {
        fun num(v: Double?, d: Double) = if (v != null && v.isFinite()) clamp(v) else d
        return NeedsState(
            hunger = num(n?.hunger, DEFAULT_NEEDS.hunger),
            energy = num(n?.energy, DEFAULT_NEEDS.energy),
            dirt = num(n?.dirt, 0.0),
            asleep = n?.asleep == true,
            at = if (n != null && n.at > 0) n.at else 0,
        )
    }

    // The needs as they are at time `t`.
    fun needsAt(n: NeedsState, t: Long): NeedsState {
        if (n.at == 0L || t <= n.at) return n
        var hunger = n.hunger
        var energy = n.energy
        var asleep = n.asleep
        var dirt = n.dirt
        var from = n.at.toDouble()
        if (asleep) {
            // He wakes up on his own the moment energy is full.
            val full = from + ((100 - energy) / sleepEnergyPerHour) * H
            val until = min(t.toDouble(), full)
            val h = (until - from) / H
            hunger = decay(hunger, hungerPerHour * sleepHungerFactor * h)
            dirt = min(100.0, dirt + dirtPerHour * sleepDirtFactor * h)
            energy = min(100.0, energy + sleepEnergyPerHour * h)
            from = until
            if (until >= full) {
                asleep = false
                energy = 100.0
            }
        }
        if (!asleep && t > from) {
            val h = (t - from) / H
            hunger = decay(hunger, hungerPerHour * h)
            energy = decay(energy, energyPerHour * h)
            dirt = min(100.0, dirt + dirtPerHour * h)
        }
        return NeedsState(round2(hunger), round2(energy), round2(dirt), asleep, t)
    }

    // When the next wake-up happens, for a sleeping button (null when awake).
    fun wakesAt(n: NeedsState): Long? =
        if (n.asleep) n.at + (((100 - n.energy) / sleepEnergyPerHour) * H).toLong() else null

    // How the button feels, for its face and its lines.
    fun moodOf(n: NeedsState): NeedsMood = when {
        n.asleep -> NeedsMood.Sleep
        n.hunger < 25 && n.energy < 25 -> NeedsMood.Sad
        n.hunger < 25 -> NeedsMood.Hungry
        n.energy < 25 -> NeedsMood.Tired
        n.hunger > 70 && n.energy > 70 -> NeedsMood.Smug
        else -> NeedsMood.Calm
    }

    // The face for his needs at `now` (src/profile/needs.js faceOf): hungry
    // makes him look bored, low energy gives him dark circles, dirt shows.
    fun faceOf(needs: NeedsState, now: Long): Face {
        val n = needsAt(needs, max(now, needs.at))
        val mood = when {
            n.asleep -> Mood.Sleep
            n.hunger < 25 -> Mood.Bored
            moodOf(n) == NeedsMood.Smug -> Mood.Smug
            else -> Mood.Calm
        }
        val dirty = if (n.dirt >= FILTHY_AT) 2 else if (n.dirt >= DIRTY_AT) 1 else 0
        return Face(mood = mood, sleepy = !n.asleep && n.energy < SLEEPY_BELOW, dirty = dirty, grime = (n.dirt / 100).toFloat())
    }

    // Why a game of `kind` can't start with these needs: "asleep", "tired",
    // "hungry", or null when it can.
    fun whyNot(n: NeedsState, kind: String): String? = when {
        n.asleep -> "asleep"
        n.energy < (PLAY_COST[kind] ?: 0.0) -> "tired"
        n.hunger < PLAY_MIN_HUNGER -> "hungry"
        else -> null
    }
}
