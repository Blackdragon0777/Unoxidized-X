package com.unoxidized.gen
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.delay

class GenActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("gen", 0); UiFlags.reduceMotion = sp.getBoolean("rm", false); UiFlags.haptics = sp.getBoolean("hp", true)
        setContent { MaterialTheme(darkColorScheme(primary = Glow, onPrimary = Color.White, background = Bg0, surface = Bg1, onSurface = Txt)) {
            Surface(Modifier.fillMaxSize(), color = Bg0) { ToastRoot { TechBackground(painterResource(R.drawable.bg_char)) { GenBoot(this@GenActivity) } } } } }
    }
}

@Composable fun GenApp(c: Context) {
    val sp = remember { c.getSharedPreferences("gen", 0) }; val cb = LocalClipboardManager.current; val toast = LocalToast.current
    var tab by remember { mutableStateOf(0) }; var id by remember { mutableStateOf(Lic.deviceId(c)) }; var key by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }; var good by remember { mutableStateOf(true) }; var day by remember { mutableStateOf(0L) }; var tick by remember { mutableStateOf(0) }
    val me = Lic.deviceId(c); val ids = remember(tick) { (sp.getStringSet("ids", emptySet()) ?: emptySet()).toList().sorted() }
    fun left(dev: String): Long { val i = sp.getLong("i_$dev", -1L); return if (i < 0) 0 else maxOf(0L, i + Lic.DAYS - Lic.today()) }
    fun gen() {
        val dev = id.trim().uppercase()
        if (CtlState.revoked(dev)) { key = ""; good = false; msg = "This device was revoked by the owner."; return }
        if (!Regex("[A-F0-9]{12}").matches(dev)) { key = ""; good = false; msg = "Device ID must be 12 letters/numbers (0-9, A-F). Copy it from NACHT X."; return }
        val issued = sp.getLong("i_$dev", -1L); val t = Lic.today(); val d = if (issued >= 0 && issued + Lic.DAYS > t) issued else t
        if (d == t) sp.edit().putLong("i_$dev", t).putStringSet("ids", (sp.getStringSet("ids", emptySet()) ?: emptySet()) + dev).apply()
        key = Lic.make(dev, d); day = d; good = true; tick++
        msg = if (d == t) "New key created. It works for 7 days." else "This ID already has an active key. A new one unlocks in ${d + Lic.DAYS - t} day(s)."
        toast(if (d == t) "Key generated" else "Active key shown")
    }
    fun send(k: String) { try { c.startActivity(Intent().setClassName("com.unoxidized.x", "com.unoxidized.x.MainActivity").putExtra("ux_key", k).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } catch (e: Exception) { toast("Install NACHT X first") } }
    fun share(k: String) { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "My NACHT X key: $k"), "Share key").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoImg(painterResource(R.drawable.logo), 42); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("NACHT X GENERATOR", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp); Text("WEEKLY ACCESS KEYS", color = Dim, fontSize = 9.sp, letterSpacing = 2.sp) }
            val l = left(me); Chip(if (l > 0) "ACTIVE" else "NO KEY", if (l > 0) Ok else Dim)
        }
        Box(Modifier.weight(1f).swipeTabs({ if (tab > 0) { tab-- } }, { if (tab < 2) { tab++ } })) {
            PageSwitch(tab) { t -> PullRefresh({ tick++; delay(400); toast("Updated") }) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp)) {
                when (t) {
                    0 -> {
                        val l = left(me)
                        AnnounceCard(); UpdateCard("6.0"); LinkedMain(c)
                        Glass { Row(verticalAlignment = Alignment.CenterVertically) { Gauge(l / 7f, l.toInt(), "DAYS LEFT", if (l > 2) Ok else if (l > 0) Warn else Dim, 140); Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Cap("This phone"); Text(me, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(if (l > 0) "Your key is active." else "No active key. Generate one below.", color = Dim, fontSize = 12.sp) } } }
                        Glass { Cap("Device ID")
                            OutlinedTextField(id, { id = it.uppercase().filter { ch -> !ch.isWhitespace() } }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(), singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("PASTE") { id = (cb.getText()?.text ?: "").uppercase().filter { ch -> !ch.isWhitespace() } }; MiniBtn("THIS PHONE") { id = me } }
                            Text("This phone's ID is filled in automatically. Paste another ID to make a key for a different device.", color = Dim, fontSize = 12.sp)
                            Btn("GENERATE KEY") { gen() } }
                        if (msg.isNotEmpty()) Text(msg, color = if (good) Ok else Bad, fontSize = 13.sp)
                        if (key.isNotEmpty()) Glass { Cap("Your key"); SelectionContainer { Text(key, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                            Text("Valid until ${Lic.until(day)}", color = Dim, fontSize = 12.sp)
                            Btn("COPY KEY") { cb.setText(AnnotatedString(key)); toast("Key copied") }; Btn("SEND TO NACHT X", false) { send(key) }; Btn("SHARE", false) { share(key) } }
                    }
                    1 -> { H("My keys", "Every device ID you made a key for.")
                        if (ids.isEmpty()) Glass { Text("Nothing here yet", fontWeight = FontWeight.Bold); Text("Generate your first key on the Generate tab.", color = Dim, fontSize = 12.sp); Btn("GO TO GENERATE") { tab = 0 } }
                        ids.forEach { d -> val iss = sp.getLong("i_$d", 0L); val lf = left(d); val k = Lic.make(d, iss)
                            Glass { Row(verticalAlignment = Alignment.CenterVertically) { Text(d, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Chip(if (lf > 0) "$lf DAYS LEFT" else "EXPIRED", if (lf > 0) Ok else Bad) }
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF222222))) { Box(Modifier.fillMaxWidth(lf / 7f).fillMaxHeight().background(WhiteGrad)) }
                                Text(if (lf > 0) "Valid until ${Lic.until(iss)}" else "Expired. Generate a new key for this ID.", color = Dim, fontSize = 12.sp)
                                if (lf > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("COPY") { cb.setText(AnnotatedString(k)); toast("Key copied") }; MiniBtn("SEND") { send(k) }; MiniBtn("SHARE") { share(k) } }
                                else Btn("MAKE NEW KEY", false) { id = d; tab = 0; gen() } } } }
                    else -> { var rm by remember { mutableStateOf(UiFlags.reduceMotion) }
                        H("Help", "How the weekly keys work.")
                        Glass { listOf("1" to "Open NACHT X and copy the Device ID on its login screen.", "2" to "Paste it here (or use This phone) and tap Generate key.", "3" to "Tap Send to NACHT X. It logs you in automatically.", "4" to "After 7 days the key expires. Come back and generate a new one.").forEach { (n, tx) ->
                            Row(verticalAlignment = Alignment.Top) { Text(n, Modifier.width(26.dp), fontWeight = FontWeight.Black, color = Dim); Text(tx, color = Color(0xFFCCCCCC), fontSize = 13.sp) } } }
                        Glass { Cap("Settings"); Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Reduce motion", fontWeight = FontWeight.Bold); Text("Calmer background, shorter transitions", color = Dim, fontSize = 12.sp) }
                            Switch(rm, { rm = it; UiFlags.reduceMotion = it; sp.edit().putBoolean("rm", it).apply() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Glow, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF222222))) } }
                        ShowcaseCard("6.0")
                        Glass { Cap("About"); Text("NACHT X Generator", fontWeight = FontWeight.Bold); Text("Made by @UnoxidizedX on Telegram. One key per device ID per week.", color = Dim, fontSize = 12.sp) } }
                }
                Spacer(Modifier.height(8.dp)) } } }
        }
        Box(Modifier.fillMaxWidth(), Alignment.Center) { Watermark() }
        NavBar(listOf("🔑" to "GENERATE", "🗂️" to "MY KEYS", "💡" to "HELP"), tab) { tab = it }
    }
}
