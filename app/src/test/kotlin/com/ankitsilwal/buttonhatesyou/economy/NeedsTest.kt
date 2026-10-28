package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsTest {
    @Test fun `needs drift with the clock like the web version`() {
        // Values printed by the web version's needsAt.
        assertEquals(NeedsState(68.0, 71.0, 4.5, false, T + 3 * HOUR + 1234), Needs.needsAt(NeedsState(80.0, 80.0, 0.0, false, T), T + 3 * HOUR + 1234))
        assertEquals(NeedsState(48.0, 80.0, 10.45, true, T + HOUR), Needs.needsAt(NeedsState(50.0, 30.0, 10.0, true, T), T + HOUR))
        // he wakes up by himself when energy is full (after 1.4 h), then drifts awake
        assertEquals(NeedsState(32.8, 89.2, 16.03, false, T + 5 * HOUR), Needs.needsAt(NeedsState(50.0, 30.0, 10.0, true, T), T + 5 * HOUR))
        // the floor: time alone never drags a need below 15 (or pulls one up)
        assertEquals(NeedsState(15.0, 10.0, 100.0, false, T + 30 * HOUR), Needs.needsAt(NeedsState(20.0, 10.0, 99.0, false, T), T + 30 * HOUR))
        // a new player's clock hasn't started
        assertEquals(Needs.DEFAULT_NEEDS, Needs.needsAt(Needs.DEFAULT_NEEDS, T))
    }

    @Test fun `wakesAt and moods`() {
        assertEquals(T + (1.4 * HOUR).toLong(), Needs.wakesAt(NeedsState(50.0, 30.0, 0.0, true, T)))
        assertNull(Needs.wakesAt(NeedsState(at = T)))
        assertEquals(NeedsMood.Sleep, Needs.moodOf(NeedsState(asleep = true)))
        assertEquals(NeedsMood.Sad, Needs.moodOf(NeedsState(20.0, 20.0)))
        assertEquals(NeedsMood.Hungry, Needs.moodOf(NeedsState(20.0, 50.0)))
        assertEquals(NeedsMood.Tired, Needs.moodOf(NeedsState(50.0, 20.0)))
        assertEquals(NeedsMood.Smug, Needs.moodOf(NeedsState(80.0, 80.0)))
        assertEquals(NeedsMood.Calm, Needs.moodOf(NeedsState(60.0, 60.0)))
    }

    @Test fun `faces for needs`() {
        assertEquals(Face(Mood.Smug, false, 0, 0f), Needs.faceOf(NeedsState(80.0, 80.0, 0.0, false, T), T))
        assertEquals(Face(Mood.Sleep, false, 0, 0.1f), Needs.faceOf(NeedsState(50.0, 30.0, 10.0, true, T), T))
        assertEquals(Face(Mood.Bored, true, 1, 0.5f), Needs.faceOf(NeedsState(20.0, 30.0, 50.0, false, T), T))
        assertEquals(Face(Mood.Calm, false, 2, 0.8f), Needs.faceOf(NeedsState(50.0, 50.0, 80.0, false, T), T))
        // it reads the needs at `now`: three hours later he is no longer smug
        assertEquals(Mood.Calm, Needs.faceOf(NeedsState(72.0, 72.0, 0.0, false, T), T + 3 * HOUR).mood)
    }

    private val T0 = T
    private val base = Rules.normalize(START.copy(coins = 100, needs = NeedsState(hunger = 10.0, energy = 90.0, asleep = false, at = T0)))
    private val fed = base.copy(needs = base.needs.copy(hunger = 50.0))
    private val asleep = fed.copy(needs = fed.needs.copy(energy = 30.0, asleep = true))

    @Test fun `playing needs a fed tummy and energy`() {
        assertNull("too hungry to play", apply(base, play("adventure", T0)))
        assertNotNull(apply(fed, play("adventure", T0)))
        assertNull("too tired", apply(fed.copy(needs = fed.needs.copy(energy = 1.0)), play("adventure", T0)))
        assertNull("story costs more", apply(fed.copy(needs = fed.needs.copy(energy = 4.0)), play("story", T0)))
        assertNull("asleep", apply(asleep, play("adventure", T0)))
        assertNull("unknown kind", apply(fed, play("nope", T0)))
        assertNull("no time", apply(fed, play("adventure", 0)))
        assertEquals(88.0, fed.then(play("adventure", T0)).needs.energy, 0.0)
        assertEquals("hungry", Needs.whyNot(base.needs, "adventure"))
        assertEquals("tired", Needs.whyNot(fed.needs.copy(energy = 1.0), "adventure"))
        assertEquals("asleep", Needs.whyNot(asleep.needs, "adventure"))
        assertNull(Needs.whyNot(fed.needs, "adventure"))
    }

    @Test fun `a paid wake-up wakes him and gives energy back`() {
        assertNull("awake: nothing to wake", apply(fed, wake(T0, "wake-alarm")))
        val up = asleep.then(wake(T0, "wake-alarm"))
        assertFalse(up.needs.asleep)
        assertEquals(42.0, up.needs.energy, 0.0)
        assertEquals(60, up.coins)
        assertNull("unknown wake option", apply(asleep, wake(T0, "nope")))
        assertNull("cannot afford", apply(asleep.copy(coins = 0), wake(T0, "wake-alarm")))
        assertEquals("free wake gives nothing", 30.0, asleep.then(wake(T0)).needs.energy, 0.0)
        assertNull("already asleep", apply(asleep, sleep(T0)))
    }

    @Test fun `games kick up dirt and a filthy button refuses to sleep`() {
        val gw = fed.then(play("adventure", T0))
        assertEquals(1.5, gw.needs.dirt, 0.0)
        val filthy = fed.copy(needs = fed.needs.copy(energy = 40.0, dirt = 80.0))
        assertNull("too dirty to sleep", apply(filthy, sleep(T0)))
        val clean = filthy.then(wash(T0))
        assertEquals(0.0, clean.needs.dirt, 0.0)
        assertEquals(1, clean.stats.washes)
        val slept = clean.then(sleep(T0))
        assertTrue("clean: can sleep", slept.needs.asleep)
        assertEquals(1, slept.stats.naps)
        assertNull("cannot wash a sleeper", apply(asleep, wash(T0)))
        // time alone makes him dirty, slowly (and a wash clears it)
        assertEquals(0.0, fed.copy(needs = fed.needs.copy(dirt = 0.0)).then(wash(T0 + HOUR)).needs.dirt, 0.0)
        assertEquals(1.5, Needs.needsAt(fed.needs, T0 + HOUR).dirt, 0.0)
    }

    @Test fun `events can't go back in time`() {
        // an event earlier than the needs' clock happens at the clock's time
        val w = fed.then(play("adventure", T0 - HOUR))
        assertEquals(T0, w.needs.at)
    }

    @Test fun `stocking the pantry pays and doesn't eat, feeding uses the stock`() {
        var sk = Rules.normalize(START.copy(coins = 100, diamonds = 0))
        sk = sk.then(stock("food-cookie"))
        assertEquals(listOf(85, 1, 0), listOf(sk.coins, sk.pantry["food-cookie"], sk.stats.eaten))
        assertEquals(Rules.normalize(START).needs.hunger, sk.needs.hunger, 0.0) // not eaten
        assertNull("unknown item", apply(sk, stock("food-nope")))
        assertNull("1 diamond, has none", apply(sk, stock("food-floppy")))
        assertNull(apply(sk, stock("__proto__")))
        assertNull("cannot afford", apply(sk.copy(coins = 5), stock("food-cookie")))
        val hungry = sk.copy(needs = sk.needs.copy(hunger = 10.0, at = T))
        val sf = hungry.then(feed("food-cookie", T + 1))
        assertEquals(listOf(85, 0, 1), listOf(sf.coins, sf.pantry["food-cookie"], sf.stats.eaten)) // eaten from the stock: free
        assertEquals(20.0, sf.needs.hunger, 0.01)
        // then a paid one
        val paid = sf.then(feed("food-cookie", T + 2))
        assertEquals(70, paid.coins)
        assertNull("asleep", apply(paid.copy(needs = paid.needs.copy(asleep = true)), feed("food-cookie", T + 3)))
        val full = Rules.normalize(START.copy(coins = 1_000_000, pantry = mapOf("food-cookie" to 98)))
        val c99 = full.then(stock("food-cookie"))
        assertEquals(99, c99.pantry["food-cookie"])
        assertNull("capped at 99", apply(c99, stock("food-cookie")))
        assertEquals(1_000_000 - 15, c99.coins)
    }

    @Test fun `normalize clamps needs`() {
        val n = Needs.normalize(NeedsState(150.0, -3.0, Double.NaN, true, -5))
        assertEquals(NeedsState(100.0, 0.0, 0.0, true, 0), n)
    }
}
