package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.avatar.drawButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Run Away: the button grew legs and sprints right, away from the cursor.
// Tap to jump (hold for higher), swipe down to duck.
// Port of src/adventure/games/run.js.
class RunGame(private val ctx: GameContext) : Game {
    private companion object {
        val GROUND = (H * 0.66f).roundToInt().toFloat()
        const val GRAVITY = 2600f
        const val JUMP = -820f
        const val HOLD = 0.24f // how long a held jump keeps floating up
        const val SIZE = 58f
        const val FOOT = SIZE * (20f / 38f) // center of the button to the soles of its feet
        const val TALL = 56f // standing hitbox height; ducking shrinks it
        const val ME_X = 170f
        const val PER_METER = 36f // pixels run per meter of score
        const val START_SPEED = 320f
        const val TOP_SPEED = 640f
        val AD_BOTTOM = GROUND - 44f // ads float just low enough to need a duck
        const val MAX_THINGS = 24
        const val MAX_COINS = 48

        const val BOX = 0
        const val STAPLER = 1
        const val BOXES = 2
        const val AD = 3
        const val GAP = 4

        val RUNS = arrayOf("Leave me alone!", "Stop chasing me.", "Faster, legs!", "I can hear your finger.", "Not today, cursor.")
        val JUMPS = arrayOf("Hup.", "Junk everywhere.", "Who leaves a stapler here?")
        val DUCKS = arrayOf("Not clicking that.", "No thank you.", "Ads. Ugh.")
        val COINS = arrayOf("Mine.", "Running money.", "Not sharing.")
        val CRASH = arrayOf("OW.", "Do not click me.", "Caught. Great.")
        val ADS = arrayOf("YOU WON!", "FREE RAM", "CLICK ME", "1 VIRUS!", "HOT DEALS")
        val CURSOR = floatArrayOf(0f, 0f, 0f, 17f, 4f, 13f, 7f, 20f, 9.5f, 19f, 6.5f, 12f, 12f, 12f)
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private var meY = GROUND - FOOT
    private var vy = 0f
    private var ground = true
    private var angle = 0f

    // obstacles (junk, ads and gaps) in screen x, oldest first
    private val tKind = IntArray(MAX_THINGS)
    private val tx = FloatArray(MAX_THINGS)
    private val tw = FloatArray(MAX_THINGS)
    private val th = FloatArray(MAX_THINGS)
    private val tLabel = IntArray(MAX_THINGS)
    private val tDucked = BooleanArray(MAX_THINGS)
    private var things = 0

    private val kx = FloatArray(MAX_COINS)
    private val ky = FloatArray(MAX_COINS)
    private val kGot = BooleanArray(MAX_COINS)
    private var nCoins = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var dust = 0f
    // a finger press: wait a moment to tell a tap (jump) from a swipe down (duck)
    private var pressing = false
    private var pressY = 0f
    private var pressWait = 0f
    private var pressJumped = false
    private var pressSwiped = false
    private var speed = START_SPEED
    private var ran = 0f // pixels run so far
    private var bonus = 0
    private var scroll = 0f
    private var ahead = 520f // pixels until the next obstacle
    private var coinAhead = 900f
    private var hold = 0f
    private var buffer = 0f // a jump pressed just before landing
    private var coyote = 0f // a jump pressed just after running off an edge
    private var duckLeft = 0f
    private var duck = 0f // 0 standing .. 0.4 fully squashed
    private var squash = 0f
    private var cursorX = 80f
    private var chatter = 3f
    private var t = 0f
    private var safe = 0f // can't crash for a moment after a Continue

    private fun meters() = floor(ran / PER_METER).toInt()

    private fun addThing() {
        val m = meters()
        // the web picks from a list that grows: box, stapler, (ad, ad), (boxes), (gap, gap)
        var n = 2
        if (m > 60) n += 2
        if (m > 150) n += 1
        if (m > 250) n += 2
        val r = Random.nextInt(n)
        val kind = when {
            r == 0 -> BOX
            r == 1 -> STAPLER
            m > 60 && r < 4 -> AD
            m > 150 && r == 4 -> BOXES
            else -> GAP
        }
        if (things >= MAX_THINGS) removeThing(0)
        val i = things++
        tKind[i] = kind
        tx[i] = W + 40f
        tDucked[i] = false
        tLabel[i] = Random.nextInt(ADS.size)
        when (kind) {
            BOX -> { tw[i] = 44f; th[i] = 42f }
            STAPLER -> { tw[i] = 64f; th[i] = 30f }
            BOXES -> { tw[i] = 46f; th[i] = 80f }
            AD -> { tw[i] = 130f; th[i] = 80f }
            else -> { tw[i] = rand(90f, 110f + min(40f, m * 0.05f)); th[i] = 0f }
        }
        ahead = tw[i] + rand(speed * 0.95f, speed * 1.7f) + 120f
    }

    private fun removeThing(i: Int) {
        for (j in i until things - 1) {
            tKind[j] = tKind[j + 1]
            tx[j] = tx[j + 1]
            tw[j] = tw[j + 1]
            th[j] = th[j + 1]
            tLabel[j] = tLabel[j + 1]
            tDucked[j] = tDucked[j + 1]
        }
        things--
    }

    private fun addCoins() {
        val n = if (Random.nextBoolean()) 1 else 3
        val y = when (Random.nextInt(3)) {
            0 -> GROUND - 40f
            1 -> GROUND - 120f
            else -> GROUND - 170f
        }
        for (i in 0 until n) {
            if (nCoins >= MAX_COINS) removeCoin(0)
            kx[nCoins] = W + 30f + i * 42f
            ky[nCoins] = y
            kGot[nCoins] = false
            nCoins++
        }
        coinAhead = rand(speed * 2.2f, speed * 4f)
    }

    private fun removeCoin(i: Int) {
        for (j in i until nCoins - 1) {
            kx[j] = kx[j + 1]
            ky[j] = ky[j + 1]
            kGot[j] = kGot[j + 1]
        }
        nCoins--
    }

    // A gap the button's feet are entirely over.
    private fun overGap(): Boolean {
        for (i in 0 until things) if (tKind[i] == GAP && ME_X - 8f > tx[i] && ME_X + 8f < tx[i] + tw[i]) return true
        return false
    }

    private fun jumpNow() {
        if (over) return
        if (!ground && coyote <= 0f) {
            buffer = 0.12f
            return
        }
        vy = JUMP
        ground = false
        hold = HOLD
        coyote = 0f
        buffer = 0f
        squash = 0.2f
        for (i in 0 until 6) fx.mote(ME_X + rand(-14f, 14f), GROUND - 2f, DIRT, rand(-120f, 40f), rand(-60f, -10f), rand(3f, 5f), 0.45f, add = false, drag = 3f)
        ctx.sfx.play("jump")
        mouth.maybe(JUMPS, 0.06f)
    }

    private fun crash(fell: Boolean) {
        if (over) return
        over = true
        ctx.sfx.play("hit")
        ctx.haptics.thud()
        fx.burst(ME_X, meY, lookColor, 22, 320f)
        fx.burst(ME_X, meY, WHITE, 10, 260f, size = 2f)
        fx.ring(ME_X, meY, col("#ffd0c0"), 90f, 0.4f)
        mouth.say(if (fell) "AAAAH—" else pick(CRASH), 3f)
        if (!fell) {
            vy = -480f
            ground = false
        }
    }

    override fun down(x: Float, y: Float) {
        pressing = true
        pressY = y
        pressWait = 0.08f
        pressJumped = false
        pressSwiped = false
    }

    override fun move(x: Float, y: Float) {
        if (pressing && !pressSwiped && y - pressY > 36f) {
            pressSwiped = true
            duckLeft = 0.45f
            hold = 0f
        }
    }

    override fun up(x: Float, y: Float) {
        if (pressing && !pressJumped && !pressSwiped) jumpNow()
        pressing = false
    }

    // a paid Continue: clear what's near, back on its feet, a moment of safety
    override fun revive() {
        over = false
        for (i in things - 1 downTo 0) if (tx[i] < ME_X + 380f) removeThing(i)
        ahead = max(ahead, min(TOP_SPEED, START_SPEED + t * 4f) * 1.2f)
        meY = GROUND - FOOT
        vy = 0f
        ground = true
        angle = 0f
        pressing = false
        safe = 2f
        cursorX = 40f
        reviveFx(fx, mouth, ctx, ME_X, meY, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        // kicked-up dust while running
        dust -= dt
        if (ground && !over && dust <= 0f) {
            dust = 0.07f
            fx.mote(ME_X - 10f, GROUND - 3f, DUST, -speed * 0.5f + rand(-20f, 20f), rand(-40f, -10f), rand(2f, 3.5f), 0.35f, add = false)
        }
        squash = max(0f, squash - dt)
        safe -= dt
        buffer -= dt
        coyote -= dt
        duckLeft -= dt
        hold -= dt

        if (pressing && !pressJumped && !pressSwiped) {
            pressWait -= dt
            if (pressWait <= 0f) {
                pressJumped = true
                jumpNow()
            }
        }

        val ducking = !over && ((pressing && pressSwiped) || duckLeft > 0f)
        val holding = pressing && pressJumped && !pressSwiped
        duck += ((if (ducking) 0.4f else 0f) - duck) * min(1f, dt * 18f)

        // a held jump floats; ducking in the air slams it back down
        var grav = GRAVITY
        if (ducking && !ground) {
            grav *= 1.9f
            hold = 0f
        } else if (holding && hold > 0f && vy < 0f) {
            grav *= 0.35f
        }

        if (over) {
            speed = max(0f, speed - 900f * dt)
            angle += (if (ground) 0f else -4f) * dt
        } else {
            speed = min(TOP_SPEED, min(START_SPEED + t * 4f, speed + 300f * dt)) // eases back up after a Continue
        }

        // run off the edge of a gap
        if (ground && overGap()) {
            ground = false
            coyote = 0.08f
        }
        if (!ground) {
            val before = meY + FOOT
            vy += grav * dt
            meY += vy * dt
            val feet = meY + FOOT
            if (feet >= GROUND && before <= GROUND + 2f && !overGap()) {
                meY = GROUND - FOOT
                vy = 0f
                ground = true
                squash = 0.18f
                for (i in 0 until 7) fx.mote(ME_X + rand(-18f, 18f), GROUND - 2f, DIRT, rand(-140f, 80f), rand(-50f, -10f), rand(3f, 6f), 0.5f, add = false, drag = 3f)
                if (over) angle = 0f
                if (buffer > 0f) jumpNow()
            } else if (feet > GROUND + 10f && !over && safe <= 0f) {
                crash(true)
            }
            // never fall forever (or turn into NaN)
            if (meY.isNaN() || meY > H + 400f) {
                meY = H + 400f
                vy = 0f
            }
        }

        // the cursor creeps closer, and pounces once it is over
        val want = if (over) ME_X - SIZE * 0.3f else 80f + sin(t * 2.2f) * 10f
        cursorX += (want - cursorX) * min(1f, dt * (if (over) 5f else 2f))

        val move = speed * dt
        scroll += move
        for (i in 0 until things) tx[i] -= move + (if (tKind[i] == AD && speed > 0f) 50f * dt else 0f)
        for (i in 0 until nCoins) kx[i] -= move
        if (over) return

        ran += move
        ahead -= move
        coinAhead -= move
        if (ahead <= 0f) addThing()
        if (coinAhead <= 0f) addCoins()
        while (things > 0 && tx[0] + tw[0] < -60f) removeThing(0)
        while (nCoins > 0 && kx[0] < -30f) removeCoin(0)

        // a forgiving box around the button; ducking makes it shorter
        val feet = meY + FOOT
        val bl = ME_X - SIZE * 0.3f
        val br = ME_X + SIZE * 0.3f
        val bt = feet - TALL + duck * 55f
        val bb = feet - 2f
        for (i in 0 until things) {
            val kind = tKind[i]
            if (kind == GAP || tx[i] + 4f > br || tx[i] + tw[i] - 4f < bl) continue
            val top = if (kind == AD) AD_BOTTOM - th[i] else GROUND - th[i]
            val bottom = if (kind == AD) AD_BOTTOM else GROUND
            if (bb > top + 4f && bt < bottom - 2f && safe <= 0f) {
                crash(false)
                return
            }
            if (kind == AD && ducking && !tDucked[i] && tx[i] < ME_X) {
                tDucked[i] = true
                mouth.maybe(DUCKS, 0.3f)
            }
        }

        for (i in 0 until nCoins) {
            if (kGot[i] || dist(ME_X, meY + duck * FOOT, kx[i], ky[i]) > SIZE * 0.5f + 12f) continue
            kGot[i] = true
            bonus += 10
            ctx.sfx.play("pop")
            ctx.haptics.tap()
            fx.burst(kx[i], ky[i], COIN_GLOW, 12, 220f, gravity = 200f)
            fx.ring(kx[i], ky[i], col("#ffe9a0"), 34f, 0.3f, 4f)
            fx.float(kx[i], ky[i] - 20f, "+10", col("#ffe27a"))
            mouth.maybe(COINS, 0.1f)
        }

        chatter -= dt
        if (chatter <= 0f) {
            chatter = rand(5f, 9f)
            mouth.maybe(RUNS, 0.6f)
        }

        score = meters() + bonus
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        scenery(c)
        for (i in 0 until things) if (tKind[i] == GAP) pit(c, i)

        for (i in 0 until nCoins) if (!kGot[i]) coin(c, i)
        for (i in 0 until things) {
            val kind = tKind[i]
            if (kind == GAP) continue
            val x = tx[i]
            c.shadow(x + tw[i] / 2f, GROUND + 1f, tw[i] * 0.65f, if (kind == AD) 0.15f else 0.4f)
            when (kind) {
                BOX -> c.sprite(art.box, x, GROUND - th[i])
                BOXES -> {
                    c.sprite(art.boxLow, x, GROUND - 40f)
                    c.sprite(art.boxTop, x + 3f, GROUND - 80f)
                }
                STAPLER -> c.sprite(art.stapler, x, GROUND - th[i] - 4f)
                AD -> ad(c, i)
            }
        }

        // a shadow on the ground that shrinks as it jumps
        val lift = GROUND - (meY + FOOT)
        if (lift > 2f && !overGap()) {
            c.shadow(ME_X, GROUND + 2f, SIZE * 0.5f * clamp(1f - lift / 300f, 0.3f, 1f), 0.35f * clamp(1f - lift / 300f, 0.2f, 1f))
        }
        speedLines(c, (speed - START_SPEED) / (TOP_SPEED - START_SPEED))

        // squash down for a duck, keeping the feet on the floor
        val sq = max(duck, squash)
        runner(
            c, ME_X, meY + FOOT * sq, SIZE, look,
            mood = when {
                over -> Mood.Sad
                duck > 0.2f -> Mood.Worried
                !ground -> Mood.Scared
                else -> Mood.Angry
            },
            squash = sq,
            legs = true,
            alpha = blinkAlpha(safe, t),
        )

        cursor(c, cursorX, GROUND - 70f + sin(t * 5f) * 6f)

        fx.draw(c)
        c.vignette(0.3f, VIG)
        mouth.draw(c, ME_X + 20f, meY - SIZE * 0.6f)

        c.drawScore(score, if (t < 4f) "Tap to jump · swipe down to duck" else "meters")
    }

    // The button with its legs out, like bobby() but through drawButton directly.
    private fun DrawScope.runner(c: Canvas, x: Float, y: Float, size: Float, look: Look, mood: Mood, squash: Float, legs: Boolean, alpha: Float) {
        if (x.isNaN() || y.isNaN() || alpha <= 0f) return
        if (alpha < 0.995f) {
            val m = size * 1.6f
            c.saveLayerAlpha(x - m, y - m, x + m, y + m, (alpha * 255f).toInt())
        } else {
            c.save()
        }
        if (angle != 0f && !angle.isNaN()) c.rotate(angle * DEG, x, y)
        if (face.mood != mood) face = Face(mood = mood)
        drawButton(Offset(x, y), size, look, face, t = t, squash = clamp(squash, -0.5f, 0.5f), legs = legs, tears = over, facing = 0.4f)
        c.restore()
    }

    private var face = Face()

    /* ---------- drawing ---------- */

    private fun scenery(c: Canvas) {
        c.sprite(art.sky, 0f, 0f)
        c.rays(W - 120f, GROUND - 170f, 380f, RAYS, 0.2f, t * 0.03f)
        for (i in 0 until 4) c.sprite(art.puff, wrap(i * 160f + 40f - scroll * 0.1f, 200f), 140f + (i % 3) * 64f, alpha = 0.85f)
        c.scrollTile(art.mountains, scroll * 0.06f, GROUND - 250f)
        c.scrollTile(art.hills, scroll * 0.25f, GROUND - 150f)
        // haze where the far land meets the ground
        c.sprite(art.haze, 0f, GROUND - 90f, W, 90f)
        c.scrollTile(art.bushes, scroll * 0.6f, GROUND - 80f)
        c.scrollTile(art.ground, scroll, GROUND)
    }

    // Wraps a scrolling x so things loop around the screen.
    private fun wrap(x: Float, pad: Float): Float {
        val span = W + pad * 2f
        return (((x + pad) % span) + span) % span - pad
    }

    private fun pit(c: Canvas, i: Int) {
        val x = tx[i]
        val w = tw[i]
        c.sprite(art.pit, x, GROUND, w, H - GROUND)
        // the walls: lit edge on the far side, shadow on the near
        c.drawRect(x + w - 6f, GROUND, x + w, H, pitLit)
        c.drawRect(x, GROUND, x + 8f, H, pitDark)
        c.drawRect(x - 3f, GROUND, x, GROUND + 14f, pitGrass)
        c.drawRect(x + w, GROUND, x + w + 3f, GROUND + 14f, pitGrass)
    }

    // A popup ad window flying in at duck height, glossy and glowing.
    private fun ad(c: Canvas, i: Int) {
        val x = tx[i]
        val y = AD_BOTTOM - th[i]
        val blink = sin(t * 10f) > 0f
        c.glow(x + tw[i] / 2f, y + th[i] / 2f, tw[i] * 0.85f, if (blink) AD_RED else AD_AMBER, 0.35f)
        c.sprite(art.ad(tLabel[i], blink), x - 2f, y - 2f)
    }

    private fun coin(c: Canvas, i: Int) {
        val x = kx[i]
        val y = ky[i]
        val squeeze = abs(cos(t * 4f + x * 0.02f))
        c.glow(x, y, 30f, COIN_GLOW2, 0.45f)
        val hw = 11f * squeeze + 2f
        c.sprite(art.coin, x - hw, y - 12f, hw * 2f, 24f)
        // a glint sweeping round
        val ph = (t * 1.3f + x * 0.003f) % 1f
        if (ph in 0f..0.25f) c.glow(x - 5f * squeeze, y - 6f, 10f, WHITE, 1f - ph * 4f)
    }

    // The giant mouse pointer chasing the button, tip at (x, y), pointing right.
    private fun cursor(c: Canvas, x: Float, y: Float) {
        c.glow(x - 30f, y - 10f, 120f, CURSOR_GLOW, 0.18f + sin(t * 4f) * 0.06f)
        c.save()
        c.translate(x, y)
        c.rotate(2f * DEG)
        c.scale(6f, 6f)
        c.save()
        c.translate(1.2f, -1.4f)
        c.drawPath(art.cursorPath, cursorShadow)
        c.restore()
        c.drawPath(art.cursorPath, art.cursorFill)
        c.drawPath(art.cursorPath, cursorLine)
        c.restore()
    }

    // Streaks of wind once the run gets fast.
    private fun speedLines(c: Canvas, a: Float) {
        if (!(a > 0f)) return
        for (i in 0 until 8) {
            val fi = i.toFloat()
            val ph = (t * (1.6f + hash(fi) * 0.8f) + hash(fi + 3f)) % 1f
            val x = W - ph * (W + 200f)
            val y = 140f + hash(fi + 9f) * (GROUND - 160f)
            wind.alpha = (clamp(a * 0.35f * sin(ph * PI.toFloat()), 0f, 1f) * 255f).roundToInt()
            wind.strokeWidth = 1.5f + hash(fi + 1f) * 1.5f
            c.drawLine(x, y, x + 60f + hash(fi + 2f) * 80f, y, wind)
        }
    }

    /* ---------- art ---------- */

    private val DIRT = col("#f3d2a2")
    private val DUST = col("#e9c495")
    private val COIN_GLOW = col("#ffd84d")
    private val COIN_GLOW2 = col("#ffc23a")
    private val RAYS = col("#ffd9a0")
    private val VIG = col("#2a1030")
    private val AD_RED = col("#ff5a4a")
    private val AD_AMBER = col("#ffb340")
    private val CURSOR_GLOW = col("#ff4060")
    private val pitLit = fill(rgba(176, 122, 72, 0.6f))
    private val pitDark = fill(rgba(0, 0, 0, 0.5f))
    private val pitGrass = fill(col("#6fb04a"))
    private val cursorShadow = fill(rgba(20, 10, 40, 0.3f))
    private val cursorLine = stroke(col("#1b1b22"), 0.9f)
    private val wind = stroke(WHITE, 2f)
    private val art = Art()

    private class Art {
        val sky by lazy {
            makeSprite(W, GROUND + 20f, 1.5f) { c ->
                c.drawRect(0f, 0f, W, GROUND + 20f, fill().shade(lin(0f, 0f, 0f, GROUND, 0f to col("#24357e"), 0.35f to col("#6a5cb4"), 0.62f to col("#e77e9b"), 0.85f to col("#ffbf7a"), 1f to col("#ffe2a6"))))
                c.drawRect(
                    0f, 0f, W, GROUND + 20f,
                    fill().shade(
                        rad(
                            W - 120f, GROUND - 170f, 0f, 300f,
                            0f to rgba(255, 250, 225, 1f), 0.1f to rgba(255, 236, 180, 0.95f), 0.14f to rgba(255, 200, 140, 0.55f),
                            0.45f to rgba(255, 150, 120, 0.22f), 1f to rgba(255, 150, 120, 0f),
                        ),
                    ),
                )
                val star = fill(WHITE)
                for (i in 0 until 40) {
                    val fi = i.toFloat()
                    star.color = rgba(255, 255, 255, 0.15f + hash(fi) * 0.4f)
                    val sx = hash(fi + 1f) * W
                    val sy = hash(fi + 2f) * 160f
                    c.drawRect(sx, sy, sx + 1.5f, sy + 1.5f, star)
                }
            }
        }

        private fun mtnY(x: Float) = 120f - abs(sin((x / W) * PI.toFloat() * 3f)) * 70f - sin((x / W) * PI.toFloat() * 8f) * 12f

        // Far mountains, then rolling hills with trees, then near bushes: each tileable.
        val mountains by lazy {
            makeSprite(W, 260f) { c ->
                val p = Path()
                p.moveTo(0f, 260f)
                var x = 0f
                while (x <= W) {
                    p.lineTo(x, mtnY(x))
                    x += 6f
                }
                p.lineTo(W, 260f)
                p.close()
                c.drawPath(p, fill().shade(lin(0f, 40f, 0f, 260f, 0f to col("#9b78b8"), 1f to col("#f3a98e"))))
                // snowy sunlit ridges
                c.save()
                c.clipPath(p)
                val ridge = Path()
                x = 0f
                while (x <= W) {
                    if (x == 0f) ridge.moveTo(x, mtnY(x) + 1f) else ridge.lineTo(x, mtnY(x) + 1f)
                    x += 6f
                }
                c.drawPath(ridge, stroke(rgba(255, 230, 210, 0.5f), 2f))
                c.restore()
            }
        }

        val hills by lazy {
            fun f(x: Float) = 90f - sin((x / W) * PI.toFloat() * 4f) * 26f - sin((x / W) * PI.toFloat() * 10f + 1f) * 8f
            makeSprite(W, 200f) { c ->
                val tree = fill(col("#5f7a74"))
                for (k in 0 until 10) {
                    // poplar trees standing on the hills
                    val fk = k.toFloat()
                    val x = (fk + 0.3f + hash(fk) * 0.5f) * (W / 10f)
                    val y = f(x)
                    val h = 26f + hash(fk + 5f) * 20f
                    val rx = 6f + hash(fk + 2f) * 3f
                    c.drawOval(RectF(x - rx, y - h / 2f - (h / 2f + 4f), x + rx, y - h / 2f + (h / 2f + 4f)), tree)
                }
                val p = Path()
                p.moveTo(0f, 200f)
                var x = 0f
                while (x <= W) {
                    p.lineTo(x, f(x))
                    x += 6f
                }
                p.lineTo(W, 200f)
                p.close()
                c.drawPath(p, fill().shade(lin(0f, 50f, 0f, 200f, 0f to col("#6f9a68"), 1f to col("#3d6a48"))))
                c.save()
                c.clipPath(p)
                // warm haze along the top edge
                c.drawRect(0f, 0f, W, 200f, fill().shade(lin(0f, 50f, 0f, 130f, 0f to rgba(255, 190, 140, 0.3f), 1f to rgba(255, 190, 140, 0f))))
                c.restore()
            }
        }

        val bushes by lazy {
            makeSprite(W, 80f) { c ->
                val p = Path()
                val oval = RectF()
                fun dome(x: Float, r: Float) {
                    oval.set(x - r, 80f - r, x + r, 80f + r)
                    p.addArc(oval, 180f, 180f)
                }
                for (i in 0 until 8) {
                    val fi = i.toFloat()
                    val x = fi * 60f + hash(fi) * 20f
                    for ((dx, r) in arrayOf(0f to 26f, 26f to 20f, -20f to 16f)) dome(x + dx, r * (0.8f + hash(fi + dx) * 0.4f))
                }
                // repeat across the seam
                dome(-30f, 26f)
                dome(W + 30f, 26f)
                c.drawPath(p, fill().shade(lin(0f, 40f, 0f, 80f, 0f to col("#5c9a4a"), 1f to col("#2f5f34"))))
                c.save()
                c.clipPath(p)
                c.drawRect(0f, 0f, W, 80f, fill().shade(lin(0f, 40f, 0f, 66f, 0f to rgba(255, 200, 130, 0.55f), 1f to rgba(255, 200, 130, 0f))))
                c.restore()
            }
        }

        // The ground: grass lip, layered soil, pebbles; tileable.
        val ground by lazy {
            val h = H - GROUND
            makeSprite(W, h) { c ->
                c.drawRect(0f, 0f, W, h, fill().shade(lin(0f, 0f, 0f, h, 0f to col("#b07a48"), 0.3f to col("#8d5d36"), 1f to col("#2e1c11"))))
                // strata
                for (i in 0 until 4) {
                    val p = Path()
                    val y0 = 50f + i * 55f
                    p.moveTo(0f, y0)
                    var x = 0f
                    while (x <= W) {
                        p.lineTo(x, y0 + sin((x / W) * PI.toFloat() * 2f * (i + 2)) * 6f)
                        x += 12f
                    }
                    p.lineTo(W, y0 + 14f)
                    p.lineTo(0f, y0 + 14f)
                    p.close()
                    c.drawPath(p, fill(rgba(0, 0, 0, 0.06f + i * 0.02f)))
                }
                // pebbles
                val oval = RectF()
                for (i in 0 until 70) {
                    val fi = i.toFloat()
                    val x = hash(fi) * W
                    val y = 26f + hash(fi + 70f) * (h - 30f)
                    val r = 2f + hash(fi + 140f) * 4f
                    oval.set(x - r * 1.3f, y - r, x + r * 1.3f, y + r)
                    c.drawOval(oval, fill().shade(lin(0f, y - r, 0f, y + r, 0f to col("#d6b48c"), 1f to col("#6e4a2c"))))
                }
                // grass lip
                val lip = Path()
                lip.moveTo(0f, 0f)
                lip.lineTo(W, 0f)
                lip.lineTo(W, 14f)
                var x = W
                while (x >= 0f) {
                    lip.lineTo(x, 14f + hash(x) * 6f)
                    x -= 8f
                }
                lip.close()
                c.drawPath(lip, fill().shade(lin(0f, 0f, 0f, 18f, 0f to col("#a6dd6a"), 1f to col("#4f9a3c"))))
                c.drawRect(0f, 0f, W, 2f, fill(rgba(255, 255, 220, 0.55f)))
                // shadow under the lip
                c.drawRect(0f, 14f, W, 32f, fill().shade(lin(0f, 14f, 0f, 32f, 0f to rgba(30, 15, 5, 0.35f), 1f to rgba(30, 15, 5, 0f))))
            }
        }

        val pit by lazy { skySprite(col("#2a1a10"), col("#0d0805"), col("#000000"), col("#000000")) }

        val haze by lazy {
            makeSprite(2f, 128f, 1f) { c ->
                c.drawRect(0f, 0f, 2f, 128f, fill().shade(lin(0f, 0f, 0f, 128f, 0f to rgba(255, 207, 154, 0f), 1f to rgba(255, 207, 154, 0.35f))))
            }
        }

        val puff by lazy {
            makeSprite(110f, 70f) { c ->
                val p = fill().shade(lin(0f, 10f, 0f, 60f, 0f to col("#fff2e0"), 0.6f to col("#ffc2a8"), 1f to col("#c983a8")))
                p.setShadowLayer(5f, 0f, 0f, rgba(255, 220, 200, 0.6f))
                val path = Path()
                path.addCircle(22f, 42f, 20f, Path.Direction.CW)
                path.addCircle(48f, 32f, 24f, Path.Direction.CW)
                path.addCircle(74f, 42f, 18f, Path.Direction.CW)
                c.drawPath(path, p)
            }
        }

        private fun paintBox(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
            c.drawRoundRect(RectF(x, y, x + w, y + h), 3f, 3f, fill().shade(lin(x, 0f, x + w, 0f, 0f to col("#e2ad6c"), 0.7f to col("#c38a4c"), 1f to col("#94632f"))))
            c.drawRect(x + 1f, y, x + w - 1f, y + 2f, fill(rgba(255, 240, 210, 0.6f)))
            c.drawRect(x, y + 4f, x + w, y + 8f, fill(rgba(80, 45, 15, 0.35f)))
            c.drawRect(x + w / 2f - 4f, y, x + w / 2f + 4f, y + h, fill(col("#efdcae")))
            c.drawRect(x + w / 2f - 4f, y, x + w / 2f - 2f, y + h, fill(rgba(255, 255, 255, 0.35f)))
            // a "this way up" mark
            val mark = stroke(rgba(90, 50, 20, 0.45f), 1.5f)
            c.drawLine(x + 7f, y + h - 8f, x + 7f, y + h - 16f, mark)
            c.drawLine(x + 4f, y + h - 13f, x + 7f, y + h - 16f, mark)
            c.drawLine(x + 7f, y + h - 16f, x + 10f, y + h - 13f, mark)
        }

        val box by lazy { makeSprite(44f, 42f) { paintBox(it, 0f, 0f, 44f, 42f) } }
        val boxLow by lazy { makeSprite(46f, 40f) { paintBox(it, 0f, 0f, 46f, 40f) } }
        val boxTop by lazy { makeSprite(40f, 40f) { paintBox(it, 0f, 0f, 40f, 40f) } }

        // drawn 4 units higher than its top, for the curve of the arm
        val stapler by lazy {
            makeSprite(64f, 34f) { c ->
                c.translate(0f, 4f)
                val w = 64f
                c.drawRoundRect(RectF(0f, 18f, w, 30f), 4f, 4f, fill().shade(lin(0f, 18f, 0f, 30f, 0f to col("#6a6e7c"), 1f to col("#22242c"))))
                val top = Path()
                top.moveTo(4f, 18f)
                top.lineTo(6f, 4f)
                top.quadTo(w * 0.6f, -2f, w, 12f)
                top.lineTo(w, 18f)
                top.close()
                c.drawPath(top, fill().shade(lin(0f, 0f, 0f, 18f, 0f to col("#ff7a6a"), 0.5f to col("#d8433a"), 1f to col("#8e1f1a"))))
                val hl = Path()
                hl.moveTo(8f, 5f)
                hl.quadTo(w * 0.55f, 0.5f, w - 4f, 10f)
                c.drawPath(hl, stroke(rgba(255, 220, 210, 0.7f), 1.5f))
                c.drawCircle(8f, 16f, 4f, fill(col("#c8ccd6")))
                c.drawRect(6.5f, 14f, 8f, 15.5f, fill(WHITE))
            }
        }

        private val ads = arrayOfNulls<Sprite>(ADS.size * 2)

        // The ad window (with its drop shadow), drawn 2 units up and left of its box.
        fun ad(label: Int, blink: Boolean): Sprite {
            val k = label * 2 + (if (blink) 1 else 0)
            ads[k]?.let { return it }
            val s = makeSprite(140f, 92f) { c ->
                c.translate(2f, 2f)
                val w = 130f
                val h = 80f
                c.drawRoundRect(RectF(6f, 8f, 6f + w, 8f + h), 6f, 6f, fill(rgba(20, 10, 30, 0.25f)))
                c.drawRoundRect(RectF(0f, 0f, w, h), 6f, 6f, fill().shade(lin(0f, 0f, 0f, h, 0f to WHITE, 1f to col("#e3e8f2"))))
                val bar = fill().shade(lin(0f, 0f, 0f, 18f, 0f to col("#6f9bff"), 1f to col("#2f5ac8")))
                c.drawRoundRect(RectF(0f, 0f, w, 18f), 6f, 6f, bar)
                c.drawRect(0f, 10f, w, 18f, bar)
                c.drawRect(4f, 2f, w - 4f, 6f, fill(rgba(255, 255, 255, 0.35f)))
                c.drawRoundRect(RectF(w - 17f, 3f, w - 4f, 15f), 3f, 3f, fill(col("#e0453a")))
                Txt.draw(c, "×", w - 10.5f, 9f, 12f)
                Txt.draw(c, ADS[label], w / 2f, 40f, 17f, if (blink) col("#e0453a") else col("#f2a516"))
                c.drawRoundRect(RectF(w / 2f - 28f, 55f, w / 2f + 28f, 72f), 5f, 5f, fill().shade(lin(0f, 55f, 0f, 72f, 0f to col("#6bd486"), 1f to col("#2f9a4a"))))
                Txt.draw(c, "OK", w / 2f, 64f, 11f)
            }
            ads[k] = s
            return s
        }

        // A coin face-on (squeezed sideways as it spins).
        val coin by lazy {
            makeSprite(26f, 24f) { c ->
                c.drawOval(RectF(0f, 0f, 26f, 24f), fill(col("#b9781a")))
                c.drawOval(RectF(1.2f, 1f, 24.8f, 23f), fill().shade(lin(3f, 0f, 23f, 24f, 0f to col("#fff2a8"), 0.5f to col("#f2b632"), 1f to col("#c98a1a"))))
                c.drawOval(RectF(6.5f, 5.5f, 19.5f, 18.5f), fill(rgba(255, 240, 170, 0.9f)))
            }
        }

        val cursorPath by lazy {
            Path().apply {
                moveTo(CURSOR[0], CURSOR[1])
                var i = 2
                while (i < CURSOR.size) {
                    lineTo(CURSOR[i], CURSOR[i + 1])
                    i += 2
                }
                close()
            }
        }

        val cursorFill by lazy { fill().shade(lin(0f, 0f, 10f, 18f, 0f to WHITE, 1f to col("#c9cfe0"))) }
    }
}
