package com.ankitsilwal.buttonhatesyou.ui

import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Mood

// What he says (src/components/Hub.jsx, src/hub/*Pane.jsx, src/house/EnergyGate.jsx).
object Lines {
    fun pick(list: List<String>): String = list.random()

    fun greeting(face: Face, coins: Int): String = when {
        face.dirty == 2 -> "Do you smell that? That's me. Bathroom. Please."
        face.mood == Mood.Bored -> "I'm hungry. And bored. Mostly hungry."
        face.sleepy -> "I did not sleep. Look at my eyes. LOOK at them."
        face.mood == Mood.Sleep -> "Zzz… (do not press)"
        coins >= 1000 -> "You're rich. Buy me something nice."
        else -> "Oh. A visitor. Please don't press anything."
    }

    val POKES = listOf(
        "Hey. HEY. Hands off." to Mood.Annoyed,
        "I'm not a button in here. I'm a homeowner." to Mood.Smug,
        "Go play a game. Bother someone else." to Mood.Annoyed,
        "That tickles. I hate that it tickles." to Mood.Smug,
        "Poke me one more time and I'm calling Kevin." to Mood.Angry,
        "This is a no-pressing zone." to Mood.Angry,
        "I can hear you breathing." to Mood.Scared,
        "Nice outfit, right? You paid for it." to Mood.Smug,
    )

    val MOAN_BORED = listOf("Feed me and I might look less bored.", "Nothing to do. Nothing to eat. Typical.")
    val MOAN_SLEEPY = listOf("Zzz-no. I am awake. Barely.", "My eyes feel like two raisins.")
    val MOAN_DIRTY = listOf("I smell like a gym bag. Bathroom?", "Is that a fly? It is. It lives here now.", "I will not sleep like this. Wash me first.")
    const val ASLEEP_POKE = "Zzz… *mumbles* …not the button…"

    /** A poke in the living room: needs moans first, then the usual lines. Returns the line and a mood to flash. */
    fun poke(n: Int, face: Face, asleep: Boolean): Pair<String, Mood?> = when {
        asleep -> ASLEEP_POKE to null
        n % 7 == 0 -> "OKAY. Go earn some coins and leave me alone." to Mood.Angry
        face.dirty > 0 && n % 2 == 1 -> pick(MOAN_DIRTY) to Mood.Sad
        face.mood == Mood.Bored && n % 2 == 0 -> pick(MOAN_BORED) to Mood.Bored
        face.sleepy && n % 3 == 0 -> pick(MOAN_SLEEPY) to Mood.Tired
        else -> POKES.random().let { it.first to it.second }
    }

    // the kitchen
    val HUNGRY = listOf("I could eat a keyboard.", "My tummy is making dial-up noises.", "Feed me. Please. Do not tell anyone I said please.")
    val FULL = listOf("I'm stuffed. Absolutely stuffed.", "One more bite and I pop.")

    // the bathroom
    val DIRTY_LINES = listOf("I smell like a gym bag. Soap me.", "There is something living on me. Soap. Now.", "Do you see the flies? The flies are my friends now.")
    val CLEAN_LINES = listOf("I'm already clean. Don't be weird.", "Squeaky. Nothing to scrub.")
    val SCRUB_LINES = listOf("Left a bit. Down. NO, the other left.", "Ow. Gentle. I am a button.", "That tickles! Stop. Keep going.", "Foam on my face. Great.")
    val LATHER_LINES = listOf("Okay, I'm all foamy. Now drag me into the shower!", "Peak bubbles. Shower time. Drag me in.")
    val DONE_LINES = listOf("Squeaky clean. I smell like a meadow.", "Fresh. Do not touch. You will smudge me.", "New button, who dis?")
    const val ASLEEP_HINT = "Zzz… (he is asleep. Wake him up in the Bedroom first)"
    val SOAP_REFUSED = listOf("Zzz… *swats the soap away* …no bath…", "Mmph. No soap. Sleeping.", "Zzz… *the soap will not budge* (wake him up first)")

    // decorating / shopping
    val BOUGHT = listOf("Bought and put up. Looks great.", "Ooh. Fancy.", "Finally, some taste in here.")
    val PLACED = listOf("There. Perfect.", "Hmm. Acceptable.", "Move it again and he screams.")
    const val BROKE = "You're broke. Go play something."

    // the energy gate
    val GATE = mapOf(
        "tired" to listOf("Too tired. Feed me or let me nap.", "I have no energy for your games. Snacks first.", "Can't. My battery is at zero. Emotionally too."),
        "hungry" to listOf("My stomach is eating itself. Feed me first.", "I'm too hungry to care about your games.", "Snack. Then game. That is the order."),
        "asleep" to listOf("Shh. I'm asleep. Wake me up at home first.", "Zzz… (he is asleep. Go wake him up in the Bedroom.)"),
    )
}
