package com.unoxidized.x
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.concurrent.thread

// ---------- live engine data (everything is read from the phone) ----------
data class EngInfo(val nw: Int = 0, val nh: Int = 0, val cw: Int = 0, val ch: Int = 0, val gpu: String = "", val gles: String = "", val vk: String = "", val cpu: Int? = null, val gpuLoad: Int? = null, val angle: Boolean = false) {
    val ratio: Float get() = if (nw > 0) cw.toFloat() / nw else 1f
}
object Eng {
    fun sec(o: String, n: String) = o.substringAfter("@@$n\n", "").substringBefore("@@").trim()
    private val size = Regex("(\\d+)x(\\d+)")
    fun static(p: EngInfo): EngInfo {
        val o = Sh.run("echo @@gl; dumpsys SurfaceFlinger | grep -m1 GLES; echo @@vk; pm list features | grep -i vulkan; echo @@end")
        val gl = sec(o, "gl"); val parts = gl.substringAfter("GLES:", "").split(",").map { it.trim() }
        val ver = Regex("OpenGL ES (\\d+\\.\\d+)").find(gl)?.groupValues?.get(1) ?: ""
        val vkRaw = Regex("vulkan\\.version=(\\d+)").find(sec(o, "vk"))?.groupValues?.get(1)?.toLongOrNull()
        return p.copy(gpu = parts.getOrNull(1) ?: "", gles = ver, vk = vkRaw?.let { "${it shr 22}.${(it shr 12) and 0x3ff}" } ?: "")
    }
    fun read(p: EngInfo): EngInfo {
        val o = Sh.run("echo @@wm; wm size; echo @@gpu; cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null; echo @@angle; pm path com.android.angle 2>/dev/null; echo @@end")
        val wm = sec(o, "wm").lines(); val ph = wm.firstOrNull { it.contains("Physical") }?.let { size.find(it) }; val ov = wm.firstOrNull { it.contains("Override") }?.let { size.find(it) }
        val nw = ph?.groupValues?.get(1)?.toIntOrNull() ?: p.nw; val nh = ph?.groupValues?.get(2)?.toIntOrNull() ?: p.nh
        return p.copy(nw = nw, nh = nh, cw = ov?.groupValues?.get(1)?.toIntOrNull() ?: nw, ch = ov?.groupValues?.get(2)?.toIntOrNull() ?: nh,
            gpuLoad = Regex("(\\d+)").find(sec(o, "gpu"))?.value?.toIntOrNull(), angle = sec(o, "angle").contains("package:"), cpu = Sys.cpu() ?: p.cpu)
    }
}

// ---------- restore points ----------
object Backup {
    private val keys = listOf("a1", "a2", "a3", "h1", "h2", "lp", "t1", "t2", "ap", "av", "ov")
    fun snapshot(pkg: String): JSONObject {
        val o = Sh.run("echo @@wm; wm size; echo @@a1; settings get global window_animation_scale; echo @@a2; settings get global transition_animation_scale; echo @@a3; settings get global animator_duration_scale; " +
            "echo @@h1; settings get system min_refresh_rate; echo @@h2; settings get system peak_refresh_rate; echo @@lp; settings get global low_power; echo @@t1; settings get secure long_press_timeout; " +
            "echo @@t2; settings get secure multi_press_timeout; echo @@ap; settings get global angle_gl_driver_selection_pkgs; echo @@av; settings get global angle_gl_driver_selection_values; echo @@ov; device_config get game_overlay $pkg; echo @@end")
        val j = JSONObject(); keys.forEach { j.put(it, Eng.sec(o, it)) }
        val ov = Eng.sec(o, "wm").lines().firstOrNull { it.contains("Override") }?.let { Regex("(\\d+x\\d+)").find(it)?.value }
        j.put("wm", ov ?: "none"); j.put("pkg", pkg); return j
    }
    fun save(sp: android.content.SharedPreferences, pkg: String) { val j = snapshot(pkg).toString(); val e = sp.edit().putString("bk_last", j); if (!sp.contains("bk_orig")) e.putString("bk_orig", j); e.apply() }
    private fun s(ns: String, k: String, v: String) = if (v.isBlank() || v == "null") "settings delete $ns $k" else "settings put $ns $k '$v'"
    fun restoreCmd(j: JSONObject): String {
        val wm = j.optString("wm", "none"); val pkg = j.optString("pkg", DEFAULT_PKG); val ov = j.optString("ov")
        return listOf(if (wm == "none") "wm size reset" else "wm size $wm", s("global", "window_animation_scale", j.optString("a1")), s("global", "transition_animation_scale", j.optString("a2")), s("global", "animator_duration_scale", j.optString("a3")),
            s("system", "min_refresh_rate", j.optString("h1")), s("system", "peak_refresh_rate", j.optString("h2")), s("global", "low_power", j.optString("lp")), s("secure", "long_press_timeout", j.optString("t1")),
            s("secure", "multi_press_timeout", j.optString("t2")), s("global", "angle_gl_driver_selection_pkgs", j.optString("ap")), s("global", "angle_gl_driver_selection_values", j.optString("av")),
            "cmd power set-fixed-performance-mode-enabled false", if (ov.isBlank() || ov == "null") "cmd game reset $pkg; device_config delete game_overlay $pkg" else "device_config put game_overlay $pkg '$ov'").joinToString("; ")
    }
}
fun gameResetSteps(a: AS) = listOf(
    Step("Removing the game profile", "Removed") { val r = Sh.run("cmd game reset ${a.pkg}; device_config delete game_overlay ${a.pkg}; settings delete global angle_gl_driver_selection_pkgs; settings delete global angle_gl_driver_selection_values"); if (r.startsWith("Error")) r else null },
    Step("Checking with Android", "No profile left") { if (Sh.run("device_config get game_overlay ${a.pkg}").contains("fps=")) "Android still holds a profile" else null })
fun restoreSteps(j: JSONObject?): List<Step> = if (j == null) listOf(Step("Reading the restore point") { "No restore point yet. Boost or apply a setting first." }) else listOf(
    Step("Putting your settings back", "Restored") { val r = Sh.run(Backup.restoreCmd(j)); if (r.contains("Exception") || r.startsWith("Error")) r.trim() else null },
    Step("Checking the screen size", "Matches the restore point") { val chk = Sh.run("wm size"); val want = j.optString("wm", "none"); if (want == "none") (if (chk.contains("Override")) "Screen still shows an override" else null) else (if (chk.contains(want)) null else "Screen size differs") })

// ---------- real tweaks, each verified ----------
fun modeSteps(a: AS, m: String): List<Step> {
    val lp = if (m == "Battery") 1 else 0; val fixed = m == "Gaming" || m == "Performance"
    val l = mutableListOf(
        Step("Battery saver " + (if (lp == 1) "on" else "off"), "Applied") { Sh.run("settings put global low_power $lp"); val v = Sh.run("settings get global low_power").trim(); if (v == lp.toString()) null else "Android kept battery saver at $v" },
        Step("Sustained performance (if your phone supports it)", if (fixed) "Requested" else "Released") { Sh.run("cmd power set-fixed-performance-mode-enabled $fixed"); null })
    if (m == "Gaming") l.add(Step("Do Not Disturb (priority only)", "On") { Sh.run("cmd notification set_dnd priority"); null })
    if (m == "Gaming" || m == "Performance") l.add(Step("Faster system animations", "0.5x") { Sh.run("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"); null })
    return l
}
fun touchSteps(on: Boolean) = listOf(Step(if (on) "Faster touch response" else "Restoring touch timing", if (on) "Press delay 250 ms" else "Default") {
    if (on) Sh.run("settings put secure long_press_timeout 250; settings put secure multi_press_timeout 250") else Sh.run("settings delete secure long_press_timeout; settings delete secure multi_press_timeout")
    val v = Sh.run("settings get secure long_press_timeout").trim(); if (on && v != "250") "Android kept $v" else null })
fun hzSteps(a: AS, lock: Boolean): List<Step> { val t = a.hzs.firstOrNull { it >= a.fps } ?: a.maxHz
    return listOf(Step(if (lock) "Locking refresh rate to $t Hz" else "Releasing the refresh lock", if (lock) "$t Hz" else "System default") {
        if (lock) Sh.run("settings put system min_refresh_rate $t; settings put system peak_refresh_rate $t") else Sh.run("settings delete system min_refresh_rate; settings delete system peak_refresh_rate")
        val v = Sh.run("settings get system peak_refresh_rate").trim(); if (lock && v.toFloatOrNull()?.toInt() != t) "Android kept $v" else null }) }
fun angleSteps(a: AS) = listOf(Step("Graphics driver: ${a.angle}", "Set for ${a.pkg.takeLast(10)}") {
    if (a.angle == "Default") Sh.run("settings delete global angle_gl_driver_selection_pkgs; settings delete global angle_gl_driver_selection_values")
    else Sh.run("settings put global angle_gl_driver_selection_pkgs ${a.pkg}; settings put global angle_gl_driver_selection_values ${if (a.angle == "ANGLE") "angle" else "native"}"); null })

fun AS.refreshEng() { thread { eng = Eng.read(eng) } }
fun AS.done(title: String): (Boolean) -> Unit = { ok -> if (ok) { Hist.add(sp, "$title applied"); sp.edit().putLong("lastopt", System.currentTimeMillis()).apply() }; refreshEng() }
fun AS.applyTuning(title: String, extra: List<Step>) {
    save(); runTask(tk, title, listOf(Step("Saving a restore point", "Saved") { Backup.save(sp, pkg); null }) + extra + applySteps(pkg, scale, fps, mode), done(title))
}
fun AS.boostPlus(launch: Boolean, title: String = if (launch) "Boost and launch" else "Boost", note: String? = null) {
    save(); val s = Sys.snap(c); snap = s; val tx = s.tempC?.let { " · " + it.toInt() + "°C" } ?: ""
    val st = mutableListOf(Step("Reading your hardware", note ?: ("RAM ${s.ramPct}% · battery " + (if (s.batt < 0) "n/a" else "${s.batt}%") + tx)) { null },
        Step("Checking Shizuku", "Connected") { if (Sh.running()) null else "Shizuku is not running. Start it in the Shizuku app." },
        Step("Checking permissions", "Authorized") { if (Sh.granted()) null else "Permission missing. Authorize NACHT X in Shizuku." },
        Step("Saving a restore point", "Saved") { Backup.save(sp, pkg); null },
        Step("Cleaning background apps", "Done") { Sh.run("am kill-all"); null })
    st.addAll(modeSteps(this, perfMode)); if (touchOpt) st.addAll(touchSteps(true)); if (lockHz) st.addAll(hzSteps(this, true)); if (angle != "Default" && eng.angle) st.addAll(angleSteps(this))
    st.addAll(applySteps(pkg, scale, fps, mode)); if (launch) st.add(Step("Launching the game", "Started") { Sh.run(launchCmd); null })
    runTask(tk, title, st, done(title))
}
fun AS.autoOptimize() {
    val s = Sys.snap(c); snap = s; val low = Regex("T6\\d\\d|SC98|SC77|G25|G35|G37|MT676|MT673").containsMatchIn(Env.soc().uppercase())
    var tier = if (s.ramMb < 3300 || low) 0 else if (s.ramMb < 6200) 1 else 2
    val why = mutableListOf("RAM " + "%.1f".format(java.util.Locale.US, s.ramMb / 1024.0) + " GB", "$maxHz Hz screen"); if (low) why.add(Env.soc())
    val t = s.tempC; if (t != null && t >= 41f) { tier = maxOf(0, tier - 1); why.add("hot " + t.toInt() + "°C") }
    val lowBatt = s.batt in 0..19 && !s.charging; if (lowBatt) { tier = 0; why.add("low battery") }
    scale = listOf(.6f, .8f, .9f)[tier]; fps = clampFps(listOf(45, 60, 90)[tier], maxHz); mode = if (lowBatt) "Battery" else "Performance"; prof = ""
    boostPlus(false, "Auto Boost", why.joinToString(" · ") + " → ${ds(scale)}x · $fps FPS")
}
fun AS.sysScale(ratio: Float) {
    val e = eng
    if (e.nw <= 0) { runTask(tk, "Screen resolution", listOf(Step("Reading the screen size") { "Could not read the screen size. Is Shizuku running?" })); return }
    val native = ratio >= .995f; val w = (e.nw * ratio / 2).toInt() * 2; val h = (e.nh * ratio / 2).toInt() * 2
    runTask(tk, if (native) "Restore native resolution" else "Screen resolution ${ds(ratio)}x", listOf(
        Step("Saving a restore point", "Saved") { Backup.save(sp, pkg); null },
        Step(if (native) "Restoring the native size" else "Setting ${w}x$h", if (native) "${e.nw}x${e.nh}" else "${w}x$h") { Sh.run(if (native) "wm size reset" else "wm size ${w}x$h"); val chk = Sh.run("wm size")
            if (native) (if (chk.contains("Override")) "Android still shows an override" else null) else (if (chk.contains("${w}x$h")) null else "Android did not accept ${w}x$h") }),
        { ok -> refreshEng(); if (ok && !native) { confirmRevert = true } else if (ok) { Hist.add(sp, "Native resolution restored") } })
}

// ---------- UI ----------
fun iconOf(c: Context, pkg: String): ImageBitmap? = try {
    val d = c.packageManager.getApplicationIcon(pkg); val b = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, 120, 120); d.draw(android.graphics.Canvas(b)); b.asImageBitmap()
} catch (e: Exception) { null }
fun labelOf(c: Context, pkg: String): String = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg.substringAfterLast('.') }

@Composable fun EngCard(m: Modifier, k: String, v: String, sub: String, col: Color) = Column(m.clip(RoundedCornerShape(16.dp)).background(Color(0xFF12121A)).border(1.dp, Ln, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(k, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(v, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(sub, color = Dim, fontSize = 11.sp, maxLines = 1) }
@Composable fun OutBtn(m: Modifier, t: String, col: Color, onClick: () -> Unit) { val sh = RoundedCornerShape(14.dp)
    Box(m.press(onClick).height(48.dp).clip(sh).background(Color(0xFF14141C)).border(1.dp, col.copy(alpha = 0.55f), sh), Alignment.Center) { Text(t, color = col, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) } }

@Composable fun EngineCards(a: AS) {
    val s = a.snap; val e = a.eng; val label = remember(a.pkg) { labelOf(a.c, a.pkg) }; val t = s.tempC; val used = s.ramMb * s.ramPct / 100
    Cap("System & engine monitor (9 cards)")
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
        EngCard(Modifier.weight(1f), "GAME", label.take(10), a.pkg.takeLast(12), Glow); EngCard(Modifier.weight(1f), "FPS", Session.fpsNow?.let { "$it FPS" } ?: "${a.fps} FPS", if (Session.fpsNow != null) "Live" else "Target: ${a.fps}", Color(0xFF22D3EE))
        EngCard(Modifier.weight(1f), "REFRESH", "${s.hz} Hz", "Max ${a.maxHz}Hz", Color(0xFFA78BFA)) }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
        val r = if (e.ratio < .995f) e.ratio else a.scale
        EngCard(Modifier.weight(1f), "DOWNSCALE", ds(r) + "x", if (e.ratio < .995f) "Whole screen" else if (a.scale < .995f) "Game profile" else "Native 100%", Color(0xFF34D399))
        EngCard(Modifier.weight(1f), "RENDERER", if (e.gles.isNotBlank()) "ES ${e.gles}" else "n/a", if (e.vk.isNotBlank()) "Vulkan ${e.vk}" else "No Vulkan", Color(0xFF38BDF8))
        EngCard(Modifier.weight(1f), "CPU", e.cpu?.let { "$it%" } ?: "n/a", "${Runtime.getRuntime().availableProcessors()} Cores", Color(0xFFF59E0B)) }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
        EngCard(Modifier.weight(1f), "GPU", e.gpu.ifBlank { "n/a" }.take(11), e.gpuLoad?.let { "$it% load" } ?: "load n/a", Color(0xFFEC4899))
        EngCard(Modifier.weight(1f), "RAM", "${used}MB", "${s.ramPct}% Used", Color(0xFF818CF8))
        EngCard(Modifier.weight(1f), "TEMP", t?.let { "%.0f°C".format(java.util.Locale.US, it) } ?: "n/a", if (t == null) "Not available" else if (t < 38) "Normal" else if (t < 42) "Warm" else "Hot", Color(0xFF34D399)) }
}
@Composable fun TargetGameCard(a: AS) {
    val icon = remember(a.pkg) { iconOf(a.c, a.pkg) }; val label = remember(a.pkg) { labelOf(a.c, a.pkg) }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Target game"); Spacer(Modifier.weight(1f)); MiniBtn("CHANGE") { a.picker = true } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) else Text("🎮", fontSize = 40.sp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1); Text(a.pkg, color = Dim, fontSize = 12.sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip(if (a.mode == "Battery") "SAVER" else "TURBO", Glow); Chip("${a.fps} FPS", Color(0xFF22D3EE)); Chip(ds(a.scale) + "x", Ok) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.weight(1.4f)) { Btn("BOOST GAME") { a.boostPlus(true) } }; Box(Modifier.weight(1f)) { Btn("LAUNCH", false) { a.run(a.launchCmd) } } }
    }
}
@Composable fun AutoBoostCard(a: AS) = Glass {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(WhiteGrad), Alignment.Center) { Text("✨", fontSize = 26.sp) }; Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text("AUTO BOOST ENGINE", fontWeight = FontWeight.Black); Text("Reads your hardware, heat and battery, then picks scale, FPS and memory cleanup for you.", color = Dim, fontSize = 12.sp) } }
    Btn("OPTIMIZE") { a.autoOptimize() }
}
@Composable fun RecoveryCard(a: AS) {
    fun load(k: String): JSONObject? = try { JSONObject(a.sp.getString(k, "") ?: "") } catch (e: Exception) { null }
    Glass { Cap("Backup & restore recovery"); Text("Every boost saves a restore point first. Undo anything with one tap.", color = Dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutBtn(Modifier.weight(1f), "RESTORE LAST", Txt) { runTask(a.tk, "Restore last settings", restoreSteps(load("bk_last")), a.done("Restore")) }
            OutBtn(Modifier.weight(1f), "RESTORE GAME", Color(0xFF22D3EE)) { runTask(a.tk, "Restore game", gameResetSteps(a), a.done("Game restore")) }
            OutBtn(Modifier.weight(1f), "RESET ALL", Bad) { runTask(a.tk, "Reset everything", restoreSteps(load("bk_orig")) + gameResetSteps(a), a.done("Reset")) } } }
}
class AppItem(val pkg: String, val label: String, val game: Boolean)
@Composable fun GamePicker(a: AS) {
    if (!a.picker) return
    var q by remember { mutableStateOf("") }; var items by remember { mutableStateOf<List<AppItem>?>(null) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { val pm = a.c.packageManager
        pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != a.c.packageName }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), (Build.VERSION.SDK_INT >= 26 && it.category == ApplicationInfo.CATEGORY_GAME) || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0) }
            .sortedWith(compareByDescending<AppItem> { it.game }.thenBy { it.label.lowercase() }) } }
    Dialog({ a.picker = false }, DialogProperties(usePlatformDefaultWidth = false)) { Glass(Modifier.padding(16.dp)) {
        Cap("Choose the target game"); OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        val list = items
        if (list == null) Text("Loading your apps...", color = Dim) else Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            list.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }.take(80).forEach { app ->
                val ic = remember(app.pkg) { iconOf(a.c, app.pkg) }
                Row(Modifier.fillMaxWidth().press { a.pkg = app.pkg; a.prof = ""; a.save(); a.picker = false }.clip(RoundedCornerShape(14.dp)).background(Color(0xFF14141C)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ic != null) Image(ic, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1); Text(app.pkg, color = Dim, fontSize = 11.sp, maxLines = 1) }; if (app.game) Chip("GAME", Ok) } } }
        Btn("CLOSE", false) { a.picker = false } } }
}
@Composable fun RevertDialog(a: AS) {
    if (!a.confirmRevert) return
    var left by remember { mutableStateOf(15) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- }; if (a.confirmRevert) { a.confirmRevert = false; withContext(Dispatchers.IO) { Sh.run("wm size reset") }; a.refreshEng() } }
    Dialog({ }, DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) { Glass(Modifier.padding(20.dp)) {
        Cap("Keep this resolution?"); Text("$left", fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text("If you do nothing, the screen goes back to normal in $left seconds.", color = Dim, fontSize = 13.sp)
        Btn("KEEP IT") { a.confirmRevert = false }; Btn("GO BACK NOW", false) { a.confirmRevert = false; thread { Sh.run("wm size reset"); a.refreshEng() } } } }
}
fun presetOf(r: Float) = when (Math.round(r * 10)) { 4 -> "POTATO"; 5 -> "ULTRA"; 6 -> "LOW"; 8 -> "SMOOTH"; 9 -> "BALANCE"; 10 -> "NATIVE"; else -> "" }

@Composable fun TunerPage(a: AS) {
    val e = a.eng; var apply by remember { mutableStateOf("Game only") }; var ratio by remember { mutableStateOf(if (a.scale < .995f) a.scale else 1f) }
    val label = remember(a.pkg) { labelOf(a.c, a.pkg) }; val rs = "%.1f".format(java.util.Locale.US, ratio) + "x"
    val presets = mapOf("POTATO" to .4f, "ULTRA" to .5f, "LOW" to .6f, "SMOOTH" to .8f, "BALANCE" to .9f, "NATIVE" to 1f)
    H("Graphics & tuning", "Applied through Android and checked afterwards.")
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Graphics downscaler"); Spacer(Modifier.weight(1f)); Chip(if (e.ratio < .995f) ds(e.ratio) + "x ACTIVE" else "1.0x NATIVE", if (e.ratio < .995f) Warn else Ok) }
        Text("Renders at a lower internal resolution to ease GPU load and hold FPS.", color = Dim, fontSize = 12.sp)
        Text(if (e.nw > 0) "Display native: ${e.nw}x${e.nh}   Rendering: ${e.cw}x${e.ch}" else "Screen size not read yet (is Shizuku running?)", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFCCCCCC))
        Cap("Apply to"); Pick(listOf("Game only", "Whole screen"), apply) { apply = it }
        Text(if (apply == "Game only") "Only $label is rendered smaller (Android Game Mode). The safe choice." else "Changes the whole screen with wm size. A 15-second timer undoes it unless you keep it.", color = Dim, fontSize = 11.sp)
        Cap("Downscale ratio"); Pick(listOf("0.4x", "0.5x", "0.6x", "0.7x", "0.8x", "0.9x", "1.0x"), rs) { ratio = it.dropLast(1).toFloat() }
        Cap("Presets"); Pick(listOf("POTATO", "ULTRA", "LOW"), presetOf(ratio)) { ratio = presets[it] ?: 1f }; Pick(listOf("SMOOTH", "BALANCE", "NATIVE"), presetOf(ratio)) { ratio = presets[it] ?: 1f }
        Btn("APPLY DOWNSCALE") { if (apply == "Game only") { a.scale = ratio; a.prof = ""; a.applyTuning("Downscale ${ds(ratio)}x", emptyList()) } else { a.sysScale(ratio) } }
        Btn("RESTORE NATIVE", false) { if (apply == "Game only") { a.scale = 1f; a.prof = ""; ratio = 1f; a.applyTuning("Native resolution", emptyList()) } else { ratio = 1f; a.sysScale(1f) } }
    }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Graphics renderer"); Spacer(Modifier.weight(1f)); Chip(if (e.gles.isNotBlank()) "OpenGL ES ${e.gles}" else "NOT READ", if (e.gles.isNotBlank()) Ok else Dim) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip(if (e.vk.isNotBlank()) "VULKAN ${e.vk}" else "NO VULKAN", if (e.vk.isNotBlank()) Ok else Dim); Chip("GPU " + e.gpu.ifBlank { "n/a" }.take(14), Glow) }
        Text("The game picks Vulkan or OpenGL itself. This shows what your phone supports instead of a switch that does nothing.", color = Dim, fontSize = 11.sp)
        Cap("ANGLE driver for the target game"); Pick(listOf("Default", "ANGLE", "Native"), a.angle) { a.angle = it; a.sp.edit().putString("angle", it).apply() }
        Text(if (e.angle) "ANGLE is installed on this phone." else "ANGLE isn't installed here, so this setting would have no effect.", color = if (e.angle) Ok else Warn, fontSize = 12.sp)
        Btn("APPLY DRIVER", true, e.angle) { a.applyTuning("Graphics driver", angleSteps(a)) }
    }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Target FPS & frame pacing"); Spacer(Modifier.weight(1f)); Text("Max: ${a.maxHz}Hz", color = Glow, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) { listOf(30, 45, 60, 90, 120, 144, 165).forEach { v -> val on = a.fps == v; val ok = v <= a.maxHz; val sh = RoundedCornerShape(12.dp)
            Box(Modifier.weight(1f).then(if (ok) Modifier.press { a.fps = v; a.prof = "" } else Modifier).height(44.dp).clip(sh).background(if (on) Acc else Color(0xFF14141C)).border(1.dp, if (on) Glow else Ln, sh), Alignment.Center) {
                Text("$v", color = if (!ok) Dim.copy(alpha = 0.4f) else if (on) Color.White else Txt, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) } } }
        Toggle("Lock refresh rate to target", "Matches the screen to your target so frames arrive evenly.", a.lockHz) { a.lockHz = it; a.sp.edit().putBoolean("lockHz", it).apply() }
        Text("The game FPS cap always applies. Android Game Mode accepts up to 120.", color = Dim, fontSize = 11.sp)
        Btn("APPLY FPS SETTINGS") { a.applyTuning("FPS and frame pacing", hzSteps(a, a.lockHz)) }
    }
    Glass {
        Cap("Performance mode & touch"); val cur = a.perfMode.uppercase(); val set = { v: String -> a.perfMode = v.lowercase().replaceFirstChar { it.uppercase() }; a.sp.edit().putString("perfMode", a.perfMode).apply() }
        Pick(listOf("GAMING", "PERFORMANCE"), cur) { set(it) }; Pick(listOf("BALANCED", "BATTERY"), cur) { set(it) }
        Text(when (a.perfMode) { "Gaming" -> "Battery saver off, sustained performance, Do Not Disturb and faster animations."; "Performance" -> "Battery saver off, sustained performance and faster animations."; "Balanced" -> "Battery saver off with normal power behavior."; else -> "Battery saver on and normal performance for longer battery life." }, color = Dim, fontSize = 12.sp)
        Toggle("Touch optimization", "Shortens Android's long-press and multi-press delays for snappier taps.", a.touchOpt) { a.touchOpt = it; a.sp.edit().putBoolean("touchOpt", it).apply() }
        Btn("APPLY MODE") { a.applyTuning(a.perfMode + " mode", modeSteps(a, a.perfMode) + touchSteps(a.touchOpt)) }
    }
    RecoveryCard(a)
}
