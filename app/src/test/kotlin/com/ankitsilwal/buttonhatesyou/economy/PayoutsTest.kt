package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.economy.Payouts.adventurePayout
import com.ankitsilwal.buttonhatesyou.economy.Payouts.scoreFor
import com.ankitsilwal.buttonhatesyou.economy.Payouts.tierPayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayoutsTest {
    @Test fun `flappy tiers rise`() {
        // 25 for pipes 1-10, 50 more for 11-20, 75 more for 21-30...
        assertEquals(0, tierPayout(0.0, 10, 25))
        assertEquals(2, tierPayout(1.0, 10, 25))
        assertEquals(25, tierPayout(10.0, 10, 25))
        assertEquals(50, tierPayout(15.0, 10, 25))
        assertEquals(75, tierPayout(20.0, 10, 25))
        assertEquals(112, tierPayout(25.0, 10, 25))
        assertEquals(150, tierPayout(30.0, 10, 25))
        assertEquals(250, tierPayout(40.0, 10, 25))
        assertEquals(50, adventurePayout("flap", 15.0))
        assertEquals(Catalog.RunLimits.maxPayout, adventurePayout("flap", 200.0))
        // Button Run: the coins picked up, 1:1
        assertEquals(37, adventurePayout("button-run", 99999.0, 37.0))
        assertEquals(0, adventurePayout("the-reason", 50.0))
        assertEquals(0, adventurePayout("nope", 50.0))
    }

    @Test fun `scoreFor is the lowest paying score`() {
        assertEquals(15, scoreFor(50, 10, 25))
        assertEquals(30, scoreFor(150, 10, 25))
        assertEquals(0, scoreFor(0, 10, 25))
    }

    @Test fun `the web README payout table`() {
        // From `node scripts/check-economy.mjs`.
        assertEquals(listOf(25, 75, 150), listOf(10, 20, 30).map { adventurePayout("flap", it.toDouble()) })
        assertEquals(listOf(25, 75, 150), listOf(1000, 2000, 3000).map { adventurePayout("pop", it.toDouble()) })
        assertEquals(listOf(25, 75, 150), listOf(400, 800, 1200).map { adventurePayout("tightrope", it.toDouble()) })
    }

    @Test fun `a run pays through applyEvent and remembers the best`() {
        var w = START.copy(coins = 0)
        w = w.then(run("flap", 15, 40_000, T))
        assertEquals(50, w.coins)
        assertEquals(50, w.runs["flap"])
        w = w.then(run("button-run", 4000, 90_000, T + 100_000, extra = 61))
        assertEquals(111, w.coins)
    }

    @Test fun `a score the play time can't explain is cut down`() {
        val fast = START.copy(coins = 0).then(run("flap", 500, 10_000, T))
        assertEquals(tierPayout(15.0 + 5, 10, 25), fast.coins) // 1.5 pipes/s * 10s + half a tier of slack
    }

    @Test fun `runs can't overlap`() {
        var o = START.copy(coins = 0).then(run("simon", 10, 60_000, T))
        val o1 = o.coins
        o = o.then(run("simon", 10, 60_000, T + 1_000))
        assertTrue("an overlapping run is clipped", o.coins - o1 < o1)
    }

    @Test fun `junk is rejected or paid nothing`() {
        assertNull(apply(START, run("nope", 9, 0, T)))
        assertEquals(0, START.copy(coins = 0).then(run("flap", 0, -5, T)).coins)
        assertNull(apply(START, run("the-reason", 9, 1000, T)))
    }

    @Test fun `the daily soft cap`() {
        var d = START.copy(coins = 0)
        for (i in 0 until 20) d = d.then(run("flap", 40, 600_000, T + i * 700_000L))
        assertEquals(20 * 250, d.day!!.coins)
        assertEquals(3000 + (250 * 0.25).toInt() * 8, d.coins)
    }

    @Test fun `continues cost 1, 2, then 3 diamonds`() {
        assertEquals(listOf(1, 2, 3, null, 3), listOf(Rules.continueCost(1), Rules.continueCost(2), Rules.continueCost(3), Rules.continueCost(4), Rules.MAX_CONTINUES))
        var c = START.copy(coins = 0, diamonds = 7)
        c = c.then(cont("flap", 1, T))
        c = c.then(cont("flap", 2, T + 1))
        assertEquals(4, c.diamonds)
        assertNull("can't afford", apply(c.copy(diamonds = 2), cont("flap", 3)))
        assertNull("at most 3", apply(c, cont("flap", 4)))
        for (n in listOf(0, -1)) assertNull(apply(c, cont("flap", n)))
        assertNull(apply(c, cont("nope", 1)))
        assertNull("Adventure games only", apply(c, cont("the-reason", 1)))
        // replayed like any other event: the queue can't overspend
        assertEquals(0, Rules.replay(START.copy(diamonds = 3), listOf(1, 2, 3).map { cont("pop", it) }).diamonds)
    }

    @Test fun `a continued run pays by its final score over all its play time`() {
        // 3 segments of 20 s with 12 pipes each still fit the 1.5 pipes/s cap
        val ev = run("flap", 36, 60_000, T, continues = 2)
        assertEquals(adventurePayout("flap", 36.0), START.copy(coins = 0).then(ev).coins)
        assertEquals(adventurePayout("flap", 36.0), START.copy(coins = 0).then(ev.copy(continues = null)).coins)
    }
}
