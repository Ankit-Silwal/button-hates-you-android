package com.ankitsilwal.buttonhatesyou.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ankitsilwal.buttonhatesyou.games.DeviceHaptics
import com.ankitsilwal.buttonhatesyou.games.SynthSfx
import kotlinx.coroutines.delay
import java.util.Calendar

/** Taps and sounds for the UI: a light haptic tick on every tap, a few sounds. */
class Feedback(private val haptics: DeviceHaptics?, private val sfx: SynthSfx?) {
    fun tick() = try { haptics?.tap() } catch (_: Throwable) {}
    fun thud() = try { haptics?.thud() } catch (_: Throwable) {}
    fun sound(name: String) = try { sfx?.play(name) } catch (_: Throwable) {}
}

val LocalFeedback = staticCompositionLocalOf { Feedback(null, null) }

/** Sound and vibration switches, kept in SharedPreferences and pushed to the game kit's mute flags. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var sound by mutableStateOf(prefs.getBoolean(SOUND, true))
        private set
    var vibration by mutableStateOf(prefs.getBoolean(VIBRATION, true))
        private set

    init {
        apply()
    }

    fun changeSound(on: Boolean) {
        sound = on
        prefs.edit().putBoolean(SOUND, on).apply()
        apply()
    }

    fun changeVibration(on: Boolean) {
        vibration = on
        prefs.edit().putBoolean(VIBRATION, on).apply()
        apply()
    }

    private fun apply() {
        SynthSfx.muted = !sound
        DeviceHaptics.muted = !vibration
    }

    companion object {
        private const val SOUND = "sound"
        private const val VIBRATION = "vibration"
    }
}

/* ------------------------------- the clock ------------------------------- */

enum class Sky { Day, Dusk, Dawn, Night }

fun skyOf(h: Float): Sky = when {
    h >= 7 && h < 17 -> Sky.Day
    h >= 17 && h < 20 -> Sky.Dusk
    h >= 5 && h < 7 -> Sky.Dawn
    else -> Sky.Night
}

/** The wall-clock time, as hours (0..24) and minutes, refreshed every half minute. */
data class Clock(val hours: Float, val minute: Int) {
    val sky: Sky get() = skyOf(hours)
}

fun clockNow(): Clock {
    val c = Calendar.getInstance()
    val h = c.get(Calendar.HOUR_OF_DAY)
    val m = c.get(Calendar.MINUTE)
    return Clock(h + m / 60f, m)
}

@Composable
fun rememberClock(): Clock {
    val c by produceState(clockNow()) {
        while (true) {
            delay(30_000)
            value = clockNow()
        }
    }
    return c
}

/** System time, ticking every [every] ms. */
@Composable
fun rememberNow(every: Long = 30_000): Long {
    val t by produceState(System.currentTimeMillis(), every) {
        while (true) {
            delay(every)
            value = System.currentTimeMillis()
        }
    }
    return t
}
