package com.ankitsilwal.buttonhatesyou.data

import android.content.Context
import com.ankitsilwal.buttonhatesyou.avatar.Face
import com.ankitsilwal.buttonhatesyou.avatar.Look
import com.ankitsilwal.buttonhatesyou.economy.Achievement
import com.ankitsilwal.buttonhatesyou.economy.Achievements
import com.ankitsilwal.buttonhatesyou.economy.Catalog
import com.ankitsilwal.buttonhatesyou.economy.Chest
import com.ankitsilwal.buttonhatesyou.economy.ChestLoot
import com.ankitsilwal.buttonhatesyou.economy.ChestState
import com.ankitsilwal.buttonhatesyou.economy.EconomyJson
import com.ankitsilwal.buttonhatesyou.economy.Event
import com.ankitsilwal.buttonhatesyou.economy.Needs
import com.ankitsilwal.buttonhatesyou.economy.NeedsState
import com.ankitsilwal.buttonhatesyou.economy.Rules
import com.ankitsilwal.buttonhatesyou.economy.Wallet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.max

// What is saved on the device: the wallet, and the events made since the
// last cloud sync (`outbox`; nothing sends them yet, see src/profile/store.js).
@Serializable
data class SavedProfile(
    val v: Int = 1,
    val wallet: Wallet = Rules.START,
    val outbox: List<Event> = emptyList(),
)

// What an event paid, and the achievements it made claimable (for an
// "Achievement ready!" line).
data class Earned(val coins: Int, val diamonds: Int, val ready: List<Achievement>)

// A daily chest that was just opened: its loot and the streak it made.
data class OpenedChest(val loot: ChestLoot, val streak: Int)

// The player's profile, offline only for now (a port of src/profile/store.js).
// Every action builds an event, runs it through Rules.applyEvent, and when it
// is allowed updates `wallet`, saves, and queues the event in the outbox.
// Each returns false/null when the wallet doesn't allow it.
class ProfileStore(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var saved: SavedProfile = load()
    private val state = MutableStateFlow(saved.wallet)

    val wallet: StateFlow<Wallet> = state.asStateFlow()

    // Events not yet synced to the cloud.
    val outbox: List<Event> get() = synchronized(lock) { saved.outbox }

    fun now(): Long = clock()

    /* ---------- reading ---------- */

    // The needs as they are right now (hunger, energy and dirt drift with the clock).
    fun currentNeeds(): NeedsState {
        val n = wallet.value.needs
        return Needs.needsAt(n, max(now(), n.at))
    }

    // The button's face for his needs right now.
    fun face(): Face = Needs.faceOf(wallet.value.needs, now())

    // What he is wearing.
    fun look(): Look = Catalog.lookOf(wallet.value.equipped)

    // Why a game of `kind` ("adventure"/"story") can't start right now:
    // "asleep", "tired", "hungry", or null when it can.
    fun whyNot(kind: String): String? = Needs.whyNot(currentNeeds(), kind)

    // Today's chest: ready or not, the streak it would make.
    fun chest(): ChestState = Rules.chestFor(wallet.value, now())

    /* ---------- spending ---------- */

    fun buy(item: String) = tryRecord(Event.Buy(uid(), item))
    fun equip(slot: String, item: String) = tryRecord(Event.Equip(uid(), slot, item))

    // A wallpaper/floor (spot "wall"/"floor") or a piece of furniture in a spot
    // of `room` ("living", "kitchen", "bedroom"); item null takes it down.
    fun decorate(room: String, spot: String, item: String?) =
        tryRecord(Event.Decor(uid(), spot, item, room.takeIf { it != "living" }))

    /* ---------- the button's needs ---------- */

    // Eats a food: from the pantry when there is one, otherwise bought.
    fun feed(food: String) = tryRecord(Event.Feed(uid(), food, now()))
    // Buys a food for the pantry (it is not eaten).
    fun stockFood(food: String) = tryRecord(Event.Stock(uid(), food))
    fun sleep() = tryRecord(Event.Sleep(uid(), now()))
    // `item`: a paid wake-up (WAKE_OPTIONS id), or null for a free one.
    fun wake(item: String? = null) = tryRecord(Event.Wake(uid(), now(), item))
    fun wash() = tryRecord(Event.Wash(uid(), now()))
    // Starting a game: spends energy, or returns false when he's asleep, too
    // tired or too hungry.
    fun play(kind: String) = tryRecord(Event.Play(uid(), kind, now()))

    /* ---------- earning ---------- */

    // A finished Adventure run: its score, `extra` (Button Run's coins), ms of
    // play and the Continues it used. Returns what it paid.
    fun recordRun(game: String, score: Int, extra: Int = 0, ms: Long, continues: Int = 0): Earned? =
        earn(Event.Run(uid(), game, score.toLong(), extra.toLong(), ms, now(), continues.takeIf { it > 0 }))

    // Pays diamonds for the nth Continue of a run. False when it can't be afforded.
    fun continueRun(game: String, n: Int) = tryRecord(Event.Continue(uid(), game, n, now()))

    // A finished story part (an id in STORY_REWARDS). Pays once; null after that.
    fun claimStory(id: String): Earned? = earn(Event.Claim(uid(), id))

    // Claims the next level of an achievement: what it paid, or null.
    fun claimAchievement(id: String, level: Int): Earned? = earn(Event.Achieve(uid(), id, level))

    // Opens today's chest, or null when it was already opened today.
    fun openChest(): OpenedChest? = synchronized(lock) {
        val at = now()
        val before = saved.wallet
        val chest = Rules.chestFor(before, at)
        if (!chest.ready || earn(Event.Chest(uid(), at)) == null) return null
        OpenedChest(Chest.loot(before.seed, chest.day, chest.streak), chest.streak)
    }

    /* ---------- recording ---------- */

    private fun tryRecord(e: Event): Boolean = record(e) != null

    private fun earn(e: Event): Earned? = synchronized(lock) {
        val before = saved.wallet
        val after = record(e) ?: return null
        Earned(after.coins - before.coins, after.diamonds - before.diamonds, Achievements.newlyReady(before, after))
    }

    // Applies the event; when allowed, publishes and saves the new wallet.
    private fun record(e: Event): Wallet? = synchronized(lock) {
        val next = Rules.applyEvent(saved.wallet, e) ?: return null
        // Only the last outfit change per slot (or furniture per spot) matters:
        // don't let the queue grow every time someone tries things on.
        val outbox = saved.outbox.filterNot { sameSlot(it, e) } + e
        saved = saved.copy(wallet = next, outbox = outbox)
        state.value = next
        save()
        next
    }

    private fun sameSlot(a: Event, b: Event) = when {
        a is Event.Equip && b is Event.Equip -> a.slot == b.slot
        a is Event.Decor && b is Event.Decor -> a.spot == b.spot && (a.room ?: "living") == (b.room ?: "living")
        else -> false
    }

    /* ---------- the file ---------- */

    private fun load(): SavedProfile {
        val loaded = try {
            if (file.exists()) EconomyJson.decodeFromString(SavedProfile.serializer(), file.readText()) else null
        } catch (e: Exception) {
            // Unreadable: keep it aside rather than overwrite it, and start fresh.
            file.renameTo(File(file.path + ".bad"))
            null
        }
        val p = loaded ?: SavedProfile(wallet = Rules.START)
        // The daily chest needs a seed of its own.
        val w = Rules.normalize(p.wallet).let { if (it.seed == null) it.copy(seed = uid()) else it }
        return p.copy(wallet = w)
    }

    // Atomic: write a temp file, then move it over the old one.
    private fun save() {
        val json = EconomyJson.encodeToString(SavedProfile.serializer(), saved)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val FILE = "profile.json"

        @Volatile private var instance: ProfileStore? = null

        // The app's one profile, saved in its private files.
        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) {
                instance ?: ProfileStore(File(context.applicationContext.filesDir, FILE)).also { instance = it }
            }

        private fun uid() = UUID.randomUUID().toString()
    }
}

// The needs, re-read every few seconds as they drift with the clock (every
// second while he sleeps, so the energy meter visibly fills), and right away
// whenever the wallet's needs change.
@OptIn(ExperimentalCoroutinesApi::class)
fun NeedsTicker(store: ProfileStore): Flow<NeedsState> =
    store.wallet.map { it.needs }.distinctUntilChanged().flatMapLatest { needs ->
        flow {
            while (true) {
                val n = Needs.needsAt(needs, max(store.now(), needs.at))
                emit(n)
                delay(if (n.asleep) 1_000L else 5_000L)
            }
        }
    }
