package com.ankitsilwal.buttonhatesyou.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestTest {
    private val D = T / DAY
    private fun at(d: Int, h: Int = 12) = (D + d) * DAY + h * HOUR

    @Test fun `the same seed and day give the same loot`() {
        assertEquals(Chest.loot("abc", D, 1), Chest.loot("abc", D, 1))
    }

    @Test fun `the generator matches the web one bit for bit`() {
        // Values printed by the web version's chestLoot.
        assertEquals(ChestLoot(80, 0, null, false, false), Chest.loot("abc", 20389, 1))
        assertEquals(ChestLoot(90, 0, "food-battery", true, false), Chest.loot("abc", 20390, 2))
        assertEquals(ChestLoot(355, 1, "food-floppy", true, true), Chest.loot("abc", 20389, 7))
        assertEquals(ChestLoot(135, 0, null, false, false), Chest.loot("seed-3", 20500, 1))
        assertEquals(ChestLoot(105, 0, null, false, false), Chest.loot("", 20000, 1))
        assertEquals(ChestLoot(135, 0, "food-pixels", false, false), Chest.loot(null, 20001, 3))
        assertEquals(ChestLoot(115, 0, "food-gluedog", false, false), Chest.loot("ünï", 20002, 1))
    }

    @Test fun `loot odds over a thousand days`() {
        var food = 0
        var energy = 0
        var diamonds = 0
        for (d in 0 until 1000) {
            val l = Chest.loot("seed-${d % 7}", D + d, 1)
            assertTrue(l.coins >= Chest.coins.first && l.coins <= Chest.coins.second && l.coins % 5 == 0)
            if (l.food != null) food++
            if (l.energy) energy++
            diamonds += l.diamonds
        }
        assertTrue("$food $energy $diamonds", food in 301..499 && energy in 171..329 && diamonds in 16..69)
        val big = Chest.loot("abc", D, 7)
        assertTrue(big.big && big.diamonds == 1 && big.coins >= Chest.bigCoins.first && big.food == "food-floppy" && big.energy)
    }

    @Test fun `once a day, streaks, and the big chest`() {
        var ch = Rules.normalize(START.copy(coins = 0, seed = "abc"))
        ch = ch.then(chest(at(0)), at(0, 13))
        val l0 = Chest.loot("abc", D, 1)
        assertEquals(listOf(l0.coins, l0.diamonds, 1, 1), listOf(ch.coins, ch.diamonds, ch.stats.chests, ch.stats.streak))
        if (l0.food != null) assertEquals(1, ch.pantry[l0.food])
        assertNull("once a day", apply(ch, chest(at(0, 20)), at(0, 21)))
        assertNull("the clock decides the day", apply(ch, chest(at(1)), at(0, 21)))
        assertFalse(Rules.chestFor(ch, at(0, 22)).ready)
        assertTrue(Rules.chestFor(ch, at(1)).ready)
        assertEquals(2, Rules.chestFor(ch, at(1)).streak)
        for (d in 1 until 7) ch = ch.then(chest(at(d)), at(d, 13))
        assertEquals(listOf(7, 7, 7), listOf(ch.stats.streak, ch.stats.bestStreak, ch.stats.chests))
        assertTrue((ch.pantry["food-floppy"] ?: 0) >= 1) // day 7: the big chest
        ch = ch.then(chest(at(9)), at(9, 13)) // missed a day: the streak starts again
        assertEquals(listOf(1, 7), listOf(ch.stats.streak, ch.stats.bestStreak))
        assertEquals(0, Rules.chestFor(ch, at(11)).alive)
        assertEquals(1, Rules.chestFor(ch, at(10)).alive)
    }

    @Test fun `the energy refill fills energy`() {
        // "abc" on day 20390 (streak 2) holds an energy refill
        val day = 20390L
        val t = day * DAY + 12 * HOUR
        val w = Rules.normalize(START.copy(seed = "abc", stats = Stats(chestDay = day - 1, streak = 1), needs = NeedsState(energy = 30.0, at = t)))
        assertEquals(100.0, w.then(chest(t), t + 1).needs.energy, 0.0)
    }

    @Test fun `the seed comes from the wallet, and backdated chests are refused`() {
        val l0 = Chest.loot("abc", D, 1)
        val seeded = Rules.normalize(START.copy(coins = 0, seed = "abc")).then(chest(at(0)), at(0, 13))
        assertEquals(l0.coins, seeded.coins)
        assertNull(apply(Rules.normalize(START.copy(seed = "abc")), chest(at(-30)), at(0)))
    }

    @Test fun `a snack from the chest is eaten for free`() {
        var pa = Rules.normalize(START.copy(coins = 0, pantry = mapOf("food-usb" to 1)))
        pa = pa.then(feed("food-usb", T))
        assertEquals(listOf(0, 0), listOf(pa.coins, pa.pantry["food-usb"]))
        assertNull("none left and no coins", apply(pa, feed("food-usb", T + 1)))
    }
}
