package com.ankitsilwal.buttonhatesyou.economy

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// The rules (a port of src/economy/rules.js). A wallet only ever changes
// through events, and `applyEvent` is the single place that decides whether
// one is allowed and what it does. It is the same function the web version
// and its server run, so a replayed event queue gives the same wallet.
object Rules {
    const val DAY_MS = 86_400_000L
    const val STOCK_MAX = 99

    // The nth Continue of a run costs CONTINUE_COST[n - 1] diamonds.
    val CONTINUE_COST = listOf(1, 2, 3)
    val MAX_CONTINUES = CONTINUE_COST.size
    fun continueCost(n: Int): Int? = if (n >= 1) CONTINUE_COST.getOrNull(n - 1) else null

    // A new player's wallet.
    val START: Wallet = normalize(Wallet())

    fun owns(wallet: Wallet, item: Priced) = item.price == null || item.id in wallet.owned

    fun canAfford(wallet: Wallet, item: Priced) =
        wallet.coins >= (item.price?.coins ?: 0) && wallet.diamonds >= (item.price?.diamonds ?: 0)

    // The new wallet, or null when the event is not allowed. `now` caps event
    // times (a server would pass its own clock; the app leaves it open).
    fun applyEvent(wallet: Wallet, e: Event, now: Long = Long.MAX_VALUE): Wallet? {
        val w = normalize(wallet)
        val next = applyAny(w, e, now) ?: return null
        return countDay(next, e, now) // see "Achievements" below
    }

    // A queue of events on top of a wallet; refused events are skipped.
    fun replay(wallet: Wallet, events: List<Event>, now: Long = Long.MAX_VALUE): Wallet =
        events.fold(normalize(wallet)) { w, e -> applyEvent(w, e, now) ?: w }

    private fun applyAny(w: Wallet, e: Event, now: Long): Wallet? = when (e) {
        is Event.Achieve -> applyAchieve(w, e)
        is Event.Chest -> applyChest(w, e, now)
        is Event.Buy -> applyBuy(w, e)
        is Event.Equip -> {
            val item = Catalog.ITEM[e.item]
            if (item == null || item.slot != e.slot || !owns(w, item)) null
            else w.copy(equipped = w.equipped + (item.slot to item.id))
        }
        is Event.Decor -> applyDecor(w, e)
        is Event.Stock -> applyStock(w, e)
        is Event.Feed -> applyFeed(w, e, now)
        is Event.Sleep -> applySleepWake(w, true, e.at, null, now)
        is Event.Wake -> applySleepWake(w, false, e.at, e.item, now)
        is Event.Play -> applyPlay(w, e, now)
        is Event.Wash -> {
            val needs = needsFor(w, e.at, now)
            if (needs == null || needs.asleep) null
            else w.copy(needs = needs.copy(dirt = 0.0), stats = w.stats.copy(washes = w.stats.washes + 1))
        }
        is Event.Continue -> applyContinue(w, e)
        is Event.Run -> applyRun(w, e, now)
        is Event.Claim -> applyClaim(w, e)
        is Event.Reward -> applyLegacyReward(w, e)
    }

    /* ====================== the economy: what things pay ====================== */

    private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
    private fun clamp(v: Long, lo: Long, hi: Long) = min(hi, max(lo, v))
    private fun clamp(v: Int, lo: Int, hi: Int) = min(hi, max(lo, v))
    private fun add(w: Wallet, coins: Int, diamonds: Int) = w.copy(coins = w.coins + coins, diamonds = w.diamonds + diamonds)
    private fun pay(w: Wallet, price: Price?) =
        w.copy(coins = w.coins - (price?.coins ?: 0), diamonds = w.diamonds - (price?.diamonds ?: 0))

    // The anti-cheat part. The score is taken on trust, but only up to what the
    // play time allows (`rate` per second, plus a little slack), play time is
    // capped per run and per day, and run coins past `dailyFull` a day only pay
    // `overCap` of their worth. Days only move forward, and never past `now`.
    private fun applyRun(w: Wallet, e: Event.Run, now: Long): Wallet? {
        val game = Catalog.GAME[e.game] ?: return null
        val p = game.payout ?: return null
        val L = Catalog.RunLimits
        val prev = w.day ?: Day()
        val at = max(min(if (e.at > 0) e.at else System.currentTimeMillis(), now), prev.end)
        val n = max(prev.n, at / DAY_MS)
        val day = if (n == prev.n) prev else Day(n, 0, 0, 0)

        // Runs can't overlap: a run's play time fits between the end of the last
        // run and this one's end.
        val room = minOf(L.maxRunMs, DAY_MS - day.ms, at - prev.end)
        val ms = clamp(e.ms, 0, room)
        val most = p.rate * (ms / 1000.0) + (if (p.coins) 10.0 else p.per / 2.0)
        val score = clamp(e.score.toDouble(), 0.0, most)
        val extra = clamp(e.extra.toDouble(), 0.0, most)
        val raw = Payouts.adventurePayout(game, score, extra)

        val full = clamp(L.dailyFull - day.coins, 0, raw)
        val coins = full + floor((raw - full) * L.overCap).toInt()
        val best = w.runs[game.id] ?: 0

        // Runs pay coins only: diamonds come from achievements.
        return add(w, coins, 0).copy(
            runs = w.runs + (game.id to max(best, raw)),
            day = Day(n, day.coins + raw, day.ms + ms, at),
            stats = countRun(w.stats, game.id, if (p.coins) extra else score, coins),
        )
    }

    // Story parts pay once per wallet.
    private fun applyClaim(w: Wallet, e: Event.Claim): Wallet? {
        val prize = Catalog.STORY_REWARDS[e.claim] ?: return null
        if (e.claim in w.claimed) return null
        return add(w, prize.coins, prize.diamonds).copy(claimed = w.claimed + e.claim)
    }

    // Old fixed rewards: still paid at the old amounts, but only `max` times each.
    private fun applyLegacyReward(w: Wallet, e: Event.Reward): Wallet? {
        val prize = Catalog.REWARDS[e.game] ?: return null
        val times = w.legacy[e.game] ?: 0
        if (times >= prize.max) return null
        return add(w, prize.coins, prize.diamonds).copy(legacy = w.legacy + (e.game to times + 1))
    }

    private fun applyBuy(w: Wallet, e: Event.Buy): Wallet? {
        val item: Priced = House.HOUSE_ITEM[e.item] ?: Catalog.ITEM[e.item] ?: return null
        if (owns(w, item) || !canAfford(w, item)) return null
        return pay(w, item.price).copy(owned = w.owned + item.id)
    }

    /* ====================== the house and the button's needs ====================== */

    // Wallets saved before the house (or before some fields) existed: fill in
    // what's missing. Normalizing twice changes nothing.
    fun normalize(w: Wallet): Wallet {
        // Before the other rooms existed the house had a bed in it: that bed
        // moves to the bedroom, and the living room goes without one.
        val bed = w.house.place["bed"]
        fun room(id: String): Room {
            val d = House.DEFAULT_ROOMS.getValue(id)
            val r = w.rooms[id]
            return Room(
                wall = r?.wall?.ifBlank { null } ?: d.wall,
                floor = r?.floor?.ifBlank { null } ?: d.floor,
                place = d.place + (r?.place ?: emptyMap()),
            )
        }
        var bedroom = room("bedroom")
        if (w.rooms.isEmpty() && bed != null && bed in House.HOUSE_ITEM) bedroom = bedroom.copy(place = bedroom.place + ("bed" to bed))
        return w.copy(
            equipped = Catalog.DEFAULT_LOOK + w.equipped,
            house = Room(
                wall = w.house.wall.ifBlank { House.DEFAULT_HOUSE.wall },
                floor = w.house.floor.ifBlank { House.DEFAULT_HOUSE.floor },
                place = w.house.place - "bed",
            ),
            rooms = linkedMapOf("kitchen" to room("kitchen"), "bedroom" to bedroom),
            needs = Needs.normalize(w.needs),
        )
    }

    // The needs at an event's time. Time only moves forward: an event can't be
    // earlier than the last one, nor later than `now`.
    private fun needsFor(w: Wallet, at: Long, now: Long): NeedsState? {
        if (at <= 0) return null
        val t = max(min(at, now), w.needs.at)
        return Needs.needsAt(w.needs, t).copy(at = t) // a new player's clock starts here
    }

    // A wallpaper/floor (spot "wall"/"floor") or a piece of furniture in a spot
    // of a room. The piece must be allowed in that room and the spot must
    // exist there; the bedroom's bed and lamp can be swapped, never taken down.
    // (An old event for the "bed" spot with no room means the bedroom.)
    private fun applyDecor(w: Wallet, e: Event.Decor): Wallet? {
        val id = e.room ?: if (e.spot == "bed") "bedroom" else "living"
        val cfg = House.ROOMS[id] ?: return null
        val here = House.roomOf(w, id)
        fun put(next: Room) = if (id == "living") w.copy(house = next) else w.copy(rooms = w.rooms + (id to next))
        val item = e.item?.let { House.HOUSE_ITEM[it] }
        if (e.spot == "wall" || e.spot == "floor") {
            if (item == null || item.kind != e.spot || id !in item.rooms || !owns(w, item)) return null
            return put(if (e.spot == "wall") here.copy(wall = item.id) else here.copy(floor = item.id))
        }
        if (e.spot !in House.SPOT || e.spot !in cfg.spots) return null
        val place = LinkedHashMap(here.place)
        if (e.item == null) {
            place.remove(e.spot)
        } else {
            if (item == null || item.kind != "furniture" || e.spot !in item.spots || id !in item.rooms || !owns(w, item)) return null
            place.values.removeAll { it == item.id } // only one of each in a room
            place[e.spot] = item.id
        }
        if (!cfg.required.all { place[it] != null }) return null // the bed and its lamp stay
        return put(here.copy(place = place))
    }

    // Bought a food for the pantry: pays its price, pantry + 1 (at most STOCK_MAX).
    private fun applyStock(w: Wallet, e: Event.Stock): Wallet? {
        val food = House.FOOD[e.item] ?: return null
        val have = w.pantry[food.id] ?: 0
        if (!canAfford(w, food) || have >= STOCK_MAX) return null
        return pay(w, food.price).copy(pantry = w.pantry + (food.id to have + 1))
    }

    // Ate a food: from the pantry when there is one (free), otherwise bought.
    private fun applyFeed(w: Wallet, e: Event.Feed, now: Long): Wallet? {
        val food = House.FOOD[e.item] ?: return null
        val needs = needsFor(w, e.at, now) ?: return null
        val free = w.pantry[food.id] ?: 0
        if (needs.asleep || (free == 0 && !canAfford(w, food))) return null
        val paid = if (free > 0) w.copy(pantry = w.pantry + (food.id to free - 1)) else pay(w, food.price)
        return paid.copy(
            stats = w.stats.copy(eaten = w.stats.eaten + 1),
            needs = needs.copy(
                hunger = Needs.round2(min(100.0, needs.hunger + food.hunger)),
                energy = Needs.round2(min(100.0, needs.energy + food.energy)),
            ),
        )
    }

    // Went to bed (not when FILTHY_AT or dirtier), or got up; a paid wake-up
    // (`item`, a WAKE_OPTIONS id) also gives some energy back.
    private fun applySleepWake(w: Wallet, sleep: Boolean, at: Long, item: String?, now: Long): Wallet? {
        val needs = needsFor(w, at, now) ?: return null
        if (needs.asleep == sleep) return null
        if (sleep && needs.dirt >= Needs.FILTHY_AT) return null // clean first
        if (!sleep && item != null) {
            val opt = House.WAKE_OPTION[item] ?: return null
            if (!canAfford(w, opt)) return null
            return pay(w, opt.price).copy(
                needs = needs.copy(asleep = false, energy = Needs.round2(min(100.0, needs.energy + opt.energy))),
            )
        }
        val naps = w.stats.naps + if (sleep) 1 else 0
        return w.copy(needs = needs.copy(asleep = sleep), stats = w.stats.copy(naps = naps))
    }

    // Started a game: costs PLAY_COST[kind] energy and adds PLAY_DIRT[kind] dirt.
    private fun applyPlay(w: Wallet, e: Event.Play, now: Long): Wallet? {
        val cost = Needs.PLAY_COST[e.kind] ?: return null
        val needs = needsFor(w, e.at, now) ?: return null
        if (needs.asleep || needs.energy < cost || needs.hunger < Needs.PLAY_MIN_HUNGER) return null
        return w.copy(
            needs = needs.copy(
                energy = Needs.round2(needs.energy - cost),
                dirt = Needs.round2(min(100.0, needs.dirt + Needs.PLAY_DIRT.getValue(e.kind))),
            ),
        )
    }

    /* ====================== Continue with diamonds (Adventure) ====================== */

    // Only spends; the run event at the end still pays by the final score.
    private fun applyContinue(w: Wallet, e: Event.Continue): Wallet? {
        val game = Catalog.GAME[e.game]
        val cost = continueCost(e.n)
        if (game?.payout == null || cost == null || w.diamonds < cost) return null
        return w.copy(diamonds = w.diamonds - cost)
    }

    /* ====================== achievements, stats and the daily chest ====================== */

    // A run counted into the stats: one more run, the coins it paid, and the
    // game's best score (coins grabbed for Button Run), as applyRun clamped it.
    private fun countRun(stats: Stats, game: String, score: Double, coins: Int) = stats.copy(
        best = stats.best + (game to max(stats.best[game] ?: 0, floor(score).toInt())),
        runs = stats.runs + 1,
        runCoins = stats.runCoins + coins,
    )

    // When a timed event happened, for the stats.
    private fun timeOf(e: Event): Long? = when (e) {
        is Event.Run -> e.at
        is Event.Feed -> e.at
        is Event.Sleep -> e.at
        is Event.Wake -> e.at
        is Event.Play -> e.at
        is Event.Wash -> e.at
        is Event.Chest -> e.at
        else -> null
    }

    // Capped at `now`, or null when it has no time or (with a real `now`) is
    // too old to count, so nobody can backdate a year of daily chests.
    private fun eventTime(at: Long, now: Long): Long? {
        if (at <= 0) return null
        val t = min(at, now)
        if (now != Long.MAX_VALUE && t < now - Chest.window * DAY_MS) return null
        return t
    }

    // Days played: an accepted timed event on a later UTC day than the last one counted.
    private fun countDay(w: Wallet, e: Event, now: Long): Wallet {
        val t = eventTime(timeOf(e) ?: return w, now) ?: return w
        val day = t / DAY_MS
        if (day <= w.stats.lastDay) return w
        return w.copy(stats = w.stats.copy(days = w.stats.days + 1, lastDay = day))
    }

    // Claims level `level` of an achievement: only the next unclaimed one, and
    // only once its stat is there. Pays that level's diamonds.
    private fun applyAchieve(w: Wallet, e: Event.Achieve): Wallet? {
        val a = Achievements.ACHIEVEMENT[e.ach] ?: return null
        val done = Achievements.levelOf(w, a.id)
        val level = a.levels.getOrNull(done) ?: return null
        if (e.level != done + 1) return null // in order, once each
        if (a.stat(Achievements.statsOf(w)) < level.at) return null
        return w.copy(diamonds = w.diamonds + level.diamonds, achieved = w.achieved + (a.id to done + 1))
    }

    // Opens the daily chest: once per UTC day. The loot comes from the
    // wallet's seed and the day, never from the event.
    private fun applyChest(w: Wallet, e: Event.Chest, now: Long): Wallet? {
        val t = eventTime(e.at, now) ?: return null
        val day = t / DAY_MS
        val s = w.stats
        if (day <= s.chestDay) return null // one a day
        val streak = if (day == s.chestDay + 1) s.streak + 1 else 1
        val loot = Chest.loot(w.seed, day, streak)
        var next = add(w, loot.coins, loot.diamonds).copy(
            stats = s.copy(chests = s.chests + 1, chestDay = day, streak = streak, bestStreak = max(s.bestStreak, streak)),
        )
        if (loot.food != null) next = next.copy(pantry = w.pantry + (loot.food to (w.pantry[loot.food] ?: 0) + 1))
        if (loot.energy) needsFor(w, t, now)?.let { next = next.copy(needs = it.copy(energy = 100.0)) }
        return next
    }

    // The chest at time `t`: whether it can be opened, the streak it would make
    // and what's in it (for the chest's preview).
    fun chestFor(wallet: Wallet, t: Long): ChestState {
        val s = normalize(wallet).stats
        val day = t / DAY_MS
        val ready = day > s.chestDay
        val streak = if (!ready) s.streak else if (day == s.chestDay + 1) s.streak + 1 else 1
        // a streak is alive if the last chest was today or yesterday
        val alive = if (day - s.chestDay <= 1) s.streak else 0
        return ChestState(ready, streak, alive, day, (day + 1) * DAY_MS)
    }
}
