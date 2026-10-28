package com.ankitsilwal.buttonhatesyou.economy

import kotlin.math.floor
import kotlin.math.min

// What's in a daily chest. `food` is a FOODS id (a free snack for the
// pantry), `energy` a full refill, `big` every 7th day of a streak.
data class ChestLoot(
    val coins: Int,
    val diamonds: Int,
    val food: String?,
    val energy: Boolean,
    val big: Boolean,
)

// The chest for today: whether it can be opened, the streak it would make,
// the streak still alive, today's UTC day number and when the next one opens (ms).
data class ChestState(val ready: Boolean, val streak: Int, val alive: Int, val day: Long, val next: Long)

// The daily chest (src/economy/chest.js): once per UTC day, free goodies.
// What's inside comes from a seeded generator, from the wallet's `seed` and
// the day, so it is the same on every device and nobody can pick their own.
// The generator is the web one, bit for bit.
object Chest {
    val coins = 50 to 150 // a normal day: this many coins (in steps of 5)
    val bigCoins = 300 to 500 // every `bigEvery`th day of a streak: a big chest
    const val bigEvery = 7
    const val foodChance = 0.4 // a free snack for the fridge
    const val energyChance = 0.25 // a full energy refill
    const val diamondChance = 0.04 // 1 diamond (rare); a big chest always has one
    const val window = 7 // days: a chest opened offline still counts if it syncs within this many days

    // The coin-priced snacks a chest can hold; a big chest holds the waffle.
    private val SNACKS = House.FOODS.filter { it.price.coins > 0 }
    private const val BIG_SNACK = "food-floppy"

    // FNV-1a over UTF-16 code units (like JS charCodeAt): a string to 32 bits.
    internal fun hash(s: String): Int {
        var h = 0x811c9dc5.toInt()
        for (ch in s) {
            h = h xor ch.code
            h *= 0x01000193
        }
        return h
    }

    // mulberry32: a tiny seeded generator, 0 <= r() < 1.
    internal fun rng(seed: Int): () -> Double {
        var a = seed
        return {
            a += 0x6d2b79f5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
        }
    }

    fun isBigDay(streak: Int) = streak > 0 && streak % bigEvery == 0

    // What the chest for `day` (UTC day number) holds, on day `streak` of a streak.
    fun loot(seed: String?, day: Long, streak: Int): ChestLoot {
        val r = rng(hash("${seed ?: ""}:chest:$day"))
        val big = isBigDay(streak)
        val (lo, hi) = if (big) bigCoins else coins
        // always draw the same numbers in the same order
        val rolls = List(5) { r() }
        val c = lo + floor((rolls[0] * (hi - lo + 5)) / 5).toInt() * 5
        val food = if (big) BIG_SNACK else if (rolls[1] < foodChance) SNACKS[floor(rolls[2] * SNACKS.size).toInt()].id else null
        val energy = big || rolls[3] < energyChance
        val diamonds = if (big || rolls[4] < diamondChance) 1 else 0
        return ChestLoot(min(c, hi), diamonds, food, energy, big)
    }
}
