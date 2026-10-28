package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.avatar.drawButton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// Button Run: a dash down three lanes of a giant keyboard, seen from just
// behind the button. The web version is real 3D (three.js); this one is a
// 2.5D Canvas port with its own little perspective camera. The world is in
// meters: x across (lanes at -1.6, 0, 1.6), y up, z ahead of the button
// (which always sits at z = 0). Swipe left/right to change lanes, swipe up or
// tap to jump, swipe down to slide.
// Port of src/adventure/games/button-run.js (+ run3d/world.js for the look).
class ButtonRunGame(private val ctx: GameContext) : Game {
    private companion object {
        const val LANE = 1.6f
        val LANES = floatArrayOf(-LANE, 0f, LANE)
        const val CAM_UP = 3f // camera height above the button
        const val CAM_BACK = 5.2f // camera distance behind the button
        const val FOCAL = 600f
        const val HORIZON = 296f
        const val NEAR = -4.5f // things behind this are gone for good
        const val FAR = 75f // things appear this far ahead
        const val MIN_Z = 1.2f - CAM_BACK // nothing is drawn closer to the camera than this
        const val TILE = 2f // keycap length along the track
        const val NEAR_KEY = -4f
        val ROWS = ceil((FAR - NEAR_KEY) / TILE).toInt() + 2

        const val GRAVITY = 38f
        const val JUMP = 12.5f // about 2 m high
        const val SPRING_JUMP = 18f // about 4 m: over a train
        const val FLY = 5f // cruising height with wings
        const val TALL = 1f
        const val LOW = 0.5f
        const val PZ = 0.3f
        const val SHIFT = 13f
        const val START_SPEED = 13f
        const val TOP_SPEED = 28f
        const val SPAM_SPEED = 7f
        const val TRAIN_TOP = 2.4f
        const val RAMP = 4f
        const val AD_LOW = 1.15f
        const val COIN = 5
        const val INTRO = 1.8f
        const val COIN_RATE = 11f // wallet coins never outrun this many a second (catalog allows 15)

        // obstacle kinds
        const val BLOCK = 0
        const val SLIDER = 1
        const val AD = 2
        const val WALL = 3
        const val TRAIN = 4
        const val RAMP_K = 5
        const val HOLE = 6
        const val CLICK = 7
        const val MOUSE = 8
        const val BAR = 9

        // power-ups
        const val MAGNET = 0
        const val WINGS = 1
        const val SPRING = 2
        const val BUBBLE = 3
        const val GHOST = 4
        const val DOUBLE = 5
        val P_NAME = arrayOf("Magnet", "Wings", "Spring shoes", "Bubble wrap", "Incognito", "Double coins")
        val P_ICON = arrayOf("🧲", "🕊", "👟", "◯", "🕶", "x2")
        val P_COLOR = intArrayOf(cc("#e0453a"), cc("#7cc9ff"), cc("#7cc6a4"), cc("#a7e3ff"), cc("#a58bd6"), cc("#f2c14e"))
        val P_TIME = floatArrayOf(10f, 7f, 10f, 0f, 6f, 15f)
        val GOT = arrayOf(
            "Money, come here.", "I am a bird now. Bye.", "Boing. Respectfully.",
            "Wrapped. Untouchable.", "Incognito. You can't see me.", "Double coins, double spite.",
        )

        val RUNS = arrayOf("You again?", "Stop following me!", "This keyboard is huge.", "I can hear your mouse.", "Leave me alone!", "Why is everything a key?")
        val BUMPS = arrayOf("Ow, my side!", "It is RIGHT behind me!", "Not a wall. NOT a wall.")
        val CRASH = arrayOf("Clicked. Again.", "OW.", "Fine. You win. For now.")
        val POPS = arrayOf("My bubble!", "Pop. Rude.")
        val CLOSE = arrayOf("Whoa.", "Too close!", "Missed me!")
        val STARTS = arrayOf("Bye!", "Catch me if you can.", "Run, legs, RUN.")
        val KEYS = arrayOf("ESC", "DEL", "ALT", "F1", "TAB", "END")
        val ADS = arrayOf("YOU WON!", "FREE RAM", "CLICK ME", "HOT DEALS", "1 VIRUS!")
        val SERVERS = arrayOf("SERVER", "CLOUD", "BACKUP")
        const val LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM"
        val CURSOR = floatArrayOf(0f, 0f, 0f, 17f, 4f, 13f, 7f, 20f, 9.5f, 19f, 6.5f, 12f, 12f, 12f)

        // sunset, night, morning, and round again
        val SKY_TOP = intArrayOf(cc("#0d1130"), cc("#020309"), cc("#1a3a74"))
        val SKY_MID = intArrayOf(cc("#4a3a8a"), cc("#0d1433"), cc("#6b8fcc"))
        val SKY_LOW = intArrayOf(cc("#d98a86"), cc("#1c2350"), cc("#e9bfa6"))
        val SKY_DISC = intArrayOf(cc("#ffe2c8"), cc("#d6e0ff"), WHITE)
        val SKY_STARS = floatArrayOf(0.35f, 1f, 0f)
        val SKY_LIGHT = floatArrayOf(0.92f, 0.5f, 1f)
        const val SKY_LEG = 900f
    }

    private class Thing {
        var kind = 0
        var lane = -1
        var x = 0f
        var z = 0f
        var len = 0f
        var label = ""
        var pct = 0f
        var ph = 0f
        var w = 0f
        var from = 0
        var tx = 0f
        var cy = 7f
        var slammed = false
        var track = false
        var moving = false
        var inZ = false
    }

    private class Coin(var x: Float, var y: Float, var z: Float) {
        var got = false
    }

    private class Pickup(val kind: Int, val x: Float, var z: Float) {
        var got = false
    }

    private class Decor(val kind: Int, val x: Float, var z: Float, val h: Float, val color: Int, val n: Int, val colors: IntArray)

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    // the wallet gets what the counter shows, but never faster than the catalog allows
    override val coins: Int get() = min(coinCount, (t * COIN_RATE).toInt() + 8)

    // the button
    private var lane = 1
    private var fromLane = 1
    private var meX = 0f
    private var meY = 0f
    private var vy = 0f
    private var ground = true
    private var gh = 0f
    private var onRamp = false
    private var slide = 0f
    private var squash = 0f
    private var spin = 0f
    private var pitch = 0f

    private val things = ArrayList<Thing>()
    private val coinList = ArrayList<Coin>()
    private val pickups = ArrayList<Pickup>()
    private val decor = ArrayList<Decor>()
    private val active = FloatArray(6) // seconds left per power; bubble is 1 until it pops
    private val laneEnd = FloatArray(3)
    private var camX = 0f
    private var camY = CAM_UP
    private val mouth = Mouth()
    private val fx = Fx(360)

    private var pressing = false
    private var pressX = 0f
    private var pressY = 0f
    private var pressDone = false
    private var speed = START_SPEED
    private var dist = 0f
    private var coinPts = 0
    private var coinCount = 0
    private var rowZ = 48f
    private var decorZ = 0f
    private var nextPower = 120f
    private var safe = 0f
    private var chase = 0f
    private var buffer = 0f
    private var chatter = 4f
    private var overT = 0f
    private var t = 0f
    private var shiftedAt = -9f
    private var coinPop = 0f
    private var flashColor = 0
    private var flashLeft = 0f
    private var phase = 0f
    private var glance = 1f
    private var glanceFor = 0f
    private var intro = INTRO
    private var shake = 0f

    private fun has(p: Int) = active[p] > 0f
    private fun lookBack(sec: Float) {
        glanceFor = max(glanceFor, sec)
    }

    private fun skipIntro() {
        intro = min(intro, 0.45f)
    }

    private fun flash(color: Int, sec: Float) {
        flashColor = color
        flashLeft = sec
    }

    /* ---------- projection ---------- */

    private var sx = 0f
    private var sy = 0f
    private var sk = 0f

    // Screen point of world (x, y, z) into sx, sy; sk is pixels per meter there.
    private fun proj(x: Float, y: Float, z: Float) {
        val d = max(0.05f, z + CAM_BACK)
        sk = FOCAL / d
        sx = W / 2f + (x - camX) * sk
        sy = HORIZON + (camY - y) * sk
    }

    /* ---------- building the track ---------- */

    private fun lineOfCoins(ln: Int, z0: Float, n: Int, arc: Float = 0f, arcAt: Float = 0f, ramp: Boolean = false) {
        for (i in 0 until n) {
            val z = z0 + i * 1.6f
            val y = when {
                ramp -> 0.6f + clamp((z - arcAt) / RAMP, 0f, 1f) * TRAIN_TOP
                arc > 0f -> 0.6f + max(0f, arc * (1f - ((z - arcAt) / 4.5f).let { it * it }))
                else -> 0.6f
            }
            coinList.add(Coin(LANES[ln], y, z))
        }
    }

    private fun add(o: Thing) {
        if (o.lane >= 0 && o.kind != MOUSE && o.kind != SLIDER) o.x = LANES[o.lane]
        things.add(o)
        if (o.lane >= 0) laneEnd[o.lane] = max(laneEnd[o.lane], o.z + o.len)
    }

    private fun thing(kind: Int, ln: Int, z: Float, len: Float, label: String = ""): Thing = Thing().also {
        it.kind = kind
        it.lane = ln
        it.z = z
        it.len = len
        it.label = label
    }

    private val shuffled = IntArray(3)
    private fun shuffleLanes() {
        shuffled[0] = 0
        shuffled[1] = 1
        shuffled[2] = 2
        for (i in 2 downTo 1) {
            val j = Random.nextInt(i + 1)
            val k = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = k
        }
    }

    private val row = IntArray(3)
    private val rowNone = 0
    private val rowBlock = 1
    private val rowAd = 2
    private val rowWall = 3
    private val rowHole = 4
    private val rowClick = 5

    private fun spawnRow(z: Float) {
        val gap = clamp(speed * 1.1f, 14f, 24f) + rand(0f, 6f)
        val r = Random.nextFloat()

        // a loading bar across the whole track: jump it
        if (dist > 250f && r < 0.1f) {
            add(thing(BAR, -1, z, 0.3f).also { it.pct = rand(0.6f, 0.97f) })
            lineOfCoins(Random.nextInt(3), z - 4f, 6, arc = 1.7f, arcAt = z)
            rowZ = z + gap
            return
        }
        // a keycap gliding back and forth between two lanes
        if (dist > 180f && r < 0.18f) {
            val a = Random.nextInt(2)
            add(
                thing(SLIDER, a, z, 0.7f, pick(KEYS)).also {
                    it.from = a
                    it.x = LANES[a]
                    it.ph = rand(0f, TAU)
                    it.w = rand(2.2f, 3.2f)
                },
            )
            if (chance(0.5f)) add(thing(BLOCK, if (a == 0) 2 else 0, z, 0.7f, pick(KEYS)))
            rowZ = z + gap
            if (chance(0.6f)) coinPattern(z + 3f, rowZ - 3f)
            return
        }
        // a giant mouse skidding sideways across the whole track: jump it
        if (dist > 280f && r < 0.25f) {
            val from = if (Random.nextBoolean()) -1f else 1f
            add(
                thing(MOUSE, if (from < 0f) 0 else 2, z, 1.3f).also {
                    it.x = from * 3.4f
                    it.tx = LANES[Random.nextInt(3)]
                },
            )
            rowZ = z + gap
            return
        }
        // trains of servers (some with a ramp to run up) and oncoming spam
        if (dist > 80f && r < 0.38f) {
            shuffleLanes()
            val n = if (dist > 400f && chance(0.4f)) 2 else 1
            val last = max(laneEnd[0], max(laneEnd[1], laneEnd[2]))
            val room = z - last > (SPAM_SPEED * (last + 3f)) / speed + 3f
            var far = z
            for (k in 0 until n) {
                val l = shuffled[k]
                val len = rand(10f, 16f)
                val moving = dist > 300f && room && chance(0.4f)
                val ramp = !moving && chance(0.5f)
                val tz = if (ramp) z + RAMP else z
                if (ramp) {
                    add(thing(RAMP_K, l, z, RAMP))
                    lineOfCoins(l, z + 0.5f, 9, arcAt = z, ramp = true)
                }
                add(thing(TRAIN, l, tz, len, if (moving) "SPAM" else pick(SERVERS)).also { it.moving = moving })
                far = max(far, tz + len)
            }
            val free = shuffled[n + Random.nextInt(3 - n)]
            if (chance(0.6f)) lineOfCoins(free, z, 6)
            else if (dist > 500f) add(thing(BLOCK, free, z + rand(2f, 8f), 0.7f, pick(KEYS)))
            rowZ = far + gap * 0.6f
            return
        }

        // a row of single obstacles, one per lane at most
        var poolN = 3
        val pool = IntArray(8)
        pool[0] = rowNone
        pool[1] = rowNone
        pool[2] = rowBlock
        if (dist > 40f) pool[poolN++] = rowAd
        if (dist > 120f) pool[poolN++] = rowWall
        if (dist > 220f) pool[poolN++] = rowHole
        if (dist > 350f) pool[poolN++] = rowClick
        for (l in 0 until 3) row[l] = pool[Random.nextInt(poolN)]
        var clicks = 0
        for (l in 0 until 3) if (row[l] == rowClick && clicks++ > 0) row[l] = rowNone
        if ((0 until 3).all { row[it] == rowWall || row[it] == rowClick }) row[Random.nextInt(3)] = rowNone
        if ((0 until 3).all { row[it] == rowNone }) row[Random.nextInt(3)] = rowBlock
        val anyWall = (0 until 3).any { row[it] == rowWall }

        for (l in 0 until 3) {
            when (row[l]) {
                rowBlock -> add(thing(BLOCK, l, z, 0.7f, pick(KEYS)))
                rowAd -> add(thing(AD, l, z, 0.25f, pick(ADS)))
                rowWall -> add(thing(WALL, l, z, 1f))
                rowHole -> {
                    // two keys missing, lined up with the keycaps on the track
                    val w0 = ceil((dist + z - 2f - NEAR_KEY) / TILE) * TILE + NEAR_KEY
                    add(thing(HOLE, l, w0 - dist, TILE * 2f))
                }
                rowClick -> add(thing(CLICK, l, z, 1.2f).also { it.track = !anyWall })
            }
        }

        // coins arcing over something jumpable in this row
        var openN = 0
        var openPick = -1
        for (l in 0 until 3) if (row[l] == rowBlock || row[l] == rowHole) if (Random.nextInt(++openN) == 0) openPick = l
        if (openPick >= 0 && chance(0.35f)) lineOfCoins(openPick, z - 4f, 6, arc = 1.7f, arcAt = z)

        // and now and then a power-up in an empty lane
        var emptyN = 0
        var emptyPick = -1
        for (l in 0 until 3) if (row[l] == rowNone) if (Random.nextInt(++emptyN) == 0) emptyPick = l
        if (dist >= nextPower && emptyPick >= 0) {
            pickups.add(Pickup(Random.nextInt(6), LANES[emptyPick], z + 3f))
            nextPower = dist + rand(200f, 340f)
        }
        rowZ = z + gap
        if (chance(0.75f)) coinPattern(z + 3f, rowZ - 3f)
    }

    // Coins laid out in a shape, between z0 and z1 (which is clear of obstacles).
    private fun coinPattern(z0: Float, z1: Float) {
        val len = min(z1 - z0, 14f)
        if (len < 5f) return
        fun put(x: Float, y: Float, z: Float) {
            coinList.add(Coin(x, y, z))
        }
        when (Random.nextInt(6)) {
            0 -> {
                // weaving from lane to lane
                var ln = Random.nextInt(3)
                var next = (ln + if (Random.nextBoolean()) 1 else -1).coerceIn(0, 2)
                var z = 0f
                while (z < len) {
                    val leg = (z % 4f) / 4f
                    put(LANES[ln] + (LANES[next] - LANES[ln]) * clamp((leg - 0.55f) / 0.45f, 0f, 1f), 0.6f, z0 + z)
                    if (leg + 1.3f / 4f >= 1f) {
                        ln = next
                        next = if (ln == 0 || ln == 2) 1 else if (Random.nextBoolean()) 0 else 2
                    }
                    z += 1.3f
                }
            }
            1 -> {
                // a smooth S across all three lanes
                val ph = rand(0f, TAU)
                var z = 0f
                while (z < len) {
                    put(sin(z * 0.45f + ph) * LANE, 0.6f, z0 + z)
                    z += 1.2f
                }
            }
            2 -> {
                // a bump in the air: jump for the middle ones
                val ln = Random.nextInt(3)
                var z = 0f
                while (z < len) {
                    put(LANES[ln], 0.6f + max(0f, sin((z / len) * PI_F) * 2.2f), z0 + z)
                    z += 1.3f
                }
            }
            3 -> {
                // towers of three, only the bottom one is free
                val ln = Random.nextInt(3)
                var z = 1f
                while (z < len) {
                    put(LANES[ln], 0.6f, z0 + z)
                    put(LANES[ln], 1.4f, z0 + z)
                    put(LANES[ln], 2.2f, z0 + z)
                    z += 4f
                }
            }
            4 -> {
                val mid = z0 + len / 2f
                val ln = Random.nextInt(3)
                val d = floatArrayOf(0f, -2.4f, -0.5f, -1.2f, 0.5f, -1.2f, -1f, 0f, 0f, 0f, 1f, 0f, -0.5f, 1.2f, 0.5f, 1.2f, 0f, 2.4f)
                var i = 0
                while (i < d.size) {
                    val dl = d[i]
                    val dz = d[i + 1]
                    val x = LANES[ln] + dl * LANE * 0.55f
                    if (abs(x) <= LANE + 0.3f) put(x, 0.6f + (if (dl == 0f && dz == 0f) 0.5f else 0f), mid + dz)
                    i += 2
                }
            }
            else -> {
                val ln = Random.nextInt(3)
                var z = 0f
                while (z < len) {
                    put(LANES[ln], 0.6f, z0 + z)
                    z += 1.6f
                }
            }
        }
    }

    private val PENCILS = intArrayOf(cc("#c9ccd4"), cc("#1d1f27"), cc("#c9a36a"), cc("#38d6ff"))
    private val MUGS = intArrayOf(cc("#f2f2f2"), cc("#1d1f27"), cc("#b8bcc6"))
    private val BOOKS = intArrayOf(cc("#2a2d36"), cc("#8a2f2f"), cc("#2f4a6b"), cc("#d9d4c7"), cc("#3c3f47"))

    private fun spawnDecor(z: Float) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * rand(3.4f, 4.6f)
            val r = Random.nextFloat()
            val dz = z + rand(0f, 3f)
            when {
                r < 0.45f -> decor.add(Decor(0, x, dz, rand(2.2f, 4.6f), PENCILS[Random.nextInt(PENCILS.size)], 0, PENCILS))
                r < 0.7f -> decor.add(Decor(1, x, dz, 0.9f, MUGS[Random.nextInt(MUGS.size)], 0, MUGS))
                else -> {
                    val cs = IntArray(4) { BOOKS[Random.nextInt(BOOKS.size)] }
                    decor.add(Decor(2, x, dz, 0f, 0, 2 + Random.nextInt(3), cs))
                }
            }
        }
        decorZ = z + rand(5f, 9f)
    }

    /* ---------- the button ---------- */

    private fun jumpNow() {
        if (over || has(WINGS)) return
        if (!ground) {
            buffer = 0.15f
            return
        }
        vy = if (has(SPRING)) SPRING_JUMP else JUMP
        ground = false
        slide = 0f
        buffer = 0f
        squash = 0.2f
        ctx.sfx.play("jump")
    }

    private fun slideNow() {
        if (over || has(WINGS)) return
        slide = 0.7f
        if (!ground) vy = min(vy, -16f) // slam back down
        ctx.sfx.play("whoosh")
    }

    private fun shift(dir: Int) {
        if (over) return
        val to = (lane + dir).coerceIn(0, 2)
        if (to == lane) {
            squash = 0.12f
            return
        }
        fromLane = lane
        lane = to
        shiftedAt = t
        ctx.sfx.play("tick")
        ctx.haptics.tap()
    }

    private fun power(kind: Int) {
        active[kind] = if (P_TIME[kind] > 0f) P_TIME[kind] else 1f
        ctx.sfx.play("win")
        ctx.haptics.tap()
        flash(P_COLOR[kind], 0.35f)
        mouth.say(GOT[kind], 2f)
        lookBack(1.2f)
        if (kind == WINGS) {
            // a trail of coins up in the sky
            var ln = lane
            var z = 6f
            while (z < speed * (P_TIME[kind] - 1f)) {
                if (chance(0.12f)) ln = (ln + if (Random.nextBoolean()) 1 else -1).coerceIn(0, 2)
                coinList.add(Coin(LANES[ln], FLY + 0.5f, z))
                z += 2.2f
            }
        }
    }

    private fun crash(fell: Boolean) {
        if (over) return
        over = true
        ctx.sfx.play("hit")
        ctx.haptics.thud()
        shake = 1f
        flash(cc("#ff3b2f"), 0.5f)
        proj(meX, meY + 0.5f, 0f)
        fx.burst(sx, sy, lookColor, 22, 320f)
        fx.sparks(sx, sy, WHITE, 10, 260f)
        fx.ring(sx, sy, cc("#ffd0c0"), 100f, 0.4f)
        mouth.say(if (fell) "AAAAH—" else pick(CRASH), 3f)
        if (!fell) {
            vy = 9f
            ground = false
        }
    }

    // Brushed the side of something while changing lanes: bounce back, and the
    // cursor catches up. Do it twice in a row and it has you.
    private fun bump() {
        if (chase > 0f) return crash(false)
        chase = 5f
        safe = 0.6f
        lane = fromLane
        ctx.sfx.play("thud")
        ctx.haptics.thud()
        shake = max(shake, 0.5f)
        mouth.say(pick(BUMPS), 1.6f)
        lookBack(1.4f)
    }

    // What the button stands on right now: the desk, a train roof, a ramp, or
    // nothing at all over a hole.
    private fun groundUnder(): Float {
        var g = 0f
        onRamp = false
        for (o in things) {
            if (o.lane < 0 || abs(o.x - meX) > 0.75f) continue
            if (o.kind == HOLE && abs(o.x - meX) < 0.45f && o.z + 0.3f < 0f && o.z + o.len - 0.3f > 0f) {
                g = -20f
            } else if (o.kind == TRAIN && o.z < PZ && o.z + o.len > -PZ && meY >= TRAIN_TOP - 0.5f) {
                g = max(g, TRAIN_TOP)
            } else if (o.kind == RAMP_K && o.z < 0f && o.z + o.len > 0f) {
                val s = (-o.z / o.len) * TRAIN_TOP
                if (meY >= s - 0.6f && s >= g) {
                    g = s
                    onRamp = true
                }
            }
        }
        return g
    }

    private fun physics(dt: Float) {
        val g = groundUnder()
        gh = g
        if (has(WINGS)) {
            meY += (FLY - meY) * min(1f, dt * 4f)
            vy = 0f
            ground = false
            return
        }
        if (ground) {
            if (g < meY - 0.05f) {
                ground = false // ran off the end of a roof, or over a hole
                vy = 0f
            } else {
                meY = g
            }
        }
        if (!ground) {
            vy -= GRAVITY * (if (slide > 0f) 1.8f else 1f) * dt
            meY += vy * dt
            if (vy <= 0f && meY <= g && meY > g - 0.6f) {
                meY = g
                vy = 0f
                ground = true
                squash = 0.18f
                if (over) {
                    spin = 0f
                } else if (t > 0.5f) {
                    proj(meX, meY, 0f)
                    fx.mote(sx - 14f, sy, rgba(255, 255, 255, 0.8f), -60f, -30f, 3f, 0.4f, add = false, drag = 3f)
                    fx.mote(sx + 14f, sy, rgba(255, 255, 255, 0.8f), 60f, -30f, 3f, 0.4f, add = false, drag = 3f)
                }
                if (buffer > 0f) jumpNow()
            } else if (meY < -0.8f && !over) {
                crash(true)
            }
        }
        if (meY.isNaN() || meY < -30f) {
            meY = -30f
            vy = 0f
        }
    }

    // The box the button can run into, or false for things it can't.
    private var hx0 = 0f
    private var hx1 = 0f
    private var hy0 = 0f
    private var hy1 = 0f
    private fun hitOf(o: Thing): Boolean {
        val lx = o.x
        when (o.kind) {
            SLIDER, BLOCK -> set(lx - 0.7f, lx + 0.7f, 0f, 0.8f)
            AD -> set(lx - 0.75f, lx + 0.75f, AD_LOW, AD_LOW + 1.25f)
            WALL -> set(lx - 0.75f, lx + 0.75f, 0f, 3.2f)
            TRAIN -> set(lx - 0.72f, lx + 0.72f, 0f, TRAIN_TOP)
            MOUSE -> set(lx - 0.6f, lx + 0.6f, 0f, 0.75f)
            BAR -> set(-2.6f, 2.6f, 0.1f, 0.7f)
            CLICK -> if (o.cy < 1.2f) set(lx - 0.6f, lx + 0.6f, o.cy, o.cy + 1.6f) else return false
            else -> return false
        }
        return true
    }

    private fun set(a: Float, b: Float, c: Float, d: Float) {
        hx0 = a
        hx1 = b
        hy0 = c
        hy1 = d
    }

    private fun hits() {
        val bx0 = meX - 0.38f
        val bx1 = meX + 0.38f
        val by0 = meY + 0.05f
        val by1 = meY + if (slide > 0f) LOW else TALL
        for (o in things) {
            if (!hitOf(o)) continue
            val was = o.inZ
            o.inZ = o.z < PZ && o.z + o.len > -PZ
            if (!o.inZ) continue
            if (hx1 <= bx0 || hx0 >= bx1 || hy1 <= by0 || hy0 >= by1) {
                // swerved out of its lane at the last moment
                if (!was && o.lane == fromLane && t - shiftedAt < 0.45f && (o.kind == WALL || o.kind == TRAIN || o.kind == CLICK || o.kind == SLIDER)) {
                    coinPts += 10
                    proj(meX, meY + 1.6f, 0f)
                    fx.float(sx, sy, "Close one! +10", cc("#ffe27a"))
                    ctx.sfx.play("coin")
                    mouth.maybe(CLOSE, 0.5f, 1f)
                }
                continue
            }
            if (safe > 0f || has(GHOST)) continue
            if (has(BUBBLE)) {
                active[BUBBLE] = 0f
                safe = 1.2f
                ctx.sfx.play("crack")
                ctx.haptics.thud()
                proj(meX, meY + 0.5f, 0f)
                fx.sparks(sx, sy, cc("#cdefff"), 16, 260f)
                fx.ring(sx, sy, cc("#cdefff"), 80f, 0.35f)
                mouth.say(pick(POPS), 1.4f)
                continue
            }
            // already alongside it before this frame: we swerved into its side
            if (was) bump() else crash(false)
            if (over) return
        }
    }

    private fun collect(dt: Float) {
        val magnet = has(MAGNET)
        for (k in coinList) {
            if (k.got) continue
            if (magnet && k.z < 14f && k.z > -1f) {
                val f = min(1f, dt * 9f)
                k.x += (meX - k.x) * f
                k.y += (meY + 0.5f - k.y) * f
                k.z += -k.z * f
            }
            // spring shoes sweep up the coins underneath a big jump, too
            val low = if (has(SPRING) && !ground) gh - 0.3f else meY - 0.45f
            if (abs(k.z) < 0.7f && abs(k.x - meX) < 0.75f && k.y > low && k.y < meY + 1.45f) {
                k.got = true
                val dbl = has(DOUBLE)
                coinPts += if (dbl) COIN * 2 else COIN
                coinCount += if (dbl) 2 else 1
                coinPop = 0.18f
                ctx.sfx.play("coin")
                proj(k.x, k.y, max(k.z, 0f))
                fx.burst(sx, sy, COIN_GLOW, 6, 160f, gravity = 200f, size = 2.4f, life = 0.4f)
            }
        }
        for (p in pickups) {
            if (p.got) continue
            if (abs(p.z) < 0.8f && abs(p.x - meX) < 0.8f && meY < 2.5f) {
                p.got = true
                power(p.kind)
                proj(p.x, 1f, 0f)
                fx.sparks(sx, sy, P_COLOR[p.kind], 20, 260f)
                fx.ring(sx, sy, P_COLOR[p.kind], 90f, 0.4f)
            }
        }
    }

    private fun step(dt: Float) {
        if (over) {
            overT += dt
            speed = max(0f, speed - 30f * dt)
            spin += dt * 5f
        } else {
            speed = min(TOP_SPEED, min(START_SPEED + t * 0.16f, speed + 6f * dt)) // eases back up after a Continue
        }

        val move = speed * dt
        for (o in things) o.z -= move + (if (o.moving && !over) SPAM_SPEED * dt else 0f)
        for (k in coinList) k.z -= move
        for (p in pickups) p.z -= move
        for (d in decor) d.z -= move
        for (l in 0 until 3) laneEnd[l] -= move
        rowZ -= move
        decorZ -= move

        // things that move on their own
        for (o in things) {
            when {
                o.kind == SLIDER -> {
                    o.x = (LANES[o.from] + LANES[o.from + 1]) / 2f + sin(t * o.w + o.ph) * (LANE / 2f)
                    o.lane = ((o.x / LANE).roundToInt() + 1).coerceIn(0, 2)
                }
                o.kind == MOUSE && !over -> {
                    // timed to cross the middle lane just as it reaches you
                    val left = max(0.4f, (o.z + 1f) / max(speed, 1f))
                    o.x += ((o.tx - o.x) / left) * dt
                    o.lane = ((o.x / LANE).roundToInt() + 1).coerceIn(0, 2)
                }
                o.kind == CLICK && o.track && o.z > 24f && !over -> {
                    // it follows your lane until it locks on
                    o.x += clamp(LANES[lane] - o.x, -5f * dt, 5f * dt)
                    o.lane = ((o.x / LANE).roundToInt() + 1).coerceIn(0, 2)
                }
                o.kind == CLICK -> o.x += (LANES[o.lane] - o.x) * min(1f, dt * 10f)
            }
        }

        // the cursor hovers over its lane, then clicks down as you get close
        for (o in things) {
            if (o.kind != CLICK) continue
            val k = clamp((16f - o.z) / 7f, 0f, 1f)
            o.cy = 7f * (1f - k * k)
            if (k >= 1f && !o.slammed) {
                o.slammed = true
                ctx.sfx.play("thud")
                if (o.z < 20f) shake = max(shake, 0.35f)
                proj(o.x, 0f, o.z + 0.6f)
                fx.sparks(sx, sy, WHITE, 12, 200f)
            }
        }

        meX += clamp(LANES[lane] - meX, -SHIFT * dt, SHIFT * dt)
        slide -= dt
        physics(dt)

        if (!over) {
            dist += move
            var guard = 0
            while (rowZ < FAR && guard++ < 8) spawnRow(rowZ)
            hits()
            if (!over) collect(dt)
        }
        var guard = 0
        while (decorZ < FAR && guard++ < 8) spawnDecor(decorZ)

        things.removeAll { it.z + it.len < NEAR - 1f }
        coinList.removeAll { it.got || it.z < NEAR }
        pickups.removeAll { it.got || it.z < NEAR }
        decor.removeAll { it.z < NEAR - 1f }
    }

    /* ---------- input ---------- */

    override fun down(x: Float, y: Float) {
        if (intro > 0f) return skipIntro()
        pressing = true
        pressX = x
        pressY = y
        pressDone = false
    }

    override fun move(x: Float, y: Float) {
        if (!pressing || pressDone) return
        val dx = x - pressX
        val dy = y - pressY
        if (max(abs(dx), abs(dy)) < 28f) return
        pressDone = true
        if (abs(dx) > abs(dy)) shift(sign(dx).toInt()) else if (dy < 0f) jumpNow() else slideNow()
    }

    override fun up(x: Float, y: Float) {
        if (pressing && !pressDone) jumpNow()
        pressing = false
    }

    // a paid Continue: clear the track near the button, put it back on the
    // desk in its lane, and give it a couple of seconds of safety
    override fun revive() {
        over = false
        things.removeAll { it.z < 28f }
        for (l in 0 until 3) laneEnd[l] = 0f
        for (o in things) if (o.lane >= 0) laneEnd[o.lane] = max(laneEnd[o.lane], o.z + o.len)
        fromLane = lane
        meX = LANES[lane]
        meY = 0f
        vy = 0f
        ground = true
        slide = 0f
        spin = 0f
        pitch = 0f
        speed = max(speed, START_SPEED * 0.6f)
        overT = 0f
        chase = 0f
        buffer = 0f
        pressing = false
        safe = 2.2f
        flash(cc("#8fe9ff"), 0.35f)
        lookBack(1.2f)
        proj(meX, meY + 0.5f, 0f)
        reviveFx(fx, mouth, ctx, sx, sy, lookColor)
    }

    override fun update(dt: Float) {
        if (!(dt > 0f)) return
        t += dt
        coinPop = max(0f, coinPop - dt)
        flashLeft -= dt
        shake = max(0f, shake - dt * 2.2f)
        mouth.update(dt)
        fx.update(dt)
        squash = max(0f, squash - dt)
        buffer -= dt
        safe -= dt
        chase -= dt

        if (intro > 0f) {
            if (intro == INTRO) mouth.say("Oh no. Not you again.", 1.4f)
            intro -= dt
            if (intro <= 0f) {
                mouth.say(pick(STARTS), 1.2f)
                ctx.sfx.play("whoosh")
            }
        } else {
            // small steps, so nothing thin gets skipped over at top speed
            val n = ceil(dt / (1f / 90f)).toInt().coerceIn(1, 6)
            for (i in 0 until n) step(dt / n)
        }

        if (!over) {
            for (k in 0 until 6) {
                if (k == BUBBLE || active[k] <= 0f) continue
                active[k] -= dt
                if (active[k] > 0f) continue
                active[k] = 0f
                if (k == WINGS) safe = 1.5f // a soft landing
            }
            chatter -= dt
            if (chatter <= 0f && intro <= 0f) {
                chatter = rand(6f, 10f)
                mouth.maybe(RUNS, 0.6f)
                if (mouth.line != null) lookBack(1.5f)
            }
            score = floor(dist).toInt() + coinPts
        }

        if (ground && !over) phase += dt * speed * 0.8f
        // lean into a ramp while running up it
        pitch += ((if (ground && onRamp) 0.42f else 0f) - pitch) * min(1f, dt * 12f)
        glanceFor -= dt
        val wantGlance = if (glanceFor > 0f || over || intro > 0f) 1f else 0f
        glance += (wantGlance - glance) * min(1f, dt * 7f)
        camX += (meX * 0.75f - camX) * min(1f, dt * 6f)
        camY += (CAM_UP + max(0f, meY) * 0.75f - camY) * min(1f, dt * 5f)
        if (camX.isNaN()) camX = 0f
        if (camY.isNaN()) camY = CAM_UP
    }

    /* ---------- drawing ---------- */

    private val COIN_GLOW = cc("#ffd84d")
    private val NEON = cc("#38d6ff")
    private val path = Path()
    private val solid = fill(WHITE)
    private val line = stroke(WHITE, 2f)
    private val rect = RectF()
    private var fogColor = SKY_LOW[0]
    private var light = 1f
    private var stars = 0f
    private var skyTop = 0
    private var skyMid = 0
    private var disc = WHITE
    private var skyKey = -1
    private val skyPaint = fill()
    private val deskPaint = fill()
    private var face = Face()

    private fun updateSky() {
        val leg = floor(dist / SKY_LEG).toInt()
        val a = ((leg % 3) + 3) % 3
        val b = (a + 1) % 3
        val p = (dist / SKY_LEG) - leg
        val f = smooth(clamp((p - 0.75f) / 0.25f, 0f, 1f))
        skyTop = mix(SKY_TOP[a], SKY_TOP[b], f)
        skyMid = mix(SKY_MID[a], SKY_MID[b], f)
        fogColor = mix(SKY_LOW[a], SKY_LOW[b], f)
        disc = mix(SKY_DISC[a], SKY_DISC[b], f)
        stars = SKY_STARS[a] + (SKY_STARS[b] - SKY_STARS[a]) * f
        light = SKY_LIGHT[a] + (SKY_LIGHT[b] - SKY_LIGHT[a]) * f
        // gradients are rebuilt only when the colours have moved on a step
        val key = a * 100 + (f * 40f).toInt()
        if (key != skyKey) {
            skyKey = key
            skyPaint.shader = lin(0f, 0f, 0f, HORIZON, 0f to skyTop, 0.6f to skyMid, 1f to fogColor)
            val deskNear = shadeOf(cc("#4a3b55"), 0f)
            deskPaint.shader = lin(0f, HORIZON, 0f, H, 0f to fogColor, 0.12f to mix(fogColor, deskNear, 0.6f), 1f to darken(deskNear, 0.25f))
        }
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    private fun fogOf(z: Float): Float = clamp((z - 12f) / (FAR - 12f), 0f, 1f).let { it * (2f - it) } * 0.92f

    // A surface colour at fog amount f, darkened at night.
    private fun shadeOf(c: Int, f: Float): Int {
        val lit = if (light < 1f) mix(c, cc("#0a0c1c"), (1f - light) * 0.7f) else c
        return mix(lit, fogColor, f)
    }

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        scope = this
        updateSky()
        val shx = shakeBy(shake * 14f)
        val shy = shakeBy(shake * 10f)
        c.save()
        c.translate(shx, shy)
        drawSky(c)
        c.drawRect(-20f, HORIZON, W + 20f, H + 20f, deskPaint)
        drawKeyboard(c)
        drawWorld(c)
        c.restore()

        if (speed > 17f && !over) speedLines(c, (speed - 17f) / (TOP_SPEED - 17f))
        fx.draw(c)
        c.vignette(0.3f, cc("#1a0b24"))
        proj(meX, meY + 1.35f, 0f)
        mouth.draw(c, sx + 10f, sy + shy)

        if (intro > 0f) {
            val a = clamp(intro / 0.4f, 0f, 1f)
            Txt.draw(c, "Swipe ← → to dodge", W / 2f, 560f, 20f, WHITE, rgba(30, 14, 40, 0.5f), 5f, alpha = a)
            Txt.draw(c, "Swipe ↑ or tap to jump · ↓ to slide", W / 2f, 590f, 16f, WHITE, rgba(30, 14, 40, 0.5f), 4f, alpha = a)
        }
        c.drawScore(score, if (t < 5f) "Swipe to dodge · tap to jump" else "points")
        hud(c)
        if (flashLeft > 0f) {
            solid.color = rgba(flashColor, min(0.45f, flashLeft))
            c.drawRect(0f, 0f, W, H, solid)
        }
    }

    private fun drawSky(c: Canvas) {
        c.drawRect(-20f, -20f, W + 20f, HORIZON + 1f, skyPaint)
        if (stars > 0.01f) {
            for (i in 0 until 46) {
                val fi = i.toFloat()
                val x = hash(fi + 1f) * W - camX * 4f
                val y = hash(fi + 2f) * (HORIZON - 60f)
                val tw = 0.5f + 0.5f * sin(t * (1f + hash(fi) * 2f) + fi)
                solid.color = rgba(255, 255, 255, stars * (0.25f + hash(fi + 3f) * 0.6f) * tw)
                val wx = ((x % W) + W) % W
                c.drawRect(wx, y, wx + 1.6f, y + 1.6f, solid)
            }
        }
        // the sun (or moon) low over the far end of the keyboard
        val sunX = W / 2f + 70f - camX * 6f
        val sunY = HORIZON - 70f + (camY - CAM_UP) * 8f
        c.glow(sunX, sunY, 150f, disc, 0.35f)
        solid.color = disc
        c.drawCircle(sunX, sunY, 26f, solid)
        // haze where the desk meets the sky
        c.softLight(W / 2f, HORIZON, 260f, fogColor, 0.5f)
    }

    // The keyboard: a dark body, neon strips between the keys, and rows of
    // keycaps rushing toward the camera. Holes are keys that are missing.
    private val holeZ0 = FloatArray(16)
    private val holeZ1 = FloatArray(16)
    private val holeLane = IntArray(16)
    private var holes = 0

    private fun drawKeyboard(c: Canvas) {
        holes = 0
        for (o in things) if (o.kind == HOLE && holes < 16) {
            holeZ0[holes] = o.z
            holeZ1[holes] = o.z + o.len
            holeLane[holes] = o.lane
            holes++
        }
        // the body
        solid.color = shadeOf(cc("#1a1c22"), 0f)
        quadPaint(c, -3.1f, -0.42f, MIN_Z, 3.1f, -0.42f, MIN_Z, 3.1f, -0.42f, FAR + 6f, -3.1f, -0.42f, FAR + 6f, solid)
        // neon strips between the keys
        line.color = NEON
        line.xfermode = ADD
        for (x in floatArrayOf(-0.8f, 0.8f, -2.36f - 0.25f, 2.36f + 0.25f)) {
            proj(x, -0.4f, MIN_Z)
            val ax = sx
            val ay = sy
            proj(x, -0.4f, FAR)
            line.strokeWidth = 5f
            line.alpha = 70
            c.drawLine(ax, ay, sx, sy, line)
            line.strokeWidth = 2f
            line.alpha = 220
            c.drawLine(ax, ay, sx, sy, line)
        }
        line.xfermode = null
        line.alpha = 255

        val off = ((dist % TILE) + TILE) % TILE
        val base = floor(dist / TILE).toInt()
        for (i in ROWS - 1 downTo 0) {
            val k0 = NEAR_KEY + i * TILE - off
            val k1 = k0 + TILE - 0.16f
            if (k1 < MIN_Z || k0 > FAR + 2f) continue
            val z0 = max(k0, MIN_Z)
            val f = fogOf(z0)
            val absRow = base + i
            val top = shadeOf(cc("#eceef4"), f)
            val front = shadeOf(cc("#a8abb9"), f)
            val sideTop = shadeOf(cc("#3a3d48"), f)
            val sideFront = shadeOf(cc("#24262e"), f)
            // the narrow keys along the edges
            for (sd in intArrayOf(-1, 1)) {
                val x0 = sd * 2.36f - 0.21f
                keycap(c, x0, x0 + 0.42f, z0, k1, sideTop, sideFront)
            }
            for (l in 0 until 3) {
                val mid = (k0 + k1) / 2f
                val x0 = LANES[l] - 0.72f
                val x1 = LANES[l] + 0.72f
                if (inHole(l, mid)) {
                    pit(c, x0, x1, z0, k1, f)
                    continue
                }
                keycap(c, x0, x1, z0, k1, top, front)
                // the letter printed on it
                if (z0 < 34f) {
                    val ch = LETTERS[(hash(absRow * 3f + l) * LETTERS.length).toInt().coerceIn(0, LETTERS.length - 1)]
                    proj(LANES[l], 0f, (z0 + k1) / 2f)
                    val cx = sx
                    val cyy = sy
                    val size = 0.5f * sk
                    if (size > 4f) {
                        proj(LANES[l], 0f, z0)
                        val nearY = sy
                        proj(LANES[l], 0f, k1)
                        val squeeze = clamp((nearY - sy) / (1.84f * sk), 0.15f, 1f)
                        c.save()
                        c.translate(cx, cyy)
                        c.scale(1f, squeeze)
                        Txt.draw(c, LETTER_STR[ch - 'A'], 0f, 0f, size, rgba(shadeOf(cc("#5a5e70"), f), 0.85f), black = true)
                        c.restore()
                    }
                }
            }
        }
    }

    private val LETTER_STR = Array(26) { ('A' + it).toString() }

    private fun inHole(l: Int, z: Float): Boolean {
        for (i in 0 until holes) if (holeLane[i] == l && z > holeZ0[i] && z < holeZ1[i]) return true
        return false
    }

    private fun keycap(c: Canvas, x0: Float, x1: Float, z0: Float, z1: Float, top: Int, front: Int) {
        solid.color = top
        quadPaint(c, x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, x0, 0f, z1, solid)
        // the front face (a screen-aligned rectangle, it faces the camera)
        proj(x0, 0f, z0)
        val l = sx
        val tp = sy
        proj(x1, -0.44f, z0)
        solid.color = front
        c.drawRect(l, tp, sx, sy, solid)
    }

    private fun pit(c: Canvas, x0: Float, x1: Float, z0: Float, z1: Float, f: Float) {
        solid.color = shadeOf(cc("#050508"), f * 0.6f)
        quadPaint(c, x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, x0, 0f, z1, solid)
        // the far wall catches a little light
        proj(x0, 0f, z1)
        val l = sx
        val tp = sy
        proj(x1, -0.3f, z1)
        solid.color = shadeOf(cc("#2a1f3a"), f)
        c.drawRect(l, tp, sx, min(sy, tp + 6f), solid)
    }

    private fun quadPaint(
        c: Canvas,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        p: Paint,
    ) {
        path.reset()
        proj(ax, ay, az)
        path.moveTo(sx, sy)
        proj(bx, by, bz)
        path.lineTo(sx, sy)
        proj(cx, cy, cz)
        path.lineTo(sx, sy)
        proj(dx, dy, dz)
        path.lineTo(sx, sy)
        path.close()
        c.drawPath(path, p)
    }

    // A box seen from the camera: the sides that face it, its top, then its
    // front. Returns the front face's screen rectangle in `rect`.
    private fun box(c: Canvas, x0: Float, x1: Float, y0: Float, y1: Float, zIn: Float, z1: Float, top: Int, front: Int, side: Int) {
        val z0 = max(zIn, MIN_Z)
        if (z1 <= z0) {
            rect.setEmpty()
            return
        }
        val f = fogOf(z0)
        solid.color = shadeOf(side, f)
        if (camX < x0) quadPaint(c, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, solid)
        if (camX > x1) quadPaint(c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, solid)
        if (camY > y1) {
            solid.color = shadeOf(top, f)
            quadPaint(c, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, solid)
        }
        proj(x0, y1, z0)
        val l = sx
        val tp = sy
        proj(x1, y0, z0)
        rect.set(l, tp, sx, sy)
        solid.color = shadeOf(front, f)
        c.drawRect(rect, solid)
    }

    // Fog over a sprite drawn on a front face.
    private fun fogRect(c: Canvas, z: Float) {
        val f = fogOf(max(z, MIN_Z))
        val dark = (1f - light) * 0.55f
        if (dark > 0.02f) {
            solid.color = rgba(cc("#0a0c1c"), dark)
            c.drawRect(rect, solid)
        }
        if (f > 0.02f) {
            solid.color = rgba(fogColor, f)
            c.drawRect(rect, solid)
        }
    }

    /* ---------- the world, far to near ---------- */

    private val dKey = FloatArray(1024)
    private val dRef = arrayOfNulls<Any>(1024)
    private val order = IntArray(1024)
    private val ME = Any()

    private fun drawWorld(c: Canvas) {
        var n = 0
        fun put(key: Float, ref: Any) {
            if (n >= dKey.size) return
            dKey[n] = key
            dRef[n] = ref
            n++
        }
        for (o in things) {
            if (o.kind == HOLE) continue
            if (o.z > FAR + 2f || o.z + o.len < MIN_Z) continue
            var key = max(o.z, MIN_Z)
            // something the button is standing on (or running up) goes under it
            if ((o.kind == TRAIN || o.kind == RAMP_K) && o.z < 0.3f && o.z + o.len > -0.3f && abs(o.x - meX) < 1f) key = 0.01f
            put(key, o)
        }
        for (k in coinList) if (!k.got && k.z > MIN_Z + 0.3f && k.z < FAR) put(k.z, k)
        for (p in pickups) if (!p.got && p.z > MIN_Z + 0.3f && p.z < FAR) put(p.z, p)
        for (d in decor) if (d.z > MIN_Z && d.z < FAR + 2f) put(d.z, d)
        put(0f, ME)
        for (i in 0 until n) order[i] = i
        // insertion sort, farthest first (nearly sorted frame to frame)
        for (i in 1 until n) {
            val v = order[i]
            val kv = dKey[v]
            var j = i - 1
            while (j >= 0 && dKey[order[j]] < kv) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = v
        }
        for (i in 0 until n) {
            when (val r = dRef[order[i]]) {
                is Thing -> drawThing(c, r)
                is Coin -> drawCoin(c, r)
                is Pickup -> drawPickup(c, r)
                is Decor -> drawDecor(c, r)
                else -> if (r === ME) drawMe(c)
            }
        }
        for (i in 0 until n) dRef[i] = null
    }

    private fun drawThing(c: Canvas, o: Thing) {
        val x = o.x
        when (o.kind) {
            BLOCK, SLIDER -> {
                val tint = if (o.kind == BLOCK) cc("#ff6b5a") else cc("#5fd0c0")
                c.shadowAt(x, o.z + 0.35f, 0.9f)
                box(c, x - 0.7f, x + 0.7f, 0f, 0.8f, o.z, o.z + 0.7f, lighten(tint, 0.35f), darken(tint, 0.15f), darken(tint, 0.4f))
                if (!rect.isEmpty && rect.height() > 6f) {
                    Txt.draw(c, o.label, rect.centerX(), rect.centerY(), rect.height() * 0.45f, rgba(255, 255, 255, 0.92f), black = true)
                    fogRect(c, o.z)
                }
            }
            AD -> {
                // a popup window floating at slide height on two thin wires
                proj(x - 0.6f, AD_LOW, o.z)
                val wl = sx
                val wy = sy
                proj(x + 0.6f, 0f, o.z)
                line.color = rgba(shadeOf(cc("#c8ccd6"), fogOf(o.z)), 0.7f)
                line.strokeWidth = max(1f, sk * 0.03f)
                c.drawLine(wl, wy, wl, sy, line)
                c.drawLine(sx, wy, sx, sy, line)
                proj(x - 0.75f, AD_LOW + 1.25f, max(o.z, MIN_Z))
                val l = sx
                val tp = sy
                proj(x + 0.75f, AD_LOW, max(o.z, MIN_Z))
                rect.set(l, tp, sx, sy)
                val blink = sin(t * 10f) > 0f
                c.glow(rect.centerX(), rect.centerY(), rect.width() * 0.9f, if (blink) cc("#ff5a4a") else cc("#ffb340"), 0.35f * (1f - fogOf(o.z)))
                c.sprite(art.ad(ADS.indexOf(o.label).coerceAtLeast(0), blink), rect.left, rect.top, rect.width(), rect.height())
                fogRect(c, o.z)
            }
            WALL -> {
                // a firewall: bricks on fire
                c.shadowAt(x, o.z + 0.5f, 1.1f)
                box(c, x - 0.75f, x + 0.75f, 0f, 3.2f, o.z, o.z + 1f, cc("#ff8a3a"), cc("#b8432c"), cc("#7a2a1f"))
                if (!rect.isEmpty) {
                    c.sprite(art.firewall, rect.left, rect.top, rect.width(), rect.height())
                    val f = 1f - fogOf(o.z)
                    // flames licking off the top
                    for (k in 0 until 3) {
                        val fk = k.toFloat()
                        val fl = 0.6f + 0.4f * sin(t * (9f + fk * 3f) + fk * 2f + o.z)
                        val gx = rect.left + rect.width() * (0.2f + fk * 0.3f)
                        c.glow(gx, rect.top - rect.width() * 0.08f * fl, rect.width() * 0.35f * fl, cc("#ff7a2a"), 0.8f * f)
                    }
                    c.glow(rect.centerX(), rect.centerY(), rect.width() * 1.1f, cc("#ff4a20"), 0.25f * f)
                    fogRect(c, o.z)
                }
            }
            TRAIN -> {
                val spam = o.moving
                val body = if (spam) cc("#d8433a") else cc("#3a3f4f")
                c.shadowAt(x, o.z + 1f, 1f)
                box(c, x - 0.72f, x + 0.72f, 0f, TRAIN_TOP, o.z, o.z + o.len, lighten(body, 0.25f), body, darken(body, 0.35f))
                // LED stripes down the side we can see
                val sideX = if (camX < x - 0.72f) x - 0.72f else if (camX > x + 0.72f) x + 0.72f else Float.NaN
                if (!sideX.isNaN()) {
                    val z0 = max(o.z, MIN_Z)
                    val z1 = o.z + o.len
                    if (z1 > z0) {
                        val led = if (spam) cc("#ffe27a") else NEON
                        line.color = rgba(led, 0.8f * (1f - fogOf(z0)))
                        for (yy in floatArrayOf(0.7f, 1.5f)) {
                            proj(sideX, yy, z0)
                            val ax = sx
                            val ay = sy
                            line.strokeWidth = max(1f, sk * 0.05f)
                            proj(sideX, yy, z1)
                            c.drawLine(ax, ay, sx, sy, line)
                        }
                    }
                }
                if (!rect.isEmpty) {
                    c.sprite(art.server(o.label), rect.left, rect.top, rect.width(), rect.height())
                    if (spam) {
                        // headlights, coming at you
                        val f = 1f - fogOf(o.z)
                        c.glow(rect.left + rect.width() * 0.22f, rect.bottom - rect.height() * 0.16f, rect.width() * 0.4f, cc("#fff2c0"), f)
                        c.glow(rect.right - rect.width() * 0.22f, rect.bottom - rect.height() * 0.16f, rect.width() * 0.4f, cc("#fff2c0"), f)
                    }
                    fogRect(c, o.z)
                }
            }
            RAMP_K -> {
                val f = fogOf(max(o.z, MIN_Z))
                val z0 = max(o.z, MIN_Z)
                val z1 = o.z + o.len
                if (z1 <= z0) return
                val y0 = ((z0 - o.z) / o.len) * TRAIN_TOP
                solid.color = shadeOf(cc("#a77a2a"), f)
                if (camX < x - 0.72f) tri(c, x - 0.72f, z0, y0, z1)
                if (camX > x + 0.72f) tri(c, x + 0.72f, z0, y0, z1)
                solid.color = shadeOf(cc("#f0c36d"), f)
                quadPaint(c, x - 0.72f, y0, z0, x + 0.72f, y0, z0, x + 0.72f, TRAIN_TOP, z1, x - 0.72f, TRAIN_TOP, z1, solid)
                // chevrons pointing up the slope
                line.color = rgba(shadeOf(cc("#3a2a12"), f), 0.75f)
                for (k in 1..3) {
                    val zz = o.z + o.len * k / 4f
                    if (zz < z0) continue
                    val yy = (k / 4f) * TRAIN_TOP
                    proj(x - 0.5f, yy, zz)
                    val ax = sx
                    val ay = sy
                    line.strokeWidth = max(1f, sk * 0.08f)
                    proj(x, yy + 0.25f, zz + 0.3f)
                    val bx = sx
                    val by = sy
                    proj(x + 0.5f, yy, zz)
                    c.drawLine(ax, ay, bx, by, line)
                    c.drawLine(bx, by, sx, sy, line)
                }
            }
            CLICK -> {
                // your own cursor, hovering over a lane, then clicking down
                val f = 1f - fogOf(o.z)
                val k = clamp(1f - o.cy / 7f, 0f, 1f)
                c.shadowAt(x, o.z + 0.6f, 0.4f + k * 0.6f, 0.15f + k * 0.35f)
                proj(x, o.cy, max(o.z + 0.6f, MIN_Z))
                c.glow(sx, sy - sk * 0.6f, sk * 1.4f, cc("#ff4060"), (0.2f + k * 0.2f) * f)
                cursorArrow(c, sx, sy, sk * 0.09f, PI_F + 0.45f)
            }
            MOUSE -> {
                c.shadowAt(x, o.z + 0.65f, 0.9f)
                box(c, x - 0.6f, x + 0.6f, 0f, 0.45f, o.z, o.z + 1.3f, cc("#e9ebf0"), cc("#c8ccd6"), cc("#8a8f9c"))
                // the domed top and the scroll wheel
                val z0 = max(o.z, MIN_Z)
                proj(x - 0.6f, 0.75f, z0)
                val l = sx
                val tp = sy
                proj(x + 0.6f, 0.3f, z0)
                rect.set(l, tp, sx, sy)
                solid.color = shadeOf(cc("#f6f7fa"), fogOf(z0))
                c.drawOval(rect, solid)
                solid.color = shadeOf(cc("#4a4f5c"), fogOf(z0))
                val cx = rect.centerX()
                c.drawRoundRect(cx - rect.width() * 0.05f, rect.top + rect.height() * 0.1f, cx + rect.width() * 0.05f, rect.top + rect.height() * 0.45f, 4f, 4f, solid)
                line.color = rgba(shadeOf(cc("#8a8f9c"), fogOf(z0)), 0.8f)
                line.strokeWidth = max(1f, sk * 0.02f)
                c.drawLine(cx, rect.top + rect.height() * 0.05f, cx, rect.centerY(), line)
                // the cable trailing behind it
                proj(x, 0.3f, z0 + 1.3f)
                val ax = sx
                val ay = sy
                proj(x - (o.tx - x).sign * 2f, 0.05f, z0 + 3f)
                line.color = shadeOf(cc("#2a2d36"), fogOf(z0))
                c.drawLine(ax, ay, sx, sy, line)
            }
            BAR -> {
                val z0 = max(o.z, MIN_Z)
                val f = fogOf(z0)
                // two posts and a loading bar strung between them
                for (px in floatArrayOf(-2.6f, 2.6f)) box(c, px - 0.08f, px + 0.08f, 0f, 0.8f, o.z, o.z + 0.16f, cc("#d6d9e2"), cc("#a8abb9"), cc("#6a6e7c"))
                proj(-2.5f, 0.7f, z0)
                val l = sx
                val tp = sy
                proj(2.5f, 0.1f, z0)
                rect.set(l, tp, sx, sy)
                solid.color = shadeOf(cc("#f4f5f9"), f)
                c.drawRoundRect(rect, rect.height() * 0.3f, rect.height() * 0.3f, solid)
                val inset = rect.height() * 0.16f
                solid.color = shadeOf(cc("#2a2d36"), f)
                c.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, rect.height() * 0.2f, rect.height() * 0.2f, solid)
                solid.color = shadeOf(cc("#4fd06a"), f)
                val fillTo = rect.left + inset + (rect.width() - inset * 2f) * o.pct
                c.drawRoundRect(rect.left + inset, rect.top + inset, fillTo, rect.bottom - inset, rect.height() * 0.2f, rect.height() * 0.2f, solid)
                if (rect.height() > 8f) Txt.draw(c, "Loading… ${(o.pct * 100f).toInt()}%", rect.centerX(), rect.centerY(), rect.height() * 0.42f, WHITE, alpha = 1f - f)
            }
        }
    }

    // A ramp's side: a right triangle rising from the desk.
    private fun tri(c: Canvas, x: Float, z0: Float, y0: Float, z1: Float) {
        path.reset()
        proj(x, 0f, z0)
        path.moveTo(sx, sy)
        proj(x, y0, z0)
        path.lineTo(sx, sy)
        proj(x, TRAIN_TOP, z1)
        path.lineTo(sx, sy)
        proj(x, 0f, z1)
        path.lineTo(sx, sy)
        path.close()
        c.drawPath(path, solid)
    }

    // A soft shadow on the keys under (x, z).
    private fun Canvas.shadowAt(x: Float, z: Float, rx: Float, a: Float = 0.35f) {
        if (z < MIN_Z) return
        proj(x, 0f, z)
        shadow(sx, sy, rx * sk, a * (1f - fogOf(z)), rx * sk * 0.28f)
    }

    private fun cursorArrow(c: Canvas, x: Float, y: Float, scale: Float, angle: Float) {
        if (!(scale > 0f) || x.isNaN() || y.isNaN()) return
        c.save()
        c.translate(x, y)
        c.rotate(angle * DEG)
        c.scale(scale, scale)
        c.save()
        c.translate(1.2f, -1.4f)
        c.drawPath(art.cursorPath, cursorShadow)
        c.restore()
        c.drawPath(art.cursorPath, art.cursorFill)
        c.drawPath(art.cursorPath, cursorLine)
        c.restore()
    }

    private val cursorShadow = fill(rgba(20, 10, 40, 0.3f))
    private val cursorLine = stroke(cc("#1b1b22"), 0.9f)

    private fun drawCoin(c: Canvas, k: Coin) {
        proj(k.x, k.y, k.z)
        val r = 0.3f * sk
        if (r < 0.8f) return
        val f = fogOf(k.z)
        val squeeze = abs(cos(t * 5f + k.z * 0.3f))
        c.glow(sx, sy, r * 2.4f, cc("#ffc23a"), 0.45f * (1f - f))
        val hw = r * (0.15f + 0.85f * squeeze)
        c.sprite(art.coin, sx - hw, sy - r, hw * 2f, r * 2f, 1f - f * 0.7f)
    }

    private fun drawPickup(c: Canvas, p: Pickup) {
        val y = 1f + sin(t * 3f + p.z) * 0.15f
        proj(p.x, y, p.z)
        val r = 0.42f * sk
        if (r < 1f) return
        val col = P_COLOR[p.kind]
        val f = fogOf(p.z)
        c.shadowAt(p.x, p.z, 0.45f)
        c.glow(sx, sy, r * 3f, col, 0.7f * (1f - f))
        c.rays(sx, sy, r * 3.2f, col, 0.25f * (1f - f), t * 0.6f)
        solid.color = shadeOf(col, f * 0.5f)
        c.drawCircle(sx, sy, r, solid)
        solid.color = rgba(255, 255, 255, 0.5f * (1f - f))
        c.drawCircle(sx - r * 0.3f, sy - r * 0.35f, r * 0.28f, solid)
        if (p.kind == BUBBLE) {
            line.color = rgba(255, 255, 255, 0.9f)
            line.strokeWidth = max(1f, r * 0.12f)
            c.drawCircle(sx, sy, r * 0.5f, line)
        } else {
            Txt.draw(c, P_ICON[p.kind], sx, sy + r * 0.05f, r * (if (p.kind == DOUBLE) 0.8f else 1.05f), INK, black = true, alpha = 1f - f)
        }
    }

    private fun drawDecor(c: Canvas, d: Decor) {
        val f = fogOf(max(d.z, MIN_Z))
        when (d.kind) {
            0 -> {
                // a pencil standing in the desk
                box(c, d.x - 0.12f, d.x + 0.12f, -1f, -1f + d.h, d.z, d.z + 0.24f, lighten(d.color, 0.2f), d.color, darken(d.color, 0.35f))
                if (!rect.isEmpty) {
                    proj(d.x, -1f + d.h + 0.35f, max(d.z, MIN_Z))
                    path.reset()
                    path.moveTo(rect.left, rect.top)
                    path.lineTo(rect.right, rect.top)
                    path.lineTo(sx, sy)
                    path.close()
                    solid.color = shadeOf(cc("#e8c39a"), f)
                    c.drawPath(path, solid)
                }
            }
            1 -> {
                // a mug
                box(c, d.x - 0.4f, d.x + 0.4f, -1f, -0.1f, d.z, d.z + 0.8f, darken(d.color, 0.55f), d.color, darken(d.color, 0.3f))
                if (!rect.isEmpty) {
                    line.color = shadeOf(darken(d.color, 0.1f), f)
                    line.strokeWidth = rect.width() * 0.12f
                    val side = if (d.x < 0f) 1f else -1f
                    val hx = if (side > 0f) rect.right else rect.left
                    rect.set(hx - rect.width() * 0.22f, rect.top + rect.height() * 0.2f, hx + rect.width() * 0.22f, rect.top + rect.height() * 0.65f)
                    c.drawArc(rect, if (side > 0f) -90f else 90f, 180f, false, line)
                }
            }
            else -> {
                // a stack of books
                var y = -1f
                for (i in 0 until d.n) {
                    val h = 0.32f
                    val wob = (hash(d.z * 7f + i) - 0.5f) * 0.3f
                    box(c, d.x - 0.7f + wob, d.x + 0.7f + wob, y, y + h, d.z, d.z + 1.6f, cc("#efe8d8"), d.colors[i % d.colors.size], darken(d.colors[i % d.colors.size], 0.3f))
                    y += h
                }
            }
        }
    }

    // The button: from behind while it runs, turning round to glare at you
    // now and then. Wings, a bubble and spring shoes when it has them.
    private fun drawMe(c: Canvas) {
        proj(meX, meY, 0f)
        val feetX = sx
        val feetY = sy
        val k = sk
        // its shadow on whatever is under it
        if (gh > -1f) {
            proj(meX, max(gh, 0f) + 0.01f, 0f)
            val lift = clamp((meY - max(gh, 0f)) / 4f, 0f, 1f)
            c.shadow(sx, sy, 0.45f * sk * (1f - lift * 0.5f), 0.4f * (1f - lift * 0.6f), 0.13f * sk)
        }
        val sliding = slide > 0f && ground
        val sq = when {
            sliding -> 0.38f
            else -> squash * 1.6f - (if (!ground && vy > 0f) 0.12f else 0f)
        }
        val size = 0.84f * k
        val bob = if (ground && !over && intro <= 0f) abs(sin(phase * 1.6f)) * size * 0.07f else 0f
        val cx = feetX
        val cy = feetY - size * 0.72f - bob
        val alpha = when {
            has(GHOST) -> 0.45f
            else -> blinkAlpha(safe, t)
        }
        val lean = (meX - LANES[lane]) * 0.25f
        val angle = if (over) -spin else -pitch * 0.5f + lean

        // falling into a hole: the keys in front hide it
        var clipped = false
        if (gh < -1f && meY < 0.2f) {
            proj(meX, 0f, -0.3f)
            c.save()
            c.clipRect(-100f, -100f, W + 100f, sy)
            clipped = true
        }

        if (has(WINGS)) wings(c, cx, cy, size)
        if (has(SPRING) && !sliding) spring(c, cx, feetY, size)

        if (alpha < 0.995f) {
            val m = size * 1.8f
            c.saveLayerAlpha(cx - m, cy - m, cx + m, cy + m, (alpha * 255f).toInt())
        } else {
            c.save()
        }
        c.rotate(angle * DEG, cx, feetY)
        val mood = when {
            over -> Mood.Sad
            chase > 0f -> Mood.Worried
            !ground -> Mood.Scared
            intro > 0f -> Mood.Annoyed
            else -> Mood.Angry
        }
        if (face.mood != mood) face = Face(mood = mood)
        scope?.drawButton(Offset(cx, cy), size, look, face, t = t, squash = clamp(sq, -0.5f, 0.5f), legs = true, tears = over, facing = (lane - fromLane) * 0.3f * (1f - glance))
        // seen from behind: the back of its round body covers the face
        val back = 1f - glance
        if (back > 0.02f) {
            c.save()
            if (sq != 0f) c.scale(1f + clamp(sq, -0.5f, 0.5f), 1f - clamp(sq, -0.5f, 0.5f), cx, cy + size * 0.55f)
            c.sprite(art.back, cx - size * 0.505f, cy - size * 0.505f, size * 1.01f, size * 1.01f, back)
            c.restore()
        }
        c.restore()

        if (has(BUBBLE)) {
            val r = size * 0.85f + sin(t * 4f) * 2f
            c.softLight(cx, cy, r * 1.1f, cc("#a7e3ff"), 0.25f)
            line.color = rgba(225, 245, 255, 0.75f)
            line.strokeWidth = max(1.5f, size * 0.03f)
            c.drawCircle(cx, cy + size * 0.05f, r, line)
            solid.color = rgba(255, 255, 255, 0.6f)
            c.drawCircle(cx - r * 0.45f, cy - r * 0.45f, r * 0.12f, solid)
        }
        if (has(MAGNET)) Txt.draw(c, P_ICON[MAGNET], cx + size * 0.55f, cy - size * 0.55f + sin(t * 6f) * 3f, size * 0.32f)
        if (clipped) c.restore()

        // the cursor right behind it after a bump, and clicking it at the end
        if (chase > 0f || over) {
            val kk = if (over) clamp(overT / 0.35f, 0f, 1f) else 0f
            val ax = cx - size * 0.75f + (if (over) size * 0.7f * kk else sin(t * 3f) * size * 0.15f)
            val ay = cy - size * 1.15f + (if (over) size * 0.7f * kk else sin(t * 9f) * size * 0.08f)
            c.glow(ax, ay - size * 0.4f, size * 1.4f, cc("#ff4060"), 0.2f)
            cursorArrow(c, ax, ay, size * 0.11f, PI_F + 0.55f)
        }
    }

    private var scope: DrawScope? = null

    private fun wings(c: Canvas, cx: Float, cy: Float, size: Float) {
        val flap = sin(t * 14f) * 0.5f
        for (sd in intArrayOf(-1, 1)) {
            c.save()
            c.translate(cx + sd * size * 0.35f, cy - size * 0.05f)
            c.rotate(sd * (-20f + flap * 40f))
            c.scale(sd.toFloat(), 1f)
            c.drawPath(art.wingPath, art.wingFill)
            c.restore()
        }
        // a few feathers drifting off
        if (chance(0.08f)) fx.mote(cx + rand(-size, size), cy, WHITE, rand(-40f, 40f), rand(20f, 60f), 2.5f, 0.6f)
    }

    private fun spring(c: Canvas, cx: Float, feetY: Float, size: Float) {
        line.color = cc("#7cc6a4")
        line.strokeWidth = max(1.5f, size * 0.05f)
        val h = size * 0.16f
        for (sd in intArrayOf(-1, 1)) {
            val x = cx + sd * size * 0.18f
            path.reset()
            path.moveTo(x, feetY)
            for (i in 1..4) path.lineTo(x + (if (i % 2 == 0) -1 else 1) * size * 0.06f, feetY - h * i / 4f)
            c.drawPath(path, line)
        }
    }

    // Streaks rushing past the edges of the screen at speed.
    private fun speedLines(c: Canvas, k: Float) {
        line.color = rgba(255, 255, 255, 0.12f + k * 0.25f)
        line.strokeWidth = 2f
        val ft = floor(t * 20f)
        val ft2 = floor(t * 30f).toInt()
        for (i in 0 until 18) {
            val a = i * 2.39996f + ft * 0.7f
            val dx = cos(a)
            val dy = sin(a) * 1.4f
            val r0 = 300f + ((i * 53 + ft2 * 17) % 120)
            val r1 = r0 + 60f + k * 90f
            c.drawLine(W / 2f + dx * r0, 400f + dy * r0, W / 2f + dx * r1, 400f + dy * r1, line)
        }
    }

    private val hudBg = fill(rgba(25, 18, 40, 0.55f))
    private val hudTrack = fill(rgba(255, 255, 255, 0.25f))

    // Power-ups running, with time left, down the left side; coins top right.
    private fun hud(c: Canvas) {
        var y = 150f
        for (k in 0 until 6) {
            val left = active[k]
            if (left <= 0f) continue
            c.drawRoundRect(12f, y, 152f, y + 36f, 18f, 18f, hudBg)
            solid.color = P_COLOR[k]
            c.drawCircle(30f, y + 18f, 14f, solid)
            if (k == BUBBLE) {
                line.color = WHITE
                line.strokeWidth = 2f
                c.drawCircle(30f, y + 18f, 7f, line)
            } else {
                Txt.draw(c, P_ICON[k], 30f, y + 19f, if (k == DOUBLE) 13f else 15f, INK, black = true)
            }
            if (P_TIME[k] > 0f) {
                c.drawRoundRect(52f, y + 14f, 140f, y + 22f, 4f, 4f, hudTrack)
                val w = 88f * clamp(left / P_TIME[k], 0f, 1f)
                if (w > 1f) c.drawRoundRect(52f, y + 14f, 52f + w, y + 22f, 4f, 4f, solid)
            } else {
                Txt.draw(c, "one hit", 96f, y + 18f, 13f, WHITE)
            }
            y += 44f
        }
        val pop = 1f + coinPop * 1.6f
        c.save()
        c.scale(pop, pop, W - 58f, 168f)
        c.drawRoundRect(W - 104f, 150f, W - 12f, 186f, 18f, 18f, hudBg)
        solid.color = cc("#f2b632")
        c.drawCircle(W - 86f, 168f, 11f, solid)
        solid.color = cc("#ffe27a")
        c.drawCircle(W - 86f, 168f, 6f, solid)
        Txt.draw(c, coinText(), W - 46f, 169f, 18f, WHITE)
        c.restore()
    }

    private var lastCoins = -1
    private var lastCoinText = "0"
    private fun coinText(): String {
        val n = coins
        if (n != lastCoins) {
            lastCoins = n
            lastCoinText = n.toString()
        }
        return lastCoinText
    }

    private val art = Art(look)

    private class Art(look: Look) {
        val coin by lazy {
            makeSprite(26f, 26f) { c ->
                c.drawOval(RectF(0f, 0f, 26f, 26f), fill(cc("#b9781a")))
                c.drawOval(RectF(1.2f, 1f, 24.8f, 25f), fill().shade(lin(3f, 0f, 23f, 26f, 0f to cc("#fff2a8"), 0.5f to cc("#f2b632"), 1f to cc("#c98a1a"))))
                c.drawOval(RectF(6.5f, 6f, 19.5f, 20f), fill(rgba(255, 240, 170, 0.9f)))
                Txt.draw(c, "¢", 13f, 13.5f, 12f, cc("#c98a1a"), black = true)
            }
        }

        // The back of the button: its body colour, lit from the top.
        val back by lazy {
            val color = look.color.toInt()
            val dark = look.dark.toInt()
            makeSprite(100f, 100f) { c ->
                c.drawCircle(50f, 50f, 50f, fill().shade(rad(38f, 30f, 0f, 70f, 0f to lighten(color, 0.3f), 0.55f to color, 1f to dark)))
                c.drawCircle(50f, 50f, 49f, stroke(rgba(dark, 0.6f), 2f))
                // a soft rim light and a little seam down the middle
                c.drawArc(RectF(8f, 6f, 92f, 90f), 200f, 70f, false, stroke(rgba(255, 255, 255, 0.45f), 4f))
                c.drawLine(50f, 14f, 50f, 86f, stroke(rgba(dark, 0.25f), 2f))
            }
        }

        val firewall by lazy {
            makeSprite(75f, 160f) { c ->
                c.drawRect(0f, 0f, 75f, 160f, fill().shade(lin(0f, 0f, 0f, 160f, 0f to cc("#d9573a"), 1f to cc("#8e2a1a"))))
                val mortar = stroke(rgba(60, 20, 10, 0.55f), 1.6f)
                var y = 0f
                var r = 0
                while (y < 160f) {
                    c.drawLine(0f, y, 75f, y, mortar)
                    var x = if (r % 2 == 0) 0f else 12.5f
                    while (x < 75f) {
                        c.drawLine(x, y, x, y + 14f, mortar)
                        x += 25f
                    }
                    y += 14f
                    r++
                }
                // hot glow in the gaps, and the sign
                c.drawRect(0f, 0f, 75f, 160f, fill().shade(lin(0f, 0f, 0f, 160f, 0f to rgba(255, 200, 80, 0.35f), 0.4f to rgba(255, 120, 40, 0f))))
                c.drawRoundRect(RectF(6f, 64f, 69f, 86f), 4f, 4f, fill(rgba(30, 10, 10, 0.75f)))
                Txt.draw(c, "FIREWALL", 37.5f, 75f, 10.5f, cc("#ffb340"), black = true)
            }
        }

        private val servers = HashMap<String, Sprite>()
        fun server(label: String): Sprite = servers.getOrPut(label) {
            val spam = label == "SPAM"
            makeSprite(72f, 120f) { c ->
                val body = if (spam) cc("#d8433a") else cc("#3a3f4f")
                c.drawRoundRect(RectF(0f, 0f, 72f, 120f), 5f, 5f, fill().shade(lin(0f, 0f, 0f, 120f, 0f to lighten(body, 0.2f), 1f to darken(body, 0.3f))))
                if (spam) {
                    // a big envelope face, and a windscreen
                    c.drawRoundRect(RectF(8f, 10f, 64f, 40f), 4f, 4f, fill(cc("#2a1418")))
                    c.drawRect(14f, 54f, 58f, 84f, fill(cc("#fff4e0")))
                    val env = stroke(cc("#d8433a"), 2f)
                    c.drawLine(14f, 54f, 36f, 72f, env)
                    c.drawLine(58f, 54f, 36f, 72f, env)
                    Txt.draw(c, "SPAM", 36f, 25f, 15f, cc("#ffe27a"), black = true)
                } else {
                    // racks with blinking lights
                    for (i in 0 until 6) {
                        val y = 10f + i * 16f
                        c.drawRoundRect(RectF(6f, y, 66f, y + 12f), 2f, 2f, fill(cc("#262a36")))
                        c.drawCircle(13f, y + 6f, 2f, fill(if (i % 3 == 0) cc("#4fd06a") else cc("#38d6ff")))
                        c.drawCircle(19f, y + 6f, 2f, fill(if (i % 2 == 0) cc("#ffb340") else cc("#4fd06a")))
                        c.drawRect(28f, y + 4f, 60f, y + 8f, fill(rgba(255, 255, 255, 0.12f)))
                    }
                    Txt.draw(c, label, 36f, 109f, 11f, cc("#bfe9ff"), black = true)
                }
                c.drawRect(2f, 1f, 70f, 3f, fill(rgba(255, 255, 255, 0.3f)))
            }
        }

        private val ads = arrayOfNulls<Sprite>(ADS.size * 2)
        fun ad(label: Int, blink: Boolean): Sprite {
            val k = label * 2 + (if (blink) 1 else 0)
            ads[k]?.let { return it }
            val s = makeSprite(130f, 108f) { c ->
                val w = 130f
                val h = 108f
                c.drawRoundRect(RectF(0f, 0f, w, h), 6f, 6f, fill().shade(lin(0f, 0f, 0f, h, 0f to WHITE, 1f to cc("#e3e8f2"))))
                val bar = fill().shade(lin(0f, 0f, 0f, 18f, 0f to cc("#6f9bff"), 1f to cc("#2f5ac8")))
                c.drawRoundRect(RectF(0f, 0f, w, 20f), 6f, 6f, bar)
                c.drawRect(0f, 10f, w, 20f, bar)
                c.drawRoundRect(RectF(w - 18f, 3f, w - 4f, 17f), 3f, 3f, fill(cc("#e0453a")))
                Txt.draw(c, "×", w - 11f, 10f, 13f)
                Txt.draw(c, ADS[label], w / 2f, 50f, 19f, if (blink) cc("#e0453a") else cc("#f2a516"), black = true)
                c.drawRoundRect(RectF(w / 2f - 30f, 72f, w / 2f + 30f, 94f), 5f, 5f, fill().shade(lin(0f, 72f, 0f, 94f, 0f to cc("#6bd486"), 1f to cc("#2f9a4a"))))
                Txt.draw(c, "OK", w / 2f, 83f, 12f)
            }
            ads[k] = s
            return s
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

        val cursorFill by lazy { fill().shade(lin(0f, 0f, 10f, 18f, 0f to WHITE, 1f to cc("#c9cfe0"))) }

        // A wing, unit-sized (about 60 wide), pointing out to the right.
        val wingPath by lazy {
            Path().apply {
                moveTo(0f, 0f)
                cubicTo(20f, -30f, 50f, -34f, 62f, -22f)
                cubicTo(50f, -14f, 54f, -6f, 44f, -2f)
                cubicTo(40f, 4f, 30f, 8f, 22f, 6f)
                cubicTo(14f, 12f, 6f, 8f, 0f, 0f)
                close()
            }
        }
        val wingFill by lazy { fill().shade(lin(0f, -30f, 0f, 10f, 0f to WHITE, 1f to cc("#bfe3ff"))) }
    }

    init {
        // a first stretch of desk junk so the sides aren't bare
        var z = -4f
        while (z < FAR) {
            spawnDecor(z)
            z = decorZ
        }
    }

}

// Colours by hex, parsed once (drawing code asks for them every frame).
private val ccCache = HashMap<String, Int>()
private fun cc(hex: String): Int = ccCache.getOrPut(hex) { col(hex) }
