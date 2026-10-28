package com.ankitsilwal.buttonhatesyou.data

import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.economy.Event
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Rules
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val T = 1_793_188_800_000L // Date.UTC(2026, 9, 28, 12)
    private var clock = T
    private fun file() = File(tmp.root, "profile.json")
    private fun store() = ProfileStore(file()) { clock }

    @Test fun `a new player starts with the START wallet and a chest seed`() {
        val s = store()
        assertEquals(300, s.wallet.value.coins)
        assertNotNull(s.wallet.value.seed)
        assertEquals(Rules.START.copy(seed = s.wallet.value.seed), s.wallet.value)
        assertFalse(file().exists()) // nothing saved until something happens
    }

    @Test fun `actions apply, save, and queue events`() {
        val s = store()
        assertTrue(s.buy("eyes-sleepy"))
        assertFalse("can't afford", s.buy("hat-crown"))
        assertTrue(s.equip("eyes", "eyes-sleepy"))
        assertEquals("sleepy", s.look().eyes)
        assertTrue(s.decorate("kitchen", "rug", null))
        assertEquals(50, s.wallet.value.coins)
        assertEquals(3, s.outbox.size)

        // a fresh store reads the same profile back
        val again = store()
        assertEquals(s.wallet.value, again.wallet.value)
        assertEquals(s.outbox, again.outbox)
        assertFalse(File(file().path + ".tmp").exists())
    }

    @Test fun `only the last change per slot stays queued`() {
        val s = store()
        s.equip("hat", "hat-none")
        s.equip("hat", "hat-none")
        s.equip("eyes", "eyes-classic")
        assertEquals(2, s.outbox.size)
        assertEquals(listOf("hat", "eyes"), s.outbox.map { (it as Event.Equip).slot })
    }

    @Test fun `runs, continues, story, achievements`() {
        val s = store()
        val paid = s.recordRun("flap", score = 12, ms = 60_000)
        assertNotNull(paid)
        assertEquals(35, paid!!.coins)
        assertEquals(listOf("score-flap"), paid.ready.map { it.id })
        assertNull(s.recordRun("nope", 5, ms = 1000))
        assertFalse("no diamonds", s.continueRun("flap", 1))
        assertEquals(200, s.claimStory("cursor:1")!!.coins)
        assertNull(s.claimStory("cursor:1"))
        assertEquals(1, s.claimAchievement("score-flap", 1)!!.diamonds)
        assertTrue(s.continueRun("flap", 1))
        assertEquals(1, s.claimAchievement("story-cursor", 1)!!.diamonds)
        assertNull(s.claimAchievement("story-cursor", 1))
    }

    @Test fun `needs, sleep and the clock`() {
        val s = store()
        assertTrue(s.play("adventure")) // starts the needs clock
        assertEquals(T, s.wallet.value.needs.at)
        assertEquals(78.0, s.currentNeeds().energy, 0.0)
        clock += 2 * 3_600_000L
        assertEquals(72.0, s.currentNeeds().energy, 0.0)
        assertTrue(s.sleep())
        assertFalse(s.sleep())
        assertEquals(Mood.Sleep, s.face().mood)
        assertEquals("asleep", s.whyNot("adventure"))
        assertFalse(s.wash())
        clock += 1_000
        assertTrue(s.wake("wake-alarm"))
        assertEquals(260, s.wallet.value.coins)
        assertTrue(s.wash())
        assertTrue(s.stockFood("food-cookie"))
        assertTrue(s.feed("food-cookie"))
        assertEquals(0, s.wallet.value.pantry["food-cookie"])
        assertEquals(245, s.wallet.value.coins)
    }

    @Test fun `the daily chest opens once a day`() {
        val s = store()
        assertTrue(s.chest().ready)
        val opened = s.openChest()
        assertNotNull(opened)
        assertEquals(1, opened!!.streak)
        assertEquals(300 + opened.loot.coins, s.wallet.value.coins)
        assertNull(s.openChest())
        clock += 86_400_000L
        assertEquals(2, s.openChest()!!.streak)
    }

    @Test fun `the needs ticker emits the needs now`() = runBlocking {
        val s = store()
        s.play("adventure")
        clock += 3_600_000L
        val n = NeedsTicker(s).first()
        assertEquals(NeedsState(76.0, 75.0, 3.0, false, clock), n)
        // a change to the wallet's needs is emitted right away
        val two = async { NeedsTicker(s).take(2).toList() }
        kotlinx.coroutines.yield()
        s.wash()
        assertEquals(0.0, two.await()[1].dirt, 0.0)
    }

    @Test fun `an unreadable file is kept aside`() {
        file().writeText("{ not json")
        val s = store()
        assertEquals(300, s.wallet.value.coins)
        assertTrue(File(file().path + ".bad").exists())
    }
}

