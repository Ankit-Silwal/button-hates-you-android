package com.ankitsilwal.buttonhatesyou.games

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Short synthesized sounds, no audio files. Every sound is rendered to PCM
// once at startup; a single low-latency streaming AudioTrack on its own
// thread mixes whatever is playing (up to VOICES at once), so play() never
// blocks the frame and sounds can overlap. The track pauses when idle.
//
// Names: click, hit, pop, win, bad, sob, tick, jump, coin, whoosh, and the
// extras boom, thud, nope, crack (unknown names are ignored).
class SynthSfx(context: Context) : Sfx {
    companion object {
        private const val RATE = 22050
        private const val CHUNK = 256 // frames per mix step (~12 ms)
        private const val VOICES = 10
        private const val IDLE_CHUNKS = 30 // ~350 ms of silence before pausing

        // Global mute: nothing plays while true.
        @Volatile
        @JvmStatic
        var muted: Boolean = false

        // Master volume 0..1.
        @Volatile
        @JvmStatic
        var volume: Float = 0.8f

        private var shared: SynthSfx? = null

        // One mixer for the whole app (rendering the sounds costs a few ms once).
        fun shared(context: Context): SynthSfx = synchronized(this) {
            shared ?: SynthSfx(context.applicationContext).also { shared = it }
        }
    }

    @Suppress("unused")
    private val app = context.applicationContext

    @Volatile
    private var clips: Map<String, ShortArray> = emptyMap()
    private val lock = Object()
    private val voiceClip = arrayOfNulls<ShortArray>(VOICES)
    private val voicePos = IntArray(VOICES)
    private val voiceAge = LongArray(VOICES)
    private var serial = 0L

    @Volatile
    private var alive = true
    private val thread = Thread({ run() }, "SynthSfx").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
        start()
    }

    override fun play(name: String) {
        if (muted || !alive) return
        val clip = clips[name] ?: return
        synchronized(lock) {
            var slot = -1
            var oldest = Long.MAX_VALUE
            for (i in 0 until VOICES) {
                if (voiceClip[i] == null) {
                    slot = i
                    break
                }
                if (voiceAge[i] < oldest) {
                    oldest = voiceAge[i]
                    slot = i
                }
            }
            voiceClip[slot] = clip
            voicePos[slot] = 0
            voiceAge[slot] = ++serial
            lock.notifyAll()
        }
    }

    // Stops the mixer thread for good (the shared instance never needs this).
    fun release() {
        alive = false
        synchronized(lock) { lock.notifyAll() }
    }

    private fun run() {
        try {
            clips = render()
        } catch (e: Throwable) {
            Log.w("SynthSfx", "could not render sounds", e)
            return
        }
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuf, CHUNK * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val acc = IntArray(CHUNK)
            val out = ShortArray(CHUNK)
            var playing = false
            var idle = 0
            while (alive) {
                var any = false
                synchronized(lock) {
                    while (alive && !playing && voiceClip.all { it == null }) lock.wait()
                    acc.fill(0)
                    for (v in 0 until VOICES) {
                        val clip = voiceClip[v] ?: continue
                        any = true
                        val p = voicePos[v]
                        val n = min(CHUNK, clip.size - p)
                        for (i in 0 until n) acc[i] += clip[p + i].toInt()
                        voicePos[v] = p + n
                        if (voicePos[v] >= clip.size) voiceClip[v] = null
                    }
                }
                if (!alive) break
                val vol = if (muted) 0f else volume
                for (i in 0 until CHUNK) out[i] = (acc[i] * vol).toInt().coerceIn(-32767, 32767).toShort()
                if (any) {
                    idle = 0
                    if (!playing) {
                        track.play()
                        playing = true
                    }
                } else if (++idle > IDLE_CHUNKS) {
                    track.pause()
                    track.flush()
                    playing = false
                    idle = 0
                    continue
                }
                track.write(out, 0, CHUNK)
            }
        } catch (e: Throwable) {
            Log.w("SynthSfx", "audio stopped", e)
            alive = false
        } finally {
            try {
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }

    /* ---------- the sounds ---------- */

    private class Buf(sec: Float) {
        val data = FloatArray((sec * RATE).toInt().coerceAtLeast(1))
        val n get() = data.size
    }

    private fun render(): Map<String, ShortArray> {
        val m = HashMap<String, ShortArray>()
        // Copy Me's four pad notes (web sound.beep(f): a 0.25 s sine)
        for (f in intArrayOf(262, 330, 392, 523)) m["beep$f"] = pcm(tone(0.25f, f.toFloat(), f.toFloat(), 0.55f, decay = 7f))
        m["click"] = pcm(click())
        m["tick"] = pcm(tone(0.035f, 1900f, 1900f, 0.35f, decay = 90f, wave = 0))
        m["pop"] = pcm(pop())
        m["hit"] = pcm(hit())
        m["jump"] = pcm(tone(0.13f, 320f, 760f, 0.4f, decay = 18f, wave = 2))
        m["coin"] = pcm(coin())
        m["win"] = pcm(win())
        m["bad"] = pcm(bad())
        m["sob"] = pcm(sob())
        m["whoosh"] = pcm(whoosh())
        m["boom"] = pcm(boom())
        m["thud"] = pcm(thud())
        m["nope"] = pcm(nope())
        m["crack"] = pcm(crack())
        return m
    }

    private fun pcm(b: Buf): ShortArray {
        val d = b.data
        // tiny fade in and out so nothing clicks
        val fade = min(40, d.size / 4)
        for (i in 0 until fade) {
            val k = i / fade.toFloat()
            d[i] *= k
            d[d.size - 1 - i] *= k
        }
        return ShortArray(d.size) { (d[it].coerceIn(-1f, 1f) * 32767f * 0.5f).toInt().toShort() }
    }

    private fun osc(wave: Int, phase: Double): Float {
        val p = phase - kotlin.math.floor(phase)
        return when (wave) {
            1 -> if (p < 0.5) 0.7f else -0.7f // square
            2 -> (4.0 * kotlin.math.abs(p - 0.5) - 1.0).toFloat() // triangle
            3 -> (2.0 * p - 1.0).toFloat() * 0.7f // saw
            else -> sin(2.0 * PI * p).toFloat()
        }
    }

    // A sweep from f0 to f1 with an exponential decay.
    private fun tone(sec: Float, f0: Float, f1: Float, amp: Float, decay: Float = 8f, wave: Int = 0, into: Buf? = null, at: Float = 0f): Buf {
        val b = into ?: Buf(sec)
        val start = (at * RATE).toInt()
        val len = (sec * RATE).toInt()
        var ph = 0.0
        for (i in 0 until len) {
            val j = start + i
            if (j >= b.n) break
            val k = i / len.toFloat()
            val f = f0 + (f1 - f0) * k
            ph += f / RATE
            val env = exp(-decay * i / RATE.toFloat()) * min(1f, i / 30f)
            b.data[j] += osc(wave, ph) * amp * env
        }
        return b
    }

    private fun noise(b: Buf, sec: Float, amp: Float, decay: Float, lowpass: Float, at: Float = 0f) {
        val start = (at * RATE).toInt()
        val len = (sec * RATE).toInt()
        var y = 0f
        for (i in 0 until len) {
            val j = start + i
            if (j >= b.n) break
            val x = Random.nextFloat() * 2f - 1f
            y += (x - y) * lowpass
            b.data[j] += y * amp * exp(-decay * i / RATE.toFloat())
        }
    }

    private fun click(): Buf = tone(0.03f, 1300f, 900f, 0.4f, decay = 120f, wave = 1)

    private fun pop(): Buf = tone(0.09f, 950f, 280f, 0.7f, decay = 30f)

    private fun hit(): Buf {
        val b = Buf(0.18f)
        noise(b, 0.12f, 0.8f, 40f, 0.5f)
        tone(0.18f, 180f, 70f, 0.8f, decay = 18f, into = b)
        return b
    }

    private fun coin(): Buf {
        val b = Buf(0.3f)
        tone(0.07f, 988f, 988f, 0.4f, decay = 10f, wave = 1, into = b)
        tone(0.23f, 1319f, 1319f, 0.4f, decay = 12f, wave = 1, into = b, at = 0.07f)
        return b
    }

    private fun win(): Buf {
        val b = Buf(0.5f)
        val notes = floatArrayOf(523f, 659f, 784f, 1047f)
        for ((i, f) in notes.withIndex()) tone(if (i == 3) 0.26f else 0.09f, f, f, 0.38f, decay = 7f, wave = 2, into = b, at = i * 0.08f)
        return b
    }

    private fun bad(): Buf {
        val b = Buf(0.42f)
        tone(0.2f, 330f, 260f, 0.4f, decay = 4f, wave = 3, into = b)
        tone(0.22f, 250f, 120f, 0.45f, decay = 5f, wave = 3, into = b, at = 0.2f)
        return b
    }

    private fun sob(): Buf {
        val b = Buf(0.6f)
        var ph = 0.0
        for (i in 0 until b.n) {
            val k = i / b.n.toFloat()
            val f = 520f - 220f * k + 18f * sin(2.0 * PI * 7.0 * i / RATE).toFloat()
            ph += f / RATE
            val env = min(1f, i / 300f) * (1f - k) * (0.6f + 0.4f * sin(2.0 * PI * 3.0 * i / RATE).toFloat())
            b.data[i] += osc(2, ph) * 0.5f * env
        }
        return b
    }

    private fun whoosh(): Buf {
        val b = Buf(0.28f)
        var y = 0f
        for (i in 0 until b.n) {
            val k = i / b.n.toFloat()
            val x = Random.nextFloat() * 2f - 1f
            y += (x - y) * (0.05f + 0.25f * k)
            b.data[i] = y * 1.4f * sin(PI * k).toFloat()
        }
        return b
    }

    private fun boom(): Buf {
        val b = Buf(0.5f)
        noise(b, 0.45f, 1.1f, 9f, 0.12f)
        tone(0.4f, 110f, 40f, 0.9f, decay = 8f, into = b)
        return b
    }

    private fun thud(): Buf {
        val b = Buf(0.14f)
        tone(0.14f, 150f, 60f, 0.8f, decay = 25f, into = b)
        noise(b, 0.04f, 0.3f, 60f, 0.3f)
        return b
    }

    private fun nope(): Buf {
        val b = Buf(0.26f)
        tone(0.1f, 220f, 210f, 0.35f, decay = 6f, wave = 1, into = b)
        tone(0.12f, 180f, 170f, 0.35f, decay = 6f, wave = 1, into = b, at = 0.13f)
        return b
    }

    private fun crack(): Buf {
        val b = Buf(0.12f)
        noise(b, 0.1f, 1f, 45f, 0.9f)
        tone(0.06f, 2400f, 1200f, 0.2f, decay = 60f, into = b)
        return b
    }
}
