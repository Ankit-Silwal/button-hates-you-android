package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.avatar.Look
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {
    private val events: List<Event> = listOf(
        run("flap", 15, 40_000, T),
        run("button-run", 4000, 90_000, T + 100_000, extra = 61, continues = 2),
        cont("flap", 1, T),
        claim("reason:1"),
        Event.Reward(nextId(), "please-dont"),
        buy("hat-top"),
        Event.Equip(nextId(), "hat", "hat-top"),
        decor("rug", "rug-round", "kitchen"),
        decor("rug", null),
        feed("food-cookie", T),
        stock("food-usb"),
        sleep(T),
        wake(T, "wake-alarm"),
        wake(T),
        play("adventure", T),
        wash(T),
        achieve("score-flap", 1),
        chest(T),
    )

    @Test fun `events round-trip through JSON`() {
        for (e in events) {
            val json = EconomyJson.encodeToString(Event.serializer(), e)
            assertEquals(json, e, EconomyJson.decodeFromString(Event.serializer(), json))
        }
        val list = EconomyJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Event.serializer()), events)
        assertEquals(events, EconomyJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Event.serializer()), list))
    }

    @Test fun `events use the web's type names`() {
        val json = EconomyJson.parseToJsonElement(EconomyJson.encodeToString(Event.serializer(), cont("pop", 2))).jsonObject
        assertEquals("continue", json["type"]!!.jsonPrimitive.content)
        assertEquals("pop", json["game"]!!.jsonPrimitive.content)
        // a web event decodes (unknown fields ignored)
        val web = """{"id":"abc","type":"run","game":"flap","score":15,"extra":0,"ms":40000,"at":$T,"inflight":true}"""
        assertEquals(Event.Run("abc", "flap", 15, 0, 40_000, T), EconomyJson.decodeFromString(Event.serializer(), web))
        val living = """{"id":"d","type":"decor","spot":"wall","item":"wall-mint"}"""
        assertEquals(Event.Decor("d", "wall", "wall-mint", null), EconomyJson.decodeFromString(Event.serializer(), living))
    }

    @Test fun `a played wallet round-trips through JSON`() {
        val w = Rules.replay(START.copy(coins = 5000, diamonds = 20, seed = "abc"), events)
        assertTrue(w.owned.isNotEmpty() && w.stats.runs > 0 && w.claimed.isNotEmpty())
        val json = EconomyJson.encodeToString(Wallet.serializer(), w)
        assertEquals(w, EconomyJson.decodeFromString(Wallet.serializer(), json))
    }

    @Test fun `an old web wallet loads`() {
        val web = """{"coins":900,"diamonds":7,"owned":["eyes-sleepy"],"equipped":{"color":"color-tomato","eyes":"eyes-sleepy","ears":"ears-none","scarf":"scarf-none","hat":"hat-none"},
            "house":{"wall":"wall-cozy","floor":"floor-oak","place":{"bed":"bed-basic"}},"needs":{"hunger":50,"energy":60.5,"asleep":false,"at":$T},"runs":{"flap":150},"somethingNew":1}"""
        val w = Rules.normalize(EconomyJson.decodeFromString(Wallet.serializer(), web))
        assertEquals(900, w.coins)
        assertEquals("outfit-none", w.equipped["outfit"])
        assertEquals("bed-basic", w.rooms.getValue("bedroom").place["bed"])
        assertEquals(NeedsState(50.0, 60.5, 0.0, false, T), w.needs)
        assertEquals(Look(eyes = "sleepy"), Catalog.lookOf(w.equipped))
    }
}
