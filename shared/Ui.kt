package com.unoxidized.shared
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Ln = Color(0xFF2A2A2A); val Dim = Color(0xFF8C8C8C); val Ok = Color(0xFF3DDC84); val Bad = Color(0xFFFF6B6B); val Warn = Color(0xFFFFC857)
val WhiteGrad = Brush.horizontalGradient(listOf(Color.White, Color(0xFFB8B8B8)))
object UiFlags { var reduceMotion by mutableStateOf(false); var haptics by mutableStateOf(true) }
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { { } }

fun Modifier.press(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }; val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.97f else 1f, tween(90), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable fun TechBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val ph = t.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "ph")
    Box(Modifier.fillMaxSize().background(Color.Black).drawBehind {
        val p = if (UiFlags.reduceMotion) 0.4f else ph.value; val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(Color(0x26FFFFFF), Color.Transparent), center = Offset(w * (0.2f + 0.6f * p), h * 0.12f), radius = w * 0.9f))
        drawRect(Brush.radialGradient(listOf(Color(0x14FFFFFF), Color.Transparent), center = Offset(w * (0.9f - 0.7f * p), h * 0.85f), radius = w * 0.8f))
        val g = 56.dp.toPx(); var x = 0f; while (x < w) { drawLine(Color(0x0AFFFFFF), Offset(x, 0f), Offset(x, h)); x += g }
        var y = 0f; while (y < h) { drawLine(Color(0x0AFFFFFF), Offset(0f, y), Offset(w, y)); y += g }
    }, content = content)
}

@Composable fun ToastRoot(content: @Composable () -> Unit) {
    var msg by remember { mutableStateOf("") }; var id by remember { mutableStateOf(0) }; var show by remember { mutableStateOf(false) }
    LaunchedEffect(id) { if (id > 0) { show = true; delay(1800); show = false } }
    CompositionLocalProvider(LocalToast provides { m: String -> msg = m; id++ }) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(show, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp), enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it }, exit = fadeOut(tween(200))) {
                Text(msg, Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFFF2F2F2)).padding(horizontal = 20.dp, vertical = 12.dp), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable fun <T> PageSwitch(page: T, content: @Composable (T) -> Unit) {
    val d = if (UiFlags.reduceMotion) 0 else 260
    AnimatedContent(page, transitionSpec = { (fadeIn(tween(d)) + slideInVertically(tween(d)) { it / 24 }) togetherWith fadeOut(tween(d / 2)) }, label = "pg") { content(it) }
}

@Composable fun Glass(m: Modifier = Modifier, c: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(22.dp)
    Column(m.fillMaxWidth().clip(sh).background(Brush.verticalGradient(listOf(Color(0xE6181818), Color(0xE60B0B0B)))).border(1.dp, Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF1A1A1A))), sh).padding(18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = c)
}
@Composable fun H(t: String, s: String) = Column { Text(t, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(s, color = Dim, fontSize = 13.sp) }
@Composable fun Cap(t: String) = Text(t.uppercase(), color = Dim, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
@Composable fun Btn(t: String, primary: Boolean = true, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val hf = LocalHapticFeedback.current; val sh = RoundedCornerShape(16.dp)
    val src = remember { MutableInteractionSource() }; val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.97f else 1f, tween(90), label = "btn")
    Box(Modifier.fillMaxWidth().height(54.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.45f }.clip(sh)
        .then(if (primary) Modifier.background(WhiteGrad) else Modifier.background(Color(0xFF111111)).border(1.dp, Ln, sh))
        .clickable(interactionSource = src, indication = null, enabled = enabled && !loading, onClick = { if (UiFlags.haptics) hf.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }), Alignment.Center) {
        if (loading) CircularProgressIndicator(color = if (primary) Color.Black else Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text(t, color = if (primary) Color.Black else Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
    }
}
@Composable fun MiniBtn(t: String, onClick: () -> Unit) = Text(t, Modifier.press(onClick).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
@Composable fun CopyChip(text: String) { val cb = LocalClipboardManager.current; val toast = LocalToast.current; MiniBtn("COPY") { cb.setText(AnnotatedString(text)); toast("Copied") } }
@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Ln, cursorColor = Color.White)
@Composable fun LogoImg(p: Painter, sz: Int) = Image(p, null, Modifier.size(sz.dp).clip(CircleShape).border(1.dp, Color(0xFF555555), CircleShape))
@Composable fun Chip(t: String, color: Color) = Text(t, Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
@Composable fun Watermark() { val c = LocalContext.current
    Text("Made by @UnoxidizedX  ·  Telegram", Modifier.clickable { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/UnoxidizedX")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }.padding(6.dp), color = Dim, fontSize = 11.sp, letterSpacing = 1.sp) }

@Composable fun Gauge(progress: Float, number: Int, label: String, color: Color, size: Int = 160) {
    val pr by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (UiFlags.reduceMotion) 0 else 900), label = "g")
    val n by animateIntAsState(number, tween(if (UiFlags.reduceMotion) 0 else 900), label = "n")
    Box(Modifier.size(size.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 12.dp.toPx(); val pad = sw / 2 + 2f; val a = Size(this.size.width - 2 * pad, this.size.height - 2 * pad)
            drawArc(Color(0xFF1E1E1E), 135f, 270f, false, Offset(pad, pad), a, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * pr, false, Offset(pad, pad), a, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$n", fontSize = (size / 3).sp, fontWeight = FontWeight.Black); Text(label, color = Dim, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
    }
}
@Composable fun Spark(data: List<Float>, max: Float, color: Color, h: Int = 54) {
    Canvas(Modifier.fillMaxWidth().height(h.dp)) {
        if (data.size < 2) return@Canvas
        val st = size.width / (data.size - 1); val p = Path()
        data.forEachIndexed { i, v -> val x = i * st; val y = size.height - (v / max).coerceIn(0f, 1f) * size.height; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        drawPath(p, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
@Composable fun Skel(m: Modifier) {
    val t = rememberInfiniteTransition(label = "sk"); val x = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "x")
    Box(m.clip(RoundedCornerShape(8.dp)).drawBehind { drawRect(Brush.horizontalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF2E2E2E), Color(0xFF1A1A1A)), startX = x.value * 700f - 250f, endX = x.value * 700f + 250f)) })
}
@Composable fun NavBar(items: List<Pair<String, String>>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xF2080808)).padding(horizontal = 8.dp, vertical = 8.dp), Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (ic, l) ->
            val on = i == sel; val bg by animateColorAsState(if (on) Color(0xFF262626) else Color.Transparent, label = "nb")
            Column(Modifier.weight(1f).press { onSel(i) }.clip(RoundedCornerShape(16.dp)).background(bg).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ic, fontSize = 20.sp); Text(l, fontSize = 9.sp, color = if (on) Color.White else Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}
