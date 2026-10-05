package com.unoxidized.x
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

const val DEFAULT_PKG = "com.garena.game.codm"
class Step(val label: String, val okText: String = "Done", val run: () -> String?)
data class Prof(val key: String, val name: String, val sc: Float, val fps: Int, val mode: String, val desc: String)
data class Profile(val name: String, val sc: Float, val fps: Int, val mode: String)
val PROFS = listOf(
    Prof("bat", "BATTERY SAVER", .7f, 30, "Battery", "Lower resolution and a 30 FPS cap. Least heat and battery use."),
    Prof("bal", "BALANCED", .9f, 45, "Performance", "Near-native sharpness with a 45 FPS cap. Cooler, steadier sessions."),
    Prof("perf", "PERFORMANCE", .8f, 60, "Performance", "Slightly reduced resolution and a 60 FPS cap for smoother play."),
    Prof("max", "MAX PERFORMANCE", .6f, 120, "Performance", "Lowest resolution and the highest FPS cap your screen supports. Runs warmer."))
val GUIDE = listOf("Install both apps" to "NACHT X and NACHT X Generator come in the same zip.",
    "Get your key" to "Open NACHT X Generator, tap Generate key, then 'Send to NACHT X'. Keys last 7 days.",
    "Install and start Shizuku" to "Get Shizuku from the Play Store. Turn on Wireless debugging, pair in Shizuku, then Start.",
    "Authorize the app" to "Tap Authorize access and press Allow in the Shizuku popup.",
    "Pick a profile" to "Game tab > choose a profile. Every step shows a real check.",
    "Launch CODM" to "Home > Boost & launch CODM. Inside CODM, set Frame Rate to the highest option.",
    "Undo anytime" to "Tools > Restore defaults. Restart Shizuku after every phone reboot.")
fun ds(s: Float): String = "%.2f".format(java.util.Locale.US, s).trimEnd('0').trimEnd('.')
fun fpsOpts(maxHz: Int) = listOf(30, 45, 60, 90, 120).filter { it <= maxOf(maxHz, 60) }
fun clampFps(f: Int, maxHz: Int) = fpsOpts(maxHz).lastOrNull { it <= f } ?: 30
fun modeName(m: String) = if (m == "Battery") "battery" else "performance"
fun bad(r: String) = r.startsWith("Error") || r.contains("Exception") || r.contains("Invalid") || r.contains("not supported") || r.contains("denied")
fun fmt(t: Long): String = if (t <= 0) "Never" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t))
fun applySteps(pkg: String, sc: Float, fps: Int, mode: String): List<Step> {
    val f = if (sc >= .995f) "" else "downscaleFactor=${ds(sc)},"
    return listOf(
        Step("Checking the game is installed", "Installed") { if (Sh.run("pm path $pkg").contains("package:")) null else "Game not found: $pkg. Check the package in Tools." },
        Step("Preparing the CODM profile", "Profile saved") { val r = Sh.run("device_config put game_overlay $pkg 'mode=2,${f}fps=$fps:mode=3,${f}fps=$fps'"); if (bad(r)) r.trim() else null },
        Step("Applying supported optimizations", modeName(mode) + " mode active") { val r = Sh.run("sleep 1; cmd game set --mode ${modeName(mode)} $pkg"); if (bad(r)) r.trim() else null },
        Step("Checking final status", "Verified: ${ds(sc)}x · $fps FPS") { val r = Sh.run("device_config get game_overlay $pkg"); if (r.contains("fps=$fps")) null else "Android did not keep the profile: ${r.trim().take(90)}" })
}
fun parseProfile(raw: String): Profile? = try {
    val t = raw.trim(); val js = if (t.startsWith("UXP1:")) String(android.util.Base64.decode(t.drop(5), android.util.Base64.DEFAULT)) else t
    val o = org.json.JSONObject(js)
    Profile(o.optString("name", "Imported"), o.optDouble("scale", 0.8).toFloat().coerceIn(.3f, 1f), o.optInt("fps", 60), if (o.optString("mode", "performance").lowercase() == "battery") "Battery" else "Performance")
} catch (e: Exception) { null }

class TaskState { var title by mutableStateOf(""); var open by mutableStateOf(false); var done by mutableStateOf(false); var ok by mutableStateOf(true); var note by mutableStateOf("")
    val labels = mutableStateListOf<String>(); val oks = mutableStateListOf<String>(); val st = mutableStateListOf<Int>() }
fun runTask(t: TaskState, title: String, steps: List<Step>, after: ((Boolean) -> Unit)? = null) {
    t.title = title; t.labels.clear(); t.labels.addAll(steps.map { it.label }); t.oks.clear(); t.oks.addAll(steps.map { it.okText }); t.st.clear(); steps.forEach { _ -> t.st.add(0) }
    t.note = ""; t.done = false; t.ok = true; t.open = true
    thread {
        for ((i, s) in steps.withIndex()) {
            t.st[i] = 1; Thread.sleep(if (UiFlags.reduceMotion) 120 else 380)
            val err = try { s.run() } catch (e: Throwable) { e.message ?: "error" }
            if (err != null) { t.st[i] = 3; t.ok = false; t.note = err; break }
            t.st[i] = 2
        }
        t.done = true; after?.invoke(t.ok)
    }
}

class Mon { val cpu = mutableStateListOf<Float>(); val ram = mutableStateListOf<Float>(); val tmp = mutableStateListOf<Float>()
    var cpuNow by mutableStateOf<Int?>(null); var cpuTries by mutableStateOf(0); var peak by mutableStateOf<Float?>(null); var now by mutableStateOf(System.currentTimeMillis()); val start = System.currentTimeMillis()
    fun add(l: MutableList<Float>, v: Float) { l.add(v); if (l.size > 40) l.removeAt(0) } }

class AS(val c: Context, val sp: SharedPreferences) {
    val hzs = Env.hz(c); val maxHz = hzs.max(); val tk = TaskState(); val mon = Mon()
    var pkg by mutableStateOf(sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG)
    var scale by mutableStateOf(sp.getFloat("sc", 0.8f)); var fps by mutableStateOf(clampFps(sp.getInt("fps", 60), maxHz))
    var mode by mutableStateOf(sp.getString("md", "Performance") ?: "Performance"); var hz by mutableStateOf(sp.getInt("hz", maxHz))
    var prof by mutableStateOf(sp.getString("prof", "") ?: ""); var snap by mutableStateOf(Sys.snap(c)); var log by mutableStateOf("Ready.")
    var keyLeft by mutableStateOf(99999L); var keyRole by mutableStateOf("F"); var tick by mutableStateOf(0); val mine = mutableStateListOf<MyProf>().also { it.addAll(ProfStore.load(sp)) }; var editing by mutableStateOf<MyProf?>(null)
    fun saveMine() { ProfStore.save(sp, mine) }
    fun applyMine(p: MyProf) { scale = p.sc; fps = clampFps(p.fps, maxHz); mode = p.mode; prof = "my:" + p.id; apply("Profile " + p.name) }
    val launchCmd: String get() = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    fun save() { sp.edit().putFloat("sc", scale).putInt("fps", fps).putString("md", mode).putString("pkg", pkg).putInt("hz", hz).putString("prof", prof).apply() }
    fun run(cmd: String) { log = "Running..."; thread { val r = Sh.run(cmd); log = "$ $cmd\n$r" } }
    fun after(title: String): (Boolean) -> Unit = { ok -> if (ok) { Hist.add(sp, title); sp.edit().putLong("lastopt", System.currentTimeMillis()).apply() } }
    fun apply(title: String) { save(); runTask(tk, title, applySteps(pkg, scale, fps, mode), after(title + " applied")) }
    fun quick(title: String, label: String, cmd: String) { runTask(tk, title, listOf(Step(label, "Applied") { val r = Sh.run(cmd); log = "$ $cmd\n$r"; if (bad(r)) r.trim() else null }), { ok -> if (ok) Hist.add(sp, title) }) }
    fun boost(launch: Boolean) {
        save(); val s = Sys.snap(c); snap = s; val tx = s.tempC?.let { " · " + it.toInt() + "°C" } ?: ""
        val st = mutableListOf(Step("Checking device status", "RAM ${s.ramPct}% · battery " + (if (s.batt < 0) "n/a" else "${s.batt}%") + tx) { null },
            Step("Checking Shizuku", "Connected") { if (Sh.running()) null else "Shizuku is not running. Start it in the Shizuku app." },
            Step("Checking permissions", "Authorized") { if (Sh.granted()) null else "Permission missing. Authorize NACHT X in Shizuku." },
            Step("Cleaning background apps", "Done") { Sh.run("am kill-all"); null })
        st.addAll(applySteps(pkg, scale, fps, mode))
        if (launch) st.add(Step("Preparing CODM", "Launched") { Sh.run(launchCmd); null })
        runTask(tk, if (launch) "Boost and launch" else "Boost", st, after(if (launch) "Boost applied, CODM launched" else "Boost applied"))
    }
    fun importText(raw: String) {
        val p = parseProfile(raw)
        if (p == null) { runTask(tk, "Import profile", listOf(Step("Reading profile") { "Not a valid Unoxidized profile (UXP1 code or JSON)." })); return }
        scale = Math.round(p.sc * 20) / 20f; fps = clampFps(p.fps, maxHz); mode = p.mode; prof = ""; save()
        runTask(tk, "Import ${p.name}", listOf(Step("Reading profile \"${p.name}\"", "Valid") { null }, Step("Optimizing for ${Env.device()}", "${ds(scale)}x · $fps FPS") { null }) + applySteps(pkg, scale, fps, mode), after("Imported profile ${p.name}"))
    }
}

class MainActivity : ComponentActivity() {
    var incoming by mutableStateOf<String?>(null)
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); incoming = intent?.getStringExtra("ux_key")
        val sp = getSharedPreferences("ux", 0); UiFlags.reduceMotion = sp.getBoolean("rm", false); UiFlags.haptics = sp.getBoolean("hp", true)
        setContent {
            MaterialTheme(darkColorScheme(primary = Glow, onPrimary = Color.White, background = Bg0, surface = Bg1, onSurface = Txt, onBackground = Txt, secondaryContainer = Color(0xFF242424), onSecondaryContainer = Txt)) {
                Surface(Modifier.fillMaxSize(), color = Bg0) { ToastRoot { TechBackground(painterResource(R.drawable.bg_char)) { Boot(this@MainActivity, incoming) { incoming = null } } } }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("ux_key")?.let { incoming = it } }
}

@Composable fun Metric(title: String, value: String, data: List<Float>, max: Float, col: Color, na: Boolean = false) = Glass {
    Row(verticalAlignment = Alignment.CenterVertically) { Cap(title); Spacer(Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp) }
    if (data.size > 1) Spark(data, max, col) else if (na) Text("Not available on this device.", color = Dim, fontSize = 12.sp) else Skel(Modifier.fillMaxWidth().height(40.dp))
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
        LogoImg(painterResource(R.drawable.logo), 170); Spacer(Modifier.height(10.dp))
        Text("NACHT X", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Text("SHADOW SYSTEM", color = Dim, fontSize = 12.sp, letterSpacing = 3.sp)
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
        Btn("OPEN NACHT X GENERATOR", false) { Env.openApp(c, "com.unoxidized.gen") }
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
            Text(if (done) "✓" else "$n", color = if (done || current) Color.White else Dim, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(body, color = Dim, fontSize = 12.sp) }
    }
    if (current) content()
}
@Composable fun GateUi(c: Context, inst: Boolean, run: Boolean, ok: Boolean) {
    val st = listOf(inst, run, ok); val done = st.count { it }; val cur = st.indexOfFirst { !it }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); LogoImg(painterResource(R.drawable.logo), 110)
        Text("AUTHORIZE ACCESS", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text("NACHT X changes game settings through Shizuku. Complete these 3 steps to unlock every feature.", color = Dim, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(done / 3f).fillMaxHeight().background(WhiteGrad)) }
        StepCard(1, "Install Shizuku", "The free app that grants access.", inst, cur == 0) { Btn("INSTALL SHIZUKU") { Env.installShizuku(c) } }
        StepCard(2, "Pair and start Shizuku", "Wireless debugging, then Start.", run, cur == 1) {
            Text("1. Turn on Wireless debugging.\n2. In Shizuku tap Pairing and enter the code.\n3. Tap 'Start via Wireless debugging'.", fontSize = 13.sp, color = Color(0xFFCCCCCC))
            Btn("OPEN DEVELOPER OPTIONS", false) { Env.openDev(c) }; Btn("OPEN SHIZUKU") { Env.openApp(c, Env.SZ) } }
        StepCard(3, "Authorize NACHT X", "Allow this app inside Shizuku.", ok, cur == 2) {
            Btn("AUTHORIZE ACCESS") { Sh.ask() }
            Text("No popup? Open Shizuku > Authorized apps and switch NACHT X on.", fontSize = 12.sp, color = Dim) }
        Text("This screen unlocks automatically when all steps are done. If Shizuku stops, you come back here.", color = Dim, fontSize = 12.sp)
        Watermark()
    }
}

@Composable fun TaskDialog(a: AS) {
    val t = a.tk
    if (!t.open) return
    Dialog({ if (t.done) t.open = false }, DialogProperties(dismissOnBackPress = t.done, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Glass(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Cap(t.title); Text(if (!t.done) "WORKING..." else if (t.ok) "COMPLETE" else "NEEDS ATTENTION", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp, color = if (!t.done) Glow else if (t.ok) Ok else Bad) }
                if (!t.done) CircularProgressIndicator(color = Glow, modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
            val d = t.st.count { it == 2 }; val n = maxOf(t.st.size, 1)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(d / n.toFloat()).fillMaxHeight().background(if (t.ok) WhiteGrad else Brush.horizontalGradient(listOf(Bad, Bad)))) }
            t.labels.forEachIndexed { i, l -> val s = t.st.getOrElse(i) { 0 }
                Row(verticalAlignment = Alignment.Top) {
                    Text(when (s) { 1 -> "◐"; 2 -> "✓"; 3 -> "✕"; else -> "○" }, Modifier.width(28.dp), color = when (s) { 2 -> Ok; 3 -> Bad; 1 -> Glow; else -> Dim }, fontWeight = FontWeight.Black)
                    Column { Text(l, color = if (s == 0) Dim else Glow, fontSize = 14.sp); if (s == 2) Text(t.oks.getOrElse(i) { "" }, color = Dim, fontSize = 11.sp) } } }
            if (t.done) {
                if (!t.ok) Text(t.note, color = Bad, fontSize = 13.sp)
                if (t.ok) { Btn("LAUNCH CODM") { t.open = false; a.run(a.launchCmd) }; Btn("RESTART CODM", false) { t.open = false; a.run("am force-stop ${a.pkg}; ${a.launchCmd}") } }
                Btn(if (t.ok) "CLOSE" else "OK", false) { t.open = false }
            }
        }
    }
}

@Composable fun HomePage(a: AS, go: (Int) -> Unit) {
    val s = a.snap; val ready = Sh.granted(); val sc = Sys.score(s, ready); val col = if (sc >= 80) Ok else if (sc >= 60) Warn else Bad
    AnnounceCard(); UpdateCard("6.0"); KeyReminder(a); RankCard(a)
    Glass { Row(verticalAlignment = Alignment.CenterVertically) {
        Gauge(sc / 100f, sc, "PERFORMANCE SCORE", col, 150); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (sc >= 80) "SYSTEM READY" else if (sc >= 60) "FAIR" else "NEEDS ATTENTION", col)
            Chip(if (ready) "SHIZUKU CONNECTED" else "PERMISSION REQUIRED", if (ready) Ok else Bad)
            Text("Based on RAM, heat, battery and storage. It is not an FPS prediction.", color = Dim, fontSize = 11.sp) } } }
    if (Session.active) Glass { Row(verticalAlignment = Alignment.CenterVertically) { Chip("CODM SESSION LIVE", Ok); Spacer(Modifier.weight(1f)); Text(Session.fpsNow?.let { "$it FPS" } ?: "Measuring", fontWeight = FontWeight.Black) } }
    Btn("BOOST & LAUNCH CODM") { a.boost(true) }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "⚡", "BOOST", "Optimize, don't launch") { a.boost(false) }; Tile(Modifier.weight(1f), "🎮", "CODM PROFILE", "Choose a profile") { go(1) } }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "📊", "LIVE MONITOR", "CPU, RAM, heat") { go(2) }; Tile(Modifier.weight(1f), "🧰", "TOOLS", "Info and restore") { go(3) } }
    Cap("Device status")
    StatRow("RAM" to "${s.ramPct}%", "BATTERY" to (if (s.batt < 0) "Not available" else "${s.batt}%"))
    StatRow("TEMP" to (s.tempC?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available"), "STORAGE" to "${s.storPct}%")
    StatRow("SCREEN" to "${s.hz} Hz", "PROFILE" to (profLabel(a)))
    Text("Last optimization: ${fmt(a.sp.getLong("lastopt", 0L))}", color = Dim, fontSize = 12.sp)
}

@Composable fun GamePage(a: AS) {
    var inst by remember { mutableStateOf<Boolean?>(null) }; var runn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(a.pkg, a.tick) { while (true) { val i = withContext(Dispatchers.IO) { Sys.installed(a.pkg) }; val r = withContext(Dispatchers.IO) { Sys.running(a.pkg) }; inst = i; runn = r; delay(4000) } }
    H("CODM", "Your game control center.")
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("🎮", fontSize = 30.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Call of Duty: Mobile", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(a.pkg, color = Dim, fontSize = 11.sp) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inst == null) Skel(Modifier.size(100.dp, 24.dp)) else Chip(if (inst == true) "INSTALLED" else "NOT INSTALLED", if (inst == true) Ok else Bad)
            if (runn == null) Skel(Modifier.size(100.dp, 24.dp)) else Chip(if (runn == true) "RUNNING" else "NOT RUNNING", if (runn == true) Ok else Dim) }
        if (inst == false) Text("Not found. If you use another CODM version, change the package in Tools.", color = Warn, fontSize = 12.sp)
        Text("Last optimization: ${fmt(a.sp.getLong("lastopt", 0L))}", color = Dim, fontSize = 12.sp)
        Text("Last launch detected: ${fmt(a.sp.getLong("lastlaunch", 0L))}", color = Dim, fontSize = 12.sp)
        Btn("LAUNCH CODM", true, inst == true) { a.run(a.launchCmd) }; Btn("RESTART CODM", false, inst == true) { a.run("am force-stop ${a.pkg}; ${a.launchCmd}") }
    }
    Cap("Profiles")
    PROFS.forEach { p -> val on = a.prof == p.key; val sh = RoundedCornerShape(20.dp)
        Column(Modifier.fillMaxWidth().press { a.scale = p.sc; a.fps = clampFps(p.fps, a.maxHz); a.mode = p.mode; a.prof = p.key; a.apply("Profile ${p.name.lowercase()}") }.clip(sh)
            .background(if (on) Brush.linearGradient(listOf(Color(0xFF2E2E2E), Color(0xFF151515))) else Brush.linearGradient(listOf(Color(0xFF161616), Color(0xFF0C0C0C)))).border(if (on) 1.5.dp else 1.dp, if (on) Glow else Ln, sh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Black, letterSpacing = 1.sp); if (on) Chip("SELECTED", Ok) }
            Text(p.desc, color = Dim, fontSize = 12.sp); Text("${ds(p.sc)}x · up to ${clampFps(p.fps, a.maxHz)} FPS · ${p.mode}", color = Color(0xFFBBBBBB), fontSize = 11.sp)
        } }
    Text("Profiles don't guarantee a specific FPS. Results depend on your device, heat and the game.", color = Dim, fontSize = 11.sp)
    MyProfilesSection(a)
    Cap("Fine tune")
    Glass { Cap("Render scale"); Text("${ds(a.scale)}x", fontSize = 40.sp, fontWeight = FontWeight.Black)
        Slider(a.scale, { a.scale = (Math.round(it * 20) / 20f); a.prof = "" }, valueRange = 0.3f..1f, colors = SliderDefaults.colors(thumbColor = Glow, activeTrackColor = Glow, inactiveTrackColor = Color(0xFF2A2A2A)))
        Cap("FPS target"); Pick(fpsOpts(a.maxHz).map { it.toString() }, a.fps.toString()) { a.fps = it.toInt(); a.prof = "" }
        if (a.maxHz <= 60) Text("Your screen is ${a.maxHz} Hz, so higher targets are hidden.", color = Dim, fontSize = 12.sp)
        Cap("Mode"); Pick(listOf("Performance", "Battery"), a.mode) { a.mode = it; a.prof = "" }
        Cap("Refresh rate (Hz)"); Pick(a.hzs.map { it.toString() }, a.hz.toString()) { a.hz = it.toInt() }
        Btn("APPLY SETTINGS") { a.apply("Custom settings") }
        Btn("APPLY REFRESH RATE", false) { a.save(); a.quick("Refresh rate", "Setting ${a.hz} Hz", "settings put system min_refresh_rate ${a.hz}; settings put system peak_refresh_rate ${a.hz}") } }
    Cap("Graphics guidance")
    Glass { Text("Set these inside CODM. They are your choices, the app never edits game files.", color = Dim, fontSize = 12.sp)
        listOf("Frame rate" to "Highest available", "Graphics quality" to "Low or Medium", "Depth of field" to "Off", "Anti-aliasing" to "Off", "Bloom" to "Off", "Ragdoll" to "Off", "Real-time shadows" to "Off").forEach { (k, v) ->
            Row { Text(k, Modifier.weight(1f), fontSize = 14.sp); Text(v, color = Color(0xFFBBBBBB), fontSize = 13.sp, fontWeight = FontWeight.Bold) } } }
}

@Composable fun MonitorPage(a: AS) {
    val m = a.mon; val s = a.snap; val sec = ((m.now - m.start) / 1000).toInt()
    val temp = s.tempC; val th = if (temp == null) "Not available" else if (temp < 38) "NORMAL" else if (temp < 42) "WARM" else "HOT"
    H("Live monitor", "Real readings only. Anything the phone doesn't expose says so.")
    StatRow("SESSION" to "%02d:%02d".format(sec / 60, sec % 60), "PEAK TEMP" to (m.peak?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available"))
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Thermal"); Spacer(Modifier.weight(1f)); Chip(th, if (th == "NORMAL") Ok else if (th == "WARM") Warn else if (th == "HOT") Bad else Dim) }
        if (th == "HOT") Text("Your phone is hot. Consider the Battery Saver profile and a short break.", color = Bad, fontSize = 12.sp) }
    Metric("CPU", m.cpuNow?.let { "$it%" } ?: "--", m.cpu, 100f, Glow, m.cpuTries >= 4 && m.cpu.isEmpty())
    Metric("RAM", "${s.ramPct}%", m.ram, 100f, Color(0xFF9EC5FF))
    Metric("BATTERY TEMP", temp?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available", m.tmp, 60f, Warn, temp == null)
    StatRow("BATTERY" to (if (s.batt < 0) "Not available" else "${s.batt}%" + if (s.charging) " ⚡" else ""), "STORAGE" to "${s.storPct}% of ${s.storGb} GB")
    FpsCard(a)
}

@Composable fun ToolsPage(a: AS) {
    val s = a.snap; val cb = LocalClipboardManager.current; val toast = LocalToast.current
    var anim by remember { mutableStateOf<Boolean?>(null) }; var dnd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { anim = withContext(Dispatchers.IO) { Sh.run("settings get global window_animation_scale").trim().startsWith("0.5") } }
    val run = Sh.running(); val gr = Sh.granted()
    H("Tools", "System info, helpers and restore.")
    Cap("System")
    StatRow("DEVICE" to Env.device(), "ANDROID" to android.os.Build.VERSION.RELEASE)
    StatRow("CHIP" to Env.soc(), "RAM" to "${s.ramMb} MB")
    StatRow("STORAGE" to "${s.storGb} GB", "SCREEN" to a.hzs.joinToString("/") + " Hz")
    Cap("Performance")
    Glass { Btn("CLEAN BACKGROUND APPS") { a.quick("Clean background apps", "Closing background apps", "am kill-all") }
        Toggle("Faster animations", if (anim == null) "Reading current state..." else "Cuts system animation time in half", anim == true) { anim = it; val v = if (it) "0.5" else "1.0"
            a.quick("Animation speed", "Setting animations to ${v}x", "settings put global window_animation_scale $v; settings put global transition_animation_scale $v; settings put global animator_duration_scale $v") }
        Toggle("Silence notifications", "Do Not Disturb while you play", dnd) { dnd = it; a.quick("Do Not Disturb", "Setting Do Not Disturb", "cmd notification set_dnd ${if (it) "priority" else "off"}") } }
    Cap("Connection")
    Glass { listOf("Shizuku installed" to Env.installed(a.c), "Shizuku running" to run, "App authorized" to gr).forEach { (k, v) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k, Modifier.weight(1f)); Chip(if (v) "YES" else "NO", if (v) Ok else Bad) } }
        Text("Access level: ${Env.access()}", color = Dim, fontSize = 12.sp) }
    Cap("Game package")
    Glass { OutlinedTextField(a.pkg, { a.pkg = it.trim() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(), singleLine = true)
        Btn("CHECK CURRENT CONFIG", false) { a.run("device_config get game_overlay ${a.pkg}; cmd game mode ${a.pkg}") } }
    Cap("Utility")
    Glass { Btn("COPY DIAGNOSTICS", false) { cb.setText(AnnotatedString("NACHT X diagnostics\nDevice: ${Env.device()}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nChip: ${Env.soc()}\nRAM: ${s.ramMb} MB (${s.ramPct}% used)\nScreen: ${a.hzs.joinToString("/")} Hz\nShizuku running: $run, authorized: $gr, access: ${Env.access()}\nProfile: ${ds(a.scale)}x, ${a.fps} FPS, ${a.mode}\nPackage: ${a.pkg}")); toast("Diagnostics copied") }
        Btn("RESTORE DEFAULTS", false) { runTask(a.tk, "Restore defaults", listOf(
            Step("Removing the CODM profile", "Removed") { val r = Sh.run("cmd game reset ${a.pkg}; device_config delete game_overlay ${a.pkg}"); if (r.startsWith("Error")) r else null },
            Step("Restoring animation speed", "1.0x") { Sh.run("settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0"); null },
            Step("Restoring refresh rate", "System default") { Sh.run("settings delete system min_refresh_rate; settings delete system peak_refresh_rate"); null }), a.after("Settings restored")); anim = false; a.prof = ""; a.save() }
        Text("Last command output:", color = Dim, fontSize = 11.sp); Text(a.log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (bad(a.log)) Bad else Color(0xFF9EE6B5)) }
}

@Composable fun ImportPage(a: AS) {
    val cb = LocalClipboardManager.current; val toast = LocalToast.current; var code by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) a.importText(try { a.c.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }) }
    H("Import", "Load a booster profile and apply it to the game.")
    Glass { Btn("IMPORT PROFILE FILE") { picker.launch(arrayOf("*/*")) }
        Cap("Or paste a profile code"); OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), placeholder = { Text("UXP1:...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 3)
        Btn("IMPORT CODE", false, code.isNotBlank()) { a.importText(code) } }
    Glass { Cap("Share your setup"); Text("Copy a code of your current settings to send to friends.", color = Dim, fontSize = 12.sp)
        Btn("COPY MY PROFILE CODE", false) { val o = org.json.JSONObject().put("name", "My profile").put("scale", a.scale.toDouble()).put("fps", a.fps).put("mode", modeName(a.mode))
            cb.setText(AnnotatedString("UXP1:" + android.util.Base64.encodeToString(o.toString().toByteArray(), android.util.Base64.NO_WRAP))); toast("Profile code copied") } }
}

@Composable fun HistPage(a: AS) {
    var tick by remember { mutableStateOf(0) }; val items = remember(tick, a.tick) { Hist.list(a.sp) }
    val dk = { t: Long -> java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(t)) }
    val now = System.currentTimeMillis(); val today = dk(now); val yest = dk(now - 86400000L)
    H("History", "What this app actually applied on your phone.")
    if (items.isEmpty()) Glass { Text("No history yet", fontWeight = FontWeight.Bold); Text("Applied profiles, boosts and restores will show up here.", color = Dim, fontSize = 12.sp) }
    else { var last = ""
        items.forEach { (t, m) -> val k = dk(t); if (k != last) { last = k; Cap(if (k == today) "Today" else if (k == yest) "Yesterday" else java.text.DateFormat.getDateInstance().format(java.util.Date(t))) }
            Glass(Modifier.swipeDelete { Hist.remove(a.sp, t); tick++ }) { Text(m, fontWeight = FontWeight.Bold); Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(t)), color = Dim, fontSize = 12.sp) } }
        Btn("CLEAR HISTORY", false) { Hist.clear(a.sp); tick++ }
        Text("Swipe an entry left or right to delete it.", color = Dim, fontSize = 11.sp) }
}

@Composable fun GuidePage(a: AS) {
    var open by remember { mutableStateOf(-1) }; var codm by remember { mutableStateOf(false) }
    LaunchedEffect(a.pkg) { codm = withContext(Dispatchers.IO) { Sys.installed(a.pkg) } }
    val checks = listOf("Shizuku installed" to Env.installed(a.c), "Shizuku running" to Sh.running(), "App authorized" to Sh.granted(), "CODM found" to codm, "Profile applied" to (a.sp.getLong("lastopt", 0L) > 0))
    val pct = checks.count { it.second } * 100 / checks.size
    H("Setup guide", "Live checks, not guesses.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Setup progress"); Spacer(Modifier.weight(1f)); Text("$pct%", fontWeight = FontWeight.Black, fontSize = 20.sp) }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(WhiteGrad)) }
        checks.forEach { (k, v) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k, Modifier.weight(1f), fontSize = 14.sp); Chip(if (v) "OK" else "TODO", if (v) Ok else Warn) } } }
    GUIDE.forEachIndexed { i, (t, b) -> Glass(Modifier.press { open = if (open == i) -1 else i }) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Black, color = Dim); Text(t, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(if (open == i) "−" else "+", color = Dim, fontSize = 20.sp) }
        if (open == i) Text(b, color = Color(0xFFCCCCCC), fontSize = 13.sp) } }
    Cap("Troubleshooting")
    Glass { listOf("Shizuku keeps stopping" to "Restart it after every reboot: Shizuku > Start via Wireless debugging.",
        "Authorize popup never appears" to "Open Shizuku > Authorized apps and switch NACHT X on.",
        "Profile not kept by Android" to "Some phones or CODM versions reject Game Mode profiles. The popup shows the real reason.",
        "Key expired" to "Open NACHT X Generator and make a new weekly key.").forEach { (q, ans) -> Text(q, fontWeight = FontWeight.Bold); Text(ans, color = Dim, fontSize = 12.sp) } }
}

@Composable fun SettingsPage(a: AS) {
    var rm by remember { mutableStateOf(UiFlags.reduceMotion) }; var hp by remember { mutableStateOf(UiFlags.haptics) }
    H("Settings", "Make the app yours.")
    Glass { Cap("Appearance")
        Toggle("Reduce motion", "Calmer background and shorter transitions", rm) { rm = it; UiFlags.reduceMotion = it; a.sp.edit().putBoolean("rm", it).apply() }
        Toggle("Haptic feedback", "Vibrate lightly on button taps", hp) { hp = it; UiFlags.haptics = it; a.sp.edit().putBoolean("hp", it).apply() } }
    TrackingSettings(a)
    Cap("Transparency")
    Glass { Text("What NACHT X does", fontWeight = FontWeight.Bold)
        Text("• Writes an Android Game Mode profile for CODM (resolution scale and FPS cap).\n• Closes background apps and changes system animation or refresh settings only when you ask.\n• Reads RAM, battery, temperature and storage from Android.", color = Color(0xFFCCCCCC), fontSize = 12.sp)
        Text("What it never does", fontWeight = FontWeight.Bold)
        Text("• Edit CODM files, bypass anti-cheat, or give gameplay advantages.\n• Invent FPS, temperature or success results.\n• Promise a specific FPS gain.", color = Color(0xFFCCCCCC), fontSize = 12.sp) }
}

@Composable fun AccountPage(a: AS, key: String, logout: () -> Unit) {
    val dev = Lic.deviceId(a.c); val r = Lic.check(dev, key)
    H("Account", "Your access.")
    Glass { Cap("Device ID"); Row(verticalAlignment = Alignment.CenterVertically) { Text(dev, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 18.sp); CopyChip(dev) }
        Cap("Access level"); Text(Lic.roleName(r.role), fontWeight = FontWeight.Bold)
        Cap("Key expires"); Text(r.untilText + (if (r.left > 9000) "" else "  (${r.left} days left)"))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(r.frac).fillMaxHeight().background(WhiteGrad)) }
        Cap("Made by"); Text("@UnoxidizedX on Telegram", fontWeight = FontWeight.Bold) }
    LinkedGen(a.c); Cap("Showcase"); ShowcaseCard("6.0")
    Btn("OPEN NACHT X GENERATOR", false) { Env.openApp(a.c, "com.unoxidized.gen") }
    Btn("OPEN NACHT X INFORMATION", false) { Env.openApp(a.c, "com.unoxidized.info") }
    Btn("LOG OUT", false, onClick = logout)
}

@Composable fun Main(c: Context, sp: SharedPreferences, key: String, logout: () -> Unit) {
    val a = remember { AS(c, sp) }; val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    val pages = listOf("Home" to "🏠", "Game" to "🎮", "Monitor" to "📊", "Tools" to "🧰", "Sessions" to "⏱️", "Import" to "📥", "History" to "🕘", "Guide" to "📖", "Settings" to "⚙️", "Account" to "👤")
    var page by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { a.snap = Sys.snap(c); delay(2500) } }
    LaunchedEffect(page) { if (page == 2) while (true) {
        val s = Sys.snap(c); a.snap = s; val m = a.mon; m.now = System.currentTimeMillis(); m.add(m.ram, s.ramPct.toFloat())
        s.tempC?.let { m.add(m.tmp, it); if ((m.peak ?: 0f) < it) m.peak = it }
        val cpu = withContext(Dispatchers.IO) { Sys.cpu() }; m.cpuTries++; if (cpu != null) { m.cpuNow = cpu; m.add(m.cpu, cpu.toFloat()) }
        delay(1500) } }
    BackHandler(enabled = page != 0) { page = 0 }
    LaunchedEffect(key) { val r = Lic.check(Lic.deviceId(c), key); a.keyLeft = r.left; a.keyRole = r.role }
    val toast = LocalToast.current
    val refresh: suspend (Int) -> Unit = { p -> a.snap = Sys.snap(c); a.tick++; if (p == 2) { a.mon.cpu.clear(); a.mon.ram.clear(); a.mon.tmp.clear(); a.mon.cpuTries = 0 }; delay(500); toast("Updated") }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (sp.getBoolean("track", true)) { if (android.os.Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notif.launch(android.Manifest.permission.POST_NOTIFICATIONS); SessionService.start(c) } }
    val go: (Int) -> Unit = { i -> page = i }
    TaskDialog(a); ProfEditor(a)
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF080808)) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 52); Spacer(Modifier.width(12.dp))
            Column { Text("NACHT X", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp); Text("SHADOW SYSTEM", color = Dim, fontSize = 9.sp, letterSpacing = 1.5.sp) } }
        pages.forEachIndexed { i, (n, ic) -> NavigationDrawerItem({ Text("$ic   $n", fontWeight = FontWeight.Bold) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF242424), unselectedContainerColor = Color.Transparent, selectedTextColor = Glow, unselectedTextColor = Color(0xFFBBBBBB))) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoImg(painterResource(R.drawable.logo), 40); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("NACHT X", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 3.sp); Text("SHADOW SYSTEM", color = Dim, fontSize = 9.sp, letterSpacing = 2.sp) }
                val ok = Sh.granted(); Chip(if (ok) "CONNECTED" else "NO ACCESS", if (ok) Ok else Bad)
            }
            Box(Modifier.weight(1f).swipeTabs({ if (page in 1..3) { page-- } else if (page >= 4) { page = 0 } }, { if (page < 3) { page++ } })) {
                PageSwitch(page) { p -> PullRefresh({ refresh(p) }) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                    when (p) { 0 -> HomePage(a, go); 1 -> GamePage(a); 2 -> MonitorPage(a); 3 -> ToolsPage(a); 4 -> SessionsPage(a); 5 -> ImportPage(a); 6 -> HistPage(a); 7 -> GuidePage(a); 8 -> SettingsPage(a); else -> AccountPage(a, key, logout) }
                    Spacer(Modifier.height(8.dp)) } } }
            }
            Box(Modifier.fillMaxWidth(), Alignment.Center) { Watermark() }
            NavBar(listOf("🏠" to "HOME", "🎮" to "GAME", "📊" to "MONITOR", "🧰" to "TOOLS", "☰" to "MORE"), if (page < 4) page else 4) { i -> if (i == 4) { scope.launch { drawer.open() } } else { page = i } }
        }
    }
}
