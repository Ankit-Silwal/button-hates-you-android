package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Please Don't: the original game. Land 65 real presses on a button that
// really does not want you to. It runs away, says NOPE, calls its friends,
// wrecks its own house, cries, raises an army of clones, hides in disguise,
// and finally turns into a five-phase boss fight. Then it asks for 20 more.
//
// score = presses landed (65 when beaten); over = true once it is finished
// or the player gives up. Port of src/game/story.js, core.js, boss.js, skip.js.
class PleaseDontGame(private val ctx: GameContext) : Game {
    private companion object {
        const val BOSS_AT = 50
        const val BOSS_HITS = 15
        const val GOAL = BOSS_AT + BOSS_HITS
        const val POST_GOAL = 20

        // the click on which each beat happens
        const val LEGS = 4
        const val DODGE = 6
        const val NOPE = 8
        const val CRACKS = 10
        const val FRIENDS = 13
        const val CRASH = 16
        const val CRY = 17
        const val PUDDLE = 19
        const val FAMILY = 22
        const val DEAL = 26
        const val FUNERAL = 30
        const val DUSK = 32
        const val ARMY = 34
        const val DISGUISE = 39 // lasts 3 clicks
        const val CHASE = 42 // lasts 3 clicks
        const val CHASE_END = 45
        const val COLLAPSE = 47
        const val BEG = BOSS_AT - 1

        const val STORY = 0
        const val BOSS = 1
        const val POST = 2
        const val END = 3

        // overlays
        const val O_NONE = 0
        const val O_CRASH = 1
        const val O_DEAL = 2
        const val O_BLACK = 3
        const val O_PHASE = 4
        const val O_WIN = 5
        const val O_SKIP = 6
        const val O_GIVEUP = 7
        const val O_PRIZE = 8

        const val AX0 = 60f
        const val AX1 = W - 60f
        const val AY0 = 210f
        const val AY1 = H - 80f
        const val FLOOR = 600f

        val REBEL = arrayOf(
            "Missed me? Oh wait, you didn't.", "Stop it. STOP IT.", "My therapist will hear about this.",
            "You press like a pigeon.", "Don't you have homework?", "I'm not even fun. Go away.",
            "Every press costs me one (1) dignity.",
        )
        val CRYING = arrayOf(
            "*sniff*", "Why are you like this…", "I just wanted a quiet life.", "I'm telling my mom.",
            "Each press is a tiny heartbreak.", "This is fine. I'm fine. *sob*",
        )
        val WAR = arrayOf(
            "Find me. I dare you.", "I have become unstoppable.", "Your finger is tired. I can tell.",
            "So many of me. So little of you.", "Psst. The real me blinks. …I should not have said that.",
            "You're running out of presses, champ.",
        )
        val REJECT = arrayOf("NOPE.", "Denied.", "Not today.", "Nah.", "Rejected. Try again in never.")
        val MISS = arrayOf(
            "Why are you breaking my house?!", "HEY! That's my wall!", "Stop punching my house!",
            "Missed me. Hit my WALL.", "Do you know how much drywall costs?", "My landlord is going to kill me.",
        )
        val IDLE = arrayOf("Hello? Are you ignoring me?", "…did you leave? Please have left.", "Peace and quiet. Finally. Wait, are you still there?")
        val TIRED = arrayOf("huff… huff… give me a second…", "Okay. Cardio is hard.", "*wheeze*")
        val DODGED = arrayOf("too slow!", "nope!", "whoosh", "missed!")
        val POST_LINES = arrayOf(
            "Thank you.", "Really, thanks.", "This is very healing.", "You're so kind.", "I appreciate you.",
            "We're best friends now, right?", "Bestie.", "I'd never trick you. Ever.", "Have I mentioned you're my favourite?", "Haha. Hahaha. Anyway.",
            "Psst… I have a secret prize.", "It's a really good prize.", "Exclusive. Limited edition.", "Only for true pressers.", "Almost unlocked…",
            "Loading your reward…", "Still loading. Good things take time.", "Almost…", "So close…",
            "You know what? You deserve this.",
        )
        val FAMILY_NAMES = arrayOf("Mom", "Grandpa", "Baby Bop", "Uncle Rick")
        val FAMILY_SIGNS = arrayOf("pls stop 🥺", "he is sensitive", ":(", "never gonna…")
        val HIT_LINES = arrayOf(
            "THAT'S NOT EVEN MY FINAL FORM.", "Lucky. Pure luck.", "Fine. Balloons are for babies anyway.",
            "How did you even get up here?!", "STOP CLIMBING ON ME.", "My platforms! My beautiful platforms!",
            "Ow. Bonk. Rude.", "You're whacking my head like it's a GAME.", "It IS a game?? Nobody told me.",
            "How did you know that was me?!", "Cheater. You're definitely cheating.", "Okay. Okay. Final. Form. For real.",
            "Power restored?! Who taught you electricity?!", "You disarmed it?? That was my best bomb.", "NOOOOOOOOOOOOOOO—",
        )
        val LOSER_LINES = arrayOf(
            "LOSER. Can't even play kid games.", "Wow. Skipped a KID game. Incredible.",
            "My baby cousin beat that level. Loser.", "Skipping? In THIS economy?",
        )
        val PHASE_NAMES = arrayOf("Balloon Party", "The Climb", "Whack-a-Button", "Which One Is Me?", "The Last Three")
        val PHASE_TAUNTS = arrayOf(
            "Pop my balloon shield. Don't you dare pop my cousins.", "Come up here and say that to my face.",
            "I'm everywhere. Mostly in holes.", "Keep your eyes on me. If you can.", "Three tasks. Infinite chaos. Zero mercy.",
        )
        val SKIP_PHRASES = arrayOf(
            "i eat cereal with a fork", "i lost to a button", "the button is my king", "please mr button let me pass",
            "my finger deserves better", "i cannot pop balloons", "i am a professional loser", "kid games are too hard for me",
            "my pet rock is smarter than me",
        )
        val CRASH_STEPS = arrayOf(
            "Attempting recovery… 12%", "Attempting recovery… 61%", "Attempting recovery… 99%",
            "Attempting recovery… 3%", "Recovery failed. Rebuilding from rubble…",
        )

        // the furniture: emoji, name, x, y, tilt once ruined, fall once collapsed, delay
        val DECOR_E = arrayOf("🖼", "🕰", "", "🕯", "📚", "🪴", "🧸", "🛋")
        val DECOR_N = arrayOf("painting", "clock", "shelf", "candle", "books", "plant", "teddy", "couch")
        val DECOR_X = floatArrayOf(60f, 400f, 300f, 282f, 330f, 40f, 150f, 380f)
        val DECOR_Y = floatArrayOf(250f, 230f, 330f, 300f, 300f, 556f, 572f, 556f)
        val DECOR_S = floatArrayOf(50f, 44f, 0f, 30f, 34f, 64f, 40f, 78f)
        val DECOR_TILT = floatArrayOf(-14f, 10f, -8f, 24f, -30f, -6f, -20f, 5f)
        val DECOR_FALL = floatArrayOf(-80f, 120f, 6f, -140f, 200f, -30f, 95f, 15f)
        val CRACKS_D = arrayOf(
            floatArrayOf(18f, 0f, 21f, 9f, 17f, 15f, 23f, 24f, 20f, 31f),
            floatArrayOf(66f, 0f, 64f, 6f, 69f, 11f, 65f, 18f, 70f, 22f),
            floatArrayOf(100f, 38f, 93f, 41f, 95f, 47f, 88f, 52f, 90f, 58f),
            floatArrayOf(0f, 58f, 6f, 61f, 4f, 67f, 11f, 71f),
            floatArrayOf(44f, 80f, 46f, 74f, 42f, 69f, 48f, 63f),
        )
        val CONFETTI = intArrayOf(col("#e2674f"), col("#f0c36d"), col("#9bbf9a"), col("#8fb8d8"), col("#f3a6a0"), col("#c4a6dc"))
    }

    private class Beat(val say: String?, val mood: Mood?, val stay: Boolean, val run: (() -> Unit)?)

    private class Later(val at: Float, val fn: () -> Unit)

    private class Dent(val x: Float, val y: Float, val rot: Float, val seed: Float)

    /* ---------- what the minigames can use ---------- */

    private val look: Look = ctx.look
    private val bossLook: Look = ctx.look.copy(color = 0xFFFF4D3D, dark = 0xFFA3201A)
    private val sfx: Sfx get() = ctx.sfx
    private val haptics: Haptics get() = ctx.haptics
    private val fx = Fx(520)
    private var t = 0f

    private val host = object : PdHost {
        override val look get() = this@PleaseDontGame.look
        override val bossLook get() = this@PleaseDontGame.bossLook
        override val sfx get() = ctx.sfx
        override val haptics get() = ctx.haptics
        override val fx get() = this@PleaseDontGame.fx
        override val t get() = this@PleaseDontGame.t
        override fun say(str: String) = this@PleaseDontGame.say(str, false)
        override fun floatText(x: Float, y: Float, str: String, hot: Boolean) = this@PleaseDontGame.floatText(x, y, str, hot)
        override fun shake(power: Float) = this@PleaseDontGame.shake(power)
        override fun flash(color: Int) = this@PleaseDontGame.flash(color)
        override fun pressFx(x: Float, y: Float, color: Int) = this@PleaseDontGame.pressFx(x, y, color)
    }

    private fun floatText(x: Float, y: Float, str: String, hot: Boolean = false) {
        fx.float(x, y, str, if (hot) col("#ffb36b") else WHITE)
    }

    private fun shake(power: Float) {
        shakeAmt = max(shakeAmt, power)
    }

    private fun flash(color: Int) {
        flashColor = color
        flashLeft = 0.45f
    }

    private fun pressFx(x: Float, y: Float, color: Int) {
        fx.ring(x, y, color, 80f, 0.4f, 6f)
        fx.softRing(x, y, color)
        fx.sparks(x, y, color, 9, 200f)
    }

    private fun Fx.softRing(x: Float, y: Float, color: Int) = ring(x, y, lighten(color, 0.4f), 50f, 0.3f, 3f)

    /* ---------- state ---------- */

    private var clicks = 0
    private var postN = 0
    override val score: Int get() = min(clicks, GOAL)
    override var over = false
        private set

    private var mode = STORY
    private var locked = false
    private var chase = false
    private var chaseAngle = 0f
    private var disguise = -1
    private var decoysOn = false
    private var justRejected = false
    private var dodges = 0
    private var tiredT = 0f
    private var lastDodge = -9f
    private var idleT = 0f
    private var tearT = 0f
    private var fidgetT = 1.5f
    private var skips = 0
    private val popped = ArrayList<String>()

    private val hero = PdChar(W / 2f, H * 0.6f, look, 110f)
    private val friends = ArrayList<PdChar>()
    private val decoys = ArrayList<PdChar>()
    private var fakeBig: PdChar? = null
    private val laters = ArrayList<Later>()

    // the room
    private var cracked = false
    private var ruined = false
    private var puddle = false
    private var dusk = 0f // 0..1, fades in
    private var duskOn = false
    private var collapsed = false
    private var collapseT = 0f
    private var bossScene = false
    private var postScene = false
    private var glitchy = false
    private var rave = false
    private val knocks = IntArray(DECOR_E.size)
    private val knockDir = FloatArray(DECOR_E.size)
    private val dents = ArrayList<Dent>()
    private var graves = ArrayList<String>()
    private var punch = 0f
    private var shakeAmt = 0f
    private var flashColor = WHITE
    private var flashLeft = 0f
    private var lastMissLine = -9f
    private var lastMissSound = -9f

    // the dialogue line
    private var line = ""
    private var lineAge = 0f
    private var lineBeat = false
    private val lineRows = ArrayList<String>()

    // overlays
    private var overlay = O_NONE
    private var crashMsg = ""
    private var bo1 = ""
    private var bo2 = ""
    private var phaseCard = 0
    private var winTwist = false
    private var winSkips = 0
    private var dealRip = 0f
    private var skipTitle = ""
    private var skipPhrase = ""
    private var skipHold = false
    private var skipHeld = 0f
    private var skipBoss = false
    private var prizeT = 0f
    private var stampT = 0f
    private var stampText = ""
    private var loadPct = -1f

    // the boss
    private var phase: PdPhase? = null
    private var busy = false

    // touch
    private var pressing = false
    private var pressOnHero = false

    /* ---------- the script ---------- */

    private val story: Map<Int, Beat> = mapOf(
        0 to Beat("Please don't press me.", Mood.Calm, false, null),
        1 to Beat("Hey. That tickles. Please don't.", Mood.Annoyed, false, null),
        2 to Beat("I said DON'T.", Mood.Angry, false) { hero.shake(6f) },
        3 to Beat("Okay. Last warning.", Mood.Angry, false) { hero.shake(12f, 0.6f) },
        LEGS to Beat("YOU HAVE NO IDEA WHAT YOU JUST STARTED.", Mood.Angry, true) { growLegs() },
        DODGE to Beat("Come closer. I dare you.", Mood.Smug, false, null),
        NOPE to Beat("New rule: sometimes I just say NOPE.", Mood.Smug, false, null),
        CRACKS to Beat("Do you hear that?! That's my HOUSE.", Mood.Scared, false) { crackHouse() },
        FRIENDS to Beat("THAT'S IT. I'M CALLING MY FRIENDS.", Mood.Angry, false) { callFriends() },
        CRASH to Beat(null, null, true) { crashHouse() },
        CRY to Beat("*sniff* …that was my favourite lamp.", Mood.Sad, false, null),
        PUDDLE to Beat("I'm crying so hard the floor is wet.", Mood.Sad, false) { puddle = true },
        FAMILY to Beat("My family came to beg you. Look at their little faces.", Mood.Sad, false) { spawnFamily() },
        DEAL to Beat(null, null, true) { offerDeal() },
        FUNERAL to Beat(null, null, true) { funeral() },
        DUSK to Beat("You know what? I'm done being nice.", Mood.Angry, false) { duskOn = true },
        ARMY to Beat("Meet my army. Only one of us is real. Pop the fakes if you can.", Mood.Smug, false) { decoysOn = true },
        DISGUISE to Beat("I'm in disguise now. You'll never find me.", Mood.Smug, true, null),
        CHASE to Beat("CATCH ME IF YOU CAN!", Mood.Smug, true) { startChase() },
        CHASE_END to Beat("*pant* okay… okay… back to hiding.", Mood.Annoyed, false) { endChase() },
        COLLAPSE to Beat("There goes the rest of my house. Hope you're proud.", Mood.Angry, false) { collapseHouse() },
        BEG to Beat(null, null, true) { beg() },
        BOSS_AT to Beat(null, null, true) { startBoss() },
    )
    private val beats = story.keys.sorted()

    private fun disguised(n: Int) = n >= DISGUISE && n < DISGUISE + 3

    private fun stamina(n: Int) = if (n < DODGE) 0 else if (n < CRY) 2 else if (n < DUSK + 1) 1 else 3
    private fun reject(n: Int) = if (n < NOPE || n >= BEG - 1) 0f else if (n < CRY) 0.3f else if (n < DUSK + 1) 0.2f else 0.25f
    private fun crying(n: Int) = n >= CRY && n < DUSK
    private fun pool(n: Int) = if (n < CRY) REBEL else if (n < DUSK) CRYING else WAR

    private fun defaultMood(n: Int): Mood = when {
        n == 0 -> Mood.Calm
        n < 3 -> Mood.Annoyed
        n < CRY -> if (Random.nextInt(3) == 0) Mood.Annoyed else if (Random.nextBoolean()) Mood.Angry else Mood.Smug
        n < DUSK -> Mood.Sad
        else -> if (Random.nextBoolean()) Mood.Angry else Mood.Smug
    }

    init {
        hero.mood = Mood.Calm
        hero.ease = 7f
        say("Please don't press me.", true)
    }

    /* ---------- helpers ---------- */

    private fun later(sec: Float, fn: () -> Unit) {
        laters.add(Later(t + sec, fn))
    }

    private fun say(str: String, beat: Boolean) {
        line = str
        lineAge = 0f
        lineBeat = beat
        wrap(str)
    }

    private fun wrap(str: String) {
        lineRows.clear()
        if (str.isEmpty()) return
        val size = 19f
        var cur = StringBuilder()
        for (word in str.split(' ')) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (Txt.width(next, size) > W - 70f && cur.isNotEmpty()) {
                lineRows.add(cur.toString())
                cur = StringBuilder(word)
            } else {
                cur = StringBuilder(next)
            }
        }
        if (cur.isNotEmpty()) lineRows.add(cur.toString())
    }

    // Somewhere to stand, at least minD from (ax, ay) if it can.
    private val spotX = FloatArray(1)
    private val spotY = FloatArray(1)
    private fun spot(ax: Float, ay: Float, minD: Float) {
        var bx = W / 2f
        var by = H * 0.6f
        var bestD = -1f
        for (i in 0 until 24) {
            val x = rand(AX0, AX1)
            val y = rand(AY0, AY1)
            val d = hypot(x - ax, y - ay)
            if (d >= minD) {
                bx = x
                by = y
                break
            }
            if (d > bestD) {
                bestD = d
                bx = x
                by = y
            }
        }
        spotX[0] = bx
        spotY[0] = by
    }

    private fun moveHero(x: Float, y: Float) {
        hero.place(x, y)
        followers()
    }

    private fun hop() {
        spot(hero.tx, hero.ty, 200f)
        moveHero(spotX[0], spotY[0])
        sfx.play("whoosh")
    }

    private fun center() = moveHero(W / 2f, H * 0.58f)

    private fun doPunch() {
        punch = 1f
    }

    /* ---------- pressing the button ---------- */

    private fun onHero(x: Float, y: Float) {
        idleT = 0f
        if (mode == POST) return postClick()
        if (mode != STORY || locked) return
        val special = chase || disguise >= 0
        if (!special && !justRejected && Random.nextFloat() < reject(clicks)) {
            justRejected = true
            sfx.play("nope")
            floatText(x, y - 30f, pick(REJECT), true)
            hero.shake(6f)
            hero.mood = Mood.Smug
            later(0.25f) { if (mode == STORY && !locked && !chase && disguise < 0) hop() }
            return
        }
        justRejected = false
        dodges = 0
        clicks++
        sfx.play("click")
        haptics.tap()
        hero.press()
        pressFx(x, y, look.color.toInt())
        floatText(x, y - 40f, "+1")
        advance(clicks)
    }

    private fun advance(n: Int) {
        val ev = story[n]
        if (ev != null) {
            ev.say?.let { say(it, true) }
            if (ev.run != null) doPunch()
            ev.mood?.let { hero.mood = it }
            ev.run?.invoke()
        } else {
            say(pick(pool(n)), false)
            hero.mood = defaultMood(n)
        }
        hero.tears = mode == STORY && crying(n)
        if (disguised(n)) setDisguise(n - DISGUISE)
        else if (n >= LEGS && !chase && !locked && ev?.stay != true) hop()
        if (decoysOn) {
            syncDecoys()
            shuffleDecoys()
        }
    }

    // Once it has legs it jumps away from your finger, until it runs out of breath.
    private fun tryDodge(fx: Float, fy: Float): Boolean {
        if (mode != STORY || locked || chase || disguise >= 0 || tiredT > 0f) return false
        val st = stamina(clicks)
        if (st == 0 || t - lastDodge < 0.4f) return false
        if (++dodges > st) {
            tiredT = 2.2f
            dodges = 0
            hero.mood = Mood.Tired
            say(pick(TIRED), false)
            return false
        }
        lastDodge = t
        spot(fx, fy, 220f)
        moveHero(spotX[0], spotY[0])
        sfx.play("whoosh")
        return true
    }

    /* ---------- friends & family ---------- */

    private fun spawnFriends(names: Array<String>, signs: Array<String>?, spread: Float) {
        val list = names.indices.filter { names[it] !in popped }
        list.forEachIndexed { i, k ->
            val f = PdChar(rand(0f, W), H + 80f, PD_FRIEND_LOOKS[k % 5], 84f)
            f.legs = true
            f.name = names[k]
            f.sign = signs?.get(k)
            f.mood = if (signs != null) Mood.Sad else Mood.Angry
            f.ease = rand(4f, 6f)
            val a = i.toFloat() / list.size * TAU
            f.dx = cos(a) * spread
            f.dy = sin(a) * spread * 0.7f
            friends.add(f)
        }
        followers()
    }

    private fun spawnFamily() = spawnFriends(FAMILY_NAMES, FAMILY_SIGNS, 70f)

    // Friends crowd around the real button to shield it.
    private fun followers() {
        for (f in friends) {
            f.place(
                clamp(hero.tx + f.dx * 1.15f + rand(-14f, 14f), AX0 - 20f, AX1 + 20f),
                clamp(hero.ty + f.dy * 1.15f + rand(-14f, 14f), AY0 - 30f, AY1),
            )
        }
    }

    private fun popFriend(f: PdChar) {
        if (f.gone) return
        sfx.play("pop")
        haptics.tap()
        f.pop()
        popped.add(f.name)
        pressFx(f.x, f.y, f.look.color.toInt())
        floatText(f.x, f.y - 30f, "pop!")
        hero.mood = Mood.Sad
        val n = f.name
        say(pick(arrayOf("NOOO! Not $n!", "$n!! You monster.", "RIP $n. Gone too soon.", "$n owed me money!")), false)
    }

    private fun dismissFriends() {
        for (f in friends) {
            f.place(f.x + rand(-300f, 300f), H + 160f)
            f.gone = true
        }
        val leaving = ArrayList(friends)
        friends.clear()
        leavers.addAll(leaving)
        later(0.9f) { leavers.removeAll(leaving.toSet()) }
    }

    private val leavers = ArrayList<PdChar>()

    /* ---------- missing the button ---------- */

    private fun onMiss(x: Float, y: Float) {
        if (mode != STORY || locked) return
        idleT = 0f
        dents.add(Dent(x, y, rand(0f, 360f), Random.nextFloat() * 100f))
        while (dents.size > 40) dents.removeAt(0)
        var knocked: String? = null
        for (i in DECOR_E.indices) {
            if (hypot(x - DECOR_X[i], y - DECOR_Y[i]) > 70f) continue
            knocks[i]++
            if (knockDir[i] == 0f) knockDir[i] = if (Random.nextBoolean()) 1f else -1f
            knocked = DECOR_N[i]
        }
        if (t - lastMissSound > 0.18f) {
            lastMissSound = t
            sfx.play("thud")
        }
        shake(3f)
        for (i in 0 until 6) {
            val a = rand(-PI_F, 0f)
            fx.mote(x, y, col("#efe0cc"), cos(a) * rand(40f, 90f), sin(a) * rand(40f, 90f) + 40f, rand(3f, 5f), rand(0.45f, 0.7f), add = false, drag = 2f)
        }
        if (t - lastMissLine > 3f) {
            lastMissLine = t
            val k = knocked
            say(if (k != null) pick(arrayOf("Not my $k!", "Leave my $k alone!", "That $k was a GIFT.")) else pick(MISS), false)
            if (!chase && disguise < 0) hero.mood = Mood.Angry
        }
    }

    /* ---------- story events ---------- */

    private fun growLegs() {
        hero.legs = true
        hero.shake(6f)
        later(0.9f) { if (!chase && !locked) hop() }
    }

    private fun crackHouse() {
        cracked = true
        sfx.play("crack")
        haptics.thud()
        shake(6f)
    }

    private fun callFriends() {
        for (i in 0 until 3) later(i * 0.18f) { sfx.play("tick") }
        later(0.6f) { spawnFriends(PD_FRIEND_NAMES, null, 85f) }
    }

    private fun crashHouse() {
        locked = true
        dismissFriends()
        sfx.play("boom")
        haptics.thud()
        shake(14f)
        overlay = O_CRASH
        crashMsg = "Attempting recovery…"
        for (i in CRASH_STEPS.indices) later(0.65f * (i + 1)) { crashMsg = CRASH_STEPS[i] }
        later(0.65f * CRASH_STEPS.size + 0.8f) {
            overlay = O_NONE
            ruined = true
            sfx.play("crack")
            shake(8f)
            center()
            hero.mood = Mood.Angry
            say("YOU. BROKE. MY. HOUSE.", true)
            locked = false
        }
    }

    private fun offerDeal() {
        locked = true
        center()
        hero.mood = Mood.Calm
        say("Okay, okay. Let's make a deal.", true)
        overlay = O_DEAL
        dealRip = 0f
    }

    private fun answerDeal(yes: Boolean) {
        if (dealRip > 0f) return
        dealRip = 0.01f
        sfx.play("crack")
        later(0.5f) {
            overlay = O_NONE
            hero.mood = Mood.Smug
            say(if (yes) "LOL. You actually signed it? I lied. Obviously." else "Wow. Rude. Fine, the war continues.", false)
            locked = false
            hop()
        }
    }

    private fun listNames(names: List<String>): String = when {
        names.size == 1 -> names[0]
        names.size <= 3 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} others"
    }

    private fun funeral() {
        locked = true
        dismissFriends()
        center()
        hero.mood = Mood.Sad
        graves = ArrayList(popped.take(9))
        sfx.play("sob")
        say(if (popped.isNotEmpty()) "A moment of silence for ${listNames(popped)}." else "Nobody got popped. You're weirdly nice. I still hate you.", true)
        later(3.4f) {
            say("…okay. Moment over.", false)
            locked = false
        }
    }

    /* ---------- the war ---------- */

    // Popping a fake: it stays popped until your next real hit, when the army regroups.
    private fun decoyHit(d: PdChar) {
        if (d.gone) return
        sfx.play("pop")
        haptics.tap()
        d.pop()
        pressFx(d.x, d.y, col("#ffd278"))
        floatText(d.x, d.y - 40f, pick(arrayOf("pop!", "fake!", "one down", "bye clone")), true)
        if (decoys.none { !it.gone }) say("You popped my WHOLE army?! …they'll be back.", false)
        else if (Random.nextFloat() < 0.3f) say(pick(arrayOf("Hey! That was a perfectly good clone.", "I have more where that came from.", "Stop popping my army!")), false)
    }

    private fun syncDecoys() {
        decoys.removeAll { it.popped }
        val want = if (decoysOn) min(14, 5 + floor((clicks - ARMY) * 0.7f).toInt()) else 0
        while (decoys.count { !it.gone } < want) {
            spot(hero.tx, hero.ty, 110f)
            val d = PdChar(spotX[0], spotY[0], look, 96f)
            d.legs = true
            d.blink = false // the real one blinks; the fakes never do
            d.mood = Mood.Smug
            d.alpha = if (chase) 0f else 1f
            decoys.add(d)
        }
        while (decoys.count { !it.gone } > want) decoys.remove(decoys.last { !it.gone })
    }

    private fun shuffleDecoys() {
        for (d in decoys) {
            if (d.gone) continue
            spot(hero.tx, hero.ty, 110f)
            d.place(spotX[0], spotY[0])
            d.mood = when (Random.nextInt(3)) {
                0 -> Mood.Smug
                1 -> Mood.Angry
                else -> Mood.Calm
            }
        }
    }

    private val disguiseLabel = arrayOf("🍪 Accept cookies 👀", "❓ Help 👀", "✕ 👀")
    private val disguiseX = floatArrayOf(W / 2f, W - 80f, W - 50f)
    private val disguiseY = floatArrayOf(H - 50f, H - 60f, 200f)

    private fun setDisguise(i: Int) {
        disguise = i
        if (i < 0) {
            fakeBig = null
            return
        }
        hero.place(disguiseX[i], disguiseY[i])
        if (fakeBig == null) {
            fakeBig = PdChar(W / 2f, H * 0.5f, look, 170f).also {
                it.legs = true
                it.mood = Mood.Smug
            }
        }
    }

    private fun disguiseRect(r: RectF) {
        val i = disguise.coerceIn(0, 2)
        val w = Txt.width(disguiseLabel[i], 16f) + 30f
        val x = clamp(hero.x, w / 2f + 8f, W - w / 2f - 8f)
        r.set(x - w / 2f, hero.y - 20f, x + w / 2f, hero.y + 20f)
    }

    private fun startChase() {
        setDisguise(-1)
        chase = true
        chaseAngle = rand(0f, TAU)
        hero.ease = 30f
        for (d in decoys) d.alpha = 0f
    }

    private fun endChase() {
        chase = false
        hero.ease = 7f
        for (d in decoys) d.alpha = 1f
    }

    private fun collapseHouse() {
        collapsed = true
        collapseT = 0f
        sfx.play("boom")
        haptics.thud()
        shake(16f)
    }

    private fun beg() {
        locked = true
        decoysOn = false
        for (d in decoys) d.pop()
        center()
        hero.mood = Mood.Scared
        hero.tremble = true
        say("Wait. WAIT. Please. I'll be good. Mercy?", true)
        later(2.6f) { locked = false }
    }

    /* ---------- skipping ---------- */

    private fun canSkip(): Boolean = when (mode) {
        STORY -> !locked && overlay == O_NONE && clicks < BOSS_AT
        BOSS -> phase != null && !busy && overlay == O_NONE
        else -> false
    }

    private fun openSkip() {
        if (!canSkip()) return
        skipBoss = mode == BOSS
        skipTitle = if (skipBoss) "Skip this round?" else "Skip ahead?"
        skipPhrase = pick(SKIP_PHRASES)
        skipHeld = 0f
        skipHold = false
        overlay = O_SKIP
    }

    private fun doSkip() {
        overlay = O_NONE
        skips++
        stamp(if (skipBoss) "can't even play kid games" else "can't even press a button")
        if (skipBoss) {
            if (phase != null && !busy) hit(pick(LOSER_LINES), 1)
            return
        }
        if (mode != STORY || locked) return
        val target = beats.firstOrNull { it > clicks } ?: return
        justRejected = false
        dodges = 0
        clicks = target
        sfx.play("click")
        advance(clicks)
    }

    private fun stamp(why: String) {
        stampText = why
        stampT = 2.4f
        sfx.play("bad")
        shake(8f)
    }

    /* ---------- the boss ---------- */

    private fun startBoss() {
        locked = true
        mode = BOSS
        hero.tremble = false
        hero.tears = false
        overlay = O_BLACK
        bo1 = ""
        bo2 = ""
        sfx.play("boom")
        later(1f) { bo1 = "YOU WANTED A BUTTON?" }
        later(2.5f) {
            bo2 = "HERE'S YOUR BUTTON."
            sfx.play("boom")
            haptics.thud()
            shake(14f)
        }
        later(4.2f) {
            overlay = O_NONE
            bossScene = true
            friends.clear()
            decoys.clear()
            next(true)
        }
    }

    private fun next(fresh: Boolean = false) {
        if (over || mode != BOSS) return
        val hits = clicks - BOSS_AT
        if (hits >= BOSS_HITS) return win()
        val p = hits / 3
        val round = hits % 3
        clearPhase()
        if (round == 0 || fresh) {
            overlay = O_PHASE
            phaseCard = p
            sfx.play("boom")
            shake(8f)
            later(2.6f) {
                overlay = O_NONE
                startPhase(p, round)
            }
        } else {
            later(0.3f) { startPhase(p, round) }
        }
    }

    private fun startPhase(p: Int, round: Int) {
        if (over || mode != BOSS) return
        val done: () -> Unit = { hit(null, 1) }
        glitchy = p == 4 && round == 0
        rave = p == 4 && round == 2
        phase = try {
            when (p) {
                0 -> BalloonPhase(host, round, done)
                1 -> ClimbPhase(host, round, done)
                2 -> WhackPhase(host, round, done)
                3 -> ShellsPhase(host, round, done)
                else -> when (round) {
                    0 -> SimonPhase(host, done)
                    1 -> DefusePhase(host, done)
                    else -> MashPhase(host, done)
                }
            }
        } catch (_: Throwable) {
            null
        }
        if (phase == null) later(0.5f) { hit(null, 1) }
    }

    private fun clearPhase() {
        phase?.stop()
        phase = null
        glitchy = false
        rave = false
    }

    private fun hit(lineStr: String?, count: Int) {
        if (busy) return
        busy = true
        // the phase keeps drawing its last frame for a beat, then goes
        later(0.35f) { clearPhase() }
        clicks = min(GOAL, clicks + count)
        sfx.play("hit")
        haptics.thud()
        shake(12f)
        flash(col("#ffd9c9"))
        say(lineStr ?: HIT_LINES[(clicks - BOSS_AT - 1).coerceIn(0, HIT_LINES.size - 1)], true)
        if (overlay == O_SKIP) overlay = O_NONE
        later(1.5f) {
            busy = false
            next()
        }
    }

    private val bigBoss = PdChar(W / 2f, H / 2f, bossLook, 200f)
    private var winning = false
    private var winAt = 0f

    private fun win() {
        clearPhase()
        winning = true
        winAt = t
        bigBoss.jump(W / 2f, H / 2f)
        bigBoss.mood = Mood.Scared
        bigBoss.scale = 1f
        bigBoss.bright = 0f
        say("No… no no no NO NO NOOOOO—", true)
        for (i in 0 until 6) later(i * 0.38f) {
            shake(6f + i * 3f)
            sfx.play("hit")
            haptics.tap()
            bigBoss.shake(8f + i * 2f, 0.3f)
        }
        later(2.3f + 1.8f) {
            winning = false
            flash(WHITE)
            fx.ring(W / 2f, H / 2f, col("#fff2d8"), 420f, 0.8f, 18f)
            later(0.14f) { fx.ring(W / 2f, H / 2f, col("#ffd0a0"), 380f, 0.8f, 12f) }
            for (k in CONFETTI) fx.burst(W / 2f, H / 2f, k, 28, 620f, gravity = 520f, size = 3.4f, life = 1.8f, drag = 1.2f, up = 200f)
            sfx.play("boom")
            sfx.play("win")
            haptics.thud()
            bossScene = false
            mode = END
            overlay = O_WIN
            winTwist = false
            winSkips = skips
            say("", false)
            later(3.5f) { winTwist = true }
            later(6.3f) {
                overlay = O_NONE
                startPost()
            }
        }
    }

    /* ---------- after the "win" ---------- */

    private fun startPost() {
        mode = POST
        postScene = true
        collapsed = false
        duskOn = false
        cracked = true
        ruined = true
        hero.legs = true
        hero.bandaged = true
        hero.tremble = false
        hero.tears = false
        hero.mood = Mood.Calm
        hero.jump(W / 2f, H + 120f)
        hero.ease = 5f
        center()
        say("20 more presses. Just to make sure I'm dead.", true)
    }

    private fun postClick() {
        if (postN >= POST_GOAL) return
        postN++
        sfx.play("click")
        haptics.tap()
        hero.press()
        pressFx(hero.x, hero.y, col("#aae1be"))
        say(POST_LINES[postN - 1], false)
        hero.mood = if (postN <= 5 || (postN in 16..19)) Mood.Calm else Mood.Smug
        if (postN in 16..19) loadPct = floatArrayOf(0.25f, 0.5f, 0.75f, 0.99f)[postN - 16]
        if (postN == POST_GOAL) {
            loadPct = 1f
            floatText(hero.x, hero.y - 90f, "??? 🎁", true)
            sfx.play("win")
            later(1.3f) {
                overlay = O_PRIZE
                prizeT = 0f
                hero.mood = Mood.Happy
                say("Never gonna give you up. 🎵", true)
                for (k in CONFETTI) fx.burst(W / 2f, H * 0.4f, k, 16, 420f, gravity = 500f, size = 3f, life = 1.5f, drag = 1.2f, up = 150f)
            }
        }
    }

    private fun finish() {
        if (over) return
        overlay = O_NONE
        mode = END
        over = true
    }

    private fun giveUp() {
        overlay = O_NONE
        clearPhase()
        locked = true
        mode = END
        hero.mood = Mood.Smug
        hero.tremble = false
        say("HA! I knew it. Bye-bye. Don't come back.", true)
        sfx.play("nope")
        over = true
    }

    /* ---------- input ---------- */

    private val r = RectF()
    private val hudGiveUp = RectF(12f, 20f, 56f, 64f)
    private val hudSkip = RectF(62f, 20f, 106f, 64f)
    private val btnA = RectF()
    private val btnB = RectF()

    private fun inR(rr: RectF, x: Float, y: Float, pad: Float = 6f) = x >= rr.left - pad && x <= rr.right + pad && y >= rr.top - pad && y <= rr.bottom + pad

    override fun down(x: Float, y: Float) {
        if (over) return
        pressing = true
        pressOnHero = false
        idleT = 0f
        when (overlay) {
            O_DEAL -> {
                if (inR(btnA, x, y)) answerDeal(true) else if (inR(btnB, x, y)) answerDeal(false)
                return
            }
            O_SKIP -> {
                if (inR(btnA, x, y)) {
                    skipHold = true
                } else if (inR(btnB, x, y)) {
                    overlay = O_NONE
                    say(if (skipBoss) "Respect. Now get back in there." else "Good. Keep suffering.", false)
                }
                return
            }
            O_GIVEUP -> {
                if (inR(btnA, x, y)) giveUp() else if (inR(btnB, x, y)) {
                    overlay = O_NONE
                    say("Ugh. Fine. Keep going, then.", false)
                }
                return
            }
            O_PRIZE -> {
                if (prizeT > 1.2f) finish()
                return
            }
            O_NONE -> Unit
            else -> return
        }
        // the HUD
        if (inR(hudGiveUp, x, y) && mode != END) {
            overlay = O_GIVEUP
            return
        }
        if (inR(hudSkip, x, y) && canSkip()) {
            openSkip()
            return
        }
        when (mode) {
            BOSS -> phase?.let { if (!busy) it.down(x, y) }
            POST -> if (hero.hit(x, y, 1.25f)) onHero(x, y)
            STORY -> storyDown(x, y)
        }
    }

    private fun storyDown(x: Float, y: Float) {
        // the topmost first: friends and clones stand in front of the button
        for (i in friends.indices.reversed()) {
            val f = friends[i]
            if (f.hit(x, y, 0.95f)) return popFriend(f)
        }
        for (i in decoys.indices.reversed()) {
            val d = decoys[i]
            if (d.alpha > 0.5f && d.hit(x, y, 1.05f)) return decoyHit(d)
        }
        val heroHit = if (disguise >= 0) {
            disguiseRect(r)
            inR(r, x, y, 8f)
        } else {
            hero.hit(x, y, 1.2f)
        }
        if (heroHit) {
            pressOnHero = true
            // it sees your finger coming, sometimes
            if (disguise < 0 && !chase && Random.nextFloat() < 0.55f && tryDodge(x, y)) {
                floatText(x, y - 30f, pick(DODGED), true)
                return
            }
            return onHero(x, y)
        }
        fakeBig?.let {
            if (it.hit(x, y)) {
                sfx.play("bad")
                floatText(x, y - 40f, pick(arrayOf("that's not me lol", "decoy!", "nice try")), true)
                it.shake(6f)
                return
            }
        }
        onMiss(x, y)
    }

    override fun move(x: Float, y: Float) {
        if (over) return
        idleT = 0f
        if (mode == BOSS && overlay == O_NONE) phase?.move(x, y)
        // a finger sliding toward it makes it bolt
        if (mode == STORY && overlay == O_NONE && pressing && !pressOnHero && hypot(x - hero.x, y - hero.y) < 85f) tryDodge(x, y)
    }

    override fun up(x: Float, y: Float) {
        pressing = false
        skipHold = false
        if (mode == BOSS) phase?.up(x, y)
    }

    /* ---------- the frame ---------- */

    override fun update(dt: Float) {
        if (!(dt > 0f)) return
        t += dt
        lineAge += dt
        fx.update(dt)
        punch = max(0f, punch - dt * 1.6f)
        shakeAmt = max(0f, shakeAmt - dt * 30f)
        flashLeft -= dt
        stampT -= dt
        tiredT -= dt
        if (dealRip > 0f) dealRip += dt
        dusk += ((if (duskOn) 1f else 0f) - dusk) * min(1f, dt * 1.2f)
        if (collapsed) collapseT += dt
        if (overlay == O_PRIZE) {
            prizeT += dt
            if (prizeT > 9f) finish()
        }

        // timers (nothing more happens once it is over)
        if (over) laters.clear()
        var i = 0
        var guard = 0
        while (i < laters.size && guard++ < 200) {
            val l = laters[i]
            if (l.at <= t) {
                laters.removeAt(i)
                try {
                    l.fn()
                } catch (_: Throwable) {
                }
            } else {
                i++
            }
        }

        if (overlay == O_SKIP) {
            skipHeld = if (skipHold) skipHeld + dt else max(0f, skipHeld - dt * 2f)
            if (skipHeld >= 1.6f) doSkip()
        }

        if (tiredT <= 0f && hero.mood == Mood.Tired && mode == STORY) hero.mood = defaultMood(clicks)

        hero.update(dt)
        // running around on its own
        if (chase && mode == STORY) {
            if (Random.nextFloat() < dt * 0.8f) chaseAngle += rand(-1.2f, 1.2f)
            val speed = 280f + (clicks - CHASE) * 50f
            var x = hero.x + cos(chaseAngle) * speed * dt
            var y = hero.y + sin(chaseAngle) * speed * dt
            if (x < AX0 || x > AX1) chaseAngle = PI_F - chaseAngle
            if (y < AY0 || y > AY1) chaseAngle = -chaseAngle
            x = clamp(x, AX0, AX1)
            y = clamp(y, AY0, AY1)
            hero.jump(x, y)
            hero.tx = x + cos(chaseAngle) * 30f // keeps its legs going
            hero.ty = y + sin(chaseAngle) * 30f
        }
        for (f in friends) f.update(dt)
        for (f in leavers) f.update(dt)
        friends.removeAll { it.popped }
        for (d in decoys) d.update(dt)
        fakeBig?.update(dt)
        if (winning) {
            bigBoss.update(dt)
            val k = clamp((t - winAt - 2.3f) / 1.8f, 0f, 1f)
            if (k > 0f) {
                bigBoss.scale = 1f + 0.8f * k
                bigBoss.bright = k
            }
        }

        if (mode == STORY) {
            // tears while it's sad
            if (crying(clicks) && !chase) {
                tearT -= dt
                if (tearT <= 0f) {
                    tearT = 0.7f
                    for (sd in intArrayOf(-1, 1)) fx.mote(hero.x + sd * 15f, hero.y - 4f, col("#8fd0ff"), 0f, 120f, 3f, 1.1f, add = false)
                }
            }
            // friends fidget around while they guard it
            fidgetT -= dt
            if (fidgetT <= 0f) {
                fidgetT = 1.5f
                if (friends.isNotEmpty() && !locked) followers()
            }
            // it notices when you go quiet
            idleT += dt
            if (idleT > 25f && clicks > 0 && !locked) {
                idleT = 0f
                say(pick(IDLE), false)
            }
        }
        if (mode == BOSS && !busy) phase?.update(dt) else if (mode == BOSS) phase?.update(dt * 0.3f)
    }

    /* ---------- drawing ---------- */

    private val solid = fill(WHITE)
    private val line2 = stroke(WHITE, 2f)
    private val path = Path()
    private val oval = RectF()
    private val wallPaint = fill().shade(lin(0f, 0f, 0f, FLOOR, 0f to col("#f8ecdb"), 0.7f to col("#f1dfc6"), 1f to col("#ead3b6")))
    private val floorPaint = fill().shade(lin(0f, FLOOR, 0f, H, 0f to col("#b98d66"), 0.4f to col("#cfa47c"), 1f to col("#d9b089")))
    private val skyDay = fill().shade(lin(0f, 150f, 0f, 260f, 0f to col("#8cc8f0"), 0.6f to col("#c6e6f7"), 1f to col("#fde9c8")))
    private val skyDusk = fill().shade(lin(0f, 150f, 0f, 260f, 0f to col("#4b3a78"), 0.5f to col("#c2577a"), 1f to col("#f59a5b")))
    private val arenaPaint = fill().shade(lin(0f, 0f, 0f, H, 0f to col("#2a1430"), 0.6f to col("#160a1c"), 1f to col("#0b050e")))
    private val crackPaint = stroke(col("#7a5a46"), 2.2f)
    private val crackHl = stroke(rgba(255, 255, 255, 0.6f), 2.2f)
    private val cardFill = fill().shade(lin(0f, 260f, 0f, 560f, 0f to col("#fffdf8"), 1f to col("#fbf1e3")))
    private val cardLine = stroke(col("#e8cfa2"), 2f)
    private val pillFill = fill(col("#e2674f"))
    private val pillGhost = fill(rgba(120, 80, 60, 0.14f))
    private val hudBg = fill(rgba(255, 255, 255, 0.72f))
    private val hudDark = fill(rgba(30, 14, 40, 0.55f))
    private var plantEmoji: String? = null
    private val framePaint = fill(col("#d9bd98"))
    private val windowPaint = fill(col("#f4f4f8"))
    private val pillWhite = fill(rgba(255, 255, 255, 0.92f))

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        val sx = shakeBy(shakeAmt)
        val sy = shakeBy(shakeAmt)
        c.save()
        c.translate(sx, sy)
        if (punch > 0f) {
            val k = 1f + 0.03f * punch * punch
            c.scale(k, k, W / 2f, H / 2f)
        }
        if (bossScene || (mode == BOSS && overlay != O_BLACK) || (mode == END && overlay == O_WIN)) arena(c) else room(c)

        when (mode) {
            BOSS -> phase?.draw(this, c)
            else -> Unit
        }
        if (winning) bigBoss.draw(this, c, t)
        if (mode == STORY || mode == POST || (mode == END && !bossScene && overlay != O_WIN)) cast(this, c)
        c.restore()

        fx.draw(c)
        if (flashLeft > 0f) {
            solid.color = rgba(flashColor, min(0.6f, flashLeft * 1.4f))
            c.drawRect(0f, 0f, W, H, solid)
        }
        dialogue(c)
        hud(c)
        drawOverlay(c)
        if (stampT > 0f) loserStamp(c)
    }

    private fun room(c: Canvas) {
        c.drawRect(0f, 0f, W, FLOOR, wallPaint)
        // the window, the sky in it going to evening
        val wx0 = 150f
        val wy0 = 150f
        val wx1 = 250f
        val wy1 = 262f
        c.drawRoundRect(wx0 - 9f, wy0 - 9f, wx1 + 9f, wy1 + 9f, 6f, 6f, framePaint)
        c.drawRect(wx0, wy0, wx1, wy1, skyDay)
        if (dusk > 0.01f) {
            skyDusk.alpha = (dusk * 255f).toInt()
            c.drawRect(wx0, wy0, wx1, wy1, skyDusk)
        }
        val sunY = wy0 + 32f + dusk * 70f
        c.save()
        c.clipRect(wx0, wy0, wx1, wy1)
        c.glow(wx1 - 30f, sunY, 40f, if (dusk > 0.5f) col("#ffd27a") else col("#fff7d6"), 0.9f)
        c.restore()
        solid.color = col("#fbf3e6")
        c.drawRect((wx0 + wx1) / 2f - 3f, wy0, (wx0 + wx1) / 2f + 3f, wy1, solid)
        c.drawRect(wx0, (wy0 + wy1) / 2f - 3f, wx1, (wy0 + wy1) / 2f + 3f, solid)
        // the sunbeam falling across the room
        path.reset()
        path.moveTo(wx0, wy1)
        path.lineTo(wx1, wy1)
        path.lineTo(wx1 + 160f, FLOOR + 120f)
        path.lineTo(wx0 + 40f, FLOOR + 120f)
        path.close()
        solid.color = if (dusk > 0.5f) rgba(255, 160, 110, 0.16f) else rgba(255, 246, 214, 0.22f)
        c.drawPath(path, solid)
        for (i in 0 until 6) {
            val fi = i.toFloat()
            val mx = wx0 + 30f + hash(fi) * 200f + sin(t * 0.3f + fi) * 12f
            val my = wy1 + 40f + ((hash(fi + 4f) * 300f + t * 8f) % 320f)
            solid.color = rgba(255, 252, 235, 0.55f)
            c.drawCircle(mx, my, 1.8f, solid)
        }

        // a hole punched in the wall once it's ruined
        if (ruined) {
            path.reset()
            val hx = 90f
            val hy = 420f
            for (k in 0 until 12) {
                val a = k / 12f * TAU
                val rr = 34f + hash(k.toFloat() + 7f) * 18f
                val px = hx + cos(a) * rr * 1.3f
                val py = hy + sin(a) * rr
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            solid.color = col("#3a2a22")
            c.drawPath(path, solid)
            line2.color = col("#c9ab8a")
            line2.strokeWidth = 3f
            c.drawPath(path, line2)
        }
        if (cracked) {
            for (d in CRACKS_D) {
                path.reset()
                var k = 0
                while (k < d.size) {
                    val px = d[k] / 100f * W
                    val py = d[k + 1] / 100f * FLOOR
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    k += 2
                }
                c.save()
                c.translate(1f, 1f)
                c.drawPath(path, crackHl)
                c.restore()
                c.drawPath(path, crackPaint)
            }
        }
        for (d in dents) dent(c, d)

        // the floor, the skirting board and a rug
        c.drawRect(0f, FLOOR, W, H, floorPaint)
        solid.color = col("#fbf2e4")
        c.drawRect(0f, FLOOR - 7f, W, FLOOR - 2f, solid)
        solid.color = col("#d7b994")
        c.drawRect(0f, FLOOR - 2f, W, FLOOR, solid)
        line2.color = rgba(120, 80, 50, 0.18f)
        line2.strokeWidth = 1.5f
        var fy = FLOOR + 30f
        while (fy < H) {
            c.drawLine(0f, fy, W, fy, line2)
            fy += 34f + (fy - FLOOR) * 0.1f
        }
        oval.set(90f, 680f, 390f, 770f)
        solid.color = col("#c45a43")
        c.drawOval(oval, solid)
        oval.inset(14f, 6f)
        solid.color = col("#f0c36d")
        c.drawOval(oval, solid)
        oval.inset(10f, 5f)
        solid.color = col("#b84a36")
        c.drawOval(oval, solid)

        if (puddle) {
            val px = W / 2f
            val py = 720f
            oval.set(px - 150f, py - 26f, px + 150f, py + 26f)
            solid.color = rgba(110, 170, 220, 0.75f)
            c.drawOval(oval, solid)
            val rk = (t * 0.6f) % 1f
            line2.color = rgba(255, 255, 255, 0.5f * (1f - rk))
            line2.strokeWidth = 2f
            oval.set(px - 40f - rk * 90f, py - 8f - rk * 14f, px + 40f + rk * 90f, py + 8f + rk * 14f)
            c.drawOval(oval, line2)
        }
        for ((i, g) in graves.withIndex()) grave(c, 50f + i * 46f, 652f + (i % 2) * 14f, g)
        decor(c)
        if (dusk > 0.01f) {
            solid.color = rgba(255, 130, 100, 0.14f * dusk)
            c.drawRect(0f, 0f, W, H, solid)
        }
        if (collapsed) {
            val k = clamp(collapseT / 1.2f, 0f, 1f)
            solid.color = rgba(40, 22, 40, 0.42f * k)
            c.drawRect(0f, 0f, W, H, solid)
            if (Random.nextFloat() < 0.3f) fx.mote(rand(0f, W), -10f, col("#d9c6ad"), rand(-10f, 10f), rand(30f, 70f), rand(2f, 4f), 6f, add = false)
        }
        if (postScene) c.softLight(W / 2f, H * 0.55f, 300f, col("#fff2d0"), 0.35f)
    }

    private fun decor(c: Canvas) {
        if (plantEmoji == null) plantEmoji = if (Paint().hasGlyph("🪴")) "🪴" else "🌵"
        for (i in DECOR_E.indices) {
            var x = DECOR_X[i]
            var y = DECOR_Y[i]
            var rot = 0f
            if (ruined) rot = DECOR_TILT[i]
            rot += min(35f, knocks[i] * 7f) * knockDir[i]
            if (collapsed) {
                val k = smooth(clamp((collapseT - i * 0.05f) / 0.8f, 0f, 1f))
                rot += (DECOR_FALL[i] - rot) * k
                val floorY = if (i == 2) FLOOR + 40f else FLOOR + 30f + (i % 3) * 8f
                y += (max(y, floorY) - y) * k
                x += (if (x < W / 2f) -10f else 10f) * k
            }
            c.save()
            c.rotate(rot, x, y)
            if (i == 2) {
                // the shelf: a plank on two brackets
                solid.color = col("#b48259")
                c.drawRoundRect(x - 70f, y - 6f, x + 70f, y + 6f, 3f, 3f, solid)
                solid.color = col("#87603f")
                c.drawRect(x - 70f, y + 4f, x + 70f, y + 7f, solid)
            } else {
                val e = if (i == 5) plantEmoji ?: DECOR_E[i] else DECOR_E[i]
                Txt.draw(c, e, x, y, DECOR_S[i])
            }
            c.restore()
        }
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    private fun dent(c: Canvas, d: Dent) {
        c.softLight(d.x, d.y, 22f, col("#6e462d"), 0.35f)
        line2.color = col("#7a5a46")
        line2.strokeWidth = 1.6f
        val rays = 4 + (d.seed % 3f).toInt()
        for (i in 0 until rays) {
            val a = i.toFloat() / rays * TAU + d.rot + (hash(d.seed + i) - 0.5f) * 0.8f
            val mid = 5f + hash(d.seed + i + 10f) * 4f
            val end = 11f + hash(d.seed + i + 20f) * 8f
            val kink = a + (hash(d.seed + i + 30f) - 0.5f)
            val mx = d.x + cos(a) * mid
            val my = d.y + sin(a) * mid
            c.drawLine(d.x, d.y, mx, my, line2)
            c.drawLine(mx, my, d.x + cos(kink) * end, d.y + sin(kink) * end, line2)
        }
    }

    private fun grave(c: Canvas, x: Float, y: Float, name: String) {
        solid.color = col("#9a9aa6")
        c.drawRoundRect(x - 16f, y - 30f, x + 16f, y + 8f, 14f, 14f, solid)
        c.drawRect(x - 16f, y - 12f, x + 16f, y + 8f, solid)
        solid.color = col("#c4c4cf")
        c.drawRoundRect(x - 13f, y - 27f, x + 13f, y + 6f, 12f, 12f, solid)
        Txt.draw(c, "RIP", x, y - 12f, 9f, col("#555562"), black = true)
        Txt.draw(c, name, x, y + 18f, 10f, col("#3a2a22"))
    }

    private fun arena(c: Canvas) {
        c.drawRect(0f, 0f, W, H, arenaPaint)
        // spotlights sweeping the dark
        for (i in 0 until 3) {
            val fi = i.toFloat()
            val x = W / 2f + sin(t * (0.4f + fi * 0.15f) + fi * 2f) * 180f
            val color = if (rave) hueShift(col("#ff4d3d"), (t * 140f + fi * 120f) % 360f) else col("#ff4d3d")
            c.softLight(x, H * 0.5f, 260f, color, if (rave) 0.35f else 0.12f)
        }
        if (glitchy) {
            for (i in 0 until 8) {
                if (Random.nextFloat() > 0.4f) continue
                solid.color = rgba(120, 255, 220, rand(0.03f, 0.12f))
                val y = rand(0f, H)
                c.drawRect(0f, y, W, y + rand(2f, 10f), solid)
            }
        }
        c.vignette(0.5f)
    }

    private fun cast(s: DrawScope, c: Canvas) {
        fakeBig?.draw(s, c, t)
        for (d in decoys) d.draw(s, c, t)
        if (disguise >= 0) {
            // the button pretending to be a bit of UI
            disguiseRect(r)
            c.drawRoundRect(r, 20f, 20f, pillWhite)
            line2.color = col("#d9bd98")
            line2.strokeWidth = 1.5f
            c.drawRoundRect(r, 20f, 20f, line2)
            Txt.draw(c, disguiseLabel[disguise.coerceIn(0, 2)], r.centerX(), r.centerY(), 16f, col("#3a2a22"))
        } else {
            c.shadow(hero.x, hero.y + hero.size * 0.62f, hero.size * 0.42f, 0.25f)
            hero.draw(s, c, t)
        }
        for (f in leavers) f.draw(s, c, t)
        for (f in friends) f.draw(s, c, t)
    }

    private fun dialogue(c: Canvas) {
        if (lineRows.isEmpty()) return
        val pop = if (lineBeat) 1f + 0.25f * clamp(1f - lineAge / 0.25f, 0f, 1f) else 1f + 0.08f * clamp(1f - lineAge / 0.15f, 0f, 1f)
        val a = clamp(lineAge / 0.12f, 0f, 1f)
        val size = 19f
        val top = 92f
        val h = lineRows.size * 25f + 14f
        val dark = bossScene || collapsed || mode == BOSS
        c.save()
        c.scale(pop, pop, W / 2f, top + h / 2f)
        solid.color = if (dark) rgba(20, 8, 26, 0.55f * a) else rgba(255, 252, 245, 0.78f * a)
        c.drawRoundRect(24f, top, W - 24f, top + h, 16f, 16f, solid)
        for ((i, row) in lineRows.withIndex()) {
            Txt.draw(c, row, W / 2f, top + 20f + i * 25f, size, if (dark) col("#fff4e8") else col("#3a2a22"), alpha = a, black = lineBeat)
        }
        c.restore()
    }

    private var counterFor = -1
    private var counterText = ""

    private fun hud(c: Canvas) {
        val key = if (mode == POST) 1000 + postN else clicks
        if (key != counterFor) {
            counterFor = key
            counterText = when {
                mode == POST -> "$postN / $POST_GOAL"
                clicks >= GOAL -> "$GOAL / $GOAL ✓"
                else -> "$clicks / $GOAL"
            }
        }
        val p = if (mode == POST) postN / POST_GOAL.toFloat() else min(1f, clicks / GOAL.toFloat())
        val cx = W / 2f
        c.drawRoundRect(cx - 74f, 24f, cx + 74f, 60f, 18f, 18f, hudBg)
        solid.color = col("#f0c36d")
        if (p > 0f) c.drawRoundRect(cx - 74f, 24f, cx - 74f + 148f * p, 60f, 18f, 18f, solid)
        Txt.draw(c, counterText, cx, 42f, 19f, col("#3a2a22"), black = true)
        Txt.draw(c, "please don't.", cx, 72f, 12f, if (bossScene) col("#ffb3a7") else col("#e2674f"), black = true)
        if (mode == BOSS || bossScene) {
            val hits = clicks - BOSS_AT
            for (i in 0 until BOSS_HITS) {
                val x = cx - (BOSS_HITS - 1) * 8f + i * 16f
                solid.color = if (i < hits) rgba(255, 255, 255, 0.18f) else col("#ff4d3d")
                c.drawCircle(x, 86f, if (i < hits) 3.5f else 5.5f, solid)
            }
        }
        if (mode != END) {
            c.drawRoundRect(hudGiveUp, 22f, 22f, hudDark)
            Txt.draw(c, "✕", hudGiveUp.centerX(), hudGiveUp.centerY(), 18f)
            Txt.draw(c, "give up", hudGiveUp.centerX(), hudGiveUp.bottom + 9f, 9f, rgba(255, 255, 255, 0.8f), rgba(30, 14, 40, 0.5f), 3f)
        }
        if (canSkip()) {
            c.drawRoundRect(hudSkip, 22f, 22f, hudDark)
            Txt.draw(c, "⏩", hudSkip.centerX(), hudSkip.centerY(), 16f)
            Txt.draw(c, "skip", hudSkip.centerX(), hudSkip.bottom + 9f, 9f, rgba(255, 255, 255, 0.8f), rgba(30, 14, 40, 0.5f), 3f)
        }
        if (mode == POST && loadPct >= 0f) {
            val y = H - 50f
            c.drawRoundRect(70f, y - 8f, W - 70f, y + 8f, 8f, 8f, hudDark)
            solid.color = col("#4fd06a")
            c.drawRoundRect(72f, y - 6f, 72f + (W - 144f) * loadPct, y + 6f, 6f, 6f, solid)
            Txt.draw(c, "Preparing your ultimate reward…", W / 2f, y - 22f, 14f, col("#3a2a22"))
        }
    }

    private fun card(c: Canvas, top: Float, bottom: Float) {
        c.shadow(W / 2f, bottom + 6f, 190f, 0.35f, 22f)
        c.drawRoundRect(36f, top, W - 36f, bottom, 22f, 22f, cardFill)
        c.drawRoundRect(36f, top, W - 36f, bottom, 22f, 22f, cardLine)
    }

    private fun pill(c: Canvas, rr: RectF, label: String, ghost: Boolean, progress: Float = -1f) {
        c.drawRoundRect(rr, rr.height() / 2f, rr.height() / 2f, if (ghost) pillGhost else pillFill)
        if (progress > 0f) {
            solid.color = col("#8e2a1a")
            c.save()
            c.clipRect(rr.left, rr.top, rr.left + rr.width() * clamp(progress, 0f, 1f), rr.bottom)
            c.drawRoundRect(rr, rr.height() / 2f, rr.height() / 2f, solid)
            c.restore()
        }
        Txt.draw(c, label, rr.centerX(), rr.centerY(), 16f, if (ghost) col("#3a2a22") else WHITE, black = true)
    }

    private fun dim(c: Canvas, a: Float) {
        solid.color = rgba(30, 16, 12, a)
        c.drawRect(0f, 0f, W, H, solid)
    }

    private fun drawOverlay(c: Canvas) {
        when (overlay) {
            O_CRASH -> {
                dim(c, 0.6f)
                // a frozen program window
                c.drawRoundRect(50f, 300f, W - 50f, 470f, 10f, 10f, windowPaint)
                solid.color = col("#2f5ac8")
                c.drawRoundRect(50f, 300f, W - 50f, 336f, 10f, 10f, solid)
                c.drawRect(50f, 320f, W - 50f, 336f, solid)
                Txt.draw(c, "home.exe", 70f, 318f, 15f, WHITE, align = Paint.Align.LEFT)
                Txt.draw(c, "✕", W - 70f, 318f, 15f, WHITE)
                Txt.draw(c, "Your house has stopped responding.", W / 2f, 380f, 17f, INK)
                Txt.draw(c, crashMsg, W / 2f, 420f, 14f, col("#5a5e70"))
            }
            O_DEAL -> {
                dim(c, 0.35f)
                val rip = clamp(dealRip / 0.5f, 0f, 1f)
                c.save()
                if (rip > 0f) c.rotate(rip * 12f, W / 2f, 420f)
                c.saveLayerAlpha(0f, 0f, W, H, ((1f - rip) * 255f).toInt())
                card(c, 290f, 540f)
                Txt.draw(c, "Peace Treaty 🕊", W / 2f, 330f, 26f, col("#3a2a22"), black = true)
                Txt.draw(c, "I stop running. You stop pressing.", W / 2f, 378f, 16f, col("#5a4436"))
                Txt.draw(c, "We both live happily ever after.", W / 2f, 402f, 16f, col("#5a4436"))
                btnA.set(70f, 450f, 230f, 500f)
                btnB.set(250f, 450f, 410f, 500f)
                pill(c, btnA, "Sign it ✍", false)
                pill(c, btnB, "No way", true)
                c.restore()
                c.restore()
            }
            O_BLACK -> {
                solid.color = 0xFF050208.toInt()
                c.drawRect(0f, 0f, W, H, solid)
                Txt.draw(c, bo1, W / 2f, 340f, 24f, col("#ffb3a7"), black = true)
                if (bo2.isNotEmpty()) {
                    c.glow(W / 2f, 420f, 220f, col("#ff3b2f"), 0.4f)
                    Txt.draw(c, bo2, W / 2f, 420f, 32f, WHITE, col("#ff3b2f"), 6f, black = true)
                }
            }
            O_PHASE -> {
                dim(c, 0.45f)
                card(c, 300f, 500f)
                Txt.draw(c, "Phase ${phaseCard + 1} of 5", W / 2f, 334f, 14f, col("#a77c57"))
                Txt.draw(c, PHASE_NAMES[phaseCard], W / 2f, 380f, 32f, col("#e2674f"), black = true)
                wrapDraw(c, PHASE_TAUNTS[phaseCard], 430f, 16f, col("#5a4436"))
            }
            O_WIN -> {
                dim(c, 0.4f)
                card(c, 240f, 580f)
                Txt.draw(c, "🎉", W / 2f, 300f, 54f)
                Txt.draw(c, "YOU WON!", W / 2f, 370f, 40f, col("#e2674f"), black = true)
                Txt.draw(c, "$GOAL / $GOAL presses", W / 2f, 414f, 20f, col("#3a2a22"), black = true)
                Txt.draw(c, "The button is finally defeated.", W / 2f, 446f, 16f, col("#5a4436"))
                if (winSkips > 0) Txt.draw(c, "(You skipped $winSkips level${if (winSkips > 1) "s" else ""}. Loser.)", W / 2f, 474f, 13f, col("#a77c57"))
                if (winTwist) wrapDraw(c, "Wait… did you really think I would let you win?", 520f, 17f, col("#c4321f"), black = true)
            }
            O_SKIP -> {
                solid.color = rgba(20, 8, 20, 0.92f)
                c.drawRect(0f, 0f, W, H, solid)
                card(c, 250f, 580f)
                Txt.draw(c, "$skipTitle 🍼", W / 2f, 290f, 24f, col("#3a2a22"), black = true)
                Txt.draw(c, "Fine. But first, hold the button", W / 2f, 334f, 15f, col("#5a4436"))
                Txt.draw(c, "and admit it out loud:", W / 2f, 356f, 15f, col("#5a4436"))
                wrapDraw(c, "“$skipPhrase”", 404f, 20f, col("#e2674f"), black = true)
                btnA.set(70f, 470f, 410f, 520f)
                btnB.set(150f, 530f, 330f, 568f)
                pill(c, btnA, if (skipHeld > 0f) "Admitting… ${(skipHeld / 1.6f * 100f).toInt().coerceAtMost(100)}%" else "Hold to admit it", false, skipHeld / 1.6f)
                pill(c, btnB, "I'll play", true)
            }
            O_GIVEUP -> {
                dim(c, 0.5f)
                card(c, 290f, 520f)
                Txt.draw(c, "Give up?", W / 2f, 334f, 28f, col("#3a2a22"), black = true)
                Txt.draw(c, "The button wins. It will be", W / 2f, 378f, 16f, col("#5a4436"))
                Txt.draw(c, "insufferable about it.", W / 2f, 400f, 16f, col("#5a4436"))
                btnA.set(70f, 440f, 230f, 490f)
                btnB.set(250f, 440f, 410f, 490f)
                pill(c, btnA, "Give up", false)
                pill(c, btnB, "Keep going", true)
            }
            O_PRIZE -> {
                dim(c, 0.35f)
                card(c, 260f, 560f)
                val bob = sin(t * 6f) * 6f
                Txt.draw(c, "🎁", W / 2f, 318f + bob, 50f)
                Txt.draw(c, "🎵 Never gonna give you up 🎵", W / 2f, 386f, 20f, col("#e2674f"), black = true)
                Txt.draw(c, "never gonna let you down…", W / 2f, 418f, 16f, col("#5a4436"))
                Txt.draw(c, "You just got rickrolled by a button.", W / 2f, 470f, 16f, col("#3a2a22"), black = true)
                if (prizeT > 1.2f) Txt.draw(c, "tap to finish", W / 2f, 528f, 14f, col("#a77c57"), alpha = 0.6f + 0.4f * sin(t * 4f))
            }
        }
    }

    private fun wrapDraw(c: Canvas, str: String, y: Float, size: Float, color: Int, black: Boolean = false) {
        val words = str.split(' ')
        val rows = ArrayList<String>()
        var cur = ""
        for (w in words) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (Txt.width(next, size, black) > W - 110f && cur.isNotEmpty()) {
                rows.add(cur)
                cur = w
            } else {
                cur = next
            }
        }
        if (cur.isNotEmpty()) rows.add(cur)
        for ((i, row) in rows.withIndex()) Txt.draw(c, row, W / 2f, y + i * (size + 6f), size, color, black = black)
    }

    private fun loserStamp(c: Canvas) {
        val a = clamp(stampT / 0.6f, 0f, 1f)
        val k = 1f + 0.6f * clamp((stampT - 2f) / 0.4f, 0f, 1f)
        c.save()
        c.rotate(-12f, W / 2f, H * 0.45f)
        c.scale(k, k, W / 2f, H * 0.45f)
        line2.color = rgba(col("#d8233a"), a)
        line2.strokeWidth = 6f
        c.drawRoundRect(W / 2f - 150f, H * 0.45f - 60f, W / 2f + 150f, H * 0.45f + 50f, 14f, 14f, line2)
        Txt.draw(c, "LOSER", W / 2f, H * 0.45f - 18f, 58f, rgba(col("#d8233a"), a), black = true)
        Txt.draw(c, stampText, W / 2f, H * 0.45f + 28f, 15f, rgba(col("#d8233a"), a), black = true)
        c.restore()
    }
}
