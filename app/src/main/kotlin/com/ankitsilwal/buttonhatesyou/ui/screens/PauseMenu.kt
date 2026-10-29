package com.ankitsilwal.buttonhatesyou.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitsilwal.buttonhatesyou.economy.GameInfo
import com.ankitsilwal.buttonhatesyou.ui.art.GameIconTile
import com.ankitsilwal.buttonhatesyou.ui.art.gameTint
import com.ankitsilwal.buttonhatesyou.ui.common.LocalFeedback
import com.ankitsilwal.buttonhatesyou.ui.common.fmt
import com.ankitsilwal.buttonhatesyou.ui.theme.LocalReduceMotion
import com.ankitsilwal.buttonhatesyou.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the pause menu is asking about: nothing yet, or confirming a restart or a quit. */
enum class MenuAsk { None, Restart, Quit }

private val Panel = Color(0xF21B1626)
private val PanelEdge = Color(0x33FFFFFF)
private val Soft = Color(0xFFB9B0CC)
private val Danger = Color(0xFFE5574A)

/**
 * The in-game pause screen: the run blurred behind a dark scrim, a glass panel
 * with the game, the score so far and the best, and Resume / Restart / Quit.
 * Restart and Quit ask once more in the same panel before they act.
 */
@Composable
fun PauseMenu(
    info: GameInfo,
    score: Int?,
    best: Int,
    ask: MenuAsk,
    onAsk: (MenuAsk) -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val e = enter.value
    val tint = gameTint(info.id)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = e.coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(Color(0x99100A18), Color(0xE0100A18))))
            // a tap on the scrim backs out of a question, or resumes
            .clickable(remember { MutableInteractionSource() }, null) { if (ask != MenuAsk.None) onAsk(MenuAsk.None) else onResume() }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - e) * 60.dp.toPx()
                    scaleX = 0.92f + 0.08f * e
                    scaleY = 0.92f + 0.08f * e
                }
                .shadow(32.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(30.dp))
                .background(Panel)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), Color.Transparent), endY = 420f))
                .border(1.dp, PanelEdge, RoundedCornerShape(30.dp))
                // swallow taps so they don't reach the scrim
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = ask,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.94f)) togetherWith fadeOut(tween(120)) },
                label = "pause-ask",
            ) { now ->
                when (now) {
                    MenuAsk.None -> MainPanel(info, score, best, onResume, { onAsk(MenuAsk.Restart) }, { onAsk(MenuAsk.Quit) })
                    MenuAsk.Restart -> ConfirmPanel(
                        icon = { restartIcon(it) },
                        tint = Palette.Mustard,
                        title = "Restart the run?",
                        body = "This run ends now and pays nothing. A fresh one starts from zero.",
                        yes = "Restart",
                        onYes = onRestart,
                        onNo = { onAsk(MenuAsk.None) },
                    )
                    MenuAsk.Quit -> ConfirmPanel(
                        icon = { quitIcon(it) },
                        tint = Danger,
                        title = "Quit the run?",
                        body = "A run you quit pays nothing. He will be delighted.",
                        yes = "Quit",
                        onYes = onQuit,
                        onNo = { onAsk(MenuAsk.None) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPanel(info: GameInfo, score: Int?, best: Int, onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GameIconTile(info.id, Modifier.size(56.dp), corner = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("PAUSED", color = Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                Text(info.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("THIS RUN", score?.let { fmt(it) } ?: "–", info.unit, Modifier.weight(1f))
            Stat("BEST", if (best > 0) fmt(best) else "–", info.unit, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        MenuButton("Resume", "Pick up where you left off", { playIcon(it) }, Palette.Btn, primary = true, index = 0, onClick = onResume)
        Spacer(Modifier.height(10.dp))
        MenuButton("Restart", "Start this game over", { restartIcon(it) }, Palette.Mustard, index = 1, onClick = onRestart)
        Spacer(Modifier.height(10.dp))
        MenuButton("Quit", "Back to the Games room", { quitIcon(it) }, Danger, index = 2, onClick = onQuit)
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(
        modifier
            .background(Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit != null && value != "–") {
                Text(" $unit", color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun ConfirmPanel(icon: DrawScope.(Color) -> Unit, tint: Color, title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).background(tint.copy(alpha = 0.18f), CircleShape).border(1.5.dp, tint.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(34.dp)) { icon(tint) }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Soft, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlatButton("Cancel", Color(0x1FFFFFFF), Color.White, Modifier.weight(1f), onNo)
            FlatButton(yes, tint, if (tint == Palette.Mustard) Palette.Ink else Color.White, Modifier.weight(1f), onYes)
        }
    }
}

/** A wide menu row: a round icon badge, a label and a hint, pressed down a little on touch. */
@Composable
private fun MenuButton(
    label: String,
    hint: String,
    icon: DrawScope.(Color) -> Unit,
    tint: Color,
    primary: Boolean = false,
    index: Int,
    onClick: () -> Unit,
) {
    val fb = LocalFeedback.current
    val reduce = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh), label = "menu-press")
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(70L * index + 80L)
            enter.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val shape = RoundedCornerShape(20.dp)
    val face = if (primary) Brush.horizontalGradient(listOf(tint, lerp(tint, Color.Black, 0.12f))) else Brush.horizontalGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF)))
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val v = enter.value
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 18.dp.toPx()
                val s = 1f - 0.03f * press
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(face)
            .border(1.dp, if (primary) Color(0x40FFFFFF) else Color(0x1FFFFFFF), shape)
            .clickable(source, null, role = Role.Button, onClickLabel = label) { fb.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(if (primary) Color(0x33FFFFFF) else tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) { icon(if (primary) Color.White else tint) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = if (primary) Color(0xD9FFFFFF) else Soft, fontSize = 12.sp)
        }
        Canvas(Modifier.size(14.dp)) { chevron(if (primary) Color.White else Soft) }
    }
}

@Composable
private fun FlatButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val fb = LocalFeedback.current
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(role = Role.Button) { fb.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** The round pause button over a running game, with drawn bars instead of an emoji. */
@Composable
fun PauseChip(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val fb = LocalFeedback.current
    Box(
        modifier
            .size(size)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color(0xCC1B1626), CircleShape)
            .border(1.dp, Color(0x40FFFFFF), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Pause") { fb.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.4f)) { pauseIcon(Color.White) }
    }
}

/* ------------------------------- icons ------------------------------- */
// Drawn on the scope's square, so they stay crisp at any size.

private fun DrawScope.u() = minOf(size.width, size.height) / 24f

private fun DrawScope.pauseIcon(c: Color) {
    val u = u()
    drawRoundRect(c, Offset(5f * u, 3f * u), androidx.compose.ui.geometry.Size(5f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
    drawRoundRect(c, Offset(14f * u, 3f * u), androidx.compose.ui.geometry.Size(5f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
}

private fun DrawScope.playIcon(c: Color) {
    val u = u()
    val p = Path().apply { moveTo(7f * u, 4f * u); lineTo(20f * u, 12f * u); lineTo(7f * u, 20f * u); close() }
    drawPath(p, c)
    drawPath(p, c, style = Stroke(2.5f * u, join = StrokeJoin.Round))
}

/** A circular arrow, three quarters round, with its head at the top. */
private fun DrawScope.restartIcon(c: Color) {
    val u = u()
    val stroke = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = 8f * u
    val cx = 12f * u
    val cy = 13f * u
    // a gap at the top right, where the arrow head sits pointing back up the turn
    drawArc(c, -40f, 290f, false, Offset(cx - r, cy - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = stroke)
    val a = (-40.0 * PI / 180.0)
    val at = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    val dir = Offset(sin(a).toFloat(), -cos(a).toFloat()) // tangent, against the sweep
    val out = Offset(cos(a).toFloat(), sin(a).toFloat()) // away from the centre
    val head = Path().apply {
        moveTo(at.x + dir.x * 4.5f * u, at.y + dir.y * 4.5f * u)
        lineTo(at.x + out.x * 3.8f * u, at.y + out.y * 3.8f * u)
        lineTo(at.x - out.x * 3.8f * u, at.y - out.y * 3.8f * u)
        close()
    }
    drawPath(head, c)
}

/** A door frame with an arrow leaving it. */
private fun DrawScope.quitIcon(c: Color) {
    val u = u()
    val stroke = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply { moveTo(13f * u, 3.5f * u); lineTo(5f * u, 3.5f * u); lineTo(5f * u, 20.5f * u); lineTo(13f * u, 20.5f * u) }, c, style = stroke)
    drawLine(c, Offset(10f * u, 12f * u), Offset(21f * u, 12f * u), stroke.width, StrokeCap.Round)
    drawPath(Path().apply { moveTo(17f * u, 8f * u); lineTo(21f * u, 12f * u); lineTo(17f * u, 16f * u) }, c, style = stroke)
}

private fun DrawScope.chevron(c: Color) {
    val u = u()
    drawPath(Path().apply { moveTo(8f * u, 4f * u); lineTo(16f * u, 12f * u); lineTo(8f * u, 20f * u) }, c, style = Stroke(3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
