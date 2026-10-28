package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Pop Tantrum: tap groups of 2+ same-coloured buttons to pop them. 25 taps.
// Port of src/adventure/games/pop.js.
class PopGame(private val ctx: GameContext) : Game {
    private companion object {
        const val COLS = 8
        const val ROWS = 10
        const val CELL = 54f
        const val GX = (W - COLS * CELL) / 2f
        const val GY = 168f
        const val FACE = 44f
        const val TAPS = 25
        const val MORE_TAPS = 10 // a paid Continue adds this many
        const val GOAL = 1500 // matches the catalog, only used for the ending mood
        const val FALL = 2600f
        const val ME_X = 74f
        const val ME_Y = H - 46f
        const val ME_SIZE = 58f
        const val TW = COLS * CELL + 16f // the tray, with its 8px border
        const val TH = ROWS * CELL + 16f
        const val POP_LIFE = 0.3f
        const val MAX_POPPING = COLS * ROWS

        // the cousins, as (color, dark) pairs
        val PALETTE = arrayOf(
            0xFFFF6B6BL to 0xFFC94848L,
            0xFFFFD166L to 0xFFD1A33AL,
            0xFF6FD08CL to 0xFF46A464L,
            0xFF5FA8FFL to 0xFF3B7FD1L,
            0xFFB48CFFL to 0xFF8A62D6L,
        )

        val SINGLE = arrayOf("That one is alone. Like you.", "Two or more. Can you count?", "Nope.", "It takes two, genius.", "Free tap? Not today.")
        val POPS = arrayOf("That was my cousin!", "Monster.", "Uncle Gary, no!", "I will tell Grandma.", "Rude.", "Stop popping us!")
        val BIG = arrayOf("A WHOLE BRANCH!", "My family tree!", "You did that on purpose.")
        val DONE_GOOD = arrayOf("You popped my whole family.", "I hope you are proud.")
        val DONE_BAD = arrayOf("Family reunion: saved.", "Ha. Out of taps.")
    }

    private class Cell(var k: Int, var y: Float) {
        var vy = 0f
        var shake = 0f
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()
    private val palette = IntArray(PALETTE.size) { PALETTE[it].first.toInt() }

    // the cousins keep the outfit's eyes and scarf; hats and ears would poke into the next row
    private val looks = Array(PALETTE.size) { look.copy(color = PALETTE[it].first, dark = PALETTE[it].second, hat = null, ears = null) }

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    // cols[x] is a column of cells, bottom first
    private val cols = Array(COLS) { ArrayList<Cell>(ROWS) }
    private val popX = FloatArray(MAX_POPPING)
    private val popY = FloatArray(MAX_POPPING)
    private val popK = IntArray(MAX_POPPING)
    private val popLife = FloatArray(MAX_POPPING)
    private var popping = 0

    private val mouth = Mouth()
    private val fx = Fx()
    private var used = 0
    private var taps = TAPS // grows with each Continue
    private var t = 0f
    private var squash = 0f
    private var sulk = 0f // how long the main button stays upset after a pop

    private fun restY(i: Int) = GY + (ROWS - 1 - i) * CELL + CELL / 2f
    private fun cellX(x: Int) = GX + x * CELL + CELL / 2f
    private fun newK() = Random.nextInt(PALETTE.size)

    // fill every column up to ROWS; new cells start stacked above the grid
    private fun refill() {
        for (col in cols) {
            var above = 0
            while (col.size < ROWS) col.add(Cell(newK(), GY - CELL / 2f - CELL * above++))
        }
    }

    init {
        refill()
        for (col in cols) for ((i, cell) in col.withIndex()) cell.y = restY(i) - 500f - i * 30f - rand(0f, 40f) // rain in at the start
    }

    private fun settled(): Boolean {
        for (col in cols) for (i in col.indices) if (col[i].y != restY(i)) return false
        return true
    }

    // flood fill: every same-coloured neighbour of (x, i), into groupA/groupB
    private val seen = BooleanArray(COLS * ROWS)
    private val stackA = IntArray(COLS * ROWS)
    private val groupA = IntArray(COLS * ROWS)
    private val groupB = IntArray(COLS * ROWS)

    private fun groupAt(x: Int, i: Int): Int {
        val k = cols[x][i].k
        seen.fill(false)
        seen[x * ROWS + i] = true
        var sp = 0
        stackA[sp++] = x * ROWS + i
        var found = 0
        while (sp > 0) {
            val v = stackA[--sp]
            val a = v / ROWS
            val b = v % ROWS
            groupA[found] = a
            groupB[found] = b
            found++
            for (d in 0 until 4) {
                val na = a + (if (d == 0) 1 else if (d == 1) -1 else 0)
                val nb = b + (if (d == 2) 1 else if (d == 3) -1 else 0)
                if (na < 0 || na >= COLS || nb < 0 || nb >= ROWS) continue
                val id = na * ROWS + nb
                if (seen[id] || cols[na][nb].k != k) continue
                seen[id] = true
                stackA[sp++] = id
            }
        }
        return found
    }

    // with a refilled grid this almost never happens, but just in case
    private fun anyMove(): Boolean {
        for (x in 0 until COLS) for (i in 0 until ROWS) {
            if (x + 1 < COLS && cols[x + 1][i].k == cols[x][i].k) return true
            if (i + 1 < ROWS && cols[x][i + 1].k == cols[x][i].k) return true
        }
        return false
    }

    override fun down(x: Float, y: Float) {
        if (over || !settled()) return
        val cx = floor((x - GX) / CELL).toInt()
        val i = ROWS - 1 - floor((y - GY) / CELL).toInt()
        if (cx < 0 || cx >= COLS || i < 0 || i >= ROWS) return

        val n = groupAt(cx, i)
        if (n < 2) {
            cols[cx][i].shake = 0.4f
            ctx.sfx.play("nope")
            mouth.say(pick(SINGLE), 1.6f)
            return
        }

        val points = n * (n - 1) * 5
        score += points
        used++
        squash = 0.2f
        sulk = 1.2f
        ctx.sfx.play("pop")
        ctx.haptics.tap()
        if (n >= 6) {
            ctx.sfx.play("crack")
            ctx.haptics.thud()
        }

        var sx = 0f
        var sy = 0f
        for (g in 0 until n) {
            val a = groupA[g]
            val cell = cols[a][groupB[g]]
            if (popping < MAX_POPPING) {
                popX[popping] = cellX(a)
                popY[popping] = cell.y
                popK[popping] = cell.k
                popLife[popping] = POP_LIFE
                popping++
            }
            fx.sparks(cellX(a), cell.y, palette[cell.k], 5, 180f)
            sx += cellX(a) / n
            sy += cell.y / n
            cell.k = -1 // marked gone
        }
        fx.float(sx, sy - 10f, "+$points")
        for (col in cols) col.removeAll { it.k < 0 }
        refill()
        if (!anyMove()) for (col in cols) for (cell in col) cell.k = newK()

        if (used >= taps) {
            over = true
            mouth.say(pick(if (score >= GOAL) DONE_GOOD else DONE_BAD), 3f)
            ctx.sfx.play(if (score >= GOAL) "sob" else "nope")
        } else if (n >= 6) {
            mouth.say(pick(BIG), 1.8f)
        } else {
            mouth.maybe(POPS, 0.4f)
        }
    }

    // a paid Continue: more taps, same board, same score
    override fun revive() {
        over = false
        taps = used + MORE_TAPS
        sulk = 0f
        reviveFx(fx, mouth, ctx, ME_X + 20f, ME_Y - 30f, lookColor)
        fx.float(W - 100f, ME_Y - 34f, "+$MORE_TAPS taps", col("#aef3ff"))
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        squash = max(0f, squash - dt)
        sulk = max(0f, sulk - dt)
        var n = 0
        for (p in 0 until popping) {
            popLife[p] -= dt
            if (popLife[p] > 0f) {
                popX[n] = popX[p]; popY[n] = popY[p]; popK[n] = popK[p]; popLife[n] = popLife[p]
                n++
            }
        }
        popping = n

        // gravity: every cell falls to its resting row, with a bounce-free landing
        for (col in cols) {
            for (i in col.indices) {
                val cell = col[i]
                cell.shake = max(0f, cell.shake - dt)
                val rest = restY(i)
                if (cell.y >= rest) {
                    cell.y = rest
                    continue
                }
                cell.vy += FALL * dt
                cell.y += cell.vy * dt
                if (cell.y >= rest || cell.y.isNaN()) {
                    cell.y = rest
                    cell.vy = 0f
                }
            }
        }
    }

    private var shownLeft = -1
    private var shownText = ""

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        // a deep candy-pink room with drifting bokeh behind a glass tray
        c.sprite(art.room, 0f, 0f, W, H)
        for (i in 0 until 10) {
            val fi = i.toFloat()
            val bx = hash(fi * 2.7f) * W + sin(t * 0.25f + fi) * 20f
            val span = H + 200f
            val by = (((hash(fi * 4.1f) * span - t * (6f + hash(fi) * 10f)) % span) + span) % span - 100f
            c.softLight(bx, by, 30f + hash(fi + 5f) * 50f, palette[i % palette.size], 0.18f + sin(t + fi) * 0.06f)
        }
        c.sprite(art.tray, GX - 20f, GY - 20f)

        // new cells fall in from above, so hide anything outside the tray
        c.save()
        c.clipRect(GX - 8f, GY - 8f, GX - 8f + TW, GY - 8f + TH)
        for (x in 0 until COLS) {
            val col = cols[x]
            for (i in col.indices) {
                val cell = col[i]
                if (cell.k < 0) continue
                val falling = cell.y < restY(i)
                val cx = cellX(x)
                if (cell.shake > 0f) {
                    // the shaken one is drawn live, wobbling and angry
                    bobby(c, cx + sin(cell.shake * 60f) * 4f, cell.y, FACE, looks[cell.k], Mood.Angry, t + x * 0.9f + i * 1.7f)
                } else {
                    val s = cousin(cell.k, falling)
                    c.sprite(s, cx - s.w / 2f, cell.y - s.h / 2f)
                }
            }
        }
        for (p in 0 until popping) {
            val k = popLife[p] / POP_LIFE
            c.glow(popX[p], popY[p], FACE * (0.9f + (1f - k) * 0.9f), palette[popK[p]], k * 0.9f)
            bobby(c, popX[p], popY[p], FACE * (1f + (1f - k) * 0.6f), looks[popK[p]], Mood.Scared, t, alpha = k)
        }
        c.restore()
        // the tray's glass: a sheen over the top of everything inside it
        c.sprite(art.glass, GX - 8f, GY - 8f)

        // the main button sulks at the bottom, on a little spotlight
        c.softLight(ME_X, ME_Y + ME_SIZE * 0.3f, ME_SIZE * 1.3f, SPOT, 0.35f)
        val mood = when {
            over -> if (score >= GOAL) Mood.Sad else Mood.Smug
            sulk > 0f -> Mood.Scared
            mouth.line != null -> Mood.Smug
            else -> Mood.Angry
        }
        bobby(c, ME_X, ME_Y, ME_SIZE, look, mood, t, squash, tears = over && score >= GOAL)

        // taps left, on a glowing chip that turns red near the end
        val left = max(0, taps - used)
        val low = left <= 5
        val pulse = if (low && !over) 0.5f + sin(t * 8f) * 0.5f else 0f
        if (low) c.glow(W - 100f, ME_Y, 80f, ALARM, 0.35f + pulse * 0.35f)
        chip.set(W - 186f, ME_Y - 22f, W - 14f, ME_Y + 22f)
        chipFill.color = if (low) rgba(90, 8, 30, 0.75f) else rgba(50, 10, 50, 0.55f)
        c.drawRoundRect(chip, 22f, 22f, chipFill)
        chipLine.color = if (low) rgba(255, 120, 140, 0.8f) else rgba(255, 190, 230, 0.45f)
        c.drawRoundRect(chip, 22f, 22f, chipLine)
        if (left != shownLeft) {
            shownLeft = left
            shownText = "$left tap${if (left == 1) "" else "s"} left"
        }
        Txt.draw(c, shownText, W - 100f, ME_Y + 1f, 21f, if (low) col("#ffd0d8") else col("#ffe6f4"))

        fx.draw(c)
        c.vignette(0.4f, VIGNETTE)
        mouth.draw(c, ME_X, ME_Y - ME_SIZE * 0.6f)

        if (used > 0 || score > 0) c.drawScore(score)
        else c.drawScore("Tap a group", "Two or more of the same color.")
    }

    // A resting or falling cousin, rendered once through drawBobby and reused
    // (80 live buttons a frame would be wasteful).
    private val cousins = arrayOfNulls<Sprite>(PALETTE.size * 2)

    private fun cousin(k: Int, falling: Boolean): Sprite {
        val idx = k * 2 + (if (falling) 1 else 0)
        cousins[idx]?.let { return it }
        val s = renderBobby(looks[k], if (falling) Mood.Worried else Mood.Calm, FACE)
        cousins[idx] = s
        return s
    }

    /* ---------- art ---------- */

    private val SPOT = col("#ffb3dd")
    private val ALARM = col("#ff3a5a")
    private val VIGNETTE = col("#1a0420")
    private val chip = RectF()
    private val chipFill = fill()
    private val chipLine = stroke(WHITE, 1.5f)
    private val art = Art()

    private class Art {
        val room by lazy {
            makeSprite(W, H, 1f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#ff7cc0"), 0.35f to col("#d24a9a"), 0.75f to col("#7a2470"), 1f to col("#3e1046"))))
                // a warm light from the top centre
                c.drawRect(0f, 0f, W, H, fill().shade(rad(W / 2f, 40f, 0f, 420f, 0f to rgba(255, 230, 245, 0.45f), 1f to rgba(255, 230, 245, 0f))))
                // soft diagonal stripes, like candy wrapping
                val p = fill(rgba(255, 255, 255, 0.05f))
                var x = -H
                val path = Path()
                while (x < W) {
                    path.reset()
                    path.moveTo(x, H)
                    path.lineTo(x + 26f, H)
                    path.lineTo(x + 26f + H * 0.6f, 0f)
                    path.lineTo(x + H * 0.6f, 0f)
                    path.close()
                    c.drawPath(path, p)
                    x += 56f
                }
            }
        }

        // the tray: a recessed glass panel with a slot under every cell
        val tray by lazy {
            makeSprite(TW + 40f, TH + 40f) { c ->
                c.translate(20f, 20f)
                val box = RectF(0f, 0f, TW, TH)
                // drop shadow, baked once
                val sh = fill(rgba(60, 10, 60, 0.5f))
                sh.setShadowLayer(24f, 0f, 10f, rgba(40, 0, 40, 0.55f))
                c.drawRoundRect(box, 20f, 20f, sh)
                // the panel: darker at the bottom
                c.drawRoundRect(box, 20f, 20f, fill().shade(lin(0f, 0f, 0f, TH, 0f to rgba(70, 14, 70, 0.55f), 1f to rgba(30, 4, 40, 0.7f))))
                // the slots
                for (x in 0 until COLS) for (y in 0 until ROWS) {
                    val cx = 8f + x * CELL + CELL / 2f
                    val cy = 8f + y * CELL + CELL / 2f
                    c.drawCircle(cx, cy, CELL * 0.46f, fill().shade(rad(cx, cy - 2f, 4f, CELL * 0.46f, 0f to rgba(0, 0, 0, 0.25f), 0.8f to rgba(0, 0, 0, 0.1f), 1f to rgba(255, 200, 240, 0.08f))))
                }
                // an inner shadow along the top edge, and a bright rim
                c.save()
                val clip = Path()
                clip.addRoundRect(box, 20f, 20f, Path.Direction.CW)
                c.clipPath(clip)
                c.drawRect(0f, 0f, TW, 30f, fill().shade(lin(0f, 0f, 0f, 30f, 0f to rgba(20, 0, 30, 0.45f), 1f to rgba(20, 0, 30, 0f))))
                c.restore()
                c.drawRoundRect(RectF(0.75f, 0.75f, TW - 0.75f, TH - 0.75f), 19f, 19f, stroke(rgba(255, 190, 235, 0.55f), 1.5f))
            }
        }

        // a faint glass reflection laid over the tray's contents
        val glass by lazy {
            makeSprite(TW, TH, 1f) { c ->
                val clip = Path()
                clip.addRoundRect(RectF(0f, 0f, TW, TH), 20f, 20f, Path.Direction.CW)
                c.clipPath(clip)
                c.drawRect(0f, 0f, TW, TH, fill().shade(lin(0f, 0f, TW, TH, 0f to rgba(255, 255, 255, 0.12f), 0.35f to rgba(255, 255, 255, 0.03f), 0.36f to rgba(255, 255, 255, 0f), 1f to rgba(255, 255, 255, 0f))))
            }
        }
    }
}

// Renders the button once into a sprite (through drawBobby, so the real
// renderer shows up here too). The box is 1.6x the button's size.
internal fun renderBobby(look: Look, mood: Mood, size: Float, res: Float = RES): Sprite {
    val box = size * 1.6f
    val px = max(1, ceil(box * res).toInt())
    val img = ImageBitmap(px, px)
    try {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(px.toFloat(), px.toFloat())) {
            scale(px / box, px / box, Offset.Zero) {
                drawBobby(Offset(box / 2f, box / 2f), size, look, mood)
            }
        }
    } catch (_: Throwable) {
    }
    return Sprite(img.asAndroidBitmap(), box, box)
}

