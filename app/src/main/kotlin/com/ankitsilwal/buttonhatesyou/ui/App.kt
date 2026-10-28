package com.ankitsilwal.buttonhatesyou.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.ankitsilwal.buttonhatesyou.data.NeedsTicker
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.games.Arcade
import com.ankitsilwal.buttonhatesyou.games.DeviceHaptics
import com.ankitsilwal.buttonhatesyou.games.SynthSfx
import com.ankitsilwal.buttonhatesyou.ui.common.AppSettings
import com.ankitsilwal.buttonhatesyou.ui.common.Feedback
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.rememberNow
import com.ankitsilwal.buttonhatesyou.ui.dialogs.ChestDialog
import com.ankitsilwal.buttonhatesyou.ui.dialogs.GateDialog
import com.ankitsilwal.buttonhatesyou.ui.dialogs.SettingsDialog
import com.ankitsilwal.buttonhatesyou.ui.home.HomeScreen
import com.ankitsilwal.buttonhatesyou.ui.home.HomeSpeakers
import com.ankitsilwal.buttonhatesyou.ui.home.Page
import com.ankitsilwal.buttonhatesyou.ui.screens.AchievementsScreen
import com.ankitsilwal.buttonhatesyou.ui.screens.GameScreen
import com.ankitsilwal.buttonhatesyou.ui.theme.ButtonTheme
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import java.io.File
import kotlin.system.exitProcess

/**
 * The whole app. Navigation is a small state machine: "home" (the room
 * pager), "achievements", or "game:<id>"; dialogs (chest, settings, the
 * energy gate) float over whichever is showing. Back returns home.
 */
@Composable
fun App() {
    ButtonTheme {
        val context = LocalContext.current
        val view = LocalView.current
        val store = remember { ProfileStore.get(context) }
        val settings = remember { AppSettings(context) }
        val feedback = remember(view) {
            Feedback(
                try { DeviceHaptics(view) } catch (_: Throwable) { null },
                try { SynthSfx.shared(context) } catch (_: Throwable) { null },
            )
        }
        val wallet by store.wallet.collectAsState()
        val ticker = remember(store) { NeedsTicker(store) }
        val needs by ticker.collectAsState(store.currentNeeds())
        val face = Needs.faceOf(needs, needs.at)
        val now = rememberNow(30_000)

        var screen by rememberSaveable { mutableStateOf("home") }
        val pager = rememberPagerState(initialPage = Page.Living.ordinal) { Page.entries.size }
        val speakers = remember { HomeSpeakers() }
        var chestOpen by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        var gate by remember { mutableStateOf<String?>(null) }
        var jump by remember { mutableStateOf<Page?>(null) }

        val achievementsReady = remember(wallet) { Achievements.readyCount(wallet) }
        val chestReady = remember(wallet, now) { store.chest().ready }

        // spends a game's energy, or says why not (the energy gate)
        fun tryPlay(): Boolean {
            val why = store.whyNot("adventure")
            if (why != null) { gate = why; return false }
            if (!store.play("adventure")) { gate = store.whyNot("adventure") ?: "tired"; return false }
            return true
        }

        fun goHome(page: Page? = null) {
            screen = "home"
            jump = page
        }

        LaunchedEffect(jump, screen) {
            val p = jump ?: return@LaunchedEffect
            if (screen == "home") {
                pager.animateScrollToPage(p.ordinal)
                jump = null
            }
        }

        CompositionLocalProvider(LocalFeedback provides feedback) {
            Box(Modifier.fillMaxSize().background(Palette.Wall)) {
                AnimatedContent(
                    screen,
                    transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
                    label = "screen",
                ) { s ->
                    when {
                        s == "achievements" -> AchievementsScreen(store, wallet, Catalog.lookOf(wallet.equipped), onBack = { goHome() }, onChest = { chestOpen = true })
                        s.startsWith("game:") -> GameScreen(
                            gameId = s.removePrefix("game:"),
                            store = store,
                            wallet = wallet,
                            look = Catalog.lookOf(wallet.equipped),
                            tryPlay = ::tryPlay,
                            onBack = { goHome(Page.Games) },
                            onAchievements = { screen = "achievements" },
                        )
                        else -> HomeScreen(
                            store = store,
                            wallet = wallet,
                            needs = needs,
                            face = face,
                            pager = pager,
                            speakers = speakers,
                            achievementsReady = achievementsReady,
                            chestReady = chestReady,
                            onPlay = { id ->
                                if (Arcade.byId(id) != null && tryPlay()) screen = "game:$id"
                            },
                            onAchievements = { screen = "achievements" },
                            onChest = { chestOpen = true },
                            onSettings = { settingsOpen = true },
                        )
                    }
                }

                gate?.let { why ->
                    GateDialog(
                        why = why,
                        look = Catalog.lookOf(wallet.equipped),
                        store = store,
                        onKitchen = { gate = null; goHome(Page.Kitchen) },
                        onBedroom = { gate = null; goHome(Page.Bedroom) },
                        onClose = { gate = null },
                    )
                }
                if (chestOpen) ChestDialog(store, wallet) { chestOpen = false }
                if (settingsOpen) {
                    SettingsDialog(settings, onReset = { resetProgress(context) }, onClose = { settingsOpen = false })
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Wipes the saved profile and restarts the app. (ProfileStore has no reset
 * yet and keeps its wallet in memory, so the process starts over.)
 */
private fun resetProgress(context: Context) {
    val dir = context.applicationContext.filesDir
    listOf("profile.json", "profile.json.tmp", "profile.json.bad").forEach { File(dir, it).delete() }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    val activity = context.findActivity()
    if (launch != null) context.startActivity(launch)
    activity?.finishAffinity()
    exitProcess(0)
}
