package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.economy.House.DEFAULT_HOUSE
import com.ankitsilwal.buttonhatesyou.economy.House.DEFAULT_ROOMS
import com.ankitsilwal.buttonhatesyou.economy.House.HOUSE_ITEM
import com.ankitsilwal.buttonhatesyou.economy.House.ROOMS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The rooms: each has its own wall, floor and furniture.
class HouseTest {
    private val rich = Rules.normalize(START.copy(coins = 99999, diamonds = 50))
    private val own = listOf(
        "toaster", "nightstand-modern", "bed-cozy", "wall-kitchen-sage", "wall-bedroom-plum",
        "plant-cactus", "wall-mint", "rug-round", "floor-kitchen-gold",
    ).fold(rich) { w, id -> w.then(buy(id)) }

    @Test fun `the catalog is consistent`() {
        for (i in House.HOUSE_ITEMS) {
            assertTrue("${i.id} rooms", i.rooms.isNotEmpty() && i.rooms.all { it in ROOMS })
            if (i.kind == "furniture") for (r in i.rooms) assertTrue("${i.id} fits somewhere in $r", i.spots.any { it in ROOMS.getValue(r).spots })
        }
        assertEquals(House.HOUSE_ITEMS.size, HOUSE_ITEM.size)
        // the starting pieces are free (always owned), allowed in their room, and fit their spots
        for (r in listOf("kitchen", "bedroom")) {
            val d = DEFAULT_ROOMS.getValue(r)
            for (id in listOf(d.wall, d.floor) + d.place.values) {
                val item = HOUSE_ITEM[id]
                assertTrue("$r default $id", item != null && item.price == null && r in item.rooms)
            }
            for ((spot, id) in d.place) assertTrue(spot in HOUSE_ITEM.getValue(id).spots && spot in ROOMS.getValue(r).spots)
        }
        assertTrue("the bedroom starts with its bed and lamp", ROOMS.getValue("bedroom").required.all { DEFAULT_ROOMS.getValue("bedroom").place[it] != null })
        assertNull(HOUSE_ITEM.getValue("lamp-bedside").price)
        assertEquals(listOf("living", "kitchen", "bedroom"), House.ROOM_IDS)
    }

    @Test fun `a new player has all three rooms`() {
        assertEquals(mapOf("bed" to "bed-basic", "nightstand" to "lamp-bedside"), START.rooms.getValue("bedroom").place)
        assertEquals("wall-kitchen-tile", START.rooms.getValue("kitchen").wall)
        assertEquals(DEFAULT_HOUSE, START.house)
        assertEquals(300, START.coins)
        assertEquals(Catalog.DEFAULT_LOOK, START.equipped)
    }

    @Test fun `a wallet saved before the other rooms moves its bed to the bedroom`() {
        val saved = Wallet(
            coins = 50,
            owned = listOf("bed-cozy", "plant-cactus", "wall-mint"),
            house = Room("wall-mint", "floor-oak", mapOf("bed" to "bed-cozy", "floor-left" to "plant-cactus")),
        )
        val mig = Rules.normalize(saved)
        assertEquals(Room("wall-mint", "floor-oak", mapOf("floor-left" to "plant-cactus")), mig.house)
        assertEquals(mapOf("bed" to "bed-cozy", "nightstand" to "lamp-bedside"), mig.rooms.getValue("bedroom").place)
        assertEquals(DEFAULT_ROOMS["kitchen"], mig.rooms["kitchen"])
        assertEquals("normalizing twice changes nothing", mig, Rules.normalize(mig))
        assertTrue(Rules.normalize(START.copy(rooms = emptyMap(), house = Room())).rooms.getValue("bedroom").place["bed"] != null)
        assertEquals(
            "a partial room is filled in", "floor-kitchen-checker",
            Rules.normalize(mig.copy(rooms = mapOf("kitchen" to Room(wall = "wall-kitchen-sage")))).rooms.getValue("kitchen").floor,
        )
        // an old outfit (before the 'outfit' slot) gets the slot
        assertEquals("outfit-none", Rules.normalize(Wallet(equipped = mapOf("color" to "color-mint"))).equipped["outfit"])
    }

    @Test fun `furniture goes where its room has a spot for it`() {
        val k = own.then(decor("counter", "toaster", "kitchen"))
        assertEquals("toaster", k.rooms.getValue("kitchen").place["counter"])
        assertEquals("the living room is untouched", own.house, k.house)
        assertEquals("toaster", House.roomOf(k, "kitchen").place["counter"])
        assertNull("no counter in the living room", apply(own, decor("counter", "toaster")))
        assertNull("no counter in the bedroom", apply(own, decor("counter", "toaster", "bedroom")))
        assertNull("a toaster does not go on the floor", apply(own, decor("floor-left", "toaster", "kitchen")))
        assertNull("not owned", apply(rich, decor("counter", "toaster", "kitchen")))
        assertNull(apply(own, decor("floor-left", "disco-ball", "kitchen")))
        assertNull(apply(own, decor("ceiling", "disco-ball", "kitchen")))
        assertNull("no such room", apply(own, decor("rug", "rug-round", "attic")))
        assertNull(apply(own, decor("rug", "rug-round", "constructor")))
        assertNull("no such spot", apply(own, decor("nope", "rug-round", "kitchen")))
    }

    @Test fun `owned pieces are shared, one of a piece per room`() {
        var rugs = own.then(decor("rug", "rug-round"))
        rugs = rugs.then(decor("rug", "rug-round", "kitchen"))
        rugs = rugs.then(decor("rug", "rug-round", "bedroom"))
        assertEquals(
            listOf("rug-round", "rug-round", "rug-round"),
            listOf(rugs.house.place["rug"], rugs.rooms.getValue("kitchen").place["rug"], rugs.rooms.getValue("bedroom").place["rug"]),
        )
        val two = own.then(decor("floor-left", "plant-cactus")).then(decor("floor-right", "plant-cactus"))
        assertEquals(listOf(null, "plant-cactus"), listOf(two.house.place["floor-left"], two.house.place["floor-right"]))
    }

    @Test fun `wallpaper and floors go in the rooms they name`() {
        assertEquals("wall-kitchen-sage", own.then(decor("wall", "wall-kitchen-sage", "kitchen")).rooms.getValue("kitchen").wall)
        assertNull("a kitchen wallpaper is not for the bedroom", apply(own, decor("wall", "wall-kitchen-sage", "bedroom")))
        assertNull(apply(own, decor("wall", "wall-bedroom-plum")))
        assertEquals("wall-bedroom-plum", own.then(decor("wall", "wall-bedroom-plum", "bedroom")).rooms.getValue("bedroom").wall)
        assertEquals("general wallpapers fit every room", "wall-mint", own.then(decor("wall", "wall-mint", "bedroom")).rooms.getValue("bedroom").wall)
        assertEquals("floor-kitchen-gold", own.then(decor("floor", "floor-kitchen-gold", "kitchen")).rooms.getValue("kitchen").floor)
        assertNull("a wall is not a floor", apply(own, decor("floor", "wall-mint", "kitchen")))
        assertNull("not owned", apply(rich, decor("wall", "wall-kitchen-sage", "kitchen")))
        assertNull("nothing", apply(own, decor("wall", null, "kitchen")))
    }

    @Test fun `the bed and its lamp are swapped, never taken down`() {
        assertNull("the bed stays", apply(own, decor("bed", null, "bedroom")))
        assertNull("the lamp stays", apply(own, decor("nightstand", null, "bedroom")))
        assertEquals("bed-cozy", own.then(decor("bed", "bed-cozy", "bedroom")).rooms.getValue("bedroom").place["bed"])
        assertEquals("nightstand-modern", own.then(decor("nightstand", "nightstand-modern", "bedroom")).rooms.getValue("bedroom").place["nightstand"])
        assertNull("not owned", apply(own, decor("bed", "bed-loft", "bedroom")))
        assertNull(apply(own, decor("bed", "toaster", "bedroom")))
        assertNull("beds are for the bedroom", apply(own, decor("bed", "bed-cozy", "living")))
        // an old queued event for the bed (no room) still means the bedroom
        assertEquals("bed-cozy", own.then(decor("bed", "bed-cozy")).rooms.getValue("bedroom").place["bed"])
        // the normal pieces can come down
        val free = own.then(decor("rug", "rug-round", "bedroom"))
        assertNull(free.then(decor("rug", null, "bedroom")).rooms.getValue("bedroom").place["rug"])
    }

    @Test fun `old queued events without a room keep decorating the living room`() {
        val oldDecor = Rules.replay(rich, listOf(buy("plant-cactus"), decor("floor-left", "plant-cactus"), buy("wall-mint"), decor("wall", "wall-mint")))
        assertEquals(listOf("plant-cactus", "wall-mint"), listOf(oldDecor.house.place["floor-left"], oldDecor.house.wall))
        assertEquals(rich.rooms, oldDecor.rooms)
    }

    @Test fun `buying house items and outfits`() {
        val w = START.then(buy("plant-cactus"))
        assertEquals(150, w.coins)
        assertNull("already owned", apply(w, buy("plant-cactus")))
        assertNull("free items are always owned", apply(w, buy("wall-cozy")))
        assertNull("cannot afford", apply(w, buy("wall-space")))
        assertNull(apply(w, buy("nope")))
        val e = START.then(buy("eyes-sleepy"))
        assertEquals(50, e.coins)
        assertEquals("sleepy", Catalog.lookOf(e.then(Event.Equip(nextId(), "eyes", "eyes-sleepy")).equipped).eyes)
        assertNull("not owned", apply(e, Event.Equip(nextId(), "hat", "hat-top")))
        assertNull("wrong slot", apply(e, Event.Equip(nextId(), "hat", "eyes-sleepy")))
        assertEquals("hat-none", e.then(Event.Equip(nextId(), "hat", "hat-none")).equipped["hat"])
    }
}
