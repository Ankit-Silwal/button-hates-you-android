package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Sky Hop: the button bounces up cloud after cloud, as far from you as it can
// get. Hold or drag left and right to steer. Port of src/adventure/games/hop.js.
class HopGame(private val ctx: GameContext) : Game {
    private companion object {
        const val GRAVITY = 1700f
        const val BOUNCE = -900f
        const val SPRING = -1550f
        const val STEER = 380f // top sideways speed
        const val SIZE = 48f
        const val FEET = SIZE * 0.5f // center of the button to its feet
        const val CLOUD_W = 84f
        const val PER_METER = 8f // pixels climbed per meter of score
        const val SPACE = 24000f // pixels climbed before the sky is fully dark
        const val MAX_CLOUDS = 64

        const val START = 0
        const val NORMAL = 1
        const val MOVING = 2
        const val CRUMBLY = 3

        val HOPS = arrayOf("Up. Away. From you.", "Boing. Ugh.", "Higher. Faster.", "Clouds are damp.", "Do not follow me.")
        val SPRINGS = arrayOf("WHEEE— I mean, ugh.", "Bye forever!", "See you never!")
        val CRUMBLES = arrayOf("Cheap cloud.", "Who built this?")
        val MILES = arrayOf("Still too close to you.", "The air is thin. Good.", "Is that space?", "Almost free of you.")
        val FALLS = arrayOf("Your fault.", "NOOO—", "I blame you.")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private var meX = W / 2f
    private var meY = H - 160f
    private var vx = 0f
    private var vy = BOUNCE
    private val startY = meY

    // clouds as parallel arrays
    private val cx = FloatArray(MAX_CLOUDS)
    private val cy = FloatArray(MAX_CLOUDS)
    private val cw = FloatArray(MAX_CLOUDS)
    private val ckind = IntArray(MAX_CLOUDS)
    private val cvx = FloatArray(MAX_CLOUDS)
    private val cspring = FloatArray(MAX_CLOUDS) // NaN: no spring
    private val csprung = FloatArray(MAX_CLOUDS)
    private val csquish = FloatArray(MAX_CLOUDS)
    private val cgone = BooleanArray(MAX_CLOUDS)
    private val cfall = FloatArray(MAX_CLOUDS)
    private var clouds = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var breeze = 0f // seconds until the next drifting mote
    private var pointer = Float.NaN // the x the finger is held at, if it is held
    private var cam = 0f // world y at the top of the screen; only goes down (up the sky)
    private var top = meY // the highest the button has been
    private var nextY = H - 90f
    private var nextMile = 500
    private var t = 0f
    private var squash = 0f
    private var safe = 0f // blinks for a moment after a Continue

    init {
        addCloud(nextY, START)
    }

    private fun push(x: Float, y: Float, w: Float, kind: Int, cvx0: Float, spring: Float, squish: Float) {
        if (clouds >= MAX_CLOUDS) removeCloud(0)
        val i = clouds++
        cx[i] = x
        cy[i] = y
        cw[i] = w
        ckind[i] = kind
        cvx[i] = cvx0
        cspring[i] = spring
        csprung[i] = 0f
        csquish[i] = squish
        cgone[i] = false
        cfall[i] = 0f
    }

    private fun removeCloud(i: Int) {
        for (j in i until clouds - 1) {
            cx[j] = cx[j + 1]
            cy[j] = cy[j + 1]
            cw[j] = cw[j + 1]
            ckind[j] = ckind[j + 1]
            cvx[j] = cvx[j + 1]
            cspring[j] = cspring[j + 1]
            csprung[j] = csprung[j + 1]
            csquish[j] = csquish[j + 1]
            cgone[j] = cgone[j + 1]
            cfall[j] = cfall[j + 1]
        }
        clouds--
    }

    // How far apart clouds are, and what kinds show up, depends on the height.
    private fun addCloud(y: Float, kind0: Int = -1) {
        val m = (startY - y) / PER_METER
        val k = clamp(m / 3000f, 0f, 1f)
        var kind = kind0
        if (kind < 0) {
            val r = Random.nextFloat()
            val moving = if (m > 150f) 0.08f + k * 0.25f else 0f
            val crumbly = if (m > 300f) 0.06f + k * 0.2f else 0f
            kind = if (r < moving) MOVING else if (r < moving + crumbly) CRUMBLY else NORMAL
        }
        push(
            x = if (kind == START) W / 2f else rand(CLOUD_W / 2f + 6f, W - CLOUD_W / 2f - 6f),
            y = y,
            w = if (kind == START) 200f else CLOUD_W,
            kind = kind,
            cvx0 = if (kind == MOVING) (if (Random.nextBoolean()) -1f else 1f) * rand(60f, 90f + k * 80f) else 0f,
            spring = if (kind == NORMAL && m > 60f && Random.nextFloat() < 0.07f) rand(-20f, 20f) else Float.NaN,
            squish = 0f,
        )
        nextY = y - rand(0.75f, 1f) * clamp(70f + m * 0.035f, 70f, 175f)
    }

    private fun land(i: Int) {
        meY = cy[i] - FEET
        squash = 0.25f
        csquish[i] = 1f
        if (!cspring[i].isNaN() && abs(meX - (cx[i] + cspring[i])) < 22f) {
            vy = SPRING
            csprung[i] = 0.3f
            ctx.sfx.play("pop")
            ctx.haptics.thud()
            fx.burst(meX, cy[i], SPRING_GLOW, 16, 320f)
            fx.ring(meX, cy[i], col("#ffe9a0"), 70f, 0.4f)
            mouth.maybe(SPRINGS, 0.5f)
            return
        }
        vy = BOUNCE
        ctx.sfx.play("jump")
        if (ckind[i] == CRUMBLY) {
            cgone[i] = true
            ctx.sfx.play("crack")
            ctx.haptics.tap()
            fx.burst(cx[i], cy[i] + 6f, col("#d9c3a0"), 14, 180f, gravity = 700f, size = 3.5f)
            mouth.maybe(CRUMBLES, 0.25f)
        } else {
            mouth.maybe(HOPS, 0.05f)
            // a little puff of cloud kicked up by the landing
            for (n in 0 until 5) fx.mote(meX + rand(-18f, 18f), cy[i] + 2f, WHITE, rand(-60f, 60f), rand(-40f, -10f), rand(2f, 4f), 0.45f)
        }
    }

    private fun fall() {
        if (over) return
        over = true
        ctx.sfx.play("bad")
        ctx.haptics.thud()
        fx.burst(meX, cam + H - 10f, lookColor, 22, 300f)
        mouth.say(pick(FALLS), 3f)
    }

    override fun down(x: Float, y: Float) {
        pointer = x
    }

    override fun move(x: Float, y: Float) {
        if (!pointer.isNaN()) pointer = x
    }

    override fun up(x: Float, y: Float) {
        pointer = Float.NaN
    }

    // a paid Continue: a wide rescue cloud low on the screen catches the button
    override fun revive() {
        over = false
        val y = cam + H * 0.74f
        push(W / 2f, y, 200f, START, 0f, Float.NaN, 1f)
        meX = W / 2f
        meY = y - FEET
        vx = 0f
        vy = BOUNCE
        squash = 0.25f
        safe = 1.5f
        ctx.sfx.play("jump")
        reviveFx(fx, mouth, ctx, meX, y, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        breeze -= dt
        if (breeze <= 0f) {
            breeze = rand(0.15f, 0.4f)
            fx.mote(rand(0f, W), cam + rand(0f, H), WHITE, rand(-25f, 25f), rand(-30f, -8f), rand(1f, 2.2f), rand(1.5f, 3f))
        }
        squash = max(0f, squash - dt)
        safe -= dt

        // steer: a held finger pulls toward its x
        var want = 0f
        if (!pointer.isNaN()) want = clamp((pointer - meX) * 5f, -STEER, STEER)
        if (over) want = 0f
        vx += (want - vx) * min(1f, dt * 10f)

        val before = meY + FEET
        vy += GRAVITY * dt
        meX += vx * dt
        meY += vy * dt
        // wrap around the sides
        if (meX < -SIZE / 2f) meX += W + SIZE
        if (meX > W + SIZE / 2f) meX -= W + SIZE
        if (meX.isNaN() || meY.isNaN()) {
            meX = W / 2f
            meY = cam + H * 0.5f
            vx = 0f
            vy = 0f
        }

        for (i in 0 until clouds) {
            csquish[i] = max(0f, csquish[i] - dt * 4f)
            csprung[i] = max(0f, csprung[i] - dt)
            if (cvx[i] != 0f) {
                cx[i] += cvx[i] * dt
                val half = cw[i] / 2f
                if (cx[i] < half || cx[i] > W - half) {
                    cx[i] = clamp(cx[i], half, W - half)
                    cvx[i] = -cvx[i]
                }
            }
            if (cgone[i]) {
                cfall[i] += 900f * dt
                cy[i] += cfall[i] * dt
            }
        }

        // it only lands on the way down, and only if its feet crossed the top
        if (!over && vy > 0f) {
            val after = meY + FEET
            for (i in 0 until clouds) {
                if (cgone[i] || before > cy[i] + 4f || after < cy[i]) continue
                if (abs(meX - cx[i]) < cw[i] / 2f + SIZE * 0.25f) {
                    land(i)
                    break
                }
            }
        }
        if (over) return

        // the camera follows the button up, never down
        cam = min(cam, meY - H * 0.4f)
        top = min(top, meY)
        score = max(0, floor((startY - top) / PER_METER).toInt())
        if (score >= nextMile) {
            nextMile += 500
            mouth.say(pick(MILES), 2.2f)
        }

        var guard = 0
        while (nextY > cam - 120f && guard++ < 32) addCloud(nextY)
        for (i in clouds - 1 downTo 0) if (cy[i] - cam > H + 80f) removeCloud(i)

        if (meY - FEET - cam > H) fall()
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        // the sky darkens into space the higher it gets
        val k = clamp(-cam / SPACE, 0f, 1f)
        backdrop(c, k)

        for (i in 0 until clouds) cloud(c, i, cy[i] - cam, k)

        val mood = when {
            over -> Mood.Scared
            vy < -1100f -> Mood.Happy
            vy < 0f -> Mood.Smug
            vy > 700f -> Mood.Worried
            else -> Mood.Calm
        }
        val sy = meY - cam
        val angle = (vx / STEER) * 0.25f
        val alpha = blinkAlpha(safe, t)
        // a soft halo behind the button: sunlit by day, its own glow by night
        c.softLight(meX, sy, SIZE * 1.3f, WHITE, 0.35f * (1f - k))
        c.glow(meX, sy, SIZE * 1.4f, lookColor, 0.35f * k)
        if (vy < -1000f && !over) speedLines(c, meX, sy, clamp((-vy - 1000f) / 400f, 0f, 1f))
        bobby(c, meX, sy, SIZE, look, mood, t, squash, tears = over, angle = angle, alpha = alpha)
        // a second copy peeks in from the other side while it wraps
        if (meX < SIZE / 2f) bobby(c, meX + W + SIZE, sy, SIZE, look, mood, t, squash, tears = over, angle = angle, alpha = alpha)
        if (meX > W - SIZE / 2f) bobby(c, meX - W - SIZE, sy, SIZE, look, mood, t, squash, tears = over, angle = angle, alpha = alpha)

        c.save()
        c.translate(0f, -cam)
        fx.draw(c)
        c.restore()
        c.vignette(0.25f + k * 0.2f, if (k > 0.5f) VIG_NIGHT else VIG_DAY)
        mouth.draw(c, clamp(meX, 40f, W - 40f), clamp(sy - SIZE * 0.7f, 160f, H - 20f))

        c.drawScore(score, if (t < 4f) "Hold left or right to steer" else "meters")
    }

    /* ---------- drawing ---------- */

    // Everything behind the clouds: the sky, the sun or the stars, the world far
    // below and two layers of slow distant clouds. k is 0 by day, 1 in space.
    private fun backdrop(c: Canvas, k: Float) {
        if (k < 1f) c.sprite(art.day, 0f, 0f, W, H)
        if (k > 0f) c.sprite(art.night, 0f, 0f, W, H, alpha = k)
        val up = -cam // pixels climbed

        // nebula and stars fade in up high
        val night = clamp((k - 0.3f) * 1.6f, 0f, 1f)
        if (night > 0f) {
            val ny = ((up * 0.03f) % H + H) % H
            c.sprite(art.nebula, 0f, ny - H, W, H, alpha = night * 0.85f)
            c.sprite(art.nebula, 0f, ny, W, H, alpha = night * 0.85f)
            for (i in 0 until 70) {
                val fi = i.toFloat()
                val sx = hash(fi) * W
                val sy = (((hash(fi + 99f) * H + up * (0.03f + hash(fi + 7f) * 0.05f)) % H) + H) % H
                val tw = 0.55f + 0.45f * sin(t * (1.5f + hash(fi + 3f) * 2f) + fi)
                val r = 2f + hash(fi + 5f) * 5f
                c.glow(sx, sy, r * (0.8f + tw * 0.4f), if (i % 7 != 0) STAR else STAR_WARM, night * tw)
            }
            // a ringed planet drifts by far away
            val pk = clamp((k - 0.55f) * 3f, 0f, 1f)
            if (pk > 0f) c.sprite(art.planet, W - 190f, 150f + ((up * 0.015f) % 60f), 150f, 110f, alpha = pk)
        }

        // the sun, with slow-turning rays, fades as the sky darkens
        val day = 1f - k
        if (day > 0.02f) {
            val sy = 150f + min(up * 0.02f, 120f)
            c.rays(W - 92f, sy, 330f, SUN_RAYS, 0.22f * day, t * 0.04f)
            c.softLight(W - 92f, sy, 190f, SUN_SOFT, 0.55f * day)
            c.glow(W - 92f, sy, 60f, SUN_CORE, 0.9f * day)
        }

        // the town far below, lost in the haze once the climb starts
        val gy = H - 150f + up * 0.35f
        if (gy < H) c.sprite(art.town, 0f, gy, W, 300f)

        // far and near layers of big soft clouds, scrolling at their own pace
        val tint = if (k > 0.5f) 1 else 0
        for (layer in 0 until 2) {
            val depth = if (layer == 0) 0.12f else 0.3f
            val scale = if (layer == 0) 0.7f else 1f
            val a = (if (layer == 0) 0.32f else 0.5f) * (1f - k * 0.75f)
            if (a <= 0.02f) continue
            val span = H + 260f
            val spr = art.farCloud(tint)
            for (i in 0 until 4) {
                val y = ((((i * span) / 4f + up * depth) % span) + span) % span - 160f
                val x = ((hash(i * 3f + depth * 10f) * (W + 120f) + t * 6f * depth * 10f) % (W + 280f)) - 200f
                c.sprite(spr, x, y, spr.w * scale, spr.h * scale, alpha = a)
            }
        }
    }

    private val srcR = Rect()
    private val dstR = RectF()
    private val halfPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // A cloud platform whose walkable top is at y. k: how dark the sky is.
    private fun cloud(c: Canvas, i: Int, y: Float, k: Float) {
        if (y < -70f || y > H + 70f) return
        val x = cx[i]
        val w = cw[i]
        val kind = ckind[i]
        val spr = art.cloud(kind, w, false)
        val sw = spr.w
        val sh = spr.h
        val dy = y + csquish[i] * 5f
        if (cgone[i]) {
            // the two halves drift apart as it falls
            halfPaint.alpha = (clamp(1f - cfall[i] / 700f, 0f, 1f) * 255f).toInt()
            if (halfPaint.alpha <= 0) return
            val bw = spr.bmp.width
            val bh = spr.bmp.height
            for (side in 0 until 2) {
                val s = if (side == 0) -1f else 1f
                c.save()
                c.translate(x + s * cfall[i] * 0.04f, dy)
                c.rotate(s * cfall[i] * 0.0006f * DEG)
                if (side == 0) {
                    srcR.set(0, 0, bw / 2, bh)
                    dstR.set(-sw / 2f, -TOPY, 0f, -TOPY + sh)
                } else {
                    srcR.set(bw / 2, 0, bw, bh)
                    dstR.set(0f, -TOPY, sw / 2f, -TOPY + sh)
                }
                c.drawBitmap(spr.bmp, srcR, dstR, halfPaint)
                c.restore()
            }
            return
        }

        // moonlit clouds glow faintly from beneath
        if (k > 0.3f) c.glow(x, dy + 12f, w * 0.7f, MOON_GLOW, (k - 0.3f) * 0.5f)
        if (kind == MOVING) c.glow(x, dy + 16f, w * 0.55f, MOVE_GLOW, 0.35f + sin(t * 4f) * 0.1f)
        val sq = 1f - csquish[i] * 0.08f
        c.sprite(spr, x - sw / 2f, dy - TOPY * sq, sw, sh * sq)
        if (k > 0.2f && kind != CRUMBLY && kind != MOVING) {
            // moonlight takes over from the sun
            c.sprite(art.cloud(kind, w, true), x - sw / 2f, dy - TOPY * sq, sw, sh * sq, alpha = min(1f, (k - 0.2f) * 1.5f))
        }

        if (kind == MOVING) {
            // little arrows hint that it slides
            val wob = sin(t * 6f) * 2f
            val lx = x - w / 2f + 12f - wob
            val rx = x + w / 2f - 12f + wob
            c.drawLine(lx, dy + 7f, lx - 6f, dy + 11f, arrow)
            c.drawLine(lx - 6f, dy + 11f, lx, dy + 15f, arrow)
            c.drawLine(rx, dy + 7f, rx + 6f, dy + 11f, arrow)
            c.drawLine(rx + 6f, dy + 11f, rx, dy + 15f, arrow)
        }

        if (!cspring[i].isNaN()) spring(c, x + cspring[i], dy, csprung[i])
    }

    private val coil = Path()

    // A shiny coil with a glowing pad that squashes when bounced on.
    private fun spring(c: Canvas, sx: Float, dy: Float, sprung: Float) {
        val tall = if (sprung > 0f) 8f else 18f
        coil.reset()
        coil.moveTo(sx - 8f, dy)
        for (n in 1..4) coil.lineTo(sx + (if (n % 2 == 1) 8f else -8f), dy - (tall * n) / 4f)
        c.drawPath(coil, coilDark)
        c.drawPath(coil, coilLight)
        val py = dy - tall - 5f
        c.glow(sx, py + 3f, 26f + (if (sprung > 0f) 20f else 0f), SPRING_PAD_GLOW, 0.45f + sin(t * 6f) * 0.12f + sprung * 2f)
        c.sprite(art.springPad, sx - 13f, py)
    }

    // Streaks of wind rushing past during a big spring launch.
    private fun speedLines(c: Canvas, x: Float, y: Float, a: Float) {
        for (i in 0 until 7) {
            val fi = i.toFloat()
            val ox = (hash(fi) - 0.5f) * 70f
            val ph = (t * 3f + hash(fi + 4f)) % 1f
            val ly = y + 10f + ph * 90f
            wind.alpha = (clamp(a * 0.55f * (1f - ph), 0f, 1f) * 255f).roundToInt()
            wind.strokeWidth = 2f + hash(fi + 8f) * 2f
            c.drawLine(x + ox, ly, x + ox, ly + 30f + hash(fi + 2f) * 30f, wind)
        }
    }

    /* ---------- art ---------- */

    private val SPRING_GLOW = col("#ffd84d")
    private val SPRING_PAD_GLOW = col("#ff6a4f")
    private val STAR = col("#cfe0ff")
    private val STAR_WARM = col("#ffd9a8")
    private val SUN_RAYS = col("#fff2c8")
    private val SUN_SOFT = col("#fff4d8")
    private val SUN_CORE = col("#fff6dc")
    private val MOON_GLOW = col("#7a6cff")
    private val MOVE_GLOW = col("#59c8ff")
    private val VIG_NIGHT = col("#05031a")
    private val VIG_DAY = col("#0b2a55")
    private val arrow = stroke(rgba(40, 130, 200, 0.9f), 2.5f)
    private val coilDark = stroke(col("#4c5260"), 4.5f)
    private val coilLight = stroke(col("#d9dee8"), 1.8f)
    private val wind = stroke(WHITE, 2f).apply { xfermode = ADD }
    private val art = Art()

    private class Art {
        val day by lazy { skySprite(col("#2f7fd6"), col("#5fb0f0"), col("#a9dcff"), col("#ffe8cf")) }
        val night by lazy { skySprite(col("#03031a"), col("#0d0b38"), col("#26195e"), col("#4b2a78")) }

        val nebula by lazy {
            makeSprite(W, H, 0.5f) { c ->
                val blobs = arrayOf(
                    floatArrayOf(120f, 180f, 260f, 0.35f), floatArrayOf(380f, 330f, 230f, 0.28f), floatArrayOf(220f, 560f, 300f, 0.22f),
                    floatArrayOf(420f, 720f, 200f, 0.3f), floatArrayOf(60f, 760f, 180f, 0.2f),
                )
                val cols = intArrayOf(col("#5b2aa8"), col("#1f6fa8"), col("#a83a8a"), col("#3a2aa8"), col("#1f8fa0"))
                for (b in blobs.indices) {
                    val (x, y, r, a) = blobs[b]
                    c.drawRect(0f, 0f, W, H, fill().shade(rad(x, y, 0f, r, 0f to rgba(cols[b], a), 1f to rgba(cols[b], 0f))).apply { xfermode = ADD })
                }
                // a dusting of tiny far stars baked in
                val p = fill(WHITE)
                for (i in 0 until 220) {
                    val fi = i.toFloat()
                    p.color = rgba(255, 255, 255, 0.2f + hash(fi + 300f) * 0.5f)
                    val s = if (hash(fi + 500f) < 0.9f) 1.5f else 2.5f
                    val sx = hash(fi + 100f) * W
                    val sy = hash(fi + 200f) * H
                    c.drawRect(sx, sy, sx + s * 2f, sy + s * 2f, p)
                }
            }
        }

        val planet by lazy {
            makeSprite(150f, 110f) { c ->
                val pcx = 75f
                val pcy = 55f
                val ringBox = RectF(pcx - 70f, pcy - 14f, pcx + 70f, pcy + 14f)
                val ringP = stroke(rgba(255, 214, 170, 0.55f), 5f)
                c.save()
                c.rotate(-0.25f * DEG, pcx, pcy)
                // the back half of the ring
                c.drawArc(ringBox, 180f, 180f, false, ringP)
                c.restore()
                c.drawCircle(
                    pcx, pcy, 36f,
                    fill().shade(
                        android.graphics.RadialGradient(
                            pcx - 14f, pcy - 16f, 44f,
                            intArrayOf(col("#ffd7b0"), col("#d9806a"), col("#4a2050")),
                            floatArrayOf(0.1f, 0.55f, 1f),
                            android.graphics.Shader.TileMode.CLAMP,
                        ),
                    ),
                )
                c.save()
                c.rotate(-0.25f * DEG, pcx, pcy)
                c.drawArc(ringBox, 0f, 180f, false, ringP)
                c.drawOval(ringBox, stroke(rgba(255, 240, 220, 0.5f), 1.5f))
                c.restore()
            }
        }

        // Hills and rooftops far below the start, softened by haze.
        val town by lazy {
            makeSprite(W, 300f) { c ->
                val back = Path()
                back.moveTo(0f, 300f)
                var x = 0f
                while (x <= W) {
                    back.lineTo(x, 120f + sin(x * 0.012f) * 30f + sin(x * 0.031f) * 12f)
                    x += 20f
                }
                back.lineTo(W, 300f)
                back.close()
                c.drawPath(back, fill(col("#9cc7ea")))
                val house = fill(col("#7fb0d9"))
                for (i in 0 until 22) {
                    val fi = i.toFloat()
                    val hx = hash(fi + 40f) * W
                    val w = 16f + hash(fi + 41f) * 26f
                    val h = 30f + hash(fi + 42f) * 70f
                    c.drawRect(hx, 190f - h, hx + w, 190f - h + h + 120f, house)
                    if (hash(fi + 43f) > 0.5f) {
                        val roof = Path()
                        roof.moveTo(hx - 3f, 190f - h)
                        roof.lineTo(hx + w / 2f, 190f - h - 14f)
                        roof.lineTo(hx + w + 3f, 190f - h)
                        roof.close()
                        c.drawPath(roof, house)
                    }
                }
                val front = Path()
                front.moveTo(0f, 300f)
                x = 0f
                while (x <= W) {
                    front.lineTo(x, 210f + sin(x * 0.02f + 1f) * 14f)
                    x += 16f
                }
                front.lineTo(W, 300f)
                front.close()
                c.drawPath(front, fill(col("#6aa2cf")))
                // haze over everything, thickest at the top
                c.drawRect(
                    0f, 0f, W, 300f,
                    fill().shade(lin(0f, 60f, 0f, 300f, 0f to rgba(255, 232, 207, 1f), 0.35f to rgba(255, 232, 207, 0.55f), 1f to rgba(255, 232, 207, 0.15f))).apply { xfermode = ATOP },
                )
            }
        }

        private val far = arrayOfNulls<Sprite>(2)

        // A big soft background cloud, lit from above (tint 1 = moonlit violet).
        fun farCloud(tint: Int): Sprite = far[tint] ?: makeSprite(320f, 200f) { c ->
            c.translate(10f, 25f)
            val lobes = arrayOf(
                floatArrayOf(70f, 95f, 42f), floatArrayOf(120f, 70f, 56f), floatArrayOf(180f, 78f, 48f),
                floatArrayOf(230f, 98f, 36f), floatArrayOf(150f, 108f, 46f), floatArrayOf(100f, 112f, 34f),
            )
            val p = fill().shade(
                lin(
                    0f, 20f, 0f, 140f,
                    0f to (if (tint == 1) col("#b9a8ff") else WHITE),
                    0.55f to (if (tint == 1) col("#6f5fc0") else col("#e2effc")),
                    1f to (if (tint == 1) col("#3a2f80") else col("#9dbde3")),
                ),
            )
            p.setShadowLayer(9f, 0f, 0f, if (tint == 1) rgba(170, 150, 255, 0.5f) else rgba(255, 255, 255, 0.6f))
            val path = Path()
            for (l in lobes) path.addCircle(l[0], l[1], l[2], Path.Direction.CW)
            c.drawPath(path, p)
        }.also { far[tint] = it }

        private val clouds = HashMap<Int, Sprite>()

        // A puffy, lit cloud platform w wide, painted once per kind and width.
        fun cloud(kind: Int, w: Float, night: Boolean): Sprite {
            val key = kind * 100000 + w.roundToInt() * 2 + (if (night) 1 else 0)
            return clouds.getOrPut(key) { paintCloud(kind, w, night) }
        }

        private fun paintCloud(kind: Int, w: Float, night: Boolean): Sprite = makeSprite(w + CPAD * 2f, 74f) { c ->
            val pal = when {
                night && kind != CRUMBLY && kind != MOVING -> intArrayOf(col("#f1edff"), col("#b3a8ec"), col("#4f42a0"))
                kind == MOVING -> intArrayOf(col("#f4fdff"), col("#c4ecff"), col("#5aa6dc"))
                kind == CRUMBLY -> intArrayOf(col("#fff4e2"), col("#ead4b4"), col("#a5845f"))
                else -> intArrayOf(WHITE, col("#f0f6ff"), col("#8eaedb"))
            }
            val x0 = CPAD
            val y = TOPY
            val s = min(1f, w / 84f)
            val shape = Path()
            shape.addRoundRect(RectF(x0, y - 2f, x0 + w, y + 18f), 10f, 10f, Path.Direction.CW)
            val bumps = max(3, (w / 26f).roundToInt())
            for (i in 0 until bumps) {
                val bx = x0 + (w / bumps) * (i + 0.5f)
                val r = (if (i % 2 == 1) 11f else 14f) * s
                shape.addCircle(bx, y + 4f, r, Path.Direction.CW)
            }
            // softer lobes underneath
            for (i in 0 until bumps - 1) {
                val bx = x0 + (w / bumps) * (i + 1)
                shape.addCircle(bx, y + 16f, 9f, Path.Direction.CW)
            }
            shape.fillType = Path.FillType.WINDING
            // an airy halo around it
            val halo = fill(pal[1])
            halo.setShadowLayer(
                8f, 0f, 3f,
                when (kind) {
                    MOVING -> rgba(120, 210, 255, 0.75f)
                    CRUMBLY -> rgba(120, 90, 50, 0.3f)
                    else -> rgba(255, 255, 255, 0.85f)
                },
            )
            c.drawPath(shape, halo)
            // body: bright on top, shaded underneath
            c.drawPath(shape, fill().shade(lin(0f, y - 14f, 0f, y + 26f, 0f to pal[0], 0.45f to pal[1], 1f to pal[2])))
            c.save()
            c.clipPath(shape)
            // sunlit rim along the top left
            c.drawRect(0f, 0f, w + CPAD * 2f, 74f, fill().shade(rad(x0 + w * 0.3f, y - 10f, 2f, w * 0.6f, 0f to rgba(255, 255, 255, 0.95f), 1f to rgba(255, 255, 255, 0f))))
            // bounce light from below
            c.drawRect(
                0f, y + 10f, w + CPAD * 2f, y + 30f,
                fill().shade(lin(0f, y + 14f, 0f, y + 26f, 0f to rgba(255, 255, 255, 0f), 1f to (if (kind == CRUMBLY) rgba(255, 200, 140, 0.35f) else rgba(200, 235, 255, 0.5f)))),
            )
            if (kind == CRUMBLY) {
                // dry cracks and specks
                val crack = stroke(rgba(110, 80, 50, 0.65f), 1.6f)
                val m = x0 + w / 2f
                val p = Path()
                p.moveTo(m - 14f, y - 6f)
                p.lineTo(m - 6f, y + 4f)
                p.lineTo(m - 12f, y + 12f)
                p.lineTo(m - 8f, y + 20f)
                p.moveTo(m + 18f, y - 4f)
                p.lineTo(m + 10f, y + 8f)
                p.lineTo(m + 15f, y + 16f)
                p.moveTo(m - 30f, y + 2f)
                p.lineTo(m - 24f, y + 10f)
                c.drawPath(p, crack)
                val speck = fill(rgba(120, 90, 60, 0.35f))
                for (i in 0 until 14) {
                    val sx = x0 + hash(i + w) * w
                    val sy = y - 4f + hash(i + 9f) * 24f
                    c.drawRect(sx, sy, sx + 2f, sy + 2f, speck)
                }
            }
            c.restore()
        }

        val springPad by lazy {
            makeSprite(26f, 7f) { c ->
                c.drawRoundRect(RectF(0f, 0f, 26f, 7f), 3.5f, 3.5f, fill().shade(lin(0f, 0f, 0f, 7f, 0f to col("#ff8a6a"), 1f to col("#b8261f"))))
                c.drawRoundRect(RectF(4f, 1f, 16f, 3f), 1f, 1f, fill(rgba(255, 255, 255, 0.7f)))
            }
        }
    }
}

private const val CPAD = 26f // room around a cloud sprite for its glow
private const val TOPY = 34f // the walkable top inside the sprite

