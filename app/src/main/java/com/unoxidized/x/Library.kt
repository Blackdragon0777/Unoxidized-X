package com.unoxidized.x
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

// ---------- library data (favorites, game library, recents) ----------
object LibStore {
    private fun arr(sp: SharedPreferences, k: String): List<String> = try { val a = JSONArray(sp.getString(k, "[]")); (0 until a.length()).map { a.getString(it) } } catch (e: Exception) { emptyList() }
    private fun put(sp: SharedPreferences, k: String, l: List<String>) { sp.edit().putString(k, JSONArray(l).toString()).apply() }
    fun favs(sp: SharedPreferences) = arr(sp, "fav"); fun recents(sp: SharedPreferences) = arr(sp, "recent"); fun games(sp: SharedPreferences) = arr(sp, "games")
    fun toggleFav(sp: SharedPreferences, pkg: String) { val l = favs(sp).toMutableList(); if (!l.remove(pkg)) l.add(pkg); put(sp, "fav", l.take(24)) }
    fun toggleGame(sp: SharedPreferences, pkg: String) { val l = games(sp).toMutableList(); if (!l.remove(pkg)) l.add(pkg); put(sp, "games", l.take(60)) }
    fun addRecent(sp: SharedPreferences, pkg: String) { put(sp, "recent", (listOf(pkg) + recents(sp).filter { it != pkg }).take(12)); sp.edit().putLong("last_$pkg", System.currentTimeMillis()).apply() }
    fun name(sp: SharedPreferences, pkg: String, def: String) = sp.getString("nm_$pkg", null) ?: def
    fun setName(sp: SharedPreferences, pkg: String, n: String) { if (n.isBlank()) sp.edit().remove("nm_$pkg").apply() else sp.edit().putString("nm_$pkg", n.trim().take(24)).apply() }
}
fun launchApp(c: Context, sp: SharedPreferences, pkg: String): String? {
    val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return "This app can't be opened. It may have been removed."
    return try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); LibStore.addRecent(sp, pkg); null } catch (e: Exception) { "Android refused to open it: ${e.message}" }
}
fun loadApps(c: Context): List<AppItem> { val pm = c.packageManager
    return pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != c.packageName }
        .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), (Build.VERSION.SDK_INT >= 26 && it.category == ApplicationInfo.CATEGORY_GAME) || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0) }
        .sortedWith(compareByDescending<AppItem> { it.game }.thenBy { it.label.lowercase() }) }
object FreeKey {
    /** Free access renews every 3 days. It is checked on the phone, so it is a convenience gate, not strong security. */
    fun get(c: Context, dev: String): String { val sp = c.getSharedPreferences("ux", 0); val t = Lic.today(); val i = sp.getLong("fi_$dev", -1L)
        val d = if (i >= 0 && i + Lic.DAYS > t) i else t; if (d == t) sp.edit().putLong("fi_$dev", t).apply(); return Lic.make(dev, d) }
}
fun openSettingsIntent(c: Context, i: Intent) { try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }
fun openOverlaySettings(c: Context) = openSettingsIntent(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))

// ---------- tier and engine gates ----------
@Composable fun PremiumGate(a: AS, name: String, content: @Composable () -> Unit) {
    val fk = when (name) { "Graphics tuner" -> "tuner"; "Game profiles" -> "profiles"; "Session tracking" -> "sessions"; "Import and export" -> "importExport"; "Auto Boost" -> "autoBoost"; else -> "" }
    val needsPremium = (CtlState.data?.tiers?.get(fk) ?: "premium") == "premium"
    if (a.keyRole != "F" || !needsPremium) content() else Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Chip("PREMIUM", Glow); Spacer(Modifier.weight(1f)); Text("🔒", fontSize = 20.sp) }
        Text("$name is part of NACHT X Premium.", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Free access includes the library, floating panel, tools and the Information Center. Ask the owner on Telegram for a Premium key, then enter it in Account.", color = Dim, fontSize = 12.sp)
        Btn("UPGRADE TO PREMIUM") { a.nav(12) }; Btn("I ALREADY HAVE A KEY", false) { a.nav(11) }
    }
}
@Composable fun NeedsEngine(a: AS, content: @Composable () -> Unit) {
    if (Sh.granted()) content() else Glass {
        Chip("ENGINE OFF", Warn); Text("This feature needs root or Shizuku.", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("You're in Basic Mode. Activate the engine to unlock features that change system settings. Nothing else needs it.", color = Dim, fontSize = 12.sp)
        Btn("ACTIVATE ENGINE") { Access.mode = ""; a.c.getSharedPreferences("ux", 0).edit().remove("mode").apply() }
    }
}

// ---------- library page ----------
@Composable fun AppIcon(c: Context, pkg: String, sz: Int) { val ic = remember(pkg) { iconOf(c, pkg) }
    if (ic != null) Image(ic, null, Modifier.size(sz.dp).clip(RoundedCornerShape((sz / 4).dp))) else Box(Modifier.size(sz.dp).clip(RoundedCornerShape((sz / 4).dp)).background(Color(0xFF1E1730)), Alignment.Center) { Text("🎮", fontSize = (sz / 2).sp) } }

@Composable fun LibRow(a: AS, app: AppItem, fav: Boolean, inLib: Boolean, changed: () -> Unit, rename: () -> Unit) {
    val toast = LocalToast.current; val last = a.sp.getLong("last_${app.pkg}", 0L)
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { AppIcon(a.c, app.pkg, 48); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(LibStore.name(a.sp, app.pkg, app.label), fontWeight = FontWeight.Black, maxLines = 1); Text(app.pkg, color = Dim, fontSize = 11.sp, maxLines = 1)
                Text(if (last > 0) "Last opened ${fmt(last)}" else "Not opened from here yet", color = Dim, fontSize = 11.sp) }; if (app.game) Chip("GAME", Ok) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("LAUNCH") { val e = launchApp(a.c, a.sp, app.pkg); if (e != null) toast(e) else changed() }
            MiniBtn(if (fav) "★ FAVORITE" else "☆ FAVORITE") { LibStore.toggleFav(a.sp, app.pkg); changed() }; MiniBtn(if (inLib) "− LIBRARY" else "+ LIBRARY") { LibStore.toggleGame(a.sp, app.pkg); changed() } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("SET AS TARGET") { a.pkg = app.pkg; a.prof = ""; a.save(); toast("Target game set") }; MiniBtn("RENAME") { rename() } }
    }
}
@Composable fun LibraryPage(a: AS) {
    var tab by remember { mutableStateOf("Games") }; var q by remember { mutableStateOf("") }; var apps by remember { mutableStateOf<List<AppItem>?>(null) }; var ver by remember { mutableStateOf(0) }; var rename by remember { mutableStateOf<AppItem?>(null) }
    LaunchedEffect(a.tick) { apps = withContext(Dispatchers.IO) { loadApps(a.c) } }
    val favs = remember(ver) { LibStore.favs(a.sp) }; val games = remember(ver) { LibStore.games(a.sp) }; val recents = remember(ver) { LibStore.recents(a.sp) }
    H("Library", "Every installed app, launched in one tap.")
    Pick(listOf("Games", "Apps", "Favorites", "Recent"), tab) { tab = it }
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search installed apps") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
    val all = apps
    if (all == null) { Skel(Modifier.fillMaxWidth().height(70.dp)); Skel(Modifier.fillMaxWidth().height(70.dp)) } else {
        val by = all.associateBy { it.pkg }
        val list = when (tab) { "Games" -> games.mapNotNull { by[it] } + all.filter { it.game && it.pkg !in games }.take(30); "Favorites" -> favs.mapNotNull { by[it] }; "Recent" -> recents.mapNotNull { by[it] }; else -> all }
        val shown = list.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }.take(120)
        if (shown.isEmpty()) Glass { Text("Nothing here yet", fontWeight = FontWeight.Bold)
            Text(when (tab) { "Games" -> "Detected games show up here. Add any app with + LIBRARY."; "Favorites" -> "Tap ☆ FAVORITE on an app to pin it to Quick launch and the floating panel."; "Recent" -> "Apps you open from NACHT X appear here."; else -> "No launchable apps found." }, color = Dim, fontSize = 12.sp) }
        shown.forEach { app -> LibRow(a, app, app.pkg in favs, app.pkg in games, { ver++ }, { rename = app }) }
        Text("${all.size} launchable apps found. Only apps with a launcher icon can be opened.", color = Dim, fontSize = 11.sp)
    }
    rename?.let { app -> var t by remember(app.pkg) { mutableStateOf(LibStore.name(a.sp, app.pkg, app.label)) }
        Dialog({ rename = null }) { Glass(Modifier.padding(20.dp)) { Cap("Rename in your library")
            OutlinedTextField(t, { t = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            Btn("SAVE NAME", true, t.isNotBlank()) { LibStore.setName(a.sp, app.pkg, t); rename = null; ver++ }; Btn("USE THE ORIGINAL NAME", false) { LibStore.setName(a.sp, app.pkg, ""); rename = null; ver++ }; Btn("CANCEL", false) { rename = null } } } }
}
@Composable fun QuickLaunch(a: AS) {
    var ver by remember { mutableStateOf(0) }; val toast = LocalToast.current
    val favs = remember(ver, a.tick) { LibStore.favs(a.sp) }; val rec = remember(ver, a.tick) { LibStore.recents(a.sp) }; val list = (favs + rec.filter { it !in favs }).take(10)
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Quick launch"); Spacer(Modifier.weight(1f)); MiniBtn("OPEN LIBRARY") { a.nav(1) } }
        if (list.isEmpty()) Text("Add favorites in the Library and they appear here and in the floating panel.", color = Dim, fontSize = 12.sp)
        else Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(14.dp)) { list.forEach { pkg -> Column(Modifier.press { val e = launchApp(a.c, a.sp, pkg); if (e != null) toast(e) else ver++ }, horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(a.c, pkg, 52); Text(LibStore.name(a.sp, pkg, labelOf(a.c, pkg)).take(9), color = Dim, fontSize = 10.sp, maxLines = 1) } } } }
}
@Composable fun FloatingCard(a: AS) {
    val c = a.c; var on by remember { mutableStateOf(FloatingService.running) }; var perm by remember { mutableStateOf(Settings.canDrawOverlays(c)) }
    LaunchedEffect(Unit) { while (true) { on = FloatingService.running; perm = Settings.canDrawOverlays(c); delay(1000) } }
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Floating panel"); Spacer(Modifier.weight(1f)); Chip(if (on) "ON" else "OFF", if (on) Ok else Dim) }
        Text("A draggable bubble that floats over your game. Tap it for favorites, recents and quick tools.", color = Dim, fontSize = 12.sp)
        if (!perm) { Text("Needs the 'Display over other apps' permission.", color = Warn, fontSize = 12.sp); Btn("GRANT OVERLAY PERMISSION") { openOverlaySettings(c) } }
        else Btn(if (on) "HIDE FLOATING PANEL" else "SHOW FLOATING PANEL", !on) { if (on) FloatingService.stop(c) else FloatingService.start(c) } }
}

// ---------- home helpers ----------
@Composable fun WelcomeCard(a: AS) = Glass {
    Row(verticalAlignment = Alignment.CenterVertically) { BrandLogo(56); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(Brand.title, Modifier.shimmer(), fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Glow); Text(if (Brand.welcome.isNotBlank()) Brand.welcome else "Shadow System · version 7.0", color = Dim, fontSize = 12.sp) }
        Chip(if (a.keyRole != "F") Lic.roleName(a.keyRole).uppercase() else "FREE", if (a.keyRole != "F") Ok else Glow) }
}
@Composable fun CompatCard(a: AS) {
    val c = a.c; val sdk = Build.VERSION.SDK_INT; val notif = sdk < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val rows = listOf(Triple("Android", "Android ${Build.VERSION.RELEASE}", true), Triple("Game Mode profiles", if (sdk >= 31) "SUPPORTED" else "NEEDS ANDROID 12+", sdk >= 31),
        Triple("Engine", (when (Access.mode) { "root" -> "ROOT"; "shizuku" -> "SHIZUKU"; else -> "BASIC" }) + if (Sh.granted()) " · READY" else "", Access.mode == "basic" || Sh.granted()),
        Triple("Floating panel permission", if (Settings.canDrawOverlays(c)) "GRANTED" else "NEEDED", Settings.canDrawOverlays(c)), Triple("Notifications", if (notif) "GRANTED" else "NEEDED", notif))
    Glass { Cap("Compatibility & permissions"); rows.forEach { (k, v, ok) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k, Modifier.weight(1f), fontSize = 13.sp); Chip(v, if (ok) Ok else Warn) } } }
}
@Composable fun FreeAccessCard(a: AS, key: String) {
    var k by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }; val dev = remember { Lic.deviceId(a.c) }; val cb = LocalClipboardManager.current
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Access tier"); Spacer(Modifier.weight(1f)); Chip(if (a.keyRole != "F") Lic.roleName(a.keyRole).uppercase() else "FREE", if (a.keyRole != "F") Ok else Glow) }
        Text(if (a.keyRole != "F") "Premium is active. Everything is unlocked." else "Free access renews every ${Lic.DAYS} days. Premium unlocks the tuner, profiles, sessions and Auto Boost.", color = Dim, fontSize = 12.sp)
        Cap("Enter a Premium key"); OutlinedTextField(k, { k = it; err = "" }, Modifier.fillMaxWidth(), placeholder = { Text("UX2-$dev-...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 4)
        MiniBtn("PASTE") { k = cb.getText()?.text ?: ""; err = "" }
        if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
        Btn("ACTIVATE KEY") { val r = Lic.check(dev, k); if (r.ok) { a.sp.edit().putString("k", Lic.clean(k)).apply(); (a.c as? Activity)?.recreate() } else err = r.msg }
        Text("Your device ID: $dev", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
}

// ---------- settings blocks ----------
@Composable fun FloatingSettings(a: AS) {
    val sp = a.sp; var alpha by remember { mutableStateOf(sp.getFloat("fl_alpha", 1f)) }; var snap by remember { mutableStateOf(sp.getBoolean("fl_snap", true)) }; var size by remember { mutableStateOf(sp.getInt("fl_size", 1)) }
    val names = listOf("Small", "Medium", "Large")
    Glass { Cap("Floating panel settings")
        Text("Transparency ${(alpha * 100).toInt()}%", fontWeight = FontWeight.Bold)
        Slider(alpha, { alpha = it; sp.edit().putFloat("fl_alpha", it).apply(); FloatingService.refresh(a.c) }, valueRange = 0.4f..1f, colors = SliderDefaults.colors(thumbColor = Glow, activeTrackColor = Glow, inactiveTrackColor = Color(0xFF2A2140)))
        Cap("Panel size"); Pick(names, names[size.coerceIn(0, 2)]) { size = names.indexOf(it); sp.edit().putInt("fl_size", size).apply(); FloatingService.refresh(a.c) }
        Toggle("Snap to screen edge", "The bubble slides to the nearest edge when you let go.", snap) { snap = it; sp.edit().putBoolean("fl_snap", it).apply() }
        Btn("RESET BUBBLE POSITION", false) { sp.edit().remove("fl_x").remove("fl_y").apply(); FloatingService.refresh(a.c) } }
}
@Composable fun AccessSettings(a: AS) {
    var ask by remember { mutableStateOf(false) }; val c = a.c
    Glass { Cap("Access & permissions"); Text("Engine: " + (when (Access.mode) { "root" -> "Root"; "shizuku" -> "Shizuku"; else -> "Basic Mode" }), fontWeight = FontWeight.Bold)
        Btn("CHANGE ACCESS LEVEL", false) { Access.mode = ""; a.sp.edit().remove("mode").apply() }
        Btn("OVERLAY PERMISSION", false) { openOverlaySettings(c) }
        Btn("NOTIFICATION SETTINGS", false) { openSettingsIntent(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)) }
        Btn("BATTERY OPTIMIZATION", false) { openSettingsIntent(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        Btn("RESET MY PREFERENCES", false) { ask = true } }
    if (ask) Confirm("Reset preferences?", "Clears favorites, library, floating panel and tuning choices. Your key and restore points stay.", {
        a.sp.edit().apply { listOf("fav", "recent", "games", "fl_alpha", "fl_snap", "fl_size", "fl_x", "fl_y", "angle", "perfMode", "touchOpt", "lockHz", "rm", "hp", "track").forEach { remove(it) } }.apply(); ask = false; (c as? Activity)?.recreate() }, { ask = false })
}

// ---------- Information Center ----------
@Composable fun Expand2(ic: String, title: String, content: @Composable ColumnScope.() -> Unit) { var open by remember { mutableStateOf(false) }
    Glass(Modifier.press { open = !open }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(ic, fontSize = 22.sp); Spacer(Modifier.width(10.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(if (open) "−" else "+", color = Dim, fontSize = 22.sp) }; if (open) content() } }
val IC_GUIDE = listOf("🚀" to ("Get started" to "Tap GET FREE ACCESS on the key screen. Choose Basic Mode to start right away, or ADB via Shizuku for the tuning tools. You can change this any time in Settings."),
    "📚" to ("Library and quick launch" to "Open the Library tab, tap ☆ FAVORITE on the apps you use, and they appear in Quick launch on Home and in the floating panel."),
    "🫧" to ("Floating panel" to "On Home tap SHOW FLOATING PANEL. The first time, Android asks you to allow 'Display over other apps'. Drag the bubble anywhere, tap it to open favorites, recents and quick tools."),
    "🛡️" to ("Set up Shizuku (optional)" to "Install Shizuku from the Play Store. Turn on Wireless debugging in Developer options, pair inside Shizuku, then Start. Back in NACHT X choose ADB via Shizuku and tap Authorize."),
    "💎" to ("Premium" to "Premium adds the graphics tuner, game profiles, session tracking, Auto Boost and import/export. Ask the owner on Telegram for a key, then enter it under Account."),
    "🛟" to ("Boost safely" to "Every boost saves a restore point first. Use RESTORE LAST, RESTORE GAME or RESET ALL on Home if you want your old settings back."))
val IC_FAQ = listOf("Which Android versions work?" to "NACHT X installs on Android 7 and newer, up to the latest Android. The library, floating panel, tools and information work on every supported version. Game Mode profiles use features added in Android 12.",
    "What is free and what is Premium?" to "Free: library, quick launch, floating panel, device tools, monitor and this Information Center. Premium: graphics tuner, game profiles, session tracking, Auto Boost, and import/export.",
    "Is it a cheat?" to "No. It never touches a game's files, memory or network. It only opens apps and changes Android's own settings.",
    "Do I need Shizuku?" to "Only for features that change system settings. Basic Mode needs nothing special.",
    "The floating panel disappeared." to "Some phones stop background services to save battery. Allow NACHT X in your battery settings (Settings > Battery optimization) and show the panel again.",
    "Why does FPS say Not available?" to "Some phones hide frame data from apps. NACHT X says so instead of inventing numbers.",
    "My Free access ended." to "Free access renews every 3 days. Open the app and tap GET FREE ACCESS again on the key screen.",
    "Is my data safe?" to "Everything stays on your phone. The app only uses the internet to read announcements and owner branding from a public file.")
@Composable fun InfoCenterPage(a: AS) {
    var tab by remember { mutableStateOf("About") }; val c = a.c; val cb = LocalClipboardManager.current; val toast = LocalToast.current
    H("Information Center", "Everything about NACHT X in one place.")
    Pick(listOf("About", "Guide", "FAQ"), tab) { tab = it }; Pick(listOf("Privacy", "Help"), tab) { tab = it }
    when (tab) {
        "About" -> { Glass { Row(verticalAlignment = Alignment.CenterVertically) { BrandLogo(72); Spacer(Modifier.width(14.dp)); Column { Text(Brand.title, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("SHADOW SYSTEM · V7.0", color = Dim, fontSize = 11.sp, letterSpacing = 2.sp) } }
                Text("A game launcher, floating panel and performance toolkit for Android. Built to be honest: if your phone can't do something, it tells you.", color = Color(0xFFCCCCCC), fontSize = 13.sp) }
            CompatCard(a); AnnounceCard(); UpdateCard("7.0")
            Glass { Cap("What's new in 7.0"); listOf("One app: the Generator and Information apps are now inside NACHT X", "Game library with favorites and recents", "Floating panel over your games", "Engine Activation: root, Shizuku or Basic Mode", "Free and Premium tiers", "Owner branding and announcements").forEach { Text("•  $it", fontSize = 13.sp, color = Color(0xFFDDDDDD)) } } }
        "Guide" -> IC_GUIDE.forEach { (ic, p) -> Expand2(ic, p.first) { Text(p.second, color = Color(0xFFDDDDDD), fontSize = 13.sp) } }
        "FAQ" -> IC_FAQ.forEach { (q, ans) -> Expand2("❓", q) { Text(ans, color = Color(0xFFDDDDDD), fontSize = 13.sp) } }
        "Privacy" -> Glass { Cap("Permissions explained")
            listOf("Display over other apps" to "Shows the floating panel.", "Notifications" to "Required by Android for the floating panel and session tracking notices.", "Internet" to "Reads announcements, update info and owner branding. Nothing about you is sent.", "App list access" to "Lets the library show apps you can open. It never leaves your phone.").forEach { (k, v) -> Text(k, fontWeight = FontWeight.Bold); Text(v, color = Dim, fontSize = 12.sp) }
            Text("Free access is checked on your phone. It is a convenience gate, not strong security.", color = Warn, fontSize = 12.sp) }
        else -> Glass { Cap("Help & contact")
            Text("Shizuku keeps stopping? Start it again after every reboot. Panel not showing? Allow the overlay permission and battery access in Settings.", color = Dim, fontSize = 12.sp)
            Btn("COPY DIAGNOSTICS") { cb.setText(AnnotatedString("NACHT X 7.0 diagnostics\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice ${Env.device()}\nAccess: ${Access.mode.ifEmpty { "not set" }}, engine ready: ${Sh.granted()}\nTier: ${if (a.keyRole != "F") "Premium" else "Free"}\nOverlay permission: ${Settings.canDrawOverlays(c)}\nFloating panel on: ${FloatingService.running}")); toast("Diagnostics copied") }
            Btn("REPORT A PROBLEM ON TELEGRAM", false) { openUrl(c, "https://t.me/UnoxidizedX") }; Btn("CONTACT @UnoxidizedX", false) { openUrl(c, "https://t.me/UnoxidizedX") } }
    }
}
