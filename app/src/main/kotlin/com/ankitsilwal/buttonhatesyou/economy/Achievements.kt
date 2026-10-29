package com.ankitsilwal.buttonhatesyou.economy

import kotlin.math.max
import kotlin.math.min

// Achievements (src/economy/achievements.js): the only steady source of
// diamonds. Every achievement has three levels, Bronze, Silver and Gold, each
// with a threshold on one stat and a diamond reward. Levels are claimed by
// hand, in order, with an Event.Achieve.

data class Medal(val id: String, val name: String)

data class Level(val at: Int, val diamonds: Int)

data class AchievementCategory(val id: String, val name: String, val blurb: String)

// Everything an achievement can look at, worked out from the wallet.
data class AchStats(
    val best: Map<String, Int>,
    val runs: Int,
    val runCoins: Int,
    val eaten: Int,
    val naps: Int,
    val days: Int,
    val chests: Int,
    val bestStreak: Int,
    val reason: Int, // story parts claimed
    val cursor: Int,
    val outfits: Int, // priced items owned
    val furniture: Int,
    val rooms: Int, // priced wallpapers and floors owned
)

data class Achievement(
    val id: String,
    val cat: String,
    val icon: String,
    val name: String,
    val stat: (AchStats) -> Int,
    val goal: (Int) -> String,
    val levels: List<Level>,
    val game: String? = null,
)

// An achievement's state: levels claimed, the stat's value, the next level
// (null when done), whether it can be claimed, and how many levels could be.
data class AchievementState(
    val a: Achievement,
    val level: Int,
    val value: Int,
    val next: Level?,
    val ready: Boolean,
    val claimable: Int,
)

object Achievements {
    val MEDALS = listOf(Medal("bronze", "Bronze"), Medal("silver", "Silver"), Medal("gold", "Gold"))

    val CATEGORIES = listOf(
        AchievementCategory("adventure", "Adventure", "Beat your best in every arcade game. The button keeps score. Grudgingly."),
        AchievementCategory("story", "Story", "Finish the stories. Bring tissues, collect diamonds."),
        AchievementCategory("home", "Home & Wardrobe", "Spend money on a button that hates you. Get diamonds for it."),
        AchievementCategory("daily", "Daily", "Keep coming back. He pretends not to notice."),
    )

    fun statsOf(w: Wallet): AchStats {
        val st = w.stats
        // Wallets from before stats only remember each game's best payout; the
        // lowest score that pays that much is a fair floor for the best score.
        val best = Catalog.GAMES.mapNotNull { g ->
            val p = g.payout ?: return@mapNotNull null
            val paid = w.runs[g.id] ?: 0
            val old = if (p.coins) paid else Payouts.scoreFor(paid, p.per, p.base)
            g.id to max(st.best[g.id] ?: 0, old)
        }.toMap()
        return AchStats(
            best = best,
            runs = st.runs,
            runCoins = st.runCoins,
            eaten = st.eaten,
            naps = st.naps,
            days = st.days,
            chests = st.chests,
            bestStreak = st.bestStreak,
            reason = w.claimed.count { it.startsWith("reason:") },
            cursor = w.claimed.count { it.startsWith("cursor:") },
            outfits = w.owned.count { Catalog.ITEM[it]?.price != null },
            furniture = w.owned.count { House.HOUSE_ITEM[it]?.let { i -> i.kind == "furniture" && i.price != null } == true },
            rooms = w.owned.count { House.HOUSE_ITEM[it]?.let { i -> (i.kind == "wall" || i.kind == "floor") && i.price != null } == true },
        )
    }

    private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

    // One "score N in <game>" achievement per Adventure game.
    private fun scoreAch(game: String, name: String, icon: String, levels: List<Int>, d: List<Int> = listOf(1, 2, 3)) = Achievement(
        id = "score-$game",
        cat = "adventure",
        icon = icon,
        name = name,
        stat = { s -> s.best[game] ?: 0 },
        goal = { n ->
            val g = Catalog.GAME.getValue(game)
            if (g.payout!!.coins) "Grab $n coins in one ${g.name} run" else "Reach $n ${g.unit} in ${g.name}"
        },
        levels = levels.mapIndexed { i, at -> Level(at, d[i]) },
        game = game,
    )

    private fun ach(id: String, cat: String, icon: String, name: String, stat: (AchStats) -> Int, goal: (Int) -> String, vararg levels: Pair<Int, Int>) =
        Achievement(id, cat, icon, name, stat, goal, levels.map { (at, diamonds) -> Level(at, diamonds) })

    val ACHIEVEMENTS: List<Achievement> = listOf(
        /* ---------- Adventure ---------- */
        scoreAch("flap", "Pipe Dreams", "🪽", listOf(10, 20, 40)),
        scoreAch("pop", "Family Feud", "🫧", listOf(1000, 2000, 4000)),
        scoreAch("simon", "Elephant Brain", "🎵", listOf(5, 10, 20)),
        scoreAch("hop", "Social Distancing", "☁️", listOf(2000, 4000, 8000)),
        scoreAch("whack", "Anger Management", "🔨", listOf(20, 40, 80)),
        scoreAch("run", "Restraining Order", "🏃", listOf(600, 1200, 2400)),
        scoreAch("stack", "Back Problems", "🗼", listOf(12, 24, 48)),
        scoreAch("slice", "Knife to Meet You", "🔪", listOf(30, 60, 120)),
        scoreAch("button-run", "Coin Goblin", "⌨️", listOf(50, 100, 200)),
        ach("runs", "adventure", "🎮", "Professional Loser", { it.runs }, { n -> "Finish ${plural(n, "Adventure run")}" }, 10 to 1, 50 to 3, 200 to 6),
        ach("run-coins", "adventure", "🪙", "Capitalism Enjoyer", { it.runCoins }, { n -> "Earn $n 🪙 from Adventure runs" }, 1000 to 1, 10000 to 3, 50000 to 6),

        /* ---------- Story ---------- */
        ach(
            "story-reason", "story", "🗡️", "Emotional Damage", { it.reason },
            { n -> if (n == 9) "Finish The Reason (all 9 episodes)" else "Finish ${plural(n, "episode")} of The Reason" },
            1 to 1, 5 to 3, 9 to 8,
        ),
        ach(
            "story-cursor", "story", "💍", "Hopeless Romantic", { it.cursor },
            { n -> if (n == 6) "Finish How I Met Your Cursor (all 6 chapters)" else "Finish ${plural(n, "chapter")} of How I Met Your Cursor" },
            1 to 1, 3 to 3, 6 to 6,
        ),
        // (Please Don't has no achievement: it pays nothing, on purpose.)
        ach(
            "stories", "story", "📚", "Bookworm", { it.reason + it.cursor },
            { n -> if (n == 15) "Finish both platformer stories (all 15 parts)" else "Finish $n episodes or chapters, in any story" },
            3 to 1, 8 to 2, 15 to 4,
        ),

        /* ---------- Home & Wardrobe ---------- */
        ach("outfits", "home", "🎩", "Drip Check", { it.outfits }, { n -> "Own ${plural(n, "outfit item")}" }, 1 to 1, 6 to 2, 15 to 4),
        ach("furniture", "home", "🛋️", "Interior Disaster", { it.furniture }, { n -> "Own ${plural(n, "piece", "pieces")} of furniture" }, 3 to 1, 8 to 2, 15 to 4),
        ach("rooms", "home", "🖼️", "Feng Shui Crimes", { it.rooms }, { n -> "Own ${plural(n, "wallpaper or floor", "wallpapers and floors")}" }, 1 to 1, 4 to 2, 8 to 4),
        ach("eaten", "home", "🍪", "Feeding Frenzy", { it.eaten }, { n -> "Feed him ${plural(n, "snack")}" }, 10 to 1, 50 to 2, 200 to 4),
        ach("naps", "home", "💤", "Nap Champion", { it.naps }, { n -> "Put him to bed ${plural(n, "time")}" }, 3 to 1, 15 to 2, 50 to 4),

        /* ---------- Daily ---------- */
        ach("days", "daily", "📅", "Can't Stay Away", { it.days }, { n -> "Play on ${plural(n, "different day")}" }, 3 to 1, 10 to 3, 30 to 6),
        ach("chests", "daily", "🎁", "Treasure Goblin", { it.chests }, { n -> "Open ${plural(n, "daily chest")}" }, 5 to 1, 25 to 2, 100 to 4),
        ach("streak", "daily", "🔥", "Streak Freak", { it.bestStreak }, { n -> "Open the chest $n days in a row" }, 3 to 1, 7 to 2, 30 to 5),
    )

    // Achievements of retired games: hidden, but kept so levels already claimed still replay.
    private val RETIRED: List<Achievement> = listOf(
        scoreAch("catch", "Stress Eater", "🍩", listOf(25, 50, 100)),
    )

    val ACHIEVEMENT: Map<String, Achievement> = (ACHIEVEMENTS + RETIRED).associateBy { it.id }

    // All the diamonds achievements can ever pay.
    val ACHIEVEMENT_DIAMONDS: Int = ACHIEVEMENTS.sumOf { a -> a.levels.sumOf { it.diamonds } }

    // Levels already claimed (0 to 3).
    fun levelOf(wallet: Wallet, id: String?): Int = max(0, id?.let { wallet.achieved[it] } ?: 0)

    fun states(wallet: Wallet, stats: AchStats = statsOf(wallet)): List<AchievementState> = ACHIEVEMENTS.map { a ->
        val level = min(levelOf(wallet, a.id), a.levels.size)
        val value = a.stat(stats)
        var claimable = 0
        while (level + claimable < a.levels.size && value >= a.levels[level + claimable].at) claimable++
        AchievementState(a, level, value, a.levels.getOrNull(level), claimable > 0, claimable)
    }

    // How many levels could be claimed right now (for a badge).
    fun readyCount(wallet: Wallet): Int = states(wallet).sumOf { it.claimable }

    // Achievements with a level that just became claimable (for an
    // "Achievement ready!" line after a run or a story part).
    fun newlyReady(before: Wallet, after: Wallet): List<Achievement> {
        val was = states(before).associate { it.a.id to it.level + it.claimable }
        return states(after).filter { it.level + it.claimable > (was[it.a.id] ?: 0) }.map { it.a }
    }
}
