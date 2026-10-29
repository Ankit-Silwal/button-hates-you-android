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

enum class PillKind(val bg: Color, val edge: Color, val fg: Color) {
    Main(Palette.Btn, Palette.BtnDark, Color.White),
    Alt(Palette.Mustard, Palette.MustardDark, Palette.Ink),
    House(Palette.Sage, Palette.SageDark, Palette.Ink),
    Ghost(Palette.Cream, Color(0xFFD8BC98), Palette.Ink),
    Night(Color(0xFF3A3F8A), Color(0xFF22265A), Color(0xFFF1ECFF)),
}

/**
 * The web's chunky pill: a coloured face over a darker rim that it presses
 * down into. [index] staggers its springy entrance in a row of pills.
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
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh), label = "press")
    val enter = remember { Animatable(if (index < 0 || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(60L * index)
            enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val rim = 4.dp
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier
            .graphicsLayer {
                val e = enter.value
                scaleX = 0.7f + 0.3f * e
                scaleY = 0.7f + 0.3f * e
                this.alpha = e.coerceIn(0f, 1f) * alpha
                translationY = (1f - e) * 24.dp.toPx()
            }
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .clickable(source, null, enabled = enabled, role = Role.Button) {
                fb.tick()
                onClick()
            }
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                val rimPx = rim.toPx()
                drawRoundRect(kind.edge, cornerRadius = r)
                drawRoundRect(kind.bg, topLeft = Offset(0f, press * rimPx), size = Size(size.width, size.height - rimPx), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(size.height * 0.3f, press * rimPx + 3.dp.toPx()), size = Size(size.width - size.height * 0.6f, 3.dp.toPx()), cornerRadius = CornerRadius(4f))
            }
            .padding(bottom = rim)
            .offset(y = (press * 4).dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.height(height - rim), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                AppIcon(icon, 20.dp, tint = kind.fg)
                Spacer(Modifier.width(6.dp))
            }
            IconText(text, color = kind.fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row of pills that shares the width (they wrap their text to fit). */
@Composable
fun PillRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)
}

/* --------------------------------- chips --------------------------------- */

/** A round frosted icon button for the top bar, with an optional badge. */
@Composable
fun IconChip(icon: String, label: String, onClick: () -> Unit, badge: String? = null, hot: Boolean = false, modifier: Modifier = Modifier) {
    val fb = LocalFeedback.current
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        val pulse = if (hot) rememberPulse() else 0f
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer { scaleX = 1f + pulse * 0.08f; scaleY = 1f + pulse * 0.08f }
                .shadow(4.dp, CircleShape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                .background(if (hot) Color(0xFFFFE7B0) else Palette.Cream.copy(alpha = 0.92f), CircleShape)
                .border(1.dp, Color.White, CircleShape)
                .clickable(role = Role.Button, onClickLabel = label) {
                    fb.tick()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, 24.dp)
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(18.dp, 18.dp)
                    .background(Palette.Alert, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconText(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
fun Meter(value: Float, color: Color, modifier: Modifier = Modifier, track: Color = Color(0x22000000), height: Dp = 8.dp) {
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
    v < 25 -> Palette.Alert
    v < 50 -> Palette.Mustard
    else -> Color(0xFF7CC08A)
}

/** The frosted meter chip under a room: icon, label, bar and value, then a note. */
@Composable
fun NeedHud(icon: String, label: String, value: Float, note: String?, dark: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (dark) Color(0xC7262A52) else Palette.Cream.copy(alpha = 0.85f)
    val fg = if (dark) Palette.NightInk else Palette.Ink
    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(icon, 17.dp)
            IconText(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Meter(value, needColor(value), Modifier.weight(1f), track = if (dark) Color(0x33FFFFFF) else Color(0x22000000), height = 9.dp)
            IconText("${value.toInt()}", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        if (!note.isNullOrBlank()) {
            IconText(note, color = if (dark) Color(0xFFD8D4FF) else Palette.InkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp), maxLines = 2)
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
    val pop = remember(line.n) { Animatable(if (reduce) 1f else 0.6f) }
    LaunchedEffect(line.n) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
    val bg = if (dark) Color(0xD9262A52) else Color(0xEBFFFBF4)
    val fg = if (dark) Palette.NightInk else Palette.Ink
    Column(
        modifier
            .widthIn(max = 340.dp)
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
                .background(bg, RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.8f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            IconText(line.text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .offset(y = (-7).dp)
                .size(14.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(bg, RoundedCornerShape(bottomEnd = 4.dp)),
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
            .shadow(18.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Palette.Cream, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp, 5.dp).background(Color(0x334A3428), RoundedCornerShape(50)))
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
                Box(Modifier.size(34.dp).background(Palette.Wall, CircleShape), contentAlignment = Alignment.Center) {
                    IconText("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
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
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Tab) { fb.tick(); onSelect(id) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(if (on) Palette.Ink else Palette.Wall, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconText(label, color = if (on) Palette.Cream else Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            .background(Color(0xFFFFF3DD))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconText(
                tag.uppercase(),
                color = Color(0xFFFFE9B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.background(Color(0xFF3B2A20), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconText(name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!note.isNullOrBlank()) IconText(note, fontSize = 12.sp, color = Palette.InkSoft, maxLines = 2)
        extra?.invoke()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = buttons)
    }
}
