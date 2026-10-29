package com.ankitsilwal.buttonhatesyou.games

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Copy Me: the button plays a tune on its four friends, you play it back.
// Port of src/adventure/games/simon.js.
class SimonGame(private val ctx: GameContext) : Game {
    private companion object {
        const val CX = W / 2f
        const val CY = H * 0.58f
        const val REACH = 150f // how far each pad sits from the middle
        const val PAD = 128f
        const val ME_SIZE = 78f
        const val LIT = 0.22f // how long a pad you tap stays lit
        const val RING = REACH + PAD * 0.62f // the dance floor's radius
        const val MAX_SEQ = 512

        const val WAIT = 0 // before a round
        const val SHOW = 1 // the button plays
        const val COPY = 2 // your turn

        // up, right, down, left
        val DX = floatArrayOf(0f, 1f, 0f, -1f)
        val DY = floatArrayOf(-1f, 0f, 1f, 0f)
        val COLORS = arrayOf("#6fd08c", "#ff6b6b", "#ffd166", "#5fa8ff")
        val DARKS = arrayOf("#46a464", "#c94848", "#d1a33a", "#3b7fd1")
        val NOTES = arrayOf("beep262", "beep330", "beep392", "beep523")
        val LABELS = arrayOf("1", "2", "3", "4")

        val START = arrayOf("Watch closely. Or don’t.", "Try to keep up.", "This is my song.")
        val RIGHT = arrayOf("Lucky.", "Fine. Longer then.", "Hmph.", "That was the easy part.", "Stop remembering things.")
        val WAITS = arrayOf("I’m not done!", "Wait your turn.", "Rude. I’m performing.")
        val WRONG = arrayOf("Wrong. Obviously.", "Goldfish memory. Classic.", "Ha! HA!", "I knew you would.")
    }

    private val look = ctx.look
    private val lookColor = look.color.toInt()

    override var score = 0
        private set
    override var over = false
        private set
    override val canRevive get() = true

    private val color = IntArray(4) { col(COLORS[it]) }
    private val px = FloatArray(4) { CX + DX[it] * REACH }
    private val py = FloatArray(4) { CY + DY[it] * REACH }
    private val lit = FloatArray(4)
    private val padSquash = FloatArray(4)
    private val padLooks = Array(4) { look.copy(color = col(COLORS[it]).toLong() and 0xFFFFFFFFL, dark = col(DARKS[it]).toLong() and 0xFFFFFFFFL) }

    private val seq = IntArray(MAX_SEQ)
    private var seqLen = 0
    private val mouth = Mouth()
    private val fx = Fx()
    private var mode = WAIT
    private var timer = 1.2f
    private var step = 0 // which note of the sequence is next, while showing or copying
    private var t = 0f
    private var squash = 0f
    private var wrongPad = -1
    private var wrongAt = 0f // when the wrong pad was hit, for the red flash

    // notes get shorter as the tune gets longer
    private fun noteLen() = clamp(0.55f - score * 0.035f, 0.24f, 0.55f)
    private fun gapLen() = noteLen() * 0.45f

    private fun light(i: Int, sec: Float) {
        lit[i] = sec
        ctx.sfx.play(NOTES[i])
    }

    private fun nextRound() {
        if (seqLen < MAX_SEQ) seq[seqLen++] = Random.nextInt(4)
        mode = WAIT
        timer = 0.9f
    }

    private fun press(i: Int) {
        if (over || i < 0) return
        if (mode != COPY) {
            mouth.maybe(WAITS, 0.6f, 1.2f)
            return
        }
        if (i != seq[step]) {
            over = true
            wrongPad = i
            lit[i] = 0f
            ctx.sfx.play("bad")
            ctx.haptics.thud()
            fx.sparks(px[i], py[i], col("#ff3a4a"), 12)
            wrongAt = t
            mouth.say(pick(WRONG), 3f)
            return
        }
        light(i, LIT)
        padSquash[i] = 0.15f
        ctx.haptics.tap()
        step++
        if (step >= seqLen) {
            score++
            squash = 0.2f
            ctx.sfx.play("pop")
            fx.float(CX, CY - ME_SIZE * 0.8f, "+1")
            fx.sparks(px[i], py[i], color[i], 10)
            mouth.maybe(RIGHT, 0.6f)
            nextRound()
            timer = 1.1f // let the last note ring before the next tune
        }
    }

    override fun down(x: Float, y: Float) {
        // whichever pad is closest, as long as the tap is near one
        var best = -1
        var bestD = PAD * 0.62f
        for (i in 0 until 4) {
            val d = dist(x, y, px[i], py[i])
            if (d < bestD) {
                best = i
                bestD = d
            }
        }
        press(best)
    }

    // a paid Continue: the button plays the same tune again, and you try again
    override fun revive() {
        over = false
        wrongPad = -1
        mode = WAIT
        step = 0
        timer = 1.6f
        reviveFx(fx, mouth, ctx, CX, CY, lookColor)
    }

    override fun update(dt: Float) {
        t += dt
        mouth.update(dt)
        fx.update(dt)
        squash = max(0f, squash - dt)
        for (i in 0 until 4) {
            lit[i] = max(0f, lit[i] - dt)
            padSquash[i] = max(0f, padSquash[i] - dt)
        }
        if (over) return

        if (mode == COPY) return
        timer -= dt
        if (timer > 0f) return
        if (mode == WAIT) {
            mode = SHOW
            step = 0
            timer = 0.15f
            if (score == 0) mouth.say(pick(START), 1.6f)
        } else if (step < seqLen) {
            light(seq[step], noteLen())
            squash = 0.12f
            step++
            timer = noteLen() + gapLen()
        } else {
            mode = COPY
            step = 0
        }
    }

    private var subStep = -1
    private var subLen = -1
    private var subText = ""

    override fun DrawScope.draw() {
        val c = drawContext.canvas.nativeCanvas
        // a dark stage with a glowing dance floor, lit by whatever is playing
        c.sprite(art.stage, 0f, 0f, W, H)
        for (i in 0 until 9) {
            val fi = i.toFloat()
            val bx = hash(fi * 3.3f) * W
            val by = 170f + hash(fi * 5.1f) * 520f + sin(t * 0.4f + fi) * 14f
            c.glow(bx, by, 26f + hash(fi + 2f) * 40f, color[i % 4], 0.12f + sin(t * 0.9f + fi * 2.1f) * 0.06f)
        }

        var playing = -1
        for (i in 0 until 4) if (lit[i] > 0f) {
            playing = i
            break
        }
        val heat = if (playing >= 0) min(1f, lit[playing] * 6f) else 0f
        // the floor's neon rim takes the colour of the note being played
        val ro = RING + 40f
        c.sprite(art.ring(VIOLET), CX - ro, CY - ro, alpha = 0.55f + sin(t * 2f) * 0.1f)
        if (playing >= 0) c.sprite(art.ring(color[playing]), CX - ro, CY - ro, alpha = heat)

        // while the button plays, a beam of light runs from it to the pad
        if (playing >= 0 && mode == SHOW) {
            beam.color = color[playing]
            beam.alpha = (heat * 0.25f * 255f).toInt()
            beam.strokeWidth = 16f
            c.drawLine(CX, CY, px[playing], py[playing], beam)
            beam.color = color[playing]
            beam.alpha = (heat * 0.7f * 255f).toInt()
            beam.strokeWidth = 3f
            c.drawLine(CX, CY, px[playing], py[playing], beam)
        }

        for (i in 0 until 4) {
            val wrong = i == wrongPad
            val k = if (lit[i] > 0f) min(1f, lit[i] * 6f) else 0f
            // each pad sits on its own coloured light, which flares when it plays
            c.sprite(art.base(color[i]), px[i] - PAD * 0.62f, py[i] + PAD * 0.05f, alpha = 0.35f + k * 0.65f)
            if (k > 0f) c.glow(px[i], py[i], PAD * 1.05f, color[i], k * 0.85f)
            if (wrong) c.glow(px[i], py[i], PAD * 1.15f, WRONG_RED, 0.85f + sin(t * 20f) * 0.15f)
            bobby(
                c, px[i] + (if (wrong) sin(t * 50f) * 3f else 0f), py[i], PAD, padLooks[i],
                mood = when {
                    wrong -> Mood.Worried
                    lit[i] > 0f -> Mood.Happy
                    mode == COPY -> Mood.Calm
                    else -> Mood.Closed
                },
                t = t + i * 1.3f,
                squash = if (lit[i] > 0f) -0.06f else padSquash[i],
                alpha = if (lit[i] > 0f || mode == COPY || over) 1f else 0.8f,
            )
            // which number it is
            val kx = px[i] + DX[i] * PAD * 0.58f
            val ky = py[i] + DY[i] * PAD * 0.62f
            c.drawCircle(kx, ky, 11f, keyFill)
            keyLine.color = rgba(color[i], 0.5f + k * 0.5f)
            c.drawCircle(kx, ky, 11f, keyLine)
            Txt.draw(c, LABELS[i], kx, ky + 1f, 13f, if (k > 0f) WHITE else rgba(color[i], 0.95f))
        }

        // the button in the middle leans toward whatever it is playing, under a spotlight
        val lean = if (mode == SHOW && playing >= 0) DX[playing] * 0.25f else 0f
        c.sprite(art.spot, CX - 110f, 0f, 220f, CY + 40f, alpha = 0.5f + heat * 0.3f, add = true)
        c.shadow(CX, CY + ME_SIZE * 0.5f, ME_SIZE * 0.6f, 0.45f)
        bobby(
            c, CX, CY, ME_SIZE, look,
            mood = when {
                over -> Mood.Smug
                mode == SHOW -> Mood.Happy
                mode == COPY -> Mood.Worried
                else -> Mood.Angry
            },
            t = t, squash = squash, angle = lean,
        )

        fx.draw(c)
        c.vignette(0.5f, VIG)
        if (over && t - wrongAt < 0.8f) c.vignette(0.9f, FLASH_RED, alpha = 1f - (t - wrongAt) / 0.8f)
        mouth.draw(c, CX, CY - ME_SIZE * 0.6f)

        val sub = when {
            over -> "Wrong pad."
            mode == COPY -> {
                if (step != subStep || seqLen != subLen) {
                    subStep = step
                    subLen = seqLen
                    subText = "Your turn: $step/$seqLen"
                }
                subText
            }
            else -> "Watch…"
        }
        c.drawScore(score, sub)
    }

    /* ---------- art ---------- */

    private val VIOLET = col("#8f7bff")
    private val WRONG_RED = col("#ff2a3a")
    private val FLASH_RED = col("#ff1a2e")
    private val VIG = col("#05020c")
    private val beam = stroke(WHITE, 16f).apply { xfermode = ADD }
    private val keyFill = fill(rgba(10, 6, 24, 0.55f))
    private val keyLine = stroke(WHITE, 1.5f)
    private val art = Art()

    init {
        nextRound()
    }

    private class Art {
        val stage by lazy {
            makeSprite(W, H, 1.5f) { c ->
                c.drawRect(0f, 0f, W, H, fill().shade(lin(0f, 0f, 0f, H, 0f to col("#1d1240"), 0.5f to col("#140c30"), 1f to col("#0a0618"))))
                // a soft violet wash from above
                c.drawRect(0f, 0f, W, H, fill().shade(rad(CX, 0f, 0f, 520f, 0f to rgba(150, 110, 255, 0.35f), 1f to rgba(150, 110, 255, 0f))))
                // the floor: a glossy disc with faint grooves
                c.drawCircle(
                    CX, CY, RING,
                    fill().shade(
                        android.graphics.RadialGradient(
                            CX - 60f, CY - 90f, RING + 60f,
                            intArrayOf(col("#3e2d78"), col("#241850"), col("#170f38")),
                            floatArrayOf(0f, 0.7f, 1f),
                            android.graphics.Shader.TileMode.CLAMP,
                        ),
                    ),
                )
                val groove = stroke(rgba(180, 160, 255, 0.08f), 1f)
                var r = 40f
                while (r < RING) {
                    c.drawCircle(CX, CY, r, groove)
                    r += 24f
                }
                // a sheen across the top of the floor
                c.drawOval(
                    RectF(CX - RING * 0.8f, CY - RING * 0.45f - RING * 0.5f, CX + RING * 0.8f, CY - RING * 0.45f + RING * 0.5f),
                    fill().shade(lin(0f, CY - RING, 0f, CY, 0f to rgba(255, 255, 255, 0.10f), 1f to rgba(255, 255, 255, 0f))),
                )
            }
        }

        private val rings = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()

        // The neon rim of the floor in one colour, glow baked in.
        fun ring(color: Int): Sprite = rings.getOrPut(color) {
            val size = (RING + 40f) * 2f
            makeSprite(size, size) { c ->
                val m = size / 2f
                val p = stroke(color, 4f)
                p.setShadowLayer(11f, 0f, 0f, color)
                c.drawCircle(m, m, RING, p)
                c.drawCircle(m, m, RING, p)
                c.drawCircle(m, m, RING, stroke(rgba(255, 255, 255, 0.7f), 1.5f))
            }
        }

        private val bases = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()

        // A flat ellipse of coloured light for a pad to stand in.
        fun base(color: Int): Sprite = bases.getOrPut(color) {
            val w = PAD * 1.24f
            val h = PAD * 0.6f
            makeSprite(w, h) { c ->
                c.scale(1f, h / w)
                c.drawRect(0f, 0f, w, w, fill().shade(rad(w / 2f, w / 2f, 0f, w / 2f, 0f to rgba(color, 0.85f), 0.45f to rgba(color, 0.35f), 1f to rgba(color, 0f))))
            }
        }

        // A soft cone of light from the top of the screen.
        val spot by lazy {
            makeSprite(110f, 240f) { c ->
                val path = Path()
                path.moveTo(42f, 0f)
                path.lineTo(68f, 0f)
                path.lineTo(108f, 240f)
                path.lineTo(2f, 240f)
                path.close()
                c.drawPath(
                    path,
                    fill().shade(lin(0f, 0f, 0f, 240f, 0f to rgba(200, 180, 255, 0f), 0.5f to rgba(200, 180, 255, 0.12f), 0.85f to rgba(230, 220, 255, 0.26f), 1f to rgba(230, 220, 255, 0f))),
                )
                // fade the sides
                c.drawRect(0f, 0f, 110f, 240f, fill().shade(lin(0f, 0f, 110f, 0f, 0f to rgba(0, 0, 0, 0f), 0.5f to rgba(0, 0, 0, 1f), 1f to rgba(0, 0, 0, 0f))).apply { xfermode = DST_IN })
            }
        }
    }
}
