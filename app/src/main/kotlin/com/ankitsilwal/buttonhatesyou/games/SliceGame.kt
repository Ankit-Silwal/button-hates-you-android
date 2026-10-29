package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Slice Spree: fruit and junk are tossed up from the bottom. Swipe across them
// to slice them. Sometimes the button is tossed up too. Do NOT slice it.
// Port of src/adventure/games/slice.js.
class SliceGame(private val ctx: GameContext) : Game {
    private companion object {
        const val GRAVITY = 900f
        const val LIVES = 3
        const val TRAIL = 0.2f // how long a bit of blade trail stays, in seconds
        const val COMBO = 3
        const val BOBBY = 62f
        const val MAX_ITEMS = 32
        const val MAX_HALVES = 64
        const val MAX_TRAIL = 96
        const val MAX_QUEUE = 16
        const val MAX_SPLATS = 21
        const val MAX_SLASHES = 24

        // kinds: melon, orange, banana, key, duck; 5 is the button
        const val BOB = 5
        val R = floatArrayOf(40f, 28f, 30f, 28f, 30f)
        val JUICE = arrayOf("#ff5a6e", "#ffa53a", "#fff2a0", "#cfd6e2", "#ffe14d")
        val INNER = arrayOf("#ff4f62", "#ffc06a", "#fff6c9", "#5d6274", "#e8b81a")
        val GLYPHS = arrayOf("Q", "W", "E", "R", "Z", "X", "A", "?")

        val TOSSED = arrayOf("Don’t you DARE.", "Put me DOWN!", "Not the face!", "I bruise easily!", "Eyes on the fruit!")
        val SAFE = arrayOf("Phew.", "Still in one piece.", "Missed me. Good.")
        val MISSES = arrayOf("Ha. Missed one.", "Butterfingers.", "That melon was free.", "You call that a swipe?")
        val COMBOS = arrayOf("Okay, that was a bit cool.", "Show-off.", "Calm down, ninja.")
        val IDLE = arrayOf("I hate fruit.", "Swipe faster.", "That duck is mine, by the way.", "Who throws a keyboard key?")
        val HURT = arrayOf("YOU CUT ME?!", "MY FACE!!", "I KNEW IT!!", "BETRAYAL!!")
        val FAIL = arrayOf("Three strikes. Bye.", "Fruit wins.", "Pathetic.")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val juice = IntArray(5) { col(JUICE[it]) }

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    // things in the air
    private val iKind = IntArray(MAX_ITEMS)
    private val ix = FloatArray(MAX_ITEMS)
    private val iy = FloatArray(MAX_ITEMS)
    private val ivx = FloatArray(MAX_ITEMS)
    private val ivy = FloatArray(MAX_ITEMS)
    private val iAngle = FloatArray(MAX_ITEMS)
    private val iva = FloatArray(MAX_ITEMS)
    private val ir = FloatArray(MAX_ITEMS)
    private val iGlyph = IntArray(MAX_ITEMS)
    private val iHurt = BooleanArray(MAX_ITEMS)
    private var items = 0

    // sliced halves
    private val hKind = IntArray(MAX_HALVES)
    private val hx = FloatArray(MAX_HALVES)
    private val hy = FloatArray(MAX_HALVES)
    private val hvx = FloatArray(MAX_HALVES)
    private val hvy = FloatArray(MAX_HALVES)
    private val hAngle = FloatArray(MAX_HALVES)
    private val hva = FloatArray(MAX_HALVES)
    private val hInner = FloatArray(MAX_HALVES)
    private val hSide = FloatArray(MAX_HALVES)
    private val hGlyph = IntArray(MAX_HALVES)
    private var halves = 0

    // the blade trail, oldest first
    private val trX = FloatArray(MAX_TRAIL)
    private val trY = FloatArray(MAX_TRAIL)
    private val trLife = FloatArray(MAX_TRAIL)
    private var trail = 0

    // tosses waiting to happen
    private val qAt = FloatArray(MAX_QUEUE)
    private val qBob = BooleanArray(MAX_QUEUE)
    private var queue = 0

    // juice left on the board
    private val spX = FloatArray(MAX_SPLATS)
    private val spY = FloatArray(MAX_SPLATS)
    private val spKind = IntArray(MAX_SPLATS)
    private val spAngle = FloatArray(MAX_SPLATS)
    private val spSize = FloatArray(MAX_SPLATS)
    private val spLife = FloatArray(MAX_SPLATS)
    private var splats = 0

    // bright cut lines
    private val slX = FloatArray(MAX_SLASHES)
    private val slY = FloatArray(MAX_SLASHES)
    private val slAngle = FloatArray(MAX_SLASHES)
    private val slLife = FloatArray(MAX_SLASHES)
    private val slR = FloatArray(MAX_SLASHES)
    private var slashes = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var started = false
    private var t = 0f
    private var lives = LIVES
    private var held = false
    private var lastX = Float.NaN // the previous finger position while swiping
    private var lastY = Float.NaN
    private var hits = 0 // slices in the current swipe
    private var wave = 0f // seconds until the next wave
    private var chat = 4f
    private var hurt = false // the button got sliced
    private var shout = ""
    private var flash = 0f
    private var hurtX = W / 2f // where the cut button last was, for its shout
    private var hurtY = H / 2f

    private fun flyer(): Int {
        for (i in 0 until items) if (iKind[i] == BOB) return i
        return -1
    }

    private fun removeItem(i: Int) {
        for (j in i until items - 1) {
            iKind[j] = iKind[j + 1]
            ix[j] = ix[j + 1]
            iy[j] = iy[j + 1]
            ivx[j] = ivx[j + 1]
            ivy[j] = ivy[j + 1]
            iAngle[j] = iAngle[j + 1]
            iva[j] = iva[j + 1]
            ir[j] = ir[j + 1]
            iGlyph[j] = iGlyph[j + 1]
            iHurt[j] = iHurt[j + 1]
        }
        items--
    }

    private fun removeHalf(i: Int) {
        for (j in i until halves - 1) {
            hKind[j] = hKind[j + 1]
            hx[j] = hx[j + 1]
            hy[j] = hy[j + 1]
            hvx[j] = hvx[j + 1]
            hvy[j] = hvy[j + 1]
            hAngle[j] = hAngle[j + 1]
            hva[j] = hva[j + 1]
            hInner[j] = hInner[j + 1]
            hSide[j] = hSide[j + 1]
            hGlyph[j] = hGlyph[j + 1]
        }
        halves--
    }

    private fun toss(bobby: Boolean) {
        if (items >= MAX_ITEMS) return
        val x = rand(70f, W - 70f)
        val vy = -rand(1000f, 1150f)
        val apex = -vy / GRAVITY // seconds until it stops rising
        val kind = if (bobby) BOB else Random.nextInt(5)
        val i = items++
        iKind[i] = kind
        ix[i] = x
        iy[i] = H + 40f
        ivx[i] = (rand(110f, W - 110f) - x) / (apex * 1.6f)
        ivy[i] = vy
        iAngle[i] = rand(0f, TAU)
        iva[i] = rand(-3f, 3f)
        ir[i] = if (bobby) BOBBY * 0.48f else R[kind]
        iGlyph[i] = Random.nextInt(GLYPHS.size)
        iHurt[i] = false
        ctx.sfx.play("jump")
        if (bobby) mouth.say(pick(TOSSED), 1.4f)
    }

    private fun nextWave() {
        val n = min(1 + score / 10 + (if (Random.nextFloat() < 0.4f) 1 else 0), 6)
        val withBobby = score >= 4 && flyer() < 0 && Random.nextFloat() < min(0.15f + score / 300f, 0.35f)
        val who = if (withBobby) Random.nextInt(n) else -1
        var at = 0f
        for (i in 0 until n) {
            if (queue < MAX_QUEUE) {
                qAt[queue] = at
                qBob[queue] = i == who
                queue++
            }
            at += rand(0.12f, 0.4f)
        }
        wave = at + kotlin.math.max(0.8f, 2.1f - score * 0.022f)
    }

    private fun cutBobby(i: Int) {
        over = true
        hurt = true
        iHurt[i] = true
        flash = 0.2f
        ivy[i] = min(ivy[i], -320f)
        iva[i] = (if (iva[i] < 0f) -1f else 1f) * 14f
        shout = pick(HURT)
        ctx.sfx.play("crack")
        ctx.sfx.play("hit")
        ctx.haptics.thud()
        fx.burst(ix[i], iy[i], lookColor, 30, 380f)
        fx.burst(ix[i], iy[i], WHITE, 14, 260f, size = 2f)
        fx.ring(ix[i], iy[i], col("#ff8a7a"), 120f, 0.5f, 10f)
    }

    private fun cut(i: Int, angle: Float) {
        val kind = iKind[i]
        val x = ix[i]
        val y = iy[i]
        val r = ir[i]
        val nx = -sin(angle)
        val ny = cos(angle)
        for (s in 0 until 2) {
            val side = if (s == 0) -1f else 1f
            if (halves >= MAX_HALVES) removeHalf(0)
            val h = halves++
            hKind[h] = kind
            hx[h] = x + nx * side * 4f
            hy[h] = y + ny * side * 4f
            hvx[h] = ivx[i] * 0.6f + nx * side * 130f
            hvy[h] = min(ivy[i], 0f) * 0.4f + ny * side * 130f - 80f
            hAngle[h] = angle
            hva[h] = side * rand(2f, 4f)
            hInner[h] = iAngle[i] - angle
            hSide[h] = side
            hGlyph[h] = iGlyph[i]
        }
        fx.burst(x, y, juice[kind], 16, 320f, gravity = 700f)
        fx.burst(x, y, WHITE, 5, 200f, size = 1.6f, life = 0.3f)
        if (splats >= MAX_SPLATS) shiftSplat()
        spX[splats] = x
        spY[splats] = y
        spKind[splats] = kind
        spAngle[splats] = rand(0f, TAU)
        spSize[splats] = r * rand(1.6f, 2.2f)
        spLife[splats] = 3f
        splats++
        if (slashes < MAX_SLASHES) {
            slX[slashes] = x
            slY[slashes] = y
            slAngle[slashes] = angle
            slLife[slashes] = 0.22f
            slR[slashes] = r * 1.9f
            slashes++
        }
        removeItem(i)
        ctx.sfx.play("pop")
        ctx.haptics.tap()
        score++
        hits++
    }

    private fun shiftSplat() {
        for (j in 0 until splats - 1) {
            spX[j] = spX[j + 1]
            spY[j] = spY[j + 1]
            spKind[j] = spKind[j + 1]
            spAngle[j] = spAngle[j + 1]
            spSize[j] = spSize[j + 1]
            spLife[j] = spLife[j + 1]
        }
        splats--
    }

    private fun shiftSlash() {
        for (j in 0 until slashes - 1) {
            slX[j] = slX[j + 1]
            slY[j] = slY[j + 1]
            slAngle[j] = slAngle[j + 1]
            slLife[j] = slLife[j + 1]
            slR[j] = slR[j + 1]
        }
        slashes--
    }

    private fun shiftTrail() {
        for (j in 0 until trail - 1) {
            trX[j] = trX[j + 1]
            trY[j] = trY[j + 1]
            trLife[j] = trLife[j + 1]
        }
        trail--
    }

    private fun addTrail(x: Float, y: Float) {
        if (trail >= MAX_TRAIL) shiftTrail()
        trX[trail] = x
        trY[trail] = y
        trLife[trail] = TRAIL
        trail++
    }

    // The blade moved from (ax, ay) to (bx, by): slice whatever it crossed.
    private fun slash(ax: Float, ay: Float, bx: Float, by: Float) {
        val len = hypot(bx - ax, by - ay)
        if (!(len >= 1f)) return
        val angle = atan2(by - ay, bx - ax)
        for (i in items - 1 downTo 0) {
            if (i >= items) continue
            // distance from the item to the nearest point on the segment
            val k = clamp(((ix[i] - ax) * (bx - ax) + (iy[i] - ay) * (by - ay)) / (len * len), 0f, 1f)
            val d = hypot(ax + (bx - ax) * k - ix[i], ay + (by - ay) * k - iy[i])
            if (d > ir[i] * 0.9f) continue
            if (iKind[i] == BOB) {
                cutBobby(i)
                return
            }
            cut(i, angle)
        }
    }

    // A swipe ended: three or more slices in one go earn a bonus.
    private fun endSwipe() {
        if (hits >= COMBO && !over) {
            score += hits
            val x = if (lastX.isNaN()) W / 2f else lastX
            val y = if (lastY.isNaN()) H / 2f else lastY
            fx.float(x, y - 30f, "Combo x$hits! +$hits", col("#fff6a8"))
            ctx.sfx.play("coin")
            mouth.maybe(COMBOS, 0.5f)
        }
        hits = 0
    }

    private fun lose(x: Float) {
        lives--
        ctx.sfx.play("bad")
        ctx.haptics.tap()
        fx.float(clamp(x, 40f, W - 40f), H - 60f, "×", col("#ff5a6e"))
        if (lives <= 0) {
            over = true
            ctx.sfx.play("sob")
            mouth.say(pick(FAIL), 3f)
        } else {
            mouth.maybe(MISSES, 0.6f)
        }
    }

    override fun down(x: Float, y: Float) {
        if (over) return
        if (!started) {
            started = true
            wave = 0.6f
        }
        held = true
        hits = 0
        lastX = x
        lastY = y
        addTrail(x, y)
    }

    override fun move(x: Float, y: Float) {
        if (!held || over || lastX.isNaN()) return
        slash(lastX, lastY, x, y)
        lastX = x
        lastY = y
        addTrail(x, y)
    }

    override fun up(x: Float, y: Float) {
        if (!held) return
        held = false
        endSwipe()
    }

    // a paid Continue: a life back, everything in the air (the button too)
    // cleared away, and a short breather before the next wave
    override fun revive() {
        over = false
        lives = kotlin.math.max(lives, 1)
        for (i in 0 until items) fx.burst(ix[i], iy[i], col("#cfefff"), 8, 180f)
        items = 0
        queue = 0
        hurt = false
        shout = ""
        flash = 0f
        held = false
        hits = 0
        wave = 1.4f
        reviveFx(fx, mouth, ctx, W / 2f, H / 2f, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        for (i in 0 until splats) spLife[i] -= dt
        while (splats > 0 && spLife[0] <= 0f) shiftSplat()
        for (i in 0 until slashes) slLife[i] -= dt
        while (slashes > 0 && slLife[0] <= 0f) shiftSlash()
        flash = kotlin.math.max(0f, flash - dt)
        for (i in 0 until trail) trLife[i] -= dt
        while (trail > 0 && trLife[0] <= 0f) shiftTrail()

        for (i in 0 until items) {
            ivy[i] += GRAVITY * dt
            ix[i] += ivx[i] * dt
            iy[i] += ivy[i] * dt
            iAngle[i] += iva[i] * dt
        }
        for (i in 0 until halves) {
            hvy[i] += GRAVITY * dt
            hx[i] += hvx[i] * dt
            hy[i] += hvy[i] * dt
            hAngle[i] += hva[i] * dt
        }
        for (i in halves - 1 downTo 0) if (hy[i] > H + 80f || hy[i].isNaN()) removeHalf(i)

        // whatever falls back off the bottom unsliced
        for (i in items - 1 downTo 0) {
            if (iy[i].isNaN()) {
                removeItem(i)
                continue
            }
            if (ivy[i] < 0f || iy[i] < H + 60f) continue
            val kind = iKind[i]
            val x = ix[i]
            removeItem(i)
            if (over) continue
            if (kind == BOB) mouth.say(pick(SAFE), 1.4f) else lose(x)
        }
        if (!started || over) return

        wave -= dt
        if (wave <= 0f) nextWave()
        for (i in 0 until queue) qAt[i] -= dt
        while (queue > 0 && qAt[0] <= 0f) {
            val bob = qBob[0]
            for (j in 0 until queue - 1) {
                qAt[j] = qAt[j + 1]
                qBob[j] = qBob[j + 1]
            }
            queue--
            toss(bob)
        }

        chat -= dt
        if (chat <= 0f) {
            chat = rand(5f, 9f)
            mouth.maybe(IDLE, 0.6f)
        }
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.sprite(art.board, 0f, 0f, W, H)
        // juice soaks into the board and fades
        for (i in 0 until splats) {
            val s = spSize[i]
            c.save()
            c.translate(spX[i], spY[i])
            c.rotate(spAngle[i] * DEG)
            c.sprite(art.splat(spKind[i], juice[spKind[i]]), -s, -s, s * 2f, s * 2f, alpha = clamp(spLife[i] / 3f, 0f, 1f) * 0.5f)
            c.restore()
        }

        // everything in the air casts a soft shadow on the board below
        for (i in 0 until items) c.shadow(ix[i] + 14f, iy[i] + 26f, ir[i] * 1.05f, 0.4f, ir[i] * 0.6f)
        for (i in 0 until halves) {
            val r = R[hKind[i]]
            c.shadow(hx[i] + 14f, hy[i] + 26f, r * 0.9f, 0.3f, r * 0.45f)
        }

        for (i in 0 until halves) half(c, i)
        for (i in 0 until items) {
            if (iKind[i] == BOB) {
                val hit = iHurt[i]
                bobby(c, ix[i], iy[i], BOBBY, look, if (hit) Mood.Angry else Mood.Worried, t, flash = flash > 0f && hit, tears = hit, angle = iAngle[i])
                if (hit) plaster(c, ix[i], iy[i], iAngle[i])
            } else {
                c.save()
                c.translate(ix[i], iy[i])
                c.rotate(iAngle[i] * DEG)
                drawItem(c, iKind[i], iGlyph[i])
                c.restore()
            }
        }

        // the blade trail: a glowing ribbon, thick and bright at the tip
        for (i in 1 until trail) {
            val k = clamp(trLife[i] / TRAIL, 0f, 1f)
            val ax = trX[i - 1]
            val ay = trY[i - 1]
            val bx = trX[i]
            val by = trY[i]
            blade.color = rgba(80, 170, 255, 0.22f * k)
            blade.strokeWidth = 6f + 22f * k
            c.drawLine(ax, ay, bx, by, blade)
            blade.color = rgba(170, 225, 255, 0.5f * k)
            blade.strokeWidth = 3f + 9f * k
            c.drawLine(ax, ay, bx, by, blade)
            blade.color = rgba(255, 255, 255, k)
            blade.strokeWidth = 1f + 4f * k
            c.drawLine(ax, ay, bx, by, blade)
        }
        if (trail > 0 && held) c.glow(trX[trail - 1], trY[trail - 1], 26f, TIP, clamp(trLife[trail - 1] / TRAIL, 0f, 1f))
        // a white-hot line where something was just cut
        for (i in 0 until slashes) {
            val k = clamp(slLife[i] / 0.22f, 0f, 1f)
            val dx = cos(slAngle[i]) * slR[i] * (1.4f - k * 0.4f)
            val dy = sin(slAngle[i]) * slR[i] * (1.4f - k * 0.4f)
            blade.color = rgba(255, 255, 255, k)
            blade.strokeWidth = 2f + 5f * k
            c.drawLine(slX[i] - dx, slY[i] - dy, slX[i] + dx, slY[i] + dy, blade)
            c.glow(slX[i], slY[i], slR[i] * 1.2f, WHITE, k * 0.6f)
        }

        fx.draw(c)

        // the button peeks up from the corner, unless it is the one in the air
        val up = flyer()
        if (up >= 0) {
            if (!hurt) mouth.draw(c, ix[up], iy[up] - BOBBY * 0.6f)
        } else if (!hurt) {
            val mood = when {
                lives < LIVES && mouth.line != null -> Mood.Smug
                over -> Mood.Happy
                else -> Mood.Calm
            }
            bobby(c, W - 48f, H - 24f, 64f, look, mood, t)
            mouth.draw(c, W - 48f, H - 64f)
        }
        if (hurt) {
            if (up >= 0) {
                hurtX = ix[up]
                hurtY = iy[up]
            }
            c.bubble(clamp(hurtX, 60f, W - 60f), clamp(hurtY - BOBBY * 0.6f, 220f, H - 120f), shout, 30f, HURT_BG, HURT_INK)
        }

        for (i in 0 until LIVES) {
            val lx = W / 2f + (i - 1) * 30f
            if (i < lives) c.glow(lx, 150f, 22f, HEART, 0.5f)
            c.heart(lx, 150f, 10f, if (i < lives) HEART else HEART_GONE)
        }
        if (flash > 0f || hurt) c.vignette(if (hurt) 0.35f + flash * 2f else flash * 3f, RED)

        if (started) c.drawScore(score) else c.drawScore("Swipe to start", "Slice everything. Except me.")
    }

    private fun drawItem(c: Canvas, kind: Int, glyph: Int) {
        val s = art.item(kind, glyph)
        c.sprite(s, -s.w / 2f, -s.h / 2f)
    }

    // Half of a sliced item: the item clipped along the cut, showing its inside.
    private fun half(c: Canvas, i: Int) {
        val kind = hKind[i]
        val r = R[kind]
        c.save()
        c.translate(hx[i], hy[i])
        c.rotate(hAngle[i] * DEG)
        if (hSide[i] < 0f) c.clipRect(-r * 2f, -r * 2f, r * 2f, 0f) else c.clipRect(-r * 2f, 0f, r * 2f, r * 2f)
        c.save()
        c.rotate(hInner[i] * DEG)
        drawItem(c, kind, hGlyph[i])
        c.restore()
        // the juicy cut face
        val face = art.face(kind)
        c.sprite(face, -face.w / 2f, -face.h / 2f)
        if (kind == 0) {
            for (k in floatArrayOfSeeds) c.drawCircle(k * r, hSide[i] * 3f, 2f, seed)
        }
        c.restore()
    }

    // A sticking plaster across the button's face, for when it got cut.
    private fun plaster(c: Canvas, x: Float, y: Float, angle: Float) {
        c.save()
        c.translate(x, y)
        c.rotate((angle + 0.6f) * DEG)
        rect.set(-24f, -6f, 24f, 6f)
        c.drawRoundRect(rect, 5f, 5f, plasterFill)
        c.drawRect(-6f, -5f, 6f, 5f, plasterPad)
        c.drawRect(-22f, -5f, 22f, -3f, plasterShine)
        c.restore()
    }

    /* ---------- art ---------- */

    private val floatArrayOfSeeds = floatArrayOf(-0.5f, -0.15f, 0.2f, 0.55f)
    private val TIP = col("#9fd8ff")
    private val HEART = col("#ff4f7b")
    private val HEART_GONE = rgba(255, 255, 255, 0.25f)
    private val RED = col("#d01a20")
    private val HURT_BG = col("#ffe9e6")
    private val HURT_INK = col("#b3202a")
    private val blade = stroke(WHITE, 4f).apply { xfermode = ADD }
    private val seed = fill(col("#2b1d17"))
    private val plasterFill = fill(col("#f5c9a0"))
    private val plasterPad = fill(col("#e8a77a"))
    private val plasterShine = fill(rgba(255, 255, 255, 0.4f))
    private val rect = RectF()
    private val art = Art()

    private class Art {
        // The chopping board: dark oiled planks with grain, a warm pool of light
        // from above and deep corners. Painted once.
        val board by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, W, H, 0f to col("#5a3520"), 1f to col("#3a2213"))))
                val line = stroke(WHITE, 1f)
                for (p in 0 until 6) {
                    val fp = p.toFloat()
                    val x0 = fp * 80f
                    // each plank a slightly different tone
                    val tone = 0.03f + hash(fp) * 0.05f
                    c.drawRect(x0, 0f, x0 + 80f, H, fill(if (p % 2 == 1) rgba(255, 200, 150, tone) else rgba(0, 0, 0, tone)))
                    // wavy grain lines
                    for (i in 0 until 16) {
                        val fi = i.toFloat()
                        val gx = x0 + 4f + hash(fp * 20f + fi) * 72f
                        line.color = if (hash(fp * 31f + fi) > 0.5f) rgba(20, 8, 2, 0.28f) else rgba(255, 190, 130, 0.08f)
                        val path = Path()
                        var y = 0f
                        while (y <= H) {
                            val x = gx + sin(y * 0.012f + fi + fp) * 3f + sin(y * 0.05f + fi) * 0.8f
                            if (y == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                            y += 20f
                        }
                        c.drawPath(path, line)
                    }
                    // a knot or two
                    for (k in 0 until 2) {
                        if (hash(fp * 7f + k) < 0.5f) continue
                        val kx = x0 + 20f + hash(fp + k * 3f) * 40f
                        val ky = hash(fp * 3f + k) * H
                        line.color = rgba(20, 8, 2, 0.25f)
                        var r = 3f
                        while (r < 14f) {
                            c.drawOval(RectF(kx - r * 0.7f, ky - r * 1.6f, kx + r * 0.7f, ky + r * 1.6f), line)
                            r += 3f
                        }
                    }
                    // knife scratches
                    line.color = rgba(255, 220, 180, 0.07f)
                    for (i in 0 until 5) {
                        val sx = x0 + hash(fp * 9f + i) * 80f
                        val sy = hash(fp * 13f + i) * H
                        val a = hash(fp + i * 5f) * PI.toFloat()
                        c.drawLine(sx, sy, sx + cos(a) * 40f, sy + sin(a) * 40f, line)
                    }
                    // the seam between planks: a dark gap with a lit edge
                    c.drawRect(x0 - 1.5f, 0f, x0 + 1.5f, H, fill(rgba(10, 4, 0, 0.55f)))
                    c.drawRect(x0 + 1.5f, 0f, x0 + 3f, H, fill(rgba(255, 200, 150, 0.08f)))
                }
                // a warm overhead light
                c.drawRect(0f, 0f, W, H, fill().shade(rad(W / 2f, H * 0.42f, 20f, H * 0.65f, 0f to rgba(255, 200, 140, 0.32f), 0.5f to rgba(255, 170, 110, 0.1f), 1f to rgba(255, 170, 110, 0f))))
                c.drawRect(0f, 0f, W, H, fill().shade(rad(W / 2f, H * 0.48f, H * 0.25f, H * 0.75f, 0f to rgba(0, 0, 0, 0f), 1f to rgba(8, 3, 0, 0.7f))))
            }
        }

        private val splats = arrayOfNulls<Sprite>(5)

        // A juice splat left on the board.
        fun splat(kind: Int, color: Int): Sprite = splats[kind] ?: makeSprite(128f, 128f, 1f) { c ->
            c.translate(64f, 64f)
            val p = fill(color)
            val path = Path()
            for (i in 0..24) {
                val a = (i / 24f) * TAU
                val r = 26f + hash(i + 7f + kind) * 14f
                if (i == 0) path.moveTo(cos(a) * r, sin(a) * r) else path.lineTo(cos(a) * r, sin(a) * r)
            }
            path.close()
            c.drawPath(path, p)
            for (i in 0 until 9) {
                val a = hash(i * 3f) * TAU
                val d = 40f + hash(i * 5f) * 20f
                c.drawCircle(cos(a) * d, sin(a) * d, 2f + hash(i * 7f) * 5f, p)
            }
            // wet highlight
            c.save()
            c.rotate(-0.5f * DEG, -8f, -10f)
            c.drawOval(RectF(-20f, -16f, 4f, -4f), fill(rgba(255, 255, 255, 0.18f)))
            c.restore()
        }.also { splats[kind] = it }

        private val items = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()

        // An item's sprite, centered, unrotated (cached per kind, and glyph for keys).
        fun item(kind: Int, glyph: Int): Sprite {
            val key = kind * 100 + (if (kind == 3) glyph else 0)
            return items.getOrPut(key) {
                val r = R[kind]
                val size = (r * 2f + 20f) * (if (kind == 2) 1.5f else 1f)
                makeSprite(size, size) { c ->
                    c.translate(size / 2f, size / 2f)
                    paintItem(c, kind, GLYPHS[glyph])
                }
            }
        }

        private val faces = arrayOfNulls<Sprite>(5)

        // The juicy cut face of a half: a flat ellipse, white at the core.
        fun face(kind: Int): Sprite = faces[kind] ?: run {
            val r = R[kind]
            val inner = col(INNER[kind])
            makeSprite(r * 1.7f, r * 0.4f) { c ->
                c.drawOval(
                    RectF(0f, 0f, r * 1.7f, r * 0.4f),
                    fill().shade(rad(r * 0.85f, r * 0.2f, 0f, r * 0.85f, 0f to WHITE, 0.25f to inner, 1f to inner)),
                )
            }
        }.also { faces[kind] = it }

        // A round body with a lit side, a dark side and a soft specular spot.
        private fun ball(c: Canvas, r: Float, hi: String, mid: String, lo: String) {
            c.drawCircle(
                0f, 0f, r,
                fill().shade(
                    android.graphics.RadialGradient(
                        -r * 0.35f, -r * 0.4f, r * 1.4f,
                        intArrayOf(col(hi), col(mid), col(lo)),
                        floatArrayOf(0.07f, 0.5f, 0.9f),
                        android.graphics.Shader.TileMode.CLAMP,
                    ),
                ),
            )
        }

        private fun shine(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 0.6f) {
            c.save()
            c.rotate(-0.6f * DEG, x, y)
            c.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), fill().shade(rad(x, y, 0f, rx, 0f to rgba(255, 255, 255, a), 1f to rgba(255, 255, 255, 0f))))
            c.restore()
        }

        // a warm rim light along the lower right edge
        private fun rim(c: Canvas, r: Float, color: Int = rgba(255, 190, 120, 0.5f)) {
            c.drawArc(RectF(-r + 1f, -r + 1f, r - 1f, r - 1f), -0.3f * DEG, 1.9f * DEG, false, stroke(color, 2f))
        }

        private fun paintItem(c: Canvas, kind: Int, glyph: String) {
            val r = R[kind]
            when (kind) {
                0 -> { // melon
                    ball(c, r, "#7fd36a", "#3a9a38", "#174a1a")
                    c.save()
                    val clip = Path()
                    clip.addCircle(0f, 0f, r, Path.Direction.CW)
                    c.clipPath(clip)
                    val stripe = stroke(rgba(16, 60, 20, 0.75f), 5f)
                    for (k in floatArrayOf(-0.75f, -0.25f, 0.25f, 0.75f)) {
                        val p = Path()
                        var y = -r
                        while (y <= r) {
                            val q = 1f - (y / r) * (y / r)
                            val x = k * r * sqrt(kotlin.math.max(0f, q)) + sin(y * 0.5f) * 1.5f
                            if (y == -r) p.moveTo(x, y) else p.lineTo(x, y)
                            y += 4f
                        }
                        c.drawPath(p, stripe)
                    }
                    c.restore()
                    shine(c, -r * 0.38f, -r * 0.42f, r * 0.4f, r * 0.24f, 0.55f)
                    rim(c, r, rgba(190, 255, 170, 0.35f))
                }
                1 -> { // orange
                    ball(c, r, "#ffd08a", "#ff9418", "#b44a00")
                    val dimple = fill(rgba(150, 60, 0, 0.3f))
                    for (i in 0 until 26) {
                        val a = hash(i.toFloat()) * TAU
                        val d = sqrt(hash(i + 9f)) * r * 0.85f
                        c.drawCircle(cos(a) * d, sin(a) * d, 1.1f, dimple)
                    }
                    shine(c, -r * 0.35f, -r * 0.4f, r * 0.42f, r * 0.26f, 0.65f)
                    rim(c, r)
                    c.save()
                    c.rotate(-0.4f * DEG, 6f, -r + 2f)
                    c.drawOval(RectF(6f - 9f, -r + 2f - 4.5f, 6f + 9f, -r + 2f + 4.5f), fill().shade(lin(0f, -r - 4f, 0f, -r + 6f, 0f to col("#8fd06a"), 1f to col("#2f7a2c"))))
                    c.restore()
                    c.drawRect(-1.5f, -r - 1f, 1.5f, -r + 4f, fill(col("#5a3a1a")))
                }
                2 -> { // banana
                    c.save()
                    c.translate(0f, -r * 0.2f)
                    val outer = RectF(-r * 1.6f, -r - r * 1.6f, r * 1.6f, -r + r * 1.6f)
                    val inner = RectF(-r * 1.5f, -r * 1.35f - r * 1.5f, r * 1.5f, -r * 1.35f + r * 1.5f)
                    val path = Path()
                    path.arcTo(outer, 0.22f * 180f, (0.78f - 0.22f) * 180f, true)
                    path.arcTo(inner, 0.76f * 180f, -(0.76f - 0.24f) * 180f)
                    path.close()
                    c.drawPath(path, fill().shade(lin(0f, r * 0.1f, 0f, r * 0.7f, 0f to col("#fff3a0"), 0.5f to col("#ffd52a"), 1f to col("#c99a12"))))
                    c.drawPath(path, stroke(col("#a07a10"), 1.5f))
                    val hl = RectF(-r * 1.5f - 3f, -r * 1.35f - r * 1.5f - 3f, r * 1.5f + 3f, -r * 1.35f + r * 1.5f + 3f)
                    c.drawArc(hl, 0.35f * 180f, (0.62f - 0.35f) * 180f, false, stroke(rgba(255, 255, 230, 0.75f), 2.5f))
                    val tip = fill(col("#4e3414"))
                    val a1 = PI.toFloat() * 0.22f
                    val a2 = PI.toFloat() * 0.78f
                    c.drawCircle(cos(a1) * r * 1.6f, -r + sin(a1) * r * 1.6f, 3f, tip)
                    c.drawCircle(cos(a2) * r * 1.6f, -r + sin(a2) * r * 1.6f, 3f, tip)
                    c.restore()
                }
                3 -> { // a keycap: dark sides, a dished top face, the letter
                    c.drawRoundRect(RectF(-r * 0.8f, -r * 0.7f, r * 0.8f, r * 0.9f), 8f, 8f, fill().shade(lin(0f, -r * 0.7f, 0f, r * 0.9f, 0f to col("#b9bdcb"), 1f to col("#5f6372"))))
                    c.drawRoundRect(RectF(-r * 0.65f, -r * 0.8f, r * 0.65f, r * 0.5f), 7f, 7f, fill().shade(lin(0f, -r * 0.8f, 0f, r * 0.5f, 0f to WHITE, 1f to col("#d9dce6"))))
                    c.drawLine(-r * 0.5f, -r * 0.78f, r * 0.5f, -r * 0.78f, stroke(rgba(255, 255, 255, 0.9f), 1.5f))
                    Txt.draw(c, glyph, 0f, -r * 0.15f, 22f, col("#3a3d4a"))
                }
                else -> { // duck
                    val body = fill().shade(rad(-r * 0.4f, -r * 0.5f, 2f, r * 1.2f, 0f to col("#fff6a0"), 0.5f to col("#ffd21f"), 1f to col("#d99a00")))
                    val p = Path()
                    p.addOval(RectF(-r, r * 0.3f - r * 0.6f, r, r * 0.3f + r * 0.6f), Path.Direction.CW)
                    p.addCircle(-r * 0.35f, -r * 0.35f, r * 0.48f, Path.Direction.CW)
                    c.drawPath(p, body)
                    c.drawOval(RectF(-r * 0.9f - r * 0.25f, -r * 0.25f - r * 0.12f, -r * 0.9f + r * 0.25f, -r * 0.25f + r * 0.12f), fill(col("#ff8a1f")))
                    c.drawCircle(-r * 0.45f, -r * 0.45f, 3f, fill(col("#2b1d17")))
                    c.drawCircle(-r * 0.48f, -r * 0.5f, 1f, fill(WHITE))
                    c.save()
                    c.rotate(0.3f * DEG, r * 0.25f, r * 0.2f)
                    c.drawOval(RectF(r * 0.25f - r * 0.4f, r * 0.2f - r * 0.2f, r * 0.25f + r * 0.4f, r * 0.2f + r * 0.2f), fill(col("#eaa800")))
                    c.restore()
                    shine(c, -r * 0.5f, -r * 0.55f, r * 0.22f, r * 0.14f, 0.8f)
                    shine(c, r * 0.1f, r * 0.05f, r * 0.3f, r * 0.12f, 0.45f)
                }
            }
        }
    }
}
