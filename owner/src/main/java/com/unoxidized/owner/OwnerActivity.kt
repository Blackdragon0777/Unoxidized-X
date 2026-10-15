package com.unoxidized.owner
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

val OWNER_HASH: String = BuildConfig.OWNER_HASH
val OPRIV: String = BuildConfig.OPRIV
const val MAIN_AUTH = "com.unoxidized.x.sync"; const val GEN_AUTH = "com.unoxidized.gen.sync"
fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
fun ownerOk(k: String) = MessageDigest.isEqual(sha(k.trim().uppercase() + "|unx.owner.v1").toByteArray(), OWNER_HASH.toByteArray())
fun fmtT(t: Long): String = if (t <= 0) "Never" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t))

object Mint {
    /** Signed v2 key. Only this app holds the signing key. dur = 0 means lifetime. */
    fun key(dev: String, dur: Long, role: String): String {
        val h = "UX2-" + dev.uppercase() + "-" + Lic.b36(Lic.today()) + "-" + Lic.b36(dur) + "-" + role
        val pk = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(OPRIV, Base64.DEFAULT)))
        val s = Signature.getInstance("SHA256withECDSA"); s.initSign(pk); s.update(h.toByteArray())
        return h + "." + Base64.encodeToString(s.sign(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
class VKey(val id: Long, val dev: String, val role: String, val issued: Long, val dur: Long, val key: String, val note: String)
fun vleft(k: VKey): Long = if (k.dur == 0L) 99999L else k.issued / 86400000L + k.dur - Lic.today()
object Vault {
    fun list(sp: SharedPreferences): List<VKey> = try { val a = JSONArray(sp.getString("vault", "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); VKey(o.getLong("id"), o.getString("d"), o.getString("r"), o.getLong("i"), o.getLong("u"), o.getString("k"), o.getString("n")) } } catch (e: Exception) { emptyList() }
    fun save(sp: SharedPreferences, l: List<VKey>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("d", it.dev).put("r", it.role).put("i", it.issued).put("u", it.dur).put("k", it.key).put("n", it.note)) }; sp.edit().putString("vault", a.toString()).apply() }
    fun add(sp: SharedPreferences, k: VKey) = save(sp, listOf(k) + list(sp))
    fun remove(sp: SharedPreferences, id: Long) = save(sp, list(sp).filter { it.id != id })
}
object OLog {
    fun list(sp: SharedPreferences): List<Pair<Long, String>> = try { val a = JSONArray(sp.getString("log", "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); o.getLong("t") to o.getString("m") } } catch (e: Exception) { emptyList() }
    private fun save(sp: SharedPreferences, l: List<Pair<Long, String>>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("t", it.first).put("m", it.second)) }; sp.edit().putString("log", a.toString()).apply() }
    fun add(sp: SharedPreferences, m: String) = save(sp, (listOf(System.currentTimeMillis() to m) + list(sp)).take(100))
    fun remove(sp: SharedPreferences, t: Long) = save(sp, list(sp).filter { it.first != t })
    fun clear(sp: SharedPreferences) { sp.edit().remove("log").apply() }
}

class OwnerActivity : ComponentActivity() {
    var unlocked by mutableStateOf(false); private var bgAt = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)); window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val sp = getSharedPreferences("own", 0); UiFlags.reduceMotion = sp.getBoolean("rm", false); UiFlags.haptics = sp.getBoolean("hp", true)
        setContent { MaterialTheme(darkColorScheme(primary = Glow, onPrimary = Color.White, background = Bg0, surface = Bg1, onSurface = Txt, onBackground = Txt, secondaryContainer = Color(0xFF242424), onSecondaryContainer = Txt)) {
            Surface(Modifier.fillMaxSize(), color = Bg0) { ToastRoot { TechBackground(painterResource(R.drawable.bg_char)) { OwnerBoot(this@OwnerActivity) } } } } }
    }
    override fun onStop() { super.onStop(); bgAt = System.currentTimeMillis() }
    override fun onStart() { super.onStart(); val sp = getSharedPreferences("own", 0)
        if (unlocked && bgAt > 0 && System.currentTimeMillis() - bgAt > sp.getInt("lockMin", 2) * 60000L) { unlocked = false; OLog.add(sp, "Auto-locked after inactivity") } }
}

@Composable fun OwnerBoot(act: OwnerActivity) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        status = "Securing session"; prog = .3f; delay(400)
        status = "Checking linked apps"; val m = withContext(Dispatchers.IO) { SyncClient.pull(act, MAIN_AUTH) }; withContext(Dispatchers.IO) { SyncClient.pull(act, GEN_AUTH) }; prog = .8f; delay(350)
        status = if (m != null) "NACHT X linked" else "NACHT X not linked"; prog = 1f; delay(450); ready = true }
    SplashSwitch(ready, { CoolSplash(painterResource(R.drawable.logo), "NACHT X OWNER", "PRIVATE CONTROL CENTER", 2, status, prog) { CrownGlyph(30) } }) { if (!act.unlocked) OwnerLogin(act) else OwnerMain(act) }
}

@Composable fun OwnerLogin(act: OwnerActivity) {
    val sp = remember { act.getSharedPreferences("own", 0) }; var t by remember { mutableStateOf("") }; var show by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }; LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val lockLeft = ((sp.getLong("lockUntil", 0L) - now) / 1000).coerceAtLeast(0)
    fun go() {
        if (lockLeft > 0) return
        if (ownerOk(t)) { sp.edit().putInt("fails", 0).putLong("lastLogin", System.currentTimeMillis()).apply(); OLog.add(sp, "Owner session started"); act.unlocked = true }
        else { val f = sp.getInt("fails", 0) + 1; val e = sp.edit().putInt("fails", f).putInt("failsTotal", sp.getInt("failsTotal", 0) + 1)
            if (f >= 5) e.putLong("lockUntil", System.currentTimeMillis() + minOf(600L, 30L * (1L shl minOf(f - 5, 4))) * 1000)
            e.apply(); OLog.add(sp, "Failed Owner Key attempt ($f)"); err = if (f >= 5) "Too many attempts. Access is locked for a while." else "Invalid Owner Key. ${5 - f} attempt(s) left before a lockout." }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        LogoImg(painterResource(R.drawable.logo), 170); Spacer(Modifier.height(10.dp))
        Text("OWNER ACCESS", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp); Text("ONLY THE OWNER KEY OPENS THIS APP", color = Dim, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(22.dp))
        Glass { Cap("Owner key")
            OutlinedTextField(t, { t = it; err = "" }, Modifier.fillMaxWidth(), placeholder = { Text("UNX-OWNER-XXXXXX") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn(if (show) "HIDE" else "SHOW") { show = !show } }
            if (lockLeft > 0) Text("Locked. Try again in ${lockLeft}s.", color = Warn, fontSize = 13.sp) else if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
            Btn("UNLOCK OWNER CONTROL", true, lockLeft == 0L && t.isNotBlank()) { go() } }
        Spacer(Modifier.height(10.dp)); Text("Generator keys and user keys never open this app.", color = Dim, fontSize = 12.sp); Watermark()
    }
}

class OS(val act: OwnerActivity, val sp: SharedPreferences) {
    var tick by mutableStateOf(0); var main by mutableStateOf<Bundle?>(null); var gen by mutableStateOf<Bundle?>(null); var lastSync by mutableStateOf(0L); var syncing by mutableStateOf(false)
    val me = Lic.deviceId(act); val ce = CE()
    fun installed(pkg: String) = try { act.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
    suspend fun sync() { syncing = true; val m = withContext(Dispatchers.IO) { SyncClient.pull(act, MAIN_AUTH) }; val g = withContext(Dispatchers.IO) { SyncClient.pull(act, GEN_AUTH) }; CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; main = m; gen = g; lastSync = System.currentTimeMillis(); syncing = false; tick++ }
    fun send(k: String): Boolean = try { act.startActivity(Intent().setClassName("com.unoxidized.x", "com.unoxidized.x.MainActivity").putExtra("ux_key", k).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)); true } catch (e: Exception) { false }
    fun share(k: String) { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, k), "Share key").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable fun KeyCard(os: OS, k: VKey, onDelete: () -> Unit) {
    val cb = LocalClipboardManager.current; val toast = LocalToast.current; val l = vleft(k)
    Glass(Modifier.swipeDelete(onDelete)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(k.dev, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Chip(Lic.roleName(k.role).uppercase(), Glow); Spacer(Modifier.width(6.dp)); Chip(if (l <= 0) "EXPIRED" else if (k.dur == 0L) "LIFETIME" else "$l DAYS", if (l <= 0) Bad else Ok) }
        if (k.note.isNotBlank()) Text(k.note, color = Color(0xFFCCCCCC), fontSize = 13.sp)
        Text("Issued ${fmtT(k.issued)}", color = Dim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("COPY") { cb.setText(AnnotatedString(k.key)); toast("Key copied") }; MiniBtn("SEND") { if (!os.send(k.key)) toast("Install NACHT X first") }; MiniBtn("SHARE") { os.share(k.key) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("REVOKE") { if (!os.ce.revoked.contains(k.dev)) os.ce.revoked.add(k.dev); toast("Added to revoke list. Publish in Server control.") }; MiniBtn("DELETE") { onDelete() } }
    }
}

@Composable fun DashPage(os: OS, go: (Int) -> Unit) {
    val v = remember(os.tick) { Vault.list(os.sp) }; val conn = listOf(os.main).count { it != null }; val scope = rememberCoroutineScope(); val toast = LocalToast.current
    BannerImg(painterResource(R.drawable.nachtx_banner), 150, "NACHT X OWNER")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) {
        Gauge(conn / 1f, conn, "MAIN APP LINKED", if (conn == 1) Ok else Dim, 140); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Chip("OWNER SESSION ACTIVE", Ok); Chip(if (CtlState.online) "CONTROL FILE ONLINE" else "CONTROL FILE OFFLINE", if (CtlState.online) Ok else Dim)
            Text("Connected means the app answered a secure on-device sync. Nothing here is estimated.", color = Dim, fontSize = 11.sp) } } }
    StatRow("KEYS ISSUED" to "${v.size}", "ACTIVE" to "${v.count { vleft(it) > 0 }}")
    StatRow("EXPIRING (3D)" to "${v.count { vleft(it) in 1..3 }}", "EXPIRED" to "${v.count { vleft(it) <= 0 }}")
    Cap("Quick actions")
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "🔑", "ISSUE KEY", "Any role, any length") { go(1) }; Tile(Modifier.weight(1f), "🔄", "SYNC NOW", "Pull app status") { scope.launch { os.sync(); toast("Synced") } } }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "🌐", "SERVER CONTROL", "Revoke, announce, maintain") { go(4) }; Tile(Modifier.weight(1f), "🔒", "LOCK NOW", "End this session") { os.act.unlocked = false } }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { Tile(Modifier.weight(1f), "👥", "ACCOUNTS", "Live users and payments") { go(11) }; Tile(Modifier.weight(1f), "💳", "GITHUB REQUESTS", "Backup approval route") { go(10) } }
    StatRow("FAILED ATTEMPTS" to "${os.sp.getInt("failsTotal", 0)}", "LAST LOGIN" to fmtT(os.sp.getLong("lastLogin", 0L)).take(12))
    Cap("Recent activity")
    val log = remember(os.tick) { OLog.list(os.sp).take(4) }
    if (log.isEmpty()) Text("No activity yet.", color = Dim, fontSize = 12.sp) else log.forEach { (t, m) -> Glass { Text(m, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(fmtT(t), color = Dim, fontSize = 11.sp) } }
}

@Composable fun KeysPage(os: OS) {
    val cb = LocalClipboardManager.current; val toast = LocalToast.current
    var tab by remember { mutableStateOf("Issue") }; var dev by remember { mutableStateOf(os.me) }; var role by remember { mutableStateOf("Free") }; var dur by remember { mutableStateOf("30 days") }
    var custom by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }; var insp by remember { mutableStateOf("") }
    H("Key Studio", "Signed keys only the Owner app can create.")
    Pick(listOf("Issue", "Inspect"), tab) { tab = it }
    if (tab == "Issue") {
        Glass { Cap("Device ID"); OutlinedTextField(dev, { dev = it.uppercase().filter { c -> !c.isWhitespace() } }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("PASTE") { dev = (cb.getText()?.text ?: "").uppercase().filter { c -> !c.isWhitespace() } }; MiniBtn("THIS PHONE") { dev = os.me } }
            Cap("Role"); Pick(listOf("Free", "Premium", "Premium+"), role) { role = it }
            Cap("Duration"); Pick(listOf("7 days", "30 days", "90 days", "1 year", "Lifetime"), dur) { dur = it; custom = "" }
            OutlinedTextField(custom, { custom = it.filter { c -> c.isDigit() }.take(4) }, Modifier.fillMaxWidth(), label = { Text("Custom days (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            OutlinedTextField(note, { note = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Note, e.g. who it's for") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
            Btn("GENERATE KEY") {
                if (!Regex("[A-F0-9]{12}").matches(dev)) { err = "Device ID must be 12 letters/numbers (0-9, A-F)."; key = ""; return@Btn }
                val d = custom.toLongOrNull()?.takeIf { it in 1..3650 } ?: when (dur) { "7 days" -> 7L; "30 days" -> 30L; "90 days" -> 90L; "1 year" -> 365L; else -> 0L }
                val r = when (role) { "Premium" -> "M"; "Premium+" -> "A"; else -> "F" }
                key = Mint.key(dev, d, r); err = ""; Vault.add(os.sp, VKey(System.currentTimeMillis(), dev, r, System.currentTimeMillis(), d, key, note)); OLog.add(os.sp, "Issued $role key for $dev (" + (if (d == 0L) "lifetime" else "$d days") + ")"); os.tick++; toast("Key issued and saved") } }
        if (key.isNotEmpty()) Glass { Cap("New key (saved to the vault)"); androidx.compose.foundation.text.selection.SelectionContainer { Text(key, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Btn("COPY KEY") { cb.setText(AnnotatedString(key)); toast("Key copied") }; Btn("SEND TO NACHT X", false) { if (!os.send(key)) toast("Install NACHT X first") }; Btn("SHARE", false) { os.share(key) } }
    } else {
        Glass { Cap("Paste any key"); OutlinedTextField(insp, { insp = it }, Modifier.fillMaxWidth(), placeholder = { Text("UX-... or UX2-...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 4)
            MiniBtn("PASTE") { insp = cb.getText()?.text ?: "" } }
        if (insp.isNotBlank()) { val i = Lic.inspect(insp)
            Glass { if (i == null) { Chip("NOT A KEY", Bad); Text("That text isn't an Unoxidized key.", color = Dim, fontSize = 12.sp) } else {
                Row(verticalAlignment = Alignment.CenterVertically) { Cap("Result"); Spacer(Modifier.weight(1f)); Chip(if (!i.sigOk) "FORGED" else if (i.expired) "EXPIRED" else "VALID", if (!i.sigOk) Bad else if (i.expired) Warn else Ok) }
                StatRow("FORMAT" to (if (i.ver == 2) "Owner-signed (v2)" else "Free (v1)"), "ROLE" to Lic.roleName(i.role))
                StatRow("DEVICE" to i.dev, "THIS PHONE" to (if (i.dev == os.me) "Yes" else "No"))
                StatRow("ISSUED" to Lic.dateText(i.day), "EXPIRES" to i.untilText) } } }
    }
}

@Composable fun AppCard(title: String, icon: String, installed: Boolean, b: Bundle?, content: @Composable ColumnScope.(Bundle) -> Unit) = Glass {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 26.sp); Spacer(Modifier.width(10.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        if (b != null) Chip("CONNECTED", Ok) else if (!installed) Chip("NOT INSTALLED", Dim) else Chip("UNABLE TO CONNECT", Warn) }
    if (b != null) content(b) else Text(if (!installed) "Install this app on the same phone to sync." else "Data unavailable. Update the app to v3 or newer, signed with the same key.", color = Dim, fontSize = 12.sp)
}

@Composable fun SyncPage(os: OS) {
    val scope = rememberCoroutineScope(); val m = os.main
    H("Sync", "Live status from the other apps on this phone.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Cap("Last sync"); Text(fmtT(os.lastSync), fontWeight = FontWeight.Bold) }; if (os.syncing) CircularProgressIndicator(color = Glow, modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }
        Btn("SYNC NOW", false, !os.syncing) { scope.launch { os.sync() } } }
    AppCard("NACHT X", "🎮", os.installed("com.unoxidized.x"), m) { b ->
        StatRow("VERSION" to (b.getString("ver") ?: "?"), "DEVICE" to (b.getString("dev") ?: "?"))
        StatRow("LICENSE" to (if (b.getBoolean("lic")) "Valid" else (b.getString("licMsg") ?: "n/a")), "ACCESS" to (if (b.getBoolean("lic")) Lic.roleName(b.getString("role") ?: "F") else "n/a"))
        StatRow("DAYS LEFT" to (if (!b.getBoolean("lic")) "n/a" else if (b.getLong("left") > 9000) "Lifetime" else b.getLong("left").toString()), "SHIZUKU" to (if (b.getBoolean("shizuku")) "Authorized" else "Not connected"))
        StatRow("LAST BOOST" to fmtT(b.getLong("lastopt")).take(12), "LAST LAUNCH" to fmtT(b.getLong("lastlaunch")).take(12))
        StatRow("SESSIONS" to b.getInt("sessions").toString(), "LAST AVG FPS" to (b.getInt("lastFps").let { if (it < 0) "Not available" else it.toString() })) }
    Cap("Backend services")
    Glass { val cd = CtlState.data
        listOf("Control file" to (if (CtlState.online) "ONLINE" else "OFFLINE"), "Maintenance mode" to (if (cd?.maintenance == true) "ON" else "OFF"), "Announcement" to (if (cd?.announce?.isNotBlank() == true) "ACTIVE" else "NONE"), "Revoked devices" to "${cd?.revoked?.size ?: 0}").forEach { (k, v) -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k, Modifier.weight(1f)); Chip(v, if (v == "ONLINE" || v == "ACTIVE") Ok else if (v == "ON") Warn else Dim) } }
        Text("Remote control runs through your GitHub control file. A live list of every user would need a write-capable server, so no user counts are shown.", color = Dim, fontSize = 11.sp) }
}

@Composable fun SecPage(os: OS) {
    var min by remember { mutableStateOf(os.sp.getInt("lockMin", 2)) }
    H("Security", "Your session and access protection.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Protection"); Spacer(Modifier.weight(1f)); Chip("ACTIVE", Ok) }
        listOf("Owner Key checked as a hash, never stored in plain text", "Screenshots and app-switcher previews blocked", "Lockout after 5 wrong keys, growing each time", "Auto-lock when the app is in the background").forEach { Text("✓  $it", fontSize = 13.sp, color = Color(0xFFCCCCCC)) } }
    Glass { Cap("Auto-lock after"); Pick(listOf("1 min", "2 min", "5 min", "10 min"), "$min min") { min = it.substringBefore(" ").toInt(); os.sp.edit().putInt("lockMin", min).apply() }
        StatRow("FAILED ATTEMPTS" to "${os.sp.getInt("failsTotal", 0)}", "LAST LOGIN" to fmtT(os.sp.getLong("lastLogin", 0L)).take(12))
        Btn("LOCK NOW", false) { os.act.unlocked = false } }
    Cap("Be aware")
    Glass { Text("• This Owner app can create unlimited keys. Never share its APK.\n• The Owner Key is checked on this phone only. There's no server, so a determined person with the APK could try to bypass it. Keep the APK private.\n• Signed v2 keys can't be forged from the main app or the Generator, because only this app holds the signing key.", color = Color(0xFFCCCCCC), fontSize = 12.sp) }
}

@Composable fun VaultPage(os: OS) {
    val cb = LocalClipboardManager.current; val toast = LocalToast.current; var f by remember { mutableStateOf("All") }; var ask by remember { mutableStateOf(false) }
    val all = remember(os.tick) { Vault.list(os.sp) }; val list = all.filter { f == "All" || (f == "Active" && vleft(it) > 0) || (f == "Expired" && vleft(it) <= 0) }
    H("Key vault", "Every key you issued from this app.")
    Pick(listOf("All", "Active", "Expired"), f) { f = it }
    if (list.isEmpty()) Glass { Text("Nothing here", fontWeight = FontWeight.Bold); Text("Issue a key in Key Studio and it shows up here.", color = Dim, fontSize = 12.sp) }
    list.forEach { k -> KeyCard(os, k) { Vault.remove(os.sp, k.id); OLog.add(os.sp, "Deleted vault key for ${k.dev}"); os.tick++ } }
    if (all.isNotEmpty()) { Text("Swipe a key left or right to delete it.", color = Dim, fontSize = 11.sp)
        Btn("COPY ALL KEYS AS TEXT", false) { cb.setText(AnnotatedString(all.joinToString("\n") { "${it.dev} | ${Lic.roleName(it.role)} | ${it.note} | ${it.key}" })); toast("Copied ${all.size} keys") }
        Btn("CLEAR VAULT", false) { ask = true } }
    if (ask) Confirm("Clear the vault?", "This deletes every saved key record on this phone. Keys already given out keep working.", { Vault.save(os.sp, emptyList()); OLog.add(os.sp, "Vault cleared"); os.tick++; ask = false }, { ask = false })
}

@Composable fun LogPage(os: OS) {
    var ask by remember { mutableStateOf(false) }; val log = remember(os.tick) { OLog.list(os.sp) }
    H("Activity log", "What happened in this app.")
    if (log.isEmpty()) Glass { Text("No activity yet", fontWeight = FontWeight.Bold) }
    log.forEach { (t, m) -> Glass(Modifier.swipeDelete { OLog.remove(os.sp, t); os.tick++ }) { Text(m, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(fmtT(t), color = Dim, fontSize = 11.sp) } }
    if (log.isNotEmpty()) { Text("Swipe an entry left or right to delete it.", color = Dim, fontSize = 11.sp); Btn("CLEAR LOG", false) { ask = true } }
    if (ask) Confirm("Clear the log?", "This removes all activity entries.", { OLog.clear(os.sp); os.tick++; ask = false }, { ask = false })
}

@Composable fun SetPage(os: OS) {
    var rm by remember { mutableStateOf(UiFlags.reduceMotion) }; var hp by remember { mutableStateOf(UiFlags.haptics) }
    H("Settings", "Make it yours.")
    Glass { Cap("Appearance")
        Toggle("Reduce motion", "Calmer background and shorter transitions", rm) { rm = it; UiFlags.reduceMotion = it; os.sp.edit().putBoolean("rm", it).apply() }
        Toggle("Haptic feedback", "Vibrate lightly on button taps", hp) { hp = it; UiFlags.haptics = it; os.sp.edit().putBoolean("hp", it).apply() } }
}

@Composable fun AboutPage() {
    H("About", "NACHT X Owner Control")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 64); Spacer(Modifier.width(14.dp)); Column { Text("NACHT X OWNER", fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("Private control center", color = Dim, fontSize = 12.sp) } }
        Text("Made by @UnoxidizedX on Telegram. Works together with NACHT X and NACHT X Generator on the same phone.", color = Color(0xFFCCCCCC), fontSize = 13.sp) }
}

@Composable fun OwnerMain(act: OwnerActivity) {
    val sp = remember { act.getSharedPreferences("own", 0) }; val os = remember { OS(act, sp) }; val toast = LocalToast.current
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope(); var page by remember { mutableStateOf(0) }
    val pages = listOf("Dashboard" to "🏠", "Key Studio" to "🔑", "Sync" to "🔄", "Security" to "🛡️", "Server Control" to "🌐", "Branding" to "🎨", "Key Vault" to "🗄️", "Activity Log" to "📜", "Settings" to "⚙️", "About" to "ℹ️", "Requests" to "💳", "Accounts" to "👥")
    LaunchedEffect(Unit) { os.sync() }
    BackHandler(enabled = page != 0) { page = 0 }
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF080808)) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 52); Spacer(Modifier.width(12.dp))
            Column { Text("NACHT X OWNER", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp); Text("PRIVATE CONTROL CENTER", color = Dim, fontSize = 9.sp, letterSpacing = 1.5.sp) } }
        pages.forEachIndexed { i, (n, ic) -> NavigationDrawerItem({ Text("$ic   $n", fontWeight = FontWeight.Bold) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF242424), unselectedContainerColor = Color.Transparent, selectedTextColor = Glow, unselectedTextColor = Color(0xFFBBBBBB))) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) { LogoImg(painterResource(R.drawable.logo), 40); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("NACHT X OWNER", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp); Text(pages[page].first.uppercase(), color = Dim, fontSize = 9.sp, letterSpacing = 2.sp) }; Chip("OWNER", Ok) }
            Box(Modifier.weight(1f).swipeTabs({ if (page in 1..3) { page-- } else if (page >= 4) { page = 0 } }, { if (page < 3) { page++ } })) {
                PageSwitch(page) { p -> PullRefresh({ os.sync(); toast("Synced") }) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                    when (p) { 0 -> DashPage(os) { page = it }; 1 -> KeysPage(os); 2 -> SyncPage(os); 3 -> SecPage(os); 4 -> ControlPage(os); 5 -> BrandingPage(os); 6 -> VaultPage(os); 7 -> LogPage(os); 8 -> SetPage(os); 10 -> RequestsPage(os); 11 -> CloudPage(os); else -> AboutPage() }
                    Spacer(Modifier.height(8.dp)) } } }
            }
            Box(Modifier.fillMaxWidth(), Alignment.Center) { Watermark() }
            NavBar(listOf("🏠" to "DASH", "🔑" to "KEYS", "🔄" to "SYNC", "🛡️" to "SECURE", "☰" to "MORE"), if (page < 4) page else 4) { i -> if (i == 4) { scope.launch { drawer.open() } } else { page = i } }
        }
    }
}
