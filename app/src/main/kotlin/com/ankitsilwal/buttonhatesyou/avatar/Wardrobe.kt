package com.ankitsilwal.buttonhatesyou.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp

// Every outfit item, ported from src/app/avatar.css, in the 100x100 unit box
// (see ButtonArt.kt). Unknown or null ids draw nothing.
internal object Wardrobe {

    val EARS = listOf("bear", "cat", "bunny", "devil", "earbuds", "headphones")
    val EYES = listOf("sleepy", "googly", "hearts", "shades", "stars", "chill", "glasses", "aviators", "visor")
    val SCARVES = listOf("red", "sage", "striped", "rainbow", "lanyard", "bandana", "chain")
    val HATS = listOf("beanie", "party", "top", "crown", "headband", "cap", "bucket", "beret")
    val OUTFITS = listOf("tee", "hoodie", "track", "denim", "varsity", "blazer", "leather", "puffer")

    /** Eye items that replace the eyes entirely. */
    val EYES_COVER = setOf("shades", "aviators", "visor", "hearts", "stars")

    private val PINK = Color(0xFFF6B8C4)

    private fun poly(vararg xy: Float): Path = Path().apply {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) {
            lineTo(xy[i], xy[i + 1]); i += 2
        }
        close()
    }

    /** Mirrors the block across the body's vertical axis (x = 50). */
    private inline fun DrawScope.mirrored(block: DrawScope.() -> Unit) {
        block()
        scale(-1f, 1f, Offset(50f, 0f)) { block() }
    }

    // ---- ears (animal ears behind the body) -----------------------------------

    private val CAT_EAR by lazy { poly(23f, -14f, 40f, 26f, 6f, 26f) }
    private val CAT_INNER by lazy { poly(23f, -2f, 31.5f, 26f, 14.5f, 26f) }
    private val DEVIL_HORN by lazy {
        Path().apply {
            moveTo(20f, 18f)
            quadraticTo(19f, 3f, 25.4f, -10f)
            quadraticTo(28f, 6f, 38f, 18f)
            close()
        }
    }
    private val DEVIL_BRUSH = Brush.linearGradient(
        0f to Color(0xFFB8302C), 0.6f to Color(0xFF8E1B1B), 1f to Color(0xFF661212),
        start = Offset(24f, -10f), end = Offset(30f, 18f),
    )

    fun earsBehind(s: DrawScope, kind: String?, c: Color, cd: Color) = with(s) {
        val fur = Brush.verticalGradient(0f to lerp(c, Color.White, 0.08f), 1f to lerp(c, cd, 0.35f), startY = -46f, endY = 26f)
        when (kind) {
            "bear" -> mirrored {
                drawCircle(fur, 17f, Offset(19f, 11f))
                drawCircle(cd, 9.5f, Offset(19f, 11f))
                drawCircle(Color.White.copy(alpha = 0.12f), 9.5f, Offset(19f, 11f), style = Stroke(1f))
            }
            "cat" -> mirrored {
                rotate(-18f, Offset(23f, 6f)) {
                    drawPath(CAT_EAR, fur)
                    drawPath(CAT_EAR, fur, style = Stroke(4f, join = StrokeJoin.Round))
                    drawPath(CAT_INNER, PINK)
                    drawPath(CAT_INNER, PINK, style = Stroke(2f, join = StrokeJoin.Round))
                }
            }
            "bunny" -> {
                rotate(-10f, Offset(29f, -14f)) { bunnyEar(18f, fur) }
                rotate(14f, Offset(71f, -14f)) { bunnyEar(60f, fur) }
            }
            "devil" -> mirrored {
                rotate(-20f, Offset(29f, 4f)) {
                    drawPath(DEVIL_HORN, DEVIL_BRUSH)
                    drawPath(DEVIL_HORN, DEVIL_BRUSH, style = Stroke(1.6f, join = StrokeJoin.Round))
                    drawLine(Color.White.copy(alpha = 0.25f), Offset(22.5f, 12f), Offset(24.6f, -3f), 1.2f, cap = StrokeCap.Round)
                }
            }
            else -> Unit
        }
    }

    private fun DrawScope.bunnyEar(x: Float, fur: Brush) {
        drawOval(fur, Offset(x, -46f), Size(22f, 64f))
        drawOval(PINK, Offset(x + 6.16f, -38.3f), Size(9.68f, 43.5f))
    }

    // ---- earbuds and headphones (in front of the body) ------------------------

    private val BUD = Brush.radialGradient(
        0f to Color.White, 0.6f to Color(0xFFE6E3DC), 1f to Color(0xFFC9C5BB),
        center = Offset(-0.5f, 53.6f), radius = 7f,
    )
    private val CUP = Brush.horizontalGradient(0f to Color(0xFF3A3D47), 1f to Color(0xFF23252C), startX = -9f, endX = 6f)

    fun earsFront(s: DrawScope, kind: String?) = with(s) {
        when (kind) {
            "earbuds" -> mirrored {
                drawRoundRect(Color(0xFFE6E3DC), Offset(-0.8f, 57f), Size(3.6f, 12f), CornerRadius(1.8f))
                drawOval(Color.Black.copy(alpha = 0.18f), Offset(-4f, 51f), Size(10f, 12f))
                drawOval(BUD, Offset(-4f, 50f), Size(10f, 12f))
            }
            "headphones" -> {
                // the band over the top
                val band = Path().apply { arcTo(Rect(-3.75f, -9.75f, 103.75f, 109.75f), 180f, 180f, true) }
                drawPath(band, Color(0xFF2B2D35), style = Stroke(4.5f))
                val shine = Path().apply { arcTo(Rect(-2.6f, -8.6f, 102.6f, 108.6f), 200f, 70f, true) }
                drawPath(shine, Color.White.copy(alpha = 0.14f), style = Stroke(1.1f, cap = StrokeCap.Round))
                mirrored {
                    drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(-9f, 41.2f), Size(15f, 27f), CornerRadius(5.7f, 10f))
                    drawRoundRect(CUP, Offset(-9f, 40f), Size(15f, 27f), CornerRadius(5.7f, 10f))
                    drawRoundRect(Color(0xFFC2765A), Offset(-6.3f, 46.5f), Size(9.6f, 14f), CornerRadius(2.9f))
                    drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(-7.6f, 42f), Size(3f, 6f), CornerRadius(1.5f))
                }
            }
            else -> Unit
        }
    }

    // ---- outfits (clipped to the body, a neck hole showing the mouth) ----------

    private class Neck(val rx: Float, val ry: Float, val cy: Float, val rib: Color?, val ribW: Float)

    private val NECKS = mapOf(
        "tee" to Neck(17f, 14f, 64f, Color(0xFF23252B), 2.6f),
        "hoodie" to Neck(18f, 13f, 64f, Color(0xFF474D58), 5f),
        "track" to Neck(13f, 9f, 66f, Color(0xFF1F3340), 4.5f),
        "denim" to Neck(16f, 11f, 66f, Color(0xFF3E5A7C), 3.5f),
        "varsity" to Neck(16f, 12f, 65f, Color(0xFFD8D0BB), 3.5f),
        "blazer" to Neck(0f, 0f, 0f, null, 0f),
        "leather" to Neck(15f, 12f, 64f, Color(0xFF2F3138), 3f),
        "puffer" to Neck(17f, 12f, 64f, Color(0xFF445A55), 6f),
    )

    /** The darker base under the button, in the outfit's colour. */
    fun baseColor(kind: String?): Color? = when (kind) {
        "tee" -> Color(0xFF202228)
        "hoodie" -> Color(0xFF41464F)
        "track" -> Color(0xFF1C2F3B)
        "denim" -> Color(0xFF3A5576)
        "varsity" -> Color(0xFF1E283C)
        "blazer" -> Color(0xFF25272D)
        "leather" -> Color(0xFF141519)
        "puffer" -> Color(0xFF3B4F4A)
        else -> null
    }

    private val fabricCache = HashMap<String, Path>()

    private fun fabric(kind: String): Path = fabricCache.getOrPut(kind) {
        val shape = if (kind == "blazer") {
            poly(0f, 64f, 16f, 60.5f, 28f, 60f, 50f, 85f, 72f, 60f, 84f, 60.5f, 100f, 64f, 100f, 100f, 0f, 100f)
        } else {
            poly(0f, 64f, 18f, 60.5f, 82f, 60.5f, 100f, 64f, 100f, 100f, 0f, 100f)
        }
        val inBody = Path().apply { op(shape, Shapes.body, PathOperation.Intersect) }
        val n = NECKS.getValue(kind)
        if (n.rx <= 0f) {
            inBody
        } else {
            val hole = Path().apply { addOval(Rect(50f - n.rx, n.cy - n.ry, 50f + n.rx, n.cy + n.ry)) }
            Path().apply { op(inBody, hole, PathOperation.Difference) }
        }
    }

    private val FAB_SHADE = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.1f), 0.2f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f),
        startY = 0f, endY = 100f,
    )

    fun outfit(s: DrawScope, kind: String?) = with(s) {
        if (kind == null || kind !in NECKS) return@with
        val path = fabric(kind)
        clipPath(Shapes.body) {
            clipPath(path) {
                fabricLayers(kind)
                drawRect(FAB_SHADE, Offset.Zero, Size(100f, 100f))
            }
            details(kind)
            val n = NECKS.getValue(kind)
            if (n.rib != null) {
                val r = Rect(50f - n.rx - 1f, n.cy - n.ry - 1f, 50f + n.rx + 1f, n.cy + n.ry + 1f).deflate(n.ribW / 2f)
                drawArc(n.rib, 0f, 180f, false, r.topLeft, r.size, style = Stroke(n.ribW))
                drawArc(Color.White.copy(alpha = 0.12f), 15f, 150f, false, r.topLeft, r.size, style = Stroke(n.ribW * 0.25f))
            }
        }
    }

    private fun DrawScope.full(color: Color) = drawRect(color, Offset.Zero, Size(100f, 100f))
    private fun DrawScope.full(brush: Brush) = drawRect(brush, Offset.Zero, Size(100f, 100f))

    private val HOODIE_SIDES = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.18f), 0.16f to Color.Transparent, 0.84f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f),
        startX = 0f, endX = 100f,
    )
    private val PUFFER_BAFFLES = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.18f), 0.44f to Color.Transparent, 0.9f to Color.Black.copy(alpha = 0.34f), 1f to Color.Black.copy(alpha = 0.34f),
        startY = 0f, endY = 8.2f, tileMode = TileMode.Repeated,
    )
    private val LEATHER_SHINE by lazy {
        cssLinear(
            112f,
            0.22f to Color.Transparent, 0.30f to Color.White.copy(alpha = 0.17f), 0.40f to Color.Transparent,
            0.62f to Color.Transparent, 0.68f to Color.White.copy(alpha = 0.09f), 0.76f to Color.Transparent,
        )
    }

    private fun DrawScope.fabricLayers(kind: String) {
        when (kind) {
            "tee" -> full(Color(0xFF33353E))
            "hoodie" -> {
                full(Color(0xFF5F6672)); full(HOODIE_SIDES)
            }
            "track" -> {
                full(Color(0xFF2E4757))
                val st = Color(0xFFE3DFD2)
                drawRect(st, Offset(16f, 0f), Size(3f, 100f))
                drawRect(st, Offset(21f, 0f), Size(1.5f, 100f))
                drawRect(st, Offset(77.5f, 0f), Size(1.5f, 100f))
                drawRect(st, Offset(81f, 0f), Size(3f, 100f))
            }
            "denim" -> {
                full(Color(0xFF4F6E92))
                drawRect(Color.White.copy(alpha = 0.2f), Offset(49.3f, 0f), Size(1.4f, 100f))
                drawCircle(Color(0xFFD9B86A), 1.6f, Offset(50f, 85f))
                drawCircle(Color(0xFFD9B86A), 1.6f, Offset(50f, 95f))
            }
            "varsity" -> {
                full(Color(0xFF2D3B57))
                val sleeve = Color(0xFFCFC6B0)
                drawRect(sleeve, Offset(0f, 0f), Size(20f, 100f))
                drawRect(sleeve, Offset(80f, 0f), Size(20f, 100f))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(20f, 0f), Size(1.2f, 100f))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(78.8f, 0f), Size(1.2f, 100f))
                drawRect(Color(0xFFA8423A), Offset(0f, 91f), Size(100f, 2.5f))
                drawRect(Color(0xFFE6DFCC), Offset(0f, 93.5f), Size(100f, 2.5f))
                drawRect(Color(0xFFA8423A), Offset(0f, 96f), Size(100f, 4f))
                drawCircle(Color(0xFFD8D0BB), 1.4f, Offset(50f, 86f))
                drawCircle(Color(0xFFD8D0BB), 1.4f, Offset(50f, 95f))
            }
            "blazer" -> {
                full(Color(0xFF383B44))
                drawRect(Color(0xFFE6DFCC), Offset(68.8f, 80.2f), Size(7f, 4.5f))
            }
            "leather" -> {
                full(Color(0xFF202126))
                full(LEATHER_SHINE)
                drawLine(Color(0xFFAEB1B8), Offset(51.4f, 60f), Offset(46.1f, 100f), 1.6f)
            }
            "puffer" -> {
                full(Color(0xFF56706A))
                full(PUFFER_BAFFLES)
                drawRect(Color.Black.copy(alpha = 0.4f), Offset(49.3f, 0f), Size(1.4f, 100f))
            }
        }
    }

    private val BLAZER_SHIRT by lazy { poly(31f, 62f, 39f, 62f, 40f, 73f, 60f, 73f, 61f, 62f, 69f, 62f, 50f, 83.6f) }
    private val BLAZER_LAPEL_L by lazy { poly(20f, 61f, 28f, 60f, 50f, 85f, 45f, 88f) }
    private val BLAZER_LAPEL_R by lazy { poly(80f, 61f, 72f, 60f, 50f, 85f, 55f, 88f) }
    private val LEATHER_LAPEL_L by lazy { poly(22f, 62f, 37f, 62f, 47f, 80f, 30f, 74f) }
    private val LEATHER_LAPEL_R by lazy { poly(78f, 62f, 63f, 62f, 55f, 77f, 67f, 75f) }
    private val HOODIE_POUCH by lazy { poly(27f, 88f, 73f, 88f, 69.3f, 100f, 30.7f, 100f) }
    private val VARSITY_PATCH by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    58f, 79f, 72f, 93f,
                    topLeftCornerRadius = CornerRadius(2.8f), topRightCornerRadius = CornerRadius(2.8f),
                    bottomRightCornerRadius = CornerRadius(7f), bottomLeftCornerRadius = CornerRadius(7f),
                ),
            )
        }
    }
    private val LETTER_B by lazy {
        Path().apply {
            moveTo(62.6f, 82.2f); lineTo(62.6f, 90f)
            moveTo(62.6f, 82.2f); lineTo(65.6f, 82.2f)
            quadraticTo(67.6f, 82.2f, 67.6f, 84f); quadraticTo(67.6f, 85.9f, 65.6f, 85.9f); lineTo(62.6f, 85.9f)
            moveTo(65.6f, 85.9f); quadraticTo(68.2f, 85.9f, 68.2f, 88f); quadraticTo(68.2f, 90f, 65.6f, 90f); lineTo(62.6f, 90f)
        }
    }

    private fun DrawScope.details(kind: String) {
        when (kind) {
            "tee" -> {
                // a small round band print
                drawCircle(Color(0xFFE6DFCC), 5.6f, Offset(50f, 89.5f), style = Stroke(1.5f))
                drawCircle(Color(0xFFD6A543), 2.4f, Offset(50f, 89.5f))
            }
            "hoodie" -> {
                drawPath(HOODIE_POUCH, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.16f), startY = 88f, endY = 100f))
                drawPath(HOODIE_POUCH, Color.Black.copy(alpha = 0.18f), style = Stroke(0.8f))
                val str = Color(0xFFE6E0D2)
                drawLine(str, Offset(38.9f, 76f), Offset(39.3f, 89f), 1.7f, cap = StrokeCap.Round)
                drawLine(str, Offset(61.1f, 76f), Offset(60.7f, 89f), 1.7f, cap = StrokeCap.Round)
            }
            "track" -> {
                val zip = Brush.verticalGradient(
                    0f to Color(0xFFD8D2C0), 0.5f to Color(0xFFD8D2C0), 0.5f to Color(0xFF6D6A60), 1f to Color(0xFF6D6A60),
                    startY = 75f, endY = 77.4f, tileMode = TileMode.Repeated,
                )
                drawRect(zip, Offset(49.2f, 75f), Size(1.6f, 25f))
                drawRoundRect(Color(0xFFD8D2C0), Offset(47.2f, 77f), Size(5.6f, 6f), CornerRadius(1f))
            }
            "denim" -> {
                val st = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f, 1.2f)))
                for (x in floatArrayOf(25f, 61f)) {
                    drawRoundRect(Color.Black.copy(alpha = 0.1f), Offset(x, 81f), Size(14f, 10f), CornerRadius(1.5f))
                    drawRoundRect(rgba(235, 215, 160, 0.6f), Offset(x + 0.5f, 81.5f), Size(13f, 9f), CornerRadius(1.5f), style = st)
                }
            }
            "varsity" -> {
                drawPath(VARSITY_PATCH, Color(0xFFA8423A))
                drawPath(VARSITY_PATCH, Color(0xFFE6DFCC), style = Stroke(1f))
                drawPath(LETTER_B, Color(0xFFF0E9D6), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "blazer" -> {
                drawPath(BLAZER_LAPEL_L, Color(0xFF4B4F5B))
                drawPath(BLAZER_LAPEL_R, Color(0xFF4B4F5B))
                drawPath(BLAZER_SHIRT, Brush.verticalGradient(0f to Color(0xFFF0ECE2), 1f to Color(0xFFD6D1C4), startY = 62f, endY = 84f))
                drawCircle(Color(0xFF1C1D22), 1.2f, Offset(50f, 92f))
            }
            "leather" -> {
                val lap = Brush.linearGradient(0f to Color(0xFF43464F), 1f to Color(0xFF2B2D34), start = Offset(22f, 62f), end = Offset(47f, 80f))
                drawPath(LEATHER_LAPEL_L, lap)
                drawPath(LEATHER_LAPEL_R, lap)
                drawCircle(Color(0xFFAEB1B8), 1f, Offset(33f, 70f))
            }
            else -> Unit
        }
    }

    // ---- neck items -----------------------------------------------------------

    private val SCARF_BAND by lazy {
        Path().apply { addRoundRect(RoundRect(10f, 74f, 90f, 90f, CornerRadius(33.3f, 8f))) }
    }
    private val SCARF_TAIL by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    62.8f, 82.8f, 77.2f, 102f,
                    topLeftCornerRadius = CornerRadius(3f), topRightCornerRadius = CornerRadius(3f),
                    bottomRightCornerRadius = CornerRadius(6f), bottomLeftCornerRadius = CornerRadius(6f),
                ),
            )
        }
    }
    private val STRIPED = Brush.horizontalGradient(
        0f to Color(0xFFF0C36D), 0.5f to Color(0xFFF0C36D), 0.5f to Color(0xFFFFFAF2), 1f to Color(0xFFFFFAF2),
        startX = 10f, endX = 29.2f, tileMode = TileMode.Repeated,
    )
    private val RAINBOW = Brush.horizontalGradient(
        listOf(Color(0xFFE2674F), Color(0xFFF0C36D), Color(0xFF9BBF9A), Color(0xFF8FB8D8), Color(0xFFC4A6DC)),
        startX = 10f, endX = 90f,
    )
    private val LANYARD_L by lazy { poly(21.8f, 72f, 28.4f, 72f, 53f, 89.4f, 47f, 89.4f) }
    private val LANYARD_R by lazy { poly(78.2f, 72f, 71.6f, 72f, 47f, 89.4f, 53f, 89.4f) }
    private val BANDANA by lazy { poly(14f, 72f, 86f, 72f, 50f, 100f) }
    private val CHAIN by lazy { Path().apply { arcTo(Rect(18.8f, 44.8f, 81.2f, 91.2f), 0f, 180f, true) } }
    private val PENDANT by lazy { poly(50f, 89f, 53.75f, 92.4f, 50f, 98f, 46.25f, 92.4f) }

    fun neck(s: DrawScope, kind: String?) = with(s) {
        when (kind) {
            "red" -> scarf(SolidOrBrush(Color(0xFFC0392B)))
            "sage" -> scarf(SolidOrBrush(Color(0xFF6F9A6E)))
            "striped" -> scarf(SolidOrBrush(brush = STRIPED))
            "rainbow" -> scarf(SolidOrBrush(brush = RAINBOW))
            "lanyard" -> {
                val strap = Color(0xFF2F6670)
                drawPath(LANYARD_L, strap)
                drawPath(LANYARD_R, strap)
                // the ID card
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(43.4f, 88.2f), Size(13.2f, 12f), CornerRadius(1.2f))
                drawRoundRect(Color(0xFFEEEBE3), Offset(43.4f, 87.6f), Size(13.2f, 12f), CornerRadius(1.2f))
                drawRect(Color(0xFF3E4D5E), Offset(46.96f, 89.3f), Size(6.07f, 4.08f))
                drawRect(Color(0xFFCFD5D9), Offset(46.3f, 96.6f), Size(7.4f, 1f))
                drawRect(strap, Offset(48.4f, 87.6f), Size(3.2f, 1.2f))
            }
            "bandana" -> {
                drawPath(BANDANA, Color(0xFF9A3F3A))
                clipPath(BANDANA) {
                    val dot = Color(0xFFF0E8D6).copy(alpha = 0.75f)
                    var y = 74f
                    var row = 0
                    while (y < 100f) {
                        var x = 14f + (if (row % 2 == 1) 3.75f else 0f)
                        while (x < 86f) { drawCircle(dot, 0.9f, Offset(x, y)); x += 7.5f }
                        y += 7.5f / 1.6f; row++
                    }
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.1f), 1f to Color.Black.copy(alpha = 0.2f), startY = 72f, endY = 100f), Offset(14f, 72f), Size(72f, 28f))
                }
                drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(14f, 71.4f), Size(72f, 5.6f), CornerRadius(2.8f))
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0xFFAD4C46), 1f to Color(0xFF82312E), startY = 70.9f, endY = 76.5f),
                    Offset(14f, 70.9f), Size(72f, 5.6f), CornerRadius(2.8f),
                )
            }
            "chain" -> {
                drawPath(CHAIN, Color.Black.copy(alpha = 0.3f), style = Stroke(1.8f))
                translate(0f, -0.5f) {
                    drawPath(CHAIN, Color(0xFFD6AB3C), style = Stroke(1.6f))
                    drawPath(CHAIN, Color(0xFFF6DB8A), style = Stroke(0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f, 1.6f))))
                }
                drawPath(PENDANT, Brush.linearGradient(0f to Color(0xFFF0D27A), 1f to Color(0xFFC0922A), start = Offset(46f, 89f), end = Offset(54f, 98f)))
                drawCircle(Color.White.copy(alpha = 0.7f), 0.7f, Offset(49f, 91.6f))
            }
            else -> Unit
        }
    }

    private class SolidOrBrush(val color: Color = Color.Unspecified, val brush: Brush? = null)

    private fun DrawScope.fill(path: Path, f: SolidOrBrush) {
        if (f.brush != null) drawPath(path, f.brush) else drawPath(path, f.color)
    }

    private fun DrawScope.scarf(f: SolidOrBrush) {
        val shade = Color.Black.copy(alpha = 0.18f)
        // the band: an inset shadow along its lower edge
        fill(SCARF_BAND, f)
        clipPath(SCARF_BAND) {
            drawPath(SCARF_BAND, shade)
            translate(0f, -3f) { fill(SCARF_BAND, f) }
        }
        rotate(-10f, Offset(70f, 92.4f)) {
            drawPath(SCARF_TAIL, Color.Black.copy(alpha = 0.15f))
            translate(-0.6f, -0.6f) {
                fill(SCARF_TAIL, f)
                clipPath(SCARF_TAIL) {
                    drawPath(SCARF_TAIL, shade)
                    translate(0f, -3f) { fill(SCARF_TAIL, f) }
                    // fringe
                    for (i in 0..3) {
                        val x = 64.8f + i * 3.4f
                        drawLine(Color.Black.copy(alpha = 0.2f), Offset(x, 97.5f), Offset(x, 102f), 0.8f)
                    }
                }
            }
        }
        // the knot
        drawOval(Color.Black.copy(alpha = 0.1f), Offset(63.5f, 78f), Size(13f, 9f))
    }

    // ---- eyes worn over (or instead of) the eyes -------------------------------

    private val SHADE_LENS by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    -12f, -8f, 12f, 8f,
                    topLeftCornerRadius = CornerRadius(4.8f, 3.2f), topRightCornerRadius = CornerRadius(4.8f, 3.2f),
                    bottomRightCornerRadius = CornerRadius(12f, 8f), bottomLeftCornerRadius = CornerRadius(12f, 8f),
                ),
            )
        }
    }
    private val AVIATOR_LENS by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    -11f, -10f, 11f, 10f,
                    topLeftCornerRadius = CornerRadius(2.2f, 1.6f), topRightCornerRadius = CornerRadius(2.2f, 1.6f),
                    bottomRightCornerRadius = CornerRadius(11f, 16f * 10f / 17.6f), bottomLeftCornerRadius = CornerRadius(11f, 16f * 10f / 17.6f),
                ),
            )
        }
    }
    private val AVIATOR_FILL by lazy {
        cssLinear(165f, 0.05f to Color(0xFF22262C), 0.55f to Color(0xFF4B525C), 1f to Color(0xFF1F2227), box = Rect(-11f, -10f, 11f, 10f))
    }
    private val VISOR_FILL = Brush.horizontalGradient(
        0f to rgba(0, 200, 225, 0.92f), 0.55f to rgba(120, 90, 235, 0.9f), 1f to rgba(225, 60, 175, 0.92f),
        startX = 24f, endX = 76f,
    )
    private val SCANLINES = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.3f), 0.33f to Color.Black.copy(alpha = 0.3f), 0.33f to Color.Transparent, 1f to Color.Transparent,
        startY = 28.5f, endY = 31.5f, tileMode = TileMode.Repeated,
    )

    fun eyesOver(s: DrawScope, kind: String?, fx: Float, t: Float) = with(s) {
        translate(fx, 0f) {
            when (kind) {
                "hearts" -> for (x in floatArrayOf(35.5f, 64.5f)) {
                    val pulse = 1f + 0.06f * kotlin.math.sin(t * 6f)
                    translate(x, 40f) {
                        scale(pulse, pulse, Offset.Zero) {
                            drawPath(Shapes.heart, Color(0xFFD93A6A))
                            drawOval(Color.White.copy(alpha = 0.45f), Offset(-5.4f, -5.2f), Size(3.4f, 2.2f))
                        }
                    }
                }
                "stars" -> for (x in floatArrayOf(35.5f, 64.5f)) {
                    translate(x, 40f) {
                        rotate(6f * kotlin.math.sin(t * 2f), Offset.Zero) {
                            drawPath(Shapes.star, INK, style = Stroke(2f, join = StrokeJoin.Round))
                            drawPath(Shapes.star, Color(0xFFFFD23F))
                        }
                    }
                }
                "shades" -> {
                    val frame = Color(0xFF1E1A24)
                    drawRect(frame, Offset(39.2f, 35.5f), Size(21.6f, 3f))
                    for (x in floatArrayOf(35f, 65f)) {
                        translate(x, 40f) {
                            drawPath(SHADE_LENS, frame)
                            drawOval(Color.White.copy(alpha = 0.55f), Offset(-8.2f, -5.4f), Size(6.7f, 3.8f))
                        }
                    }
                }
                "glasses" -> {
                    val frame = Color(0xFF2D2A30)
                    for (x in floatArrayOf(35.5f, 64.5f)) {
                        drawCircle(rgba(200, 230, 255, 0.14f), 8.4f, Offset(x, 39.8f))
                        drawCircle(frame, 8.4f, Offset(x, 39.8f), style = Stroke(1.8f))
                        rotate(-35f, Offset(x - 3.4f, 34.6f)) {
                            drawOval(rgba(150, 190, 220, 0.6f), Offset(x - 5.95f, 33.7f), Size(5.1f, 1.8f))
                        }
                    }
                    drawRect(frame, Offset(43.8f, 37.4f), Size(12.4f, 1.6f))
                }
                "aviators" -> {
                    val gold = Color(0xFFC9A24A)
                    drawRect(gold, Offset(44f, 34.8f), Size(12f, 1.8f))
                    for (x in floatArrayOf(37f, 63f)) {
                        translate(x, 42f) {
                            drawPath(AVIATOR_LENS, AVIATOR_FILL)
                            drawPath(AVIATOR_LENS, gold, style = Stroke(1.4f))
                            rotate(-30f, Offset(-5.5f, -5.3f)) {
                                drawOval(Color.White.copy(alpha = 0.35f), Offset(-8f, -7f), Size(5f, 3.4f))
                            }
                        }
                    }
                }
                "visor" -> {
                    softOval(Offset(50f, 37f), 34f, 17f, rgba(60, 220, 240, 0.35f))
                    val r = CornerRadius(6f)
                    drawRoundRect(VISOR_FILL, Offset(24f, 28.5f), Size(52f, 17f), r)
                    drawRoundRect(SCANLINES, Offset(24f, 28.5f), Size(52f, 17f), r)
                    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(24f, 28.5f), Size(52f, 17f), r, style = Stroke(0.9f))
                    // the eye slits glow through
                    val slit = Color.White.copy(alpha = 0.85f)
                    drawRoundRect(slit, Offset(32.66f, 35.2f), Size(13.7f, 3f), CornerRadius(1.5f))
                    drawRoundRect(slit, Offset(53.66f, 35.2f), Size(13.7f, 3f), CornerRadius(1.5f))
                    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(27f, 30f), Size(18f, 1.6f), CornerRadius(0.8f))
                }
                else -> Unit
            }
        }
    }

    // ---- hats ------------------------------------------------------------------

    private val BEANIE by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    14f, -10f, 86f, 24f,
                    topLeftCornerRadius = CornerRadius(36f, 27.2f), topRightCornerRadius = CornerRadius(36f, 27.2f),
                    bottomRightCornerRadius = CornerRadius(8.6f, 6.8f), bottomLeftCornerRadius = CornerRadius(8.6f, 6.8f),
                ),
            )
        }
    }
    private val PARTY_CONE by lazy { poly(56.8f, -40f, 73.8f, 6f, 39.8f, 6f) }
    private val PARTY_STRIPES = Brush.linearGradient(
        0f to Color(0xFF8FB8D8), 0.5f to Color(0xFF8FB8D8), 0.5f to Color(0xFFF3A6A0), 1f to Color(0xFFF3A6A0),
        start = Offset(39.8f, -40f), end = Offset(39.8f + 9.6f, -40f + 9.6f), tileMode = TileMode.Repeated,
    )
    private val TOP_CROWN by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    28f, -38f, 72f, 4f,
                    topLeftCornerRadius = CornerRadius(2.6f), topRightCornerRadius = CornerRadius(2.6f),
                    bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
    }
    private val CROWN by lazy {
        poly(24f, 6f, 24f, -18f, 34.4f, -7.5f, 42.2f, -24f, 50f, -9f, 57.8f, -24f, 65.6f, -7.5f, 76f, -18f, 76f, 6f)
    }
    private val CAP_DOME by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    10f, -16f, 90f, 20f,
                    topLeftCornerRadius = CornerRadius(37f, 33f), topRightCornerRadius = CornerRadius(37f, 33f),
                    bottomRightCornerRadius = CornerRadius(4.4f, 2.8f), bottomLeftCornerRadius = CornerRadius(4.4f, 2.8f),
                ),
            )
        }
    }
    private val CAP_BRIM by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    2f, 16.4f, 98f, 27.9f,
                    topLeftCornerRadius = CornerRadius(28.8f, 4.6f), topRightCornerRadius = CornerRadius(28.8f, 4.6f),
                    bottomRightCornerRadius = CornerRadius(48f, 11.5f), bottomLeftCornerRadius = CornerRadius(48f, 11.5f),
                ),
            )
        }
    }
    private val BUCKET_CROWN by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    17.7f, -19f, 82.3f, 13f,
                    topLeftCornerRadius = CornerRadius(24.6f, 22.4f), topRightCornerRadius = CornerRadius(24.6f, 22.4f),
                    bottomRightCornerRadius = CornerRadius(6.5f, 3.8f), bottomLeftCornerRadius = CornerRadius(6.5f, 3.8f),
                ),
            )
        }
    }
    private val BERET by lazy {
        Path().apply {
            addRoundRect(
                RoundRect(
                    6f, -10f, 86f, 19f,
                    topLeftCornerRadius = CornerRadius(48f, 24.6f), topRightCornerRadius = CornerRadius(32f, 23.2f),
                    bottomRightCornerRadius = CornerRadius(36f, 10.2f), bottomLeftCornerRadius = CornerRadius(36f, 10.2f),
                ),
            )
        }
    }

    fun hat(s: DrawScope, kind: String?) = with(s) {
        when (kind) {
            "beanie" -> {
                val knit = Color(0xFF5B7FA8)
                drawPath(BEANIE, knit)
                clipPath(BEANIE) {
                    drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = 0.18f), 1f to Color.Transparent, center = Offset(38f, -4f), radius = 30f), Offset(14f, -10f), Size(72f, 34f))
                    // ribbed cuff
                    drawRect(Color(0xFF3F6189), Offset(14f, 17f), Size(72f, 7f))
                    var x = 16f
                    while (x < 86f) { drawLine(Color.Black.copy(alpha = 0.15f), Offset(x, 17.6f), Offset(x, 23.4f), 0.8f); x += 3.2f }
                    // knit rows
                    var y = -4f
                    while (y < 16f) { drawLine(Color.Black.copy(alpha = 0.07f), Offset(14f, y), Offset(86f, y), 0.8f); y += 4f }
                }
                drawOval(Color.Black.copy(alpha = 0.12f), Offset(40.65f, -16.5f), Size(18.7f, 13.6f))
                drawOval(Color(0xFFFFFAF2), Offset(40.65f, -17.5f), Size(18.7f, 13.6f))
                drawOval(Color.White, Offset(44f, -15.8f), Size(6f, 3.6f))
            }
            "party" -> rotate(14f, Offset(56.8f, -17f)) {
                drawPath(PARTY_CONE, PARTY_STRIPES)
                drawPath(PARTY_CONE, Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.15f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.15f), startX = 39.8f, endX = 73.8f))
                drawCircle(Color(0xFFF0C36D), 5.1f, Offset(56.8f, -39.5f))
                drawCircle(Color.White.copy(alpha = 0.5f), 1.4f, Offset(55f, -41f))
            }
            "top" -> {
                val hat = Color(0xFF2B1D17)
                drawPath(TOP_CROWN, hat)
                drawRect(Color(0xFFB84A36), Offset(28f, -4f), Size(44f, 8f))
                drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.04f), 0.25f to Color.White.copy(alpha = 0.16f), 0.45f to Color.Transparent, startX = 28f, endX = 72f), Offset(28f, -38f), Size(44f, 42f))
                drawOval(hat, Offset(13.9f, -1.04f), Size(72.2f, 9.24f))
                drawOval(Color.White.copy(alpha = 0.1f), Offset(18f, -0.6f), Size(64f, 3f), style = Stroke(0.7f))
            }
            "crown" -> {
                drawPath(CROWN, Brush.verticalGradient(0f to Color(0xFFFFD96A), 1f to Color(0xFFE0A81E), startY = -24f, endY = 6f))
                drawPath(CROWN, Color(0xFFB8860B).copy(alpha = 0.6f), style = Stroke(0.9f, join = StrokeJoin.Round))
                drawRect(Color.Black.copy(alpha = 0.1f), Offset(24f, 1f), Size(52f, 5f))
                drawCircle(Color(0xFFC0392B), 2.2f, Offset(50f, -1f))
                drawCircle(Color(0xFF5B7FA8), 1.7f, Offset(37f, 0f))
                drawCircle(Color(0xFF6F9A6E), 1.7f, Offset(63f, 0f))
                for (p in listOf(Offset(24f, -18f), Offset(42.2f, -24f), Offset(57.8f, -24f), Offset(76f, -18f))) {
                    drawCircle(Color(0xFFFFE9A8), 1.6f, p)
                }
            }
            "headband" -> clipPath(Shapes.body) {
                rotate(-3f, Offset(50f, 50f)) {
                    drawRect(Color(0xFFA8443C), Offset(-5f, 9f), Size(110f, 11f))
                    drawRect(Color(0xFFE6DFCC), Offset(-5f, 12.4f), Size(110f, 2f))
                    drawRect(Color.White.copy(alpha = 0.14f), Offset(-5f, 9f), Size(110f, 1.2f))
                    drawRect(Color.Black.copy(alpha = 0.15f), Offset(-5f, 19f), Size(110f, 1f))
                }
            }
            "cap" -> rotate(-5f, Offset(50f, 2f)) {
                drawPath(CAP_DOME, Brush.verticalGradient(0f to Color(0xFF3A4960), 1f to Color(0xFF2B3648), startY = -16f, endY = 20f))
                clipPath(CAP_DOME) {
                    val seam = Color.Black.copy(alpha = 0.22f)
                    for (x in floatArrayOf(33.2f, 49.2f, 65.2f)) drawRect(seam, Offset(x, -16f), Size(1.6f, 36f))
                    drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = 0.16f), 1f to Color.Transparent, center = Offset(36f, -6f), radius = 26f), Offset(10f, -16f), Size(80f, 36f))
                }
                drawCircle(Color(0xFF1F2733), 1.8f, Offset(50f, -15f))
                drawPath(CAP_BRIM, Color.Black.copy(alpha = 0.25f))
                translate(0f, -1f) {
                    drawPath(CAP_BRIM, Brush.verticalGradient(0f to Color(0xFF202A3A), 0.9f to Color(0xFF3A4960), startY = 16.4f, endY = 27.9f))
                }
            }
            "bucket" -> rotate(-2f, Offset(50f, 12f)) {
                drawOval(Color.Black.copy(alpha = 0.22f), Offset(1f, 3.4f), Size(98f, 20f))
                drawOval(Brush.verticalGradient(0f to Color(0xFF8C8767), 1f to Color(0xFF6A6649), startY = 2f, endY = 22f), Offset(1f, 2f), Size(98f, 20f))
                // stitching round the lower brim
                clipRect(0f, 12f, 100f, 24f) {
                    drawOval(Color.White.copy(alpha = 0.32f), Offset(5.9f, 4.4f), Size(88.2f, 15.2f), style = Stroke(0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 1.6f))))
                }
                drawPath(BUCKET_CROWN, Brush.horizontalGradient(0f to Color(0xFF7D7859), 0.4f to Color(0xFF9B9674), 1f to Color(0xFF7D7859), startX = 17.7f, endX = 82.3f))
                clipPath(BUCKET_CROWN) { drawRect(Color.Black.copy(alpha = 0.22f), Offset(17.7f, 3.4f), Size(64.6f, 1.9f)) }
            }
            "beret" -> rotate(-12f, Offset(46f, 4.5f)) {
                drawPath(BERET, Brush.radialGradient(0f to Color(0xFF96415A), 0.6f to Color(0xFF7A2F45), 1f to Color(0xFF62243A), center = Offset(38f, -2.75f), radius = 52f))
                drawRoundRect(Color(0xFF4F1D2F), Offset(12.4f, 13.78f), Size(67.2f, 5.8f), CornerRadius(2.9f))
                drawRoundRect(Color(0xFF62243A), Offset(49.2f, -14.9f), Size(7.2f, 6.4f), CornerRadius(3f, 3f))
            }
            else -> Unit
        }
    }
}
