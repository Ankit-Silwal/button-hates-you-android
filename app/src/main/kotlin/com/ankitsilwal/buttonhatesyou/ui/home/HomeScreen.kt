package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.Lines
import com.ankitsilwal.buttonhatesyou.ui.common.PanelSheet
import com.ankitsilwal.buttonhatesyou.ui.common.rememberClock
import com.ankitsilwal.buttonhatesyou.ui.sheets.BuyFoodSheet
import com.ankitsilwal.buttonhatesyou.ui.sheets.DecorSheet
import com.ankitsilwal.buttonhatesyou.ui.sheets.WakeSheet
import com.ankitsilwal.buttonhatesyou.ui.sheets.WardrobeSheet
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch

/** The home screen's speakers, kept across trips to a game. */
class HomeSpeakers {
    val living = Speaker()
    val kitchen = Speaker()
    val bedroom = Speaker()
    val bath = Speaker()
    var greeted = false
}

/**
 * Home: a pager of five rooms (Games | Living Room | Kitchen | Bedroom |
 * Bathroom) between a top bar and a dock. Sheets open over the bottom of a
 * room; the room shrinks to what is above them so the preview stays visible.
 */
@Composable
fun HomeScreen(
    store: ProfileStore,
    wallet: Wallet,
    needs: NeedsState,
    face: Face,
    pager: PagerState,
    speakers: HomeSpeakers,
    achievementsReady: Int,
    chestReady: Boolean,
    onPlay: (String) -> Unit,
    onAchievements: () -> Unit,
    onChest: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val clock = rememberClock()
    var panel by remember { mutableStateOf<Panel?>(null) }
    var lastPanel by remember { mutableStateOf<Panel?>(null) }
    var lookPreview by remember { mutableStateOf<Map<String, String>?>(null) }
    var decorView by remember { mutableStateOf<DecorView?>(null) }
    var quip by remember { mutableStateOf<Quip?>(null) }
    val (flash, setFlash) = rememberFlash()

    LaunchedEffect(Unit) {
        if (!speakers.greeted) {
            speakers.greeted = true
            speakers.living.say(Lines.greeting(face, wallet.coins))
        }
    }

    fun go(p: Page) {
        scope.launch { if (reduce) pager.scrollToPage(p.ordinal) else pager.animateScrollToPage(p.ordinal, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
    }
    fun close() {
        panel = null
        lookPreview = null
        decorView = null
    }
    fun open(p: Panel) {
        lastPanel = p
        panel = p
    }

    BackHandler(panel != null) { close() }
    BackHandler(panel == null && pager.currentPage != Page.Living.ordinal) { go(Page.Living) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = statusTop + TOP_BAR_H
    val bottomInset = if (panel == null) navBottom + DOCK_H + 16.dp else 0.dp
    val look = Catalog.lookOf(lookPreview ?: wallet.equipped)

    val env = RoomEnv(
        store = store, wallet = wallet, needs = needs, face = face, look = look, clock = clock,
        topInset = topInset, bottomInset = bottomInset, panel = panel, decorView = decorView, quip = quip,
        go = ::go, open = ::open, play = onPlay,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = panel == null,
                    key = { it },
                ) { i ->
                    val active = pager.currentPage == i && !pager.isScrollInProgress || pager.settledPage == i
                    when (Page.entries[i]) {
                        Page.Games -> GamesRoom(env)
                        Page.Living -> LivingRoom(env, active, speakers.living, flash, setFlash)
                        Page.Kitchen -> KitchenRoom(env, active, speakers.kitchen)
                        Page.Bedroom -> BedroomRoom(env, active, speakers.bedroom)
                        Page.Bath -> BathRoom(env, active, speakers.bath)
                    }
                }
                TopBar(
                    wallet = wallet,
                    needs = needs,
                    achievementsReady = achievementsReady,
                    chestReady = chestReady,
                    onNeeds = { close(); go(fixFor(needs)) },
                    onAchievements = onAchievements,
                    onChest = onChest,
                    onSettings = onSettings,
                    modifier = Modifier.statusBarsPadding(),
                )
                androidx.compose.animation.AnimatedVisibility(
                    panel == null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
                ) {
                    Dock(pager.currentPage, needs, ::go, Modifier.navigationBarsPadding())
                }
            }
            AnimatedVisibility(
                panel != null,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(220)),
            ) {
                val p = panel ?: lastPanel
                val frac = when (p) {
                    Panel.Wardrobe -> 0.56f
                    is Panel.Decor -> 0.52f
                    Panel.BuyFood -> 0.52f
                    Panel.Wake -> 0.46f
                    null -> 0.5f
                }
                val title = when (p) {
                    Panel.Wardrobe -> "Wardrobe"
                    is Panel.Decor -> "Decorate · " + (com.ankitsilwal.buttonhatesyou.economy.House.ROOMS[p.room]?.name ?: "")
                    Panel.BuyFood -> "Buy food"
                    Panel.Wake -> "Wake-up options"
                    null -> ""
                }
                PanelSheet(title, ::close, Modifier.height(screenH * frac).padding(bottom = 0.dp)) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        when (p) {
                            Panel.Wardrobe -> WardrobeSheet(wallet, store, { lookPreview = it }) { text, mood ->
                                speakers.living.say(text)
                                setFlash(mood)
                            }
                            is Panel.Decor -> DecorSheet(p.room, wallet, store) { decorView = it }
                            Panel.BuyFood -> BuyFoodSheet(wallet, store)
                            Panel.Wake -> WakeSheet(wallet, needs, store, { quip = Quip(Page.Bedroom, it) }, ::close)
                            null -> {}
                        }
                    }
                }
            }
        }
    }
}
