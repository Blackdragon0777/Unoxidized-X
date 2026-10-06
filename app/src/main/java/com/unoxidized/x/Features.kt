package com.unoxidized.x
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.concurrent.thread
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unoxidized.shared.*
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MyProf(val id: Long, val name: String, val sc: Float, val fps: Int, val mode: String)
object ProfStore {
    fun load(sp: android.content.SharedPreferences): List<MyProf> = try { val a = JSONArray(sp.getString("myprofs", "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); MyProf(o.getLong("id"), o.getString("n"), o.getDouble("sc").toFloat(), o.getInt("fps"), o.getString("md")) } } catch (e: Exception) { emptyList() }
    fun save(sp: android.content.SharedPreferences, l: List<MyProf>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("sc", it.sc.toDouble()).put("fps", it.fps).put("md", it.mode)) }; sp.edit().putString("myprofs", a.toString()).apply() }
}
fun profLabel(a: AS): String = PROFS.firstOrNull { it.key == a.prof }?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: a.mine.firstOrNull { "my:" + it.id == a.prof }?.name ?: "Custom"

@Composable fun MyProfilesSection(a: AS) {
    Row(verticalAlignment = Alignment.CenterVertically) { Cap("My profiles"); Spacer(Modifier.weight(1f)); MiniBtn("+ CREATE") { a.editing = MyProf(System.currentTimeMillis(), "", a.scale, a.fps, a.mode) } }
    if (a.mine.isEmpty()) Glass { Text("No saved profiles yet", fontWeight = FontWeight.Bold); Text("Set render scale, FPS and mode in Fine tune, then tap + CREATE to save them under your own name.", color = Dim, fontSize = 12.sp) }
    a.mine.toList().forEach { p -> val on = a.prof == "my:" + p.id
        Glass(Modifier.swipeDelete { a.mine.remove(p); a.saveMine(); if (on) a.prof = "" }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Black); if (on) Chip("SELECTED", Ok) }
            Text("${ds(p.sc)}x · ${p.fps} FPS · ${p.mode}", color = Dim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("APPLY") { a.applyMine(p) }; MiniBtn("EDIT") { a.editing = p }
                MiniBtn("DUPLICATE") { a.mine.add(p.copy(id = System.currentTimeMillis(), name = p.name + " copy")); a.saveMine() }; MiniBtn("DELETE") { a.mine.remove(p); a.saveMine() } } } }
    if (a.mine.isNotEmpty()) Text("Swipe a profile left or right to delete it.", color = Dim, fontSize = 11.sp)
}

@Composable fun ProfEditor(a: AS) {
    val p = a.editing ?: return
    var name by remember(p.id) { mutableStateOf(p.name) }; var sc by remember(p.id) { mutableStateOf(p.sc) }; var fps by remember(p.id) { mutableStateOf(p.fps) }; var mode by remember(p.id) { mutableStateOf(p.mode) }
    Dialog({ a.editing = null }, DialogProperties(usePlatformDefaultWidth = false)) {
        Glass(Modifier.padding(20.dp)) {
            Cap(if (a.mine.any { it.id == p.id }) "Edit profile" else "New profile")
            OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), placeholder = { Text("Profile name") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            Text("Render scale ${ds(sc)}x", fontWeight = FontWeight.Bold)
            Slider(sc, { sc = Math.round(it * 20) / 20f }, valueRange = 0.3f..1f, colors = SliderDefaults.colors(thumbColor = Glow, activeTrackColor = Glow, inactiveTrackColor = Color(0xFF2A2A2A)))
            Cap("FPS target"); Pick(fpsOpts(a.maxHz).map { it.toString() }, clampFps(fps, a.maxHz).toString()) { fps = it.toInt() }
            Cap("Mode"); Pick(listOf("Performance", "Battery"), mode) { mode = it }
            Btn("SAVE PROFILE", true, name.isNotBlank()) { val np = MyProf(p.id, name.trim(), sc, clampFps(fps, a.maxHz), mode); val i = a.mine.indexOfFirst { it.id == p.id }; if (i >= 0) a.mine[i] = np else a.mine.add(np); a.saveMine(); a.editing = null }
            Btn("RESET VALUES", false) { sc = 0.8f; fps = clampFps(60, a.maxHz); mode = "Performance" }
            Btn("CANCEL", false) { a.editing = null }
        }
    }
}

@Composable fun FpsCard(a: AS) {
    val on = Session.active
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("FPS and frame-time"); Spacer(Modifier.weight(1f)); Chip(if (on) "LIVE" else if (Session.watching) "WATCHING" else "OFF", if (on) Ok else Dim) }
        if (on) { Text(Session.fpsNow?.toString() ?: "--", fontSize = 44.sp, fontWeight = FontWeight.Black); Text(Session.fpsNote, color = Dim, fontSize = 11.sp)
            if (Session.fpsHist.size > 1) Spark(Session.fpsHist, maxOf(a.maxHz, 60).toFloat(), Glow) }
        else { Text(if (Session.watching) "Start CODM and recording begins automatically." else "Session tracking is off.", color = Color(0xFFCCCCCC), fontSize = 13.sp)
            if (!Session.watching) Btn("START TRACKING") { a.sp.edit().putBoolean("track", true).apply(); SessionService.start(a.c) } }
        var diag by remember { mutableStateOf("") }
        Btn("TEST FPS NOW", false) { diag = "Testing... keep CODM open for 5 seconds."; thread { diag = Fps.diagnose(a.pkg) } }
        if (diag.isNotEmpty()) Text(diag, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFCCCCCC))
        Text("Frame data comes from Android's SurfaceFlinger through Shizuku.", color = Dim, fontSize = 11.sp)
    }
}

@Composable fun TrackingSettings(a: AS) {
    var on by remember { mutableStateOf(a.sp.getBoolean("track", true)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Glass { Cap("Session tracking")
        Toggle("Detect CODM launches", "A small background service notices when CODM starts and records the session.", on) { on = it; a.sp.edit().putBoolean("track", it).apply()
            if (it) { if (Build.VERSION.SDK_INT >= 33) perm.launch(android.Manifest.permission.POST_NOTIFICATIONS); SessionService.start(a.c) } else { SessionService.stop(a.c) } }
        Text("Status: " + Session.status, color = Dim, fontSize = 12.sp) }
}

fun stability(r: SessionRec): String = r.jank?.let { if (it <= 3) "Good" else if (it <= 8) "Fair" else "Poor" } ?: "Not available"

@Composable fun CmpRow(label: String, b: Double?, a: Double?, unit: String, higherBetter: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFFCCCCCC))
        Text((b?.let { "%.0f".format(java.util.Locale.US, it) } ?: "n/a") + " → " + (a?.let { "%.0f".format(java.util.Locale.US, it) } ?: "n/a") + unit, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        if (b != null && a != null && b != 0.0) { val d = (a - b) / b * 100; val good = if (higherBetter) d > 0.5 else d < -0.5; val worse = if (higherBetter) d < -0.5 else d > 0.5
            Chip((if (d >= 0) "+" else "") + "%.0f".format(java.util.Locale.US, d) + "%", if (good) Ok else if (worse) Bad else Dim) } else { Chip("—", Dim) }
    }
}

@Composable fun CompareCard(list: List<SessionRec>) {
    val opt = list.filter { it.optimized }; val def = list.filter { !it.optimized }
    if (opt.isEmpty() || def.isEmpty()) {
        Glass { Text("Not enough data yet", fontWeight = FontWeight.Bold)
            Text("Play one CODM session with a profile ON and one with it OFF (Tools > Restore defaults). Then this page compares the real numbers.", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            StatRow("WITH PROFILE" to "${opt.size} sessions", "WITHOUT" to "${def.size} sessions") }
        return
    }
    fun avg(l: List<SessionRec>, f: (SessionRec) -> Double?): Double? { val v = l.mapNotNull(f); return if (v.isEmpty()) null else v.average() }
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Before and after"); Spacer(Modifier.weight(1f)); Text("OFF → ON", color = Dim, fontSize = 11.sp, letterSpacing = 1.sp) }
        CmpRow("Average FPS", avg(def) { it.avgFps?.toDouble() }, avg(opt) { it.avgFps?.toDouble() }, "", true)
        CmpRow("Lowest FPS", avg(def) { it.minFps?.toDouble() }, avg(opt) { it.minFps?.toDouble() }, "", true)
        CmpRow("Stutter frames", avg(def) { it.jank?.toDouble() }, avg(opt) { it.jank?.toDouble() }, "%", false)
        CmpRow("Peak temperature", avg(def) { it.peakTemp?.toDouble() }, avg(opt) { it.peakTemp?.toDouble() }, "°C", false)
        CmpRow("Battery per hour", avg(def) { r -> r.batt?.let { it * 3600.0 / maxOf(r.durSec, 1L) } }, avg(opt) { r -> r.batt?.let { it * 3600.0 / maxOf(r.durSec, 1L) } }, "%", false)
        Text("Averages over ${def.size} session(s) without and ${opt.size} with a profile. Matches differ, so more sessions give a fairer picture. This is measured data, not a promise.", color = Dim, fontSize = 11.sp) }
}

@Composable fun SessionsPage(a: AS) {
    var tab by remember { mutableStateOf(0) }; val list = remember(a.tick, Session.last) { SessStore.list(a.sp) }
    H("Sessions", "Real CODM sessions recorded on this phone.")
    Pick(listOf("Sessions", "Compare"), if (tab == 0) "Sessions" else "Compare") { tab = if (it == "Sessions") 0 else 1 }
    if (Session.active) { val sec = ((Session.now - Session.start) / 1000).toInt()
        Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Live session"); Spacer(Modifier.weight(1f)); Chip("RECORDING", Ok) }
            StatRow("TIME" to "%02d:%02d".format(sec / 60, sec % 60), "FPS" to (Session.fpsNow?.toString() ?: "Not available"))
            StatRow("TEMP" to (Session.tempNow?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available"), "PEAK" to (Session.peakTemp?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available")) } }
    if (tab == 1) { CompareCard(list); return }
    val r = list.firstOrNull()
    if (r == null && !Session.active) Glass { Text("No sessions yet", fontWeight = FontWeight.Bold); Text(if (Session.watching) "Open CODM. Recording starts by itself and ends when you close the game." else "Turn on session tracking in Settings, then open CODM.", color = Dim, fontSize = 12.sp) }
    if (r != null) { Glass { Cap("Session complete"); Text(fmt(r.start), fontWeight = FontWeight.Bold)
        StatRow("DURATION" to "${r.durSec / 60} min", "AVERAGE FPS" to (r.avgFps?.toString() ?: "Not available"))
        StatRow("LOWEST FPS" to (r.minFps?.toString() ?: "Not available"), "STABILITY" to stability(r))
        StatRow("PEAK TEMP" to (r.peakTemp?.let { "%.1f°C".format(java.util.Locale.US, it) } ?: "Not available"), "BATTERY USED" to (r.batt?.let { "$it%" } ?: "Not available"))
        Text("Profile: ${r.profile} · avg RAM ${r.avgRam}%" + (r.p95?.let { " · 95% of frames under ${it.toInt()} ms" } ?: ""), color = Dim, fontSize = 11.sp) } }
    if (list.size > 1) { Cap("Earlier sessions")
        list.drop(1).forEach { s -> Glass(Modifier.swipeDelete { SessStore.remove(a.sp, s.start); a.tick++ }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(fmt(s.start), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp); Chip(if (s.optimized) "PROFILE" else "DEFAULT", if (s.optimized) Ok else Dim) }
            Text("${s.durSec / 60} min · avg ${s.avgFps ?: "n/a"} FPS · ${s.profile}", color = Dim, fontSize = 12.sp) } }
        Text("Swipe a session left or right to delete it.", color = Dim, fontSize = 11.sp) }
}

@Composable fun LinkedGen(c: android.content.Context) {
    var b by remember { mutableStateOf<android.os.Bundle?>(null) }; val me = remember { Lic.deviceId(c) }
    LaunchedEffect(Unit) { while (true) { b = withContext(Dispatchers.IO) { SyncClient.pull(c, "com.unoxidized.gen.sync") }; delay(5000) } }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Linked app · Generator"); Spacer(Modifier.weight(1f)); Chip(if (b != null) "LINKED" else "NOT LINKED", if (b != null) Ok else Dim) }
        val x = b
        if (x == null) Text("Install NACHT X Generator on this phone to link it.", color = Dim, fontSize = 12.sp)
        else { val mine = x.getStringArrayList("ids")?.firstOrNull { it.startsWith(me) }?.substringAfter(":")?.toLongOrNull()
            StatRow("DEVICES" to x.getInt("total").toString(), "ACTIVE KEYS" to x.getInt("active").toString())
            StatRow("THIS PHONE" to (if (mine == null) "No key yet" else if (mine > 0) "$mine days left" else "Expired"), "VERSION" to (x.getString("ver") ?: "?")) }
        Btn("OPEN NACHT X GENERATOR", false) { Env.openApp(c, "com.unoxidized.gen") }
    }
}
@Composable fun KeyReminder(a: AS) {
    if (a.keyRole == "F" && a.keyLeft <= 2) Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Chip("KEY EXPIRING", Warn); Spacer(Modifier.weight(1f)); Text(if (a.keyLeft <= 0) "today" else "${a.keyLeft} day(s) left", fontWeight = FontWeight.Black) }
        Text("Open the Generator and make a new weekly key before this one ends.", color = Color(0xFFCCCCCC), fontSize = 12.sp)
        Btn("OPEN NACHT X GENERATOR") { Env.openApp(a.c, "com.unoxidized.gen") }
    }
}
@Composable fun RankCard(a: AS) {
    val pts = remember(a.tick, Session.last, a.tk.done) { SessStore.list(a.sp).size * 3 + Hist.list(a.sp).count { it.second.contains("Boost") } }
    val ranks = listOf(0 to "INITIATE", 6 to "SHADE", 18 to "PHANTOM", 45 to "NACHTLORD"); val cur = ranks.last { pts >= it.first }; val nxt = ranks.firstOrNull { it.first > pts }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Shadow rank"); Spacer(Modifier.weight(1f)); Chip(cur.second, Glow) }
        Text("$pts shadow points", fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (nxt != null) { Box(Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF1E1730))) { Box(Modifier.fillMaxWidth((pts - cur.first).toFloat() / (nxt.first - cur.first)).fillMaxHeight().background(WhiteGrad)) }
            Text("${nxt.first - pts} more to ${nxt.second}", color = Dim, fontSize = 12.sp) } else Text("Top rank reached.", color = Ok, fontSize = 12.sp)
        Text("Earn points by boosting (1 each) and recording CODM sessions (3 each).", color = Dim, fontSize = 11.sp)
    }
}
