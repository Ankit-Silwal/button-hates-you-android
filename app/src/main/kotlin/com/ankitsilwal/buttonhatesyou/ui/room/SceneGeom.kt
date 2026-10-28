package com.ankitsilwal.buttonhatesyou.ui.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ankitsilwal.buttonhatesyou.economy.House
import com.ankitsilwal.buttonhatesyou.economy.Room
import com.ankitsilwal.buttonhatesyou.ui.art.HouseArt
import kotlin.math.max
import kotlin.math.min

enum class RoomKind(val id: String) { Living("living"), Kitchen("kitchen"), Bedroom("bedroom"), Bath("bath") }

/**
 * Where everything in a room goes, in px, worked out from the scene's size
 * (a port of the custom properties in src/hub/scene.css):
 *   hs  the button's size     hz  where the wall meets the floor
 *   u   one room unit (furniture is sized in these)
 *   top the first y below the top bar and his speech bubble
 */
class SceneGeom(
    val kind: RoomKind,
    val w: Float,
    val h: Float,
    val dp: Float,
    topInset: Float,
    room: Room?,
    bathInShower: Boolean = false,
    /** The whole canvas height (the floor runs on under the tray). */
    val fullH: Float = h,
) {
    val contentH = max(1f, h - topInset)
    val hs: Float = (min(w * 0.36f, (contentH - 70 * dp) / 1.9f)).coerceIn(84 * dp, 220 * dp)
    val hz: Float = topInset + min(contentH - hs * 0.4f, 60 * dp + 0.5f * contentH + hs * 0.62f)
    val u = hs / 17f
    /** The highest a wall prop starts (under the top bar and the speech bubble). */
    val top = topInset + 70 * dp
    /** A floor piece's bottom edge. */
    val floorY = hz + hs * 0.14f
    val narrow = w < 560 * dp

    val place: Map<String, String> = room?.place?.filter { (s, id) -> HouseArt.has(id) && (House.ROOMS[kind.id]?.spots?.contains(s) == true) } ?: emptyMap()
    fun has(spot: String) = place.containsKey(spot)

    /* ---------- kitchen ---------- */
    val cl = (w * 0.025f).coerceIn(8 * dp, 40 * dp)
    val fh = min(hs * 1.9f, hz - top + 20 * dp)
    val fw = fh * 0.4286f
    val sr = cl
    val sw = hs * 0.8f
    val cr = sr + sw + 6 * dp
    val cs = cl + fw + 4 * dp
    val cw = max(40 * dp, w - cs - cr)

    /* ---------- bedroom: the bed, the nightstand (lamp) and where he stands ---------- */
    val bl = max(8 * dp, w * 0.03f)
    private val bedId = if (kind == RoomKind.Bedroom) place["bed"] else null
    private val nsId = if (kind == RoomKind.Bedroom) place["nightstand"] else null
    /** The bed's own unit: on a phone the bed is shrunk to fit beside him. */
    val ub: Float = run {
        val bw = HouseArt.info(bedId ?: "bed-basic")?.w ?: 36f
        min(u, w * 0.44f / bw)
    }
    val bedRect: Rect? = bedId?.let { pieceRect(it, ub) { pw, ph -> Rect(bl, floorY - ph, bl + pw, floorY) } }
    val nsRect: Rect? = nsId?.let { id ->
        val info = HouseArt.info(id)
        val nu = min(u, w * 0.17f / (info?.w ?: 10f))
        val left = (bedRect?.right ?: bl) + 4 * dp
        pieceRect(id, nu) { pw, ph -> Rect(left, floorY - ph, left + pw, floorY) }
    }

    /* ---------- bathroom ---------- */
    val vx = (w * 0.03f).coerceIn(8 * dp, 60 * dp)
    val showerW = hs * 1.25f
    val showerH = min(hs * 2f, hz - top)
    val shower = Rect(w - sr - showerW, hz + hs * 0.14f - showerH, w - sr, hz + hs * 0.14f)

    /** The middle of where he stands. */
    val hx: Float = when (kind) {
        RoomKind.Bedroom -> min(
            (nsRect?.right ?: bedRect?.right ?: (w * 0.5f)) + hs * 0.55f,
            w - hs * 0.55f,
        )
        RoomKind.Bath -> if (bathInShower) shower.center.x else w * 0.42f
        else -> w * 0.5f
    }
    /** The top of his body (the Avatar's layout box). */
    val heroTop = hz - hs * 1.14f
    val hero: Rect get() = Rect(hx - hs / 2, heroTop, hx + hs / 2, heroTop + hs)

    /** A piece's rect: its width is w (art units) times [unit], its height from the art's aspect. */
    fun pieceRect(id: String, unit: Float = u, at: (Float, Float) -> Rect): Rect? {
        val a = HouseArt.info(id) ?: return null
        val pw = a.w * unit
        val ph = pw * a.vbH / max(1f, a.vbW)
        return at(pw, ph)
    }

    /** Where the furniture in [spot] goes (bed and nightstand have their own rects). */
    fun spotRect(spot: String, id: String): Rect? = when (spot) {
        "bed" -> bedRect
        "nightstand" -> nsRect
        else -> pieceRect(id) { pw, ph ->
            val wide = id == "lights-fairy" || id == "string-lights" || id == "led-strip"
            when (spot) {
                "ceiling" -> if (wide) {
                    val ww = min(w, hs * 5f)
                    val hh = ww * ph / pw
                    val y = top - 20 * dp + if (id == "led-strip") 6 * dp else 0f
                    Rect(w / 2 - ww / 2, y, w / 2 + ww / 2, y + hh)
                } else {
                    // hang from the ceiling, but no lower than over his head
                    val y = min(0f, heroTop - hs * 0.6f - ph).coerceAtLeast(-ph * 0.4f)
                    Rect(w / 2 - pw / 2, y, w / 2 + pw / 2, y + ph)
                }
                "wall-left", "wall-right" -> {
                    val cx = if (spot == "wall-left") w * 0.34f else w * 0.70f
                    val y = max(top, hz - hs * (if (id == "wall-shelf-spices") 1.9f else 2.3f))
                    Rect(cx - pw / 2, y, cx + pw / 2, y + ph)
                }
                "corner" -> {
                    val cx = min(w * 0.92f, w - pw / 2 - 6 * dp)
                    Rect(cx - pw / 2, floorY - ph, cx + pw / 2, floorY)
                }
                "floor-left" -> {
                    val cx = max(w * 0.2f, pw / 2 + 8 * dp)
                    Rect(cx - pw / 2, floorY - ph, cx + pw / 2, floorY)
                }
                "floor-right" -> {
                    val cx = min(w * 0.8f, w - pw / 2 - 8 * dp)
                    Rect(cx - pw / 2, floorY - ph, cx + pw / 2, floorY)
                }
                "counter" -> {
                    val cx = cs + cw * 0.7f
                    val b = hz - hs * 0.47f
                    Rect(cx - pw / 2, b - ph, cx + pw / 2, b)
                }
                "rug" -> {
                    val y = hz - hs * 0.2f
                    Rect(hx - pw / 2, y, hx + pw / 2, y + ph)
                }
                else -> Rect(Offset(w / 2 - pw / 2, floorY - ph), androidx.compose.ui.geometry.Size(pw, ph))
            }
        }
    }

    /** He is asleep in his bed: smaller, tucked in. Rect of his body. */
    fun tuckedHero(bed: String): Rect? {
        val b = bedRect ?: return null
        val bx = b.center.x
        val size = ub * 12f
        val (dx, dy) = when (bed) {
            "bed-cozy" -> -5f to 17.4f
            "bed-cloud" -> -2f to 18.9f
            "bed-loft" -> -10f to 23.4f
            else -> -2f to 15.1f
        }
        // the bed may have been shrunk: its own unit, measured from the floor line
        val topY = floorY - hs * 0.14f - ub * dy
        val cx = bx + ub * dx
        return Rect(cx - size / 2, topY, cx + size / 2, topY + size)
    }
}
