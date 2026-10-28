package com.ankitsilwal.buttonhatesyou.economy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The player's wallet: everything the economy knows about them. It only ever
// changes through events (Rules.applyEvent). The JSON shape matches the web
// version's wallet, so a future cloud sync can share it.
//
//   claimed  story reward ids already paid
//   runs     { game: best payout of a single run }
//   day      today's run coins and play time (the daily caps)
//   legacy   { REWARDS id: times paid } (old events pay a limited number of times)
//   stats    what achievements count; achieved { id: levels claimed }
//   seed     the daily chest's seed; pantry { food: snacks in stock }
//
// Fields missing from an old save get their defaults; Rules.normalize fills
// in the rest (rooms, equipped slots, needs).
@Serializable
data class Wallet(
    val coins: Int = 300, // a little welcome money: enough for one cheap item
    val diamonds: Int = 0,
    val owned: List<String> = emptyList(),
    val equipped: Map<String, String> = Catalog.DEFAULT_LOOK,
    val house: Room = House.DEFAULT_HOUSE, // the living room
    val rooms: Map<String, Room> = emptyMap(), // the kitchen and the bedroom (filled in by normalize)
    val needs: NeedsState = Needs.DEFAULT_NEEDS,
    val stats: Stats = Stats(),
    val achieved: Map<String, Int> = emptyMap(),
    val pantry: Map<String, Int> = emptyMap(),
    val claimed: List<String> = emptyList(),
    val runs: Map<String, Int> = emptyMap(),
    val day: Day? = null,
    val legacy: Map<String, Int> = emptyMap(),
    val seed: String? = null,
)

// Run coins earned and play time on UTC day `n`, and when the last run ended.
@Serializable
data class Day(val n: Long = 0, val coins: Int = 0, val ms: Long = 0, val end: Long = 0)

// What a wallet counts as it is played (see Achievements).
//   best       { game: best score of a run (coins grabbed, for Button Run) }
//   runs       Adventure runs finished (a continued run is one run)
//   runCoins   coins paid by those runs (after the daily cap)
//   eaten      snacks he ate        naps    times he went to bed   washes  showers
//   days       distinct UTC days with a timed event, lastDay the last one
//   chests     daily chests opened, chestDay the day of the last one,
//   streak     consecutive chest days so far, bestStreak the longest
@Serializable
data class Stats(
    val best: Map<String, Int> = emptyMap(),
    val runs: Int = 0,
    val runCoins: Int = 0,
    val eaten: Int = 0,
    val naps: Int = 0,
    val washes: Int = 0,
    val days: Int = 0,
    val lastDay: Long = 0,
    val chests: Int = 0,
    val chestDay: Long = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

// Everything that can happen to a wallet. Each has a unique `id` (for sync);
// timed ones carry `at`, when it happened (ms since the epoch). The JSON
// "type" names match the web version's events.
@Serializable
sealed class Event {
    abstract val id: String

    // A finished Adventure run: its score, `extra` (Button Run: coins picked
    // up), `ms` of play and when it ended. The payout is worked out by Rules.
    // `continues`: how many paid Continues it used (informational).
    @Serializable @SerialName("run")
    data class Run(
        override val id: String,
        val game: String,
        val score: Long,
        val extra: Long = 0,
        val ms: Long,
        val at: Long = 0,
        val continues: Int? = null,
    ) : Event()

    // Paid diamonds for the nth Continue of a run.
    @Serializable @SerialName("continue")
    data class Continue(override val id: String, val game: String, val n: Int, val at: Long = 0) : Event()

    // Finished a story part (an id in STORY_REWARDS); pays once per wallet, ever.
    @Serializable @SerialName("claim")
    data class Claim(override val id: String, val claim: String) : Event()

    // An old fixed payout from REWARDS (only events queued before the rework).
    @Serializable @SerialName("reward")
    data class Reward(override val id: String, val game: String) : Event()

    // Bought an outfit item or a house item.
    @Serializable @SerialName("buy")
    data class Buy(override val id: String, val item: String) : Event()

    // Put an owned outfit item on.
    @Serializable @SerialName("equip")
    data class Equip(override val id: String, val slot: String, val item: String) : Event()

    // Put an owned wallpaper/floor up (spot "wall"/"floor") or a piece of
    // furniture in a spot of `room` (null = the living room); item null takes it down.
    @Serializable @SerialName("decor")
    data class Decor(override val id: String, val spot: String, val item: String?, val room: String? = null) : Event()

    // Ate a food: from the pantry when there is one (free), otherwise bought and eaten.
    @Serializable @SerialName("feed")
    data class Feed(override val id: String, val item: String, val at: Long) : Event()

    // Bought a food for the pantry (not eaten).
    @Serializable @SerialName("stock")
    data class Stock(override val id: String, val item: String) : Event()

    @Serializable @SerialName("sleep")
    data class Sleep(override val id: String, val at: Long) : Event()

    // Got up; `item` (a WAKE_OPTIONS id) is a paid wake-up that gives energy back.
    @Serializable @SerialName("wake")
    data class Wake(override val id: String, val at: Long, val item: String? = null) : Event()

    // Started a game of `kind` ("adventure" or "story"): costs energy, adds dirt.
    @Serializable @SerialName("play")
    data class Play(override val id: String, val kind: String, val at: Long) : Event()

    // Soaped and showered: no more dirt.
    @Serializable @SerialName("wash")
    data class Wash(override val id: String, val at: Long) : Event()

    // Claimed level `level` (1 = Bronze) of an achievement.
    @Serializable @SerialName("achieve")
    data class Achieve(override val id: String, val ach: String, val level: Int) : Event()

    // Opened the daily chest.
    @Serializable @SerialName("chest")
    data class Chest(override val id: String, val at: Long) : Event()
}

// The JSON settings for wallets and events: lenient about unknown fields, so
// web wallets (and newer saves) still load.
val EconomyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}
