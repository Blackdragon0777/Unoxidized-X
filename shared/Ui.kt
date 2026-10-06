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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.scaleIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import android.content.Context
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.roundToInt

val Acc = Color(0xFF6D28D9); val Glow = Color(0xFFA855F7); val Txt = Color(0xFFF5F3FF); val Bg0 = Color(0xFF07070A); val Bg1 = Color(0xFF111116)
val Ln = Color(0xFF2A2140); val Dim = Color(0xFF8B8798); val Ok = Color(0xFF3DDC84); val Bad = Color(0xFFFF6B6B); val Warn = Color(0xFFFFC857)
val WhiteGrad = Brush.horizontalGradient(listOf(Glow, Acc))
object UiFlags { var reduceMotion by mutableStateOf(false); var haptics by mutableStateOf(true) }
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { { } }

fun Modifier.press(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }; val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.97f else 1f, tween(90), label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable fun TechBackground(charPainter: Painter? = null, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val ph = t.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "ph")
    Box(Modifier.fillMaxSize().background(Bg0).drawBehind {
        val p = if (UiFlags.reduceMotion) 0.4f else ph.value; val w = size.width; val h = size.height
        drawRect(Brush.radialGradient(listOf(Color(0x44A855F7), Color.Transparent), center = Offset(w * (0.15f + 0.5f * p), h * 0.1f), radius = w * 0.95f))
        drawRect(Brush.radialGradient(listOf(Color(0x336D28D9), Color.Transparent), center = Offset(w * (0.95f - 0.5f * p), h * 0.9f), radius = w * 0.85f))
        val g = 56.dp.toPx(); var x = 0f; while (x < w) { drawLine(Color(0x0CA855F7), Offset(x, 0f), Offset(x, h)); x += g }
        var y = 0f; while (y < h) { drawLine(Color(0x0CA855F7), Offset(0f, y), Offset(w, y)); y += g }
    }) {
        if (charPainter != null) Image(charPainter, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.46f }
            .drawWithContent {
                drawContent()
                drawRect(Brush.horizontalGradient(listOf(Bg0, Color(0x8807070A), Color.Transparent), startX = 0f, endX = size.width * 0.62f))
                drawRect(Brush.verticalGradient(listOf(Color(0xAA07070A), Color.Transparent, Color(0xCC07070A))))
            }, alignment = Alignment.CenterEnd, contentScale = ContentScale.Crop)
        content()
    }
}

@Composable fun ToastRoot(content: @Composable () -> Unit) {
    var msg by remember { mutableStateOf("") }; var id by remember { mutableStateOf(0) }; var show by remember { mutableStateOf(false) }
    LaunchedEffect(id) { if (id > 0) { show = true; delay(1800); show = false } }
    CompositionLocalProvider(LocalToast provides { m: String -> msg = m; id++ }) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(show, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp), enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it }, exit = fadeOut(tween(200))) {
                Text(msg, Modifier.clip(RoundedCornerShape(30.dp)).background(Acc).padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    Column(m.fillMaxWidth().clip(sh).background(Brush.verticalGradient(listOf(Color(0xEA14141C), Color(0xEA0B0B10)))).border(1.dp, Brush.verticalGradient(listOf(Color(0x996D28D9), Color(0x33A855F7))), sh).padding(18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = c)
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
        if (loading) CircularProgressIndicator(color = if (primary) Color.White else Glow, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text(t, color = if (primary) Color.White else Glow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
    }
}
@Composable fun MiniBtn(t: String, onClick: () -> Unit) = Text(t, Modifier.press(onClick).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
@Composable fun CopyChip(text: String) { val cb = LocalClipboardManager.current; val toast = LocalToast.current; MiniBtn("COPY") { cb.setText(AnnotatedString(text)); toast("Copied") } }
@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Glow, unfocusedBorderColor = Ln, cursorColor = Glow)
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
    Row(Modifier.fillMaxWidth().background(Color(0xF207070A)).padding(horizontal = 8.dp, vertical = 8.dp), Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (ic, l) ->
            val on = i == sel; val bg by animateColorAsState(if (on) Color(0x446D28D9) else Color.Transparent, label = "nb")
            Column(Modifier.weight(1f).press { onSel(i) }.clip(RoundedCornerShape(16.dp)).background(bg).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ic, fontSize = 20.sp); Text(l, fontSize = 9.sp, color = if (on) Glow else Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}

fun Modifier.swipeDelete(onDelete: () -> Unit): Modifier = composed {
    var dx by remember { mutableStateOf(0f) }; val x by animateFloatAsState(dx, tween(if (dx == 0f) 160 else 40), label = "sw")
    this.offset { IntOffset(x.roundToInt(), 0) }.graphicsLayer { alpha = 1f - (abs(x) / 1200f).coerceIn(0f, 0.5f) }
        .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { if (abs(dx) > 240f) onDelete(); dx = 0f }, onDragCancel = { dx = 0f }) { change, d -> change.consume(); dx += d } }
}
fun Modifier.swipeTabs(onRight: () -> Unit, onLeft: () -> Unit): Modifier = composed {
    var tot by remember { mutableStateOf(0f) }; val r = rememberUpdatedState(onRight); val l = rememberUpdatedState(onLeft)
    this.pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { if (tot > 150f) r.value() else if (tot < -150f) l.value(); tot = 0f }, onDragCancel = { tot = 0f }) { _, d -> tot += d } }
}
@Composable fun PullRefresh(onRefresh: suspend () -> Unit, content: @Composable () -> Unit) {
    var pull by remember { mutableStateOf(0f) }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    val th = with(LocalDensity.current) { 84.dp.toPx() }
    val conn = remember { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (pull > 0f && available.y < 0f) { val d = maxOf(available.y, -pull); pull += d; return Offset(0f, d) }
            return Offset.Zero }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!busy && source == NestedScrollSource.Drag && available.y > 0f) { pull = (pull + available.y * 0.5f).coerceAtMost(th * 1.5f); return Offset(0f, available.y) }
            return Offset.Zero }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (pull >= th && !busy) { busy = true; scope.launch { try { onRefresh() } finally { busy = false } } }
            pull = 0f; return Velocity.Zero }
    } }
    Box(Modifier.fillMaxSize().nestedScroll(conn)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = if (busy) th * 0.55f else pull * 0.55f }) { content() }
        if (pull > 0f || busy) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(32.dp).clip(CircleShape).background(Color(0xFF1E1E1E)), Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Glow, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Canvas(Modifier.size(18.dp)) { drawArc(Glow, -90f, 360f * (pull / th).coerceAtMost(1f), false, style = Stroke(4f, cap = StrokeCap.Round)) } }
    }
}

@Composable fun Loader(style: Int, progress: Float) {
    val t = rememberInfiniteTransition(label = "ld"); val rot = t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r")
    if (style == 1) { val p by animateFloatAsState(progress, tween(400), label = "pg")
        Box(Modifier.width(240.dp).height(8.dp).clip(CircleShape).background(Color(0xFF1E1E1E)).border(1.dp, Color(0xFF444444), CircleShape)) { Box(Modifier.fillMaxWidth(p.coerceIn(0.03f, 1f)).fillMaxHeight().background(WhiteGrad)) }
    } else Canvas(Modifier.size(if (style == 2) 52.dp else 44.dp)) {
        val n = if (style == 2) 16 else 12; val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
        for (i in 0 until n) {
            val ang = i * 360f / n; val lead = (((ang - rot.value) % 360f) + 360f) % 360f; val a = 1f - lead / 360f
            if (style == 2) { val rad = Math.toRadians(ang.toDouble()); val x = kotlin.math.cos(rad).toFloat(); val y = kotlin.math.sin(rad).toFloat()
                drawLine(Glow.copy(alpha = a), Offset(c.x + x * r * 0.62f, c.y + y * r * 0.62f), Offset(c.x + x * r, c.y + y * r), strokeWidth = 4f, cap = StrokeCap.Round)
            } else drawArc(Glow.copy(alpha = a), ang, 16f, false, Offset(3f, 3f), Size(size.width - 6f, size.height - 6f), style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}
@Composable fun Splash(logo: Painter, title: String, sub: String, style: Int, status: String, progress: Float, icon: String = "") {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        LogoImg(logo, 200); Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp); Text(sub, color = Dim, fontSize = 11.sp, letterSpacing = 3.sp)
        if (icon.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text(icon, fontSize = 26.sp) }
        Spacer(Modifier.height(36.dp)); Loader(style, progress); Spacer(Modifier.height(14.dp)); Text(status, color = Dim, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(30.dp)); Watermark()
    }
}
@Composable fun Pick(opts: List<String>, sel: String, set: (String) -> Unit) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
    opts.forEach { o -> val on = o == sel; val bg by animateColorAsState(if (on) Acc else Color(0xFF14141C), label = "pk")
        Box(Modifier.weight(1f).press { set(o) }.height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (on) Glow else Ln, RoundedCornerShape(14.dp)), Alignment.Center) {
            Text(o, color = if (on) Color.White else Txt, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) } }
}
@Composable fun Stat(m: Modifier, k: String, v: String) = Column(m.clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Ln, RoundedCornerShape(14.dp)).padding(12.dp)) { Cap(k); Text(v, fontWeight = FontWeight.Black, fontSize = if (v.length > 10) 12.sp else 16.sp, maxLines = 1) }
@Composable fun StatRow(vararg p: Pair<String, String>) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { p.forEach { Stat(Modifier.weight(1f), it.first, it.second) } }
@Composable fun Toggle(t: String, s: String, on: Boolean, set: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { Text(t, fontWeight = FontWeight.Bold); Text(s, color = Dim, fontSize = 12.sp) }
    Switch(on, set, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Glow, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF222222)))
}
@Composable fun Tile(m: Modifier, ic: String, name: String, desc: String, on: Boolean = false, click: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    Column(m.press(click).height(100.dp).clip(sh).background(if (on) Brush.linearGradient(listOf(Acc, Glow)) else Brush.linearGradient(listOf(Color(0xFF1C1C1C), Color(0xFF0E0E0E)))).border(1.dp, if (on) Glow else Ln, sh).padding(14.dp), Arrangement.SpaceBetween) {
        Text(ic, fontSize = 24.sp); Column { Text(name, color = if (on) Color.White else Glow, fontWeight = FontWeight.Black, fontSize = 14.sp); Text(desc, color = if (on) Color(0xFFE9D5FF) else Dim, fontSize = 11.sp) } }
}
@Composable fun Confirm(title: String, body: String, yes: () -> Unit, no: () -> Unit) = androidx.compose.ui.window.Dialog(no) {
    Glass(Modifier.padding(20.dp)) { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(body, color = Dim, fontSize = 13.sp); Btn("CONFIRM", true, onClick = yes); Btn("CANCEL", false, onClick = no) } }

fun openUrl(c: Context, u: String) { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "sh"); val x = t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "x")
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent(); val w = size.width
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFFFFF), Color.Transparent), startX = w * (x.value - 0.2f), endX = w * (x.value + 0.2f)), blendMode = BlendMode.SrcAtop)
    }
}
@Composable fun KeyGlyph(sz: Int = 34) = Canvas(Modifier.size(sz.dp)) { val w = size.width; val sw = w * 0.075f
    drawCircle(Glow, w * 0.2f, Offset(w * 0.27f, w * 0.5f), style = Stroke(sw)); drawLine(Glow, Offset(w * 0.47f, w * 0.5f), Offset(w * 0.93f, w * 0.5f), sw, StrokeCap.Round)
    drawLine(Glow, Offset(w * 0.72f, w * 0.5f), Offset(w * 0.72f, w * 0.7f), sw, StrokeCap.Round); drawLine(Glow, Offset(w * 0.88f, w * 0.5f), Offset(w * 0.88f, w * 0.65f), sw, StrokeCap.Round) }
@Composable fun CrownGlyph(sz: Int = 34) = Canvas(Modifier.size(sz.dp)) { val w = size.width; val h = size.height
    val sh = Path().apply { moveTo(w * 0.5f, h * 0.04f); lineTo(w * 0.92f, h * 0.2f); lineTo(w * 0.92f, h * 0.55f); lineTo(w * 0.5f, h * 0.96f); lineTo(w * 0.08f, h * 0.55f); lineTo(w * 0.08f, h * 0.2f); close() }
    drawPath(sh, Glow, style = Stroke(w * 0.06f, join = StrokeJoin.Round))
    val cr = Path().apply { moveTo(w * 0.27f, h * 0.58f); lineTo(w * 0.28f, h * 0.32f); lineTo(w * 0.4f, h * 0.45f); lineTo(w * 0.5f, h * 0.27f); lineTo(w * 0.6f, h * 0.45f); lineTo(w * 0.72f, h * 0.32f); lineTo(w * 0.73f, h * 0.58f); close() }
    drawPath(cr, Glow) }
@Composable fun InfoGlyph(sz: Int = 34) = Canvas(Modifier.size(sz.dp)) { val w = size.width; val sw = w * 0.075f
    drawCircle(Glow, w * 0.44f, Offset(w * 0.5f, w * 0.5f), style = Stroke(sw)); drawCircle(Glow, w * 0.05f, Offset(w * 0.5f, w * 0.3f)); drawLine(Glow, Offset(w * 0.5f, w * 0.43f), Offset(w * 0.5f, w * 0.72f), sw, StrokeCap.Round) }

@Composable fun LogoOrb(logo: Painter, size: Int) {
    val t = rememberInfiniteTransition(label = "orb")
    val a = t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "a"); val b = t.animateFloat(360f, 0f, infiniteRepeatable(tween(9500, easing = LinearEasing)), label = "b")
    val pulse = t.animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val enter = remember { Animatable(0f) }; LaunchedEffect(Unit) { enter.animateTo(1f, tween(if (UiFlags.reduceMotion) 0 else 900, easing = FastOutSlowInEasing)) }
    Box(Modifier.size((size + 80).dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2); val r = this.size.minDimension / 2; val pr = pulse.value
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), c, r * pr), r * pr, c)
            for (i in 0 until 3) { val g = Math.toRadians((a.value + i * 120).toDouble()); drawCircle(Glow, 5f, Offset(c.x + kotlin.math.cos(g).toFloat() * r * 0.94f, c.y + kotlin.math.sin(g).toFloat() * r * 0.94f)) }
            for (i in 0 until 2) { val g = Math.toRadians((b.value + i * 180).toDouble()); drawCircle(Color(0x99FFFFFF), 3.5f, Offset(c.x + kotlin.math.cos(g).toFloat() * r * 0.8f, c.y + kotlin.math.sin(g).toFloat() * r * 0.8f)) }
        }
        Box(Modifier.graphicsLayer { scaleX = 0.8f + 0.2f * enter.value; scaleY = 0.8f + 0.2f * enter.value; alpha = enter.value }) { LogoImg(logo, size) }
    }
}
@Composable fun CoolSplash(logo: Painter, title: String, sub: String, style: Int, status: String, progress: Float, icon: (@Composable () -> Unit)? = null) {
    val p by animateFloatAsState(progress, tween(500), label = "sp")
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        LogoOrb(logo, 190)
        Text(title, Modifier.shimmer(), fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Glow); Text(sub, color = Dim, fontSize = 11.sp, letterSpacing = 3.sp)
        if (icon != null) { Spacer(Modifier.height(12.dp)); icon() }
        Spacer(Modifier.height(30.dp)); Loader(style, p)
        Spacer(Modifier.height(10.dp)); Text("${(p * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 15.sp)
        AnimatedContent(status, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "st") { Text(it, color = Dim, fontSize = 12.sp, letterSpacing = 1.sp) }
        Spacer(Modifier.height(26.dp)); Watermark()
    }
}
@Composable fun SplashSwitch(ready: Boolean, splash: @Composable () -> Unit, content: @Composable () -> Unit) =
    AnimatedContent(ready, transitionSpec = { (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.94f)) togetherWith fadeOut(tween(300)) }, label = "ss") { r -> if (r) content() else splash() }

@Composable fun Blocked(logo: Painter, title: String, msg: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        LogoImg(logo, 120); Spacer(Modifier.height(18.dp)); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp)); Text(msg, color = Dim, fontSize = 14.sp); Spacer(Modifier.height(20.dp))
        if (onRetry != null) Btn("TRY AGAIN", false, onClick = onRetry); Watermark() }
}
@Composable fun AnnounceCard() { val d = CtlState.data ?: return; if (d.announce.isBlank() || CtlState.dismissed == d.announceId) return
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Chip("ANNOUNCEMENT", Warn); Spacer(Modifier.weight(1f)); MiniBtn("DISMISS") { CtlState.dismissed = d.announceId } }; Text(d.announce, fontSize = 14.sp) } }
@Composable fun UpdateCard(cur: String) { val d = CtlState.data ?: return; val c = LocalContext.current; if (d.latest.isBlank() || !Control.newer(d.latest, cur)) return
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Chip("UPDATE AVAILABLE", Ok); Spacer(Modifier.weight(1f)); Text("v${d.latest}", fontWeight = FontWeight.Black) }
        if (d.notes.isNotBlank()) Text(d.notes, color = Dim, fontSize = 12.sp); Btn("GET THE UPDATE") { openUrl(c, d.updateUrl.ifBlank { Control.MEDIAFIRE }) } } }

@Composable fun ShowcaseCard(ver: String) {
    var mode by remember { mutableStateOf(2) }; val d = CtlState.data; val c = LocalContext.current; val cb = LocalClipboardManager.current; val toast = LocalToast.current
    Pick(listOf("NACHT X", "Generator", "Both"), listOf("NACHT X", "Generator", "Both")[mode]) { mode = if (it == "NACHT X") 0 else if (it == "Generator") 1 else 2 }
    Glass {
        Text(Showcase.title(ver, mode), Modifier.shimmer(), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Glow); Text("👀", fontSize = 30.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (mode != 1) { Cap("NACHT X features"); Showcase.X.forEach { Text("*  " + it.uppercase(), fontSize = 12.sp, color = Color(0xFFDDDDDD)) } }
        if (mode != 0) { Cap("NACHT X Generator features"); Showcase.G.forEach { Text("*  " + it.uppercase(), fontSize = 12.sp, color = Color(0xFFDDDDDD)) } }
        Text(Showcase.BAR, color = Ln); Cap("Pricelist"); Showcase.prices(d).forEach { Text("->  " + it.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Text(Showcase.BAR, color = Ln); Cap("Payment methods"); Showcase.payments(d).forEach { Text("*  " + it.uppercase(), fontSize = 13.sp) }
        Text(Showcase.BAR, color = Ln); Text("WANT TO BUY?", fontWeight = FontWeight.Black); Text("BUY KEY : " + Showcase.contact(d), fontWeight = FontWeight.Black, fontSize = 16.sp)
        Btn("COPY SHOWCASE TEXT") { cb.setText(AnnotatedString(Showcase.text(ver, mode, d))); toast("Showcase copied") }
        Btn("SHARE", false) { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Showcase.text(ver, mode, d)), "Share showcase").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Btn("OPEN TELEGRAM", false) { openUrl(c, "https://t.me/" + Showcase.contact(d).removePrefix("@")) }
    }
}
