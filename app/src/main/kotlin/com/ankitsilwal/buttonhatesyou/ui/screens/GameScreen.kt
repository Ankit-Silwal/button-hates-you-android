package com.ankitsilwal.buttonhatesyou.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.data.Earned
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.GameInfo
import com.ankitsilwal.buttonhatesyou.economy.Rules
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.games.Arcade
import com.ankitsilwal.buttonhatesyou.games.Game
import com.ankitsilwal.buttonhatesyou.games.GameHost
import com.ankitsilwal.buttonhatesyou.games.GameSpec
import com.ankitsilwal.buttonhatesyou.ui.art.GameIconTile
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.fmt
import com.ankitsilwal.buttonhatesyou.ui.dialogs.PopCard
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.floor

/** Holds the running game; read when the pause menu opens. */
private class LiveRun { var game: Game? = null }

/** The offer after a crash: the nth Continue and its price. */
private data class Offer(val n: Int, val cost: Int)

/** What a finished run paid, for the results card. */
private data class RunResult(val score: Int, val coins: Int, val best: Int, val newBest: Boolean, val earned: Earned?, val continues: Int)

/**
 * Wraps a game so a crash can wait for the player's answer to a paid
 * Continue: while the offer is up the host sees the run as still going (and
 * is paused); a "no" lets the run end, a "yes" revives it.
 */
private class ContinueGame(val inner: Game, private val ask: (ContinueGame) -> Boolean) : Game by inner {
    var decided = false
    private var overFor = 0f
    var waiting = false

    override val over: Boolean get() = inner.over && decided
    override val canRevive: Boolean get() = false

    override fun update(dt: Float) {
        inner.update(dt)
        if (!inner.over) { overFor = 0f; return }
        if (decided || waiting) return
        overFor += dt
        if (overFor < 0.6f) return
        // offer a Continue (the UI pauses the host) or let the run end
        if (inner.canRevive && ask(this)) waiting = true else decided = true
    }

    fun accept() {
        waiting = false
        overFor = 0f
        try { inner.revive() } catch (_: Throwable) { decided = true }
    }

    fun decline() {
        waiting = false
        decided = true
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Hides the status and navigation bars while [on] (swipe to peek). */
@Composable
private fun Immersive(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val window = view.context.activity()?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (on) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private enum class Mode { Title, Play, Over }

/**
 * One adventure game (src/adventure/AdventureGame.jsx): a title card, the
 * game full screen, Continue offers, then the results card. Energy for the
 * first run was spent before opening; "Again" spends it again via [tryPlay]
 * (false: he refused, the caller shows why).
 */
@Composable
fun GameScreen(
    gameId: String,
    store: ProfileStore,
    wallet: Wallet,
    look: Look,
    tryPlay: () -> Boolean,
    onBack: () -> Unit,
    onAchievements: () -> Unit,
) {
    val info = Catalog.GAME[gameId]
    val spec = Arcade.byId(gameId)
    if (info == null || spec == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var mode by remember { mutableStateOf(Mode.Title) }
    var run by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(MenuAsk.None) }
    var offer by remember { mutableStateOf<Offer?>(null) }
    var result by remember { mutableStateOf<RunResult?>(null) }
    var continues by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<ContinueGame?>(null) }
    val live = remember { LiveRun() } // this run's game, for the pause menu's score (set while composing, so not state)
    val best = Achievements.statsOf(wallet).best[gameId] ?: 0

    Immersive(mode == Mode.Play)

    fun start() {
        continues = 0
        offer = null
        paused = false
        ask = MenuAsk.None
        run++
        mode = Mode.Play
    }

    fun resume() {
        paused = false
        ask = MenuAsk.None
    }

    // a restart is a new run: it spends energy like "Play again" (if he refuses, the gate says why)
    fun restart() {
        if (tryPlay()) start() else ask = MenuAsk.None
    }

    // back opens the pause menu; in the menu it backs out of a question, then resumes
    BackHandler(mode == Mode.Play) {
        when {
            offer != null -> Unit
            !paused -> paused = true
            ask != MenuAsk.None -> ask = MenuAsk.None
            else -> resume()
        }
    }
    BackHandler(mode != Mode.Play, onBack)

    Box(Modifier.fillMaxSize().background(Color(0xFF140A1E))) {
        if (mode == Mode.Play) {
            val wrapped = remember(run) {
                GameSpec(spec.id) { ctx ->
                    ContinueGame(spec.create(ctx).also { live.game = it }) { g ->
                        val n = continues + 1
                        val cost = Rules.continueCost(n)
                        if (cost == null) false else {
                            current = g
                            offer = Offer(n, cost)
                            true
                        }
                    }
                }
            }
            val reduce = LocalReduceMotion.current
            val blur by animateDpAsState(if (paused && !reduce) 14.dp else 0.dp, tween(220), label = "pause-blur")
            key(run) {
                GameHost(
                    spec = wrapped,
                    look = look,
                    modifier = Modifier.blur(blur),
                    paused = paused || offer != null,
                    onOver = { score, coins, ms ->
                        val before = Achievements.statsOf(store.wallet.value).best[gameId] ?: 0
                        // Please Don't is a story game: finishing it (65 presses) claims it once
                        val earned = if (spec.id == Arcade.pleaseDont.id) {
                            if (score >= 65) store.claimStory(gameId) else null
                        } else {
                            store.recordRun(gameId, score, coins, ms, continues)
                        }
                        val shown = if (info.payout?.coins == true) coins else score
                        result = RunResult(shown, coins, maxOf(before, shown), shown > before && shown > 0, earned, continues)
                        mode = Mode.Over
                    },
                )
            }
            if (!paused && offer == null) {
                PauseChip({ paused = true }, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(10.dp))
            }
            if (paused && offer == null) {
                PauseMenu(
                    info = info,
                    score = live.game?.let { if (info.payout?.coins == true) it.coins else it.score },
                    best = best,
                    ask = ask,
                    onAsk = { ask = it },
                    onResume = ::resume,
                    onRestart = ::restart,
                    onQuit = onBack,
                )
            }
            offer?.let { o ->
                ContinueCard(o, wallet.diamonds,
                    onYes = {
                        if (wallet.diamonds >= o.cost && store.continueRun(gameId, o.n)) {
                            continues = o.n
                            offer = null
                            current?.accept()
                        }
                    },
                    onNo = {
                        offer = null
                        current?.decline()
                    },
                )
            }
        }

        if (mode == Mode.Title) {
            Backdrop(info) {
                GameIconTile(info.id, Modifier.size(84.dp), corner = 22.dp)
                Text(info.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(info.blurb, textAlign = TextAlign.Center, color = Palette.InkSoft)
                Text(info.howTo ?: "", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.background(Palette.Wall, RoundedCornerShape(14.dp)).padding(10.dp))
                Text((info.rule ?: "") + if (best > 0) " · Best: ${fmt(best)}" else "", textAlign = TextAlign.Center, fontSize = 13.sp, color = Color(0xFF8A5A10))
                Pill("Play", ::start, icon = "▶", modifier = Modifier.fillMaxWidth(0.7f), height = 58.dp)
                Pill("Back", onBack, kind = PillKind.Ghost)
            }
        }

        if (mode == Mode.Over) {
            val r = result
            if (r != null) {
                val p = info.payout
                val good = r.newBest || (p != null && !p.coins && r.score >= p.per) || (p?.coins == true && r.coins >= 10)
                Backdrop(info) {
                    Box(Modifier.size(110.dp, 120.dp), contentAlignment = Alignment.Center) {
                        Avatar(look, Face(mood = if (good) Mood.Sad else Mood.Smug), size = 74.dp)
                    }
                    Text(if (good) "NICE RUN" else "GAME OVER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Palette.InkSoft)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmt(r.score), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" " + (info.unit ?: ""), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text((if (r.newBest) "New best!" else "Best: ${fmt(r.best)}") + if (r.continues > 0) " · Continued ×${r.continues}" else "", fontWeight = FontWeight.Bold, color = if (r.newBest) Palette.BtnDark else Palette.Ink)
                    Text("“${if (good) info.quips?.goal else info.quips?.fail}”", fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = Palette.InkSoft)
                    val coins = r.earned?.coins ?: 0
                    Text("+${fmt(coins)} 🪙" + ((r.earned?.diamonds ?: 0).takeIf { it > 0 }?.let { " +$it 💎" } ?: ""), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (coins > 0) Color(0xFF8A5A10) else Palette.InkSoft, modifier = Modifier.background(Color(0xFFFFEFC4), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 6.dp))
                    val ready = r.earned?.ready.orEmpty()
                    if (ready.isNotEmpty()) {
                        val names = ready.map { it.name }
                        Pill("Achievement ready! " + names.take(2).joinToString(", ") + if (names.size > 2) " +${names.size - 2}" else "", onAchievements, icon = "🏆", kind = PillKind.Alt, height = 46.dp)
                    }
                    Text(nextTier(info, r), fontSize = 13.sp, textAlign = TextAlign.Center, color = Palette.InkSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Games", onBack, kind = PillKind.Ghost)
                        Pill("Play again", { if (tryPlay()) start() }, icon = "↻")
                    }
                }
            }
        }
    }
}

/** The line under a run's payout: how far the next, better-paying tier was. */
private fun nextTier(g: GameInfo, r: RunResult): String {
    val p = g.payout ?: return ""
    if (p.coins) return "${r.coins} coin${if (r.coins == 1) "" else "s"} grabbed. Every one is yours."
    if (r.score < p.per) return "The first ${p.per} ${g.unit} pay ${p.base} 🪙, the next ${p.per} pay ${p.base * 2}."
    val tier = floor(r.score.toDouble() / p.per).toInt() + 1
    val left = tier * p.per - r.score
    return "$left more ${g.unit} and the next ${p.per} pay ${p.base * (tier + 1)} 🪙."
}

/** A soft card on the game's colour. */
@Composable
private fun Backdrop(info: GameInfo, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(info.color), Palette.Bg))).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(28.dp))
                .background(Palette.Cream, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private const val OFFER_SEC = 5

/** Over the frozen game after a crash: pay diamonds to carry on from here. */
@Composable
private fun ContinueCard(offer: Offer, diamonds: Int, onYes: () -> Unit, onNo: () -> Unit) {
    val ring = remember(offer) { Animatable(1f) }
    LaunchedEffect(offer) {
        ring.animateTo(0f, tween(OFFER_SEC * 1000, easing = LinearEasing))
        delay(100)
        onNo()
    }
    val short = diamonds < offer.cost
    PopCard(onNo, dismissable = false) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) { ringArc(ring.value) }
            Text("${kotlin.math.ceil(ring.value * OFFER_SEC).toInt()}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("Continue?", style = MaterialTheme.typography.headlineSmall)
        Text("Pick up right where you crashed, score and all.", color = Palette.InkSoft, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Continue ${offer.n} of ${Rules.MAX_CONTINUES}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("You have $diamonds 💎", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (short) Palette.Alert else Palette.Ink)
        }
        Pill("Continue for ${offer.cost} 💎", onYes, enabled = !short, modifier = Modifier.fillMaxWidth(), height = 56.dp)
        if (short) Text("Not enough diamonds. Earn them from Achievements.", fontSize = 12.sp, color = Palette.InkSoft, textAlign = TextAlign.Center)
        Pill("No thanks", onNo, kind = PillKind.Ghost)
    }
}

private fun DrawScope.ringArc(left: Float) {
    val sw = 7.dp.toPx()
    val inset = sw / 2
    drawArc(Color(0x22000000), 0f, 360f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw))
    drawArc(Palette.Btn, -90f, 360f * left, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
}
