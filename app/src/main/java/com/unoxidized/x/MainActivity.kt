package com.unoxidized.x
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

val Ln = Color(0xFF2A2A2A); val Dim = Color(0xFF8C8C8C); val Ok = Color(0xFF3DDC84)
val WhiteGrad = Brush.horizontalGradient(listOf(Color.White, Color(0xFFB8B8B8)))
const val DEFAULT_PKG = "com.garena.game.codm"
data class P(val ic: String, val n: String, val d: String, val sc: Float, val fps: String, val md: String)
val PRESETS = listOf(P("🪶", "Low Graphics", "0.6x · 60 FPS", .6f, "60", "Performance"), P("⚡", "Smooth", "0.8x · 90 FPS", .8f, "90", "Performance"),
    P("⚖️", "Balanced", "0.9x · 60 FPS", .9f, "60", "Standard"), P("🎯", "Max FPS", "0.5x · 120 FPS", .5f, "120", "Performance"))
val STEPS = listOf("Install Shizuku" to "Get Shizuku from the Play Store.",
    "Enable Wireless debugging" to "Settings > About phone: tap Build number 7 times. Then Developer options > Wireless debugging ON.",
    "Start Shizuku" to "Open Shizuku > Pairing > pair, then 'Start via Wireless debugging'.",
    "Grant access" to "Dashboard > Grant Shizuku access > Allow.",
    "Pick a preset" to "Tap a tile on the Dashboard, or fine-tune in Resolution, Gaming and Display.",
    "Restart the game" to "Tools > Restart game, so CODM loads the new settings.",
    "Inside CODM" to "Set Frame Rate to the highest option available.",
    "Undo anytime" to "Dashboard > Reset to default. Restart Shizuku after every phone reboot.")
fun ds(s: Float): String = if (s >= 0.995f) "disable" else "%.2f".format(java.util.Locale.US, s).trimEnd('0').trimEnd('.')

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme(darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black, surface = Color(0xFF0B0B0B), onSurface = Color.White, onBackground = Color.White, secondaryContainer = Color(0xFF242424), onSecondaryContainer = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App(this) }
            }
        }
    }
}

@Composable fun Glass(m: Modifier = Modifier, c: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(22.dp)
    Column(m.fillMaxWidth().clip(sh).background(Brush.verticalGradient(listOf(Color(0xFF181818), Color(0xFF0B0B0B)))).border(1.dp, Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF1A1A1A))), sh).padding(18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = c)
}
@Composable fun H(t: String, s: String) = Column { Text(t, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(s, color = Dim, fontSize = 13.sp) }
@Composable fun Cap(t: String) = Text(t.uppercase(), color = Dim, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
@Composable fun Btn(t: String, primary: Boolean = true, onClick: () -> Unit) {
    val hf = LocalHapticFeedback.current; val sh = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(54.dp).clip(sh).then(if (primary) Modifier.background(WhiteGrad) else Modifier.background(Color(0xFF111111)).border(1.dp, Ln, sh))
        .clickable { hf.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, Alignment.Center) {
        Text(t, color = if (primary) Color.Black else Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
    }
}
@Composable fun Pick(opts: List<String>, sel: String, set: (String) -> Unit) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
    opts.forEach { o ->
        val on = o == sel; val bg by animateColorAsState(if (on) Color.White else Color(0xFF141414))
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (on) Color.White else Ln, RoundedCornerShape(14.dp)).clickable { set(o) }, Alignment.Center) {
            Text(o, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
@Composable fun Tile(m: Modifier, p: P, on: Boolean, click: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    Column(m.height(108.dp).clip(sh).background(if (on) Brush.linearGradient(listOf(Color.White, Color(0xFFC9C9C9))) else Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0E0E0E))))
        .border(1.dp, if (on) Color.White else Ln, sh).clickable(onClick = click).padding(14.dp), Arrangement.SpaceBetween) {
        Text(p.ic, fontSize = 26.sp)
        Column { Text(p.n, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp); Text(p.d, color = if (on) Color(0xFF444444) else Dim, fontSize = 11.sp) }
    }
}
@Composable fun Stat(m: Modifier, k: String, v: String) = Column(m.clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Ln, RoundedCornerShape(14.dp)).padding(12.dp)) { Cap(k); Text(v, fontWeight = FontWeight.Black, fontSize = 18.sp) }
@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Ln, cursorColor = Color.White)
@Composable fun CopyChip(text: String) { val cb = LocalClipboardManager.current
    Text("COPY", Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).clickable { cb.setText(AnnotatedString(text)) }.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }

@Composable fun App(c: Context) {
    val sp = remember { c.getSharedPreferences("ux", 0) }
    var key by remember { mutableStateOf(sp.getString("k", "") ?: "") }
    var ok by remember { mutableStateOf(key.isNotEmpty() && License.check(c, key) == null) }
    if (!ok) Login(c) { k -> sp.edit().putString("k", k).apply(); key = k; ok = true }
    else Main(c, key) { sp.edit().remove("k").apply(); ok = false }
}

@Composable fun Login(c: Context, done: (String) -> Unit) {
    var t by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    val cb = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(WhiteGrad), Alignment.Center) { Text("UX", color = Color.Black, fontSize = 36.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(16.dp))
        Text("UNOXIDIZED X", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Text("Game optimizer · licensed access", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(26.dp))
        Glass {
            Cap("Your device ID")
            Row(verticalAlignment = Alignment.CenterVertically) { Text(License.deviceId(c), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold); CopyChip(License.deviceId(c)) }
            Cap("License key")
            OutlinedTextField(t, { t = it; err = "" }, Modifier.fillMaxWidth(), placeholder = { Text("UX-...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 4)
            Btn("PASTE FROM CLIPBOARD", false) { t = cb.getText()?.text ?: ""; err = "" }
            if (err.isNotEmpty()) Text(err, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            Btn("LOG IN") { val e = License.check(c, t); if (e == null) done(License.clean(t)) else err = e }
        }
        Spacer(Modifier.height(14.dp))
        Text("Send your device ID to the owner to get a key.", color = Dim, fontSize = 12.sp)
    }
}

@Composable fun Main(c: Context, key: String, logout: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    val pages = listOf("Dashboard" to "🏠", "Resolution" to "🖥️", "Gaming" to "🎮", "Display" to "📱", "Tools" to "🧰", "Guide" to "📖", "Account" to "👤")
    var page by remember { mutableStateOf(0) }; var pkg by remember { mutableStateOf(DEFAULT_PKG) }
    var scale by remember { mutableStateOf(0.8f) }; var fps by remember { mutableStateOf("60") }
    var mode by remember { mutableStateOf("Performance") }; var hz by remember { mutableStateOf("60") }
    var active by remember { mutableStateOf("") }; var log by remember { mutableStateOf("Ready. Output appears here.") }
    var shiz by remember { mutableStateOf(Sh.ready()) }
    fun run(cmd: String) { log = "Running..."; thread { val r = Sh.run(cmd); log = "$ $cmd\n$r"; shiz = Sh.ready() } }
    val modeId = mapOf("Performance" to 2, "Standard" to 1, "Battery" to 3)
    fun game() = "cmd game set --mode ${modeId[mode]} --downscale ${ds(scale)} --fps $fps $pkg"
    val launch = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF080808)) {
        Column(Modifier.padding(24.dp)) { Text("UNOXIDIZED X", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 3.sp); Text("Licensed to ${License.info(key)}", color = Dim, fontSize = 12.sp) }
        pages.forEachIndexed { i, (n, ic) -> NavigationDrawerItem({ Text("$ic   $n", fontWeight = FontWeight.Bold) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF242424), unselectedContainerColor = Color.Transparent, selectedTextColor = Color.White, unselectedTextColor = Color(0xFFBBBBBB))) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF181818)).border(1.dp, Ln, RoundedCornerShape(14.dp)).clickable { scope.launch { drawer.open() } }, Alignment.Center) { Text("☰", fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(pages[page].first.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp); Text("UNOXIDIZED X", color = Dim, fontSize = 10.sp, letterSpacing = 3.sp) }
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (shiz) Ok else Color(0xFFFF5252)))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                when (page) {
                    0 -> {
                        Glass { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(CircleShape).background(if (shiz) Ok else Color(0xFFFF5252))); Spacer(Modifier.width(10.dp))
                            Column { Text(if (shiz) "Shizuku connected" else "Shizuku not connected", fontWeight = FontWeight.Bold); Text(pkg, color = Dim, fontSize = 11.sp) } }
                            if (!shiz) Btn("GRANT SHIZUKU ACCESS") { Sh.ask(); shiz = Sh.ready() } }
                        Cap("Quick presets")
                        PRESETS.chunked(2).forEach { r -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { r.forEach { p -> Tile(Modifier.weight(1f), p, active == p.n) { scale = p.sc; fps = p.fps; mode = p.md; active = p.n; run(game()) } } } }
                        Btn("RESET TO DEFAULT", false) { active = ""; run("cmd game reset $pkg") }
                        Cap("Active configuration")
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Stat(Modifier.weight(1f), "Scale", ds(scale) + if (scale < .995f) "x" else ""); Stat(Modifier.weight(1f), "FPS", fps) }
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Stat(Modifier.weight(1f), "Mode", mode); Stat(Modifier.weight(1f), "Refresh", "$hz Hz") }
                    }
                    1 -> { H("Resolution", "Lower scale = more FPS, softer image.")
                        Glass { Cap("Presets"); Pick(listOf("0.9", "0.8", "0.7", "0.5"), "%.1f".format(java.util.Locale.US, scale)) { scale = it.toFloat() }
                            Cap("Custom scale"); Text("${"%.2f".format(java.util.Locale.US, scale)}x", fontSize = 44.sp, fontWeight = FontWeight.Black)
                            Slider(scale, { scale = it }, valueRange = 0.5f..1f, steps = 9, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF2A2A2A)))
                            Btn("APPLY RESOLUTION") { run(game()) }; Btn("RESET", false) { run("cmd game reset $pkg") } } }
                    2 -> { H("Gaming", "Frame rate cap and performance mode.")
                        Glass { Cap("FPS cap"); Pick(listOf("60", "90", "120", "144"), fps) { fps = it }
                            Cap("Performance mode"); Pick(listOf("Performance", "Standard", "Battery"), mode) { mode = it }
                            Btn("APPLY GAMING SETTINGS") { run(game()) } } }
                    3 -> { H("Display", "Screen refresh rate. Only works if your screen supports it.")
                        Glass { Cap("Refresh rate (Hz)"); Pick(listOf("60", "90", "120", "144"), hz) { hz = it }
                            Btn("APPLY REFRESH RATE") { run("settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz") }
                            Btn("RESET", false) { run("settings delete system min_refresh_rate; settings delete system peak_refresh_rate") } } }
                    4 -> { H("Tools", "Game package and quick actions.")
                        Glass { Cap("Game package"); OutlinedTextField(pkg, { pkg = it.trim() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(), singleLine = true)
                            Btn("LAUNCH GAME") { run(launch) }
                            Btn("RESTART GAME (APPLY SETTINGS)", false) { run("am force-stop $pkg; $launch") }
                            Btn("CHECK GAME MODE STATUS", false) { run("cmd game mode $pkg") } } }
                    5 -> { H("Guide", "Step by step setup.")
                        STEPS.forEachIndexed { i, (a, b) -> Glass { Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(WhiteGrad), Alignment.Center) { Text("${i + 1}", color = Color.Black, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.width(12.dp)); Column { Text(a, fontWeight = FontWeight.Bold); Text(b, color = Dim, fontSize = 13.sp) } } } } }
                    6 -> { H("Account", "Your license.")
                        Glass { Cap("Licensed to"); Text(License.info(key), fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Cap("Expires"); Text(License.expiry(key))
                            Cap("Device ID"); Row(verticalAlignment = Alignment.CenterVertically) { Text(License.deviceId(c), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 18.sp); CopyChip(License.deviceId(c)) } }
                        Btn("LOG OUT", false, logout) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C0C0C)).border(1.dp, Ln, RoundedCornerShape(16.dp)).heightIn(max = 120.dp).padding(12.dp)) {
                Text(log, Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (log.startsWith("Error") || log.contains("Invalid") || log.contains("Exception")) Color(0xFFFF6B6B) else Color(0xFF9EE6B5))
            }
        }
    }
}
