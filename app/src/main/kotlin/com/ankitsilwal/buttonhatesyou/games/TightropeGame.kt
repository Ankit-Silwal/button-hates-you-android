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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// Wobble Walk: the button crosses a tightrope high above a city. It walks on
// its own; hold the left or right half of the screen to lean against the
// wobble, the wind and the pigeons. Past ~1 rad of tilt it falls.
// Port of src/adventure/games/tightrope.js.
class TightropeGame(private val ctx: GameContext) : Game {
    private companion object {
        const val PX = 30f // pixels per meter
        const val CX = W / 2f
        const val ROPE = 520f // rope height at the button
        const val SIZE = 74f
        const val LIMIT = 1f // radians of tilt before the fall
        const val LEAN = 10f // rad/s^2 of lean while a side is held
        const val FALL_ACC = 4f // the tilt feeds itself: an upside-down pendulum
        const val SPAN = 118f // half length of the balancing pole
        const val MAX_STREAKS = 40

        const val PIGEON = 0
        const val LADDER = 1
        const val BALLOON = 2

        val START = arrayOf("I regret this.", "Nope nope nope.", "DON’T LOOK DOWN", "Who approved this?", "No shoes. Great.")
        val SCARED = arrayOf("DON’T LOOK DOWN", "Nope nope nope.", "Mama!", "I regret this.", "Not like this!")
        val RELIEF = arrayOf("Totally meant that.", "Smooth.", "I was never worried.", "Nailed it. Barely.", "Pfft. Easy.")
        val IDLE = arrayOf("Is the rope… moving?", "It is a long way down.", "I should have stayed in bed.", "Wind. Of course.", "Look at me go. Slowly.")
        val WIND = arrayOf("Is that wind?", "Oh no.", "Here it comes.", "Hold on, hold on.")
        val BIRD = arrayOf("PIGEON!", "Rude!", "Move it, feathers!")
        val LADDERS = arrayOf("Why is there a LADDER.", "A wobbly bit. Great.", "Rungs. Nobody asked.")
        val BALLOONS = arrayOf("Is that a balloon?", "Oh no, it’s gassy.", "Please don’t.")
        val FALL = arrayOf("AAAAAH!", "Tell my mother…", "Whoooaaa!", "Of COURSE.")
        val PUFF_TXT = arrayOf("PFFT", "ACHOO!", "BRAAP")
        val PUFF_COL = arrayOf("#b6e35a", "#ffffff", "#d8e66a")
        val FLAGS = arrayOf("#ff6f7a", "#ffd84d", "#6fb7ff", "#8fe08a")
    }

    private class Puff(val x0: Float, val txt: String, val col: Int, val dir: Float) {
        var p = 0f
        var done = false
    }

    private class Hazard(val k: Int, val side: Float) {
        var x = 0f
        var t = 0f
        var hit = false
        var d0 = 0f
        var d1 = 0f
        var puff = 1.2f
        var left = 3
        val puffs = ArrayList<Puff>(3)
        var dead = false
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val lookDark = look.dark.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val mouth = Mouth()
    private val fx = Fx()
    private val ph = floatArrayOf(rand(0f, 6.3f), rand(0f, 6.3f), rand(0f, 6.3f))
    private val birdX = FloatArray(5) { rand(0f, W) }
    private val birdY = FloatArray(5) { rand(150f, 430f) }
    private val birdS = FloatArray(5) { rand(14f, 26f) }
    private val birdV = FloatArray(5) { rand(0.2f, 0.5f) }
    private val birdP = FloatArray(5) { it * 1.7f }

    private val skX = FloatArray(MAX_STREAKS)
    private val skY = FloatArray(MAX_STREAKS)
    private val skL = FloatArray(MAX_STREAKS)
    private val skV = FloatArray(MAX_STREAKS)
    private val skA = FloatArray(MAX_STREAKS)
    private var streaks = 0

    private val haz = ArrayList<Hazard>(4)

    // the gust, if one is blowing
    private var gustOn = false
    private var gDir = 1f
    private var gStr = 0f
    private var gWarn = 0f
    private var gDur = 0f
    private var gT = 0f
    private var gNext = 4.5f
    private var nextHaz = 70f

    // the fall, if it has started
    private var falling = false
    private var fX = 0f
    private var fY = 0f
    private var fVx = 0f
    private var fVy = 0f
    private var fRot = 0f
    private var fVr = 0f
    private var fT = 0f
    private var poof = false
    private var tPoof = 0f
    private var pX = 0f
    private var pY = 0f
    private var pVx = 0f
    private var pVy = 0f
    private var pRot = 0f
    private var pVr = 0f

    private var banner: String? = null
    private var bannerT = 0f
    private var t = 0f
    private var sc = 0f // pixels scrolled, for the scenery
    private var dist = 0f
    private var speed = 5f
    private var tilt = 0f
    private var w = 0f // angular velocity
    private var ptr = 0f // -1 / 0 / 1 from the pointer
    private var held = false
    private var lean = 0f
    private var calm = 0f // seconds of gentle air after a Continue
    private var bounce = 0f // rope bounce, decays
    private var ladderF = 0f
    private var mile = 0
    private var danger = false
    private var smug = 0f
    private var scare = 0f // cooldown for the scared quip
    private var chat = 7f
    private var windVis = 0f
    private var started = false

    private fun busy(): Boolean {
        if (gustOn) return true
        for (h in haz) if (h.k != LADDER) return true
        return false
    }

    private fun say(lines: Array<String>, sec: Float = 1.8f) = mouth.say(pick(lines), sec)

    private fun shove(v: Float) {
        w += v
        bounce = min(1f, bounce + 0.6f)
    }

    private fun startGust(diff: Float) {
        gustOn = true
        gDir = if (Random.nextFloat() < 0.5f) -1f else 1f
        gStr = (2.6f + diff * 3.9f) * rand(0.85f, 1.1f)
        gWarn = 1.3f - diff * 0.45f
        gDur = rand(1.3f, 2f)
        gT = 0f
        if (Random.nextFloat() < 0.5f) say(WIND, 1.5f)
    }

    private fun spawnHazard() {
        val kind = if (mile == 0 && dist < 120f) PIGEON else Random.nextInt(3)
        val side = if (Random.nextFloat() < 0.5f) -1f else 1f
        val h = Hazard(kind, side)
        when (kind) {
            PIGEON -> {
                h.x = if (side < 0f) -50f else W + 50f
                say(BIRD, 1.4f)
            }
            LADDER -> {
                h.d0 = dist + 10f
                h.d1 = dist + 27f
                say(LADDERS, 2f)
            }
            else -> {
                h.x = if (side < 0f) -100f else W + 100f
                say(BALLOONS, 1.8f)
            }
        }
        haz.add(h)
    }

    private fun startFall() {
        val s = if (tilt < 0f) -1f else 1f
        falling = true
        fX = 0f
        fY = 0f
        fVx = s * 70f
        fVy = -280f
        fRot = tilt
        fVr = s * 6f
        fT = 0f
        poof = false
        tPoof = 0f
        pX = 0f
        pY = -30f
        pVx = -s * 120f
        pVy = -200f
        pRot = tilt
        pVr = -s * 9f
        ctx.sfx.play("bad")
        ctx.haptics.thud()
        say(FALL, 2f)
        fx.float(CX, ROPE - 130f, "AAAAH!")
        fx.burst(CX, ROPE - 20f, col("#fff3a8"), 10, 260f)
    }

    // y of the rope at screen x: sags under the button, bobs when bounced
    private fun ropeAt(x: Float): Float {
        val dx = abs(x - CX) / CX
        val diff = clamp(dist / 500f, 0f, 1f)
        val bob = sin(t * 9f) * (bounce * 7f + diff * 2.5f + 1f) * (1f - dx * 0.7f)
        return ROPE + bob - (8f + diff * 14f) * dx.pow(1.4f)
    }

    override fun down(x: Float, y: Float) {
        if (x.isNaN()) return
        held = true
        ptr = if (x < CX) -1f else 1f
    }

    override fun move(x: Float, y: Float) {
        if (held && !x.isNaN()) ptr = if (x < CX) -1f else 1f
    }

    override fun up(x: Float, y: Float) {
        held = false
        ptr = 0f
    }

    // a paid Continue: upright again, with a few seconds of gentle air
    override fun revive() {
        over = false
        falling = false
        tilt = 0f
        w = 0f
        calm = 2.6f
        gustOn = false
        gNext = 4f
        haz.removeAll { !(it.k == LADDER && it.d1 > dist) }
        nextHaz = max(nextHaz, dist + 40f)
        reviveFx(fx, mouth, ctx, CX, ROPE - 70f, lookColor)
    }

    override fun update(dt0: Float) {
        val dt = if (dt0.isNaN()) 0f else clamp(dt0, 0f, 0.05f)
        t += dt
        mouth.update(dt)
        fx.update(dt)
        bounce = max(0f, bounce - dt * 0.8f)
        smug = max(0f, smug - dt)
        scare = max(0f, scare - dt)
        if (banner != null) {
            bannerT += dt
            if (bannerT > 2.4f) banner = null
        }
        if (t > 0.4f && !started) {
            started = true
            say(START, 2.6f)
        }

        // wind streaks
        val dir = if (gustOn) gDir else 0f
        windVis = if (gustOn) dir * (if (gT < gWarn) 0.5f * (gT / gWarn) else 1f) else 0f
        if (Random.nextFloat() < abs(windVis) * 30f * dt && streaks < MAX_STREAKS) {
            val i = streaks++
            skX[i] = if (dir > 0f) -60f else W + 60f
            skY[i] = rand(200f, 690f)
            skL[i] = rand(40f, 110f)
            skV[i] = rand(700f, 1000f)
            skA[i] = rand(0.3f, 0.7f)
        }
        var i = streaks - 1
        while (i >= 0) {
            skX[i] += (if (windVis < 0f) -1f else 1f) * skV[i] * dt
            if (skX[i] < -140f || skX[i] > W + 140f) {
                val l = --streaks
                skX[i] = skX[l]; skY[i] = skY[l]; skL[i] = skL[l]; skV[i] = skV[l]; skA[i] = skA[l]
            }
            i--
        }

        val span = W + 80f
        for (b in 0 until 5) {
            val v = birdX[b] - birdV[b] * 14f * dt - speed * PX * 0.04f * dt + 40f
            birdX[b] = ((v % span) + span) % span - 40f
        }

        if (falling) {
            stepFall(dt)
            sc += 30f * dt
        } else if (!over) {
            sc += speed * PX * dt
            stepWalk(dt)
        }
        // hazards keep moving (and puffs keep flying) even in the fall
        stepHazards(dt)
    }

    /* ---------- simulation ---------- */

    private fun stepWalk(dt: Float) {
        val diff = clamp(dist / 500f, 0f, 1f)
        speed = clamp(5f + dist * 0.018f, 5f, 11.5f)
        dist += speed * dt
        score = floor(dist).toInt()
        calm = max(0f, calm - dt)
        // gentle at the start, and for a moment after a Continue
        val ease = clamp((t - 0.6f) / 4f, 0.05f, 1f)
        val f = min(ease, if (calm > 0f) clamp(1f - calm / 2f, 0.1f, 1f) else 1f)

        // gusts: telegraphed by the flag and streaks, then they push
        var gustAcc = 0f
        if (gustOn) {
            gT += dt
            if (gT >= gWarn) {
                val p = (gT - gWarn) / gDur
                if (p >= 1f) {
                    gustOn = false
                    gNext = rand(5.5f - diff * 3.3f, 8f - diff * 3.8f)
                } else {
                    gustAcc = gDir * gStr * sin(PI_F * p).coerceAtLeast(0f).pow(0.6f)
                }
            }
        } else if (t > 4f && calm <= 0f) {
            gNext -= dt
            if (gNext <= 0f) {
                if (busy()) gNext = 0.5f else startGust(diff)
            }
        }

        // the wobbly ladder bit shakes the walker harder
        var onLadder = false
        for (h in haz) if (h.k == LADDER && dist >= h.d0 && dist <= h.d1) onLadder = true
        ladderF += ((if (onLadder) 1f else 0f) - ladderF) * min(1f, dt * 4f)
        if (onLadder) bounce = max(bounce, 0.45f)

        val n = sin(t * 1.3f + ph[0]) + 0.6f * sin(t * 2.9f + ph[1]) + 0.35f * sin(t * 5.3f + ph[2])
        val wob = n * (1.1f + diff * 1.9f) * (1f + ladderF * 0.9f) * f

        val target = clamp(ptr, -1f, 1f)
        lean += (target - lean) * min(1f, dt * 12f)

        w += (FALL_ACC * tilt + wob + gustAcc * f + LEAN * lean) * dt
        w -= w * 0.9f * dt
        if (w.isNaN() || tilt.isNaN() || w.isInfinite() || tilt.isInfinite()) {
            w = 0f
            tilt = 0f
        }
        tilt += w * dt

        // hazards on a ~100 m beat, one at a time, never during a gust
        if (dist >= nextHaz && calm <= 0f && !busy()) {
            spawnHazard()
            nextHaz = dist + rand(92f, 112f)
        } else if (dist >= nextHaz) {
            nextHaz = dist + 2f
        }

        // moods and quips
        val a = abs(tilt)
        if (a > 0.6f) {
            danger = true
            if (scare <= 0f && mouth.left <= 0f) {
                say(SCARED, 1.3f)
                scare = 5f
            }
        } else if (danger && a < 0.18f) {
            danger = false
            smug = 1.3f
            if (Random.nextFloat() < 0.6f) say(RELIEF, 1.5f)
            fx.float(CX + 40f, ROPE - 90f, "phew", col("#fff3a8"))
        }
        chat -= dt
        if (chat <= 0f) {
            chat = rand(8f, 13f)
            if (mouth.left <= 0f) say(IDLE, 2f)
        }

        // milestone every 100 m
        val m = floor(dist / 100f).toInt()
        if (m > mile) {
            mile = m
            banner = "${m * 100} m!"
            bannerT = 0f
            ctx.sfx.play("win")
            ctx.haptics.tap()
            for (x in floatArrayOf(110f, 240f, 370f)) fx.burst(x, 250f + rand(-20f, 20f), col(pick(arrayOf("#ffd84d", "#ff7aa8", "#8fe9ff"))), 14, 280f)
        }

        if (calm > 0f) {
            tilt = clamp(tilt, -0.9f, 0.9f)
        } else if (abs(tilt) > LIMIT) {
            tilt = clamp(tilt, -LIMIT, LIMIT)
            startFall()
        }
    }

    private fun stepFall(dt: Float) {
        fT += dt
        fVy += 900f * dt
        fX += fVx * dt
        fY += fVy * dt
        fRot += fVr * dt
        pVy += 900f * dt
        pX += pVx * dt
        pY += pVy * dt
        pRot += pVr * dt
        fVx += sin(fT * 10f) * 40f * dt // dizzy drift
        if (!poof && fY > 215f) {
            poof = true
            tPoof = 0f
            for (k in 0 until 3) fx.burst(CX + fX + rand(-30f, 30f), ROPE + 210f, WHITE, 10, 200f, gravity = -50f, size = 5f, life = 0.9f)
            fx.ring(CX + fX, ROPE + 200f, WHITE, 90f, 0.5f, 8f)
            ctx.sfx.play("thud")
            ctx.haptics.thud()
            mouth.say("…I’m fine.", 1.6f)
        }
        if (poof && !over) {
            tPoof += dt
            if (tPoof > 0.7f) {
                over = true
                ctx.sfx.play("sob")
            }
        }
    }

    private fun stepHazards(dt: Float) {
        for (h in haz) {
            when (h.k) {
                PIGEON -> {
                    h.t += dt
                    h.x -= h.side * 190f * dt // side -1 flies right, +1 flies left
                    if (!h.hit && !falling && !over && abs(h.x - CX) < 38f) {
                        h.hit = true
                        shove(-h.side * 2.6f)
                        ctx.sfx.play("hit")
                        ctx.haptics.thud()
                        fx.burst(h.x, ROPE - 70f, col("#e8e4f0"), 10, 220f, gravity = 200f, size = 2.5f, life = 0.8f)
                        fx.float(CX, ROPE - 150f, "BONK", col("#ffd84d"))
                    }
                    h.dead = h.x < -70f || h.x > W + 70f
                }
                BALLOON -> {
                    h.t += dt
                    h.x -= h.side * 80f * dt
                    if (h.left > 0 && h.x > 70f && h.x < W - 70f && !falling && !over) {
                        h.puff -= dt
                        if (h.puff <= 0f) {
                            h.puff = 1.5f
                            h.left--
                            val k = Random.nextInt(PUFF_TXT.size)
                            val d = if (abs(h.x - CX) < 20f) (if (Random.nextBoolean()) -1f else 1f) else sign(CX - h.x)
                            h.puffs.add(Puff(h.x, PUFF_TXT[k], col(PUFF_COL[k]), d))
                        }
                    }
                    for (q in h.puffs) {
                        q.p += dt / 1.2f
                        if (q.p >= 1f && !q.done) {
                            q.done = true
                            if (!falling && !over) {
                                shove(q.dir * 2.2f)
                                ctx.sfx.play("pop")
                                ctx.haptics.tap()
                                fx.burst(CX, ROPE - 60f, q.col, 10, 200f, gravity = -80f, size = 4f, life = 0.7f)
                            }
                        }
                    }
                    h.puffs.removeAll { it.p >= 1.15f }
                    h.dead = h.x < -140f || h.x > W + 140f || h.t > 20f
                }
                else -> h.dead = dist > h.d1 + 12f
            }
        }
        haz.removeAll { it.dead }
    }

    /* ---------- drawing ---------- */

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        scenery(c)
        line.strokeWidth = 2f
        line.color = WHITE
        for (k in 0 until streaks) {
            line.alpha = (skA[k] * 0.55f * 255f).toInt()
            c.drawLine(skX[k], skY[k], skX[k] - (if (windVis < 0f) -1f else 1f) * skL[k], skY[k], line)
        }

        rope(c)
        for (h in haz) if (h.k == LADDER) ladder(c, h)

        val by = ropeAt(CX)
        if (falling) drawFall(c) else drawWalker(c, by)
        for (h in haz) if (h.k == PIGEON) pigeon(c, h)
        fx.draw(c)

        fogFront(c)
        for (h in haz) if (h.k == BALLOON) balloon(c, h)
        flag(c, windVis)

        if (!falling && abs(tilt) > 0.55f) c.vignette((clamp((abs(tilt) - 0.5f) * 1.2f, 0f, 0.6f) * 5f).roundToInt() / 5f + 0.2f, RED_VIG)
        c.vignette(0.25f, DUSK)
        mouth.draw(c, CX, if (falling) 380f else by - 104f)
        c.drawScore(score, "meters")
        meter(c)
        if (t < 3f) Txt.draw(c, "Hold left / right to lean", CX, 188f, 16f, HINT)
        banner?.let { drawBanner(c, it) }
    }

    private fun DrawScope.drawWalker(c: Canvas, by: Float) {
        val a = blinkAlpha(calm, t)
        val stride = sin(t * speed * 1.7f)
        val mood = when {
            smug > 0f -> Mood.Smug
            abs(tilt) > 0.62f -> Mood.Angry
            abs(tilt) > 0.3f -> Mood.Worried
            calm > 0f -> Mood.Happy
            else -> Mood.Calm
        }
        val layered = a < 0.99f
        if (layered) c.saveLayerAlpha(0f, 0f, W, H, (a * 255f).toInt()) else c.save()
        c.translate(CX, by)
        c.rotate(tilt * DEG)
        // tiny feet taking turns
        paint.color = lookDark
        for (s in intArrayOf(-1, 1)) {
            val fy = -3f - max(0f, stride * s) * 5f
            oval.set(s * 10f + 4f - 10f, fy - 5f, s * 10f + 4f + 10f, fy + 5f)
            c.drawOval(oval, paint)
        }
        bobby(c, 0f, -SIZE * 0.38f, SIZE, look, mood = mood, t = t, squash = stride * 0.025f)
        // the pole counter-swings against the tilt
        c.translate(0f, -30f)
        c.rotate((-tilt * 0.65f + sin(t * 3f) * 0.03f) * DEG)
        pole(c)
        paint.color = lookDark
        for (s in intArrayOf(-1, 1)) c.drawCircle(s * 24f, 0f, 5.5f, paint)
        c.restore()
        if (abs(tilt) > 0.5f && !over) {
            Txt.draw(c, "!", CX + 52f * sign(tilt), by - 100f, 30f, col("#ff4f5e"), alpha = 0.9f)
        }
    }

    private fun DrawScope.drawFall(c: Canvas) {
        c.save()
        c.translate(CX + pX, ROPE + pY)
        c.rotate(pRot * DEG)
        pole(c)
        c.restore()
        val x = CX + fX
        val y = ROPE - SIZE * 0.38f + fY
        bobby(c, x, y, SIZE, look, mood = if (fT < 0.5f) Mood.Angry else Mood.Closed, t = t, angle = fRot, flash = fT < 0.06f)
        // dizzy stars circling its head
        for (i in 0 until 3) {
            val a = t * 9f + i * TAU / 3f
            val sx = x + cos(a) * 34f
            val sy = y - 44f + sin(a) * 9f
            c.glow(sx, sy, 15f, STAR_GLOW, 0.8f)
            c.star(sx, sy, 5f, starFill, a)
        }
    }

    private fun pole(c: Canvas) {
        oval.set(-SPAN, -3f, SPAN, 3f)
        c.drawRoundRect(oval, 3f, 3f, poleFill)
        for (s in intArrayOf(-1, 1)) {
            paint.color = if (s < 0) col("#ff6f7a") else col("#6fb7ff")
            c.drawCircle(s * SPAN, 0f, 8f, paint)
            paint.color = rgba(255, 255, 255, 0.6f)
            c.drawCircle(s * SPAN - 2.5f, -2.5f, 2.5f, paint)
        }
    }

    // the rope itself with bunting that streams past to show the speed
    private fun rope(c: Canvas) {
        ropePath.reset()
        var x = -10f
        var first = true
        while (x <= W + 10f) {
            if (first) ropePath.moveTo(x, ropeAt(x)) else ropePath.lineTo(x, ropeAt(x))
            first = false
            x += 10f
        }
        line.color = col("#4a3a30")
        line.strokeWidth = 5f
        c.drawPath(ropePath, line)
        c.save()
        c.translate(0f, -1.5f)
        line.color = rgba(255, 225, 180, 0.55f)
        line.strokeWidth = 1.5f
        c.drawPath(ropePath, line)
        c.restore()
        // twist marks
        line.color = rgba(30, 20, 15, 0.5f)
        val off = sc % 14f
        x = -off
        while (x < W) {
            val y = ropeAt(x)
            c.drawLine(x, y - 2f, x + 5f, y + 2f, line)
            x += 14f
        }
        // bunting every 2 m
        val gap = 2f * PX
        val firstK = floor(sc / gap).toInt() - 1
        val count = (W / gap).toInt() + 3
        for (k in firstK until firstK + count) {
            val bx = k * gap - sc
            if (bx < -20f || bx > W + 20f || abs(bx - CX) < 50f) continue
            val y = ropeAt(bx)
            paint.color = col(FLAGS[((k % 4) + 4) % 4])
            tri.reset()
            tri.moveTo(bx - 8f, y + 1f)
            tri.lineTo(bx + 8f, y + 1f)
            tri.lineTo(bx, y + 20f)
            tri.close()
            c.drawPath(tri, paint)
        }
    }

    private fun ladder(c: Canvas, h: Hazard) {
        val x0 = CX + (h.d0 - dist) * PX
        val x1 = CX + (h.d1 - dist) * PX
        if (x1 < -20f || x0 > W + 20f) return
        val a = max(-10f, x0)
        val b = min(W + 10f, x1)
        line.color = col("#7a4f2a")
        line.strokeWidth = 4f
        for (dy in floatArrayOf(-6f, 8f)) {
            ropePath.reset()
            var x = a
            var first = true
            while (x <= b) {
                if (first) ropePath.moveTo(x, ropeAt(x) + dy) else ropePath.lineTo(x, ropeAt(x) + dy)
                first = false
                x += 10f
            }
            c.drawPath(ropePath, line)
        }
        line.strokeWidth = 3f
        line.color = col("#c99a62")
        var x = x0
        while (x <= x1) {
            if (x >= -5f && x <= W + 5f) {
                val y = ropeAt(x)
                c.drawLine(x, y - 6f, x, y + 8f, line)
            }
            x += 27f
        }
    }

    private fun pigeon(c: Canvas, h: Hazard) {
        val dir = -h.side // facing the way it flies
        val y = ROPE - 76f + sin(h.t * 7f) * 8f
        val flap = sin(h.t * 22f)
        c.save()
        c.translate(h.x, y)
        c.scale(dir, 1f)
        paint.color = col("#9ba3b8")
        oval.set(-20f, -12f, 20f, 12f)
        c.drawOval(oval, paint)
        paint.color = col("#7e87a0")
        tri.reset()
        tri.moveTo(-14f, -2f)
        tri.lineTo(-34f, -6f + flap * 3f)
        tri.lineTo(-16f, 8f)
        tri.close()
        c.drawPath(tri, paint)
        paint.color = col("#b9c0d4")
        c.save()
        c.translate(-2f, -4f - flap * 10f)
        c.rotate((-0.4f + flap * 0.5f) * DEG)
        oval.set(-9f, -15f, 9f, 15f)
        c.drawOval(oval, paint)
        c.restore()
        paint.color = col("#7fb3a0")
        c.drawCircle(16f, -8f, 8f, paint)
        paint.color = col("#ffb347")
        tri.reset()
        tri.moveTo(22f, -9f)
        tri.lineTo(31f, -6f)
        tri.lineTo(22f, -5f)
        tri.close()
        c.drawPath(tri, paint)
        paint.color = WHITE
        c.drawCircle(18f, -10f, 3f, paint)
        paint.color = col("#222222")
        c.drawCircle(19f, -10f, 1.4f, paint)
        // grumpy brow
        line.color = col("#2b2b3a")
        line.strokeWidth = 2f
        c.drawLine(14f, -15f, 23f, -12f, line)
        c.restore()
    }

    // the passerby hot-air balloon that lets one rip
    private fun balloon(c: Canvas, h: Hazard) {
        val y = 230f + sin(h.t * 1.6f) * 10f
        c.sprite(art.balloonRed, h.x - 55f, y - 80f, 110f, 150f)
        for (q in h.puffs) {
            val p = clamp(q.p, 0f, 1f)
            val x = q.x0 + (CX - q.x0) * p
            val yy = 300f + (ROPE - 70f - 300f) * p
            val r = 14f + p * 20f
            val a = clamp((1.15f - q.p) * 3f, 0f, 1f)
            c.glow(x, yy, r * 2f, q.col, 0.7f * a)
            paint.color = rgba(q.col, a)
            c.drawCircle(x - r * 0.5f, yy, r * 0.7f, paint)
            c.drawCircle(x + r * 0.5f, yy + 2f, r * 0.65f, paint)
            c.drawCircle(x, yy - r * 0.4f, r * 0.7f, paint)
            Txt.draw(c, q.txt, x, yy - r - 12f, 18f, WHITE, alpha = a)
        }
    }

    // a pennant on a little pole: limp when calm, streaming when the wind is up
    private fun flag(c: Canvas, v: Float) {
        val px = W - 62f
        val py = 232f
        line.color = col("#5a4a58")
        line.strokeWidth = 3f
        c.drawLine(px, py + 56f, px, py - 4f, line)
        val k = abs(v)
        val dir = if (v < 0f) -1f else 1f
        val ang = 1.2f * (1f - min(1f, k * 1.2f))
        val len = 40f
        val fl = sin(t * (6f + k * 16f)) * (3f + k * 7f)
        val tx = px + dir * cos(ang) * len
        val ty = py + 6f + sin(ang) * len + fl
        paint.color = if (k > 0.8f) col("#ff4f5e") else if (k > 0.2f) col("#ffb347") else col("#ff6f7a")
        tri.reset()
        tri.moveTo(px, py)
        tri.quadTo((px + tx) / 2f, (py + ty) / 2f - fl * 0.8f, tx, ty)
        tri.lineTo(px, py + 14f)
        tri.close()
        c.drawPath(tri, paint)
        if (k > 0.2f) {
            Txt.draw(c, if (dir > 0f) "WIND →" else "← WIND", px - 6f * dir, py - 20f, 13f, WHITE, alpha = 0.6f + 0.4f * sin(t * 10f))
        }
    }

    // a readout of how far over you are leaning
    private fun meter(c: Canvas) {
        val x = CX - 90f
        val y = 154f
        paint.color = rgba(30, 14, 40, 0.28f)
        oval.set(x, y, x + 180f, y + 9f)
        c.drawRoundRect(oval, 4.5f, 4.5f, paint)
        val a = clamp(abs(tilt) / LIMIT, 0f, 1f)
        paint.color = if (a > 0.75f) col("#ff4f5e") else if (a > 0.45f) col("#ffb347") else col("#8fe08a")
        val wd = a * 90f
        if (wd > 0.5f) {
            if (tilt < 0f) oval.set(CX - wd, y, CX, y + 9f) else oval.set(CX, y, CX + wd, y + 9f)
            c.drawRoundRect(oval, 4.5f, 4.5f, paint)
        }
        paint.color = rgba(255, 255, 255, 0.9f)
        c.drawRect(CX - 1f, y - 2f, CX + 1f, y + 11f, paint)
    }

    private fun drawBanner(c: Canvas, txt: String) {
        val b = bannerT
        val k = clamp(if (b < 0.3f) b / 0.3f else if (b > 2f) max(0f, (2.4f - b) / 0.4f) else 1f, 0f, 1f)
        val y = 300f - (1f - k) * 40f
        oval.set(CX - 120f, y - 30f, CX + 120f, y + 30f)
        paint.color = rgba(col("#ffd84d"), k)
        c.drawRoundRect(oval, 14f, 14f, paint)
        bannerLine.color = rgba(col("#a8731a"), k)
        c.drawRoundRect(oval, 14f, 14f, bannerLine)
        Txt.draw(c, txt, CX, y + 1f, 34f, col("#5a2d0c"), alpha = k)
    }

    /* ---------- scenery ---------- */

    private fun scenery(c: Canvas) {
        c.sprite(art.sky, 0f, 0f, W, H)
        c.scrollTile(art.cloud1, sc * 0.015f + t * 4f, 150f)
        // far skyline and a drifting balloon
        c.scrollTile(art.sky1, sc * 0.04f, 590f)
        val span = W + 200f
        val bx = (((560f - t * 10f - sc * 0.03f) % span) + span) % span - 100f
        c.sprite(art.balloonBlue, bx - 28f, 390f + sin(t * 0.8f) * 8f, 56f, 76f, 0.9f)
        line.color = rgba(70, 60, 110, 0.6f)
        line.strokeWidth = 1.8f
        for (b in 0 until 5) birdShape(c, birdX[b], birdY[b], birdS[b] * 0.5f, sin(t * 9f + birdP[b]))
        c.scrollTile(art.cloud2, sc * 0.05f + t * 8f, 380f)
        c.scrollTile(art.sky2, sc * 0.1f, 640f)
        c.scrollTile(art.sky3, sc * 0.2f, 690f)
    }

    private fun birdShape(c: Canvas, x: Float, y: Float, s: Float, flap: Float) {
        tri.reset()
        tri.moveTo(x - s, y - flap * s * 0.4f)
        tri.quadTo(x - s * 0.4f, y - s * 0.5f - flap * s * 0.3f, x, y)
        tri.quadTo(x + s * 0.4f, y - s * 0.5f - flap * s * 0.3f, x + s, y - flap * s * 0.4f)
        c.drawPath(tri, line)
    }

    // drifting fog that swallows whatever falls off the rope
    private fun fogFront(c: Canvas) {
        c.scrollTile(art.fog, t * 12f, 640f)
        c.scrollTile(art.fog, -t * 7f + 200f, 690f)
        c.sprite(art.fogFloor, 0f, 735f, W, H - 735f)
    }

    /* ---------- art ---------- */

    private val paint = fill(WHITE)
    private val line = stroke(WHITE, 2f)
    private val bannerLine = stroke(WHITE, 4f)
    private val starFill = fill(col("#fff3a0"))
    private val poleFill = fill().shade(lin(0f, -3f, 0f, 3f, 0f to col("#c99a62"), 1f to col("#6e4a2b")))
    private val ropePath = Path()
    private val tri = Path()
    private val oval = RectF()
    private val RED_VIG = col("#c0182a")
    private val DUSK = col("#2a1840")
    private val STAR_GLOW = col("#ffd84d")
    private val HINT = rgba(255, 255, 255, 0.92f)
    private val art = Art()

    private class Art {
        val sky by lazy {
            makeSprite(W, H, 1f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#4f9be6"), 0.45f to col("#9dd3f7"), 0.75f to col("#ffd9e6"), 1f to col("#ffeacb"))))
                c.drawRect(0f, 0f, W, H, fill().shade(rad(360f, 330f, 0f, 240f, 0f to rgba(255, 248, 214, 0.95f), 0.15f to rgba(255, 236, 170, 0.55f), 1f to rgba(255, 236, 170, 0f))))
            }
        }
        val cloud1 by lazy { cloudTile(4, 3f, 0.55f) }
        val cloud2 by lazy { cloudTile(3, 11f, 0.8f) }
        val fog by lazy { cloudTile(9, 21f, 0.97f) }
        val sky1 by lazy { skyline(150f, col("#c4c3e6"), col("#e9d9ee"), 1f, 40f, 110f, false) }
        val sky2 by lazy { skyline(140f, col("#8a89c4"), col("#c6b4d8"), 7f, 50f, 120f, true) }
        val sky3 by lazy { skyline(120f, col("#6a68a6"), col("#a58fc0"), 13f, 40f, 100f, true) }
        val balloonRed by lazy { balloonArt(col("#ff8a5c"), col("#ffe08a")) }
        val balloonBlue by lazy { balloonArt(col("#7fb2ff"), col("#ffe08a")) }
        val fogFloor by lazy {
            makeSprite(16f, H - 735f, 1f) { c ->
                c.drawRect(0f, 0f, 16f, H - 735f, fill().shade(lin(0f, 0f, 0f, H - 735f, 0f to rgba(255, 236, 244, 0f), 0.3f to rgba(255, 236, 244, 1f), 1f to col("#fff0f6"))))
            }
        }

        private fun cloudTile(n: Int, seed: Float, alpha: Float): Sprite = makeSprite(W, 330f, 1f) { c ->
            for (i in 0 until n) {
                val bx = hash(i + seed) * W
                val by = 40f + hash(i * 3f + seed + 5f) * 250f
                val r = 20f + hash(i * 7f + seed) * 22f
                val p = fill().shade(lin(0f, by - r, 0f, by + r, 0f to rgba(255, 255, 255, alpha), 1f to rgba(255, 222, 236, alpha * 0.8f)))
                for (ox in floatArrayOf(-W, 0f, W)) {
                    val path = Path()
                    path.addCircle(bx + ox, by, r, Path.Direction.CW)
                    path.addCircle(bx + ox - r * 1.1f, by + r * 0.3f, r * 0.75f, Path.Direction.CW)
                    path.addCircle(bx + ox + r * 1.2f, by + r * 0.25f, r * 0.8f, Path.Direction.CW)
                    path.addCircle(bx + ox + r * 0.3f, by - r * 0.5f, r * 0.8f, Path.Direction.CW)
                    c.drawPath(path, p)
                }
            }
        }

        private fun skyline(h: Float, top: Int, bottom: Int, seed: Float, lo: Float, hi: Float, lit: Boolean): Sprite = makeSprite(W, h, 1.5f) { c ->
            var x = 0f
            var i = 0
            val win = fill(rgba(255, 240, 200, 0.5f))
            while (x < W) {
                val bw = 22f + hash(i + seed) * 28f
                val bh = lo + hash(i * 3f + seed + 9f) * (hi - lo)
                val g = fill().shade(lin(0f, h - bh, 0f, h, 0f to top, 1f to bottom))
                c.drawRect(x, h - bh, x + bw + 1f, h, g)
                if (hash(i + seed + 20f) > 0.6f) c.drawRect(x + bw * 0.4f, h - bh - 14f, x + bw * 0.4f + 3f, h - bh, g) // antenna
                if (lit) {
                    var wy = h - bh + 8f
                    while (wy < h - 6f) {
                        var wx = x + 5f
                        while (wx < x + bw - 4f) {
                            if (hash(wx * 7f + wy) > 0.55f) c.drawRect(wx, wy, wx + 3f, wy + 4f, win)
                            wx += 9f
                        }
                        wy += 12f
                    }
                }
                x += bw
                i++
            }
        }

        private fun balloonArt(a: Int, b: Int): Sprite = makeSprite(110f, 150f) { c ->
            val env = Path()
            env.moveTo(55f, 4f)
            env.cubicTo(108f, 4f, 108f, 80f, 68f, 108f)
            env.lineTo(42f, 108f)
            env.cubicTo(2f, 80f, 2f, 4f, 55f, 4f)
            env.close()
            c.drawPath(env, fill().shade(rad(48f, 48f, 6f, 60f, 0f to col("#fff6dc"), 0.2f to a, 1f to col("#c04a2a"))))
            val stripe = Path()
            stripe.moveTo(55f, 4f)
            stripe.cubicTo(30f, 30f, 30f, 80f, 46f, 108f)
            stripe.lineTo(64f, 108f)
            stripe.cubicTo(80f, 80f, 80f, 30f, 55f, 4f)
            stripe.close()
            c.drawPath(stripe, fill(b))
            val rope = stroke(col("#6b4a2f"), 1.5f)
            c.drawLine(44f, 108f, 48f, 126f, rope)
            c.drawLine(66f, 108f, 62f, 126f, rope)
            c.drawRoundRect(RectF(46f, 126f, 64f, 140f), 3f, 3f, fill(col("#8a5a30")))
        }
    }
}
