package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// Tower of Regret: drop swinging button clones on top of each other. Whatever
// hangs over the edge is cut off and falls away, screaming.
// Port of src/adventure/games/stack.js.
class StackGame(private val ctx: GameContext) : Game {
    private companion object {
        const val GROUND = H - 70f
        const val SLAB_H = 44f
        const val START_W = 250f
        const val HOVER = 110f // how far above the tower the next clone swings
        const val SWING = 190f // how far left and right of center it swings
        const val GRAVITY = 2400f
        const val PERFECT = 6f
        const val HUE_STEP = 9
        const val WATCHER = 54f
        const val SPAD = 10f

        // the slab faces' moods (web: calm, sad, angry, worried, smug, closed; shout when falling)
        const val CALM = 0
        const val SAD = 1
        const val ANGRY = 2
        const val WORRIED = 3
        const val SMUG = 4
        const val CLOSED = 5
        const val SHOUT = 6

        val SCREAMS = arrayOf("AAA!", "Nooo!", "Bye!", "Eek!", "Why?!")
        val DROPS = arrayOf("That one had a family.", "Higher. Great. Wonderful.", "Stop stacking my clones.", "My back hurts.", "This is a cry for help.")
        val PERFECTS = arrayOf("Show-off.", "Lucky.", "Ugh. Perfect.", "Nobody saw that.")
        val CRASH = arrayOf("Ha! Timber.", "Told you.", "Finally.", "Gravity agrees with me.")

        val DAY = intArrayOf(col("#3d8fe0"), col("#8fcaf5"), col("#ffe2c0"))
        val DUSK = intArrayOf(col("#0b1038"), col("#3b2b78"), col("#d9708a"))
    }

    private class Floor(var x: Float, var w: Float, val mood: Int, val hue: Int) {
        var spr: Sprite? = null
    }

    private class Debris(
        var x: Float, var y: Float, val w: Float, var vx: Float, var vy: Float,
        var angle: Float, val va: Float, val hue: Int, val scream: String,
    ) {
        var spr: Sprite? = null
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val lookDark = look.dark.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val floors = ArrayList<Floor>().apply { add(Floor(W / 2f, START_W, CLOSED, 0)) } // the base, not counted
    private val debris = ArrayList<Debris>()
    private val mouth = Mouth()
    private val fx = Fx()
    private var flash = 0f // a perfect drop lights the tower top

    // the swinging clone
    private var slabX = W / 2f - SWING
    private var slabY = 0f
    private var slabW = START_W
    private var slabDir = 1f
    private var slabVy = 0f
    private var falling = false
    private var slabMood = CALM
    private var slabAngle = 0f
    private var slabVa = 0f

    private var started = false
    private var t = 0f
    private var cam = 0f // how far the view has risen
    private var shake = 0f
    private var scared = 0f // the watcher flinches for a moment after a cut

    private fun top() = GROUND - floors.size * SLAB_H // y of the tower's top edge
    private fun speed() = min(170f + score * 9f, 420f)
    private fun hueOf(n: Int) = (n * HUE_STEP) % 360

    private fun nextSlab() {
        val last = floors[floors.size - 1]
        slabW = last.w
        slabDir = if (Random.nextBoolean()) 1f else -1f
        slabX = W / 2f - slabDir * SWING
        slabY = top() - HOVER - SLAB_H
        slabVy = 0f
        falling = false
        slabMood = Random.nextInt(6)
    }

    // the piece that hangs over the edge tumbles off on its own
    private fun cutOff(x: Float, w: Float, side: Float) {
        debris.add(
            Debris(
                x, top() - SLAB_H / 2f, w, side * rand(60f, 140f), rand(-220f, -120f),
                0f, side * rand(2f, 5f), hueOf(floors.size), pick(SCREAMS),
            ),
        )
    }

    private fun land() {
        val last = floors[floors.size - 1]
        val off = slabX - last.x
        if (abs(off) >= (slabW + last.w) / 2f) {
            // missed the tower entirely: it keeps falling, and so does the run
            over = true
            falling = true
            slabVy = 100f
            slabVa = (if (off == 0f) 1f else sign(off)) * 3f
            ctx.sfx.play("bad")
            ctx.haptics.thud()
            shake = 0.4f
            mouth.say(pick(CRASH), 3f)
            return
        }

        var x = slabX
        var w = slabW
        if (abs(off) <= PERFECT) {
            x = last.x
            ctx.sfx.play("coin")
            fx.burst(x, top(), col("#fff6c8"), 22, 340f)
            fx.ring(x, top(), WHITE, slabW * 0.8f, 0.45f, 8f)
            flash = 0.5f
            fx.float(x, top() - 50f, "Perfect!", col("#fff6a8"))
            mouth.say(pick(PERFECTS), 1.6f)
        } else {
            val l = max(slabX - slabW / 2f, last.x - last.w / 2f)
            val r = min(slabX + slabW / 2f, last.x + last.w / 2f)
            val side = sign(off)
            w = r - l
            x = (l + r) / 2f
            val cutW = slabW - w
            cutOff(if (side > 0f) r + cutW / 2f else l - cutW / 2f, cutW, side)
            fx.burst(if (side > 0f) r else l, top() - SLAB_H / 2f, col("#fff1c0"), 10, 220f, size = 2.5f, life = 0.4f)
            ctx.sfx.play("thud")
            scared = 0.8f
            mouth.maybe(DROPS, 0.3f)
        }
        floors.add(Floor(x, max(1f, w), slabMood, hueOf(floors.size)))
        score++
        ctx.sfx.play("pop")
        ctx.haptics.tap()
        shake = 0.12f
        nextSlab()
    }

    private fun drop() {
        if (over || falling) return
        if (!started) mouth.say("Don't you dare.", 1.6f)
        started = true
        falling = true
        slabVy = 0f
        ctx.sfx.play("jump")
    }

    override fun down(x: Float, y: Float) = drop()

    // a paid Continue: the missed clone falls away, the top of the tower grows
    // back to a fair width, and a fresh clone swings in
    override fun revive() {
        over = false
        debris.add(Debris(slabX, slabY + SLAB_H / 2f, slabW, slabVa * 30f, slabVy, slabAngle, slabVa, hueOf(floors.size), pick(SCREAMS)))
        val last = floors[floors.size - 1]
        val grown = max(last.w, min(START_W * 0.75f, 2f * min(last.x, W - last.x) - 24f))
        if (grown != last.w) {
            last.w = grown
            last.spr = null
        }
        slabAngle = 0f
        slabVa = 0f
        nextSlab()
        flash = 0.5f
        fx.ring(last.x, top(), col("#aef3ff"), last.w * 0.8f, 0.45f, 8f)
        reviveFx(fx, mouth, ctx, last.x, top() - 10f, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        flash = max(0f, flash - dt)
        shake = max(0f, shake - dt)
        scared = max(0f, scared - dt)

        // the view rises so the tower top stays a little below the middle
        cam += (max(0f, 470f - top()) - cam) * min(1f, dt * 4f)

        for (d in debris) {
            d.vy += GRAVITY * 0.6f * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.angle += d.va * dt
        }
        if (debris.isNotEmpty() && debris[0].y + cam > H + 200f) debris.removeAt(0)

        if (falling) {
            slabVy += GRAVITY * dt
            slabY += slabVy * dt
            if (over) {
                slabAngle += slabVa * dt // the missed clone keeps tumbling
                slabX += slabVa * 30f * dt
            } else if (slabY + SLAB_H >= top()) {
                slabY = top() - SLAB_H
                land()
            }
            return
        }
        if (over) return

        slabX += slabDir * speed() * dt
        if (slabX > W / 2f + SWING) {
            slabX = W / 2f + SWING
            slabDir = -1f
        } else if (slabX < W / 2f - SWING) {
            slabX = W / 2f - SWING
            slabDir = 1f
        }
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        backdrop(c)

        c.save()
        c.translate(if (shake > 0f) rand(-3f, 3f) else 0f, cam)

        // far clouds drift past as the camera rises
        val cloudA = 0.8f - clamp(cam / 2600f, 0f, 1f) * 0.5f
        for (i in 0 until 12) {
            val cy = GROUND - 160f - i * 260f
            if (cy + cam < -80f || cy + cam > H + 80f) continue
            c.sprite(art.puff, ((i * 173f + t * 8f) % (W + 160f)) - 80f - 22f, cy - 44f, alpha = cloudA)
        }

        // the ground
        if (GROUND + cam < H) c.sprite(art.ground, -10f, GROUND)
        c.shadow(floors[0].x, GROUND + 4f, floors[0].w * 0.62f, 0.35f)

        for (i in floors.indices) {
            val f = floors[i]
            val y = GROUND - (i + 1) * SLAB_H
            if (y + cam > H + 20f || y + cam < -SLAB_H) continue
            val spr = f.spr ?: slab(f.w, f.hue).also { f.spr = it }
            clone(c, f.x, y + SLAB_H / 2f, f.w, spr, if (i > 0) f.mood else CLOSED, t + i)
        }
        val last = floors[floors.size - 1]
        if (flash > 0f) c.glow(last.x, top() + 6f, last.w * 0.9f, FLASH, flash * 1.6f)
        for (d in debris) {
            c.save()
            c.translate(d.x, d.y)
            c.rotate(d.angle * DEG)
            val spr = d.spr ?: slab(d.w, d.hue).also { d.spr = it }
            clone(c, 0f, 0f, d.w, spr, SHOUT, t)
            c.restore()
            if (d.vy < 500f) c.bubble(d.x, d.y - 26f, d.scream, size = 11f)
        }

        val lastY = top() + SLAB_H / 2f
        if (!falling && !over) {
            // a soft beam and a faint guide line from the swinging clone to the tower
            c.sprite(art.beam, slabX - slabW / 2f, slabY + SLAB_H, slabW, lastY - SLAB_H / 2f - (slabY + SLAB_H))
            c.drawLine(slabX, slabY + SLAB_H, slabX, lastY - SLAB_H / 2f, dash)
        }
        if (slabSpr == null || slabSprW != slabW || slabSprHue != hueOf(floors.size)) {
            slabSprW = slabW
            slabSprHue = hueOf(floors.size)
            slabSpr = slab(slabW, slabSprHue)
        }
        c.save()
        c.translate(slabX, slabY + SLAB_H / 2f)
        c.rotate(slabAngle * DEG)
        clone(c, 0f, 0f, slabW, slabSpr!!, if (over) SHOUT else slabMood, t)
        c.restore()
        fx.draw(c)
        c.restore()

        // the original button watches from the ground, and stays in view after it
        val wy = min(GROUND + cam, H + 6f) - WATCHER / 2f
        val mood = when {
            over -> Mood.Smug
            scared > 0f -> Mood.Scared
            score < 5 -> Mood.Calm
            score < 12 -> Mood.Worried
            else -> Mood.Sad
        }
        bobby(c, 52f, wy, WATCHER, look, mood, t, tears = !over && score >= 16)
        c.vignette(0.3f, VIGNETTE)
        mouth.draw(c, 52f, wy - WATCHER * 0.7f)

        if (started) c.drawScore(score, if (score >= 20) "My back. All our backs." else null)
        else c.drawScore("Tap to drop", "They scream. That is normal.")
    }

    // sky from day to dusk as the tower climbs, a skyline sinking below, and stars once it is dark
    private fun backdrop(c: Canvas) {
        val k = clamp(cam / 2600f, 0f, 1f)
        if (k < 1f) c.sprite(art.day, 0f, 0f, W, H)
        if (k > 0f) c.sprite(art.dusk, 0f, 0f, W, H, k)
        val night = clamp((k - 0.4f) * 2f, 0f, 1f)
        if (night > 0f) {
            for (i in 0 until 50) {
                val fi = i.toFloat()
                val sx = hash(fi) * W
                val sy = (((hash(fi + 40f) * H + cam * 0.04f) % H) + H) % H
                c.glow(sx, sy, 2.5f + hash(fi + 2f) * 4f, STAR, night * (0.55f + 0.45f * sin(t * 2f + fi)))
            }
        }
        // the sun sinks behind the city as the camera rises
        val sunY = 300f + cam * 0.25f
        if (sunY < H + 120f) {
            c.rays(W - 140f, sunY, 360f, SUN_RAYS, 0.2f * (1f - k), t * 0.03f)
            c.softLight(W - 140f, sunY, 200f, if (k > 0.3f) SUN_LATE else SUN_DAY, 0.6f)
            c.glow(W - 140f, sunY, 55f, SUN_CORE, 0.9f)
        }
        // two skylines, the far one hazier and slower
        val fy = GROUND - 250f + cam * 0.35f
        if (fy < H) c.sprite(art.skylines[0], 0f, fy, alpha = 1f - k * 0.6f)
        val ny = GROUND - 170f + cam * 0.65f
        if (ny < H) {
            c.sprite(art.skylines[1], 0f, ny)
            if (night > 0f) c.sprite(art.skylines[2], 0f, ny, alpha = night)
        }
    }

    // a squashed button clone, centred on (x, y), with a face
    private fun clone(c: Canvas, x: Float, y: Float, w: Float, spr: Sprite, mood: Int, tt: Float) {
        val h = SLAB_H - 4f
        val topY = y - h / 2f
        c.sprite(spr, x - w / 2f - SPAD, topY - SPAD, w + SPAD * 2f, h + SPAD * 2f)
        if (w > 14f) face(c, x, topY + h * 0.4f, min(1f, w / 70f), mood, tt)
    }

    // eyes and a mouth, `s` times the normal size
    private fun face(c: Canvas, x: Float, y: Float, s: Float, mood: Int, tt: Float) {
        val ex = 11f * s
        val blink = mood == CLOSED || (tt * 0.7f) % 4f > 3.88f
        for (side in -1..1 step 2) {
            val px = x + side * ex
            if (blink) {
                c.drawLine(px - 4f * s, y, px + 4f * s, y, ink)
                continue
            }
            oval.set(px - 4.5f * s, y - (if (mood == SHOUT) 6f else 4.5f) * s, px + 4.5f * s, y + (if (mood == SHOUT) 6f else 4.5f) * s)
            c.drawOval(oval, eyeWhite)
            c.drawCircle(px, y + 0.5f * s, 2.3f * s, pupil)
            if (mood == ANGRY || mood == SAD || mood == WORRIED) {
                val inner = if (mood == ANGRY) 1f else -1f
                c.drawLine(px - side * 4.5f * s, y - 7f * s + inner * 2f * s, px + side * 4.5f * s, y - 7f * s - inner * 2f * s, ink)
            }
        }
        val my = y + 10f * s
        when (mood) {
            SHOUT -> {
                oval.set(x - 4f * s, my - 4.5f * s, x + 4f * s, my + 4.5f * s)
                c.drawOval(oval, mouthFill)
            }
            CALM -> {
                oval.set(x - 4f * s, my - 7f * s, x + 4f * s, my + 1f * s)
                c.drawArc(oval, 0.3f * DEG, (PI_F - 0.6f) * DEG, false, mouthLine)
            }
            SAD, WORRIED -> {
                oval.set(x - 4f * s, my - 2f * s, x + 4f * s, my + 6f * s)
                c.drawArc(oval, (PI_F + 0.3f) * DEG, (PI_F - 0.6f) * DEG, false, mouthLine)
            }
            SMUG -> {
                mouthPath.reset()
                mouthPath.moveTo(x - 4f * s, my)
                mouthPath.quadTo(x + 2f * s, my + 2f * s, x + 5f * s, my - 2f * s)
                c.drawPath(mouthPath, mouthLine)
            }
            else -> c.drawLine(x - 4f * s, my, x + 4f * s, my, mouthLine)
        }
    }

    // a squashed clone's body, lit and shaded, for one width and hue
    private fun slab(w: Float, hue: Int): Sprite {
        val wq = max(2, w.roundToInt()).toFloat()
        val h = SLAB_H - 4f
        return makeSprite(wq + SPAD * 2f, h + SPAD * 2f) { c ->
            val left = SPAD
            val topY = SPAD
            val r = min(wq / 2f, h * 0.45f)
            val base = hueShift(lookColor, hue.toFloat())
            val dark = hueShift(lookDark, hue.toFloat())
            // underside rim
            c.drawRoundRect(RectF(left, topY + h * 0.2f, left + wq, topY + h), r, r, fill(dark))
            // top face, lit from above
            val face = RectF(left, topY, left + wq, topY + h * 0.84f)
            c.drawRoundRect(face, r, r, fill().shade(lin(0f, topY, 0f, topY + h * 0.84f, 0f to mix(base, WHITE, 0.35f), 0.45f to base, 1f to mix(base, 0xFF000000.toInt(), 0.12f))))
            c.save()
            val clip = Path()
            clip.addRoundRect(face, r, r, Path.Direction.CW)
            c.clipPath(clip)
            // side shading for roundness
            c.drawRect(left, topY, left + wq, topY + h, fill().shade(lin(left, 0f, left + wq, 0f, 0f to rgba(255, 255, 255, 0.18f), 0.12f to rgba(255, 255, 255, 0f), 0.88f to rgba(0, 0, 0, 0f), 1f to rgba(0, 0, 0, 0.18f))))
            c.restore()
            // a glossy specular streak and a thin rim of light on top
            val sx = left + min(wq * 0.22f, 40f)
            val sy = topY + h * 0.18f
            val rx = min(wq * 0.13f, 24f)
            val ry = h * 0.07f
            c.save()
            c.rotate(-0.12f * DEG, sx, sy)
            c.drawOval(RectF(sx - rx, sy - ry, sx + rx, sy + ry), fill(rgba(255, 255, 255, 0.55f)))
            c.restore()
            if (wq - 2f * r > 0f) c.drawLine(left + r, topY + 1f, left + wq - r, topY + 1f, stroke(rgba(255, 255, 255, 0.45f), 1.5f))
        }
    }

    /* ---------- art ---------- */

    private var slabSpr: Sprite? = null
    private var slabSprW = -1f
    private var slabSprHue = -1

    private val FLASH = col("#fff3c0")
    private val STAR = col("#dfe8ff")
    private val SUN_RAYS = col("#fff0c8")
    private val SUN_LATE = col("#ffb48a")
    private val SUN_DAY = col("#fff4d8")
    private val SUN_CORE = col("#fff2d0")
    private val VIGNETTE = col("#1a1030")
    private val ink = stroke(INK, 2f)
    private val eyeWhite = fill(WHITE)
    private val pupil = fill(INK)
    private val mouthFill = fill(col("#5a1f1a"))
    private val mouthLine = stroke(INK, 2f)
    private val mouthPath = Path()
    private val oval = RectF()
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rgba(255, 255, 255, 0.35f)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(6f, 8f), 0f)
    }
    private val art = Art()

    private class Art {
        val day by lazy { skySprite(*DAY) }
        val dusk by lazy { skySprite(*DUSK) }

        // layer 0: far hazy towers; 1: near city; 2: the near city's lit windows
        val skylines: Array<Sprite> by lazy { Array(3) { skyline(it) } }

        private fun skyline(layer: Int): Sprite {
            val h = if (layer > 0) 260f else 300f
            return makeSprite(W, h) { c ->
                val near = layer > 0
                val seed = if (near) 50f else 10f
                val n = if (near) 11 else 15
                val p = fill()
                for (i in 0 until n) {
                    val bw = (if (near) 38f else 26f) + hash(i + seed) * (if (near) 30f else 22f)
                    val x = (i / n.toFloat()) * W - 10f + hash(i + seed + 1f) * 20f
                    val bh = (if (near) 70f else 100f) + hash(i + seed + 2f) * (if (near) 110f else 150f)
                    val y = h - bh
                    if (layer == 2) {
                        // only the windows, glowing warm
                        var wy = y + 10f
                        while (wy < h - 10f) {
                            var wx = x + 6f
                            while (wx < x + bw - 8f) {
                                if (hash(wx * 3.1f + wy) > 0.55f) {
                                    p.shader = null
                                    p.color = if (hash(wx + wy * 7f) > 0.3f) rgba(255, 214, 140, 0.95f) else rgba(170, 220, 255, 0.9f)
                                    c.drawRect(wx, wy, wx + 5f, wy + 7f, p)
                                }
                                wx += 10f
                            }
                            wy += 14f
                        }
                        continue
                    }
                    val g = if (near) {
                        lin(x, 0f, x + bw, 0f, 0f to col("#5a6a9a"), 0.3f to col("#46507e"), 1f to col("#2c3158"))
                    } else {
                        lin(x, 0f, x + bw, 0f, 0f to col("#a9b9dc"), 1f to col("#8796c4"))
                    }
                    p.shader = g
                    p.color = WHITE
                    c.drawRect(x, y, x + bw, h, p)
                    if (hash(i + seed + 3f) > 0.6f) {
                        // an antenna with a red light
                        c.drawRect(x + bw / 2f - 1f, y - 18f, x + bw / 2f + 1f, y, p)
                        c.drawRect(x + bw / 2f - 2f, y - 20f, x + bw / 2f + 2f, y - 17f, fill(col("#ff5a5a")))
                    }
                    if (near) {
                        val win = fill(rgba(20, 24, 50, 0.35f))
                        var wy = y + 10f
                        while (wy < h - 10f) {
                            var wx = x + 6f
                            while (wx < x + bw - 8f) {
                                c.drawRect(wx, wy, wx + 5f, wy + 7f, win)
                                wx += 10f
                            }
                            wy += 14f
                        }
                        c.drawRect(x, y, x + 2f, h, fill(rgba(255, 220, 180, 0.35f)))
                    }
                }
                if (layer < 2) {
                    // haze settles at the bottom
                    val hz = fill().shade(lin(0f, h * 0.3f, 0f, h, 0f to rgba(255, 226, 192, 0f), 1f to rgba(255, 226, 192, if (near) 0.35f else 0.65f)))
                    hz.xfermode = ATOP
                    c.drawRect(0f, 0f, W, h, hz)
                }
            }
        }

        val ground by lazy {
            makeSprite(W + 20f, 140f) { c ->
                c.drawRect(0f, 0f, W + 20f, 140f, fill().shade(lin(0f, 0f, 0f, 140f, 0f to col("#c9a06a"), 1f to col("#6e4e2e"))))
                val peb = fill()
                val r = RectF()
                for (i in 0 until 50) {
                    val fi = i.toFloat()
                    peb.color = if (hash(fi) > 0.5f) rgba(255, 240, 210, 0.25f) else rgba(60, 35, 15, 0.25f)
                    val ex = hash(fi + 1f) * (W + 20f)
                    val ey = 22f + hash(fi + 2f) * 110f
                    val rx = 2f + hash(fi + 3f) * 3f
                    val ry = 1.5f + hash(fi + 4f) * 2f
                    r.set(ex - rx, ey - ry, ex + rx, ey + ry)
                    c.drawOval(r, peb)
                }
                c.drawRect(0f, 0f, W + 20f, 14f, fill().shade(lin(0f, 0f, 0f, 16f, 0f to col("#b3e87a"), 1f to col("#5aa646"))))
                c.drawRect(0f, 0f, W + 20f, 2f, fill(rgba(255, 255, 230, 0.6f)))
                c.drawRect(0f, 14f, W + 20f, 30f, fill().shade(lin(0f, 14f, 0f, 30f, 0f to rgba(40, 20, 5, 0.3f), 1f to rgba(40, 20, 5, 0f))))
            }
        }

        val puff by lazy {
            makeSprite(110f, 70f) { c ->
                val p = fill().shade(lin(0f, 10f, 0f, 64f, 0f to WHITE, 1f to col("#c9d8f0")))
                p.setShadowLayer(10f, 0f, 0f, rgba(255, 255, 255, 0.7f))
                val path = Path()
                path.addCircle(22f, 44f, 20f, Path.Direction.CW)
                path.addCircle(48f, 34f, 24f, Path.Direction.CW)
                path.addCircle(74f, 44f, 18f, Path.Direction.CW)
                c.drawPath(path, p)
            }
        }

        // a pale beam from the swinging clone down to the tower top
        val beam by lazy {
            makeSprite(64f, 4f, 1f) { c ->
                c.drawRect(0f, 0f, 64f, 4f, fill().shade(lin(0f, 0f, 64f, 0f, 0f to rgba(255, 255, 255, 0f), 0.5f to rgba(255, 255, 255, 0.22f), 1f to rgba(255, 255, 255, 0f))))
            }
        }
    }
}
