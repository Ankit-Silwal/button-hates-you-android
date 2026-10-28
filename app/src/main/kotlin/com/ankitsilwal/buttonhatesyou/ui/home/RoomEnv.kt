package com.ankitsilwal.buttonhatesyou.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Room
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.common.Clock
import com.ankitsilwal.buttonhatesyou.ui.common.Line

/** The five panels, left to right. The app opens on the living room. */
enum class Page(val title: String, val icon: String) {
    Games("Games", "🎮"),
    Living("Living Room", "🛋️"),
    Kitchen("Kitchen", "🍳"),
    Bedroom("Bedroom", "🛏️"),
    Bath("Bathroom", "🛁"),
}

/** The sheets that open over the bottom of a room. */
sealed interface Panel {
    data object Wardrobe : Panel
    data class Decor(val room: String) : Panel
    data object BuyFood : Panel
    data object Wake : Panel
}

/** What the decorating sheet is trying in a room: the room to draw and the spot being previewed. */
data class DecorView(val room: String, val house: Room, val at: String?)

/** A remark a sheet makes in a room (e.g. the wake-up sheet in the bedroom). */
data class Quip(val page: Page, val text: String, val n: Long = System.nanoTime())

/** Everything a room needs from the home screen. */
class RoomEnv(
    val store: ProfileStore,
    val wallet: Wallet,
    val needs: NeedsState,
    val face: Face,
    /** What he wears (with a wardrobe preview). */
    val look: Look,
    val clock: Clock,
    val topInset: Dp,
    val bottomInset: Dp,
    val panel: Panel?,
    val decorView: DecorView?,
    val quip: Quip?,
    val go: (Page) -> Unit,
    val open: (Panel) -> Unit,
    val play: (String) -> Unit,
) {
    val panelOpen get() = panel != null

    /** The room to draw, with whatever the decorating sheet is trying. */
    fun roomOf(id: String): Room = decorView?.takeIf { it.room == id }?.house ?: com.ankitsilwal.buttonhatesyou.economy.House.roomOf(wallet, id)
    fun previewIn(id: String): String? = decorView?.takeIf { it.room == id }?.at
}

/** A room's speech line with a counter so the bubble pops on every new line. */
class Speaker {
    var line by mutableStateOf(Line())
        private set
    private var n = 0
    fun say(text: String) {
        line = Line(text, ++n)
    }
    fun clear() {
        line = Line("", ++n)
    }
}

/**
 * A room page: the scene fills the page (its floor runs on under the tray),
 * the tray of meters, strips and pills sits at the bottom above the dock.
 * The tray is hidden while a sheet is open.
 */
@Composable
fun RoomFrame(
    env: RoomEnv,
    scene: @Composable (reserve: Dp) -> Unit,
    tray: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    var trayH by remember { mutableIntStateOf(0) }
    val reserve = if (env.panelOpen) 0.dp else with(density) { trayH.toDp() }
    Box(Modifier.fillMaxSize()) {
        scene(reserve)
        if (!env.panelOpen) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { trayH = it.height }
                    .padding(top = 6.dp, bottom = env.bottomInset + 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = tray,
            )
        }
    }
}
