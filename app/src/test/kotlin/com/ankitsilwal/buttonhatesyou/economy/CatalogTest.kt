package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.avatar.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun `ids are unique and every slot has a free default`() {
        assertEquals(Catalog.ITEMS.size, Catalog.ITEM.size)
        for (slot in Catalog.SLOTS) {
            val d = Catalog.ITEM.getValue(Catalog.DEFAULT_LOOK.getValue(slot.id))
            assertEquals(slot.id, d.slot)
            assertEquals(null, d.price)
        }
        assertEquals(listOf("color", "outfit", "eyes", "ears", "scarf", "hat"), Catalog.SLOTS.map { it.id })
        val prices = Catalog.ITEMS.mapNotNull { it.price?.coins?.takeIf { c -> c > 0 } }
        assertEquals(250, prices.min())
        assertEquals(1800, prices.max())
    }

    @Test fun `lookOf maps items to the avatar look`() {
        assertEquals(Look(), Catalog.lookOf(Catalog.DEFAULT_LOOK))
        val look = Catalog.lookOf(mapOf("color" to "color-ocean", "outfit" to "outfit-hoodie", "hat" to "hat-cap", "eyes" to "eyes-glasses", "ears" to "ears-cat", "scarf" to "scarf-chain"))
        assertEquals(Look(0xFF5B9BD5, 0xFF3B77AD, "hoodie", "glasses", "cat", "chain", "cap"), look)
        // unknown or mismatched ids fall back to the default
        assertEquals(Look(), Catalog.lookOf(mapOf("color" to "nope", "hat" to "eyes-sleepy")))
        assertEquals(0xFFE2674FL, hex("#e2674f"))
    }

    @Test fun `games and their payouts`() {
        assertEquals(18, Catalog.GAMES.size)
        assertEquals(15, Catalog.ADVENTURE_GAMES.size) // Button Run and the 14 GameSpec ids
        val flap = Catalog.GAME.getValue("flap")
        assertEquals("+25 🪙 per 10 pipes, rising", flap.prize)
        assertEquals("Every 10 pipes pays more: 25, 50, 75… 🪙", flap.rule)
        assertEquals("1 🪙 per coin you grab", Catalog.GAME.getValue("button-run").prize)
        for (g in Catalog.ADVENTURE_GAMES) {
            assertNotNull(g.howTo)
            assertNotNull(g.unit)
            assertNotNull(g.quips)
            assertEquals("adventure", g.category)
        }
        assertEquals(16, Catalog.STORY_REWARDS.size)
        assertEquals(Prize(1500), Catalog.STORY_REWARDS["reason:9"])
        assertTrue(Catalog.GAMES.all { it.color ushr 24 == 0xFFL })
    }
}
