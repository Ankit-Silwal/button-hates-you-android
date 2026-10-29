package com.ankitsilwal.buttonhatesyou.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.economy.Price
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import com.ankitsilwal.buttonhatesyou.ui.theme.Radius
import java.text.NumberFormat
import java.util.Locale
import com.ankitsilwal.buttonhatesyou.ui.art.AppIcon
import com.ankitsilwal.buttonhatesyou.ui.art.IconText

fun fmt(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

fun priceText(p: Price?): String = when {
    p == null -> "Free"
    p.diamonds > 0 -> "${p.diamonds} 💎"
    else -> "${fmt(p.coins)} 🪙"
}

/* --------------------------------- pills --------------------------------- */

// Main is the one filled call to action; Alt and House are the neutral
// secondary buttons; Ghost is the quiet one for Cancel and Back.
enum class PillKind(val bg: Color, val edge: Color, val fg: Color) {
    Main(Palette.Btn, Color.Transparent, Color.White),
    Alt(Palette.Surface.copy(alpha = 0.96f), Palette.Hairline, Palette.Ink),
    House(Palette.Surface.copy(alpha = 0.96f), Palette.Hairline, Palette.Ink),
    Ghost(Palette.SurfaceMuted, Color.Transparent, Palette.Ink),
    Night(Color(0xFF3A3F8A), Color(0x33FFFFFF), Color(0xFFF1ECFF)),
}

/**
 * A flat button with a soft shadow that dips slightly when pressed.
 * [index] staggers its fade-in in a row of buttons.
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    kind: PillKind = PillKind.Main,
    enabled: Boolean = true,
    index: Int = -1,
    height: Dp = 52.dp,
) {
    val fb = LocalFeedback.current
    val reduce = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(90), label = "press")
    val enter = remember { Animatable(if (index < 0 || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(40L * index)
            enter.animateTo(1f, tween(220))
        }
    }
    val shape = RoundedCornerShape(Radius.Control)
    val alpha = if (enabled) 1f else 0.45f
    val lifted = kind != PillKind.Ghost
    Box(
        modifier
            .graphicsLayer {
                val e = enter.value
                this.alpha = e.coerceIn(0f, 1f) * alpha
                translationY = (1f - e) * 8.dp.toPx()
                val s = 1f - 0.025f * press
                scaleX = s; scaleY = s
            }
            .heightIn(min = height)
            .then(if (lifted) Modifier.shadow((3f - 2f * press).dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow) else Modifier)
            .clip(shape)
            .background(kind.bg)
            .border(1.dp, kind.edge, shape)
            .clickable(source, null, enabled = enabled, role = Role.Button) {
                fb.tick()
                onClick()
            }
            .drawBehind { if (press > 0f) drawRect(Color.Black.copy(alpha = 0.06f * press)) }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.height(height), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                AppIcon(icon, 18.dp, tint = kind.fg)
                Spacer(Modifier.width(8.dp))
            }
            IconText(text, color = kind.fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row of pills that shares the width (they wrap their text to fit). */
@Composable
fun PillRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)
}

/* --------------------------------- chips --------------------------------- */

/** A square frosted icon button for the top bar, with an optional badge. */
@Composable
fun IconChip(icon: String, label: String, onClick: () -> Unit, badge: String? = null, hot: Boolean = false, modifier: Modifier = Modifier) {
    val fb = LocalFeedback.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .shadow(2.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                .background(Palette.Surface.copy(alpha = 0.94f), shape)
                .border(1.dp, if (hot) Palette.Btn.copy(alpha = 0.45f) else Palette.Hairline, shape)
                .clip(shape)
                .clickable(role = Role.Button, onClickLabel = label) {
                    fb.tick()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, 20.dp, tint = Palette.Ink)
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .defaultMinSize(16.dp, 16.dp)
                    .background(Palette.Btn, CircleShape)
                    .border(1.5.dp, Palette.Surface, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconText(badge, color = Color.White, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun rememberPulse(): Float {
    val reduce = LocalReduceMotion.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            a.animateTo(1f, tween(600))
            a.animateTo(0f, tween(600))
            kotlinx.coroutines.delay(900)
        }
    }
    return a.value
}

/** A small rounded meter. */
@Composable
fun Meter(value: Float, color: Color, modifier: Modifier = Modifier, track: Color = Color(0x144A3428), height: Dp = 6.dp) {
    val v by animateFloatAsState((value / 100f).coerceIn(0f, 1f), tween(500), label = "meter")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
            .drawBehind {
                drawRoundRect(color, size = Size(size.width * v, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
    )
}

fun needColor(v: Float): Color = when {
    v < 25 -> Palette.Bad
    v < 50 -> Palette.Warn
    else -> Palette.Good
}

/** The frosted meter chip under a room: icon, label, bar and value, then a note. */
@Composable
fun NeedHud(icon: String, label: String, value: Float, note: String?, dark: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (dark) Color(0xC7262A52) else Palette.Surface.copy(alpha = 0.9f)
    val fg = if (dark) Palette.NightInk else Palette.Ink
    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(12.dp))
                .border(1.dp, if (dark) Color(0x1FFFFFFF) else Palette.Hairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(icon, 16.dp, tint = fg)
            IconText(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Meter(value, needColor(value), Modifier.weight(1f), track = if (dark) Color(0x33FFFFFF) else Color(0x144A3428), height = 6.dp)
            IconText("${value.toInt()}", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        if (!note.isNullOrBlank()) {
            IconText(note, color = if (dark) Color(0xFFD8D4FF) else Palette.InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Normal, modifier = Modifier.padding(horizontal = 4.dp), maxLines = 2)
        }
    }
}

/* ----------------------------- speech bubble ----------------------------- */

/** What he says; [n] changes on every new line so the bubble pops again. */
data class Line(val text: String = "", val n: Int = 0)

@Composable
fun SpeechBubble(line: Line, modifier: Modifier = Modifier, dark: Boolean = false) {
    if (line.text.isBlank()) return
    val reduce = LocalReduceMotion.current
    val pop = remember(line.n) { Animatable(if (reduce) 1f else 0.85f) }
    LaunchedEffect(line.n) { pop.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
    val bg = if (dark) Color(0xE6262A52) else Palette.Surface.copy(alpha = 0.96f)
    val fg = if (dark) Palette.NightInk else Palette.Ink
    Column(
        modifier
            .widthIn(max = 340.dp)
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                alpha = ((pop.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                .background(bg, RoundedCornerShape(16.dp))
                .border(1.dp, if (dark) Color(0x1FFFFFFF) else Palette.Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            IconText(line.text, color = fg, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .offset(y = (-6).dp)
                .size(12.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(bg, RoundedCornerShape(bottomEnd = 3.dp)),
        )
    }
}

/* -------------------------------- sheets --------------------------------- */

/** The bottom panel the shops live in: a title, a close button, then [content]. The room stays visible above it. */
@Composable
fun PanelSheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val fb = LocalFeedback.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = Radius.Sheet, topEnd = Radius.Sheet), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .background(Palette.Surface, RoundedCornerShape(topStart = Radius.Sheet, topEnd = Radius.Sheet)),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).background(Color(0x264A3428), RoundedCornerShape(50)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconText(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Close") { fb.tick(); onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).background(Palette.SurfaceMuted, CircleShape), contentAlignment = Alignment.Center) {
                    IconText("✕", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.InkSoft)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f, fill = true)) { content() }
    }
}

/** A tab strip of rounded chips. */
@Composable
fun ChipTabs(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val fb = LocalFeedback.current
    androidx.compose.foundation.lazy.LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tabs.size) { i ->
            val (id, label) = tabs[i]
            val on = id == selected
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab) { fb.tick(); onSelect(id) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(if (on) Palette.Ink else Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.dp, if (on) Palette.Ink else Palette.Hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconText(label, color = if (on) Palette.Surface else Palette.InkSoft, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
    }
}

/** The bar at the bottom of a shop sheet while something is being tried. */
@Composable
fun BuyBar(tag: String, name: String, note: String?, modifier: Modifier = Modifier, extra: @Composable (() -> Unit)? = null, buttons: @Composable RowScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.SurfaceMuted)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconText(
                tag.uppercase(),
                color = Palette.Surface,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.background(Palette.Ink, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconText(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!note.isNullOrBlank()) IconText(note, fontSize = 12.sp, color = Palette.InkSoft, maxLines = 2)
        extra?.invoke()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = buttons)
    }
}
