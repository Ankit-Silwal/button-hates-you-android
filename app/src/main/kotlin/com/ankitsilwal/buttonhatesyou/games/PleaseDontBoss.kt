package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.avatar.drawButton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// The pieces Please Don't is built from: everyone on screen (the button, its
// friends, its clones, the boss) is a PdChar, and the boss fight's five
// minigames are PdPhases. Port of src/game/boss.js.

/* ---------- the cast ---------- */

// The friends and family, by name and colour (from src/game/core.js).
internal val PD_FRIEND_NAMES = arrayOf("Kevin", "Bubbles", "Gary", "Doris", "Pip")
internal val PD_FRIEND_LOOKS = arrayOf(
    Look(color = 0xFF9BBF9A, dark = 0xFF6F9A6E),
    Look(color = 0xFFF3A6A0, dark = 0xFFCF7D77),
    Look(color = 0xFF8FB8D8, dark = 0xFF6591B3),
    Look(color = 0xFFF0C36D, dark = 0xFFC99A45),
    Look(color = 0xFFC4A6DC, dark = 0xFF9C7FB6),
)

// One round face on screen. It glides to (tx, ty) like the web's CSS
// transitions, squashes when pressed, and pops when it is gone for good.
internal class PdChar(var x: Float, var y: Float, var look: Look, var size: Float = 110f) {
    var tx = x
    var ty = y
    var ease = 9f // how fast it glides to its target
    var mood = Mood.Calm
    var legs = false
    var blink = true
    var tears = false
    var alpha = 1f
    var name = ""
    var sign: String? = null
    var dx = 0f // where it stands next to the button it guards
    var dy = 0f
    var gone = false
    var popT = 0f // counts down while it pops
    var pressT = 0f
    var shakeT = 0f
    var shakeP = 0f
    var tremble = false
    var bandaged = false
    var scale = 1f
    var bright = 0f // 0..1 glowing white (the boss blowing up)
    val seed = Random.nextFloat() * 10f
    private var face = Face()
    private var lastX = x

    fun place(px: Float, py: Float) {
        tx = px
        ty = py
    }

    fun jump(px: Float, py: Float) {
        x = px
        y = py
        tx = px
        ty = py
    }

    fun press() {
        pressT = 0.11f
    }

    fun shake(power: Float, sec: Float = 0.4f) {
        shakeP = power
        shakeT = sec
    }

    fun pop() {
        if (popT > 0f) return
        gone = true
        popT = 0.26f
    }

    val popped get() = gone && popT <= 0f

    fun moving() = hypot(tx - x, ty - y) > 4f

    fun update(dt: Float) {
        lastX = x
        val k = min(1f, dt * ease)
        x += (tx - x) * k
        y += (ty - y) * k
        if (x.isNaN() || y.isNaN()) jump(W / 2f, H / 2f)
        if (popT > 0f) popT -= dt
        pressT -= dt
        shakeT -= dt
    }

    fun hit(px: Float, py: Float, pad: Float = 1.1f): Boolean = !gone && alpha > 0.3f && hypot(px - x, py - y) < size * 0.5f * scale * pad

    fun draw(s: DrawScope, c: Canvas, t: Float) {
        if (gone && popT <= 0f) return
        var a = alpha
        var sc = scale
        if (gone) {
            val k = 1f - clamp(popT / 0.26f, 0f, 1f)
            sc *= 1f + k * 0.5f
            a *= 1f - k
        }
        if (a <= 0.01f) return
        var jx = 0f
        var jy = 0f
        if (shakeT > 0f) {
            jx = shakeBy(shakeP * clamp(shakeT / 0.4f, 0f, 1f))
            jy = shakeBy(shakeP * 0.5f * clamp(shakeT / 0.4f, 0f, 1f))
        }
        if (tremble) {
            jx += sin(t * 60f) * 2f
            jy += cos(t * 47f) * 1.2f
        }
        val run = moving()
        val sq = when {
            pressT > 0f -> 0.25f
            run && legs -> abs(sin(t * 22f + seed)) * 0.08f - 0.04f
            else -> 0f
        }
        val cx = x + jx
        val cy = y + jy - (if (run && legs) abs(sin(t * 22f + seed)) * size * 0.06f else 0f)
        if (a < 0.995f) {
            val m = size * sc * 1.6f
            c.saveLayerAlpha(cx - m, cy - m, cx + m, cy + m, (a * 255f).toInt())
        } else {
            c.save()
        }
        if (sc != 1f) c.scale(sc, sc, cx, cy)
        if (face.mood != mood) face = Face(mood = mood)
        val facing = clamp((tx - x) / 60f, -1f, 1f)
        val sz = size
        with(s) {
            drawButton(Offset(cx, cy), sz, look, face, t = t + seed, squash = sq, legs = legs, blink = blink, tears = tears, facing = facing)
        }
        if (bandaged) {
            // a sticking plaster across its forehead
            c.save()
            c.rotate(-18f, cx, cy)
            c.drawRoundRect(cx - size * 0.3f, cy - size * 0.36f, cx + size * 0.12f, cy - size * 0.26f, 4f, 4f, PdPaint.plaster)
            c.drawRect(cx - size * 0.12f, cy - size * 0.36f, cx - size * 0.04f, cy - size * 0.26f, PdPaint.plasterPad)
            c.restore()
        }
        if (bright > 0f) c.glow(cx, cy, size * 0.9f, WHITE, bright)
        c.restore()
        sign?.let { str ->
            // family members hold up a little sign
            val w = Txt.width(str, 12f) + 14f
            val top = cy - size * 0.5f - 34f
            c.drawLine(cx, top + 22f, cx, cy - size * 0.35f, PdPaint.stick)
            c.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + 22f, 4f, 4f, PdPaint.card)
            c.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + 22f, 4f, 4f, PdPaint.cardLine)
            Txt.draw(c, str, cx, top + 11f, 12f, INK)
        }
    }
}

internal object PdPaint {
    val plaster = fill(col("#f3d2a8"))
    val plasterPad = fill(col("#e2b98a"))
    val stick = stroke(col("#a77c57"), 3f)
    val card = fill(col("#fffaf2"))
    val cardLine = stroke(col("#d9bd98"), 1.5f)
    val tagBg = fill(rgba(30, 14, 40, 0.6f))
    val solid = fill(WHITE)
    val line = stroke(WHITE, 2f)
}

// A rounded label in the arena (the web's .tag).
internal fun Canvas.pdTag(str: String, x: Float, y: Float, size: Float = 18f, color: Int = WHITE) {
    val w = Txt.width(str, size) + 28f
    drawRoundRect(x - w / 2f, y - size * 0.95f, x + w / 2f, y + size * 0.95f, size, size, PdPaint.tagBg)
    Txt.draw(this, str, x, y, size, color)
}

// What a minigame can use from the game.
internal interface PdHost {
    val look: Look
    val bossLook: Look
    val sfx: Sfx
    val haptics: Haptics
    val fx: Fx
    val t: Float
    fun say(str: String)
    fun floatText(x: Float, y: Float, str: String, hot: Boolean = false)
    fun shake(power: Float)
    fun flash(color: Int)
    fun pressFx(x: Float, y: Float, color: Int)
}

// One round of a boss minigame. It calls `done` once, when it is won.
internal interface PdPhase {
    fun update(dt: Float)
    fun draw(s: DrawScope, c: Canvas)
    fun down(x: Float, y: Float) {}
    fun move(x: Float, y: Float) {}
    fun up(x: Float, y: Float) {}
    fun stop() {}
}

/* =========================================================
   Phase 1 — Balloon Party
   Angry red balloons are its shield. Pop them, then hit it while the shield
   is down. Cute pastel balloons are its cousins: popping one adds shield.
   ========================================================= */
internal class BalloonPhase(private val h: PdHost, round: Int, private val done: () -> Unit) : PdPhase {
    private class Balloon(val x: Float, var y: Float, var ph: Float, val sp: Float, val evil: Boolean, val color: Int) {
        var dead = false
        var popT = 0f
        val sx get() = x + sin(ph) * 18f
    }

    private val need = intArrayOf(8, 10, 12)[round]
    private val speed = floatArrayOf(70f, 95f, 125f)[round]
    private val every = floatArrayOf(0.62f, 0.5f, 0.4f)[round]
    private var left = need
    private var alive = true
    private var vulnerable = false
    private var vulnT = 0f
    private var spawnT = 0f
    private val list = ArrayList<Balloon>()
    private val boss = PdChar(W / 2f, 250f, h.bossLook, 130f).also { it.mood = Mood.Smug }
    private val evilFill = fill(col("#e0453a"))
    private val shine = fill(rgba(255, 255, 255, 0.5f))
    private val string = stroke(rgba(80, 60, 70, 0.6f), 1.2f)
    private val shield = stroke(col("#9fe3ff"), 4f)
    private val oval = RectF()

    init {
        h.say("Pop the ANGRY red balloons. Leave the cute ones alone!")
    }

    override fun update(dt: Float) {
        boss.update(dt)
        if (!alive) return
        spawnT -= dt
        if (spawnT <= 0f && !vulnerable) {
            val evil = Random.nextFloat() > 0.28f
            val c = if (evil) col("#e0453a") else PD_FRIEND_LOOKS[Random.nextInt(5)].color.toInt()
            list.add(Balloon(rand(40f, W - 40f), H + 40f, rand(0f, 6f), speed * rand(0.85f, 1.25f), evil, c))
            spawnT = every
        }
        if (vulnerable) {
            vulnT -= dt
            if (vulnT <= 0f) {
                // too slow: the shield comes back, half strength
                vulnerable = false
                left = ceil(need / 2f).toInt()
                boss.mood = Mood.Smug
                h.say("Too slow! Shield's back. Hehe.")
            }
        }
        for (b in list) {
            b.y -= b.sp * dt
            b.ph += dt * 2f
            if (b.dead) b.popT -= dt
        }
        list.removeAll { (it.dead && it.popT <= 0f) || it.y < -90f }
    }

    override fun draw(s: DrawScope, c: Canvas) {
        val t = h.t
        if (!vulnerable) {
            val r = 92f + sin(t * 3f) * 3f
            c.glow(boss.x, boss.y, r * 1.3f, col("#7cc9ff"), 0.35f)
            shield.alpha = 200
            c.drawCircle(boss.x, boss.y, r, shield)
        } else {
            c.glow(boss.x, boss.y, 120f, col("#ff6b5a"), 0.3f + 0.2f * sin(t * 12f))
        }
        boss.draw(s, c, t)
        for (b in list) balloon(c, b)
        c.pdTag(if (vulnerable) "SHIELD DOWN — HIT IT!" else "🎈 $left to pop", W / 2f, 360f, 18f, if (vulnerable) col("#ffe27a") else WHITE)
    }

    private fun balloon(c: Canvas, b: Balloon) {
        val x = b.sx
        val y = b.y
        if (b.dead) {
            val k = 1f - clamp(b.popT / 0.2f, 0f, 1f)
            c.glow(x, y, 40f + k * 30f, b.color, 1f - k)
            return
        }
        c.drawLine(x, y + 30f, x + sin(b.ph * 1.3f) * 6f, y + 80f, string)
        evilFill.color = b.color
        oval.set(x - 23f, y - 28f, x + 23f, y + 28f)
        c.drawOval(oval, evilFill)
        c.drawCircle(x, y + 30f, 4f, evilFill)
        oval.set(x - 14f, y - 20f, x - 4f, y - 6f)
        c.drawOval(oval, shine)
        Txt.draw(c, if (b.evil) "ò_ó" else "♥", x, y + 2f, if (b.evil) 15f else 18f, if (b.evil) WHITE else rgba(255, 255, 255, 0.95f), black = true)
    }

    override fun down(x: Float, y: Float) {
        if (!alive) return
        for (i in list.indices.reversed()) {
            val b = list[i]
            if (b.dead) continue
            val dx = (x - b.sx) / 34f
            val dy = (y - b.y) / 40f
            if (dx * dx + dy * dy > 1f) continue
            b.dead = true
            b.popT = 0.2f
            h.sfx.play("pop")
            h.haptics.tap()
            h.fx.sparks(b.sx, b.y, b.color, 8, 160f)
            if (vulnerable) return
            if (b.evil) {
                left--
                if (left <= 0) {
                    vulnerable = true
                    vulnT = 3.2f
                    boss.mood = Mood.Scared
                    h.sfx.play("crack")
                    h.fx.sparks(boss.x, boss.y, col("#9fe3ff"), 24, 300f)
                    h.say("My shield! NO— don't click me!")
                }
            } else {
                left = min(need, left + 2)
                h.sfx.play("bad")
                h.say(pick(arrayOf("You popped my COUSIN! Shield up!", "That was Grandma's balloon!", "Cute ones are FAMILY. +2 shield.")))
            }
            return
        }
        if (boss.hit(x, y, 1.2f)) {
            if (vulnerable) {
                alive = false
                boss.press()
                done()
            } else {
                h.sfx.play("nope")
                h.floatText(x, y - 30f, "shielded!", true)
            }
        }
    }
}

/* =========================================================
   Phase 2 — The Climb
   A tiny platformer: hold to run toward your finger, tap to jump. Bonk the
   boss at the top. Round 2 adds moving platforms and falling orbs, round 3
   crumbling ones.
   ========================================================= */
internal class ClimbPhase(private val h: PdHost, private val round: Int, private val done: () -> Unit) : PdPhase {
    private class Plat(var x: Float, val y: Float, val w: Float, val ground: Boolean) {
        var base = x
        var move = 0f
        var ph = 0f
        var crumble = false
        var state = 0 // 0 solid, 1 shaking, 2 gone
        var timer = 0f
        var dx = 0f
    }

    private class Orb(var x: Float, var y: Float, var vx: Float, var vy: Float)

    private companion object {
        const val GRAV = 1900f
        const val JUMP = 760f
        const val SPEED = 250f
        const val S = 30f
        val GROUND_Y = H - 70f
        const val TOP = 230f
    }

    private val plats = ArrayList<Plat>()
    private val orbs = ArrayList<Orb>()
    private var alive = true
    private var t = 0f
    private var orbT = 1.5f
    private var px = W / 2f
    private var py = GROUND_Y - S / 2f
    private var vy = 0f
    private var onGround = true
    private var plat: Plat? = null
    private var face = 1f
    private var holding = false
    private var aim = W / 2f
    private var buffer = 0f
    private val boss: PdChar
    private val platPaint = fill()
    private val groundPaint = fill().shade(lin(0f, GROUND_Y, 0f, H, 0f to col("#3a2142"), 1f to col("#170c1c")))
    private val keyTop = fill().shade(lin(0f, -S / 2f, 0f, S / 2f, 0f to col("#c6e2c4"), 1f to col("#8db58c")))
    private val keyBase = fill(col("#5f8a5e"))
    private val eyeW = fill(WHITE)
    private val eyeB = fill(INK)
    private val orbPaint = fill(col("#ffb3a7"))

    init {
        h.say("Climb up and bonk the boss! Hold to run, tap to jump.")
        plats.add(Plat(0f, GROUND_Y, W, true))
        val gap = clamp((GROUND_Y - TOP) / 5f, 72f, 108f)
        val pw = 120f
        var cx = W / 2f
        var y = GROUND_Y - gap
        while (y >= TOP) {
            cx = clamp(cx + (if (Random.nextBoolean()) -1f else 1f) * rand(80f, 160f), pw / 2f + 10f, W - pw / 2f - 10f)
            val p = Plat(cx - pw / 2f, y, pw, false)
            p.move = if (round >= 1 && plats.size % 2 == 0) rand(35f, 70f) else 0f
            p.ph = rand(0f, 6f)
            p.crumble = round == 2
            plats.add(p)
            y -= gap
        }
        val top = plats.last()
        top.move = 0f
        top.crumble = false
        boss = PdChar(top.x + top.w / 2f, top.y - 34f, h.bossLook, 64f).also { it.mood = Mood.Angry }
    }

    private fun respawn() {
        h.sfx.play("bad")
        h.flash(col("#ff4d3d"))
        px = W / 2f
        py = GROUND_Y - S / 2f
        vy = 0f
        orbs.clear()
        h.say(pick(arrayOf("Back to the bottom! Hahaha.", "Bonk! Try again.", "Gravity is on MY side.")))
    }

    private fun jump() {
        if (onGround) {
            vy = -JUMP
            onGround = false
            buffer = 0f
            h.sfx.play("jump")
        } else {
            buffer = 0.14f
        }
    }

    override fun down(x: Float, y: Float) {
        holding = true
        aim = x
        jump()
    }

    override fun move(x: Float, y: Float) {
        aim = x
    }

    override fun up(x: Float, y: Float) {
        holding = false
    }

    override fun update(dt: Float) {
        t += dt
        boss.update(dt)
        boss.ty = plats.last().y - 34f + sin(t * 3f) * 3f
        if (!alive) return
        buffer -= dt
        for (p in plats) {
            if (p.ground) continue
            if (p.move != 0f) {
                val nx = p.base + sin(t * 1.2f + p.ph) * p.move
                p.dx = nx - p.x
                p.x = nx
            }
            if (p.state == 1) {
                p.timer -= dt
                if (p.timer <= 0f) {
                    p.state = 2
                    p.timer = 2.4f
                }
            } else if (p.state == 2) {
                p.timer -= dt
                if (p.timer <= 0f) p.state = 0
            }
        }
        var dir = 0f
        if (holding && abs(aim - px) > 10f) dir = if (aim > px) 1f else -1f
        if (dir != 0f) face = dir
        if (onGround) plat?.let { px += it.dx }
        val prevBottom = py + S / 2f
        vy += GRAV * dt
        px = clamp(px + dir * SPEED * dt, S / 2f, W - S / 2f)
        py += vy * dt
        onGround = false
        plat = null
        // one-way platforms: you only land on them while falling
        if (vy >= 0f) {
            for (p in plats) {
                if (p.state == 2) continue
                val bottom = py + S / 2f
                if (prevBottom <= p.y + 1f && bottom >= p.y && px + S / 2f > p.x && px - S / 2f < p.x + p.w) {
                    py = p.y - S / 2f
                    vy = 0f
                    onGround = true
                    plat = p
                    if (p.crumble && p.state == 0) {
                        p.state = 1
                        p.timer = 0.55f
                    }
                    break
                }
            }
        }
        if (onGround && buffer > 0f) jump()
        if (py > H + 60f || py.isNaN()) respawn()

        if (hypot(px - boss.x, py - boss.y) < 32f + S / 2f) {
            alive = false
            boss.mood = Mood.Scared
            boss.press()
            h.sfx.play("pop")
            done()
            return
        }

        if (round >= 1) {
            orbT -= dt
            if (orbT <= 0f) {
                orbT = if (round == 1) 1.7f else 1.15f
                orbs.add(Orb(boss.x, boss.y + 20f, rand(-110f, 110f), 40f))
            }
        }
        for (o in orbs) {
            o.vy += 320f * dt
            o.x += o.vx * dt
            o.y += o.vy * dt
            if (hypot(o.x - px, o.y - py) < 10f + S / 2f) {
                respawn()
                break
            }
        }
        orbs.removeAll { it.y > H + 20f }
    }

    override fun draw(s: DrawScope, c: Canvas) {
        for (p in plats) {
            if (p.state == 2) continue
            if (p.ground) {
                c.drawRect(0f, p.y, W, H, groundPaint)
                PdPaint.solid.color = rgba(255, 110, 140, 0.55f)
                c.drawRect(0f, p.y, W, p.y + 2f, PdPaint.solid)
                continue
            }
            val x = p.x + (if (p.state == 1) rand(-2f, 2f) else 0f)
            c.softLight(x + p.w / 2f, p.y + 7f, p.w * 0.62f, if (p.crumble) col("#d9a35a") else col("#f0c36d"), 0.25f)
            platPaint.shader = null
            platPaint.color = if (p.crumble) col("#d9a35a") else col("#f0c36d")
            c.drawRoundRect(x, p.y, x + p.w, p.y + 14f, 7f, 7f, platPaint)
            PdPaint.solid.color = rgba(255, 255, 255, 0.55f)
            c.drawRoundRect(x + 6f, p.y + 2f, x + p.w - 6f, p.y + 4.5f, 2f, 2f, PdPaint.solid)
            PdPaint.solid.color = rgba(0, 0, 0, 0.18f)
            c.drawRoundRect(x + 2f, p.y + 10f, x + p.w - 2f, p.y + 14f, 4f, 4f, PdPaint.solid)
        }
        c.glow(boss.x, boss.y, 90f, col("#ff4d3d"), 0.32f + sin(t * 4f) * 0.08f)
        boss.draw(s, c, t)
        for (o in orbs) {
            c.glow(o.x, o.y, 24f, col("#ff8c78"), 0.5f)
            c.drawCircle(o.x, o.y, 10f, orbPaint)
        }
        // the player: a little green keycap
        c.shadow(px, py + S / 2f + 2f, S * 0.5f, 0.3f, 3f)
        c.drawRoundRect(px - S / 2f, py - S / 2f + 3f, px + S / 2f, py + S / 2f + 3f, 8f, 8f, keyBase)
        c.save()
        c.translate(px, py)
        c.drawRoundRect(-S / 2f, -S / 2f, S / 2f, S / 2f - 2f, 8f, 8f, keyTop)
        c.restore()
        for (sd in SIDES) {
            c.drawCircle(px + sd * 6f, py - 2f, 3.4f, eyeW)
            c.drawCircle(px + sd * 6f + face * 1.2f, py - 1.6f, 1.8f, eyeB)
        }
        if (t < 3.5f) c.pdTag("Hold to run · tap to jump", W / 2f, H - 30f, 15f)
    }
}

private val SIDES = floatArrayOf(-1f, 1f)

/* =========================================================
   Phase 3 — Whack-a-Button
   It pops out of holes. Whack it 5 times. Whacking a friend costs a point.
   ========================================================= */
internal class WhackPhase(private val h: PdHost, round: Int, private val done: () -> Unit) : PdPhase {
    private class Mole(val hole: Int, val friend: Boolean, val ch: PdChar, var left: Float) {
        var whacked = false
        var rise = 0f // 0 down .. 1 up
    }

    private companion object {
        val HX = floatArrayOf(110f, 240f, 370f)
        val HY = floatArrayOf(380f, 520f, 660f)
        const val NEED = 5
    }

    private val upSec = floatArrayOf(1.05f, 0.85f, 0.7f)[round]
    private val maxUp = if (round == 2) 2 else 1
    private var score = 0
    private var alive = true
    private var next = 0.6f
    private val moles = ArrayList<Mole>()
    private val holeDark = fill(col("#1a0d12"))
    private val holeRim = fill(col("#6b4a3a"))
    private val oval = RectF()

    init {
        h.say("Whack me $NEED times when I pop up. Don't whack my friends!")
    }

    override fun update(dt: Float) {
        for (m in moles) {
            m.ch.update(dt)
            m.left -= dt
            m.rise = clamp(m.rise + (if (m.left > 0f) dt else -dt) / 0.18f, 0f, 1f)
        }
        moles.removeAll { it.left <= 0f && it.rise <= 0f }
        if (!alive) return
        next -= dt
        if (next <= 0f) {
            next = upSec * rand(0.45f, 0.75f)
            if (moles.size < maxUp) {
                var hole: Int
                var guard = 0
                do {
                    hole = Random.nextInt(9)
                } while (moles.any { it.hole == hole } && guard++ < 20)
                val friend = Random.nextFloat() < 0.22f
                val ch = PdChar(HX[hole % 3], HY[hole / 3] - 30f, if (friend) PD_FRIEND_LOOKS[Random.nextInt(5)] else h.bossLook, 86f)
                ch.mood = if (friend) Mood.Scared else if (Random.nextBoolean()) Mood.Smug else Mood.Angry
                moles.add(Mole(hole, friend, ch, upSec))
            }
        }
    }

    override fun draw(s: DrawScope, c: Canvas) {
        for (i in 0 until 9) {
            val x = HX[i % 3]
            val y = HY[i / 3]
            oval.set(x - 56f, y - 18f, x + 56f, y + 18f)
            c.drawOval(oval, holeRim)
            oval.set(x - 48f, y - 13f, x + 48f, y + 13f)
            c.drawOval(oval, holeDark)
        }
        for (m in moles) {
            val x = HX[m.hole % 3]
            val y = HY[m.hole / 3]
            c.save()
            c.clipRect(x - 70f, y - 140f, x + 70f, y)
            m.ch.y = y + 50f - m.rise * 85f
            m.ch.ty = m.ch.y
            m.ch.draw(s, c, h.t)
            c.restore()
            // the front lip of the hole over its body
            oval.set(x - 56f, y - 4f, x + 56f, y + 18f)
            c.drawArc(oval, 0f, 180f, true, holeRim)
        }
        c.pdTag("🔨 $score / $NEED", W / 2f, H - 40f, 20f)
    }

    override fun down(x: Float, y: Float) {
        if (!alive) return
        for (m in moles) {
            if (m.whacked || m.rise < 0.4f) continue
            if (!m.ch.hit(x, y, 1.25f)) continue
            m.whacked = true
            m.left = min(m.left, 0.12f)
            m.ch.press()
            if (m.friend) {
                score = max(0, score - 1)
                h.sfx.play("bad")
                h.say("Hey! That was my friend! −1")
            } else {
                score++
                h.sfx.play("pop")
                h.haptics.tap()
                m.ch.mood = Mood.Scared
                h.shake(4f)
                h.pressFx(m.ch.x, m.ch.y, col("#ffb36b"))
            }
            if (score >= NEED) {
                alive = false
                done()
            }
            return
        }
    }
}

/* =========================================================
   Phase 4 — Which One Is Me?
   A shell game with clones of itself.
   ========================================================= */
internal class ShellsPhase(private val h: PdHost, round: Int, private val done: () -> Unit) : PdPhase {
    private val n = intArrayOf(3, 4, 5)[round]
    private val swaps = intArrayOf(6, 9, 13)[round]
    private val sec = floatArrayOf(0.52f, 0.4f, 0.3f)[round]
    private var alive = true
    private val spacing = min(150f, (W - 40f) / n)
    private val size = min(96f, spacing - 14f)
    private val slotX = FloatArray(n) { W / 2f + (it - (n - 1) / 2f) * spacing }
    private val y = H * 0.55f
    private val cups = ArrayList<PdChar>()
    private val slot = IntArray(n)
    private var real = 0
    private var stage = 0 // 0 reveal, 1 masked pause, 2 swapping, 3 pick, 4 result
    private var timer = 0f
    private var swapsLeft = 0
    private var reveal = false

    init {
        play()
    }

    private fun play() {
        cups.clear()
        for (i in 0 until n) {
            val c = PdChar(slotX[i], y, h.bossLook, size)
            c.mood = Mood.Calm
            c.ease = 1f / sec * 3.2f
            cups.add(c)
            slot[i] = i
        }
        real = Random.nextInt(n)
        cups[real].mood = Mood.Smug
        reveal = true
        stage = 0
        timer = 1.6f
        h.say("Watch closely. THAT one is me.")
    }

    override fun update(dt: Float) {
        for (c in cups) c.update(dt)
        if (!alive && stage != 4) return
        timer -= dt
        if (timer > 0f) return
        when (stage) {
            0 -> {
                // masked: they all look the same, and none of them blink
                reveal = false
                for (c in cups) {
                    c.mood = Mood.Calm
                    c.blink = false
                }
                stage = 1
                timer = 0.5f
            }
            1 -> {
                stage = 2
                swapsLeft = swaps
                timer = 0f
            }
            2 -> {
                if (swapsLeft <= 0) {
                    stage = 3
                    h.say("So… which one is me?")
                    return
                }
                swapsLeft--
                val a = Random.nextInt(n)
                var b = Random.nextInt(n - 1)
                if (b >= a) b++
                val ca = (0 until n).first { slot[it] == a }
                val cb = (0 until n).first { slot[it] == b }
                slot[ca] = b
                slot[cb] = a
                cups[ca].place(slotX[b], y + (if (swapsLeft % 2 == 0) -40f else 40f))
                cups[cb].place(slotX[a], y + (if (swapsLeft % 2 == 0) 40f else -40f))
                h.sfx.play("tick")
                timer = sec + 0.04f
            }
            4 -> if (alive) play()
        }
        // the arcs settle back onto the row
        if (stage == 2 || stage == 3) for (i in 0 until n) if (abs(cups[i].x - slotX[slot[i]]) < spacing * 0.5f) cups[i].ty = y
    }

    override fun draw(s: DrawScope, c: Canvas) {
        if (reveal) c.glow(cups[real].x, cups[real].y, size * 1.1f, col("#ffe27a"), 0.5f)
        for (ch in cups) {
            c.shadow(ch.x, y + size * 0.6f, size * 0.45f, 0.3f)
            ch.draw(s, c, h.t)
        }
        if (stage == 3) c.pdTag("Tap the real one", W / 2f, H - 120f, 18f)
    }

    override fun down(x: Float, y: Float) {
        if (stage != 3 || !alive) return
        var picked = -1
        for (i in 0 until n) if (cups[i].hit(x, y, 1.2f)) picked = i
        if (picked < 0) return
        for (ch in cups) ch.blink = true
        if (picked == real) {
            cups[picked].mood = Mood.Scared
            cups[picked].press()
            h.sfx.play("pop")
            alive = false
            stage = 5
            done()
        } else {
            h.sfx.play("bad")
            cups[picked].mood = Mood.Smug
            cups[real].mood = Mood.Smug
            reveal = true
            h.floatText(cups[picked].x, cups[picked].y - 70f, "HA! WRONG!", true)
            h.say(pick(arrayOf("WRONG. Again!", "That one was my cousin Steve.", "Nope! Round two. I mean round again.")))
            stage = 4
            timer = 1.5f
        }
    }
}

/* =========================================================
   Phase 5 — The Last Three
   ========================================================= */

// Hit 13: restore power — repeat the light pattern.
internal class SimonPhase(private val h: PdHost, private val done: () -> Unit) : PdPhase {
    private companion object {
        val COLORS = intArrayOf(col("#ff6b5a"), col("#f0c36d"), col("#9bbf9a"), col("#8fb8d8"))
        val NOTES = arrayOf("beep262", "beep330", "beep392", "beep523")
        const val LEN = 5
        const val PAD = 150f
        val PX = floatArrayOf(W / 2f - PAD - 8f, W / 2f + 8f, W / 2f - PAD - 8f, W / 2f + 8f)
        val PY = floatArrayOf(250f, 250f, 250f + PAD + 16f, 250f + PAD + 16f)
    }

    private val seq = IntArray(LEN)
    private var idx = 0
    private var input = false
    private var alive = true
    private var showing = -1
    private var timer = 0.8f
    private val lit = FloatArray(4)
    private val pad = fill()

    init {
        h.say("RESTORE POWER: watch the lights, then repeat the pattern.")
        restart()
    }

    private fun restart() {
        for (i in 0 until LEN) seq[i] = Random.nextInt(4)
        idx = 0
        input = false
        showing = -1
        timer = 0.8f
    }

    private fun light(i: Int) {
        lit[i] = 0.38f
        h.sfx.play(NOTES[i])
    }

    override fun update(dt: Float) {
        for (i in 0 until 4) lit[i] -= dt
        if (!alive || input) return
        timer -= dt
        if (timer > 0f) return
        showing++
        if (showing >= LEN) {
            input = true
            return
        }
        light(seq[showing])
        timer = 0.62f
    }

    override fun draw(s: DrawScope, c: Canvas) {
        for (i in 0 until 4) {
            val on = lit[i] > 0f
            val x = PX[i]
            val y = PY[i]
            if (on) c.glow(x + PAD / 2f, y + PAD / 2f, PAD, COLORS[i], 0.9f)
            pad.color = if (on) lighten(COLORS[i], 0.35f) else darken(COLORS[i], 0.35f)
            c.drawRoundRect(x, y, x + PAD, y + PAD, 26f, 26f, pad)
            PdPaint.solid.color = rgba(255, 255, 255, if (on) 0.45f else 0.15f)
            c.drawRoundRect(x + 10f, y + 8f, x + PAD - 10f, y + 22f, 8f, 8f, PdPaint.solid)
        }
        c.pdTag(if (input) "Your turn · $idx / $LEN" else "Watch…", W / 2f, H - 90f, 18f)
    }

    override fun down(x: Float, y: Float) {
        if (!input || !alive) return
        for (i in 0 until 4) {
            if (x < PX[i] || x > PX[i] + PAD || y < PY[i] || y > PY[i] + PAD) continue
            light(i)
            if (seq[idx] == i) {
                idx++
                h.haptics.tap()
                if (idx == LEN) {
                    alive = false
                    done()
                }
            } else {
                input = false
                h.sfx.play("bad")
                h.shake(10f)
                h.say(pick(arrayOf("WRONG. Rebooting… hehehe.", "Bzzzt. Power denied.", "You call that electricity?")))
                restart()
                timer = 1.2f
            }
            return
        }
    }
}

// Hit 14: disarm the self-destruct — tap 1 to 9 in order while they drift.
internal class DefusePhase(private val h: PdHost, private val done: () -> Unit) : PdPhase {
    private companion object {
        const val LIMIT = 25f
        val NUMS = Array(9) { (it + 1).toString() }
    }

    private val tx = FloatArray(9) { rand(50f, W - 50f) }
    private val ty = FloatArray(9) { rand(220f, H - 70f) }
    private val vx = FloatArray(9) { rand(-70f, 70f) }
    private val vy = FloatArray(9) { rand(-70f, 70f) }
    private val ok = BooleanArray(9)
    private var nextN = 1
    private var timeLeft = LIMIT
    private var lastSec = ceil(LIMIT).toInt()
    private var alive = true
    private val tile = fill()
    private val tileLine = stroke(rgba(0, 0, 0, 0.25f), 2f)
    private var timerText = ""
    private var timerTenths = -1

    init {
        h.say("SELF-DESTRUCT ARMED! Tap 1 → 9 in order before time runs out!")
    }

    private fun reset() {
        nextN = 1
        for (i in 0 until 9) {
            ok[i] = false
            vx[i] = rand(-90f, 90f)
            vy[i] = rand(-90f, 90f)
        }
    }

    override fun update(dt: Float) {
        if (!alive) return
        timeLeft -= dt
        if (ceil(timeLeft).toInt() < lastSec) {
            lastSec = ceil(timeLeft).toInt()
            h.sfx.play("tick")
        }
        if (timeLeft <= 0f) {
            h.flash(WHITE)
            h.sfx.play("boom")
            h.shake(18f)
            h.say("BOOM! …just kidding. It was a fake bomb. Again!")
            timeLeft = LIMIT
            lastSec = ceil(LIMIT).toInt()
            reset()
        }
        for (i in 0 until 9) {
            tx[i] += vx[i] * dt
            ty[i] += vy[i] * dt
            if (tx[i] < 34f || tx[i] > W - 34f) vx[i] = -vx[i]
            if (ty[i] < 220f || ty[i] > H - 40f) vy[i] = -vy[i]
            tx[i] = clamp(tx[i], 34f, W - 34f)
            ty[i] = clamp(ty[i], 220f, H - 40f)
        }
    }

    override fun draw(s: DrawScope, c: Canvas) {
        val tenths = (max(0f, timeLeft) * 10f).toInt()
        if (tenths != timerTenths) {
            timerTenths = tenths
            timerText = if (alive) "${tenths / 10}.${tenths % 10}" else "DISARMED"
        }
        val alarm = alive && timeLeft < 8f
        if (alarm) c.glow(W / 2f, 180f, 120f, col("#ff3b2f"), 0.4f + 0.3f * sin(h.t * 14f))
        Txt.draw(c, if (alive) timerText else "DISARMED", W / 2f, 180f, 44f, if (alarm) col("#ff6b5a") else col("#9fffb0"), rgba(0, 0, 0, 0.5f), 6f, black = true)
        for (i in 0 until 9) {
            val x = tx[i]
            val y = ty[i]
            tile.color = if (ok[i]) col("#4fd06a") else col("#fffaf2")
            c.drawRoundRect(x - 28f, y - 28f, x + 28f, y + 28f, 12f, 12f, tile)
            c.drawRoundRect(x - 28f, y - 28f, x + 28f, y + 28f, 12f, 12f, tileLine)
            Txt.draw(c, NUMS[i], x, y + 1f, 26f, if (ok[i]) WHITE else INK, black = true)
        }
    }

    override fun down(x: Float, y: Float) {
        if (!alive) return
        // the nearest tile under the finger
        var best = -1
        var bestD = 40f
        for (i in 0 until 9) {
            val d = hypot(x - tx[i], y - ty[i])
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best < 0 || ok[best]) return
        if (best + 1 == nextN) {
            ok[best] = true
            h.sfx.play("coin")
            h.haptics.tap()
            nextN++
            if (nextN > 9) {
                alive = false
                timerTenths = -1
                done()
            }
        } else {
            h.sfx.play("bad")
            h.shake(10f)
            h.say("WRONG WIRE! Order reset!")
            reset()
        }
    }
}

// Hit 15: the final blow — smash it before it regenerates.
internal class MashPhase(private val h: PdHost, private val done: () -> Unit) : PdPhase {
    private companion object {
        const val NEED = 25
        const val LIMIT = 9f
        val HITS = arrayOf("POW", "BAM", "OOF", "ow", "!!")
    }

    private var count = 0
    private var timeLeft = LIMIT
    private var alive = true
    private var t = 0f
    private val boss = PdChar(W / 2f, H * 0.52f, h.bossLook, 170f).also {
        it.mood = Mood.Angry
        it.ease = 30f
    }

    init {
        h.say("FINAL BLOW! Smash it 25 times before it regenerates!")
    }

    override fun update(dt: Float) {
        t += dt
        boss.update(dt)
        if (!alive) return
        timeLeft -= dt
        if (timeLeft <= 0f) {
            count /= 2
            timeLeft = LIMIT
            boss.mood = Mood.Angry
            h.sfx.play("bad")
            h.say("HAHA! I REGENERATE!")
        }
        boss.size = 170f - count * 4f
        val r = 20f + count * 3f
        boss.place(W / 2f + sin(t * 2.3f) * min(r * 2f, 150f), H * 0.52f + cos(t * 3.1f) * r)
    }

    override fun draw(s: DrawScope, c: Canvas) {
        c.glow(boss.x, boss.y, boss.size * 1.3f, col("#ff7a4a"), 0.35f)
        boss.draw(s, c, h.t)
        val secs = (timeLeft * 10f).toInt()
        c.pdTag("💥 $count / $NEED · ${secs / 10}.${secs % 10}s", W / 2f, H - 70f, 20f)
    }

    override fun down(x: Float, y: Float) {
        if (!alive || !boss.hit(x, y, 1.25f)) return
        count++
        h.sfx.play("click")
        h.haptics.tap()
        boss.press()
        h.floatText(x, y - 20f, pick(HITS), true)
        h.pressFx(boss.x, boss.y, col("#ff965a"))
        h.shake(4f + count / 3f)
        if (count == 12) {
            boss.mood = Mood.Scared
            h.say("Wait wait wait—")
        }
        if (count >= NEED) {
            alive = false
            done()
        }
    }
}
