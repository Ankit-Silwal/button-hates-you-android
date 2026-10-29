package com.ankitsilwal.buttonhatesyou.economy

import com.ankitsilwal.buttonhatesyou.avatar.Look

// Everything that can be earned or bought (a port of src/economy/catalog.js).
// Ids, prices and rewards match the web version, so one wallet can be synced
// between the two.

// What something costs. A zero means "none of that currency".
data class Price(val coins: Int = 0, val diamonds: Int = 0)

// What something pays.
data class Prize(val coins: Int = 0, val diamonds: Int = 0)

// Anything with a price: outfit items, house items, foods, wake-ups.
// Items without a price are free and always owned.
interface Priced {
    val id: String
    val price: Price?
}

// "#e2674f" -> 0xFFE2674F (opaque ARGB).
fun hex(rgb: String): Long = 0xFF000000L or rgb.removePrefix("#").toLong(16)

data class Slot(val id: String, val name: String, val icon: String)

// A body color: the main color and its shadow.
data class Colors(val c: Long, val cd: Long)

// `look` is what the avatar keys off (an outfit/eyes/... name, null for
// nothing); color items have `colors` instead.
data class Item(
    override val id: String,
    val slot: String,
    val name: String,
    val look: String? = null,
    val colors: Colors? = null,
    override val price: Price? = null,
) : Priced

// How an Adventure run pays (see Payouts):
//   per, base   rising tiers: the first `per` points pay `base` in total, the
//               next `per` pay 2*base, then 3*base... (partial tiers pro rata)
//   coins       instead: pays the coins picked up in the run, 1:1
//   rate        anti-cheat: the most score (or coins) a second of play can
//               plausibly make; anything above that is not paid
data class Payout(val per: Int = 0, val base: Int = 0, val rate: Double, val coins: Boolean = false)

data class Quips(val goal: String, val fail: String)

// A mini-game as listed on the Games screen. Adventure games also carry their
// title card: `howTo`, the score `unit`, how a run pays (`payout`, spelled
// out by `prize` and `rule`) and two lines for the results card.
data class GameInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val blurb: String,
    val category: String, // "story" or "adventure"
    val prize: String,
    val rule: String? = null,
    val howTo: String? = null,
    val payout: Payout? = null,
    val unit: String? = null,
    val quips: Quips? = null,
)

data class GameCategory(val id: String, val name: String, val blurb: String)

// Old fixed rewards from before the economy rework (see Rules, "reward").
data class LegacyReward(val coins: Int, val diamonds: Int, val max: Int)

object Catalog {
    val SLOTS = listOf(
        Slot("color", "Color", "🎨"),
        Slot("outfit", "Clothes", "👕"),
        Slot("eyes", "Eyes", "👀"),
        Slot("ears", "Ears", "🎧"),
        Slot("scarf", "Neck", "🧣"), // (the slot id stays 'scarf' for saved outfits)
        Slot("hat", "Hat", "🧢"),
    )

    private fun color(id: String, name: String, c: String, cd: String, price: Price? = null) =
        Item(id, "color", name, colors = Colors(hex(c), hex(cd)), price = price)

    private fun coins(n: Int) = Price(coins = n)
    private fun gems(n: Int) = Price(diamonds = n)

    val ITEMS: List<Item> = listOf(
        color("color-tomato", "Tomato", "#e2674f", "#b84a36"),
        color("color-ocean", "Ocean", "#5b9bd5", "#3b77ad", coins(300)),
        color("color-mint", "Mint", "#7cc6a4", "#549c7c", coins(300)),
        color("color-sunny", "Sunny", "#f2c14e", "#c99a2e", coins(450)),
        color("color-grape", "Grape", "#a58bd6", "#7c63ad", coins(450)),
        color("color-bubblegum", "Bubblegum", "#f39ac0", "#cc6f97", coins(600)),
        color("color-midnight", "Midnight", "#4a5a85", "#2c3654", coins(950)),
        color("color-gold", "Solid Gold", "#e6b422", "#a87d0c", gems(10)),
        color("color-charcoal", "Charcoal", "#565b6a", "#363a46", coins(400)),
        color("color-sand", "Sand", "#d3b58f", "#a98b66", coins(400)),
        color("color-slate", "Slate", "#6c86a3", "#4a6280", coins(450)),
        color("color-forest", "Forest", "#4e7a5e", "#34523f", coins(500)),
        color("color-burgundy", "Burgundy", "#8e3b4e", "#652635", coins(550)),
        color("color-obsidian", "Obsidian", "#2b2b35", "#14141a", gems(8)),

        Item("outfit-none", "outfit", "Just the button"),
        Item("outfit-tee", "outfit", "Band tee", "tee", price = coins(400)),
        Item("outfit-hoodie", "outfit", "Hoodie", "hoodie", price = coins(700)),
        Item("outfit-track", "outfit", "Track jacket", "track", price = coins(900)),
        Item("outfit-denim", "outfit", "Denim jacket", "denim", price = coins(1000)),
        Item("outfit-varsity", "outfit", "Varsity jacket", "varsity", price = coins(1300)),
        Item("outfit-blazer", "outfit", "Blazer", "blazer", price = coins(1500)),
        Item("outfit-leather", "outfit", "Leather jacket", "leather", price = coins(1800)),
        Item("outfit-puffer", "outfit", "Puffer coat", "puffer", price = gems(5)),

        Item("eyes-classic", "eyes", "Classic"),
        Item("eyes-sleepy", "eyes", "Sleepy", "sleepy", price = coins(250)),
        Item("eyes-googly", "eyes", "Googly", "googly", price = coins(400)),
        Item("eyes-hearts", "eyes", "Hearts", "hearts", price = coins(550)),
        Item("eyes-shades", "eyes", "Shades", "shades", price = coins(700)),
        Item("eyes-stars", "eyes", "Starstruck", "stars", price = gems(6)),
        Item("eyes-chill", "eyes", "Unbothered", "chill", price = coins(350)),
        Item("eyes-glasses", "eyes", "Round glasses", "glasses", price = coins(600)),
        Item("eyes-aviators", "eyes", "Aviators", "aviators", price = coins(900)),
        Item("eyes-visor", "eyes", "Gamer visor", "visor", price = gems(6)),

        Item("ears-none", "ears", "None"),
        Item("ears-bear", "ears", "Bear", "bear", price = coins(400)),
        Item("ears-cat", "ears", "Cat", "cat", price = coins(450)),
        Item("ears-bunny", "ears", "Bunny", "bunny", price = coins(600)),
        Item("ears-devil", "ears", "Devil horns", "devil", price = gems(6)),
        Item("ears-earbuds", "ears", "Earbuds", "earbuds", price = coins(450)),
        Item("ears-headphones", "ears", "Headphones", "headphones", price = coins(750)),

        Item("scarf-none", "scarf", "None"),
        Item("scarf-red", "scarf", "Red", "red", price = coins(300)),
        Item("scarf-sage", "scarf", "Sage", "sage", price = coins(300)),
        Item("scarf-striped", "scarf", "Striped", "striped", price = coins(550)),
        Item("scarf-rainbow", "scarf", "Rainbow", "rainbow", price = gems(4)),
        Item("scarf-lanyard", "scarf", "Lanyard", "lanyard", price = coins(350)),
        Item("scarf-bandana", "scarf", "Bandana", "bandana", price = coins(450)),
        Item("scarf-chain", "scarf", "Gold chain", "chain", price = coins(1100)),

        Item("hat-none", "hat", "None"),
        Item("hat-beanie", "hat", "Beanie", "beanie", price = coins(400)),
        Item("hat-party", "hat", "Party hat", "party", price = coins(450)),
        Item("hat-top", "hat", "Top hat", "top", price = coins(800)),
        Item("hat-crown", "hat", "Crown", "crown", price = gems(10)),
        Item("hat-headband", "hat", "Headband", "headband", price = coins(350)),
        Item("hat-cap", "hat", "Snapback", "cap", price = coins(550)),
        Item("hat-bucket", "hat", "Bucket hat", "bucket", price = coins(650)),
        Item("hat-beret", "hat", "Beret", "beret", price = coins(700)),
    )

    val ITEM: Map<String, Item> = ITEMS.associateBy { it.id }

    val DEFAULT_LOOK: Map<String, String> = linkedMapOf(
        "color" to "color-tomato",
        "outfit" to "outfit-none",
        "eyes" to "eyes-classic",
        "ears" to "ears-none",
        "scarf" to "scarf-none",
        "hat" to "hat-none",
    )

    // The avatar's Look for the equipped item ids (unknown ids fall back to
    // the default for their slot).
    fun lookOf(equipped: Map<String, String>): Look {
        fun item(slot: String) = ITEM[equipped[slot]]?.takeIf { it.slot == slot } ?: ITEM.getValue(DEFAULT_LOOK.getValue(slot))
        val colors = item("color").colors ?: ITEM.getValue("color-tomato").colors!!
        return Look(
            color = colors.c,
            dark = colors.cd,
            outfit = item("outfit").look,
            eyes = item("eyes").look,
            ears = item("ears").look,
            scarf = item("scarf").look,
            hat = item("hat").look,
        )
    }

    /* ---------- rewards (the economy) ---------- */

    // Story games pay once per episode/chapter, the first time it is finished.
    // Ids are '<story>:<n>' (or just the story for Please Don't).
    private fun storyRewards(story: String, parts: Int, each: Prize, finale: Prize) =
        (1..parts).associate { "$story:$it" to if (it == parts) finale else each }

    // Coins only: the diamonds the finales used to pay now come from the story achievements.
    val STORY_REWARDS: Map<String, Prize> =
        storyRewards("reason", 9, Prize(200), Prize(1500)) +
            storyRewards("cursor", 6, Prize(200), Prize(1200)) +
            // Please Don't pays nothing: finishing it is its own (mysterious) reward.
            ("please-dont" to Prize(0))

    // Limits on what Adventure runs pay.
    object RunLimits {
        const val maxPayout = 400 // a single run never pays more than this
        const val maxRunMs = 30 * 60_000L // a single run counts at most 30 minutes of play
        const val dailyFull = 3000 // coins from runs per day at full rate...
        const val overCap = 0.25 // ...then this fraction of each payout
    }

    // Rewards from before the economy rework (only old queued events use these).
    val REWARDS: Map<String, LegacyReward> = mapOf(
        "please-dont" to LegacyReward(20, 1, 1),
        "reason-episode" to LegacyReward(15, 0, 9),
        "reason-finale" to LegacyReward(80, 5, 1),
        "cursor-chapter" to LegacyReward(15, 0, 6),
        "cursor-finale" to LegacyReward(60, 4, 1),
        "adventure-goal" to LegacyReward(8, 0, 100),
        "adventure-first" to LegacyReward(0, 1, 10),
    )

    // The two shelves on the Games screen.
    val CATEGORIES = listOf(
        GameCategory("story", "Story", "Long games with a plot. The button has feelings, apparently."),
        GameCategory("adventure", "Adventure", "Quick arcade games starring the button. Every run pays: the better you do, the more you earn."),
    )

    // How a run pays, in words: the line on the Games card (short) or the rule on the title card.
    fun payoutLine(p: Payout, unit: String?, short: Boolean): String {
        if (p.coins) return if (short) "1 🪙 per coin you grab" else "Every coin you grab is yours: 1 coin = 1 🪙."
        val first = "${p.per} $unit"
        if (short) return "+${p.base} 🪙 per $first, rising"
        return "Every $first pays more: ${p.base}, ${p.base * 2}, ${p.base * 3}… 🪙"
    }

    private fun adventure(
        id: String, name: String, emoji: String, color: String, blurb: String, howTo: String,
        payout: Payout, unit: String, goal: String, fail: String,
    ) = GameInfo(
        id = id, name = name, emoji = emoji, color = hex(color), blurb = blurb, category = "adventure",
        prize = payoutLine(payout, unit, true), rule = payoutLine(payout, unit, false),
        howTo = howTo, payout = payout, unit = unit, quips = Quips(goal, fail),
    )

    val GAMES: List<GameInfo> = listOf(
        // Story games (the Android app doesn't have them yet; kept for their rewards and achievements).
        GameInfo(
            "how-i-met-your-cursor", "How I Met Your Cursor", "💍", hex("#ffe0b8"),
            "Button Quest: the story before The Reason. Bobby, his new best friend Cursor, and the most disastrous love story in internet history. 6 chapters, about 30 minutes.",
            "story", "+200 🪙 a chapter, once",
        ),
        GameInfo(
            "the-reason", "The Reason", "🗡️", hex("#c9cde8"),
            "The prequel. A sword-swinging platformer about why the button hates you. 9 episodes, about 50 minutes. Bring tissues.",
            "story", "+200 🪙 an episode, once",
        ),
        GameInfo(
            "please-dont", "Please Don't", "🔴", hex("#f6c9b8"),
            "Press the button 65 times. It really, really does not want you to.",
            "story", "??? Mystery prize",
        ),
        adventure(
            "button-run", "Button Run", "⌨️", "#cfd0ff",
            "A 3D dash down a giant keyboard. Swerve past firewalls, spam trains and your own cursor clicking down from the sky. Grab wings, magnets and bubble wrap on the way.",
            "Swipe, or use ← → to switch lanes, ↑ to jump and ↓ to slide. Tap to jump.",
            Payout(rate = 15.0, coins = true), "points",
            "You chased me all that way for coins? Get a hobby.", "Clicked. Of course I got clicked.",
        ),
        adventure(
            "flap", "Flappy Regret", "🪽", "#bfe3ff",
            "You strapped wings on the button. Tap to flap it through the pipes. It did not consent to this.",
            "Tap, click or press Space to flap.",
            Payout(10, 25, 1.5), "pipes", "I am going to be sick.", "Gravity is my friend now.",
        ),
        adventure(
            "pop", "Pop Tantrum", "🫧", "#ffd6f0",
            "The button's cousins are stuck in a grid. Pop groups of the same color before you run out of taps.",
            "Tap a group of 2 or more same-colored buttons to pop them. 25 taps.",
            Payout(1000, 25, 5000.0), "points", "You popped my whole family.", "Family reunion: saved.",
        ),
        adventure(
            "catch", "Snack Attack", "🍩", "#ffe3b3",
            "Snacks are falling. So are bombs. Move the button to catch one and not the other.",
            "Drag, or use ← → to move. Catch snacks, dodge bombs. 3 lives.",
            Payout(25, 25, 5.0), "snacks", "I ate them out of spite.", "I was on a diet anyway.",
        ),
        adventure(
            "simon", "Copy Me", "🎵", "#d8ccff",
            "The button plays a tune on its four friends. Repeat it. It gets longer. The button gets smugger.",
            "Watch the sequence, then tap the pads in the same order.",
            Payout(5, 25, 0.6), "rounds", "You were supposed to forget.", "Goldfish memory. Classic.",
        ),
        adventure(
            "hop", "Sky Hop", "☁️", "#c9f0e4",
            "The button bounces up and up on clouds, trying to get as far away from you as possible.",
            "Steer with ← →, or hold and drag where you want it to go.",
            Payout(2000, 25, 150.0), "meters", "Still not far enough from you.", "Gravity: 1. Me: also fine.",
        ),
        adventure(
            "whack", "Whack-a-Button", "🔨", "#f6d1b8",
            "The button and its clones peek out of holes. Bonk them. Do not bonk the ones holding a heart.",
            "Tap buttons as they pop up. Skip the ones with a heart. 45 seconds.",
            Payout(20, 25, 4.0), "bonks", "My head is a pancake now.", "Too slow. We felt nothing.",
        ),
        adventure(
            "run", "Run Away", "🏃", "#d4ecb0",
            "The button finally grew legs and is sprinting away from you. Help it jump the junk in its path.",
            "Tap or press Space to jump. Hold to jump higher. Down / swipe down to duck.",
            Payout(600, 25, 40.0), "meters", "You helped me escape you. Odd.", "Tripped. Do not look at me.",
        ),
        adventure(
            "stack", "Tower of Regret", "🗼", "#ffe0c2",
            "Drop the button clones on top of each other to build a tower. Whatever hangs over the edge falls off, screaming.",
            "Tap or press Space to drop the swinging clone.",
            Payout(12, 25, 3.0), "floors", "My back hurts. All of our backs.", "A short tower for a short attention span.",
        ),
        adventure(
            "slice", "Slice Spree", "🔪", "#ffd0c9",
            "Fruit and junk fly up. Swipe to slice them. The button flies up too. Do NOT slice the button.",
            "Swipe across things to slice them. Missing 3 or slicing the button ends the run.",
            Payout(30, 25, 8.0), "slices", "You were aiming for me. I saw that.", "Ha. Missed me. Literally.",
        ),
        adventure(
            "redlight", "Freeze!", "🚦", "#ffd9d0",
            "The button is sneaking past the cursor. Run while it looks away, freeze the second it turns around.",
            "Hold to run. Let go the moment the cursor turns around. Caught moving and it is over.",
            Payout(500, 25, 40.0), "meters", "You outran a cursor. Humiliating for it.", "Caught red-handed. Red-bodied.",
        ),
        adventure(
            "spam", "Inbox Zero", "📧", "#d5e6ff",
            "Emails pour in. Real ones go right, spam goes left. Faster. Faster. The button's job depends on this.",
            "Tap or swipe left for spam, right for real mail. Wrong sort = a strike. 3 strikes.",
            Payout(25, 25, 4.0), "emails", "Promotion denied. You are too good at this.", "I trashed Mum's email. Again.",
        ),
        adventure(
            "fizz", "Shaken Not Stirred", "🥤", "#ffe7b0",
            "Shake the soda by dragging it back and forth, then let go before it blows. Bigger fizz, bigger bragging rights.",
            "Drag back and forth to shake. Release when the meter is in the green. Too far and it explodes.",
            Payout(5, 25, 0.5), "bottles", "Sticky. Victorious. Sticky.", "Soda everywhere. Mostly on my face.",
        ),
        adventure(
            "pong", "Cursor Pong", "🏓", "#d4f3e0",
            "The cursor is the ball and it is furious. Bounce it back with the button paddle before it escapes.",
            "Drag left and right to move the paddle. Do not let the cursor past.",
            Payout(20, 25, 2.5), "returns", "I bounced a cursor. I am the one with power now.", "It slipped past me. Typical cursor.",
        ),
        adventure(
            "tightrope", "Wobble Walk", "🎪", "#f9d9ff",
            "The button is crossing a tightrope in a gale, wearing no shoes. Lean to stay up. Do not look down.",
            "Hold the left or right half of the screen (or use ← →) to lean against the wobble.",
            Payout(400, 25, 25.0), "meters", "I crossed the sky. I want a sandwich.", "Fell. At least the ground is steady.",
        ),
    )

    val GAME: Map<String, GameInfo> = GAMES.associateBy { it.id }

    // Games taken out of the app. They stay in GAMES so runs already in a
    // wallet's history still replay and pay; they just can't be played any more.
    val RETIRED: Set<String> = setOf("catch", "redlight", "spam", "fizz", "pong", "tightrope")

    // The Adventure games (the ones the Android app plays).
    val ADVENTURE_GAMES: List<GameInfo> get() = GAMES.filter { it.payout != null && it.id !in RETIRED }
}
