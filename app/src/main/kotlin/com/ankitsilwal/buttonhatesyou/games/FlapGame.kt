package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

// Flappy Regret: tap to flap the button through gaps in the pipes.
// Port of src/adventure/games/flap.js.
class FlapGame(private val ctx: GameContext) : Game {
    private companion object {
        const val GRAVITY = 1500f
        const val FLAP = -460f
        const val SPEED = 170f
        const val PIPE_W = 78f
        const val SPACING = 250f
        const val GROUND = H - 70f
        const val SIZE = 50f
        const val SUN_X = 330f
        const val SUN_Y = GROUND - 370f
        const val TILE = 960f // the hill strips repeat every TILE, so they scroll seamlessly
        const val MAX_PIPES = 8

        val FLAPS = arrayOf("Stop that.", "Wheeeâ€¦ no.", "I hate heights.", "My wings hurt.", "Put me down!", "Why me?")
        val CRASH = arrayOf("OW.", "Told you.", "Finally.")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private var x = 140f
    private var y = H * 0.42f
    private var vy = 0f
    private var angle = 0f

    // pipes as parallel arrays (a ring of at most MAX_PIPES)
    private val px = FloatArray(MAX_PIPES)
    private val ptop = FloatArray(MAX_PIPES)
    private val psize = FloatArray(MAX_PIPES)
    private val ppassed = BooleanArray(MAX_PIPES)
    private var pipes = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var started = false
    private var t = 0f
    private var scroll = 0f
    private var squash = 0f
    private var hold = false // after a Continue: hover until the next tap
    private var safe = 0f // and pipes can't hit it for a moment after that

    private fun gap() = clamp(210f - score * 3f, 150f, 210f)

    private fun addPipe(at: Float) {
        if (pipes >= MAX_PIPES) return
        val size = gap()
        px[pipes] = at
        ptop[pipes] = rand(110f, GROUND - size - 60f)
        psize[pipes] = size
        ppassed[pipes] = false
        pipes++
    }

    private fun removePipe(i: Int) {
        for (j in i until pipes - 1) {
            px[j] = px[j + 1]
            ptop[j] = ptop[j + 1]
            psize[j] = psize[j + 1]
            ppassed[j] = ppassed[j + 1]
        }
        pipes--
    }

    private fun crash() {
        if (over) return
        over = true
        vy = min(vy, -200f)
        ctx.sfx.play("hit")
        ctx.haptics.thud()
        fx.sparks(x, y, lookColor, 16)
        fx.sparks(x, y, WHITE, 8, 140f) // loose feathers
        mouth.say(pick(CRASH), 3f)
    }

    private fun flapNow() {
        if (over) return
        if (!started) {
            started = true
            addPipe(W + 60f)
        }
        if (hold) {
            hold = false
            safe = 1.5f
        }
        vy = FLAP
        squash = 0.18f
        fx.sparks(x - 6f, y + SIZE * 0.4f, col("#fff4ea"), 3, 90f) // a puff of air
        ctx.sfx.play("jump")
        mouth.maybe(FLAPS, 0.2f)
    }

    override fun down(x: Float, y: Float) = flapNow()

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        squash = max(0f, squash - dt)
        safe -= dt

        if (!started || hold) {
            y = H * 0.42f + sin(t * 3f) * 10f // hover until the first tap
            angle = 0f
            return
        }

        vy += GRAVITY * dt
        y += vy * dt
        angle = clamp(vy / 900f, -0.45f, 1.2f)

        if (y + SIZE / 2f > GROUND) {
            y = GROUND - SIZE / 2f
            vy = 0f
            crash()
        }
        if (y < SIZE / 2f) {
            y = SIZE / 2f
            vy = 0f
        }
        if (over) return

        scroll += SPEED * dt
        val r = SIZE * 0.4f
        for (i in 0 until pipes) {
            px[i] -= SPEED * dt
            if (!ppassed[i] && px[i] + PIPE_W < x) {
                ppassed[i] = true
                score++
                ctx.sfx.play("pop")
                ctx.haptics.tap()
                fx.float(x, y - 40f, "+1")
            }
            // a slightly forgiving circle-vs-rectangle check
            val nx = clamp(x, px[i], px[i] + PIPE_W)
            val hitsTop = hypot(x - nx, y - clamp(y, -100f, ptop[i])) < r
            val hitsBottom = hypot(x - nx, y - clamp(y, ptop[i] + psize[i], H)) < r
            if ((hitsTop || hitsBottom) && safe <= 0f) crash()
        }
        if (pipes > 0 && px[0] < -PIPE_W) removePipe(0)
        if (pipes == 0 || px[pipes - 1] < W - SPACING) addPipe(W + 20f)
    }

    // a paid Continue: clear the pipes near the button and hover until a tap
    override fun revive() {
        over = false
        hold = true
        vy = 0f
        y = H * 0.42f
        for (i in pipes - 1 downTo 0) if (px[i] < x + 220f) removePipe(i)
        if (pipes == 0) addPipe(W + 60f)
        reviveFx(fx, mouth, ctx, x, y, lookColor)
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        // a dusk sky with the sun sinking behind three layers of hills
        c.sprite(art.sky, 0f, 0f, W, H)
        val sunPulse = 1f + sin(t * 0.8f) * 0.03f
        c.glow(SUN_X, SUN_Y, 230f * sunPulse, SUN_GLOW, 0.55f)

        for (i in 0 until 5) {
            val span = W + 260f
            val cx = (((i * 170f - scroll * 0.12f - t * 6f) % span) + span) % span - 160f
            c.sprite(art.clouds[i % 3], cx, 90f + (i % 3) * 95f + (if (i == 4) 40f else 0f))
        }
        c.scrollTile(art.ridges[0], scroll * 0.18f, GROUND - 330f)
        c.scrollTile(art.ridges[1], scroll * 0.4f, GROUND - 230f)
        c.scrollTile(art.ridges[2], scroll * 0.7f, GROUND - 150f)

        // drifting motes catching the last light
        for (i in 0 until 14) {
            val fi = i.toFloat()
            val span = W + 40f
            val mx = (((hash(fi) * W * 1.4f - scroll * (0.3f + hash(fi + 9f) * 0.5f)) % span) + span) % span - 20f
            val my = 260f + hash(fi + 3f) * 420f + sin(t * (0.6f + hash(fi + 5f)) + fi) * 18f
            c.glow(mx, my, 10f + hash(fi + 7f) * 8f, MOTE, 0.35f + sin(t * 2f + fi * 1.7f) * 0.2f)
        }

        for (i in 0 until pipes) pipe(c, i)

        // the ground: lit grass lip over dark soil, scrolling with the pipes
        c.scrollTile(art.ground, scroll, GROUND - 10f)

        // the button's shadow on the ground shrinks as it climbs
        val lift = clamp((GROUND - y) / 500f, 0f, 1f)
        c.shadow(x, GROUND + 3f, SIZE * (0.55f - lift * 0.3f), 0.35f * (1f - lift * 0.7f))

        // wings flap faster on the way up
        val beat = sin(t * (if (vy < 0f) 40f else 14f))
        val alpha = blinkAlpha(safe, t)
        wing(c, -1f, beat, alpha)
        bobby(
            c, x, y, SIZE, look,
            mood = if (over) Mood.Sad else if (vy < -150f) Mood.Scared else Mood.Worried,
            t = t, squash = squash, tears = over, angle = angle, alpha = alpha,
        )
        wing(c, 1f, beat, alpha)

        fx.draw(c)
        c.vignette(0.42f, VIGNETTE)
        mouth.draw(c, x, y - SIZE * 0.7f)

        when {
            hold -> c.drawScore(score, "Tap to flap on")
            started -> c.drawScore(score)
            else -> c.drawScore("Tap to flap", "It will complain. Ignore it.")
        }
    }

    private fun pipe(c: Canvas, i: Int) {
        val pxi = round(px[i])
        val top = ptop[i]
        val bottomY = top + psize[i]
        c.sprite(art.pipeStrip, pxi, -10f, PIPE_W, top + 10f)
        c.sprite(art.pipeStrip, pxi, bottomY, PIPE_W, GROUND - bottomY)
        // shade where the lips overhang the body (the top one hangs down, flipped)
        c.save()
        c.translate(0f, top - 26f)
        c.scale(1f, -1f)
        c.sprite(art.lipShade, pxi, 0f, PIPE_W, 26f)
        c.restore()
        c.sprite(art.lipShade, pxi, bottomY + 26f, PIPE_W, 26f)
        c.sprite(art.lip, pxi - 6f, top - 26f)
        c.sprite(art.lip, pxi - 6f, bottomY)
        // grounded: a little contact shadow at the foot of the bottom pipe
        c.shadow(pxi + PIPE_W / 2f, GROUND + 2f, PIPE_W * 0.75f, 0.4f, 6f)
    }

    private fun wing(c: Canvas, side: Float, beat: Float, alpha: Float) {
        c.save()
        c.translate(x - 4f, y - 6f)
        c.rotate(angle * DEG)
        c.scale(side, 1f)
        c.rotate((-0.4f + beat * 0.6f) * DEG)
        c.sprite(art.wing, -2f, -16f, alpha = alpha)
        c.restore()
    }

    /* ---------- art, painted once per run ---------- */

    private val SUN_GLOW = col("#ff9a5c")
    private val MOTE = col("#ffc27a")
    private val VIGNETTE = col("#12061f")
    private val art = Art()

    private class Art {
        val sky by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(
                    0f, 0f, W, H,
                    fill().shade(
                        lin(
                            0f, 0f, 0f, GROUND,
                            0f to col("#141a45"), 0.32f to col("#3a2f72"), 0.58f to col("#8a4f8e"),
                            0.78f to col("#e9787e"), 0.92f to col("#ffb27a"), 1f to col("#ffd49a"),
                        ),
                    ),
                )
                // faint stars in the high, dark part of the sky
                val star = fill(WHITE)
                for (i in 0 until 70) {
                    val sy = hash(i * 2.3f) * 300f
                    star.alpha = (clamp((1f - sy / 300f) * (0.35f + hash(i + 40f) * 0.6f), 0f, 1f) * 255f).toInt()
                    val s = if (hash(i + 80f) < 0.15f) 2f else 1.2f
                    val sx = hash(i * 1.7f + 4f) * W
                    c.drawRect(sx, sy, sx + s, sy + s, star)
                }
                // the sun disc with a hot core
                c.drawCircle(SUN_X, SUN_Y, 44f, fill().shade(rad(SUN_X, SUN_Y, 0f, 46f, 0f to col("#fffbe8"), 0.6f to col("#ffe2a6"), 1f to col("#ffc27a"))))
                // a warm haze band along the horizon
                c.drawRect(0f, GROUND - 260f, W, GROUND, fill().shade(lin(0f, GROUND - 260f, 0f, GROUND, 0f to rgba(255, 190, 140, 0f), 1f to rgba(255, 200, 150, 0.35f))))
            }
        }

        // three hill silhouettes, far (hazy, sunlit) to near (dark)
        private class Ridge(val h: Float, val top: Float, val amp: FloatArray, val k: FloatArray, val fill: IntArray, val rim: Int, val trees: Boolean)

        private val RIDGES = arrayOf(
            Ridge(340f, 70f, floatArrayOf(46f, 22f, 9f), floatArrayOf(2f, 5f, 13f), intArrayOf(col("#9a5e93"), col("#c77d8f")), rgba(255, 214, 170, 0.75f), false),
            Ridge(240f, 70f, floatArrayOf(34f, 16f, 7f), floatArrayOf(3f, 7f, 17f), intArrayOf(col("#5a3a73"), col("#7e4b80")), rgba(255, 170, 140, 0.6f), false),
            Ridge(170f, 62f, floatArrayOf(26f, 12f, 6f), floatArrayOf(4f, 9f, 23f), intArrayOf(col("#2b1f48"), col("#3a2858")), 0, true),
        )

        val ridges: Array<Sprite> by lazy { Array(3) { ridge(it) } }

        private fun ridge(i: Int): Sprite {
            val r = RIDGES[i]
            fun yAt(x: Float): Float {
                var s = r.top
                for (j in 0 until 3) s += r.amp[j] * sin((x / TILE) * TAU * r.k[j] + j * 1.7f + i)
                return s
            }
            return makeSprite(TILE, r.h) { c ->
                val path = Path()
                path.moveTo(0f, r.h)
                var x = 0f
                while (x <= TILE) {
                    path.lineTo(x, yAt(x))
                    x += 4f
                }
                path.lineTo(TILE, r.h)
                path.close()
                c.drawPath(path, fill().shade(lin(0f, 0f, 0f, r.h, 0f to r.fill[1], 1f to r.fill[0])))
                if (r.trees) {
                    // a row of round treetops riding the near ridge
                    val p = fill(r.fill[0])
                    var tx = 0f
                    while (tx < TILE) {
                        val rad = 12f + hash(tx * 0.37f + i) * 12f
                        val ty = yAt(tx + 11f) + 8f
                        for (o in floatArrayOf(0f, -TILE, TILE)) c.drawCircle(tx + 11f + o, ty, rad, p)
                        tx += 22f
                    }
                }
                if (!r.trees) {
                    // the sun catches the top edge
                    val rim = Path()
                    var rx = 0f
                    while (rx <= TILE) {
                        if (rx == 0f) rim.moveTo(rx, yAt(rx) + 1f) else rim.lineTo(rx, yAt(rx) + 1f)
                        rx += 4f
                    }
                    c.drawPath(rim, stroke(r.rim, 3f).apply { xfermode = ATOP })
                }
                // haze towards the bottom: further hills melt into the air
                c.drawRect(0f, 0f, TILE, r.h, fill().shade(lin(0f, 0f, 0f, r.h, 0f to rgba(255, 170, 150, 0f), 1f to rgba(255, 170, 150, 0.28f - i * 0.08f))).apply { xfermode = ATOP })
            }
        }

        // fluffy clouds lit from below by the setting sun
        val clouds: Array<Sprite> by lazy {
            val parts = arrayOf(
                arrayOf(floatArrayOf(34f, 48f, 22f), floatArrayOf(62f, 34f, 28f), floatArrayOf(96f, 40f, 24f), floatArrayOf(122f, 50f, 18f), floatArrayOf(80f, 54f, 22f)),
                arrayOf(floatArrayOf(30f, 50f, 18f), floatArrayOf(56f, 38f, 24f), floatArrayOf(86f, 32f, 22f), floatArrayOf(114f, 46f, 22f), floatArrayOf(138f, 54f, 14f)),
                arrayOf(floatArrayOf(44f, 46f, 20f), floatArrayOf(74f, 36f, 26f), floatArrayOf(104f, 48f, 20f)),
            )
            Array(3) { v ->
                makeSprite(160f, 80f) { c ->
                    val path = Path()
                    for (p in parts[v]) path.addCircle(p[0], p[1], p[2], Path.Direction.CW)
                    val paint = fill().shade(lin(0f, 8f, 0f, 74f, 0f to col("#6f5a9e"), 0.55f to col("#b5789f"), 1f to col("#ffb58f")))
                    paint.alpha = (0.9f * 255).toInt()
                    c.drawPath(path, paint)
                }
            }
        }

        val ground by lazy {
            val h = H - GROUND + 10f
            makeSprite(TILE, h) { c ->
                c.drawRect(0f, 10f, TILE, h, fill().shade(lin(0f, 10f, 0f, h, 0f to col("#4a2f3e"), 1f to col("#1c1224"))))
                // pebbles in the soil
                val peb = fill()
                val oval = RectF()
                for (i in 0 until 90) {
                    val fi = i.toFloat()
                    peb.color = rgba(255, 200, 170, 0.05f + hash(fi + 3f) * 0.08f)
                    val ex = hash(fi) * TILE
                    val ey = 30f + hash(fi + 1f) * (h - 34f)
                    val rx = 2f + hash(fi + 2f) * 4f
                    val ry = 1.5f + hash(fi + 2f) * 2f
                    oval.set(ex - rx, ey - ry, ex + rx, ey + ry)
                    c.drawOval(oval, peb)
                }
                // the grass lip, sunlit on top, with tufts
                val lip = fill().shade(lin(0f, 4f, 0f, 26f, 0f to col("#9ad06a"), 0.35f to col("#4f9a4a"), 1f to col("#2b5a36")))
                c.drawRect(0f, 10f, TILE, 26f, lip)
                val tufts = Path()
                var x = 0f
                while (x < TILE) {
                    val hh = 5f + hash(x * 0.13f) * 7f
                    tufts.moveTo(x, 12f)
                    tufts.lineTo(x + 4f, 12f - hh)
                    tufts.lineTo(x + 8f, 12f)
                    tufts.close()
                    x += 8f
                }
                c.drawPath(tufts, lip)
                c.drawRect(0f, 10f, TILE, 12f, fill(rgba(255, 225, 160, 0.55f)))
                c.drawRect(0f, 26f, TILE, 29f, fill(rgba(0, 0, 0, 0.25f)))
            }
        }

        private fun stripPaint(w: Float) = fill().shade(
            lin(
                0f, 0f, w, 0f,
                0f to col("#1d5a52"), 0.12f to col("#2f8c74"), 0.3f to col("#7fe0b0"), 0.38f to col("#d8ffe6"),
                0.46f to col("#6fd2a2"), 0.78f to col("#2a7a68"), 0.9f to col("#1b4d4a"), 1f to col("#a8706a"),
            ),
        )

        // the pipe body as a short cylinder strip, stretched to any height
        val pipeStrip by lazy { makeSprite(PIPE_W, 4f) { c -> c.drawRect(0f, 0f, PIPE_W, 4f, stripPaint(PIPE_W)) } }

        val lip by lazy {
            val w = PIPE_W + 12f
            makeSprite(w, 26f) { c ->
                val box = RectF(0f, 0f, w, 26f)
                c.drawRoundRect(box, 6f, 6f, stripPaint(w))
                c.drawRoundRect(
                    box, 6f, 6f,
                    fill().shade(lin(0f, 0f, 0f, 26f, 0f to rgba(255, 255, 255, 0.35f), 0.2f to rgba(255, 255, 255, 0f), 0.8f to rgba(0, 0, 0, 0f), 1f to rgba(0, 20, 20, 0.35f))),
                )
                c.drawRoundRect(RectF(1f, 1f, w - 1f, 25f), 5f, 5f, stroke(rgba(10, 40, 40, 0.55f), 2f))
            }
        }

        // a soft dark band: the lip's shadow on the pipe body
        val lipShade by lazy {
            makeSprite(4f, 32f, 1f) { c ->
                c.drawRect(0f, 0f, 4f, 32f, fill().shade(lin(0f, 0f, 0f, 32f, 0f to rgba(5, 25, 30, 0.5f), 1f to rgba(5, 25, 30, 0f))))
            }
        }

        // a feathered wing: three lobes with a soft gradient
        val wing by lazy {
            makeSprite(64f, 32f) { c ->
                c.translate(4f, 16f)
                val path = Path()
                fun lobe(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float) {
                    val p = Path()
                    p.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
                    val m = android.graphics.Matrix()
                    m.setRotate(rot * DEG, cx, cy)
                    p.transform(m)
                    path.op(p, Path.Op.UNION)
                }
                lobe(28f, -1f, 26f, 10f, 0f)
                lobe(36f, 4f, 20f, 7f, 0.15f)
                lobe(42f, 8f, 14f, 5f, 0.3f)
                c.drawPath(path, fill().shade(lin(0f, -12f, 0f, 12f, 0f to WHITE, 0.6f to col("#e9eefc"), 1f to col("#b9c3e6"))))
                c.drawPath(path, stroke(rgba(90, 90, 140, 0.55f), 1.5f))
                val vein = Path()
                vein.moveTo(8f, 0f)
                vein.quadTo(30f, 2f, 50f, 6f)
                c.drawPath(vein, stroke(rgba(120, 120, 170, 0.35f), 1.5f))
            }
        }
    }
}
