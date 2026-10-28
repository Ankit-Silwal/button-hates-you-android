package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Inbox Zero: emails land one at a time. Real mail goes right into the Inbox,
// spam goes left into the bin. A wrong sort, or letting one time out, is a
// strike. Three strikes and you are fired.
// Port of src/adventure/games/spam.js.
class SpamGame(private val ctx: GameContext) : Game {
    private companion object {
        const val MAX_STRIKES = 3
        const val CW = 292f // card size
        const val CH = 210f
        const val CX = W / 2f
        const val CY = 392f
        const val BIN_W = 92f
        const val BIN_Y = 244f
        const val BIN_H = 300f
        const val THROW = 70f // drag this far and let go to sort
        const val TAP = 16f // less than this is a tap, not a drag
        const val DESK_Y = 668f
        const val BOB_Y = 634f
        const val BOB_SIZE = 100f
        const val MAX_FLIERS = 8

        // sender, subject, isSpam
        val MAIL = arrayOf(
            Triple("Mum", "Did you eat?", false),
            Triple("Boss", "Fine.", false),
            Triple("Dentist", "Your appointment is Tuesday", false),
            Triple("Grandma", "Photo of my cat (again)", false),
            Triple("HR", "Your payslip is attached", false),
            Triple("Bank", "Your statement is ready", false),
            Triple("Landlord", "Rent is due Friday", false),
            Triple("Dad", "Call me. Not urgent. CALL NOW.", false),
            Triple("Library", "Your book is 3 days overdue", false),
            Triple("Team Lead", "Standup moved to 10:15", false),
            Triple("School", "Parents' evening reminder", false),
            Triple("Mum", "WHY DO YOU NEVER CALL", false),
            Triple("Pizza Place", "Your order is on its way", false),
            Triple("IT Dept", "Maintenance this Sat, 2am", false),
            Triple("Sister", "Did you take my charger?", false),
            Triple("Alex", "Still on for lunch?", false),
            Triple("Courier", "Parcel left with neighbour", false),
            Triple("Doctor", "Test results: all normal", false),
            Triple("Accounting", "Invoice #4471 approved", false),
            Triple("Mum", "URGENT!!! You left your scarf here", false),
            Triple("Prince of Nigeria", "URGENT!!! Claim $5,000,000", true),
            Triple("Your Cursor", "has a virus", true),
            Triple("Hot Singles", "in your RAM", true),
            Triple("Free Trial", "You won a free trial of a free trial", true),
            Triple("Totally Legit Bank", "Verify your password HERE", true),
            Triple("Dr. Miracle", "Lose 10kg by Tuesday", true),
            Triple("CryptoBro", "1000x GUARANTEED!!!", true),
            Triple("Wizard Supplies", "Enlarge your staff", true),
            Triple("Amazin Prime", "Your package is waiting (click)", true),
            Triple("Mr. Lottery", "You won a lottery you never entered", true),
            Triple("Sheriff's Office", "Pay $50 in gift cards or be arrested", true),
            Triple("Rolex4U", "Cheap watchez, bargin!!", true),
            Triple("Antivirus Pro", "47 VIRUSES FOUND!", true),
            Triple("Car Warranty", "We have been trying to reach you", true),
            Triple("Aunt Karen??", "Forward this to 10 friends or perish", true),
            Triple("Click Here", "Click here", true),
            Triple("Your Bank (not)", "Account suspended!!1", true),
            Triple("Bored Millionaire", "Needs YOUR help moving money", true),
            Triple("Mattress World", "90% off! Ends in 3 minutes", true),
            Triple("Your Boss", "Buy gift cards. Urgent. Tell no one.", true),
            Triple("Newsletter #482", "You can unsubscribe. Or can you?", true),
            Triple("Hot Tip", "Buy my NFT of a button", true),
        )

        val RIGHT = arrayOf("Sorted.", "Easy.", "Mum would be proud.", "Inbox: tidy.", "Pfft. Too easy.", "Filed.")
        val NOT_SPAM = arrayOf("That was REAL.", "Mum is calling.", "You binned a real one.", "That one mattered.")
        val WAS_SPAM = arrayOf("Now I have a virus.", "You let it IN.", "Congrats, you won a virus.", "It followed me home.")
        val LATE = arrayOf("Too slow.", "It just sat there.", "Unread. Forever.", "Time is money.")
        val END = arrayOf("You are fired.", "Pack your desk.", "I need a new job.")
        val IDLE = arrayOf("Left. Or right. Pick.", "Mind the strikes.", "Another one.")

        const val ELL = "…"
    }

    // One email: its text is laid out once, the first time it is drawn.
    private class Card(val from: String, val subj: String, val spam: Boolean, val life: Float) {
        var age = 0f
        var ox = 0f
        var oy = 0f
        var lines: Array<String>? = null
        var name = ""
        var initial = ""
        var hue = 0
    }

    private class Flier(
        val card: Card, var x: Float, var y: Float, var ang: Float,
        val vx: Float, var vy: Float, val vr: Float, val side: Int, val ok: Boolean,
        val late: Boolean, val grav: Float, val stamp: String, val stampColor: Int,
    ) {
        var age = 0f
        var sc = 1f
        var landed = false
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val mouth = Mouth()
    private val fx = Fx()
    private val fliers = ArrayList<Flier>(MAX_FLIERS)
    private var t = 0f
    private var strikes = 0
    private var combo = 0
    private var cur: Card? = null // the email being sorted
    private var dragging = false
    private var dragSx = 0f
    private var dragDx = 0f
    private var dragFar = 0f
    private var next = 0.35f // seconds until the next email arrives
    private var calm = 0 // extra-slow emails after a Continue
    private var shake = 0f
    private var moodT = 0f
    private var mood = Mood.Calm
    private var squash = 0f
    private var chat = 5f
    private var comboPop = 0f
    private val pulse = FloatArray(2) // bin bump: spam, inbox
    private val deck = IntArray(MAIL.size)
    private var deckLeft = 0
    private var lastMail = -1

    // each email stays a little less long than the one before
    private fun life() = 0.85f + 3.15f * exp(-score / 14f) + (if (calm > 0) 0.9f else 0f)

    private fun draw1(): Int {
        if (deckLeft == 0) {
            for (i in deck.indices) deck[i] = i
            for (i in deck.size - 1 downTo 1) {
                val j = Random.nextInt(i + 1)
                val tmp = deck[i]
                deck[i] = deck[j]
                deck[j] = tmp
            }
            // never the same email twice in a row across a reshuffle
            if (deck[deck.size - 1] == lastMail) {
                val tmp = deck[deck.size - 1]
                deck[deck.size - 1] = deck[0]
                deck[0] = tmp
            }
            deckLeft = deck.size
        }
        return deck[--deckLeft]
    }

    private fun arrive() {
        val i = draw1()
        lastMail = i
        val (from, subj, isSpam) = MAIL[i]
        val l = life()
        if (calm > 0) calm--
        cur = Card(from, subj, isSpam, l)
    }

    private fun feel(m: Mood, sec: Float = 1f) {
        mood = m
        moodT = sec
    }

    private fun say(lines: Array<String>, chance: Float = 1f, sec: Float = 1.8f) {
        if (chance < 1f && (mouth.left > 0f || Random.nextFloat() >= chance)) return
        mouth.say(pick(lines), sec)
    }

    private fun strike(late: Boolean) {
        strikes++
        combo = 0
        shake = 0.4f
        ctx.sfx.play("bad")
        ctx.haptics.thud()
        feel(if (late) Mood.Sad else Mood.Angry, 1.2f)
        fx.ring(CX, 166f, col("#ff4f6b"), 70f, 0.4f)
        if (strikes >= MAX_STRIKES) {
            over = true
            cur = null
            dragging = false
            ctx.sfx.play(if (score >= 25) "win" else "sob")
            mouth.say(pick(END), 3f)
            feel(Mood.Sad, 99f)
        }
    }

    private fun addFlier(f: Flier) {
        if (fliers.size >= MAX_FLIERS) fliers.removeAt(0)
        fliers.add(f)
    }

    // sends the current card off to a bin: side -1 = spam, +1 = inbox
    private fun sort(side: Int) {
        val card = cur ?: return
        if (over) return
        val ok = card.spam == (side < 0)
        val x = CX + card.ox
        val y = CY + card.oy
        cur = null
        dragging = false
        next = 0.12f + (if (calm > 0) 0.25f else 0f)
        addFlier(
            Flier(
                card, x, y, card.ox * 0.0016f, side * 1150f, -60f, side * 5f, side, ok, false, 0f,
                if (card.spam) "SPAM" else "OK", if (card.spam) STAMP_SPAM else STAMP_OK,
            ),
        )
        if (ok) {
            score++
            combo++
            ctx.sfx.play("click")
            ctx.haptics.tap()
            feel(if (Random.nextFloat() < 0.5f) Mood.Happy else Mood.Smug, 0.7f)
            squash = 0.12f
            fx.float(x, y - 90f, "+1")
            say(RIGHT, 0.12f)
            if (combo >= 5 && combo % 5 == 0) {
                ctx.sfx.play("hit")
                comboPop = 0.8f
                fx.float(CX, 300f, "COMBO x$combo!", YELLOW)
                fx.burst(CX, CY, YELLOW, 22, 380f)
                fx.ring(CX, CY, col("#fff3a8"), 120f, 0.45f, 8f)
            }
        } else {
            say(if (card.spam) WAS_SPAM else NOT_SPAM, 1f, 2f)
            strike(false)
        }
    }

    private fun timeout() {
        val card = cur ?: return
        cur = null
        dragging = false
        next = 0.3f
        addFlier(Flier(card, CX + card.ox, CY, 0f, 0f, 80f, rand(-1.5f, 1.5f), 0, false, true, 1500f, "LATE", STAMP_LATE))
        say(LATE, 1f, 1.6f)
        strike(true)
    }

    override fun down(x: Float, y: Float) {
        if (over || cur == null || x.isNaN() || y.isNaN()) return
        dragging = true
        dragSx = x
        dragDx = 0f
        dragFar = 0f
    }

    override fun move(x: Float, y: Float) {
        if (!dragging || x.isNaN()) return
        dragDx = x - dragSx
        dragFar = max(dragFar, abs(dragDx))
    }

    override fun up(x: Float, y: Float) {
        if (!dragging) return
        dragging = false
        if (cur == null || x.isNaN()) return
        if (abs(dragDx) >= THROW) sort(if (dragDx < 0f) -1 else 1)
        else if (dragFar < TAP) sort(if (x < CX) -1 else 1)
    }

    // a paid Continue: strikes back down to one, a calm moment, same score
    override fun revive() {
        over = false
        strikes = min(strikes, MAX_STRIKES - 1)
        strikes = max(0, strikes - 2)
        combo = 0
        cur = null
        dragging = false
        next = 1.2f
        calm = 5
        feel(Mood.Calm, 0.1f)
        reviveFx(fx, mouth, ctx, CX, CY, lookColor)
    }

    override fun update(dt0: Float) {
        if (dt0.isNaN()) return
        val dt = clamp(dt0, 0f, 0.05f)
        t += dt
        mouth.update(dt)
        fx.update(dt)
        shake = max(0f, shake - dt)
        comboPop = max(0f, comboPop - dt)
        squash = max(0f, squash - dt * 0.5f)
        pulse[0] = max(0f, pulse[0] - dt)
        pulse[1] = max(0f, pulse[1] - dt)

        if (!over) {
            moodT -= dt
            if (moodT <= 0f) mood = Mood.Calm
            val card = cur
            if (card != null) {
                card.age += dt
                val to = if (dragging) dragDx else 0f
                card.ox += (to - card.ox) * min(1f, dt * (if (dragging) 28f else 16f))
                if (card.ox.isNaN()) card.ox = 0f
                card.oy = abs(card.ox) * 0.05f
                if (card.age >= card.life) timeout()
            } else {
                next -= dt
                if (next <= 0f) arrive()
            }
            chat -= dt
            if (chat <= 0f) {
                chat = rand(6f, 10f)
                say(IDLE, 0.5f)
            }
        }

        for (f in fliers) {
            f.age += dt
            if (f.grav != 0f) f.vy += f.grav * dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.ang += f.vr * dt
            f.sc = clamp(1f - f.age * 0.9f, 0.3f, 1f)
            if (!f.late && !f.landed && abs(f.x - CX) > 190f) {
                f.landed = true
                val bx = if (f.side < 0) BIN_W / 2f + 6f else W - BIN_W / 2f - 6f
                pulse[if (f.side < 0) 0 else 1] = 0.3f
                fx.burst(bx, CY, if (f.ok) MINT else STAMP_RED, if (f.ok) 12 else 16, 300f)
                if (f.ok) fx.ring(bx, CY, WHITE, 60f, 0.3f)
            }
        }
        while (fliers.isNotEmpty() && fliers[0].age > 0.55f) fliers.removeAt(0)
    }

    private var shownCombo = -1
    private var comboText = ""

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.sprite(art.wall, 0f, 0f, W, H)
        c.save()
        if (shake > 0f) c.translate(rand(-1f, 1f) * shake * 14f, rand(-1f, 1f) * shake * 14f)

        // how far the card is dragged picks which bin lights up
        val cd = cur
        val lean = if (cd != null) clamp(cd.ox / THROW, -1f, 1f) else 0f
        bin(c, 0, pulse[0], clamp(-lean, 0f, 1f))
        bin(c, 1, pulse[1], clamp(lean, 0f, 1f))

        val bob = sin(t * 2f) * 1.2f
        bobby(c, CX, BOB_Y + bob, BOB_SIZE, look, mood = if (over) Mood.Sad else mood, t = t, squash = squash)
        c.sprite(art.desk, 0f, 0f, W, H)

        // a pile of unread mail behind the current one
        if (!over) {
            for (i in 2 downTo 1) {
                c.save()
                c.translate(CX + (if (i == 2) -6f else 7f), CY + i * 9f)
                c.rotate((if (i == 2) -0.04f else 0.03f) * DEG)
                rect.set(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f)
                paint.color = if (i == 2) PILE_BACK else PILE_FRONT
                c.drawRoundRect(rect, 18f, 18f, paint)
                c.restore()
            }
        }

        for (f in fliers) {
            val a = clamp((0.55f - f.age) / 0.2f, 0f, 1f)
            card(c, f.card, f.x, f.y, f.ang, f.sc, a, -1f, f.stamp, f.stampColor, 1f)
        }
        if (cd != null) {
            val k = clamp(cd.age / 0.16f, 0f, 1f)
            val e = 1f - (1f - k) * (1f - k)
            val lift = (1f - e) * -260f
            val showStamp = abs(cd.ox) > 14f
            val sa = clamp((abs(cd.ox) - 14f) / 56f, 0f, 1f)
            card(
                c, cd, CX + cd.ox, CY + cd.oy + lift, cd.ox * 0.0018f, 1f, e, 1f - cd.age / cd.life,
                if (!showStamp) null else if (cd.ox < 0f) "SPAM" else "OK",
                if (cd.ox < 0f) STAMP_SPAM else STAMP_OK, sa,
            )
        }
        c.restore()

        fx.draw(c)
        c.vignette(0.3f, NAVY)
        if (!over && cd != null && 1f - cd.age / cd.life < 0.25f) c.vignette(0.2f + 0.15f * abs(sin(t * 9f)), ALARM)
        mouth.draw(c, CX, BOB_Y - 52f)

        c.drawScore(score, "emails sorted")
        // strikes as hearts
        for (i in 0 until MAX_STRIKES) {
            val lost = i >= MAX_STRIKES - strikes
            val x = CX + (i - 1) * 40f
            c.heart(x, 164f + (if (lost) 0f else sin(t * 4f + i) * 1.5f), 12f, if (lost) HEART_LOST else HEART, if (lost) HEART_LOST_LINE else WHITE)
        }
        if (combo >= 3 && !over) {
            if (combo != shownCombo) {
                shownCombo = combo
                comboText = "x$combo"
            }
            val s = 1f + comboPop * 0.5f
            Txt.draw(c, comboText, CX, 224f, (24f * s).roundToInt().toFloat(), if (combo >= 10) YELLOW else ICE)
        }
    }

    // a bin on one side: kind 0 = spam (left), 1 = inbox (right)
    private fun bin(c: Canvas, kind: Int, bump: Float, lit: Float) {
        val s = art.bins[kind]
        val x = if (kind == 1) W - BIN_W - 6f else 6f
        val cx = x + BIN_W / 2f
        val cy = BIN_Y + BIN_H / 2f
        val glowA = max(lit, bump * 2f)
        if (glowA > 0.02f) c.glow(cx, cy, 130f, if (kind == 1) MINT else BIN_RED, clamp(glowA, 0f, 1f) * 0.8f)
        val k = 1f + bump * 0.35f + lit * 0.04f + sin(t * 2f + kind * 2f) * 0.006f
        c.save()
        c.translate(cx, cy)
        c.scale(k, k)
        c.sprite(s, -BIN_W / 2f - 6f, -BIN_H / 2f - 6f)
        c.restore()
    }

    /* ---------- cards ---------- */


    private fun fit(str0: String, maxW: Float, size: Float): String {
        if (Txt.width(str0, size) <= maxW) return str0
        var str = str0
        while (str.length > 1 && Txt.width(str + ELL, size) > maxW) str = str.dropLast(1)
        return str + ELL
    }

    // greedy word wrap, at most `max` lines, the last one truncated if needed
    private fun wrap(str: String, maxW: Float, size: Float, max: Int): Array<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (w in str.split(' ')) {
            val tryLine = if (line.isNotEmpty()) "$line $w" else w
            if (line.isNotEmpty() && Txt.width(tryLine, size) > maxW) {
                lines.add(line)
                line = w
            } else {
                line = tryLine
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        if (lines.size > max) {
            while (lines.size > max) lines.removeAt(lines.size - 1)
            lines[max - 1] = lines[max - 1] + ELL
        }
        return Array(lines.size) { fit(lines[it], maxW, size) }
    }

    private fun strHash(s: String): Int {
        var h = 0
        for (ch in s) h = (h * 31 + ch.code) % 360
        return h
    }

    private val hsv = FloatArray(3)

    // hsl(h, s, l) to an ARGB colour
    private fun hsl(h: Float, s: Float, l: Float): Int {
        val v = l + s * min(l, 1f - l)
        hsv[0] = h
        hsv[1] = if (v <= 0f) 0f else 2f * (1f - l / v)
        hsv[2] = v
        return android.graphics.Color.HSVToColor(hsv)
    }

    // frac: time left (0..1) as a bar, or < 0 for none; stamp with alpha sa
    private fun card(c: Canvas, cd: Card, x: Float, y: Float, ang: Float, sc: Float, alpha: Float, frac: Float, stamp: String?, stampColor: Int, sa: Float) {
        if (x.isNaN() || y.isNaN() || ang.isNaN() || alpha <= 0.01f) return
        if (cd.lines == null) {
            cd.lines = wrap(cd.subj, CW - 44f, 22f, 3)
            cd.name = fit(cd.from, CW - 92f, 17f)
            cd.initial = cd.from.take(1).uppercase()
            cd.hue = hsl(strHash(cd.from).toFloat(), 0.58f, 0.52f)
        }
        val lines = cd.lines ?: return
        c.save()
        c.translate(x, y)
        c.rotate(ang * DEG)
        c.scale(sc, sc)
        val layered = alpha < 0.99f
        if (layered) c.saveLayerAlpha(-CW / 2f - 10f, -CH / 2f - 10f, CW / 2f + 10f, CH / 2f + 40f, (alpha * 255f).toInt())
        c.sprite(art.cardBase, -CW / 2f - 10f, -CH / 2f - 10f)
        // avatar and sender
        paint.color = cd.hue
        c.drawCircle(-CW / 2f + 36f, -CH / 2f + 33f, 20f, paint)
        Txt.draw(c, cd.initial, -CW / 2f + 36f, -CH / 2f + 34f, 21f, WHITE)
        Txt.draw(c, cd.name, -CW / 2f + 66f, -CH / 2f + 25f, 17f, CARD_INK, align = Paint.Align.LEFT)
        Txt.draw(c, "to me  ·  just now", -CW / 2f + 66f, -CH / 2f + 46f, 15f, CARD_SUB, align = Paint.Align.LEFT)
        // subject, centred in the space under the header
        val lh = 27f
        val y0 = 16f - ((lines.size - 1) * lh) / 2f
        for (i in lines.indices) Txt.draw(c, lines[i], -CW / 2f + 22f, y0 + i * lh, 22f, CARD_INK, align = Paint.Align.LEFT)
        if (frac >= 0f) {
            val w = CW - 44f
            rect.set(-w / 2f, CH / 2f - 24f, w / 2f, CH / 2f - 16f)
            paint.color = BAR_BACK
            c.drawRoundRect(rect, 4f, 4f, paint)
            val f = clamp(frac, 0f, 1f)
            if (f > 0.02f) {
                rect.set(-w / 2f, CH / 2f - 24f, -w / 2f + w * f, CH / 2f - 16f)
                paint.color = hsl((f * 120f).roundToInt().toFloat(), 0.75f, 0.48f)
                c.drawRoundRect(rect, 4f, 4f, paint)
            }
        }
        if (stamp != null && sa > 0.02f) {
            val k = clamp(sa, 0f, 1f)
            c.save()
            c.translate(0f, 12f)
            c.rotate((if (stamp == "OK") 0.2f else -0.22f) * DEG)
            val s = 1.15f - k * 0.15f
            c.scale(s, s)
            val w = stamp.length * 26f + 30f
            stampLine.color = rgba(stampColor, k)
            rect.set(-w / 2f, -30f, w / 2f, 30f)
            c.drawRoundRect(rect, 10f, 10f, stampLine)
            Txt.draw(c, stamp, 0f, 2f, 44f, stampColor, alpha = k)
            c.restore()
        }
        if (layered) c.restore()
        c.restore()
    }

    /* ---------- art ---------- */

    private val paint = fill(WHITE)
    private val stampLine = stroke(WHITE, 6f)
    private val rect = RectF()

    private val YELLOW = col("#ffd84d")
    private val ICE = col("#bfe0ff")
    private val MINT = col("#8dffb0")
    private val STAMP_RED = col("#ff4f6b")
    private val BIN_RED = col("#ff6b86")
    private val STAMP_SPAM = col("#e0344c")
    private val STAMP_OK = col("#2fae5f")
    private val STAMP_LATE = col("#c0392b")
    private val NAVY = col("#0a1230")
    private val ALARM = col("#c0182a")
    private val PILE_BACK = col("#a8b6d6")
    private val PILE_FRONT = col("#cdd7ee")
    private val HEART = col("#ff4f7b")
    private val HEART_LOST = rgba(20, 28, 60, 0.55f)
    private val HEART_LOST_LINE = rgba(255, 255, 255, 0.25f)
    private val CARD_INK = col("#1c2540")
    private val CARD_SUB = col("#5f6c8f")
    private val BAR_BACK = rgba(30, 50, 100, 0.15f)
    private val art = Art()

    private class Art {
        // the office wall behind everything, painted once
        val wall by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#202c4d"), 1f to col("#41568a"))))
                // faint diagonal wallpaper stripes
                val stripe = stroke(rgba(255, 255, 255, 0.035f), 14f).apply { strokeCap = Paint.Cap.BUTT }
                var x = -H
                while (x < W) {
                    c.drawLine(x, H, x + H, 0f, stripe)
                    x += 44f
                }
                c.glow(CX, CY, 300f, col("#9fc4ff"), 0.3f)
                // a sticky note and a clock to make it an office
                c.save()
                c.translate(398f, 180f)
                c.rotate(0.08f * DEG)
                c.shadow(0f, 52f, 40f, 0.3f, 8f)
                c.drawRect(-36f, -36f, 36f, 36f, fill(col("#ffe66b")))
                Txt.draw(c, "INBOX", 0f, -12f, 15f, col("#6b5a10"))
                Txt.draw(c, "ZERO", 0f, 4f, 15f, col("#6b5a10"))
                Txt.draw(c, "or else", 0f, 22f, 15f, col("#aa3333"))
                c.restore()
                c.drawCircle(76f, 186f, 30f, fill(col("#f4f6fb")))
                c.drawCircle(76f, 186f, 30f, stroke(col("#2a3558"), 4f))
                val hand = stroke(col("#2a3558"), 3f)
                c.drawLine(76f, 186f, 76f, 168f, hand)
                c.drawLine(76f, 186f, 90f, 192f, hand)
            }
        }

        // the desk in front of the button, drawn over its lower half
        val desk by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.shadow(CX, DESK_Y, 260f, 0.35f, 16f)
                c.drawRect(0f, DESK_Y, W, H, fill().shade(lin(0f, DESK_Y, 0f, H, 0f to col("#c58a52"), 0.08f to col("#a9703c"), 1f to col("#5e381d"))))
                c.drawRect(0f, DESK_Y, W, DESK_Y + 3f, fill(rgba(255, 230, 190, 0.5f)))
                val grain = stroke(rgba(60, 30, 12, 0.2f), 2f)
                for (i in 1 until 4) c.drawLine(0f, DESK_Y + 22f + i * 24f, W, DESK_Y + 22f + i * 24f, grain)
                // keyboard
                c.drawRoundRect(RectF(CX - 88f, DESK_Y + 30f, CX + 88f, DESK_Y + 74f), 8f, 8f, fill(col("#e9ecf3")))
                val key = fill(col("#b6bccd"))
                for (r in 0 until 3) for (k in 0 until 11) {
                    val kx = CX - 80f + k * 14.6f
                    val ky = DESK_Y + 37f + r * 11f
                    c.drawRect(kx, ky, kx + 11f, ky + 8f, key)
                }
                // mug
                c.drawRoundRect(RectF(392f, DESK_Y + 20f, 432f, DESK_Y + 64f), 7f, 7f, fill(col("#e0524a")))
                c.drawArc(RectF(426f, DESK_Y + 32f, 446f, DESK_Y + 52f), -90f, 180f, false, stroke(col("#e0524a"), 6f).apply { strokeCap = Paint.Cap.BUTT })
                // paper stack
                c.drawRoundRect(RectF(44f, DESK_Y + 34f, 122f, DESK_Y + 76f), 4f, 4f, fill(col("#f7f3e6")))
                c.drawRect(44f, DESK_Y + 66f, 122f, DESK_Y + 69f, fill(col("#e3dcc6")))
            }
        }

        val bins: Array<Sprite> by lazy { Array(2) { binArt(it) } }

        private fun binArt(kind: Int): Sprite {
            val pal = if (kind == 1) intArrayOf(col("#2b62b8"), col("#63a9f5"), col("#bfe0ff")) else intArrayOf(col("#7d1f33"), col("#d4455c"), col("#ffc2cb"))
            return makeSprite(BIN_W + 12f, BIN_H + 12f) { c ->
                c.translate(6f, 6f)
                val body = RectF(0f, 0f, BIN_W, BIN_H)
                c.drawRoundRect(body, 18f, 18f, fill().shade(lin(0f, 0f, 0f, BIN_H, 0f to pal[1], 1f to pal[0])))
                c.drawRoundRect(body, 18f, 18f, stroke(pal[2], 3f))
                c.drawRoundRect(RectF(8f, 8f, BIN_W - 8f, 44f), 10f, 10f, fill(rgba(255, 255, 255, 0.15f)))
                Txt.draw(c, if (kind == 1) "INBOX" else "SPAM", BIN_W / 2f, 27f, 19f, WHITE)
                // icon
                val cx = BIN_W / 2f
                val cy = 120f
                val white = fill(WHITE)
                val ink = stroke(pal[0], 3f)
                if (kind == 1) {
                    c.drawRoundRect(RectF(cx - 28f, cy - 19f, cx + 28f, cy + 19f), 5f, 5f, white)
                    val p = Path()
                    p.moveTo(cx - 26f, cy - 16f)
                    p.lineTo(cx, cy + 4f)
                    p.lineTo(cx + 26f, cy - 16f)
                    c.drawPath(p, ink)
                } else {
                    c.drawRoundRect(RectF(cx - 22f, cy - 14f, cx + 22f, cy + 30f), 6f, 6f, white)
                    c.drawRoundRect(RectF(cx - 28f, cy - 24f, cx + 28f, cy - 15f), 4f, 4f, white)
                    c.drawRect(cx - 7f, cy - 30f, cx + 7f, cy - 23f, white)
                    for (dx in floatArrayOf(-11f, 0f, 11f)) c.drawLine(cx + dx, cy - 6f, cx + dx, cy + 22f, ink)
                }
                // arrow pointing outwards, like the swipe
                val s = if (kind == 1) 1f else -1f
                val arrow = Path()
                arrow.moveTo(cx + s * 22f, 210f)
                arrow.lineTo(cx - s * 14f, 190f)
                arrow.lineTo(cx - s * 14f, 230f)
                arrow.close()
                c.drawPath(arrow, fill(rgba(255, 255, 255, 0.9f)))
                Txt.draw(c, if (kind == 1) "REAL" else "JUNK", BIN_W / 2f, 262f, 16f, rgba(255, 255, 255, 0.9f))
            }
        }

        // the blank card (shadow, paper, header band, edge), 10 units of margin round it
        val cardBase by lazy {
            makeSprite(CW + 20f, CH + 50f) { c ->
                c.translate(CW / 2f + 10f, CH / 2f + 10f)
                c.shadow(0f, CH / 2f + 12f, CW * 0.46f, 0.4f, 14f)
                val r = RectF(-CW / 2f, -CH / 2f, CW / 2f, CH / 2f)
                val shape = Path().apply { addRoundRect(r, 18f, 18f, Path.Direction.CW) }
                c.drawPath(shape, fill().shade(lin(0f, -CH / 2f, 0f, CH / 2f, 0f to WHITE, 1f to col("#e8edf8"))))
                c.save()
                c.clipPath(shape)
                c.drawRect(-CW / 2f, -CH / 2f, CW / 2f, -CH / 2f + 66f, fill(col("#d8e4ff")))
                c.restore()
                c.drawRoundRect(r, 18f, 18f, stroke(rgba(30, 50, 100, 0.25f), 2f))
            }
        }
    }
}
