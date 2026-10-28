package com.ankitsilwal.buttonhatesyou.games

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

// Vibration for the games: a light tick and a heavy hit. Uses the predefined
// click effects where the phone has them, else a short one-shot, else the
// view's haptic feedback. Nothing happens while `muted`.
class DeviceHaptics(private val view: View) : Haptics {
    companion object {
        @Volatile
        @JvmStatic
        var muted: Boolean = false
    }

    private val vibrator: Vibrator? = try {
        vibratorOf(view.context)?.takeIf { it.hasVibrator() }
    } catch (_: Throwable) {
        null
    }

    private val tick: VibrationEffect? = effect(light = true)
    private val heavy: VibrationEffect? = effect(light = false)

    private fun effect(light: Boolean): VibrationEffect? = try {
        when {
            vibrator == null -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                VibrationEffect.createPredefined(if (light) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_HEAVY_CLICK)
            else -> VibrationEffect.createOneShot(if (light) 12L else 40L, if (vibrator.hasAmplitudeControl()) (if (light) 90 else 255) else VibrationEffect.DEFAULT_AMPLITUDE)
        }
    } catch (_: Throwable) {
        null
    }

    override fun tap() {
        if (muted) return
        if (!vibrate(tick)) feedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    override fun thud() {
        if (muted) return
        if (!vibrate(heavy)) feedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun vibrate(e: VibrationEffect?): Boolean {
        val v = vibrator ?: return false
        if (e == null) return false
        return try {
            v.vibrate(e)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun feedback(kind: Int) {
        try {
            view.performHapticFeedback(kind)
        } catch (_: Throwable) {
        }
    }
}

private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
