package com.ankitsilwal.buttonhatesyou.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.avatar.Avatar
import com.ankitsilwal.buttonhatesyou.avatar.Mood
import com.ankitsilwal.buttonhatesyou.data.ProfileStore
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.economy.HouseItem
import com.ankitsilwal.buttonhatesyou.economy.Item
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Priced
import com.ankitsilwal.buttonhatesyou.economy.Rules
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import com.ankitsilwal.buttonhatesyou.ui.Lines
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import com.ankitsilwal.buttonhatesyou.ui.art.RoomThemes
import com.ankitsilwal.buttonhatesyou.ui.common.BuyBar
import com.ankitsilwal.buttonhatesyou.ui.common.ChipTabs
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.Pill
import com.ankitsilwal.buttonhatesyou.ui.common.PillKind
import com.ankitsilwal.buttonhatesyou.ui.common.priceText
import com.ankitsilwal.buttonhatesyou.ui.home.DecorView
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ------------------------------ shared bits ------------------------------ */

/** "need 120 more 🪙", or null when it can be afforded. */
fun shortOf(wallet: Wallet, item: Priced): String? {
    val p = item.price ?: return null
    if (Rules.canAfford(wallet, item)) return null
    return if (p.diamonds > 0) "need ${p.diamonds - wallet.diamonds} more 💎" else "need ${p.coins - wallet.coins} more 🪙"
}

/** A one-line status that clears itself after a few seconds. */
class Said {
    var text by mutableStateOf("")
        private set
    private var job: Job? = null
    fun set(scope: kotlinx.coroutines.CoroutineScope, t: String, ms: Long = 3600) {
        text = t
        job?.cancel()
        job = scope.launch { delay(ms); text = "" }
    }
}

@Composable
private fun SaidLine(text: String, fallback: String) {
    Text(
        text.ifBlank { fallback },
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (text.isBlank()) Palette.InkSoft else Palette.Ink,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        maxLines = 2,
    )
}

/** A shop tile: a picture, a name and a price line; a check when in use, a ring when being tried. */
@Composable
fun ShopTile(name: String, cost: String, on: Boolean, trying: Boolean, enabled: Boolean = true, onClick: () -> Unit, art: @Composable BoxScope.() -> Unit) {
    val fb = LocalFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (trying) Color(0xFFFFF0CC) else Color.White)
            .border(if (trying) 2.5.dp else 1.dp, if (trying) Palette.Mustard else Color(0xFFEADBC6), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = name) { fb.tick(); onClick() }
            .padding(6.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center, content = art)
            Text(name, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 28.dp))
            Text(cost, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color(0xFF2F6B3A) else Palette.InkSoft, maxLines = 1)
        }
        if (on) {
            Box(Modifier.align(Alignment.TopEnd).size(22.dp).background(Color(0xFF5FA86B), CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/* -------------------------------- wardrobe -------------------------------- */

/**
 * Pick a slot, tap things to try them on (the big button in the room wears
 * them via [onPreview]), buy what you like (src/components/Wardrobe.jsx).
 */
@Composable
fun WardrobeSheet(wallet: Wallet, store: ProfileStore, onPreview: (Map<String, String>?) -> Unit, onSay: (String, Mood?) -> Unit) {
    var slot by remember { mutableStateOf("color") }
    var trying by remember { mutableStateOf<String?>(null) }
    val fb = LocalFeedback.current
    val tryItem = trying?.let { Catalog.ITEM[it] }
    LaunchedEffect(tryItem, wallet.equipped) {
        onPreview(tryItem?.let { wallet.equipped + (it.slot to it.id) })
    }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }

    fun choose(item: Item) {
        if (!Rules.owns(wallet, item)) { trying = item.id; return }
        trying = null
        if (wallet.equipped[item.slot] != item.id && store.equip(item.slot, item.id)) onSay("Hmm. Acceptable.", Mood.Smug)
    }

    fun purchase(item: Item) {
        if (!store.buy(item.id)) return onSay(Lines.BROKE, Mood.Annoyed)
        store.equip(item.slot, item.id)
        fb.sound("coin")
        trying = null
        onSay("Fine. I look amazing. Doesn't mean I like you.", Mood.Smug)
    }

    Column(Modifier.fillMaxSize()) {
        ChipTabs(Catalog.SLOTS.map { it.id to "${it.icon} ${it.name}" }, slot, { slot = it; trying = null })
        LazyVerticalGrid(
            GridCells.Adaptive(96.dp),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Catalog.ITEMS.filter { it.slot == slot }, key = { it.id }) { item ->
                val have = Rules.owns(wallet, item)
                val on = wallet.equipped[slot] == item.id
                ShopTile(
                    item.name,
                    if (have) (if (on) "Wearing" else "Owned") else priceText(item.price),
                    on = on,
                    trying = trying == item.id,
                    onClick = { choose(item) },
                ) {
                    Box(Modifier.padding(top = 14.dp)) {
                        Avatar(Catalog.lookOf(wallet.equipped + (slot to item.id)), size = 42.dp, legs = false)
                    }
                }
            }
        }
        if (tryItem != null) {
            val short = shortOf(wallet, tryItem)
            BuyBar("Trying on", tryItem.name, short) {
                Pill("Cancel", { trying = null }, kind = PillKind.Ghost, height = 48.dp)
                Pill("Buy · ${priceText(tryItem.price)}", { purchase(tryItem) }, enabled = short == null, height = 48.dp)
            }
        }
    }
}

/* -------------------------------- decorate -------------------------------- */

private val DECOR_TABS = listOf("wall" to "🖼️ Walls", "floor" to "🪵 Floors", "furniture" to "🛋️ Furniture")

/**
 * The decorating sheet of one room (src/house/DecorSheet.jsx): walls, floors
 * and furniture that fit it. Tap to see it in the room, then buy it, place
 * it or take it down.
 */
@Composable
fun DecorSheet(room: String, wallet: Wallet, store: ProfileStore, onPreview: (DecorView?) -> Unit) {
    val cfg = House.ROOMS[room] ?: return
    val cur = House.roomOf(wallet, room)
    val fb = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val said = remember { Said() }
    var tab by remember { mutableStateOf("furniture") }
    var trying by remember { mutableStateOf<String?>(null) }
    var spot by remember { mutableStateOf<String?>(null) }

    val item = trying?.let { House.HOUSE_ITEM[it] }
    val have = item != null && Rules.owns(wallet, item)
    val spots = if (item?.kind == "furniture") item.spots.filter { it in cfg.spots } else emptyList()
    val placedAt = if (item?.kind == "furniture") cur.place.entries.firstOrNull { it.value == item.id }?.key else null

    // what the room shows: the real room plus whatever is being tried
    val view: DecorView = when {
        item != null && item.kind != "furniture" && (if (item.kind == "wall") cur.wall else cur.floor) != item.id ->
            DecorView(room, if (item.kind == "wall") cur.copy(wall = item.id) else cur.copy(floor = item.id), item.kind)
        item?.kind == "furniture" && spot != null && cur.place[spot] != item.id -> {
            val place = LinkedHashMap(cur.place)
            place.values.removeAll { it == item.id }
            place[spot!!] = item.id
            DecorView(room, cur.copy(place = place), spot)
        }
        else -> DecorView(room, cur, null)
    }
    LaunchedEffect(trying, view) { onPreview(if (trying != null) view else null) }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }

    fun cancel() { trying = null; spot = null }
    fun choose(it: HouseItem) {
        if (trying == it.id) return cancel()
        trying = it.id
        if (it.kind == "furniture") {
            val ok = it.spots.filter { s -> s in cfg.spots }
            val where = cur.place.entries.firstOrNull { e -> e.value == it.id }?.key
            spot = where ?: ok.firstOrNull { s -> cur.place[s] == null } ?: ok.firstOrNull()
        }
    }
    fun target(it: HouseItem) = if (it.kind == "furniture") spot else it.kind
    fun buyItem(it: HouseItem) {
        if (!store.buy(it.id)) return said.set(scope, Lines.BROKE)
        target(it)?.let { s -> store.decorate(room, s, it.id) }
        fb.sound("coin")
        said.set(scope, Lines.pick(Lines.BOUGHT))
        cancel()
    }
    fun applyItem(it: HouseItem) {
        val s = target(it) ?: return
        if (!store.decorate(room, s, it.id)) return said.set(scope, "That doesn't fit there.")
        said.set(scope, Lines.pick(Lines.PLACED))
        cancel()
    }
    fun takeDown() {
        val s = placedAt ?: return
        if (!store.decorate(room, s, null)) return said.set(scope, "That one stays. He needs it.")
        said.set(scope, "Back in the box it goes.")
        cancel()
    }

    val list = House.HOUSE_ITEMS.filter { it.kind == tab && room in it.rooms }
    fun inUse(it: HouseItem) = if (it.kind == "furniture") it.id in cur.place.values else (if (it.kind == "wall") cur.wall else cur.floor) == it.id
    val required = spot in cfg.required

    Column(Modifier.fillMaxSize()) {
        ChipTabs(DECOR_TABS, tab, { tab = it; cancel() })
        SaidLine(said.text, "Tap a thing to see it in the ${cfg.name}. Nothing is charged until you buy.")
        LazyVerticalGrid(
            GridCells.Adaptive(96.dp),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { it ->
                val mine = Rules.owns(wallet, it)
                val use = inUse(it)
                ShopTile(
                    it.name,
                    if (use) (if (it.kind == "furniture") "Placed" else "In use") else if (mine) "Owned" else priceText(it.price),
                    on = use,
                    trying = trying == it.id,
                    onClick = { choose(it) },
                ) {
                    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                        if (it.kind == "furniture") {
                            val a = HouseArt.info(it.id)
                            if (a != null) {
                                val k = minOf(size.width / a.vbW, size.height / a.vbH)
                                val w = a.vbW * k
                                val h = a.vbH * k
                                HouseArt.draw(this, it.id, Rect((size.width - w) / 2, (size.height - h) / 2, (size.width + w) / 2, (size.height + h) / 2), 0f)
                            }
                        } else {
                            val s = minOf(size.width, size.height)
                            RoomThemes.drawSwatch(this, it.id, Rect((size.width - s * 1.3f) / 2, 0f, (size.width + s * 1.3f) / 2, s))
                        }
                    }
                }
            }
        }
        if (item != null) {
            val short = shortOf(wallet, item)
            val note = (if (item.kind == "furniture" && have) "Pick a spot, then place it." else if (have) "Shown in the room." else "Shown in the room. Nothing is charged until you buy.") + (short?.let { " · $it" } ?: "")
            BuyBar(
                if (have) "Your stuff" else "Previewing",
                item.name,
                note,
                extra = if (spots.size > 1) {
                    {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(spots.size) { i ->
                                val s = spots[i]
                                val on = s == spot
                                val swap = cur.place[s] != null && cur.place[s] != item.id
                                Text(
                                    (House.SPOT[s]?.name ?: s) + if (swap) " (swap)" else "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (on) Palette.Cream else Palette.Ink,
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (on) Palette.Ink else Color.White)
                                        .border(1.dp, Color(0xFFE6CFB1), RoundedCornerShape(50))
                                        .clickable(role = Role.RadioButton) { fb.tick(); spot = s }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                } else null,
            ) {
                Pill("Cancel", ::cancel, kind = PillKind.Ghost, height = 48.dp)
                when {
                    !have -> Pill("Buy · ${priceText(item.price)}", { buyItem(item) }, enabled = short == null, height = 48.dp)
                    item.kind == "furniture" && spot != null && cur.place[spot] == item.id ->
                        if (!required) Pill("Take down", ::takeDown, kind = PillKind.Alt, height = 48.dp)
                    item.kind != "furniture" && inUse(item) -> {}
                    else -> Pill(if (item.kind == "furniture") "Place it" else "Use this", { applyItem(item) }, height = 48.dp)
                }
            }
        }
    }
}

/* -------------------------------- buy food -------------------------------- */

/** The whole menu (src/hub/BuySheet.jsx): tapping a dish buys ONE for the fridge. */
@Composable
fun BuyFoodSheet(wallet: Wallet, store: ProfileStore) {
    val scope = rememberCoroutineScope()
    val fb = LocalFeedback.current
    val said = remember { Said() }
    Column(Modifier.fillMaxSize()) {
        SaidLine(said.text, "Tap a dish to put one in the fridge. Feed him from the strip in the Kitchen.")
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(House.FOODS, key = { it.id }) { f ->
                val own = wallet.pantry[f.id] ?: 0
                val full = own >= Rules.STOCK_MAX
                val fx = listOfNotNull(f.hunger.takeIf { it > 0 }?.let { "🍔+$it" }, f.energy.takeIf { it > 0 }?.let { "⚡+$it" }).joinToString(" ")
                ShopTile(
                    f.name,
                    priceText(f.price) + if (own > 0) " · $own in fridge" else "",
                    on = false,
                    trying = false,
                    enabled = Rules.canAfford(wallet, f) && !full,
                    onClick = {
                        when {
                            full -> said.set(scope, "The fridge can't hold more ${f.name}.")
                            !store.stockFood(f.id) -> said.set(scope, Lines.BROKE)
                            else -> { fb.sound("coin"); said.set(scope, "${f.name} is in the fridge.") }
                        }
                    },
                ) {
                    Canvas(Modifier.size(54.dp).align(Alignment.TopCenter)) { HouseArt.draw(this, f.id, Rect(0f, 0f, size.width, size.height), 0f) }
                    Text(fx, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.InkSoft, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/* ------------------------------- wake-up ------------------------------- */

/** Paid ways to get him out of bed on the spot, with some energy back (src/hub/WakeSheet.jsx). */
@Composable
fun WakeSheet(wallet: Wallet, needs: NeedsState, store: ProfileStore, onSay: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val fb = LocalFeedback.current
    val said = remember { Said() }
    Column(Modifier.fillMaxSize()) {
        SaidLine(said.text, "Waking him with the lamp is free but leaves him as sleepy as he was. These wake him on the spot and give some energy back.")
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(House.WAKE_OPTIONS, key = { it.id }) { o ->
                ShopTile(
                    o.name,
                    if (needs.asleep) "⚡ +${o.energy} · ${priceText(o.price)}" else "he is awake",
                    on = false,
                    trying = false,
                    enabled = needs.asleep && Rules.canAfford(wallet, o),
                    onClick = {
                        if (!store.wake(o.id)) {
                            said.set(scope, "You can't afford that. Let him sleep, or wake him with the lamp for free.")
                        } else {
                            fb.sound("jump")
                            onSay(o.quip)
                            onClose()
                        }
                    },
                ) {
                    Text(o.icon, fontSize = 38.sp)
                }
            }
        }
    }
}
