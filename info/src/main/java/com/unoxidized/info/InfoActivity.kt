package com.unoxidized.info
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val VER = "6.0"
class F(val ic: String, val t: String, val d: String)
val XF = listOf(F("🛡️", "Authorized access", "Unlocks only after Shizuku is installed, started and you allow the app. If Shizuku stops, it locks again."), F("📈", "Performance score", "A live ring built from real RAM, heat, battery and storage readings."),
    F("🎮", "CODM profiles", "Battery Saver, Balanced, Performance and Max Performance, applied through Android Game Mode."), F("💾", "Custom profiles", "Create, edit, duplicate and delete your own render-scale and FPS profiles."),
    F("⚡", "Boost and launch", "Cleans background apps, applies your profile, verifies it and opens CODM. Every step is a real check."), F("🛰️", "Launch detection", "Notices when CODM starts and begins a session automatically."),
    F("⏱️", "Session tracking", "Records duration, heat, battery use and real FPS and frame-time when your phone allows."), F("📊", "Before and after", "Compares your real sessions with and without a profile."),
    F("📡", "Live monitor", "CPU, RAM, battery temperature and thermal status."), F("📥", "Import and export", "Share setups as short UXP1 codes, or import a friend's."),
    F("🧰", "Tools", "Animation speed, Do Not Disturb, diagnostics and one-tap restore defaults."), F("🔑", "Role-based keys", "Free weekly keys, plus longer keys issued by the owner."),
    F("📣", "Remote news", "Announcements and update alerts from the owner."), F("✋", "Gestures", "Swipe between tabs, pull to refresh, swipe to delete."))
val GF = listOf(F("🗝️", "Weekly key", "One free key per device ID every 7 days."), F("📲", "Auto device ID", "Fills in this phone's ID. Paste another to make a key for a different device."),
    F("🚀", "Send to NACHT X", "One tap logs you in on the main app."), F("🗂️", "My Keys", "Every ID with a live countdown and progress bar."),
    F("📤", "Copy and share", "Copy a key or share it with the system share sheet."), F("✋", "Gestures", "Swipe between tabs and pull to refresh."))
class Sec(val ic: String, val t: String, val steps: List<String>)
val GUIDE = listOf(
    Sec("📦", "Install the apps", listOf("Open the Download tab and tap OPEN LINK. Download UnoxidizedX.zip from MediaFire.", "Unzip it. You get NACHT X, NACHT X Generator and NACHT X Information.", "Tap each .apk to install. Allow installs from this source if Android asks.", "If Play Protect warns you, tap Install anyway.")),
    Sec("🔑", "Get your key", listOf("Open NACHT X Generator. Your device ID is already filled in.", "Tap GENERATE KEY.", "Tap SEND TO NACHT X. It logs you in by itself. (Or COPY KEY and paste it in the login screen.)", "A key lasts 7 days. After that, open the Generator and make a new one.")),
    Sec("🛡️", "Set up Shizuku", listOf("Install Shizuku from the Play Store.", "Settings > About phone: tap Build number 7 times.", "Settings > Developer options: turn on Wireless debugging.", "Open Shizuku, tap Pairing, and enter the pairing code.", "Tap Start via Wireless debugging.", "Back in NACHT X tap AUTHORIZE ACCESS and press Allow.")),
    Sec("🎮", "Use NACHT X", listOf("Home > BOOST & LAUNCH CODM. Watch the popup: every line is a real check.", "Game tab: pick a profile, or fine-tune scale, FPS and mode.", "Make your own profiles with + CREATE. Swipe a profile to delete it.", "Monitor tab: live CPU, RAM and heat.", "Sessions: recorded automatically when CODM starts.")),
    Sec("📊", "Compare your results", listOf("Play one session with a profile on.", "Tools > RESTORE DEFAULTS, then play another session.", "Sessions > Compare shows your real numbers side by side.")),
    Sec("⬆️", "Update the apps", listOf("Download the new zip from the same link.", "Install the new APKs over the old ones.", "Your keys, profiles and history stay.")),
    Sec("🧯", "Troubleshooting", listOf("Shizuku stops after a reboot: open Shizuku and Start it again.", "Key says invalid: copy the whole key, or check the device ID matches.", "Profile not kept: some phones or CODM versions reject Game Mode profiles. The popup shows the reason.", "FPS says Not available: Android is hiding frame data on your phone.", "Locked out with Revoked or Maintenance: contact @UnoxidizedX.")))
val FAQ = listOf(
    "What is NACHT X?" to "A shadow-themed performance center for Call of Duty: Mobile. It tunes resolution scale and FPS cap through Android Game Mode, cleans background apps, watches your phone's heat and RAM, and records your real sessions.",
    "Which Android versions work?" to "The apps install on Android 6 and every newer version, up to the latest Android 17. Game Mode profiles (resolution scale and FPS cap) use features Android added in version 12, so on 12 and newer you get the full experience. Every version gets the boost clean-up, live monitor, tools, sessions and the key system.",
    "How do I see my real FPS?" to "Turn on session tracking in Settings, then open CODM. NACHT X reads frame data from Android through Shizuku. If you don't see a number, tap TEST FPS NOW on the Monitor tab and it tells you exactly what your phone allows.",
    "What is Shadow Rank?" to "Your rank grows as you play. Every boost gives 1 point and every recorded CODM session gives 3. Climb from Initiate to Shade, Phantom and finally Nachtlord.",
    "Cheat or tool?" to "Pure tool. It never touches CODM's files, memory or network. It only changes Android's own settings for the game, so your account and the game stay untouched.",
    "Why does it need Shizuku?" to "Android only lets the system shell change Game Mode profiles. Shizuku lends the app that access after you approve it, and you can revoke it anytime.",
    "Will it guarantee more FPS?" to "No honest tool can. Results depend on your phone, heat and the game. Use Sessions > Compare to see your own real numbers with and without a profile.",
    "What happens when my key expires?" to "NACHT X returns to the key screen. Open NACHT X Generator, make a fresh key and tap Send. Your profiles and history stay exactly as they were.",
    "Can one key work on two phones?" to "Each key is locked to one device ID. Make a separate key for every phone, the Generator can do it for any ID you paste.",
    "How do I share my setup?" to "Export your profile as a short UXP1 code on the Import page and send it to a friend. They paste it and it applies in one tap.",
    "Does session tracking drain my battery?" to "It's a tiny background service that checks every few seconds only while enabled. Turn it off in Settings any time.")

class InfoActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); val sp = getSharedPreferences("info", 0); UiFlags.reduceMotion = sp.getBoolean("rm", false); UiFlags.haptics = sp.getBoolean("hp", true)
        setContent { MaterialTheme(darkColorScheme(primary = Glow, onPrimary = Color.White, background = Bg0, surface = Bg1, onSurface = Txt, onBackground = Txt, secondaryContainer = Color(0xFF242424), onSecondaryContainer = Txt)) {
            Surface(Modifier.fillMaxSize(), color = Bg0) { ToastRoot { TechBackground(painterResource(R.drawable.bg_char)) { InfoBoot(this@InfoActivity) } } } } }
    }
}

@Composable fun InfoBoot(c: Context) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val sp = c.getSharedPreferences("info", 0)
        status = "Loading guides"; prog = .25f; delay(350); status = "Checking your apps"; prog = .5f; delay(300)
        status = "Fetching the latest news"; CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; prog = .85f; delay(350)
        status = if (CtlState.online) "Connected" else "Offline mode"; prog = 1f; delay(450); ready = true }
    SplashSwitch(ready, { CoolSplash(painterResource(R.drawable.logo), "NACHT X", "SHADOW INFORMATION", 3, status, prog) { InfoGlyph(30) } }) { InfoMain(c) }
}

fun dlLink(c: Context): String = c.getSharedPreferences("info", 0).getString("dl", "")?.takeIf { it.isNotBlank() } ?: CtlState.data?.updateUrl?.takeIf { it.isNotBlank() } ?: Control.MEDIAFIRE
fun ver(c: Context, pkg: String): String? = try { c.packageManager.getPackageInfo(pkg, 0).versionName ?: "?" } catch (e: Exception) { null }

@Composable fun Expand(ic: String, title: String, content: @Composable ColumnScope.() -> Unit) { var open by remember { mutableStateOf(false) }
    Glass(Modifier.press { open = !open }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(ic, fontSize = 22.sp); Spacer(Modifier.width(10.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(if (open) "−" else "+", color = Dim, fontSize = 22.sp) }; if (open) content() } }

@Composable fun AppRow(c: Context, name: String, pkg: String, ic: String) { val v = ver(c, pkg)
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Text(ic, fontSize = 26.sp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Black); Text(if (v != null) "Installed · v$v" else "Not installed", color = Dim, fontSize = 12.sp) }; Chip(if (v != null) "INSTALLED" else "MISSING", if (v != null) Ok else Warn) }
        if (v != null) Btn("OPEN", false) { c.packageManager.getLaunchIntentForPackage(pkg)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } else Btn("GET IT FROM THE DOWNLOAD TAB", false) { openUrl(c, dlLink(c)) } } }

@Composable fun HomeP(c: Context, go: (Int) -> Unit) {
    val all = remember { XF + GF }; var i by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3200); i = (i + 1) % all.size } }
    val ix = ver(c, "com.unoxidized.x") != null; val ig = ver(c, "com.unoxidized.gen") != null; val iz = ver(c, "moe.shizuku.privileged.api") != null; val done = listOf(ix, ig, iz).count { it }
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 84); Spacer(Modifier.width(14.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("NACHT X", Modifier.shimmer(), fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = Glow); Text("INFORMATION CENTER · V$VER", color = Dim, fontSize = 10.sp, letterSpacing = 2.sp)
        Chip(if (CtlState.online) "LIVE NEWS ONLINE" else "OFFLINE MODE", if (CtlState.online) Ok else Dim) } }
        Text("Everything about NACHT X and NACHT X Generator: features, guides, downloads and the showcase.", color = Color(0xFFCCCCCC), fontSize = 13.sp) }
    AnnounceCard(); UpdateCard(VER)
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Gauge(done / 3f, done * 100 / 3, "SETUP READY", if (done == 3) Ok else Warn, 120); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Setup checklist", fontWeight = FontWeight.Black)
            listOf("NACHT X" to ix, "Generator" to ig, "Shizuku" to iz).forEach { (k, v) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k, Modifier.weight(1f), fontSize = 13.sp); Chip(if (v) "READY" else "MISSING", if (v) Ok else Warn) } } } } }
    LiveLinks(c)
    Cap("Did you know")
    AnimatedContent(i, transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }) togetherWith fadeOut(tween(200)) }, label = "tick") { k ->
        val f = all[k % all.size]; Glass { Row(verticalAlignment = Alignment.Top) { Text(f.ic, fontSize = 26.sp); Spacer(Modifier.width(12.dp)); Column { Text(f.t, fontWeight = FontWeight.Black); Text(f.d, color = Dim, fontSize = 12.sp) } } } }
    StatRow("FEATURES" to "${all.size}", "GUIDE SECTIONS" to "${GUIDE.size}"); StatRow("FAQ ANSWERS" to "${FAQ.size}", "VERSION" to "v$VER")
    Cap("Explore")
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "✨", "FEATURES", "Everything the apps do") { go(1) }; Tile(Modifier.weight(1f), "📖", "STEP BY STEP", "Full setup guide") { go(2) } }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "⬇️", "DOWNLOAD", "Official zip link") { go(3) }; Tile(Modifier.weight(1f), "👀", "SHOWCASE", "Features and pricelist") { go(4) } }
    Cap("Your apps"); AppRow(c, "NACHT X", "com.unoxidized.x", "🎮"); AppRow(c, "NACHT X Generator", "com.unoxidized.gen", "🗝️")
}
@Composable fun FeaturesP() {
    H("Features", "Everything inside both apps.")
    Cap("NACHT X"); XF.forEach { f -> Glass { Row(verticalAlignment = Alignment.Top) { Text(f.ic, fontSize = 22.sp); Spacer(Modifier.width(12.dp)); Column { Text(f.t, fontWeight = FontWeight.Black); Text(f.d, color = Dim, fontSize = 12.sp) } } } }
    Cap("NACHT X Generator"); GF.forEach { f -> Glass { Row(verticalAlignment = Alignment.Top) { Text(f.ic, fontSize = 22.sp); Spacer(Modifier.width(12.dp)); Column { Text(f.t, fontWeight = FontWeight.Black); Text(f.d, color = Dim, fontSize = 12.sp) } } } }
}
@Composable fun GuideP(c: Context) {
    H("Step by step", "Tap a section to open it.")
    GUIDE.forEachIndexed { si, s -> Expand(s.ic, "${si + 1}. ${s.t}") {
        s.steps.forEachIndexed { i, t -> Row(verticalAlignment = Alignment.Top) { Text("${i + 1}", Modifier.width(24.dp), fontWeight = FontWeight.Black, color = Dim); Text(t, color = Color(0xFFDDDDDD), fontSize = 13.sp) } }
        if (si == 0) Btn("OPEN DOWNLOAD LINK") { openUrl(c, dlLink(c)) }
        if (si == 2) { Btn("INSTALL SHIZUKU") { openUrl(c, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api") }; Btn("OPEN DEVELOPER OPTIONS", false) { try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} } }
        if (si == 1) Btn("OPEN NACHT X GENERATOR", false) { c.packageManager.getLaunchIntentForPackage("com.unoxidized.gen")?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } } }
}
@Composable fun DownloadP(c: Context) { val cb = LocalClipboardManager.current; val toast = LocalToast.current; val sp = remember { c.getSharedPreferences("info", 0) }
    var tick by remember { mutableStateOf(0) }; var edit by remember { mutableStateOf(false) }
    val link = remember(tick, CtlState.data) { dlLink(c) }; val custom = remember(tick) { !sp.getString("dl", "").isNullOrBlank() }
    H("Download", "The link for all the apps.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Text("⬇️", fontSize = 26.sp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("UnoxidizedX.zip", fontWeight = FontWeight.Black); Text(if (custom) "Your own link" else "Official link", color = Dim, fontSize = 12.sp) }; Chip(if (custom) "CUSTOM" else "OFFICIAL", if (custom) Warn else Ok) }
        SelectionContainer { Text(link, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFCCCCCC)) }
        Btn("OPEN LINK") { openUrl(c, link) }; Btn("COPY LINK", false) { cb.setText(AnnotatedString(link)); toast("Link copied") }
        Btn("SHARE LINK", false) { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "NACHT X: $link"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Btn("EDIT LINK", false) { edit = true } }
    if (edit) { var t by remember { mutableStateOf(link) }
        androidx.compose.ui.window.Dialog({ edit = false }) { Glass(Modifier.padding(20.dp)) { Cap("Download link")
            OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            Text("Saved on this phone. The owner can also change it for everyone from the Owner app.", color = Dim, fontSize = 11.sp)
            Btn("SAVE LINK", true, t.trim().startsWith("http")) { sp.edit().putString("dl", t.trim()).apply(); tick++; edit = false; toast("Link saved") }
            Btn("USE THE OFFICIAL LINK", false) { sp.edit().remove("dl").apply(); tick++; edit = false; toast("Back to the official link") }; Btn("CANCEL", false) { edit = false } } } }
    Cap("Inside the zip"); Glass { listOf("🎮  NACHT X.apk", "🗝️  NACHT X Generator.apk", "ℹ️  NACHT X Informationrmation.apk").forEach { Text(it, fontWeight = FontWeight.Bold) } }
    Cap("You also need"); Glass { Text("🛡️  Shizuku (free)", fontWeight = FontWeight.Bold); Text("It gives NACHT X permission to change game settings.", color = Dim, fontSize = 12.sp)
        Btn("SHIZUKU ON PLAY STORE", false) { openUrl(c, "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api") }; Btn("SHIZUKU WEBSITE", false) { openUrl(c, "https://shizuku.rikka.app/download/") } }
}
@Composable fun ChangeP() { H("What's new", "Version history.")
    CtlState.data?.notes?.takeIf { it.isNotBlank() }?.let { n -> Glass { Chip("LATEST NOTES", Ok); Text(n, fontSize = 13.sp) } }
    listOf("6.0" to listOf("Remote announcements, maintenance mode and device revoke", "Showcase format with pricelist", "Cooler animated splash screens and icons", "NACHT X Informationrmation app with editable download link", "Owner control center"),
        "5.0" to listOf("CODM launch detection", "Session and FPS tracking", "Before and after comparison", "Custom profiles, pull to refresh, swipe gestures"),
        "4.0" to listOf("Redesigned look and live monitor", "Shizuku access gate", "Import and export profile codes"), "3.0" to listOf("Two apps: NACHT X and Generator", "Weekly device-bound keys")).forEach { (v, l) ->
        Glass { Row(verticalAlignment = Alignment.CenterVertically) { Text("v$v", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Black); if (v == VER) Chip("CURRENT", Ok) }; l.forEach { Text("•  $it", fontSize = 13.sp, color = Color(0xFFDDDDDD)) } } } }
@Composable fun FaqP() { H("FAQ", "Quick answers."); FAQ.forEach { (q, a) -> Expand("❓", q) { Text(a, color = Color(0xFFDDDDDD), fontSize = 13.sp) } } }
@Composable fun UpdatesP(c: Context, sp: android.content.SharedPreferences) { val scope = rememberCoroutineScope(); val toast = LocalToast.current; val d = CtlState.data
    H("Updates", "News and versions from the owner.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Status"); Spacer(Modifier.weight(1f)); Chip(if (CtlState.online) "ONLINE" else if (CtlState.noFile) "NO NEWS YET" else "OFFLINE", if (CtlState.online) Ok else Dim) }
        StatRow("LATEST" to (d?.latest?.ifBlank { "Not set" } ?: "Not available"), "THIS INFO APP" to "v$VER"); StatRow("X INSTALLED" to (ver(c, "com.unoxidized.x")?.let { "v$it" } ?: "No"), "GENERATOR" to (ver(c, "com.unoxidized.gen")?.let { "v$it" } ?: "No"))
        Text("Last checked: " + (sp.getLong("ctl_at", 0L).let { if (it == 0L) "Never" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }), color = Dim, fontSize = 11.sp)
        Btn("CHECK NOW", false) { scope.launch { CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; toast(if (CtlState.online) "Up to date with the server" else "Offline, showing saved info") } } }
    AnnounceCard(); UpdateCard(VER)
    if (d == null || (d.announce.isBlank() && d.latest.isBlank())) Text("No announcements right now.", color = Dim, fontSize = 12.sp)
}
@Composable fun ContactP(c: Context) { H("Contact", "Support, keys and questions.")
    Glass { Text("✈️  Telegram", fontWeight = FontWeight.Black); Text("@UnoxidizedX", fontSize = 20.sp, fontWeight = FontWeight.Black); Text("Message for keys, support or custom access.", color = Dim, fontSize = 12.sp); Btn("OPEN TELEGRAM") { openUrl(c, "https://t.me/UnoxidizedX") } } }
@Composable fun SetP(sp: android.content.SharedPreferences) { var rm by remember { mutableStateOf(UiFlags.reduceMotion) }; var hp by remember { mutableStateOf(UiFlags.haptics) }
    H("Settings", "Make it yours.")
    Glass { Toggle("Reduce motion", "Calmer background and shorter transitions", rm) { rm = it; UiFlags.reduceMotion = it; sp.edit().putBoolean("rm", it).apply() }
        Toggle("Haptic feedback", "Vibrate lightly on button taps", hp) { hp = it; UiFlags.haptics = it; sp.edit().putBoolean("hp", it).apply() } }
    Glass { Cap("About"); Text("NACHT X Information v$VER", fontWeight = FontWeight.Bold); Text("Made by @UnoxidizedX on Telegram.", color = Dim, fontSize = 12.sp) } }

@Composable fun InfoMain(c: Context) {
    val sp = remember { c.getSharedPreferences("info", 0) }; val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope(); var page by remember { mutableStateOf(0) }
    val pages = listOf("Home" to "🏠", "Features" to "✨", "Step by step" to "📖", "Download" to "⬇️", "Showcase" to "👀", "FAQ" to "❓", "What's new" to "🆕", "Updates" to "🔔", "Contact" to "✈️", "Settings" to "⚙️")
    val toast = LocalToast.current; BackHandler(enabled = page != 0) { page = 0 }
    LaunchedEffect(Unit) { while (true) { delay(600000); CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) } } }
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF080808)) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 52); Spacer(Modifier.width(12.dp)); Column { Text("NACHT X INFORMATION", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp); Text("MENU", color = Dim, fontSize = 9.sp, letterSpacing = 2.sp) } }
        pages.forEachIndexed { i, (n, ic) -> NavigationDrawerItem({ Text("$ic   $n", fontWeight = FontWeight.Bold) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF242424), unselectedContainerColor = Color.Transparent, selectedTextColor = Glow, unselectedTextColor = Color(0xFFBBBBBB))) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 40); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("NACHT X INFORMATION", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp); Text(pages[page].first.uppercase(), color = Dim, fontSize = 9.sp, letterSpacing = 2.sp) }; Chip(if (CtlState.online) "ONLINE" else if (CtlState.noFile) "NO NEWS YET" else "OFFLINE", if (CtlState.online) Ok else Dim) }
            Box(Modifier.weight(1f).swipeTabs({ if (page in 1..3) { page-- } else if (page >= 4) { page = 0 } }, { if (page < 3) { page++ } })) {
                PageSwitch(page) { p -> PullRefresh({ CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; toast(if (CtlState.online) "Updated" else "Offline") }) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                    when (p) { 0 -> HomeP(c) { page = it }; 1 -> FeaturesP(); 2 -> GuideP(c); 3 -> DownloadP(c); 4 -> { H("Showcase", "Post-ready format with features and pricelist."); ShowcaseCard(VER) }; 5 -> FaqP(); 6 -> ChangeP(); 7 -> UpdatesP(c, sp); 8 -> ContactP(c); else -> SetP(sp) }
                    Spacer(Modifier.height(8.dp)) } } }
            }
            Box(Modifier.fillMaxWidth(), Alignment.Center) { Watermark() }
            NavBar(listOf("🏠" to "HOME", "✨" to "FEATURES", "📖" to "GUIDE", "⬇️" to "GET", "☰" to "MORE"), if (page < 4) page else 4) { i -> if (i == 4) { scope.launch { drawer.open() } } else { page = i } }
        }
    }
}

@Composable fun LiveLinks(c: Context) {
    var m by remember { mutableStateOf<Bundle?>(null) }; var g by remember { mutableStateOf<Bundle?>(null) }
    LaunchedEffect(Unit) { while (true) { m = withContext(Dispatchers.IO) { SyncClient.pull(c, "com.unoxidized.x.sync") }; g = withContext(Dispatchers.IO) { SyncClient.pull(c, "com.unoxidized.gen.sync") }; delay(6000) } }
    Glass {
        Cap("Live link")
        Row(verticalAlignment = Alignment.CenterVertically) { Text("NACHT X", Modifier.weight(1f), fontWeight = FontWeight.Bold); Chip(if (m != null) "LINKED" else "NO LINK", if (m != null) Ok else Dim) }
        m?.let { StatRow("KEY" to (if (it.getBoolean("lic")) "Valid" else "None"), "DAYS LEFT" to (if (!it.getBoolean("lic")) "n/a" else if (it.getLong("left") > 9000) "Lifetime" else it.getLong("left").toString())) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("NACHT X Generator", Modifier.weight(1f), fontWeight = FontWeight.Bold); Chip(if (g != null) "LINKED" else "NO LINK", if (g != null) Ok else Dim) }
        g?.let { StatRow("DEVICES" to it.getInt("total").toString(), "ACTIVE KEYS" to it.getInt("active").toString()) }
        Text("Read straight from the other apps on this phone. Nothing is guessed.", color = Dim, fontSize = 11.sp)
    }
}
