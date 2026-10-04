package com.unoxidized.x
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unoxidized.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

const val DEFAULT_PKG = "com.garena.game.codm"
data class P(val ic: String, val n: String, val sc: Float, val fps: Int)
data class Profile(val name: String, val sc: Float, val fps: Int, val mode: String)
data class Step(val label: String, val run: () -> String?)
val PRESETS = listOf(P("🪶", "Low Graphics", .6f, 60), P("⚡", "Smooth", .8f, 90), P("⚖️", "Balanced", .9f, 60), P("🎯", "Max FPS", .5f, 120))
val STEPS = listOf("Install both apps" to "Unoxidized X and Unoxidized Generator come in the same zip.",
    "Get your key" to "Open Unoxidized Generator, tap Generate key, then 'Send to Unoxidized X'. Keys last 7 days.",
    "Install Shizuku" to "Get Shizuku from the Play Store.",
    "Enable Wireless debugging" to "Settings > About phone: tap Build number 7 times. Then Developer options > Wireless debugging ON.",
    "Pair and start Shizuku" to "Open Shizuku > Pairing, enter the code, then 'Start via Wireless debugging'.",
    "Authorize the app" to "Tap Authorize access and press Allow in the Shizuku popup.",
    "Boost" to "Dashboard > Boost & Launch. Watch the popup: every step is a real check.",
    "Undo anytime" to "Dashboard > Reset to default. Restart Shizuku after every phone reboot.")
fun ds(s: Float): String = "%.2f".format(java.util.Locale.US, s).trimEnd('0').trimEnd('.')
fun fpsOpts(maxHz: Int) = listOf(30, 45, 60, 90, 120).filter { it <= maxOf(maxHz, 60) }
fun clampFps(f: Int, maxHz: Int) = fpsOpts(maxHz).lastOrNull { it <= f } ?: 30
fun modeName(m: String) = if (m == "Battery") "battery" else "performance"
fun applySteps(pkg: String, sc: Float, fps: Int, mode: String): List<Step> {
    val f = if (sc >= .995f) "" else "downscaleFactor=${ds(sc)},"
    fun bad(r: String) = r.startsWith("Error") || r.contains("Exception") || r.contains("Invalid") || r.contains("not supported")
    return listOf(
        Step("Checking the game is installed") { if (Sh.run("pm path $pkg").contains("package:")) null else "Game not found: $pkg. Check the package in Tools." },
        Step("Writing the performance profile") { val r = Sh.run("device_config put game_overlay $pkg 'mode=2,${f}fps=$fps:mode=3,${f}fps=$fps'"); if (bad(r)) r.trim() else null },
        Step("Activating ${modeName(mode)} mode") { val r = Sh.run("sleep 1; cmd game set --mode ${modeName(mode)} $pkg"); if (bad(r)) r.trim() else null },
        Step("Verifying with Android") { val r = Sh.run("device_config get game_overlay $pkg"); if (r.contains("fps=$fps")) null else "Android did not keep the profile: ${r.trim().take(90)}" })
}
fun parseProfile(raw: String): Profile? = try {
    val t = raw.trim(); val js = if (t.startsWith("UXP1:")) String(android.util.Base64.decode(t.drop(5), android.util.Base64.DEFAULT)) else t
    val o = org.json.JSONObject(js)
    Profile(o.optString("name", "Imported"), o.optDouble("scale", 0.8).toFloat().coerceIn(.3f, 1f), o.optInt("fps", 60), if (o.optString("mode", "performance").lowercase() == "battery") "Battery" else "Performance")
} catch (e: Exception) { null }

class TaskState { var title by mutableStateOf(""); var open by mutableStateOf(false); var done by mutableStateOf(false); var ok by mutableStateOf(true); var note by mutableStateOf("")
    val labels = mutableStateListOf<String>(); val st = mutableStateListOf<Int>() }
fun runTask(t: TaskState, title: String, steps: List<Step>) {
    t.title = title; t.labels.clear(); t.labels.addAll(steps.map { it.label }); t.st.clear(); steps.forEach { _ -> t.st.add(0) }
    t.note = ""; t.done = false; t.ok = true; t.open = true
    thread {
        for ((i, s) in steps.withIndex()) {
            t.st[i] = 1; Thread.sleep(450)
            val err = try { s.run() } catch (e: Throwable) { e.message ?: "error" }
            if (err != null) { t.st[i] = 3; t.ok = false; t.note = err; break }
            t.st[i] = 2
        }
        t.done = true
    }
}

class MainActivity : ComponentActivity() {
    var incoming by mutableStateOf<String?>(null)
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); incoming = intent?.getStringExtra("ux_key")
        setContent {
            MaterialTheme(darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black, surface = Color(0xFF0B0B0B), onSurface = Color.White, onBackground = Color.White, secondaryContainer = Color(0xFF242424), onSecondaryContainer = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App(this, incoming) { incoming = null } }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("ux_key")?.let { incoming = it } }
}

@Composable fun Pick(opts: List<String>, sel: String, set: (String) -> Unit) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
    opts.forEach { o ->
        val on = o == sel; val bg by animateColorAsState(if (on) Color.White else Color(0xFF141414))
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, if (on) Color.White else Ln, RoundedCornerShape(14.dp)).clickable { set(o) }, Alignment.Center) {
            Text(o, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}
@Composable fun Tile(m: Modifier, p: P, desc: String, on: Boolean, click: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    Column(m.height(108.dp).clip(sh).background(if (on) Brush.linearGradient(listOf(Color.White, Color(0xFFC9C9C9))) else Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0E0E0E))))
        .border(1.dp, if (on) Color.White else Ln, sh).clickable(onClick = click).padding(14.dp), Arrangement.SpaceBetween) {
        Text(p.ic, fontSize = 26.sp)
        Column { Text(p.n, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp); Text(desc, color = if (on) Color(0xFF444444) else Dim, fontSize = 11.sp) }
    }
}
@Composable fun Stat(m: Modifier, k: String, v: String) = Column(m.clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).border(1.dp, Ln, RoundedCornerShape(14.dp)).padding(12.dp)) { Cap(k); Text(v, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1) }
@Composable fun Toggle(t: String, s: String, on: Boolean, set: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { Text(t, fontWeight = FontWeight.Bold); Text(s, color = Dim, fontSize = 12.sp) }
    Switch(on, set, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF222222)))
}

@Composable fun App(c: Context, incoming: String?, consume: () -> Unit) {
    val sp = remember { c.getSharedPreferences("ux", 0) }; val dev = remember { Lic.deviceId(c) }
    var key by remember { mutableStateOf(sp.getString("k", "") ?: "") }
    var ok by remember { mutableStateOf(key.isNotEmpty() && Lic.check(dev, key).ok) }
    if (!ok) Login(c, dev, incoming, consume) { k -> sp.edit().putString("k", k).apply(); key = k; ok = true }
    else Gate(c) { Main(c, sp, key) { sp.edit().remove("k").apply(); ok = false } }
}

@Composable fun Login(c: Context, dev: String, incoming: String?, consume: () -> Unit, done: (String) -> Unit) {
    var t by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    val cb = LocalClipboardManager.current
    fun go(k: String) { val r = Lic.check(dev, k); if (r.ok) done(Lic.clean(k)) else err = r.msg }
    LaunchedEffect(incoming) { if (incoming != null) { t = incoming; go(incoming); consume() } }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        LogoImg(painterResource(R.drawable.logo), 150); Spacer(Modifier.height(10.dp))
        Text("UNOXIDIZED X", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Text("Game optimizer · licensed access", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Glass {
            Cap("Your device ID")
            Row(verticalAlignment = Alignment.CenterVertically) { Text(dev, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold); CopyChip(dev) }
            Cap("License key")
            OutlinedTextField(t, { t = it; err = "" }, Modifier.fillMaxWidth(), placeholder = { Text("UX-$dev-...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 4)
            Btn("PASTE FROM CLIPBOARD", false) { t = cb.getText()?.text ?: ""; err = "" }
            if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
            Btn("LOG IN") { go(t) }
        }
        Spacer(Modifier.height(12.dp))
        Btn("OPEN UNOXIDIZED GENERATOR", false) { Env.openApp(c, "com.unoxidized.gen") }
        Text("Generate your free weekly key there, then send it here.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Watermark()
    }
}

@Composable fun Gate(c: Context, content: @Composable () -> Unit) {
    var inst by remember { mutableStateOf(Env.installed(c)) }; var run by remember { mutableStateOf(Sh.running()) }; var ok by remember { mutableStateOf(Sh.granted()) }
    LaunchedEffect(Unit) { while (true) { inst = Env.installed(c); run = Sh.running(); ok = Sh.granted(); delay(800) } }
    if (inst && run && ok) content() else GateUi(c, inst, run, ok)
}
@Composable fun StepCard(n: Int, title: String, body: String, done: Boolean, current: Boolean, content: @Composable ColumnScope.() -> Unit) = Glass {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (done) Brush.horizontalGradient(listOf(Ok, Ok)) else if (current) WhiteGrad else Brush.horizontalGradient(listOf(Color(0xFF222222), Color(0xFF222222)))), Alignment.Center) {
            Text(if (done) "✓" else "$n", color = if (done || current) Color.Black else Dim, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(body, color = Dim, fontSize = 12.sp) }
    }
    if (current) content()
}
@Composable fun GateUi(c: Context, inst: Boolean, run: Boolean, ok: Boolean) {
    val st = listOf(inst, run, ok); val done = st.count { it }; val cur = st.indexOfFirst { !it }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); LogoImg(painterResource(R.drawable.logo), 96)
        Text("AUTHORIZE ACCESS", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text("Unoxidized X changes game settings through Shizuku. Complete these 3 steps to unlock every feature.", color = Dim, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(done / 3f).fillMaxHeight().background(WhiteGrad)) }
        StepCard(1, "Install Shizuku", "The free app that grants access.", inst, cur == 0) { Btn("INSTALL SHIZUKU") { Env.installShizuku(c) } }
        StepCard(2, "Pair and start Shizuku", "Wireless debugging, then Start.", run, cur == 1) {
            Text("1. Turn on Wireless debugging.\n2. In Shizuku tap Pairing and enter the code.\n3. Tap 'Start via Wireless debugging'.", fontSize = 13.sp, color = Color(0xFFCCCCCC))
            Btn("OPEN DEVELOPER OPTIONS", false) { Env.openDev(c) }; Btn("OPEN SHIZUKU") { Env.openApp(c, Env.SZ) } }
        StepCard(3, "Authorize Unoxidized X", "Allow this app inside Shizuku.", ok, cur == 2) {
            Btn("AUTHORIZE ACCESS") { Sh.ask() }
            Text("No popup? Open Shizuku > Authorized apps and switch Unoxidized X on.", fontSize = 12.sp, color = Dim) }
        Text("This screen unlocks automatically when all steps are done. If Shizuku stops, you come back here.", color = Dim, fontSize = 12.sp)
        Watermark()
    }
}

@Composable fun TaskDialog(t: TaskState, restart: () -> Unit) {
    if (!t.open) return
    Dialog({ if (t.done) t.open = false }, DialogProperties(dismissOnBackPress = t.done, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Glass(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t.title.uppercase(), Modifier.weight(1f), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 15.sp)
                if (!t.done) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }
            val d = t.st.count { it == 2 }; val n = maxOf(t.st.size, 1)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(d / n.toFloat()).fillMaxHeight().background(if (t.ok) WhiteGrad else Brush.horizontalGradient(listOf(Bad, Bad)))) }
            t.labels.forEachIndexed { i, l -> val s = t.st.getOrElse(i) { 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (s) { 1 -> "◐"; 2 -> "✓"; 3 -> "✕"; else -> "○" }, Modifier.width(28.dp), color = when (s) { 2 -> Ok; 3 -> Bad; 1 -> Color.White; else -> Dim }, fontWeight = FontWeight.Black)
                    Text(l, color = if (s == 0) Dim else Color.White, fontSize = 14.sp) } }
            if (t.done) {
                Text(if (t.ok) "All set. Restart the game so it loads the profile." else t.note, color = if (t.ok) Ok else Bad, fontSize = 13.sp)
                if (t.ok) Btn("RESTART GAME") { t.open = false; restart() }
                Btn("CLOSE", false) { t.open = false }
            }
        }
    }
}

@Composable fun Term(log: String) {
    val bad = log.startsWith("Error") || log.contains("Invalid") || log.contains("not supported") || log.contains("Exception")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C0C0C)).border(1.dp, Ln, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Output"); Spacer(Modifier.weight(1f)); CopyChip(log) }
        Text(log, Modifier.heightIn(max = 80.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (bad) Bad else Color(0xFF9EE6B5))
    }
}

@Composable fun Main(c: Context, sp: android.content.SharedPreferences, key: String, logout: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope(); val cb = LocalClipboardManager.current
    val pages = listOf("Dashboard" to "🏠", "Resolution" to "🖥️", "Gaming" to "🎮", "Display" to "📱", "Boost" to "🚀", "Import" to "📥", "Tools" to "🧰", "Guide" to "📖", "Account" to "👤")
    val hzs = remember { Env.hz(c) }; val maxHz = hzs.max(); val tk = remember { TaskState() }
    var page by remember { mutableStateOf(0) }; var pkg by remember { mutableStateOf(sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG) }
    var scale by remember { mutableStateOf(sp.getFloat("sc", 0.8f)) }; var fps by remember { mutableStateOf(clampFps(sp.getInt("fps", 60), maxHz)) }
    var mode by remember { mutableStateOf(sp.getString("md", "Performance") ?: "Performance") }; var hz by remember { mutableStateOf(sp.getInt("hz", maxHz)) }
    var active by remember { mutableStateOf("") }; var log by remember { mutableStateOf("Ready. Output appears here.") }
    var dnd by remember { mutableStateOf(false) }; var anim by remember { mutableStateOf(false) }; var code by remember { mutableStateOf("") }
    fun save() { sp.edit().putFloat("sc", scale).putInt("fps", fps).putString("md", mode).putString("pkg", pkg).putInt("hz", hz).apply() }
    fun run(cmd: String) { log = "Running..."; thread { val r = Sh.run(cmd); log = "$ $cmd\n$r" } }
    val launch = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    val resetCmd = "cmd game reset $pkg; device_config delete game_overlay $pkg"
    fun apply(title: String) { save(); runTask(tk, title, applySteps(pkg, scale, fps, mode)) }
    fun importText(raw: String) {
        val p = parseProfile(raw)
        if (p == null) { runTask(tk, "Importing profile", listOf(Step("Reading profile") { "That is not a valid Unoxidized profile (UXP1 code or JSON)." })); return }
        scale = Math.round(p.sc * 20) / 20f; fps = clampFps(p.fps, maxHz); mode = p.mode; active = ""; save()
        runTask(tk, "Importing ${p.name}", listOf(Step("Reading profile \"${p.name}\"") { null }, Step("Optimizing for ${Env.device()}") { null }) + applySteps(pkg, scale, fps, mode))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importText(try { c.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }) }
    TaskDialog(tk) { run("am force-stop $pkg; $launch") }
    val left = Lic.check(Lic.deviceId(c), key).left
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF080808)) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 48); Spacer(Modifier.width(12.dp))
            Column { Text("UNOXIDIZED X", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp); Text("Key: $left day(s) left", color = Dim, fontSize = 12.sp) } }
        pages.forEachIndexed { i, (n, ic) -> NavigationDrawerItem({ Text("$ic   $n", fontWeight = FontWeight.Bold) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF242424), unselectedContainerColor = Color.Transparent, selectedTextColor = Color.White, unselectedTextColor = Color(0xFFBBBBBB))) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF181818)).border(1.dp, Ln, RoundedCornerShape(14.dp)).clickable { scope.launch { drawer.open() } }, Alignment.Center) { Text("☰", fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(pages[page].first.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp); Text("UNOXIDIZED X", color = Dim, fontSize = 10.sp, letterSpacing = 3.sp) }
                Box(Modifier.size(12.dp).clip(CircleShape).background(Ok))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                when (page) {
                    0 -> {
                        Glass { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(CircleShape).background(Ok)); Spacer(Modifier.width(10.dp))
                            Column { Text("Shizuku authorized · ${Env.access()}", fontWeight = FontWeight.Bold); Text(pkg, color = Dim, fontSize = 11.sp) } }
                            Text("${ds(scale)}x scale · $fps FPS · $mode", color = Dim, fontSize = 13.sp)
                            Btn("BOOST & LAUNCH GAME") { save(); runTask(tk, "Boosting", listOf(Step("Cleaning background apps") { Sh.run("am kill-all"); null }) + applySteps(pkg, scale, fps, mode) + Step("Launching the game") { Sh.run(launch); null }) } }
                        Cap("Quick presets")
                        PRESETS.chunked(2).forEach { r -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { r.forEach { p -> val f = clampFps(p.fps, maxHz)
                            Tile(Modifier.weight(1f), p, "${ds(p.sc)}x · $f FPS", active == p.n) { scale = p.sc; fps = f; mode = "Performance"; active = p.n; apply("Applying ${p.n}") } } } }
                        Btn("RESET TO DEFAULT", false) { active = ""; runTask(tk, "Resetting", listOf(Step("Removing the profile") { val r = Sh.run(resetCmd); if (r.startsWith("Error")) r else null })) }
                        Cap("Device")
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Stat(Modifier.weight(1f), "Device", Env.device()); Stat(Modifier.weight(1f), "Android", android.os.Build.VERSION.RELEASE) }
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) { Stat(Modifier.weight(1f), "Chip", Env.soc()); Stat(Modifier.weight(1f), "Screen", hzs.joinToString("/") + " Hz") }
                    }
                    1 -> { H("Resolution", "Lower scale = more FPS, softer image.")
                        Glass { Cap("Presets"); Pick(listOf("0.9", "0.8", "0.7", "0.5"), "%.1f".format(java.util.Locale.US, scale)) { scale = it.toFloat() }
                            Cap("Custom scale"); Text("${ds(scale)}x", fontSize = 44.sp, fontWeight = FontWeight.Black)
                            Slider(scale, { scale = (Math.round(it * 20) / 20f) }, valueRange = 0.3f..1f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF2A2A2A)))
                            Btn("APPLY RESOLUTION") { apply("Applying resolution") }; Btn("RESET", false) { run(resetCmd) } } }
                    2 -> { H("Gaming", "Frame rate cap and performance mode.")
                        Glass { Cap("FPS cap"); Pick(fpsOpts(maxHz).map { it.toString() }, fps.toString()) { fps = it.toInt() }
                            if (maxHz <= 60) Text("Your screen is $maxHz Hz, so higher caps are hidden.", color = Dim, fontSize = 12.sp)
                            Cap("Mode"); Pick(listOf("Performance", "Battery"), mode) { mode = it }
                            Btn("APPLY GAMING SETTINGS") { apply("Applying gaming settings") } } }
                    3 -> { H("Display", "Screen refresh rate your phone supports.")
                        Glass { Cap("Refresh rate (Hz)"); Pick(hzs.map { it.toString() }, hz.toString()) { hz = it.toInt() }
                            Btn("APPLY REFRESH RATE") { save(); run("settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz") }
                            Btn("RESET", false) { run("settings delete system min_refresh_rate; settings delete system peak_refresh_rate") } } }
                    4 -> { H("Boost", "One-tap helpers for smoother matches.")
                        Glass { Btn("CLEAN BACKGROUND APPS") { runTask(tk, "Cleaning", listOf(Step("Closing background apps") { Sh.run("am kill-all"); null })) }
                            Toggle("Silence notifications", "Do Not Disturb while you play", dnd) { dnd = it; run("cmd notification set_dnd ${if (it) "priority" else "off"}") }
                            Toggle("Faster animations", "Cuts system animation time in half", anim) { anim = it; val v = if (it) "0.5" else "1.0"
                                run("settings put global window_animation_scale $v; settings put global transition_animation_scale $v; settings put global animator_duration_scale $v") } } }
                    5 -> { H("Import", "Load a booster profile and apply it to the game.")
                        Glass { Btn("IMPORT PROFILE FILE") { picker.launch(arrayOf("*/*")) }
                            Cap("Or paste a profile code"); OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), placeholder = { Text("UXP1:...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 3)
                            Btn("IMPORT CODE", false) { importText(code) } }
                        Glass { Cap("Share your setup"); Text("Copy a code of your current settings to send to friends.", color = Dim, fontSize = 12.sp)
                            Btn("COPY MY PROFILE CODE", false) { val o = org.json.JSONObject().put("name", "My profile").put("scale", scale.toDouble()).put("fps", fps).put("mode", modeName(mode))
                                cb.setText(androidx.compose.ui.text.AnnotatedString("UXP1:" + android.util.Base64.encodeToString(o.toString().toByteArray(), android.util.Base64.NO_WRAP))); log = "Profile code copied." } } }
                    6 -> { H("Tools", "Game package and quick actions.")
                        Glass { Cap("Game package"); OutlinedTextField(pkg, { pkg = it.trim() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(), singleLine = true)
                            Btn("LAUNCH GAME") { save(); run(launch) }
                            Btn("RESTART GAME (APPLY SETTINGS)", false) { run("am force-stop $pkg; $launch") }
                            Btn("CHECK CURRENT CONFIG", false) { run("device_config get game_overlay $pkg; cmd game mode $pkg") } } }
                    7 -> { H("Guide", "Step by step setup.")
                        STEPS.forEachIndexed { i, (a, b) -> Glass { Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(WhiteGrad), Alignment.Center) { Text("${i + 1}", color = Color.Black, fontWeight = FontWeight.Black) }
                            Spacer(Modifier.width(12.dp)); Column { Text(a, fontWeight = FontWeight.Bold); Text(b, color = Dim, fontSize = 13.sp) } } } } }
                    8 -> { H("Account", "Your access.")
                        Glass { Cap("Device ID"); Row(verticalAlignment = Alignment.CenterVertically) { Text(Lic.deviceId(c), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 18.sp); CopyChip(Lic.deviceId(c)) }
                            Cap("Key expires"); Text(Lic.until(Lic.check(Lic.deviceId(c), key).day) + "  ($left days left)")
                            Cap("Made by"); Text("@UnoxidizedX on Telegram", fontWeight = FontWeight.Bold) }
                        Btn("OPEN UNOXIDIZED GENERATOR", false) { Env.openApp(c, "com.unoxidized.gen") }
                        Btn("LOG OUT", false, logout) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Term(log); Box(Modifier.fillMaxWidth(), Alignment.Center) { Watermark() }
        }
    }
}
