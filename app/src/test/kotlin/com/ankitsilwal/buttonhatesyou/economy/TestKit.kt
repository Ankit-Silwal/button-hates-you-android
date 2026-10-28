package com.ankitsilwal.buttonhatesyou.economy

// Shared bits for the economy tests (a port of scripts/check-economy.mjs).

// Date.UTC(2026, 9, 28, 12)
const val T = 1_793_188_800_000L
const val HOUR = 3_600_000L
const val DAY = 86_400_000L

private var counter = 0
fun nextId() = "e${counter++}"

fun apply(w: Wallet, e: Event, now: Long = Long.MAX_VALUE): Wallet? = Rules.applyEvent(w, e, now)

// Applies an event that must be allowed.
fun Wallet.then(e: Event, now: Long = Long.MAX_VALUE): Wallet =
    Rules.applyEvent(this, e, now) ?: throw AssertionError("refused: $e")

fun run(game: String, score: Long, ms: Long, at: Long, extra: Long = 0, continues: Int? = null) =
    Event.Run(nextId(), game, score, extra, ms, at, continues)

fun buy(item: String) = Event.Buy(nextId(), item)
fun claim(id: String) = Event.Claim(nextId(), id)
fun achieve(ach: String, level: Int) = Event.Achieve(nextId(), ach, level)
fun chest(at: Long) = Event.Chest(nextId(), at)
fun feed(item: String, at: Long) = Event.Feed(nextId(), item, at)
fun stock(item: String) = Event.Stock(nextId(), item)
fun play(kind: String, at: Long) = Event.Play(nextId(), kind, at)
fun sleep(at: Long) = Event.Sleep(nextId(), at)
fun wake(at: Long, item: String? = null) = Event.Wake(nextId(), at, item)
fun wash(at: Long) = Event.Wash(nextId(), at)
fun cont(game: String, n: Int, at: Long = T) = Event.Continue(nextId(), game, n, at)
fun decor(spot: String, item: String?, room: String? = null) = Event.Decor(nextId(), spot, item, room)

val START get() = Rules.START
