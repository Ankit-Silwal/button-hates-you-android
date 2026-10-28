package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Snack Attack: drag the button left and right to catch falling snacks and
// dodge the bombs. Three lives; a missed snack costs nothing.
// Port of src/adventure/games/catch.js.
class CatchGame(private val ctx: GameContext) : Game {
    private companion object {
        const val GROUND = H - 64f
        const val SIZE = 78f
        const val LIVES = 3
        const val MAX_ITEMS = 48
        const val ART_R = 22f

        const val DONUT = 0
        const val COOKIE = 1
        const val CAKE = 2
        const val APPLE = 3
        const val GOLD = 4
        const val BOMB = 5

        val IDLE = arrayOf("Feed me. Not those.", "I only eat out of spite.", "Do NOT drop the cake.", "Faster, servant.", "This is beneath me.")
        val YUM = arrayOf("Mine.", "Adequate.", "More.", "Barely edible.", "I hate that I liked that.")
        val GOLDS = arrayOf("GOLD. Finally.", "Now we are talking.", "Fancy.")
        val MISS = arrayOf("That was MY snack.", "Wasteful.", "Pick it up. No, don’t.", "Floor food. Great.")
        val BOMBED = arrayOf("That was not a snack!", "OW. Spicy.", "You moved me INTO it.", "Rude.")
        val DEAD = arrayOf("I’m full. Of bombs.", "Never cooking again.", "Worst picnic ever.")

        const val SUN_X = 370f
        const val SUN_Y = 250f
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val crumbs = intArrayOf(col("#e0a35a"), col("#c98a4b"), col("#ffb3c9"), col("#e8473d"), col("#ffd84d"), col("#555555"))

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private var meX = W / 2f
    private val meY = GROUND - SIZE * 0.53f

    // falling things, as parallel arrays
    private val ix = FloatArray(MAX_ITEMS)
    private val iy = FloatArray(MAX_ITEMS)
    private val ivy = FloatArray(MAX_ITEMS)
    private val ikind = IntArray(MAX_ITEMS)
    private val ir = FloatArray(MAX_ITEMS)
    private val ispin = FloatArray(MAX_ITEMS)
    private val iturn = FloatArray(MAX_ITEMS)
    private val idone = BooleanArray(MAX_ITEMS)
    private var items = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var lives = LIVES
    private var t = 0f
    private var next = 1f // seconds until the next drop
    private var chat = 3f
    private var held = false
    private var target = meX
    private var squash = 0f
    private var chomp = 0f
    private var hurt = 0f
    private var shake = 0f
    private var safe = 0f // bombs pass through for a moment after a Continue

    // the longer you last, the faster and thicker things fall
    private fun interval() = clamp(0.95f - t * 0.01f, 0.38f, 0.95f)
    private fun fall() = min(190f + t * 4.5f, 470f) * rand(0.9f, 1.15f)

    private fun drop() {
        if (items >= MAX_ITEMS) return
        val bomb = Random.nextFloat() < clamp(0.18f + t * 0.003f, 0.18f, 0.34f)
        val kind = if (bomb) BOMB else if (Random.nextFloat() < 0.06f) GOLD else Random.nextInt(4)
        val i = items++
        ix[i] = rand(36f, W - 36f)
        iy[i] = -30f
        ivy[i] = fall()
        ikind[i] = kind
        ir[i] = 22f
        ispin[i] = rand(0f, 6f)
        iturn[i] = rand(-2f, 2f)
        idone[i] = false
    }

    private fun sweep() {
        var n = 0
        for (i in 0 until items) {
            if (idone[i]) continue
            if (n != i) {
                ix[n] = ix[i]; iy[n] = iy[i]; ivy[n] = ivy[i]; ikind[n] = ikind[i]
                ir[n] = ir[i]; ispin[n] = ispin[i]; iturn[n] = iturn[i]; idone[n] = false
            }
            n++
        }
        items = n
    }

    private fun eat(i: Int) {
        val gold = ikind[i] == GOLD
        score += if (gold) 3 else 1
        squash = 0.2f
        chomp = 0.25f
        fx.sparks(meX, meY - SIZE * 0.2f, crumbs[ikind[i]], if (gold) 14 else 6, 140f)
        fx.float(meX, meY - SIZE * 0.8f, if (gold) "+3" else "+1", if (gold) crumbs[GOLD] else WHITE)
        if (gold) {
            ctx.sfx.play("coin")
            ctx.haptics.tap()
            mouth.say(pick(GOLDS))
        } else {
            ctx.sfx.play("pop")
            ctx.haptics.tap()
            mouth.maybe(YUM, 0.12f)
        }
    }

    private fun boom(i: Int) {
        lives--
        hurt = 0.6f
        shake = 0.35f
        ctx.sfx.play("boom")
        ctx.haptics.thud()
        fx.sparks(ix[i], iy[i], col("#ff9a3c"), 18, 300f)
        fx.sparks(ix[i], iy[i], col("#555555"), 10, 200f)
        if (lives > 0) {
            mouth.say(pick(BOMBED))
            return
        }
        over = true
        ctx.sfx.play("bad")
        mouth.say(pick(DEAD), 3f)
    }

    // while the pointer is held, the button chases it
    override fun down(x: Float, y: Float) {
        held = true
        target = x
    }

    override fun move(x: Float, y: Float) {
        if (held) target = x
    }

    override fun up(x: Float, y: Float) {
        held = false
    }

    // a paid Continue: one life back, the bombs in the air gone, a moment of safety
    override fun revive() {
        over = false
        lives = max(lives, 1)
        hurt = 0f
        safe = 2f
        next = 0.8f
        for (i in 0 until items) {
            if (ikind[i] != BOMB) continue
            idone[i] = true
            fx.sparks(ix[i], iy[i], col("#cfd6e2"), 6, 140f)
        }
        sweep()
        reviveFx(fx, mouth, ctx, meX, meY, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        squash = max(0f, squash - dt)
        chomp = max(0f, chomp - dt)
        hurt = max(0f, hurt - dt)
        shake = max(0f, shake - dt)
        safe -= dt

        if (!over) {
            if (held) meX += (target - meX) * min(1f, dt * 14f)
            meX = clamp(meX, SIZE / 2f, W - SIZE / 2f)

            next -= dt
            if (next <= 0f) {
                drop()
                next = interval()
            }
            chat -= dt
            if (chat <= 0f) {
                mouth.maybe(IDLE, 0.5f)
                chat = rand(5f, 9f)
            }
        }

        for (i in 0 until items) {
            if (idone[i]) continue
            ivy[i] += 60f * dt
            iy[i] += ivy[i] * dt
            ispin[i] += iturn[i] * dt
            // the catch zone is the top half of the button, around its mouth
            val caught = !over &&
                iy[i] + ir[i] > meY - SIZE * 0.45f &&
                iy[i] < meY + SIZE * 0.25f &&
                abs(ix[i] - meX) < SIZE * 0.5f + ir[i] * 0.4f
            if (caught && ikind[i] == BOMB && safe > 0f) continue
            if (caught) {
                idone[i] = true
                if (ikind[i] == BOMB) boom(i) else eat(i)
            } else if (iy[i] - ir[i] * 0.5f > GROUND) {
                idone[i] = true
                if (ikind[i] == BOMB) {
                    ctx.sfx.play("thud")
                    fx.sparks(ix[i], GROUND, col("#777777"), 8, 160f)
                } else {
                    fx.sparks(ix[i], GROUND, crumbs[ikind[i]], 6, 120f)
                    if (!over) mouth.maybe(MISS, 0.25f)
                }
            }
        }
        sweep()
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        c.save()
        if (shake > 0f) c.translate(rand(-6f, 6f) * shake * 3f, rand(-6f, 6f) * shake * 3f)
        // a golden-hour picnic: warm sky, slow sun rays, hazy hills
        c.sprite(art.sky, -10f, -10f, W + 20f, H + 20f)
        c.save()
        c.translate(SUN_X, SUN_Y)
        c.rotate(t * 0.04f * DEG)
        c.sprite(art.rays, -380f, -380f, 760f, 760f, 0.16f, add = true)
        c.restore()
        c.glow(SUN_X, SUN_Y, 140f, SUN_GLOW, 0.28f)
        for (i in 0 until 4) {
            val x = ((i * 150f + t * 12f) % (W + 160f)) - 80f
            c.sprite(art.clouds[i % 2], x - 30f, 200f + (i % 2) * 110f - 46f)
        }
        c.sprite(art.hills, -10f, GROUND - 230f)
        c.sprite(art.blanket, -10f, GROUND - 6f)

        // falling things cast a shadow that sharpens as they get close
        for (i in 0 until items) {
            val near = clamp(1f - (GROUND - iy[i]) / 600f, 0f, 1f)
            c.shadow(ix[i], GROUND + 6f, ir[i] * (0.5f + near * 0.6f), 0.08f + near * 0.25f, 6f)
        }
        var incoming = false
        for (i in 0 until items) {
            item(c, i)
            if (iy[i] > meY - 190f && iy[i] < meY - SIZE * 0.2f && abs(ix[i] - meX) < SIZE * 0.8f) incoming = true
        }

        // the mouth opens when something is about to land on it
        c.shadow(meX, GROUND + 4f, SIZE * 0.5f, 0.4f, 9f)
        bobby(
            c, meX, meY, SIZE, look,
            mood = when {
                over -> Mood.Sad
                hurt > 0f -> Mood.Angry
                incoming && chomp <= 0f -> Mood.Scared
                chomp > 0f -> Mood.Happy
                else -> Mood.Smug
            },
            t = t, squash = squash, flash = hurt > 0.45f, tears = over, alpha = blinkAlpha(safe, t),
        )

        fx.draw(c)
        // a bomb hit flashes the whole frame hot orange for a moment
        if (hurt > 0.3f) c.vignette(0.95f, HOT, (hurt - 0.3f) / 0.3f)
        c.vignette(0.3f, WARM)
        mouth.draw(c, meX, meY - SIZE * 0.62f)
        c.restore()

        c.drawScore(score)
        for (i in 0 until LIVES) {
            val hx = W / 2f - 30f + i * 30f
            if (i < lives) c.softLight(hx, 130f, 22f, HEART, 0.45f)
            c.heart(hx, 130f, 10f, if (i < lives) HEART else HEART_OFF)
        }
        if (t < 3f && !over) Txt.draw(c, "Drag to move", W / 2f, 168f, 16f, HINT)
    }

    private fun item(c: Canvas, i: Int) {
        val kind = ikind[i]
        val x = ix[i]
        val y = iy[i]
        val r = ir[i]
        c.save()
        c.translate(x, y)
        if (kind == GOLD) c.glow(0f, 0f, r * 2.6f, crumbs[GOLD], 0.75f + sin(t * 6f) * 0.15f)
        // a bomb close to the button pulses red, so it reads at a glance
        if (kind == BOMB) {
            val danger = clamp(1f - dist(x, y, meX, meY) / 300f, 0f, 1f)
            if (danger > 0f) c.softLight(0f, 0f, r * 2.4f, DANGER, danger * (0.5f + sin(t * 18f) * 0.3f))
        }
        c.rotate((if (kind == BOMB) sin(ispin[i]) * 0.3f else ispin[i]) * DEG)
        val s = (r / ART_R) * 40f
        c.sprite(art.items[kind], -s, -s, s * 2f, s * 2f)
        if (kind == BOMB) {
            // the lit fuse flickers and spits light
            val f = 4f + abs(sin(t * 30f)) * 4f
            c.glow(14f, -r - 10f, f * 3.2f, FUSE, 0.9f)
            dot.color = FUSE_CORE
            c.drawCircle(14f, -r - 10f, f * 0.45f, dot)
        } else if (kind == GOLD) {
            dot.color = WHITE
            val tw = 2f + abs(sin(t * 6f)) * 3f
            c.drawRect(r * 0.5f - tw / 2f, -r * 0.9f, r * 0.5f + tw / 2f, -r * 0.9f + tw, dot)
        }
        c.restore()
    }

    /* ---------- art ---------- */

    private val dot = fill(WHITE)
    private val SUN_GLOW = col("#ffb347")
    private val HOT = col("#ff5a1a")
    private val WARM = col("#4a1a08")
    private val HEART = col("#ff4f7b")
    private val HEART_OFF = rgba(255, 255, 255, 0.45f)
    private val HINT = rgba(80, 50, 30, 0.8f)
    private val DANGER = col("#ff2a2a")
    private val FUSE = col("#ffb02a")
    private val FUSE_CORE = col("#fff3b0")
    private val art = Art()

    private class Art {
        val sky by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, GROUND, 0f to col("#f08a6a"), 0.35f to col("#ffb27a"), 0.7f to col("#ffd79a"), 1f to col("#fff0cf"))))
                c.drawRect(0f, 0f, W, H, fill().shade(rad(SUN_X, SUN_Y, 0f, 340f, 0f to rgba(255, 250, 215, 0.6f), 0.25f to rgba(255, 225, 160, 0.4f), 1f to rgba(255, 225, 160, 0f))))
                c.drawCircle(SUN_X, SUN_Y, 46f, fill().shade(rad(SUN_X, SUN_Y, 0f, 48f, 0f to col("#fffef2"), 0.7f to col("#fff2bf"), 1f to col("#ffe08a"))))
            }
        }

        val rays by lazy {
            makeSprite(256f, 256f, 1f) { c ->
                c.translate(128f, 128f)
                val p = fill().shade(lin(0f, 0f, 128f, 0f, 0f to rgba(255, 240, 200, 0.9f), 1f to rgba(255, 240, 200, 0f)))
                val path = Path()
                for (i in 0 until 12) {
                    c.rotate((TAU / 12f + (hash(i.toFloat()) - 0.5f) * 0.25f) * DEG)
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.lineTo(128f, -6f - hash(i + 3f) * 10f)
                    path.lineTo(128f, 6f + hash(i + 3f) * 10f)
                    path.close()
                    c.drawPath(path, p)
                }
            }
        }

        // clouds lit warm on top with a rosy belly
        val clouds: Array<Sprite> by lazy {
            Array(2) { v ->
                makeSprite(140f, 90f) { c ->
                    c.translate(30f, 46f)
                    val path = Path()
                    val parts = if (v == 1) {
                        arrayOf(floatArrayOf(0f, 0f, 22f), floatArrayOf(24f, -10f, 26f), floatArrayOf(50f, 0f, 20f), floatArrayOf(74f, 6f, 14f))
                    } else {
                        arrayOf(floatArrayOf(0f, 0f, 22f), floatArrayOf(24f, -10f, 26f), floatArrayOf(50f, 0f, 20f))
                    }
                    for (p in parts) path.addCircle(p[0], p[1], p[2], Path.Direction.CW)
                    val paint = fill().shade(lin(0f, -36f, 0f, 22f, 0f to col("#fffaf0"), 0.6f to col("#ffe6cf"), 1f to col("#f6b7a2")))
                    paint.alpha = (0.92f * 255).toInt()
                    c.drawPath(path, paint)
                }
            }
        }

        // two hazy hill layers with a few round trees, and a meadow at the front
        val hills by lazy {
            makeSprite(W + 20f, 240f) { c ->
                c.translate(10f, 0f)
                fun hill(cx: Float, cy: Float, r: Float, top: Int, bottom: Int) {
                    val path = Path()
                    path.arcTo(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f), 180f, 180f)
                    path.lineTo(cx + r, 240f)
                    path.lineTo(cx - r, 240f)
                    path.close()
                    c.drawPath(path, fill().shade(lin(0f, cy - r, 0f, 240f, 0f to top, 1f to bottom)))
                }
                hill(380f, 160f, 260f, col("#d9c79a"), col("#c9b98e"))
                hill(40f, 175f, 220f, col("#cfc293"), col("#c2b688"))
                val tree = fill(rgba(120, 130, 80, 0.55f))
                for (i in 0 until 9) {
                    c.drawCircle(20f + hash(i + 11f) * W, 120f + hash(i + 2f) * 40f, 8f + hash(i.toFloat()) * 6f, tree)
                }
                hill(90f, 240f, 220f, col("#a8d47e"), col("#6fae5a"))
                hill(410f, 250f, 190f, col("#b2da84"), col("#78b55e"))
                c.drawRect(-10f, 0f, W + 20f, 240f, fill().shade(lin(0f, 120f, 0f, 240f, 0f to rgba(255, 240, 180, 0.5f), 0.4f to rgba(255, 240, 180, 0f))).apply { xfermode = ATOP })
                c.drawRect(-10f, 0f, W + 20f, 240f, fill().shade(lin(0f, 100f, 0f, 240f, 0f to rgba(255, 230, 190, 0f), 1f to rgba(255, 230, 190, 0.15f))).apply { xfermode = ATOP })
            }
        }

        // the checked picnic blanket, with soft folds and shading towards the front
        val blanket by lazy {
            val h = H - GROUND + 16f
            makeSprite(W + 20f, h) { c ->
                c.translate(10f, 6f)
                c.drawRect(-10f, 0f, W + 10f, h, fill(col("#fff8ee")))
                val red = fill(col("#e04f45"))
                for (y in 0 until 3) for (x in -1 until 16) if ((x + y) % 2 != 0) c.drawRect(x * 32f, y * 24f, x * 32f + 32f, y * 24f + 24f, red)
                val thread = fill(rgba(200, 40, 40, 0.25f))
                var y = 0f
                while (y < 72f) {
                    var x = -32f
                    while (x < W + 10f) {
                        c.drawRect(x + 15f, y, x + 17f, y + 24f, thread)
                        c.drawRect(x, y + 11f, x + 32f, y + 13f, thread)
                        x += 32f
                    }
                    y += 24f
                }
                val folds = Array(7) { i -> (i / 6f) to (if (i % 2 == 1) rgba(80, 20, 10, 0.10f) else rgba(255, 255, 255, 0.08f)) }
                c.drawRect(-10f, 0f, W + 10f, h, fill().shade(lin(0f, 0f, W, 0f, *folds)))
                c.drawRect(-10f, 0f, W + 10f, h, fill().shade(lin(0f, 0f, 0f, h, 0f to rgba(255, 240, 200, 0.35f), 0.15f to rgba(255, 240, 200, 0f), 1f to rgba(70, 20, 10, 0.35f))))
                c.drawRect(-10f, -6f, W + 10f, 0f, fill(rgba(90, 40, 10, 0.25f)))
            }
        }

        // every snack is painted once with shading on top, then just rotated
        val items: Array<Sprite> by lazy { Array(6) { itemArt(it) } }

        private fun itemArt(kind: Int): Sprite = makeSprite(80f, 80f) { c ->
            c.translate(40f, 40f)
            val r = ART_R
            when (kind) {
                DONUT -> donut(c, r, col("#ff8fb8"), col("#e0a35a"))
                GOLD -> donut(c, r, col("#ffd84d"), col("#e0a92a"))
                COOKIE -> cookie(c, r)
                CAKE -> cake(c, r)
                APPLE -> apple(c, r)
                else -> bomb(c, r)
            }
            // round shading over whatever was drawn: lit top left, shadowed bottom right
            val g = fill().shade(
                rad(
                    -r * 0.2f, -r * 0.25f, 2f, r * 1.5f,
                    0f to rgba(255, 255, 255, 0.35f), 0.45f to rgba(255, 255, 255, 0f), 0.8f to rgba(40, 10, 20, 0.12f), 1f to rgba(40, 10, 20, 0.3f),
                ),
            )
            g.xfermode = ATOP
            c.drawRect(-40f, -40f, 40f, 40f, g)
        }

        private fun donut(c: Canvas, r: Float, icing: Int, dough: Int) {
            val ring = Path()
            ring.fillType = Path.FillType.EVEN_ODD
            ring.addCircle(0f, 0f, r, Path.Direction.CW)
            ring.addCircle(0f, 0f, r * 0.32f, Path.Direction.CW)
            c.drawPath(ring, fill(dough))
            val ice = Path()
            ice.fillType = Path.FillType.EVEN_ODD
            for (i in 0..24) {
                val a = i / 24f * TAU
                val rad = r * (0.8f + sin(i * 2.3f) * 0.06f)
                if (i == 0) ice.moveTo(cos(a) * rad, sin(a) * rad) else ice.lineTo(cos(a) * rad, sin(a) * rad)
            }
            ice.close()
            ice.addCircle(0f, 0f, r * 0.42f, Path.Direction.CW)
            c.drawPath(ice, fill(icing))
            val colors = intArrayOf(WHITE, col("#5bc0eb"), col("#ffe066"), col("#7bd389"))
            val sprinkle = stroke(WHITE, 2.5f)
            for (i in 0 until 8) {
                val a = i * 0.8f + 0.3f
                val rad = r * 0.6f
                sprinkle.color = colors[i % 4]
                c.drawLine(cos(a) * rad, sin(a) * rad, cos(a) * rad + cos(a * 3f) * 4f, sin(a) * rad + sin(a * 3f) * 4f, sprinkle)
            }
        }

        private fun cookie(c: Canvas, r: Float) {
            c.drawCircle(0f, 0f, r, fill(col("#d9a066")))
            c.drawCircle(0f, 0f, r, stroke(col("#b07a3f"), 3f))
            val chip = fill(col("#4a2c1a"))
            val pts = floatArrayOf(-8f, -7f, 6f, -10f, 9f, 4f, -4f, 8f, -11f, 3f, 1f, -1f)
            for (i in pts.indices step 2) c.drawCircle(pts[i] * (r / 22f), pts[i + 1] * (r / 22f), 3f, chip)
        }

        private fun cake(c: Canvas, r: Float) {
            c.drawRoundRect(RectF(-r, -r * 0.35f, r, r * 0.85f), 5f, 5f, fill(col("#f6d7a7")))
            c.drawRect(-r, r * 0.25f, r, r * 0.25f + 4f, fill(col("#fff4e6")))
            val pink = fill(col("#ff8fb8"))
            c.drawRoundRect(RectF(-r, -r * 0.5f, r, -r * 0.05f), 6f, 6f, pink)
            for (x in floatArrayOf(-0.6f, -0.1f, 0.45f)) c.drawCircle(x * r, -r * 0.05f, 4f, pink)
            c.drawCircle(0f, -r * 0.68f, 6f, fill(col("#d8233a")))
            val stem = Path()
            stem.moveTo(0f, -r * 0.68f - 5f)
            stem.quadTo(3f, -r - 4f, 7f, -r - 6f)
            c.drawPath(stem, stroke(col("#3c7a2a"), 2f))
        }

        private fun apple(c: Canvas, r: Float) {
            val red = fill(col("#e8473d"))
            c.drawCircle(-r * 0.32f, 0f, r * 0.72f, red)
            c.drawCircle(r * 0.32f, 0f, r * 0.72f, red)
            c.save()
            c.rotate(-0.4f * DEG, -r * 0.45f, -r * 0.3f)
            c.drawOval(RectF(-r * 0.45f - 4f, -r * 0.3f - 7f, -r * 0.45f + 4f, -r * 0.3f + 7f), fill(rgba(255, 255, 255, 0.4f)))
            c.restore()
            c.drawLine(0f, -r * 0.55f, 2f, -r * 0.95f, stroke(col("#5a3a1e"), 3f))
            c.save()
            c.rotate(-0.5f * DEG, 9f, -r * 0.85f)
            c.drawOval(RectF(1f, -r * 0.85f - 4f, 17f, -r * 0.85f + 4f), fill(col("#5cb85c")))
            c.restore()
        }

        private fun bomb(c: Canvas, r: Float) {
            c.drawCircle(0f, 2f, r, fill().shade(rad(-r * 0.2f, -r * 0.1f, 1f, r * 1.1f, 0f to col("#5a5668"), 0.5f to col("#2a2734"), 1f to col("#16141c"))))
            c.save()
            c.rotate(0.6f * DEG, -r * 0.4f, -r * 0.25f)
            c.drawOval(RectF(-r * 0.4f - 5f, -r * 0.25f - 8f, -r * 0.4f + 5f, -r * 0.25f + 8f), fill(rgba(255, 255, 255, 0.35f)))
            c.restore()
            c.drawRect(-6f, -r - 4f, 6f, -r + 4f, fill(col("#4a4655")))
            val fuse = Path()
            fuse.moveTo(0f, -r - 4f)
            fuse.quadTo(8f, -r - 14f, 14f, -r - 10f)
            c.drawPath(fuse, stroke(col("#b08a5a"), 3f))
        }
    }
}
