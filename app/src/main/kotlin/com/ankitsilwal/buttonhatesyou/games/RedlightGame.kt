package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Freeze!: the button sneaks down a hallway toward a giant angry cursor. Hold
// to run while it faces away. When it twitches, let go. Caught moving = over.
// Port of src/adventure/games/redlight.js.
class RedlightGame(private val ctx: GameContext) : Game {
    private companion object {
        const val BX = 240f // the button never leaves this spot, the hallway moves
        const val BY = 565f
        const val CX = 240f
        const val CY = 345f
        const val SIZE = 96f
        const val SAFE = 2.2f // seconds of blinking safety after a paid Continue
        const val REACT = 0.6f // how far round the cursor must be to catch you
        const val S = 0.2f // hallway units that scroll by per meter

        const val AWAY = 0
        const val WARN = 1
        const val TURN = 2
        const val STARE = 3
        const val BACK = 4
        const val FAKE = 5

        // the hallway's projection
        const val HOR = 230f // horizon line
        const val A = 570f // how far the floor drops below the horizon at depth 0
        const val HW = 600f
        const val CEIL = 0.8f
        const val FAR = 14

        val SNEAK = arrayOf("I see nothing.", "Just a casual stroll.", "Nothing to see here.", "Totally invisible.", "La la la.")
        val FREEZE = arrayOf("Statue. I am a statue.", "Not moving. At all.", "Was never here.", "Uh oh uh oh uh oh.")
        val STARE_LINES = arrayOf("Was that you?", "I heard something.", "Who clicked?", "Hmm. Suspicious.", "Show yourself.")
        val FAKED = arrayOf("Just kidding.", "Gotcha. Almost.", "Jumpy, are we?", "Ha. You flinched.")
        val FAKE_BTN = arrayOf("Jerk.", "That was cheating.", "I hate you.", "My nerves!")
        val CAUGHT = arrayOf("GOTCHA.", "CLICKED.", "I SAW THAT.", "Left click. Left you.")
        val CAUGHT_BTN = arrayOf("I can explain.", "It was the wind.", "Not me. Another button.", "Oof.")
        val MILE = arrayOf("Still alive.", "Look at me go.", "Am I famous yet?", "Do not look back.")

        fun ease(k0: Float): Float {
            val k = clamp(k0, 0f, 1f)
            return k * k * (3f - 2f * k)
        }

        // a point on the hallway: side -1..1 across, depth d ahead, h 0 (floor) .. 1 (ceiling)
        fun px(side: Float, d: Float): Float = CX + side * HW / (d + 1f)
        fun py(d: Float, h: Float): Float {
            val s = 1f / (d + 1f)
            val yf = HOR + A * s
            return yf + (HOR - A * s * CEIL - yf) * h
        }
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val bm = Mouth() // the button
    private val cm = Mouth() // the cursor
    private val fx = Fx()
    private var t = 0f
    private var dist = 0f
    private var speed = 0f
    private var ptr = false
    private var st = AWAY
    private var stT = 0f
    private var dur = 2.4f // how long the current phase lasts
    private var fake = false
    private var f = 0f // 0 facing away .. 1 facing you
    private var safe = 0f
    private var shake = 0f
    private var flash = 0f
    private var lunge = 0f
    private var kick = 0f // squash when you stop short
    private var tickT = 0f
    private var dust = 0f
    private var quipT = 0.4f
    private var miles = 0
    private var wasRun = false
    private var first = true

    // later rounds: shorter green phases and warnings, more fake-outs
    private fun green() = rand(clamp(2.9f - dist / 260f, 1.1f, 2.9f), clamp(4.2f - dist / 220f, 1.9f, 4.2f))
    private fun warnTime() = clamp(0.8f - dist / 1400f, 0.5f, 0.8f)
    private fun stareTime() = rand(clamp(1.5f - dist / 900f, 0.9f, 1.5f), clamp(2.0f - dist / 800f, 1.2f, 2.0f))
    private fun fakeChance() = clamp((dist - 60f) / 700f, 0f, 0.35f)
    private fun top() = clamp(14f + dist / 50f, 14f, 26f) // m/s

    private fun goto(s: Int, d: Float = -1f) {
        st = s
        stT = 0f
        if (d >= 0f) dur = d
    }

    private fun sayB(lines: Array<String>, sec: Float = 1.6f) {
        if (bm.left <= 0f) bm.say(pick(lines), sec)
    }

    private fun caught() {
        over = true
        speed = 0f
        shake = 0.55f
        flash = 0.35f
        st = STARE
        f = 1f
        ctx.sfx.play("bad")
        ctx.sfx.play("boom")
        ctx.haptics.thud()
        fx.burst(BX, BY - 20f, col("#ff3b3b"), 26, 380f)
        fx.burst(BX, BY - 20f, col("#ffd84d"), 12, 300f)
        fx.ring(BX, BY - 20f, col("#ff6a5a"), 130f, 0.5f, 10f)
        fx.ring(CX, CY, col("#ff3b3b"), 200f, 0.6f, 12f)
        cm.say(pick(CAUGHT), 2.6f)
        bm.say(pick(CAUGHT_BTN), 2.6f)
    }

    override fun down(x: Float, y: Float) {
        ptr = true
    }

    override fun up(x: Float, y: Float) {
        ptr = false
    }

    // a paid Continue: same meters, the cursor looks away, a blink of safety
    override fun revive() {
        over = false
        safe = SAFE
        ptr = false
        speed = 0f
        lunge = 0f
        fake = false
        f = 0f
        goto(AWAY, 2.6f)
        reviveFx(fx, bm, ctx, BX, BY - 30f, lookColor)
        cm.say("", 0f)
    }

    override fun update(dt0: Float) {
        if (dt0.isNaN()) return
        val dt = clamp(dt0, 0f, 0.05f)
        t += dt
        bm.update(dt)
        cm.update(dt)
        fx.update(dt)
        shake = max(0f, shake - dt)
        flash = max(0f, flash - dt)
        kick = max(0f, kick - dt * 4f)
        safe = max(0f, safe - dt)
        if (over) lunge = min(1f, lunge + dt * 6f)
        if (over) return

        stT += dt
        // ---- the cursor's mood swings ----
        when (st) {
            AWAY -> {
                f = 0f
                if (stT >= dur) {
                    fake = Random.nextFloat() < fakeChance()
                    goto(WARN, warnTime())
                    tickT = 0f
                    ctx.sfx.play("tick")
                    if (speed > 2f) sayB(FREEZE, 1.2f)
                }
            }
            WARN -> {
                f = 0.16f * abs(sin(stT * 24f))
                tickT -= dt
                if (tickT <= 0f) {
                    tickT = 0.14f
                    ctx.sfx.play("tick")
                }
                if (stT >= dur) {
                    goto(if (fake) FAKE else TURN, if (fake) 0.3f else 0.16f)
                    if (st == FAKE) {
                        cm.say(pick(FAKED), 1.4f)
                        if (speed < 2f) sayB(FAKE_BTN, 1.4f)
                    }
                }
            }
            TURN -> {
                f = 0.16f + 0.84f * ease(stT / dur)
                if (stT >= dur) {
                    val d = stareTime()
                    goto(STARE, d)
                    f = 1f
                    ctx.sfx.play("nope")
                    shake = 0.1f
                    cm.say(pick(STARE_LINES), min(1.4f, d))
                }
            }
            STARE -> {
                f = 1f
                if (stT >= dur) goto(BACK, 0.4f)
            }
            BACK -> {
                f = 1f - ease(stT / dur)
                if (stT >= dur) goto(AWAY, green())
            }
            FAKE -> {
                f = 0.3f * (1f - ease(stT / dur))
                if (stT >= dur) goto(AWAY, rand(0.9f, 1.8f))
            }
        }

        // ---- running ----
        val run = ptr
        if (run) {
            speed = min(top(), speed + 60f * dt)
        } else {
            if (speed > 6f) kick = 0.5f
            speed = max(0f, speed - 300f * dt)
        }
        if (run && !wasRun && first && t > 0.1f) {
            first = false
            sayB(SNEAK, 2f)
        }
        wasRun = run
        dist += speed * dt
        if (dist.isNaN()) dist = 0f
        score = floor(dist).toInt()

        // ---- busted? ----
        if (safe <= 0f && f >= REACT && (st == TURN || st == STARE) && (run || speed > 4f)) {
            caught()
            return
        }

        // ---- juice ----
        if (speed > 3f) {
            dust -= dt
            if (dust <= 0f) {
                dust = 0.05f
                fx.mote(BX + rand(-26f, 26f), BY + 44f, DUST, rand(-30f, 30f), rand(10f, 40f), rand(2f, 4f), rand(0.3f, 0.6f), add = false)
            }
        }
        val m = floor(dist / 100f).toInt()
        if (m > miles) {
            miles = m
            ctx.sfx.play("coin")
            ctx.haptics.tap()
            fx.float(BX, BY - 100f, "${m * 100} m!", GOLD)
            fx.ring(BX, BY - 10f, GOLD, 90f, 0.4f)
            if (m % 2 == 0) sayB(MILE, 1.6f)
        }
        quipT -= dt
        if (quipT <= 0f) {
            quipT = rand(6f, 10f)
            if (st == AWAY && speed > 8f) sayB(SNEAK, 1.6f)
        }
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.save()
        if (shake > 0f) c.translate(rand(-1f, 1f) * shake * 14f, rand(-1f, 1f) * shake * 14f)
        val warn = st == WARN || st == TURN
        val staring = f >= REACT

        hall(c)
        c.sprite(art.fog, 0f, 0f, W, H)
        milestone(c)

        // speed lines streaming out of the vanishing point
        if (speed > 6f) {
            val k = speed / 26f
            line.color = rgba(255, 255, 255, 0.18f * k)
            line.strokeWidth = 2f
            for (i in 0 until 12) {
                val a = (i / 12f) * TAU + 0.3f
                val r = 150f + ((t * 280f * k + i * 71f) % 330f).toInt()
                val ca = cos(a)
                val sa = sin(a)
                c.drawLine(CX + ca * r, 240f + sa * r * 1.2f, CX + ca * (r + 26f * k), 240f + sa * (r + 26f * k) * 1.2f, line)
            }
        }

        // the cursor: green glow when it is safe, red when it sees you
        val glowCol = if (staring) RED else if (warn) AMBER else GREEN
        val pulse = if (warn) 0.7f + 0.3f * sin(t * 30f) else 0.45f
        c.glow(CX, CY, 190f + lunge * 80f, glowCol, if (staring) 0.8f else pulse)
        c.shadow(CX, 446f, 70f + lunge * 20f, 0.5f, 14f)
        cursor(c, warn)
        if (warn && !over) {
            // the '!' pops over its shoulder
            val bob = abs(sin(t * 18f)) * 8f
            dot.color = BANG
            c.drawCircle(CX + 96f, CY - 70f - bob, 26f, dot)
            ring.color = WHITE
            ring.strokeWidth = 3f
            c.drawCircle(CX + 96f, CY - 70f - bob, 26f, ring)
            Txt.draw(c, "!", CX + 96f, CY - 68f - bob, 38f, WHITE)
        }

        // the button
        val sp = clamp(speed / 26f, 0f, 1f)
        val run = sp > 0.1f
        val bob = if (run) abs(sin(t * 13f)) * 9f * sp else 0f
        val sq = if (run) sin(t * 26f) * 0.05f * sp else kick * 0.18f * sin(kick * 18f)
        c.shadow(BX, BY + 46f, 44f - bob, 0.45f, 11f)
        val mood = when {
            over -> Mood.Angry
            run -> Mood.Smug
            staring || warn -> Mood.Worried
            else -> Mood.Calm
        }
        bobby(
            c, BX, BY - bob, SIZE, look,
            mood = mood, t = t,
            squash = if (over) 0.12f else sq,
            angle = if (run) sin(t * 13f) * 0.07f * sp else if (over) 0.2f else 0f,
            tears = over,
            flash = flash > 0.28f,
            alpha = blinkAlpha(safe, t),
        )

        fx.draw(c)
        c.restore()

        c.vignette(0.4f, NIGHT)
        if (staring) c.vignette(if (over) 0.55f else 0.4f, RED_VIG)
        else if (warn) c.vignette(0.15f + 0.15f * abs(sin(t * 24f)), AMBER_VIG)
        if (flash > 0f) {
            dot.color = rgba(FLASH, clamp(flash * 1.4f, 0f, 1f))
            c.drawRect(0f, 0f, W, H, dot)
        }

        cm.draw(c, CX, 248f)
        bm.draw(c, BX, BY - SIZE * 0.62f)
        c.drawScore(score, "meters")
        if (t < 4f && !over) Txt.draw(c, "Hold to run. Freeze when it turns!", W / 2f, 170f, 16f, HINT)

        // a hold button that doubles as the traffic light
        if (!over) {
            val bad = staring || warn
            dot.color = rgba(if (bad) col("#d92d34") else if (ptr) col("#2fae5a") else col("#3a4a66"), 0.9f)
            rect.set(W / 2f - 120f, 720f, W / 2f + 120f, 774f)
            c.drawRoundRect(rect, 27f, 27f, dot)
            ring.color = rgba(255, 255, 255, 0.65f * 0.9f)
            ring.strokeWidth = 3f
            c.drawRoundRect(rect, 27f, 27f, ring)
            Txt.draw(c, if (staring) "DON'T MOVE" else if (warn) "FREEZE!" else if (ptr) "RUNNING" else "HOLD TO RUN", W / 2f, 747f, 22f, WHITE)
        }
    }

    /* ---------- the hallway ---------- */

    private val poly = Path()
    private val polyFill = fill()

    private fun quad(c: Canvas, color: Int, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        if (x1.isNaN() || y3.isNaN()) return
        poly.reset()
        poly.moveTo(x1, y1)
        poly.lineTo(x2, y2)
        poly.lineTo(x3, y3)
        poly.lineTo(x4, y4)
        poly.close()
        polyFill.color = color
        c.drawPath(poly, polyFill)
    }

    // a strip on the floor between two depths, from side a to side b
    private fun strip(c: Canvas, color: Int, a: Float, b: Float, d1: Float, d2: Float) {
        quad(c, color, px(a, d1), py(d1, 0f), px(b, d1), py(d1, 0f), px(b, d2), py(d2, 0f), px(a, d2), py(d2, 0f))
    }

    // the same on the ceiling
    private fun ceiling(c: Canvas, color: Int, a: Float, b: Float, d1: Float, d2: Float) {
        quad(c, color, px(a, d1), py(d1, 1f), px(b, d1), py(d1, 1f), px(b, d2), py(d2, 1f), px(a, d2), py(d2, 1f))
    }

    // a rectangle laid on a wall between two depths and two heights
    private fun wall(c: Canvas, color: Int, side: Float, d1: Float, d2: Float, h1: Float, h2: Float) {
        quad(c, color, px(side, d1), py(d1, h1), px(side, d2), py(d2, h1), px(side, d2), py(d2, h2), px(side, d1), py(d1, h2))
    }

    private fun hall(c: Canvas) {
        c.sprite(art.hallBase, 0f, 0f, W, H)
        val u = dist * S
        val base = floor(u).toInt()
        val frac = u - base
        for (j in FAR downTo 0) {
            val idx = base + j
            val d1 = max(0f, j - frac)
            val d2 = j + 1f - frac
            if (d2 <= 0f) continue
            val odd = idx % 2 != 0
            // floor boards with a red carpet runner down the middle
            strip(c, if (odd) BOARD_A else BOARD_B, -1f, 1f, d1, d2)
            strip(c, if (odd) CARPET_A else CARPET_B, -0.42f, 0.42f, d1, d2)
            strip(c, TRIM, -0.44f, -0.42f, d1, d2)
            strip(c, TRIM, 0.42f, 0.44f, d1, d2)
            if (odd) {
                wall(c, PANEL_L, -1f, d1, d2, 0.26f, 1f)
                wall(c, PANEL_R, 1f, d1, d2, 0.26f, 1f)
            }
            // doors on the left, posters on the right
            val m3 = ((idx % 3) + 3) % 3
            val m4 = ((idx % 4) + 4) % 4
            if (m3 == 0) {
                wall(c, DOOR_FRAME, -1f, d1 + 0.12f, d2 - 0.12f, 0f, 0.74f)
                wall(c, DOOR, -1f, d1 + 0.2f, d2 - 0.2f, 0.05f, 0.69f)
                val hd = d2 - 0.28f
                dot.color = KNOB
                c.drawCircle(px(-1f, hd), py(hd, 0.36f), 2f + 6f / (d1 + 1f), dot)
            }
            if (m4 == 1) {
                wall(c, POSTER, 1f, d1 + 0.2f, d2 - 0.2f, 0.42f, 0.78f)
                wall(c, POSTER_RED, 1f, d1 + 0.34f, d2 - 0.34f, 0.52f, 0.68f)
            }
            // ceiling lamps
            if (!odd) {
                ceiling(c, LAMP, -0.2f, 0.2f, d1 + 0.2f, d2 - 0.2f)
                val dm = (d1 + d2) / 2f
                c.glow(px(0f, dm), py(dm, 1f) + 18f / (d1 + 1f), 150f / (d1 + 1.6f), LAMP_GLOW, 0.4f)
            }
        }
    }

    private var bannerM = -1
    private var bannerText = ""

    // a "100 m" banner hanging over the next hundred-meter mark
    private fun milestone(c: Canvas) {
        val m = (floor(dist / 100f).toInt() + 1) * 100
        val d = (m - dist) * S
        if (d < 0.05f || d > FAR) return
        val lx = px(-1f, d)
        val ly = py(d, 0.94f)
        val rx = px(1f, d)
        val by = py(d, 0.7f)
        val s = 1f / (d + 1f)
        val a = clamp((FAR - d) / 5f, 0f, 1f)
        if (m != bannerM) {
            bannerM = m
            bannerText = "$m m"
        }
        dot.color = rgba(col("#ffd84d"), a)
        c.drawRect(lx, ly, rx, by, dot)
        dot.color = rgba(BANNER_INK, a)
        c.drawRect(lx, by - 3f * s - 1f, rx, by, dot)
        Txt.draw(c, bannerText, CX, (ly + by) / 2f - 1f, max(8f, (120f * s).roundToInt().toFloat()), BANNER_INK, alpha = a)
    }

    // the cursor at f: turning round is a squeeze through its edge-on moment
    private fun cursor(c: Canvas, warn: Boolean) {
        val front = f > 0.5f
        val sx = max(0.06f, abs(cos(f * PI_F)))
        val sc = 1f + lunge * 0.35f + (if (warn) sin(t * 30f) * 0.015f else 0f)
        val sway = if (warn) sin(t * 34f) * 0.07f else sin(t * 1.6f) * 0.03f
        c.save()
        c.translate(CX, CY + sin(t * 2f) * 3f - lunge * 20f)
        c.rotate(sway * DEG)
        c.scale(sx * sc, sc)
        c.sprite(if (front) art.cursorFront else art.cursorBack, -85f, -115f, 170f, 230f)
        c.restore()
    }

    /* ---------- art ---------- */

    private val line = stroke(WHITE, 2f)
    private val ring = stroke(WHITE, 3f)
    private val dot = fill(WHITE)
    private val rect = RectF()

    private val DUST = col("#d9d2e6")
    private val GOLD = col("#ffe27a")
    private val RED = col("#ff2d2d")
    private val AMBER = col("#ffb020")
    private val GREEN = col("#58e07a")
    private val BANG = col("#e8262d")
    private val NIGHT = col("#05060c")
    private val RED_VIG = col("#d01818")
    private val AMBER_VIG = col("#d08010")
    private val FLASH = col("#ff3030")
    private val HINT = rgba(255, 255, 255, 0.92f)
    private val BOARD_A = col("#3b3350")
    private val BOARD_B = col("#342d49")
    private val CARPET_A = col("#8a2b3d")
    private val CARPET_B = col("#7a2535")
    private val TRIM = col("#d8b45a")
    private val PANEL_L = rgba(255, 255, 255, 0.05f)
    private val PANEL_R = rgba(255, 255, 255, 0.03f)
    private val DOOR_FRAME = col("#5b3f26")
    private val DOOR = col("#7c5733")
    private val KNOB = col("#f2c14e")
    private val POSTER = col("#f3e7b8")
    private val POSTER_RED = col("#c0392b")
    private val LAMP = col("#fff3c4")
    private val LAMP_GLOW = col("#ffe9a0")
    private val BANNER_INK = col("#1c1a26")
    private val art = Art()

    private class Art {
        // walls, ceiling, far door: the part of the hallway that never moves
        val hallBase by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#10131f"), 1f to col("#1a1f30"))))
                val p = Path()
                fun q(color: Int, vararg pts: Float) {
                    p.reset()
                    p.moveTo(pts[0], pts[1])
                    var i = 2
                    while (i < pts.size) {
                        p.lineTo(pts[i], pts[i + 1])
                        i += 2
                    }
                    p.close()
                    c.drawPath(p, fill(color))
                }
                val far = FAR.toFloat()
                q(col("#171c2a"), px(-1f, 0f), py(0f, 1f), px(1f, 0f), py(0f, 1f), px(1f, far), py(far, 1f), px(-1f, far), py(far, 1f))
                fun wl(color: Int, side: Float, h1: Float, h2: Float) =
                    q(color, px(side, 0f), py(0f, h1), px(side, far), py(far, h1), px(side, far), py(far, h2), px(side, 0f), py(0f, h2))
                wl(col("#35566e"), -1f, 0f, 1f)
                wl(col("#2d4a60"), 1f, 0f, 1f)
                wl(col("#223548"), -1f, 0f, 0.26f)
                wl(col("#1b2b3b"), 1f, 0f, 0.26f)
                // the glowing exit at the far end
                val x1 = px(-1f, far)
                val y1 = py(far, 0f)
                val x2 = px(1f, far)
                val y2 = py(far, 1f)
                c.drawRect(x1, y2, x2, y1, fill().shade(rad(CX, 245f, 2f, 60f, 0f to col("#f6fbff"), 0.5f to col("#a5d4ff"), 1f to col("#3b5a82"))))
            }
        }

        // the soft murk that swallows the far end of the hall
        val fog by lazy {
            makeSprite(W, H, 0.75f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(rad(CX, 252f, 10f, 260f, 0f to rgba(18, 22, 36, 0.9f), 0.5f to rgba(18, 22, 36, 0.5f), 1f to rgba(18, 22, 36, 0f))))
            }
        }

        val cursorFront by lazy { cursorArt(true) }
        val cursorBack by lazy { cursorArt(false) }

        // one cursor arrow, painted facing away or facing you
        private fun cursorArt(front: Boolean): Sprite = makeSprite(170f, 230f) { c ->
            c.translate(22f, 22f)
            c.scale(6f, 6f)
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, 26f)
                lineTo(7f, 20f)
                lineTo(12f, 31f)
                lineTo(17f, 28.5f)
                lineTo(12f, 18f)
                lineTo(21f, 18f)
                close()
            }
            val gr = if (front) lin(0f, 0f, 21f, 31f, 0f to WHITE, 1f to col("#d4d9e6")) else lin(0f, 0f, 21f, 31f, 0f to col("#aab2c8"), 1f to col("#6e7690"))
            c.drawPath(path, fill().shade(gr))
            c.drawPath(path, stroke(col("#14121c"), 0.9f))
            if (!front) {
                // a fold line down the back
                val fold = stroke(rgba(20, 20, 40, 0.3f), 0.4f)
                c.drawLine(2.2f, 3f, 2.2f, 22f, fold)
                c.drawLine(4.5f, 19.5f, 10f, 28f, fold)
                return@makeSprite
            }
            // furious face
            val ink = stroke(col("#14121c"), 0.4f)
            val white = fill(WHITE)
            for (ex in floatArrayOf(5f, 10.8f)) {
                val r = RectF(ex - 2.1f, 14.5f - 2.6f, ex + 2.1f, 14.5f + 2.6f)
                c.drawOval(r, white)
                c.drawOval(r, ink)
            }
            val pupil = fill(col("#d4141c"))
            for (ex in floatArrayOf(5.3f, 10.5f)) c.drawCircle(ex, 15f, 1f, pupil)
            val brow = stroke(col("#14121c"), 0.8f)
            c.drawLine(2.2f, 11.2f, 6.6f, 13f, brow)
            c.drawLine(12.6f, 11.4f, 9f, 13f, brow)
            val mouth = Path().apply {
                moveTo(3f, 19f)
                lineTo(4.4f, 18.2f)
                lineTo(5.8f, 19.2f)
                lineTo(7.2f, 18.2f)
                lineTo(8.6f, 19.2f)
                lineTo(10f, 18.4f)
            }
            c.drawPath(mouth, brow)
        }
    }

}
