package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.economy.Achievements.ACHIEVEMENTS
import com.ankitsilwal.buttonhatesyou.economy.Achievements.newlyReady
import com.ankitsilwal.buttonhatesyou.economy.Achievements.readyCount
import com.ankitsilwal.buttonhatesyou.economy.Achievements.statsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    // A profile from before stats existed.
    private val oldProfile = Wallet(
        coins = 900,
        diamonds = 7,
        owned = listOf("eyes-sleepy", "plant-cactus", "wall-mint"),
        runs = mapOf("flap" to 150, "button-run" to 80),
        claimed = listOf("reason:1", "reason:2", "reason:3", "reason:4", "reason:5", "cursor:1", "cursor:2", "cursor:3", "please-dont"),
    )

    @Test fun `story parts pay once`() {
        var s = START.copy(coins = 0)
        s = s.then(claim("reason:1"))
        assertEquals(200, s.coins)
        assertNull(apply(s, claim("reason:1")))
        s = s.then(claim("reason:9"))
        assertEquals(listOf(1700, 0), listOf(s.coins, s.diamonds)) // finales pay coins; diamonds come from achievements
        assertNull(apply(s, claim("reason:10")))
    }

    @Test fun `old fixed rewards still pay, a limited number of times`() {
        val l = Rules.replay(START.copy(coins = 0), List(3) { Event.Reward(nextId(), "please-dont") })
        assertEquals(listOf(20, 1), listOf(l.coins, l.diamonds))
    }

    @Test fun `runs count into the stats with their clamped score`() {
        var st = START.copy(coins = 0)
        st = st.then(run("flap", 40, 600_000, T))
        assertEquals(0, st.diamonds)
        assertEquals(listOf(1, 250, 40), listOf(st.stats.runs, st.stats.runCoins, st.stats.best["flap"]))
        st = st.then(run("flap", 500, 10_000, T + 700_000)) // cut down to 20
        assertEquals(listOf(2, 40), listOf(st.stats.runs, st.stats.best["flap"]))
        st = st.then(run("button-run", 9000, 90_000, T + 900_000, extra = 61))
        assertEquals(61, st.stats.best["button-run"]) // Button Run's "score" is coins grabbed
        assertEquals(1, st.stats.days) // all on one day
        st = st.then(play("adventure", T + DAY))
        assertEquals(2, st.stats.days)
        st = st.then(feed("food-cookie", T + DAY + 1))
        st = st.then(sleep(T + DAY + 2))
        assertEquals(listOf(1, 1, 2), listOf(st.stats.eaten, st.stats.naps, st.stats.days))
        // a backdated event (with a real clock) doesn't add a day
        val now = T + 2 * DAY
        assertEquals(2, apply(st, wake(T - 30 * DAY), now)?.stats?.days ?: 2)
    }

    @Test fun `an old profile loads and its best payouts become best scores`() {
        val on = Rules.normalize(oldProfile)
        assertEquals(listOf(900, 7, 0), listOf(on.coins, on.diamonds, on.stats.runs))
        assertEquals(emptyMap<String, Int>(), on.achieved)
        assertEquals(emptyMap<String, Int>(), on.pantry)
        val os = statsOf(on)
        assertEquals(listOf(30, 80, 5, 3, 1, 1, 1), listOf(os.best["flap"], os.best["button-run"], os.reason, os.cursor, os.outfits, os.furniture, os.rooms))
    }

    @Test fun `the achievement table is well formed`() {
        assertTrue(ACHIEVEMENTS.size in 15..25)
        assertEquals(ACHIEVEMENTS.size, ACHIEVEMENTS.map { it.id }.toSet().size)
        for (a in ACHIEVEMENTS) {
            assertEquals("${a.id} has 3 levels", Achievements.MEDALS.size, a.levels.size)
            a.levels.forEachIndexed { i, l ->
                assertTrue("${a.id} level ${i + 1}", l.diamonds > 0 && a.goal(l.at).isNotEmpty())
                if (i > 0) assertTrue("${a.id} rises", l.at > a.levels[i - 1].at && l.diamonds >= a.levels[i - 1].diamonds)
            }
        }
        assertEquals("Reach 10 pipes in Flappy Regret", Achievements.ACHIEVEMENT.getValue("score-flap").goal(10))
        assertEquals("Grab 50 coins in one Button Run run", Achievements.ACHIEVEMENT.getValue("score-button-run").goal(50))
        assertEquals("Finish 1 episode of The Reason", Achievements.ACHIEVEMENT.getValue("story-reason").goal(1))
    }

    @Test fun `claiming goes in order, only when the stat is there, once`() {
        var ac = Rules.normalize(oldProfile)
        assertEquals(11, readyCount(ac)) // flap B+S, button-run B, reason B+S, cursor B+S, bookworm B+S, outfits B, rooms B
        assertNull(apply(ac, achieve("story-cursor", 2))) // not in order
        ac = ac.then(achieve("story-cursor", 1))
        ac = ac.then(achieve("story-cursor", 2))
        assertEquals(7 + 1 + 3, ac.diamonds)
        assertNull(apply(ac, achieve("story-cursor", 3))) // 3 of 6 chapters
        for (n in 4..6) ac = ac.then(claim("cursor:$n"))
        ac = ac.then(achieve("story-cursor", 3))
        assertEquals(7 + 1 + 3 + 6, ac.diamonds)
        assertNull(apply(ac, achieve("story-cursor", 3))) // once
        assertNull(apply(ac, achieve("story-cursor", 4))) // no level 4
        assertNull(apply(ac, achieve("story-reason", 2))) // not in order
        assertNull(apply(ac, achieve("score-flap", 2))) // not in order
        assertNull(apply(ac, achieve("nope", 1)))
        assertNull(apply(ac, achieve("__proto__", 1)))
        assertNull(apply(ac, achieve("score-flap", 0)))
        ac = ac.then(achieve("score-flap", 1))
        assertEquals(1, ac.achieved["score-flap"])
    }

    @Test fun `a run that crosses a threshold makes a level ready`() {
        val before = Rules.normalize(START.copy(coins = 0))
        val after = before.then(run("flap", 12, 60_000, T))
        assertEquals(listOf("score-flap"), newlyReady(before, after).map { it.id })
        // a brand new player: a decent first Flappy run and ten runs are a few diamonds
        var np = Rules.normalize(START.copy(coins = 0))
        for (i in 0 until 10) np = np.then(run("flap", 12, 60_000, T + i * 120_000L))
        assertEquals(2, readyCount(np))
    }

    @Test fun `achievement states`() {
        val st = Achievements.states(Rules.normalize(oldProfile)).first { it.a.id == "story-reason" }
        assertEquals(listOf(0, 5, 2), listOf(st.level, st.value, st.claimable))
        assertEquals(Level(1, 1), st.next)
        assertTrue(st.ready)
    }

    @Test fun `diamonds stay scarce but every diamond item is reachable`() {
        val itemDiamonds = Catalog.ITEMS.sumOf { it.price?.diamonds ?: 0 } + House.HOUSE_ITEMS.sumOf { it.price?.diamonds ?: 0 }
        assertTrue(Achievements.ACHIEVEMENT_DIAMONDS > itemDiamonds)
        assertTrue(Achievements.ACHIEVEMENT_DIAMONDS < itemDiamonds * 3)
        assertTrue("diamond items add up to $itemDiamonds", itemDiamonds in 57..168)
    }
}
