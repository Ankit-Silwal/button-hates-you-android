package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Whack-a-Button: clones of the button pop out of nine holes. Bonk them, but
// not the ones holding a heart. 45 seconds on the clock.
// Port of src/adventure/games/whack.js.
class WhackGame(private val ctx: GameContext) : Game {
    private companion object {
        const val TIME = 45f
        const val MORE_TIME = 15f // seconds a paid Continue puts back on the clock
        const val SIZE = 84f
        const val RISE = 0.15f // seconds to pop up or duck down
        const val TRAVEL = SIZE * 1.15f
        const val HOLE_W = 58f
        const val HOLE_H = 18f
        const val HORIZON = 228f
        const val BOX_X = 100f
        const val BOX_UP = 190f
        const val BOX_DOWN = 40f
        const val MAX_HAMMERS = 8
        const val L = 70f // mallet handle length

        val COLS = floatArrayOf(100f, 240f, 380f)
        val ROWS = floatArrayOf(330f, 480f, 630f)
        val HX = FloatArray(9) { COLS[it % 3] }
        val HY = FloatArray(9) { ROWS[it / 3] }

        val IDLE = arrayOf("Missed me.", "We are legion.", "Too slow.", "Is that a hammer or a feather?", "Peekaboo. I hate you.")
        val BONK = arrayOf("OW.", "My face!", "Rude.", "I felt that.", "That one had a family.")
        val WHIFF = arrayOf("Swing and a miss.", "Ha!", "Not even close.")
        val ESCAPE = arrayOf("Too slow.", "Nyeh.", "Bye!")
        val SOB = arrayOf("It was a GIFT.", "I just wanted a hugâ€¦", "You monster.", "Why would youâ€¦")
        val END = arrayOf("Finally. A nap.", "My head hurts.", "Timeâ€™s up. Leave.")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val lookDark = look.dark.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    // one clone per hole at most: index = hole
    private val up = BooleanArray(9)
    private val age = FloatArray(9)
    private val end = FloatArray(9)
    private val hasHeart = BooleanArray(9)
    private val mood = arrayOfNulls<Mood>(9)
    private val bonked = FloatArray(9) { -1f } // seconds since it got bonked, -1 if not

    private val hmX = FloatArray(MAX_HAMMERS)
    private val hmY = FloatArray(MAX_HAMMERS)
    private val hmAge = FloatArray(MAX_HAMMERS) { 1f }
    private var hmNext = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var shake = 0f
    private var pollen = 0f
    private var talkX = W / 2f
    private var talkY = 260f
    private var left = TIME
    private var extra = 0f // seconds added by Continues, so the pace keeps rising
    private var t = 0f
    private var next = 0.6f // seconds until the next clone pops up
    private var chat = 4f
    private var lastTick = TIME.toInt()

    // later clones come faster and duck sooner, and more of them at once
    private fun played() = TIME + extra - left
    private fun interval() = clamp(0.8f - played() * 0.011f, 0.38f, 0.8f)
    private fun stay() = clamp(1.05f - played() * 0.013f, 0.5f, 1.05f)
    private fun most() = min(4, 1 + floor(played() / 14f).toInt())

    private fun rise(i: Int) = clamp(min(age[i] / RISE, (end[i] - age[i]) / RISE), 0f, 1f)
    private fun centerY(i: Int) = HY[i] + SIZE * 0.75f - rise(i) * TRAVEL

    // the clone in hole i says something (sometimes, if `chance` < 1)
    private fun say(i: Int, lines: Array<String>, chance: Float = 1f, sec: Float = 1.8f) {
        if (chance < 1f && (mouth.left > 0f || Random.nextFloat() >= chance)) return
        mouth.say(pick(lines), sec)
        talkX = HX[i]
        talkY = centerY(i) - SIZE * 0.62f
    }

    private fun popUp() {
        var free = 0
        var active = 0
        for (i in 0 until 9) {
            if (!up[i]) free++ else if (bonked[i] < 0f) active++
        }
        if (free == 0 || active >= most()) return
        var k = Random.nextInt(free)
        var hole = 0
        for (i in 0 until 9) {
            if (up[i]) continue
            if (k-- == 0) {
                hole = i
                break
            }
        }
        val heart = played() > 3f && Random.nextFloat() < 0.22f
        up[hole] = true
        age[hole] = 0f
        end[hole] = RISE * 2f + stay()
        hasHeart[hole] = heart
        mood[hole] = if (heart) Mood.Happy else if (Random.nextBoolean()) Mood.Angry else Mood.Smug
        bonked[hole] = -1f
    }

    private fun bonk(i: Int) {
        bonked[i] = 0f
        end[i] = age[i] + 0.35f + RISE
        val x = HX[i]
        val y = centerY(i)
        if (hasHeart[i]) {
            score = max(0, score - 3)
            ctx.sfx.play("sob")
            ctx.haptics.thud()
            fx.burst(x, y, HEART, 16, 220f)
            fx.ring(x, y - SIZE * 0.2f, col("#ff8fb0"), 70f, 0.4f)
            fx.float(x, y - SIZE * 0.6f, "-3", HEART)
            say(i, SOB, 1f, 2.2f)
        } else {
            score++
            ctx.sfx.play("hit")
            ctx.haptics.thud()
            fx.burst(x, y - SIZE * 0.3f, lookColor, 12, 300f)
            fx.burst(x, y - SIZE * 0.3f, col("#fff3a8"), 10, 380f, size = 2.5f, life = 0.4f)
            fx.ring(x, y - SIZE * 0.3f, col("#fff6c8"), 80f, 0.3f, 8f)
            shake = 0.12f
            fx.float(x, y - SIZE * 0.6f, "+1")
            say(i, BONK, 0.18f)
        }
    }

    private fun swing(x: Float, y: Float) {
        hmX[hmNext] = x
        hmY[hmNext] = y
        hmAge[hmNext] = 0f
        hmNext = (hmNext + 1) % MAX_HAMMERS
    }

    override fun down(x: Float, y: Float) {
        if (over) return
        swing(x, y)
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until 9) {
            if (!up[i] || bonked[i] >= 0f || rise(i) < 0.35f) continue
            val cy = centerY(i)
            if (abs(x - HX[i]) > HOLE_W || y < cy - SIZE * 0.65f || y > HY[i] + HOLE_H + 6f) continue
            val d = hypot(x - HX[i], y - cy)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best >= 0) {
            bonk(best)
        } else {
            ctx.sfx.play("click")
            if (mouth.left <= 0f && Random.nextFloat() < 0.15f) {
                for (i in 0 until 9) {
                    if (up[i] && bonked[i] < 0f) {
                        say(i, WHIFF)
                        break
                    }
                }
            }
        }
    }

    // a paid Continue: more time on the clock, same score, same pace
    override fun revive() {
        over = false
        extra += MORE_TIME
        left = MORE_TIME
        lastTick = ceil(left).toInt()
        next = 0.4f
        reviveFx(fx, mouth, ctx, W / 2f, 250f, lookColor)
        talkX = W / 2f
        talkY = 230f
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        shake = max(0f, shake - dt)
        pollen -= dt
        if (pollen <= 0f) {
            pollen = rand(0.2f, 0.5f)
            fx.mote(rand(0f, W), rand(240f, H), POLLEN, rand(-12f, 12f), rand(-18f, -6f), rand(1f, 2f), rand(2f, 4f))
        }
        for (k in 0 until MAX_HAMMERS) hmAge[k] += dt

        if (!over) {
            left -= dt
            // tick through the last five seconds
            val sec = ceil(left).toInt()
            if (sec != lastTick) {
                lastTick = sec
                if (sec in 1..5) {
                    ctx.sfx.play("tick")
                    ctx.haptics.tap()
                }
            }
            if (left <= 0f) {
                left = 0f
                over = true
                for (i in 0 until 9) if (up[i]) end[i] = min(end[i], age[i] + rise(i) * RISE)
                ctx.sfx.play(if (score >= 30) "win" else "bad")
                mouth.say(pick(END), 3f)
                talkX = W / 2f
                talkY = 230f
            } else {
                next -= dt
                if (next <= 0f) {
                    popUp()
                    next = interval()
                }
                chat -= dt
                if (chat <= 0f) {
                    for (i in 0 until 9) {
                        if (up[i] && bonked[i] < 0f && rise(i) > 0.8f) {
                            say(i, IDLE, 0.6f)
                            break
                        }
                    }
                    chat = rand(4f, 7f)
                }
            }
        }

        for (i in 0 until 9) {
            if (!up[i]) continue
            age[i] += dt
            if (bonked[i] >= 0f) bonked[i] += dt
            if (age[i] >= end[i]) {
                up[i] = false
                if (bonked[i] < 0f && !hasHeart[i] && !over) say(i, ESCAPE, 0.12f)
            }
        }
    }

    private var shownSec = -1
    private var shownText = ""

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        // the static garden, drifting clouds, slow sun rays
        c.sprite(art.garden, 0f, 0f, W, H)
        for (i in 0 until 3) {
            val x = ((i * 190f + t * (6f + i * 3f)) % (W + 240f)) - 220f
            val s = 0.9f - i * 0.15f
            c.sprite(art.puff, x, 40f + i * 52f, 220f * s, 110f * s, 0.7f - i * 0.12f)
        }
        c.rays(110f, HORIZON - 30f, 420f, RAYS, 0.16f, t * 0.03f)

        c.save()
        if (shake > 0f) c.translate(rand(-2f, 2f) * shake * 10f, rand(-2f, 2f) * shake * 10f)
        for (i in 0 until 9) {
            val hx = HX[i]
            val hy = HY[i]
            if (up[i]) {
                // only what sticks out above the front rim shows
                c.save()
                c.clipPath(art.holeClip[i])
                c.saveLayer(hx - BOX_X, hy - BOX_UP, hx + BOX_X, hy + BOX_DOWN, null)
                clone(c, i, hx, centerY(i))
                // it gets darker the deeper it sits in the hole
                c.drawRect(hx - BOX_X, hy - 40f, hx + BOX_X, hy + BOX_DOWN, art.depthFor(i))
                c.restore()
                c.restore()
            }
            // the front rim
            c.sprite(art.rim, hx - art.rim.w / 2f, hy - HOLE_H - 6f)
        }
        c.restore()

        for (k in 0 until MAX_HAMMERS) if (hmAge[k] < 0.3f) hammer(c, k)
        fx.draw(c)
        // the edges warm up, and pulse red in the last five seconds
        c.vignette(0.3f, WARM)
        if (!over && left < 5f) c.vignette(0.25f + 0.2f * abs(sin(t * 6f)), ALARM)
        mouth.draw(c, talkX, talkY)

        val sec = ceil(left).toInt()
        if (sec != shownSec) {
            shownSec = sec
            shownText = "${sec}s left"
        }
        c.drawScore(score, shownText)
        if (played() < 2.5f) Txt.draw(c, "Bonk them. Not the hearts.", W / 2f, 166f, 16f, HINT)
    }

    private fun DrawScope.clone(c: Canvas, i: Int, x: Float, y: Float) {
        val isBonked = bonked[i] >= 0f
        val squash = if (isBonked) 0.3f * max(0f, 1f - bonked[i] / 0.35f) else 0f
        if (hasHeart[i] && !isBonked) c.glow(x + SIZE * 0.44f, y - SIZE * 0.02f, 46f, HEART, 0.55f + sin(t * 8f) * 0.15f)
        bobby(
            c, x, y, SIZE, look,
            mood = if (isBonked) (if (hasHeart[i]) Mood.Sad else Mood.Closed) else (mood[i] ?: Mood.Angry),
            t = t + i, // so the clones don't all blink together
            squash = squash,
            flash = isBonked && bonked[i] < 0.08f,
            tears = isBonked && hasHeart[i],
        )
        if (hasHeart[i]) {
            // a stubby arm holding the heart out to you
            arm.color = lookDark
            c.drawLine(x + SIZE * 0.3f, y + SIZE * 0.1f, x + SIZE * 0.42f, y + SIZE * 0.02f, arm)
            val beat = 1f + sin(t * 8f) * 0.08f
            c.heart(x + SIZE * 0.44f, y - SIZE * 0.02f, 13f * beat, if (isBonked) HEART_DIM else HEART, WHITE)
            if (!isBonked) {
                c.save()
                c.rotate(-0.6f * DEG, x + SIZE * 0.4f, y - SIZE * 0.07f)
                oval.set(x + SIZE * 0.4f - 3f, y - SIZE * 0.07f - 2f, x + SIZE * 0.4f + 3f, y - SIZE * 0.07f + 2f)
                c.drawOval(oval, shine)
                c.restore()
            }
        } else if (isBonked) {
            // little glowing stars spinning round its head
            for (k in 0 until 3) {
                val a = t * 7f + k * TAU / 3f
                val sx = x + cos(a) * SIZE * 0.32f
                val sy = y - SIZE * 0.55f + sin(a) * 6f
                c.glow(sx, sy, 16f, STAR_GLOW, 0.8f)
                c.star(sx, sy, 5f, starFill, a)
            }
        }
    }

    // a quick swing down onto the tapped point, then it fades away
    private fun hammer(c: Canvas, k: Int) {
        val a0 = hmAge[k]
        val rest = -0.6f
        val a = if (a0 < 0.07f) 0.5f + (rest - 0.5f) * (a0 / 0.07f) else rest
        val fade = clamp((0.3f - a0) / 0.12f, 0f, 1f)
        c.save()
        c.translate(hmX[k] - L * sin(rest), hmY[k] + L * cos(rest))
        if (a0 < 0.12f) {
            // a pale smear along the arc of the swing
            smear.alpha = (fade * (1f - a0 / 0.12f) * 0.55f * 255f).toInt()
            oval.set(-L, -L, L, L)
            val start = (-PI_F / 2f + rest) * DEG
            val sweep = (max(rest, a) - rest + 0.01f) * DEG
            c.drawArc(oval, start, sweep, false, smear)
        }
        c.rotate(a * DEG)
        c.sprite(art.mallet, -40f, -100f, alpha = fade)
        c.restore()
    }

    /* ---------- art ---------- */

    private val HEART = col("#ff4f7b")
    private val HEART_DIM = col("#b0496a")
    private val POLLEN = col("#fff1b8")
    private val RAYS = col("#ffe6b0")
    private val WARM = col("#3a1c08")
    private val ALARM = col("#c0182a")
    private val STAR_GLOW = col("#ffd84d")
    private val HINT = rgba(255, 255, 255, 0.9f)
    private val arm = stroke(WHITE, 5f)
    private val shine = fill(rgba(255, 255, 255, 0.75f))
    private val starFill = fill(col("#fff3a0"))
    private val smear = stroke(WHITE, 26f)
    private val oval = RectF()
    private val art = Art()

    private class Art {
        // the whole static garden at golden hour: sky, sun, hills, lawn, holes
        val garden by lazy { makeSprite(W, H, 1.5f) { paintGarden(it) } }

        // per hole: everything above the hole's centre line plus the front half of its pit
        val holeClip = Array(9) { i ->
            val hx = HX[i]
            val hy = HY[i]
            Path().apply {
                addRect(hx - HOLE_W - 30f, hy - SIZE * 2f, hx + HOLE_W + 30f, hy, Path.Direction.CW)
                val half = Path()
                half.arcTo(RectF(hx - HOLE_W, hy - HOLE_H, hx + HOLE_W, hy + HOLE_H), 0f, 180f)
                half.close()
                op(half, Path.Op.UNION)
            }
        }

        // darkens a clone where it sinks into the hole (drawn SRC_ATOP inside its layer)
        val depth = fill().apply {
            xfermode = ATOP
        }

        private val depthShaders = Array(3) { r ->
            val y = ROWS[r]
            lin(0f, y - 40f, 0f, y + BOX_DOWN, 0f to rgba(20, 10, 5, 0f), 1f to rgba(20, 10, 5, 0.75f))
        }

        fun depthFor(hole: Int) = depth.apply { shader = depthShaders[hole / 3] }

        // the near lip of a hole, drawn over whatever is popping out of it
        val rim by lazy {
            makeSprite((HOLE_W + 14f) * 2f, HOLE_H * 2f + 30f) { c ->
                val cx = HOLE_W + 14f
                val cy = HOLE_H + 6f
                val p = stroke(WHITE, 8f).shade(lin(0f, cy, 0f, cy + HOLE_H + 8f, 0f to col("#c99566"), 1f to col("#6e4529")))
                c.drawArc(RectF(cx - HOLE_W - 2f, cy - HOLE_H - 2f, cx + HOLE_W + 2f, cy + HOLE_H + 2f), 0.08f * DEG, (PI_F - 0.16f) * DEG, false, p)
                c.drawArc(RectF(cx - HOLE_W - 1f, cy - 2f - HOLE_H, cx + HOLE_W + 1f, cy - 2f + HOLE_H), 0.3f * DEG, (PI_F - 0.6f) * DEG, false, stroke(rgba(255, 225, 180, 0.55f), 2f))
            }
        }

        // a soft drifting cloud
        val puff by lazy {
            makeSprite(220f, 110f) { c ->
                val p = fill().shade(lin(0f, 20f, 0f, 100f, 0f to rgba(255, 255, 255, 0.95f), 1f to rgba(255, 214, 190, 0.75f)))
                p.setShadowLayer(14f, 0f, 0f, rgba(255, 255, 255, 0.7f))
                val path = Path()
                for (b in arrayOf(floatArrayOf(60f, 70f, 26f), floatArrayOf(100f, 52f, 34f), floatArrayOf(146f, 64f, 28f), floatArrayOf(120f, 78f, 24f), floatArrayOf(80f, 82f, 20f))) {
                    path.addCircle(b[0], b[1], b[2], Path.Direction.CW)
                }
                c.drawPath(path, p)
            }
        }

        // a shaded mallet, swung with a motion smear
        val mallet by lazy {
            makeSprite(80f, 120f) { c ->
                c.translate(40f, 100f) // the grip end of the handle
                c.drawRoundRect(RectF(-4f, -L, 4f, 16f), 4f, 4f, fill().shade(lin(-5f, 0f, 5f, 0f, 0f to col("#7a4f25"), 0.4f to col("#d9a464"), 1f to col("#8a5a2c"))))
                val grip = fill(rgba(40, 20, 10, 0.5f))
                for (i in 0 until 3) c.drawRect(-4f, 4f + i * 4f, 4f, 5.5f + i * 4f, grip)
                c.drawRoundRect(RectF(-28f, -L - 14f, 28f, -L + 14f), 7f, 7f, fill().shade(lin(0f, -L - 14f, 0f, -L + 14f, 0f to col("#ff7a6a"), 0.45f to col("#d8433a"), 1f to col("#8e1f1a"))))
                c.drawRoundRect(RectF(-22f, -L - 11f, 18f, -L - 6f), 2.5f, 2.5f, fill(rgba(255, 255, 255, 0.45f)))
                val band = fill().shade(lin(0f, -L - 14f, 0f, -L + 14f, 0f to WHITE, 0.5f to col("#c9ccd6"), 1f to col("#6f7380")))
                c.drawRoundRect(RectF(-31f, -L - 15f, -23f, -L + 15f), 3f, 3f, band)
                c.drawRoundRect(RectF(23f, -L - 15f, 31f, -L + 15f), 3f, 3f, band)
            }
        }

        private fun paintGarden(c: Canvas) {
            // sky
            c.drawRect(0f, 0f, W, HORIZON + 40f, fill().shade(lin(0f, 0f, 0f, HORIZON + 20f, 0f to col("#2d6fc4"), 0.55f to col("#79b9ea"), 1f to col("#ffd9a6"))))
            // low sun behind the hills
            c.drawRect(
                0f, 0f, W, HORIZON + 40f,
                fill().shade(rad(110f, HORIZON - 30f, 0f, 260f, 0f to rgba(255, 246, 214, 1f), 0.12f to rgba(255, 226, 160, 0.8f), 0.5f to rgba(255, 190, 120, 0.25f), 1f to rgba(255, 190, 120, 0f))),
            )

            // three hill layers, hazier the further away
            val cols = intArrayOf(col("#a9c9b6"), col("#86b78c"), col("#6aa865"))
            val bases = floatArrayOf(HORIZON - 40f, HORIZON - 14f, HORIZON + 8f)
            val fs = floatArrayOf(0.011f, 0.017f, 0.024f)
            val amps = floatArrayOf(26f, 22f, 14f)
            val phs = floatArrayOf(1.3f, 4.1f, 2.2f)
            for (i in 0 until 3) {
                val base = bases[i]
                val f = fs[i]
                val amp = amps[i]
                val ph = phs[i]
                fun yAt(x: Float) = base - sin(x * f + ph) * amp - sin(x * f * 2.7f + ph) * amp * 0.3f
                val hill = Path()
                hill.moveTo(0f, HORIZON + 60f)
                var x = 0f
                while (x <= W) {
                    hill.lineTo(x, yAt(x))
                    x += 8f
                }
                hill.lineTo(W, HORIZON + 60f)
                hill.close()
                c.drawPath(hill, fill(cols[i]))
                if (i == 1) {
                    // a row of round trees on the middle hill
                    for (k in 0 until 9) {
                        val tx = 20f + k * 55f + hash(k.toFloat()) * 20f
                        val ty = yAt(tx)
                        val r = 8f + hash(k + 3f) * 6f
                        c.drawRect(tx - 1.5f, ty - r, tx + 1.5f, ty + 4f, fill(col("#5a6b48")))
                        val crown = Path()
                        crown.addCircle(tx, ty - r * 1.7f, r, Path.Direction.CW)
                        crown.addCircle(tx - r * 0.6f, ty - r * 1.1f, r * 0.75f, Path.Direction.CW)
                        crown.addCircle(tx + r * 0.6f, ty - r * 1.15f, r * 0.7f, Path.Direction.CW)
                        c.drawPath(crown, fill().shade(rad(tx - r * 0.25f, ty - r * 1.85f, 1f, r * 1.8f, 0f to col("#a8d08e"), 1f to col("#5d9461"))))
                    }
                }
                // sun haze over each far layer
                c.save()
                c.clipPath(hill)
                c.drawRect(0f, base - amp * 2f, W, base - amp * 2f + 80f, fill().shade(lin(0f, base - amp * 1.5f, 0f, base + 40f, 0f to rgba(255, 224, 170, 0.35f - i * 0.12f), 1f to rgba(255, 224, 170, 0f))))
                c.restore()
            }

            // the lawn
            val top = HORIZON + 6f
            val lawn = Path()
            lawn.moveTo(0f, top + 6f)
            lawn.quadTo(W / 2f, top - 8f, W, top + 6f)
            lawn.lineTo(W, H)
            lawn.lineTo(0f, H)
            lawn.close()
            c.drawPath(lawn, fill().shade(lin(0f, top, 0f, H, 0f to col("#8ccf62"), 0.4f to col("#5fae45"), 1f to col("#2f7a32"))))
            c.save()
            c.clipPath(lawn)
            // mowing stripes, widening towards the viewer
            val stripe = fill(rgba(255, 255, 220, 0.07f))
            for (i in 0 until 9 step 2) {
                val a = (i - 4.5f) * 34f
                val b = (i - 3.5f) * 34f
                val p = Path()
                p.moveTo(W / 2f + a, top)
                p.lineTo(W / 2f + b, top)
                p.lineTo(W / 2f + b * 4.2f, H)
                p.lineTo(W / 2f + a * 4.2f, H)
                p.close()
                c.drawPath(p, stripe)
            }
            // grass blades
            val blade = stroke(WHITE, 1f)
            for (i in 0 until 520) {
                val fi = i.toFloat()
                val x = hash(fi) * W
                val y = top + hash(fi + 900f).pow(0.8f) * (H - top)
                val s = 0.5f + (y - top) / (H - top)
                blade.color = if (hash(fi + 50f) > 0.5f) rgba(30, 90, 30, 0.35f) else rgba(200, 240, 150, 0.3f)
                blade.strokeWidth = 1.2f * s
                c.drawLine(x, y, x + (hash(fi + 7f) - 0.5f) * 5f * s, y - (4f + hash(fi + 3f) * 6f) * s, blade)
            }
            // warm light falling across from the sun side
            c.drawRect(0f, top - 10f, W, H, fill().shade(lin(0f, top, W, H, 0f to rgba(255, 220, 140, 0.22f), 0.6f to rgba(255, 220, 140, 0f))))
            c.restore()
            // horizon haze line
            c.drawRect(0f, top - 14f, W, top + 30f, fill().shade(lin(0f, top - 14f, 0f, top + 30f, 0f to rgba(255, 230, 180, 0f), 0.5f to rgba(255, 230, 180, 0.35f), 1f to rgba(255, 230, 180, 0f))))

            // the holes: a dirt mound and a deep dark pit
            val r = RectF()
            for (h in 0 until 9) {
                val hx = HX[h]
                val hy = HY[h]
                r.set(hx - HOLE_W - 30f, hy + 12f - HOLE_H - 22f, hx + HOLE_W + 30f, hy + 12f + HOLE_H + 22f)
                c.drawOval(r, fill().shade(rad(hx, hy + 14f, 10f, HOLE_W + 30f, 0f to rgba(10, 40, 10, 0.45f), 1f to rgba(10, 40, 10, 0f))))
                r.set(hx - HOLE_W - 9f, hy + 4f - HOLE_H - 9f, hx + HOLE_W + 9f, hy + 4f + HOLE_H + 9f)
                c.drawOval(r, fill().shade(lin(0f, hy - HOLE_H - 6f, 0f, hy + HOLE_H + 14f, 0f to col("#b98457"), 0.5f to col("#8e5d3b"), 1f to col("#5b3a24"))))
                // crumbs of dirt on the mound
                val crumb = fill()
                for (i in 0 until 14) {
                    val a = hash(i + hx) * TAU
                    crumb.color = if (hash(i + hy) > 0.5f) rgba(60, 35, 20, 0.5f) else rgba(220, 170, 120, 0.45f)
                    c.drawCircle(hx + cos(a) * (HOLE_W + 4f), hy + 4f + sin(a) * (HOLE_H + 4f), 1.5f + hash(i.toFloat()) * 1.5f, crumb)
                }
                r.set(hx - HOLE_W, hy - HOLE_H, hx + HOLE_W, hy + HOLE_H)
                c.drawOval(r, fill().shade(rad(hx, hy + 3f, 2f, HOLE_W, 0f to col("#050302"), 0.7f to col("#1c120b"), 1f to col("#3a2617"))))
                // the far inner wall catches a bit of light
                r.set(hx - HOLE_W + 3f, hy + 2f - HOLE_H + 3f, hx + HOLE_W - 3f, hy + 2f + HOLE_H - 3f)
                c.drawArc(r, (PI_F + 0.25f) * DEG, (PI_F - 0.5f) * DEG, false, stroke(rgba(160, 110, 70, 0.5f), 3f))
            }
        }
    }
}
