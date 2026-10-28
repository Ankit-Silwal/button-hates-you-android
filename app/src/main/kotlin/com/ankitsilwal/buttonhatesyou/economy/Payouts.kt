package com.ankitsilwal.buttonhatesyou.economy

import kotlin.math.floor

// What Adventure runs pay (src/economy/tiers.js and adventurePayout in rules.js).
object Payouts {
    // Rising tiers: the first `per` units pay `base`, the next `per` pay 2*base,
    // then 3*base, and so on; a partial tier pays its share, rounded down.
    // e.g. per 10, base 25: 10 -> 25, 15 -> 50, 20 -> 75, 30 -> 150, 40 -> 250.
    fun tierPayout(score: Double, per: Int, base: Int): Int {
        val s = if (score.isFinite()) floor(score) else 0.0
        if (s <= 0) return 0
        val n = floor(s / per)
        val part = s - n * per
        return floor((base * (n * (n + 1) * per + 2 * (n + 1) * part)) / (2.0 * per)).toInt()
    }

    // The lowest score that pays at least `payout` (tierPayout only ever rises).
    fun scoreFor(payout: Int, per: Int, base: Int): Int {
        if (payout <= 0) return 0
        var lo = 0
        var hi = per
        while (tierPayout(hi.toDouble(), per, base) < payout) hi *= 2
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (tierPayout(mid.toDouble(), per, base) >= payout) hi = mid else lo = mid + 1
        }
        return lo
    }

    // What an Adventure run is worth before any anti-cheat or daily limits.
    // `extra` is Button Run's coins picked up. 0 for unknown or story games.
    fun adventurePayout(game: GameInfo?, score: Double, extra: Double = 0.0): Int {
        val p = game?.payout ?: return 0
        val coins = if (p.coins) (if (extra.isFinite()) floor(extra).toInt() else 0) else tierPayout(score, p.per, p.base)
        return coins.coerceIn(0, Catalog.RunLimits.maxPayout)
    }

    fun adventurePayout(game: String, score: Double, extra: Double = 0.0): Int =
        adventurePayout(Catalog.GAME[game], score, extra)
}
