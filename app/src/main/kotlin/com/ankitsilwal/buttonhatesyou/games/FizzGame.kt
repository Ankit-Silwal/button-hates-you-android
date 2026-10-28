package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Shaken Not Stirred: the button holds a soda. Drag it back and forth to build
// pressure, then let go while the meter is in the green. Too little is a sad
// fizzle, too much (past the red line) is a face full of soda. 3 lives.
// Port of src/adventure/games/fizz.js.
class FizzGame(private val ctx: GameContext) : Game {
    private companion object {
        const val LIVES = 3
        const val BTN_X = 150f
        const val BTN_Y = 590f
        const val BTN_SIZE = 150f
        const val BOT_X = 322f
        const val BOT_Y = 565f
        const val NECK = -108f // neck top, relative to the bottle's centre
        const val GX = 432f // pressure gauge: a tube on the right edge
        const val GW = 34f
        const val GT = 190f
        const val GB = 700f
        const val GH = GB - GT
        const val MAXTRAVEL = 3800f // px of shaking per second that count (caps the pace)
        const val K = 0.00018f // pressure per px of shaking
        const val GRAV = 920f
        const val MAX_DROPS = 520
        const val MAX_SPLATS = 60
        const val MAX_STAINS = 44

        const val IN = 0
        const val PLAY = 1
        const val POP = 2
        const val DEAD = 3

        const val GOOD = 0
        const val PERFECT = 1
        const val GUSH = 2
        const val FIZZLE = 3
        const val BOOM = 4

        // per way of letting go: seconds of spray, particles per second, spread, lean, speed range
        val SH_D = floatArrayOf(1.05f, 1.55f, 0.8f, 0.65f, 0.55f)
        val SH_RATE = floatArrayOf(780f, 1000f, 480f, 110f, 420f)
        val SH_SPREAD = floatArrayOf(0.15f, 0.17f, 0.6f, 0.55f, 1.0f)
        val SH_LEAN = floatArrayOf(-0.14f, -0.14f, -0.1f, 0f, -2.2f)
        val SH_SP0 = floatArrayOf(520f, 600f, 250f, 80f, 220f)
        val SH_SP1 = floatArrayOf(760f, 900f, 520f, 220f, 720f)

        class Label(val lines: Array<String>, val c1: String, val c2: String, val liq0: String, val liq1: String, val cap: String)

        val LABELS = arrayOf(
            Label(arrayOf("Cola-ish"), "#d8433a", "#fff1d6", "#7a3a16", "#2c1006", "#d8433a"),
            Label(arrayOf("Sparkling", "Regret"), "#7d4fd0", "#ffe0ff", "#a23ec2", "#4a1366", "#ffd84d"),
            Label(arrayOf("Mystery", "Fizz"), "#2e9e5b", "#eaffd9", "#6fd05a", "#1f6b2b", "#2e9e5b"),
            Label(arrayOf("Fizzy Lifting", "Drink"), "#ef8a1c", "#fff3d0", "#ffb02e", "#b85a06", "#ef8a1c"),
            Label(arrayOf("Burp", "Cola"), "#2d7fd0", "#e6f4ff", "#5a2e18", "#220c04", "#2d7fd0"),
            Label(arrayOf("Pop Star", "Zero"), "#e0457f", "#ffe3ef", "#ff6d9c", "#a3144a", "#e0457f"),
        )

        val NERVOUS = arrayOf("It is getting warm in here.", "Easy on the shaking.", "I do not like this bottle.", "That sounded angry.")
        val PANIC = arrayOf("NO NO NO NO", "LET GO LET GO", "It is ALIVE.", "Too much! TOO MUCH!")
        val GOODS = arrayOf("Whoa. Pretty.", "Fizzy. Fabulous.", "I regret nothing.", "Okay, that was cool.", "Sticky. Victorious.")
        val PERFECTS = arrayOf("PERFECT. I hate that.", "A masterpiece.", "Chef kiss.")
        val FIZZLES = arrayOf("Pfft. Is that it?", "A sad little hiss.", "Flat. Like my hopes.")
        val GUSHES = arrayOf("Too much. Not enough. Wrong.", "Messy. So messy.", "Overcooked.")
        val BOOMS = arrayOf("My FACE.", "Soda everywhere. Mostly on my face.", "I am wearing it now.", "Why. WHY.")
        val IDLE = arrayOf("Shake it. I dare you.", "It is just a bottle. Right?", "Go on. I will wait.")
        val END = arrayOf("Out of soda. Out of luck.", "Sticky and defeated.", "Never again.")

        fun ease(k: Float): Float = 1f - (1f - clamp(k, 0f, 1f)).pow(3)

        // the bottle's outline in a 90x250 box (neck top at y 20, body 10..80 wide)
        fun bottlePath(): Path = Path().apply {
            moveTo(34f, 20f)
            lineTo(56f, 20f)
            lineTo(56f, 78f)
            cubicTo(56f, 98f, 80f, 100f, 80f, 132f)
            lineTo(80f, 232f)
            quadTo(80f, 246f, 66f, 246f)
            lineTo(24f, 246f)
            quadTo(10f, 246f, 10f, 232f)
            lineTo(10f, 132f)
            cubicTo(10f, 100f, 34f, 98f, 34f, 78f)
            close()
        }
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

    // soda drops and foam blobs
    private val dx = FloatArray(MAX_DROPS)
    private val dy = FloatArray(MAX_DROPS)
    private val dvx = FloatArray(MAX_DROPS)
    private val dvy = FloatArray(MAX_DROPS)
    private val dr = FloatArray(MAX_DROPS)
    private val dfoam = BooleanArray(MAX_DROPS)
    private val dlife = FloatArray(MAX_DROPS)
    private val dmax = FloatArray(MAX_DROPS)
    private val dfloor = FloatArray(MAX_DROPS)
    private var drops = 0

    // puddles on the lawn
    private val spx = FloatArray(MAX_SPLATS)
    private val spy = FloatArray(MAX_SPLATS)
    private val spr = FloatArray(MAX_SPLATS)
    private val spl = FloatArray(MAX_SPLATS)
    private var splats = 0

    // soda on the button's face, button-relative
    private val stx = FloatArray(MAX_STAINS)
    private val sty = FloatArray(MAX_STAINS)
    private val str = FloatArray(MAX_STAINS)
    private val stl = FloatArray(MAX_STAINS)
    private var stains = 0

    private var t = 0f
    private var lives = LIVES
    private var label = Random.nextInt(LABELS.size)
    private var phase = PLAY
    private var pt = 0f
    private var kind = -1 // how the last bottle ended
    private var p = 0f // pressure, 0..1 (1 = boom)
    private var zc = 0.7f
    private var zw = 0.27f
    private var leak = 0.07f
    private var holding = false
    private var lx = 0f
    private var ly = 0f
    private var acc = 0f // shaking not yet turned into pressure
    private var rate = 0f // smoothed shaking intensity 0..1
    private var lastLvl = 0
    private var said = 0
    private var ox = 0f // bottle slides in/out sideways
    private var recoil = 0f
    private var jx = 0f
    private var jy = 0f
    private var ja = 0f
    private var capOn = false // a cap flying through the air
    private var capX = 0f
    private var capY = 0f
    private var capVx = 0f
    private var capVy = 0f
    private var capRot = 0f
    private var capVr = 0f
    private var capCol = 0
    private var emitAcc = 0f
    private var shake = 0f
    private var flash = 0f
    private var squash = 0f
    private var rayT = 0f
    private var banner: String? = null
    private var bannerColor = WHITE
    private var bannerT = 0f
    private var chat = 5f

    private fun zlo() = zc - zw / 2f
    private fun zhi() = zc + zw / 2f
    private fun bx() = BOT_X + ox + jx
    private fun by() = BOT_Y + jy + recoil * 14f
    private fun ba() = ja + recoil * 0.12f
    private fun neckX() = bx() + sin(ba()) * -NECK
    private fun neckY() = by() + cos(ba()) * NECK

    private fun say(lines: Array<String>, sec: Float) = mouth.say(pick(lines), sec)

    // a fresh bottle: the green zone shrinks and the pressure leaks faster as you score
    private fun newRound(first: Boolean) {
        val n = score
        zw = max(0.07f, 0.27f - 0.02f * n)
        zc = rand(0.32f + zw / 2f, 0.88f - zw / 2f)
        leak = min(0.28f, 0.07f + 0.014f * n)
        label = (label + 1) % LABELS.size
        p = 0f
        holding = false
        acc = 0f
        rate = 0f
        said = 0
        lastLvl = 0
        capOn = false
        kind = -1
        recoil = 0f
        phase = if (first) PLAY else IN
        pt = 0f
        ox = if (first) 0f else 360f
        chat = rand(4f, 6f)
    }

    private fun spawn(x: Float, y: Float, a: Float, sp: Float, foam: Boolean) {
        if (drops >= MAX_DROPS || x.isNaN() || y.isNaN()) return
        val i = drops++
        val life = if (foam) rand(0.5f, 1.1f) else 9f
        dx[i] = x
        dy[i] = y
        dvx[i] = cos(a) * sp
        dvy[i] = sin(a) * sp
        dr[i] = if (foam) rand(5f, 9f) else rand(1.6f, 4.2f)
        dfoam[i] = foam
        dlife[i] = life
        dmax[i] = life
        dfloor[i] = rand(690f, 775f)
    }

    private fun stain(x: Float, y: Float, r: Float, life: Float) {
        if (stains >= MAX_STAINS) return
        stx[stains] = x
        sty[stains] = y
        str[stains] = r
        stl[stains] = life
        stains++
    }

    private fun addTravel(d: Float) {
        if (over || phase != PLAY || !(d > 0f)) return
        acc = min(acc + d, 900f)
    }

    // let go of the cap
    private fun pop(pp: Float) {
        holding = false
        acc = 0f
        val hit = pp >= zlo() && pp <= zhi()
        val k = when {
            pp >= 1f -> BOOM
            hit -> if (abs(pp - zc) <= zw * 0.2f) PERFECT else GOOD
            pp < zlo() -> FIZZLE
            else -> GUSH
        }
        kind = k
        phase = POP
        pt = 0f
        emitAcc = 0f
        val nx = neckX()
        val ny = neckY()
        val scored = k == GOOD || k == PERFECT
        capOn = true
        capX = nx
        capY = ny
        capVx = rand(-40f, 140f)
        capVy = if (scored) -620f else if (k == BOOM) -900f else -240f
        capRot = 0f
        capVr = rand(-14f, 14f)
        capCol = col(LABELS[label].cap)
        if (scored) {
            score++
            recoil = 1f
            ctx.sfx.play("pop")
            ctx.haptics.tap()
            fx.ring(nx, ny, col("#fff3c4"), 100f, 0.45f, 8f)
            fx.burst(nx, ny, col("#ffd9a0"), 20, 460f, up = 260f)
            fx.float(nx, ny - 70f, "+1", if (k == PERFECT) GOLD else WHITE)
            say(if (k == PERFECT) PERFECTS else GOODS, 1.8f)
            if (k == PERFECT) {
                ctx.sfx.play("win")
                rayT = 1f
                showBanner("PERFECT!", GOLD)
                fx.burst(nx, ny - 30f, col("#fff7c2"), 26, 560f, up = 300f, size = 3.5f)
                fx.ring(nx, ny, GOLD, 150f, 0.6f, 10f)
                shake = 0.18f
            }
        } else {
            lives--
            when (k) {
                FIZZLE -> {
                    ctx.sfx.play("nope")
                    ctx.haptics.thud()
                    say(FIZZLES, 1.8f)
                }
                GUSH -> {
                    ctx.sfx.play("bad")
                    ctx.haptics.thud()
                    say(GUSHES, 1.8f)
                    recoil = 0.5f
                }
                else -> {
                    ctx.sfx.play("boom")
                    ctx.haptics.thud()
                    say(BOOMS, 2.4f)
                    shake = 0.5f
                    flash = 0.14f
                    squash = 0.28f
                    recoil = 1f
                    showBanner("KABLOOEY", col("#ff6a4a"))
                    fx.ring(nx, ny, col("#ffb347"), 200f, 0.5f, 12f)
                    fx.burst(nx, ny, col("#ffb347"), 30, 560f)
                    for (i in 0 until 70) spawn(nx, ny, -PI_F + rand(0f, 1.3f), rand(250f, 800f), false)
                    for (i in 0 until 12) spawn(nx, ny, rand(-PI_F, 0f), rand(100f, 380f), true)
                    for (i in 0 until 8) stain(rand(-45f, 45f), rand(-40f, 45f), rand(3f, 8f), 8f)
                }
            }
        }
    }

    private fun showBanner(s: String, c: Int) {
        banner = s
        bannerColor = c
        bannerT = 0f
    }

    private fun release() {
        if (phase != PLAY || over) return
        holding = false
        if (p < 0.05f) return // a stray tap
        pop(p)
    }

    private fun emit(dt: Float) {
        val k0 = kind
        if (k0 < 0) return
        val k = pt / SH_D[k0]
        val nx = neckX()
        val ny = neckY()
        emitAcc += SH_RATE[k0] * (1f - k * 0.5f) * dt
        val base = if (k0 == BOOM) SH_LEAN[k0] else -PI_F / 2f + SH_LEAN[k0] * (0.4f + Random.nextFloat() * 0.9f)
        var guard = 0
        while (emitAcc >= 1f && guard++ < 200) {
            emitAcc--
            val foam = Random.nextFloat() < (if (k0 == FIZZLE) 0.5f else 0.12f)
            val a = if (k0 == BOOM) base + rand(-0.9f, 0.9f) else base + rand(-SH_SPREAD[k0], SH_SPREAD[k0])
            val sp = rand(SH_SP0[k0], SH_SP1[k0]) * (1f - k * 0.4f) * (if (foam) 0.5f else 1f)
            spawn(nx + rand(-4f, 4f), ny, a, sp, foam)
        }
        if (emitAcc > 1f) emitAcc = 0f
    }

    private fun particles(dt: Float) {
        var i = drops - 1
        while (i >= 0) {
            dlife[i] -= dt
            var dead = dlife[i] <= 0f
            if (dfoam[i]) {
                dvy[i] += 120f * dt
                dvx[i] *= 1f - min(1f, dt * 2f)
                dr[i] += dt * 8f
            } else {
                dvy[i] += GRAV * dt
                // landing on the button's face leaves a stain
                val ex = (dx[i] - BTN_X) / 64f
                val ey = (dy[i] - BTN_Y) / 70f
                if (dvy[i] > 0f && ex * ex + ey * ey < 1f) {
                    stain(dx[i] - BTN_X, dy[i] - BTN_Y, dr[i] * rand(1f, 1.8f), 7f)
                    dead = true
                } else if (dy[i] > dfloor[i]) {
                    if (splats < MAX_SPLATS && Random.nextFloat() < 0.3f) {
                        spx[splats] = dx[i]
                        spy[splats] = dy[i]
                        spr[splats] = dr[i] * rand(1.4f, 2.6f)
                        spl[splats] = 8f
                        splats++
                    }
                    dead = true
                }
            }
            dx[i] += dvx[i] * dt
            dy[i] += dvy[i] * dt
            if (dead || dx[i] < -40f || dx[i] > W + 40f || dx[i].isNaN() || dy[i].isNaN()) {
                val l = --drops
                if (i != l) {
                    dx[i] = dx[l]; dy[i] = dy[l]; dvx[i] = dvx[l]; dvy[i] = dvy[l]; dr[i] = dr[l]
                    dfoam[i] = dfoam[l]; dlife[i] = dlife[l]; dmax[i] = dmax[l]; dfloor[i] = dfloor[l]
                }
            }
            i--
        }
        var n = 0
        for (k in 0 until splats) {
            spl[k] -= dt
            if (spl[k] <= 0f) continue
            spx[n] = spx[k]; spy[n] = spy[k]; spr[n] = spr[k]; spl[n] = spl[k]
            n++
        }
        splats = n
        n = 0
        for (k in 0 until stains) {
            stl[k] -= dt
            if (stl[k] <= 0f) continue
            stx[n] = stx[k]; sty[n] = sty[k]; str[n] = str[k]; stl[n] = stl[k]
            n++
        }
        stains = n
        if (capOn) {
            capVy += 1400f * dt
            capX += capVx * dt
            capY += capVy * dt
            capRot += capVr * dt
            if (capY > 900f || capY.isNaN()) capOn = false
        }
    }

    override fun down(x: Float, y: Float) {
        if (over || phase != PLAY || x.isNaN() || y.isNaN()) return
        holding = true
        lx = x
        ly = y
    }

    override fun move(x: Float, y: Float) {
        if (!holding || x.isNaN() || y.isNaN()) return
        addTravel(min(hypot(x - lx, y - ly), 90f))
        lx = x
        ly = y
    }

    override fun up(x: Float, y: Float) {
        if (holding) release()
    }

    // a paid Continue: one more life and a fresh bottle, same score
    override fun revive() {
        over = false
        lives = 1
        newRound(false)
        reviveFx(fx, mouth, ctx, BTN_X, BTN_Y - 40f, lookColor)
    }

    override fun update(dt0: Float) {
        val dt = if (dt0 > 0f) min(dt0, 0.05f) else 0f
        t += dt
        mouth.update(dt)
        fx.update(dt)
        particles(dt)
        shake = max(0f, shake - dt)
        flash = max(0f, flash - dt)
        rayT = max(0f, rayT - dt * 1.4f)
        squash *= exp(-dt * 6f)
        recoil *= exp(-dt * 5f)
        if (banner != null) {
            bannerT += dt
            if (bannerT > 1.3f) banner = null
        }
        if (over) return

        when (phase) {
            IN -> {
                pt += dt
                ox = (1f - ease(pt / 0.5f)) * 360f
                if (pt >= 0.5f) {
                    phase = PLAY
                    ox = 0f
                }
            }
            PLAY -> {
                val take = min(acc, MAXTRAVEL * dt)
                acc -= take
                val prev = p
                p += take * K - leak * dt
                if (!(p > 0f)) p = 0f
                rate += ((if (dt > 0f) take / (MAXTRAVEL * dt) else 0f) - rate) * min(1f, dt * 9f)
                // ratchet ticks as the pressure climbs
                val lvl = floor(p * 24f).toInt()
                if (lvl > lastLvl && p > prev) ctx.sfx.play("tick")
                lastLvl = lvl
                if (p >= 1f) {
                    p = 1f
                    pop(1f)
                } else {
                    if (p > 0.55f && said and 1 == 0) {
                        said = said or 1
                        say(NERVOUS, 1.6f)
                    }
                    if (p > 0.88f && said and 2 == 0) {
                        said = said or 2
                        say(PANIC, 1.4f)
                    }
                    chat -= dt
                    if (chat <= 0f) {
                        chat = rand(6f, 9f)
                        if (p < 0.05f && mouth.left <= 0f) say(IDLE, 2f)
                    }
                }
                // the bottle trembles with the shaking and the pressure
                val amp = rate * 7f + p * p * 3.5f
                jx = sin(t * 61f) * amp
                jy = cos(t * 47f) * amp * 0.5f
                ja = sin(t * 53f + 1f) * amp * 0.025f
            }
            POP -> {
                pt += dt
                jx = 0f
                jy = 0f
                ja = 0f
                val d = if (kind >= 0) SH_D[kind] else 0.5f
                if (pt < d) emit(dt)
                val out = (pt - d) / 0.35f
                if (out > 0f) ox = clamp(out, 0f, 1f).pow(2) * 360f
                if (out >= 1f) {
                    if (lives <= 0) {
                        phase = DEAD
                        over = true
                        say(END, 3f)
                        ctx.sfx.play("bad")
                    } else {
                        newRound(false)
                    }
                }
            }
        }
    }

    // the button's face follows the pressure and the last result
    private fun mood(hot: Float): Mood {
        if (over) return Mood.Sad
        if (phase == POP && kind >= 0) {
            return when (kind) {
                GOOD, PERFECT -> Mood.Happy
                BOOM -> Mood.Angry
                GUSH -> Mood.Worried
                else -> Mood.Sad
            }
        }
        if (hot > 0.82f) return Mood.Angry
        if (hot > 0.4f) return Mood.Worried
        return if (holding) Mood.Smug else Mood.Calm
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.save()
        if (shake > 0f) c.translate(rand(-2f, 2f) * shake * 14f, rand(-2f, 2f) * shake * 14f)
        c.sprite(art.bg, 0f, 0f, W, H)
        paint.color = PUDDLE
        for (k in 0 until splats) {
            paint.alpha = (clamp(spl[k] / 2f, 0f, 1f) * 0.5f * 255f).toInt()
            oval.set(spx[k] - spr[k] * 1.5f, spy[k] - spr[k] * 0.6f, spx[k] + spr[k] * 1.5f, spy[k] + spr[k] * 0.6f)
            c.drawOval(oval, paint)
        }

        // the button, nervous in step with the pressure
        val hot = if (phase == PLAY) p else 0f
        val bobY = sin(t * 2f) * 1.5f
        val bx = BTN_X + sin(t * 47f) * rate * 3f
        val by = BTN_Y + bobY
        c.shadow(BTN_X, BTN_Y + 76f, 80f, 0.3f, 12f)
        bobby(
            c, bx, by, BTN_SIZE, look,
            mood = mood(hot), t = t,
            squash = squash + sin(t * 40f) * rate * 0.03f,
            flash = flash > 0f, tears = over,
        )
        for (k in 0 until stains) {
            val a = clamp(stl[k] / 2f, 0f, 1f) * 0.8f
            paint.color = rgba(STAIN, a)
            c.drawCircle(bx + stx[k], by + sty[k], str[k], paint)
            paint.color = rgba(255, 225, 170, 0.6f * a)
            c.drawCircle(bx + stx[k] - str[k] * 0.3f, by + sty[k] - str[k] * 0.3f, str[k] * 0.3f, paint)
        }

        if (ox < 359f && phase != DEAD) bottle(c, hot)
        if (capOn) cap(c, capX, capY, capRot, capCol)

        // the jet itself, under the droplets
        val k0 = kind
        if (phase == POP && k0 >= 0 && pt < SH_D[k0] && k0 != BOOM && k0 != FIZZLE) {
            val k = pt / SH_D[k0]
            val nx = neckX()
            val ny = neckY()
            val s = if (k0 == PERFECT) 1.2f else if (k0 == GUSH) 0.5f else 0.85f
            val h = 300f * s * (1f - k * 0.6f) * min(1f, pt * 8f)
            val lean = if (k0 == GUSH) 0f else -h * 0.12f
            line.color = JET
            line.strokeWidth = 20f * s * (1f - k * 0.7f)
            c.drawLine(nx, ny, nx + lean, ny - h, line)
            line.color = JET_CORE
            line.strokeWidth = 8f * s * (1f - k * 0.7f)
            c.drawLine(nx, ny, nx + lean, ny - h, line)
            c.glow(nx, ny - 10f, 70f * s, col("#ffd9a0"), 0.5f * (1f - k))
        }
        if (rayT > 0f) c.rays(neckX(), neckY(), 460f, RAYS, 0.5f * rayT, t * 0.5f)

        drawDrops(c)
        fx.draw(c)
        c.restore()

        c.vignette(0.28f, WARM)
        if (phase == PLAY && p > 0.82f) c.vignette(0.1f + (p - 0.82f) * 1.8f * abs(sin(t * 10f)), ALARM)

        gauge(c)
        for (i in 0 until LIVES) {
            val x = W / 2f + (i - 1) * 36f
            c.heart(x, 160f, 11f, if (i < lives) HEART else HEART_OFF, if (i < lives) WHITE else HEART_OFF_LINE)
        }
        c.drawScore(score, "bottles")
        mouth.draw(c, BTN_X, 488f)

        val b = banner
        if (b != null) {
            val k = bannerT
            val sc = 1f + max(0f, 0.35f - k * 1.6f)
            c.save()
            c.translate(W / 2f, 300f)
            c.rotate(-0.06f * DEG)
            c.scale(sc, sc)
            edge(c, b, 0f, 0f, 52f, bannerColor, clamp((1.3f - k) / 0.3f, 0f, 1f))
            c.restore()
        }
        if (!over && score < 2 && phase == PLAY) {
            if (p < 0.05f) edge(c, "Drag the bottle back and forth!", W / 2f, 218f, 18f, WHITE)
            edge(c, "Let go in the green.", W / 2f - 20f, 764f, 14f, rgba(255, 255, 255, 0.92f))
        }
    }

    private fun edge(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Float = 1f) {
        Txt.draw(c, s, x, y + 2f, size, EDGE, alpha = alpha)
        Txt.draw(c, s, x, y, size, color, alpha = alpha)
    }

    // the bottle with its bubbles, rising foam, cap and the button's hand
    private fun bottle(c: Canvas, hot: Float) {
        val bx = bx()
        val by = by()
        val ba = ba()
        c.shadow(BOT_X + ox, 705f, 58f, 0.28f, 10f)
        // the arm reaches out from the button to the bottle
        val hx = bx + -36f * cos(ba) - 38f * sin(ba)
        val hy = by + -36f * sin(ba) + 38f * cos(ba)
        arm.color = lookDark
        armPath.reset()
        armPath.moveTo(BTN_X + 62f, BTN_Y + 28f)
        armPath.quadTo((BTN_X + 62f + hx) / 2f, BTN_Y + 60f, hx, hy)
        c.drawPath(armPath, arm)

        c.save()
        c.translate(bx, by)
        c.rotate(ba * DEG)
        if (hot > 0.55f) c.glow(0f, 0f, 170f, col("#ff4f4f"), (hot - 0.55f) * 1.3f)
        c.sprite(art.bottles[label], -45f, -125f, 90f, 250f)

        c.save()
        c.translate(-45f, -125f)
        c.clipPath(art.shape)
        // foam climbs the neck as the pressure rises
        if (hot > 0.02f) {
            val top = 100f - hot * 86f
            paint.color = FOAM
            c.drawRect(0f, top, 90f, 104f, paint)
            var x = 30f
            while (x <= 60f) {
                c.drawCircle(x, top + sin(t * 9f + x) * 1.5f, 5.5f, paint)
                x += 5f
            }
            paint.color = rgba(214, 170, 120, 0.5f)
            for (i in 0 until 6) {
                c.drawCircle(36f + hash(i.toFloat()) * 18f, top + 6f + hash(i + 9f) * (100f - top - 8f), 2f + hash(i + 3f) * 2f, paint)
            }
        }
        // bubbles rise faster the harder it's shaken
        val spd = 18f + rate * 260f + hot * 100f
        for (i in 0 until 18) {
            val x = 45f + (hash(i + 20f) - 0.5f) * 54f + sin(t * 3f + i) * 3f
            val y = 244f - ((t * spd + hash(i.toFloat()) * 150f) % 144f)
            val r = 1.5f + hash(i + 40f) * 2.2f
            c.drawCircle(x, y, r, bubble)
        }
        c.restore()

        if (!capOn) cap(c, 0f, NECK - hot * 3f, 0f, col(LABELS[label].cap))
        paint.color = lookColor
        c.drawCircle(-36f, 38f, 13f, paint)
        hand.color = lookDark
        c.drawCircle(-36f, 38f, 13f, hand)
        c.restore()
    }

    private fun cap(c: Canvas, x: Float, y: Float, rot: Float, color: Int) {
        if (x.isNaN() || y.isNaN()) return
        c.save()
        c.translate(x, y)
        c.rotate(rot * DEG)
        oval.set(-14f, -8f, 14f, 7f)
        paint.color = color
        c.drawRoundRect(oval, 4f, 4f, paint)
        paint.color = rgba(0, 0, 0, 0.2f)
        var i = -10f
        while (i <= 10f) {
            c.drawRect(i, -5f, i + 1.5f, 5f, paint)
            i += 5f
        }
        paint.color = rgba(255, 255, 255, 0.45f)
        c.drawRect(-11f, -6f, 11f, -3.5f, paint)
        c.restore()
    }

    private fun drawDrops(c: Canvas) {
        for (pass in 0 until 2) {
            line.color = if (pass == 1) DROP_CORE else DROP
            for (i in 0 until drops) {
                if (dfoam[i]) continue
                line.strokeWidth = if (pass == 1) dr[i] * 0.8f else dr[i] * 2f
                val o2 = if (pass == 1) -dr[i] * 0.3f else 0f
                c.drawLine(dx[i] - dvx[i] * 0.018f + o2, dy[i] - dvy[i] * 0.018f + o2, dx[i] + o2, dy[i] + o2 + 0.01f, line)
            }
        }
        for (i in 0 until drops) {
            if (!dfoam[i]) continue
            paint.color = rgba(FOAM_DROP, clamp(dlife[i] / dmax[i], 0f, 1f) * 0.85f)
            c.drawCircle(dx[i], dy[i], dr[i], paint)
        }
    }

    private fun yOf(v: Float) = GB - clamp(v, 0f, 1f) * GH

    // the pressure tube: green sweet spot, bright centre, red line at the top
    private fun gauge(c: Canvas) {
        val x0 = GX - GW / 2f
        val dim = if (over || phase == POP) 0.6f else 1f
        c.saveLayerAlpha(x0 - 60f, GT - 40f, x0 + GW + 10f, GB + 10f, (dim * 255f).toInt())
        oval.set(x0, GT - 6f, x0 + GW, GB + 6f)
        paint.color = rgba(24, 14, 36, 0.62f)
        c.drawRoundRect(oval, 17f, 17f, paint)
        c.save()
        c.clipPath(art.tube)
        val lo = zlo()
        val hi = zhi()
        paint.color = rgba(255, 200, 60, 0.35f)
        c.drawRect(x0, yOf(0.93f), x0 + GW, yOf(hi), paint)
        paint.color = col("#ff4f4f")
        c.drawRect(x0, yOf(1f), x0 + GW, yOf(0.93f), paint)
        paint.color = col("#4be37a")
        c.drawRect(x0, yOf(hi), x0 + GW, yOf(lo), paint)
        paint.color = col("#c8ffd6")
        val cy = yOf(zc + zw * 0.2f)
        c.drawRect(x0, cy, x0 + GW, cy + zw * 0.4f * GH, paint)
        // the level
        val inZone = p >= lo && p <= hi
        paint.color = if (p >= 0.93f) col("#ff5a4a") else if (p > hi) col("#ffb347") else if (inZone) col("#2fbf5e") else col("#7cc7ff")
        c.drawRect(x0 + 8f, yOf(p), x0 + GW - 8f, GB + 4f, paint)
        paint.color = rgba(255, 255, 255, 0.85f)
        c.drawRect(x0 + 8f, yOf(p), x0 + GW - 8f, yOf(p) + 3f, paint)
        c.restore()
        oval.set(x0, GT - 6f, x0 + GW, GB + 6f)
        tubeLine.color = rgba(255, 255, 255, 0.6f)
        c.drawRoundRect(oval, 17f, 17f, tubeLine)
        if (inZone && phase == PLAY) c.glow(GX, yOf(zc), 50f, col("#5cff8a"), 0.4f + sin(t * 14f) * 0.2f)
        // red line, level pointer, labels
        paint.color = col("#ff2d2d")
        c.drawRect(x0 - 6f, yOf(1f) - 1.5f, x0 + GW + 6f, yOf(1f) + 1.5f, paint)
        val ay = yOf(p)
        pointer.reset()
        pointer.moveTo(x0 - 4f, ay)
        pointer.lineTo(x0 - 18f, ay - 9f)
        pointer.lineTo(x0 - 18f, ay + 9f)
        pointer.close()
        paint.color = WHITE
        c.drawPath(pointer, paint)
        tubeLine.color = rgba(30, 14, 40, 0.5f)
        tubeLine.strokeWidth = 1.5f
        c.drawPath(pointer, tubeLine)
        tubeLine.strokeWidth = 2f
        edge(c, "BOOM", GX, GT - 22f, 14f, col("#ff6a5a"))
        edge(c, "GOOD", x0 - 36f, yOf(zc), 12f, col("#9cffb4"))
        c.restore()
    }

    /* ---------- art ---------- */

    private val paint = fill(WHITE)
    private val line = stroke(WHITE, 2f)
    private val arm = stroke(WHITE, 9f)
    private val hand = stroke(WHITE, 3f)
    private val bubble = stroke(rgba(255, 255, 255, 0.7f), 1f)
    private val tubeLine = stroke(WHITE, 2f)
    private val armPath = Path()
    private val pointer = Path()
    private val oval = RectF()

    private val GOLD = col("#ffe27a")
    private val PUDDLE = col("#6b3516")
    private val STAIN = col("#7a3a16")
    private val JET = rgba(176, 97, 44, 0.8f)
    private val JET_CORE = rgba(255, 240, 210, 0.85f)
    private val DROP = col("#b0612c")
    private val DROP_CORE = rgba(255, 226, 180, 0.85f)
    private val FOAM = col("#fff1d6")
    private val FOAM_DROP = col("#fff4de")
    private val RAYS = col("#fff2b0")
    private val WARM = col("#3a1c08")
    private val ALARM = col("#c0182a")
    private val HEART = col("#ff4f7b")
    private val HEART_OFF = rgba(60, 40, 70, 0.55f)
    private val HEART_OFF_LINE = rgba(255, 255, 255, 0.4f)
    private val EDGE = rgba(30, 14, 40, 0.5f)
    private val art = Art()

    private class Art {
        val shape = bottlePath()

        // the inside of the gauge tube
        val tube = Path().apply {
            val x0 = GX - GW / 2f
            addRoundRect(RectF(x0 + 3f, GT - 3f, x0 + GW - 3f, GB + 3f), 14f, 14f, Path.Direction.CW)
        }

        val bottles: Array<Sprite> by lazy { Array(LABELS.size) { bottleArt(it) } }

        // glass, soda, a funny label and a glassy streak, painted once per flavour
        private fun bottleArt(i: Int): Sprite = makeSprite(90f, 250f) { c ->
            val l = LABELS[i]
            val shape = bottlePath()
            c.drawPath(shape, fill(rgba(214, 240, 255, 0.3f)))
            c.save()
            c.clipPath(shape)
            c.drawRect(0f, 100f, 90f, 250f, fill().shade(lin(0f, 100f, 0f, 246f, 0f to col(l.liq0), 1f to col(l.liq1))))
            c.drawRect(0f, 100f, 90f, 102f, fill(rgba(255, 255, 255, 0.4f)))
            // label
            val c2 = col(l.c2)
            c.drawRect(0f, 150f, 90f, 212f, fill(col(l.c1)))
            c.drawRect(0f, 150f, 90f, 154f, fill(c2))
            c.drawRect(0f, 208f, 90f, 212f, fill(c2))
            val n = l.lines.size
            for (k in 0 until n) Txt.draw(c, l.lines[k], 45f, 181f + (k - (n - 1) / 2f) * 13f, 10.5f, c2)
            // shading across the glass
            c.drawRect(
                0f, 0f, 90f, 250f,
                fill().shade(lin(10f, 0f, 80f, 0f, 0f to rgba(255, 255, 255, 0.28f), 0.3f to rgba(255, 255, 255, 0f), 0.75f to rgba(0, 0, 0, 0f), 1f to rgba(0, 0, 0, 0.3f))),
            )
            c.restore()
            val shine = fill(rgba(255, 255, 255, 0.5f))
            c.drawRoundRect(RectF(17f, 124f, 22f, 214f), 2.5f, 2.5f, shine)
            c.drawRoundRect(RectF(38f, 28f, 41f, 68f), 1.5f, 1.5f, shine)
            c.drawPath(shape, stroke(rgba(255, 255, 255, 0.7f), 2f))
            c.drawPath(shape, stroke(rgba(30, 14, 40, 0.35f), 1f))
            // the ring under the cap
            c.drawRect(32f, 26f, 58f, 29f, fill(rgba(255, 255, 255, 0.55f)))
        }

        // a backyard picnic: sky, hills, fence and sunflowers, a gingham blanket
        // with a basket, cups and a watermelon slice
        val bg by lazy { makeSprite(W, H, 1.5f) { paintBg(it) } }

        private fun paintBg(c: Canvas) {
            c.drawRect(0f, 0f, W, 470f, fill().shade(lin(0f, 0f, 0f, 460f, 0f to col("#6fc3f4"), 1f to col("#eaf8ff"))))
            c.drawRect(0f, 0f, W, 470f, fill().shade(rad(410f, 120f, 0f, 190f, 0f to rgba(255, 248, 210, 1f), 0.15f to rgba(255, 236, 160, 0.8f), 1f to rgba(255, 236, 160, 0f))))
            // clouds
            val cloud = fill(rgba(255, 255, 255, 0.92f))
            for (cl in arrayOf(floatArrayOf(90f, 250f, 1f), floatArrayOf(300f, 300f, 0.8f), floatArrayOf(190f, 60f, 0.7f))) {
                val path = Path()
                for (b in arrayOf(floatArrayOf(0f, 0f, 26f), floatArrayOf(32f, -12f, 32f), floatArrayOf(66f, 0f, 26f), floatArrayOf(34f, 8f, 24f))) {
                    path.addCircle(cl[0] + b[0] * cl[2], cl[1] + b[1] * cl[2], b[2] * cl[2], Path.Direction.CW)
                }
                c.drawPath(path, cloud)
            }
            // hills
            val hillCols = intArrayOf(col("#a7d68a"), col("#7fc46a"))
            val bases = floatArrayOf(400f, 432f)
            val fs = floatArrayOf(0.012f, 0.019f)
            val amps = floatArrayOf(26f, 18f)
            val phs = floatArrayOf(1.2f, 3.4f)
            for (h in 0 until 2) {
                val path = Path()
                path.moveTo(0f, 520f)
                var x = 0f
                while (x <= W) {
                    path.lineTo(x, bases[h] - sin(x * fs[h] + phs[h]) * amps[h])
                    x += 8f
                }
                path.lineTo(W, 520f)
                path.close()
                c.drawPath(path, fill(hillCols[h]))
            }
            // a round tree on the left
            c.drawRect(30f, 330f, 44f, 440f, fill(col("#7a5230")))
            val crown = Path()
            crown.addCircle(38f, 300f, 58f, Path.Direction.CW)
            crown.addCircle(0f, 330f, 44f, Path.Direction.CW)
            crown.addCircle(76f, 332f, 40f, Path.Direction.CW)
            c.drawPath(crown, fill().shade(rad(36f, 310f, 6f, 80f, 0f to col("#9fdc7a"), 1f to col("#4f9a4a"))))
            // lawn
            c.drawRect(0f, 440f, W, H, fill().shade(lin(0f, 440f, 0f, H, 0f to col("#93d65c"), 1f to col("#3d8f37"))))
            val blade = stroke(WHITE, 1.4f)
            for (i in 0 until 260) {
                val fi = i.toFloat()
                val x = hash(fi) * W
                val y = 450f + hash(fi + 70f) * (H - 450f)
                blade.color = if (hash(fi + 5f) > 0.5f) rgba(30, 90, 30, 0.3f) else rgba(210, 245, 150, 0.3f)
                c.drawLine(x, y, x + (hash(fi + 2f) - 0.5f) * 5f, y - 5f - hash(fi + 9f) * 6f, blade)
            }
            // picket fence
            val rail = fill(col("#e9dcc4"))
            c.drawRect(0f, 418f, W, 424f, rail)
            c.drawRect(0f, 446f, W, 452f, rail)
            val picket = fill(col("#fffaf0"))
            val side = fill(rgba(120, 90, 50, 0.18f))
            var px = 6f
            while (px < W) {
                val path = Path()
                path.moveTo(px, 470f)
                path.lineTo(px, 405f)
                path.lineTo(px + 11f, 394f)
                path.lineTo(px + 22f, 405f)
                path.lineTo(px + 22f, 470f)
                path.close()
                c.drawPath(path, picket)
                c.drawRect(px + 16f, 405f, px + 22f, 470f, side)
                px += 30f
            }
            // sunflowers in front of the fence
            val stem = stroke(col("#4c9a3c"), 4f)
            val petal = fill(col("#ffd23f"))
            val seed = fill(col("#6b3d1a"))
            for (x in floatArrayOf(24f, 128f, 262f, 392f, 458f)) {
                c.drawLine(x, 476f, x, 438f, stem)
                for (k in 0 until 10) {
                    val a = (k / 10f) * TAU
                    c.save()
                    c.translate(x + cos(a) * 12f, 432f + sin(a) * 12f)
                    c.rotate(a * DEG)
                    c.drawOval(RectF(-7f, -3.4f, 7f, 3.4f), petal)
                    c.restore()
                }
                c.drawCircle(x, 432f, 7.5f, seed)
            }
            blanket(c)
            // basket (the button stands in front of it)
            c.drawRoundRect(RectF(22f, 528f, 104f, 578f), 8f, 8f, fill(col("#b9854d")))
            val weave = stroke(rgba(90, 50, 20, 0.4f), 2f)
            for (k in 0 until 4) c.drawLine(24f, 540f + k * 11f, 102f, 540f + k * 11f, weave)
            c.drawArc(RectF(33f, 500f, 93f, 560f), 180f, 180f, false, stroke(col("#8a5a2c"), 5f))
            val cloth = Path()
            cloth.moveTo(30f, 530f)
            cloth.lineTo(70f, 522f)
            cloth.lineTo(96f, 534f)
            cloth.lineTo(40f, 540f)
            cloth.close()
            c.drawPath(cloth, fill(WHITE))
            // cups
            for (cup in arrayOf(Triple(140f, 770f, "#e0453a"), Triple(172f, 776f, "#ef8a1c"), Triple(204f, 770f, "#2d7fd0"))) {
                val (x, y, cc) = cup
                val path = Path()
                path.moveTo(x - 14f, y - 26f)
                path.lineTo(x + 14f, y - 26f)
                path.lineTo(x + 10f, y + 12f)
                path.lineTo(x - 10f, y + 12f)
                path.close()
                c.drawPath(path, fill(col(cc)))
                c.drawRect(x - 11f, y - 22f, x - 7f, y + 6f, fill(rgba(255, 255, 255, 0.4f)))
            }
            // watermelon slice
            c.drawArc(RectF(346f, 716f, 414f, 784f), 0f, 180f, true, fill(col("#3f9a3d")))
            c.drawArc(RectF(352f, 722f, 408f, 778f), 0f, 180f, true, fill(col("#ff5a6e")))
            val pip = fill(col("#1b1210"))
            for (d in arrayOf(floatArrayOf(-14f, 8f), floatArrayOf(0f, 14f), floatArrayOf(14f, 8f), floatArrayOf(-6f, 22f), floatArrayOf(8f, 24f))) {
                c.save()
                c.translate(380f + d[0], 750f + d[1])
                c.rotate(0.3f * DEG)
                c.drawOval(RectF(-2f, -3.4f, 2f, 3.4f), pip)
                c.restore()
            }
            c.drawRect(320f, 746f, 440f, 750f, pip)
            c.drawRect(352f, 746f, 408f, 750f, fill(col("#ff5a6e")))
        }

        // a red gingham blanket in perspective
        private fun blanket(c: Canvas) {
            val top = 498f
            fun half(y: Float) = 170f + ((y - top) / (H - top)) * 190f
            val shape = Path()
            shape.moveTo(240f - half(top), top)
            shape.lineTo(240f + half(top), top)
            shape.lineTo(240f + half(H), H)
            shape.lineTo(240f - half(H), H)
            shape.close()
            val base = fill(col("#fff4ea"))
            base.setShadowLayer(16f, 0f, 0f, rgba(20, 50, 10, 0.5f))
            c.drawPath(shape, base)
            c.save()
            c.clipPath(shape)
            val red = fill(rgba(225, 60, 60, 0.5f))
            var i = -4
            while (i < 4) {
                val p = Path()
                p.moveTo(240f + (i * half(top)) / 4f, top)
                p.lineTo(240f + ((i + 1) * half(top)) / 4f, top)
                p.lineTo(240f + ((i + 1) * half(H)) / 4f, H)
                p.lineTo(240f + (i * half(H)) / 4f, H)
                p.close()
                c.drawPath(p, red)
                i += 2
            }
            var k = 0
            while (k < 12) {
                val y0 = top + (H - top) * (k / 12f).pow(1.4f)
                val y1 = top + (H - top) * ((k + 1) / 12f).pow(1.4f)
                c.drawRect(0f, y0, W, y1, red)
                k += 2
            }
            c.restore()
        }
    }
}
