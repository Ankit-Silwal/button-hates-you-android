package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Cursor Pong: the ball is an angry mouse cursor. The button wears the paddle
// as a hat and has to bounce it back off the walls and ceiling. Three lives.
// Port of src/adventure/games/pong.js.
class PongGame(private val ctx: GameContext) : Game {
    private companion object {
        const val LEFT = 26f
        const val RIGHT = 454f
        const val TOP = 172f // ceiling: the window's inner top edge
        const val PY = 680f // paddle centre line
        const val PH = 18f
        const val R = 13f // ball radius
        const val MISS_Y = 790f
        const val LIVES = 3
        const val MAX_RIPPLES = 12
        const val TRAIL = 8

        // the cursor sprite: 48x56 units, the arrow's centre at (CX, CY)
        const val CS = 1.9f
        const val CX = 19.5f
        const val CY = 27f

        val WALL = arrayOf("Click me!", "Is that all?", "Boing.", "Try me.", "Double click this.")
        val OUCH = arrayOf("Ow.", "Rude.", "My pixels!", "Hey!", "Ow. Again.")
        val ESCAPE = arrayOf("Free at last!", "Bye!", "Seeya, button.", "Not my problem now.")
        val END = arrayOf("Click me! Oh wait.", "I always win.", "Task failed successfully.")
        val CHEAT = arrayOf("Cheater.", "That is not fair.", "Hey, power-ups?!")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val lookDark = look.dark.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val fx = Fx()
    private val mouth = Mouth()

    private val rpX = FloatArray(MAX_RIPPLES)
    private val rpY = FloatArray(MAX_RIPPLES)
    private val rpAge = FloatArray(MAX_RIPPLES) { 1f }
    private var rpNext = 0

    private val trX = FloatArray(TRAIL)
    private val trY = FloatArray(TRAIL)
    private var trN = 0 // how many trail points (oldest first, shifted)

    // the one power-up that can be falling at a time
    private var upOn = false
    private var upX = 0f
    private var upY = 0f
    private var upDonut = true
    private var upAge = 0f

    private var talkX = W / 2f
    private var talkY = 240f
    private var pin = false

    private var ballX = W / 2f
    private var ballY = 300f
    private var vx = 0f
    private var vy = 0f
    private var wait = 0.8f
    private var ballScale = 1f

    private var t = 0f
    private var shake = 0f
    private var lives = LIVES
    private var returns = 0
    private var speed = 360f
    private var px = W / 2f
    private var tx = W / 2f
    private var pw = 150f
    private var wide = 0f // seconds of donut left
    private var slow = 0f // seconds of slow-mo left
    private var dip = 0f // paddle bounce after a hit
    private var glad = 0f
    private var sadT = 0f
    private var ouchT = 0f
    private var upIn = 7f
    private var trailT = 0f

    private fun baseW() = max(84f, 150f - returns * 1.4f)
    private fun maxV() = min(640f, 360f + returns * 7f)

    init {
        serve(360f, 0.8f)
    }

    private fun serve(sp: Float, w: Float) {
        ballX = W / 2f + rand(-40f, 40f)
        ballY = 300f
        val a = rand(-0.2f, 0.2f)
        vx = sin(a) * sp
        vy = cos(a) * sp
        wait = w
        trN = 0
    }

    private fun say(lines: Array<String>, chance: Float = 1f, sec: Float = 1.3f) {
        if (chance < 1f && (mouth.left > 0f || Random.nextFloat() >= chance)) return
        mouth.say(pick(lines), sec)
    }

    private fun ripple(x: Float, y: Float) {
        rpX[rpNext] = x
        rpY[rpNext] = y
        rpAge[rpNext] = 0f
        rpNext = (rpNext + 1) % MAX_RIPPLES
    }

    private fun lose() {
        lives--
        shake = 0.4f
        sadT = 1.2f
        ctx.sfx.play("bad")
        ctx.haptics.thud()
        val x = clamp(ballX, LEFT, RIGHT)
        fx.burst(x, MISS_Y - 30f, col("#ff5a5a"), 14, 280f)
        fx.ring(x, MISS_Y - 30f, col("#ff8a8a"), 90f, 0.4f)
        if (lives <= 0) {
            over = true
            mouth.say(pick(END), 3f)
            pin = true
            talkX = W / 2f
            talkY = 260f
            return
        }
        mouth.say(pick(ESCAPE), 1.6f)
        pin = true
        talkX = clamp(x, 70f, W - 70f)
        talkY = 735f
        serve(min(speed, 400f), 1f)
    }

    private fun wallHit(x: Float, y: Float) {
        ripple(x, y)
        ballScale = 1.25f
        ctx.sfx.play("click")
        say(WALL, 0.1f)
    }

    private fun paddleHit() {
        returns++
        score++
        speed = maxV()
        val off = clamp((ballX - px) / (pw / 2f), -1f, 1f)
        val a = off * 1.0f
        vx = sin(a) * speed
        vy = -cos(a) * speed
        ballY = PY - PH / 2f - R
        ballScale = 1.4f
        dip = 0.14f
        glad = 0.4f
        ouchT = 0.28f
        shake = max(shake, 0.06f)
        ripple(ballX, ballY + R)
        fx.burst(ballX, ballY + R, lookColor, 8, 260f)
        fx.burst(ballX, ballY + R, WHITE, 5, 200f, size = 2f)
        fx.float(ballX, ballY - 24f, "+1")
        ctx.sfx.play("pop")
        ctx.haptics.tap()
        if (returns % 10 == 0) {
            fx.ring(ballX, ballY, col("#ffd84d"), 100f, 0.5f)
            ctx.sfx.play("coin")
            mouth.say("Is that all?", 1.4f)
        } else {
            say(OUCH, 0.14f)
        }
    }

    private fun step(h: Float) {
        ballX += vx * h
        ballY += vy * h
        if (ballX < LEFT + R) {
            ballX = LEFT + R
            vx = abs(vx)
            wallHit(LEFT, ballY)
        } else if (ballX > RIGHT - R) {
            ballX = RIGHT - R
            vx = -abs(vx)
            wallHit(RIGHT, ballY)
        }
        if (ballY < TOP + R) {
            ballY = TOP + R
            vy = abs(vy)
            wallHit(ballX, TOP)
        }
        val top = PY - PH / 2f
        if (vy > 0f && ballY + R >= top && ballY - R <= top + PH && abs(ballX - px) <= pw / 2f + 6f) paddleHit()
        if (ballY > MISS_Y) lose()
    }

    private fun catchUp() {
        fx.burst(upX, upY, if (upDonut) col("#ff7fb0") else col("#6fb4ff"), 12, 280f)
        ctx.sfx.play("coin")
        ctx.haptics.tap()
        if (upDonut) {
            wide = 8f
            fx.float(upX, upY - 20f, "WIDE!", col("#ff9cc4"))
        } else {
            slow = 5f
            fx.float(upX, upY - 20f, "SLOW-MO", col("#9fd0ff"))
        }
        say(CHEAT, 0.5f)
    }

    override fun down(x: Float, y: Float) {
        if (!over && !x.isNaN()) tx = x
    }

    override fun move(x: Float, y: Float) {
        if (!over && !x.isNaN()) tx = x
    }

    // a paid Continue: one more life, the cursor starts again high and slow
    override fun revive() {
        over = false
        lives = 1
        slow = 0f
        sadT = 0f
        serve(340f, 1f)
        pin = false
        reviveFx(fx, mouth, ctx, W / 2f, 330f, lookColor)
    }

    override fun update(dt0: Float) {
        val dt = if (dt0.isNaN()) 0.016f else clamp(dt0, 0f, 0.05f)
        t += dt
        fx.update(dt)
        mouth.update(dt)
        shake = max(0f, shake - dt)
        dip = max(0f, dip - dt)
        glad = max(0f, glad - dt)
        sadT = max(0f, sadT - dt)
        ouchT = max(0f, ouchT - dt)
        ballScale += (1f - ballScale) * min(1f, dt * 12f)
        if (mouth.left <= 0f) pin = false
        for (i in 0 until MAX_RIPPLES) rpAge[i] += dt
        if (over) return

        // the paddle chases the finger
        wide = max(0f, wide - dt)
        slow = max(0f, slow - dt)
        pw += ((baseW() + (if (wide > 0f) 56f else 0f)) - pw) * min(1f, dt * 8f)
        tx = clamp(tx, LEFT + pw / 2f, RIGHT - pw / 2f)
        px += (tx - px) * min(1f, dt * 22f)
        px = clamp(px, LEFT + pw / 2f, RIGHT - pw / 2f)

        // the cursor, in sub-steps so it can never tunnel through the paddle
        if (wait > 0f) {
            wait -= dt
        } else {
            val left = dt * (if (slow > 0f) 0.6f else 1f)
            val n = clamp(ceil(hypot(vx, vy) * left / 5f), 1f, 200f).toInt()
            for (i in 0 until n) {
                if (over) break
                val before = lives
                step(left / n)
                if (lives != before) break
            }
        }
        if (ballX.isNaN() || ballY.isNaN() || vx.isNaN() || vy.isNaN()) serve(360f, 0.6f)

        trailT -= dt
        if (trailT <= 0f && wait <= 0f) {
            trailT = 0.025f
            if (trN == TRAIL) {
                for (i in 1 until TRAIL) {
                    trX[i - 1] = trX[i]
                    trY[i - 1] = trY[i]
                }
                trN--
            }
            trX[trN] = ballX
            trY[trN] = ballY
            trN++
        }

        // power-ups drift down from the ceiling
        upIn -= dt
        if (upIn <= 0f && wait <= 0f) {
            upIn = rand(8f, 13f)
            if (!upOn) {
                upOn = true
                upX = rand(60f, W - 60f)
                upY = TOP + 14f
                upDonut = Random.nextFloat() < 0.5f
                upAge = 0f
            }
        }
        if (upOn) {
            upAge += dt
            upY += 150f * dt
            if (abs(upX - px) < pw / 2f + 14f && upY + 14f > PY - PH / 2f && upY - 14f < PY + PH / 2f + 20f) {
                upOn = false
                catchUp()
            } else if (upY > H) {
                upOn = false
            }
        }
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.save()
        if (shake > 0f) c.translate(rand(-1f, 1f) * shake * 16f, rand(-1f, 1f) * shake * 16f)
        c.sprite(art.desk, 0f, 0f, W, H)

        // click ripples
        for (i in 0 until MAX_RIPPLES) {
            val k = rpAge[i] / 0.5f
            if (k >= 1f) continue
            ring.color = rgba(40, 120, 220, (1f - k) * 0.8f)
            ring.strokeWidth = 2.5f * (1f - k) + 0.5f
            c.drawCircle(rpX[i], rpY[i], 4f + k * 30f, ring)
            c.drawCircle(rpX[i], rpY[i], 2f + k * 16f, ring)
        }

        if (upOn) powerUp(c)

        // the button under its paddle hat
        val by = PY + PH / 2f + 22f
        val mood = when {
            over || sadT > 0f -> Mood.Sad
            glad > 0f -> Mood.Happy
            vy > 0f && ballY > PY - 190f -> Mood.Angry
            else -> Mood.Calm
        }
        c.shadow(px, by + 24f, 30f, 0.22f)
        bobby(c, px, by, 52f, look, mood = mood, t = t, squash = dip * 1.1f, tears = over)
        paddle(c, px, PY + dip * 40f, pw)

        if (!over) {
            // afterimages, then the furious cursor itself
            val cur = if (ouchT > 0f) art.cursorOuch else art.cursorAngry
            for (i in 0 until trN) {
                c.sprite(cur, trX[i] - CX, trY[i] - CY, alpha = (i.toFloat() / trN) * 0.22f)
            }
            c.softLight(ballX, ballY, 34f, col("#ff5a5a"), 0.3f)
            c.save()
            c.translate(ballX, ballY + (if (wait > 0f) sin(t * 6f) * 3f else 0f))
            c.rotate((clamp(vx * 0.0012f, -0.5f, 0.5f) + sin(t * 34f) * 0.04f) * DEG)
            c.scale(ballScale, ballScale)
            c.sprite(cur, -CX, -CY)
            c.restore()
        }
        c.restore()

        fx.draw(c)
        if (slow > 0f) c.vignette(0.3f, col("#2a63c8")) else c.vignette(0.18f, col("#0b2a40"))
        val ax = if (pin) talkX else clamp(ballX, 60f, W - 60f)
        val ay = if (pin) talkY else max(ballY - 24f, 222f)
        mouth.draw(c, ax, ay)

        // lives as tiny buttons, and the power-up timers
        for (i in 0 until LIVES) bobby(c, 52f + i * 30f, 200f, 24f, look, t = t + i, alpha = if (i < lives) 1f else 0.2f)
        if (wide > 0f) timer(c, true, wide / 8f, 52f, 232f)
        if (slow > 0f) timer(c, false, slow / 5f, if (wide > 0f) 100f else 52f, 232f)
        c.drawScore(score, "returns")
        if (t < 2.6f) Txt.draw(c, "Drag left and right", W / 2f, 450f, 16f, rgba(40, 70, 85, 0.75f))
    }

    // the wide rounded paddle the button wears as a hat
    private fun paddle(c: Canvas, x: Float, y: Float, w: Float) {
        if (x.isNaN() || y.isNaN() || w <= 0f) return
        val h = PH
        c.save()
        c.translate(x, y)
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        c.drawRoundRect(rect, h / 2f, h / 2f, paddleFill)
        rim.color = lookDark
        c.drawRoundRect(rect, h / 2f, h / 2f, rim)
        paint.color = rgba(255, 255, 255, 0.5f)
        rect.set(-w / 2f + 10f, -h / 2f + 3f, w / 2f - 10f, -h / 2f + 6.5f)
        c.drawRoundRect(rect, 1.75f, 1.75f, paint)
        // little ridges, like a button's rim
        paint.color = rgba(0, 0, 0, 0.12f)
        val gap = min(24f, w / 6f)
        for (i in -2..2) c.drawRect(i * gap - 1f, 1f, i * gap + 1f, 6f, paint)
        c.restore()
    }

    private fun powerUp(c: Canvas) {
        val bob = sin(upAge * 6f) * 3f
        c.save()
        c.translate(upX, upY + bob)
        c.rotate(sin(upAge * 4f) * 0.3f * DEG)
        c.softLight(0f, 0f, 30f, if (upDonut) col("#ff9cc4") else col("#9fd0ff"), 0.55f)
        c.sprite(if (upDonut) art.donut else art.clock, -18f, -18f, 36f, 36f)
        c.restore()
    }

    // an active power-up: its icon and a draining bar
    private fun timer(c: Canvas, donut: Boolean, k: Float, x: Float, y: Float) {
        c.sprite(if (donut) art.donut else art.clock, x - 14f, y - 14f, 28f, 28f)
        paint.color = rgba(30, 60, 80, 0.25f)
        rect.set(x + 18f, y - 3f, x + 48f, y + 3f)
        c.drawRoundRect(rect, 3f, 3f, paint)
        paint.color = if (donut) col("#ff7fb0") else col("#5aa0ff")
        rect.set(x + 18f, y - 3f, x + 18f + max(4f, 30f * clamp(k, 0f, 1f)), y + 3f)
        c.drawRoundRect(rect, 3f, 3f, paint)
    }

    /* ---------- art ---------- */

    private val paint = fill(WHITE)
    private val ring = stroke(WHITE, 2f)
    private val rim = stroke(WHITE, 2f)
    private val rect = RectF()
    private val paddleFill = fill().shade(lin(0f, -PH / 2f, 0f, PH / 2f, 0f to lighten(lookColor, 0.4f), 0.5f to lookColor, 1f to darken(lookColor, 0.3f)))
    private val art = Art()

    private class Art {
        val cursorAngry by lazy { cursorArt(false) }
        val cursorOuch by lazy { cursorArt(true) }
        val donut by lazy { upArt(true) }
        val clock by lazy { upArt(false) }
        val desk by lazy { makeSprite(W, H, 1.5f) { paintDesk(it) } }

        // the cursor: white with a black outline, tiny furious eyes
        private fun cursorArt(ouch: Boolean): Sprite = makeSprite(48f, 56f, 3f) { c ->
            c.translate(10f, 8f)
            c.scale(CS, CS)
            val body = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, 17f)
                lineTo(4.5f, 13.2f)
                lineTo(7.8f, 21f)
                lineTo(11f, 19.6f)
                lineTo(7.8f, 12.2f)
                lineTo(13f, 12f)
                close()
            }
            c.save()
            c.translate(0.5f, 0.7f)
            c.drawPath(body, fill(rgba(0, 0, 0, 0.25f)))
            c.restore()
            c.drawPath(body, fill().shade(lin(0f, 0f, 0f, 21f, 0f to WHITE, 1f to col("#d6dbe8"))))
            val ink = col("#14101a")
            c.drawPath(body, stroke(ink, 1.3f))
            if (ouch) {
                val l = stroke(ink, 0.8f)
                for (s in intArrayOf(-1, 1)) {
                    val ex = if (s < 0) 2.4f else 6.2f
                    val p = Path()
                    p.moveTo(ex - s * 1.2f, 7.2f)
                    p.lineTo(ex + s * 0.2f, 8.1f)
                    p.lineTo(ex - s * 1.2f, 9f)
                    c.drawPath(p, l)
                }
                c.drawOval(RectF(4.3f - 1.1f, 11f - 0.8f, 4.3f + 1.1f, 11f + 0.8f), fill(ink))
                return@makeSprite
            }
            // angry eyes, brows slanting down to the middle, and a scowl
            for (ex in floatArrayOf(2.4f, 6.2f)) {
                c.drawCircle(ex, 8.2f, 1.5f, fill(WHITE))
                c.drawCircle(ex, 8.2f, 1.5f, stroke(ink, 0.4f))
                c.drawCircle(ex + 0.3f, 8.5f, 0.8f, fill(ink))
            }
            val brow = stroke(ink, 0.9f)
            c.drawLine(0.9f, 5.9f, 3.5f, 7f, brow)
            c.drawLine(7.8f, 5.9f, 5.1f, 7f, brow)
            c.drawArc(RectF(4.3f - 1.7f, 12.4f - 1.7f, 4.3f + 1.7f, 12.4f + 1.7f), 216f, 108f, false, stroke(ink, 0.6f))
        }

        private fun upArt(donut: Boolean): Sprite = makeSprite(36f, 36f, 3f) { c ->
            c.translate(18f, 18f)
            if (donut) {
                c.drawCircle(0f, 0f, 15f, fill(col("#d9a066")))
                c.drawCircle(0f, -1f, 13f, fill(col("#ff7fb0")))
                c.drawCircle(0f, 0f, 4.6f, fill(col("#d8ecf0")))
                c.drawCircle(0f, 0f, 15f, stroke(col("#8a4f2a"), 1.5f))
                val cols = intArrayOf(WHITE, col("#ffe066"), col("#7fd6ff"), WHITE)
                val sp = stroke(WHITE, 1.8f)
                for (i in 0 until 8) {
                    val a = i * 0.8f + 0.3f
                    sp.color = cols[i % 4]
                    c.drawLine(cos(a) * 9f, sin(a) * 9f - 1f, cos(a) * 9f + cos(a + 1f) * 2.4f, sin(a) * 9f - 1f + sin(a + 1f) * 2.4f, sp)
                }
            } else {
                c.drawCircle(0f, 0f, 15f, fill(WHITE))
                c.drawCircle(0f, 0f, 15f, stroke(col("#4a7fd0"), 3f))
                val hand = stroke(col("#1d2a44"), 2f)
                c.drawLine(0f, 0f, 0f, -9f, hand)
                c.drawLine(0f, 0f, 6f, 3f, hand)
                val tick = fill(col("#4a7fd0"))
                var i = 0
                while (i < 12) {
                    val a = i * PI_F / 6f
                    c.drawRect(cos(a) * 11.5f - 0.8f, sin(a) * 11.5f - 0.8f, cos(a) * 11.5f + 0.8f, sin(a) * 11.5f + 0.8f, tick)
                    i += 3
                }
            }
        }

        // the retro desktop: wallpaper, icons, a window with a fake title bar
        // and a faint grid, and a taskbar
        private fun paintDesk(c: Canvas) {
            c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#2f7fc0"), 0.6f to col("#4aa3d8"), 1f to col("#6fc0a0"))))
            val haze = fill(rgba(255, 255, 255, 0.12f))
            val blobs = Path()
            blobs.addOval(RectF(120f - 150f, 70f - 40f, 120f + 150f, 70f + 40f), Path.Direction.CW)
            blobs.addOval(RectF(390f - 110f, 120f - 28f, 390f + 110f, 120f + 28f), Path.Direction.CW)
            c.drawPath(blobs, haze)

            icon(c, 50f, 96f, 0, "Recycle Bin")
            icon(c, 118f, 96f, 1, "Homework")
            icon(c, 420f, 96f, 2, "do_not_click")

            // window with a soft drop shadow
            c.drawRoundRect(RectF(22f, 152f, 462f, 772f), 8f, 8f, fill(rgba(0, 20, 40, 0.3f)))
            c.drawRoundRect(RectF(18f, 146f, 458f, 766f), 7f, 7f, fill(col("#d9dee6")))
            val bar = Path()
            bar.addRoundRect(RectF(18f, 146f, 458f, 172f), floatArrayOf(7f, 7f, 7f, 7f, 0f, 0f, 0f, 0f), Path.Direction.CW)
            c.drawPath(bar, fill().shade(lin(0f, 146f, 0f, 172f, 0f to col("#4b92e8"), 1f to col("#1b5fb0"))))
            Txt.draw(c, "cursor_pong.exe (Not Responding)", 30f, 160f, 13f, WHITE, align = Paint.Align.LEFT)
            val glyphs = arrayOf("_", "□", "X")
            val gcols = intArrayOf(col("#5b7fb5"), col("#5b7fb5"), col("#d8433a"))
            for (i in 0 until 3) {
                val bx = 458f - 8f - (3 - i) * 22f
                val r = RectF(bx, 150f, bx + 19f, 168f)
                c.drawRoundRect(r, 3f, 3f, fill(gcols[i]))
                c.drawRoundRect(r, 3f, 3f, stroke(rgba(255, 255, 255, 0.7f), 1f))
                Txt.draw(c, glyphs[i], bx + 9.5f, 159f, 12f, WHITE)
            }

            // the arena: a pale canvas with a faint grid
            c.drawRect(LEFT, TOP, RIGHT, 762f, fill(col("#eef7f2")))
            val grid = stroke(rgba(70, 130, 150, 0.12f), 1f).apply { strokeCap = Paint.Cap.BUTT }
            var x = LEFT + 20f
            while (x < RIGHT) {
                c.drawLine(x, TOP, x, 762f, grid)
                x += 28f
            }
            var y = TOP + 20f
            while (y < 762f) {
                c.drawLine(LEFT, y, RIGHT, y, grid)
                y += 28f
            }
            c.drawRect(LEFT, TOP, RIGHT, TOP + 40f, fill().shade(lin(0f, TOP, 0f, TOP + 40f, 0f to rgba(60, 90, 110, 0.22f), 1f to rgba(60, 90, 110, 0f))))
            Txt.draw(c, "Your cursor has stopped working.", W / 2f, 520f, 15f, rgba(60, 100, 120, 0.28f))
            Txt.draw(c, "Probably. Please do not click.", W / 2f, 542f, 13f, rgba(60, 100, 120, 0.22f))
            c.drawRect(LEFT, TOP, RIGHT, 762f, stroke(col("#8f98a6"), 2f).apply { strokeJoin = Paint.Join.MITER })

            // taskbar
            c.drawRect(0f, 768f, W, H, fill().shade(lin(0f, 768f, 0f, H, 0f to col("#dfe3ea"), 1f to col("#aeb6c4"))))
            c.drawRect(0f, 768f, W, 770f, fill(WHITE))
            c.drawRoundRect(RectF(6f, 772f, 80f, 796f), 12f, 12f, fill().shade(lin(0f, 772f, 0f, 796f, 0f to col("#5bc86a"), 1f to col("#2f9a45"))))
            Txt.draw(c, "Start", 43f, 785f, 13f, WHITE)
            val task = RectF(90f, 773f, 240f, 795f)
            c.drawRoundRect(task, 4f, 4f, fill(rgba(255, 255, 255, 0.7f)))
            c.drawRoundRect(task, 4f, 4f, stroke(col("#8f98a6"), 1f))
            Txt.draw(c, "cursor_pong.exe", 165f, 784f, 12f, col("#33404f"))
            Txt.draw(c, "13:37", W - 34f, 784f, 12f, col("#33404f"))
        }

        private fun icon(c: Canvas, x: Float, y: Float, kind: Int, label: String) {
            c.save()
            c.translate(x, y)
            when (kind) {
                0 -> {
                    val can = Path()
                    can.moveTo(-14f, -12f)
                    can.lineTo(14f, -12f)
                    can.lineTo(11f, 16f)
                    can.lineTo(-11f, 16f)
                    can.close()
                    c.drawPath(can, fill(col("#c9d2de")))
                    val l = stroke(col("#6a7686"), 1.5f)
                    c.drawPath(can, l)
                    for (dx in floatArrayOf(-5f, 0f, 5f)) c.drawLine(dx, -8f, dx * 0.8f, 12f, l)
                    val lid = RectF(-16f, -17f, 16f, -11f)
                    c.drawRoundRect(lid, 3f, 3f, fill(col("#e6ecf4")))
                    c.drawRoundRect(lid, 3f, 3f, l)
                }
                1 -> {
                    val l = stroke(col("#a9811f"), 1.5f)
                    val back = RectF(-17f, -12f, 17f, 16f)
                    c.drawRoundRect(back, 3f, 3f, fill(col("#f3c850")))
                    c.drawRoundRect(back, 3f, 3f, l)
                    val front = RectF(-17f, -7f, 17f, 16f)
                    c.drawRoundRect(front, 3f, 3f, fill(col("#ffdc78")))
                    c.drawRoundRect(front, 3f, 3f, l)
                }
                else -> {
                    val r = RectF(-16f, -14f, 16f, 14f)
                    c.drawRoundRect(r, 3f, 3f, fill(WHITE))
                    c.drawRoundRect(r, 3f, 3f, stroke(col("#4a5568"), 1.5f))
                    c.drawRect(-16f, -14f, 16f, -7f, fill(col("#d8433a")))
                    val x2 = stroke(col("#d8433a"), 3f)
                    c.drawLine(-6f, -1f, 6f, 9f, x2)
                    c.drawLine(6f, -1f, -6f, 9f, x2)
                }
            }
            c.restore()
            Txt.draw(c, label, x, y + 31f, 11f, rgba(0, 20, 40, 0.6f), rgba(0, 20, 40, 0.5f), 3f)
            Txt.draw(c, label, x, y + 30f, 11f, WHITE)
        }
    }
}
