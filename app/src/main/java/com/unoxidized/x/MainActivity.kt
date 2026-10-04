package com.unoxidized.x
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme(darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black, surface = Color(0xFF121212), onSurface = Color.White, onBackground = Color.White, secondaryContainer = Color(0xFF2A2A2A), onSecondaryContainer = Color.White)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App(this) }
            }
        }
    }
}

@Composable fun App(c: Context) {
    val sp = remember { c.getSharedPreferences("ux", 0) }
    var key by remember { mutableStateOf(sp.getString("k", "") ?: "") }
    var ok by remember { mutableStateOf(License.check(c, key) == null) }
    if (!ok) Login(c) { k -> sp.edit().putString("k", k).apply(); key = k; ok = true }
    else Main(c, key) { sp.edit().remove("k").apply(); ok = false }
}

@Composable fun Login(c: Context, done: (String) -> Unit) {
    var t by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(), Arrangement.Center) {
        Text("UNOXIDIZED X", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("Enter your license key", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Text("Your device ID (send this to get a key):", color = Color.Gray, fontSize = 13.sp)
        SelectionContainer { Text(License.deviceId(c), fontFamily = FontFamily.Monospace, fontSize = 22.sp) }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text("License key") })
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF5555))
        Spacer(Modifier.height(12.dp))
        Button({ val e = License.check(c, t); if (e == null) done(t.trim()) else err = e }, Modifier.fillMaxWidth()) { Text("LOG IN") }
    }
}

@Composable fun Chips(opts: List<String>, sel: String, set: (String) -> Unit) = Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
    opts.forEach { o -> FilterChip(o == sel, { set(o) }, { Text(o) }, Modifier.weight(1f)) }
}

@Composable fun Main(c: Context, key: String, logout: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    val pages = listOf("Dashboard", "Resolution", "Gaming", "Display", "Step-by-step guide", "Account")
    var page by remember { mutableStateOf(0) }
    var pkg by remember { mutableStateOf("com.activision.callofduty.shooter") }
    var scale by remember { mutableStateOf(0.8f) }; var fps by remember { mutableStateOf("60") }
    var mode by remember { mutableStateOf("Performance") }; var hz by remember { mutableStateOf("60") }
    var log by remember { mutableStateOf("Log output appears here.") }
    var shiz by remember { mutableStateOf(Sh.ready()) }
    fun run(cmd: String) { log = "Running..."; thread { val r = Sh.run(cmd); log = "$ $cmd\n$r"; shiz = Sh.ready() } }
    val modeId = mapOf("Performance" to 2, "Standard" to 1, "Battery" to 3)
    fun game() = "cmd game set --mode ${modeId[mode]} --downscale ${"%.2f".format(scale)} --fps $fps $pkg"
    ModalNavigationDrawer({ ModalDrawerSheet(drawerContainerColor = Color(0xFF0A0A0A)) {
        Text("UNOXIDIZED X", Modifier.padding(24.dp), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        pages.forEachIndexed { i, p -> NavigationDrawerItem({ Text(p) }, i == page, { page = i; scope.launch { drawer.close() } }, Modifier.padding(horizontal = 12.dp)) }
    } }, drawerState = drawer) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton({ scope.launch { drawer.open() } }) { Text("☰ MENU", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f)); Text(pages[page].uppercase(), color = Color.Gray)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp)) {
                when (page) {
                    0 -> { Text(if (shiz) "● Shizuku connected" else "○ Shizuku not connected", color = if (shiz) Color(0xFF4CAF50) else Color(0xFFFF5555))
                        if (!shiz) Button({ Sh.ask(); shiz = Sh.ready() }) { Text("GRANT SHIZUKU ACCESS") }
                        OutlinedTextField(pkg, { pkg = it }, Modifier.fillMaxWidth(), label = { Text("Game package") })
                        Text("Presets", color = Color.Gray)
                        Button({ scale = .6f; fps = "60"; mode = "Performance"; run(game()) }, Modifier.fillMaxWidth()) { Text("LOW GRAPHICS") }
                        Button({ scale = .8f; fps = "90"; mode = "Performance"; run(game()) }, Modifier.fillMaxWidth()) { Text("SMOOTH GAMEPLAY") }
                        Button({ scale = .9f; fps = "60"; mode = "Standard"; run(game()) }, Modifier.fillMaxWidth()) { Text("BALANCED") }
                        OutlinedButton({ run("cmd game reset $pkg") }, Modifier.fillMaxWidth()) { Text("DEFAULT (RESET)") } }
                    1 -> { Text("Render scale: ${"%.2f".format(scale)}x", fontSize = 20.sp)
                        Slider(scale, { scale = it }, valueRange = 0.5f..1f, steps = 9)
                        Button({ run(game()) }, Modifier.fillMaxWidth()) { Text("APPLY RESOLUTION") }
                        OutlinedButton({ run("cmd game reset $pkg") }, Modifier.fillMaxWidth()) { Text("RESET") } }
                    2 -> { Text("FPS cap"); Chips(listOf("60", "90", "120", "144"), fps) { fps = it }
                        Text("Performance mode"); Chips(listOf("Performance", "Standard", "Battery"), mode) { mode = it }
                        Button({ run(game()) }, Modifier.fillMaxWidth()) { Text("APPLY GAMING SETTINGS") } }
                    3 -> { Text("Refresh rate (Hz)"); Chips(listOf("60", "90", "120", "144"), hz) { hz = it }
                        Button({ run("settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz") }, Modifier.fillMaxWidth()) { Text("APPLY REFRESH RATE") }
                        OutlinedButton({ run("settings delete system min_refresh_rate; settings delete system peak_refresh_rate") }, Modifier.fillMaxWidth()) { Text("RESET") }
                        Text("Only works if your screen supports it.", color = Color.Gray, fontSize = 12.sp) }
                    4 -> Text("1. Install Shizuku from Play Store.\n2. Settings > About phone: tap Build number 7 times.\n3. Settings > Developer options: turn on Wireless debugging.\n4. Open Shizuku > 'Pairing' > pair, then 'Start via Wireless debugging'.\n5. Open Unoxidized X, enter your key, tap Dashboard > GRANT SHIZUKU ACCESS > Allow.\n6. Pick a preset or set Resolution / Gaming / Display values, tap Apply.\n7. Open Call of Duty Mobile. In CODM set Frame Rate to the highest available.\n8. To undo: Dashboard > DEFAULT (RESET).\n\nShizuku must be restarted after every phone reboot.", lineHeight = 24.sp)
                    5 -> { Text("Licensed to: ${License.info(key)}"); Text("Device ID: ${License.deviceId(c)}", fontFamily = FontFamily.Monospace)
                        OutlinedButton(logout) { Text("LOG OUT") } }
                }
            }
            Text(log, Modifier.fillMaxWidth().background(Color(0xFF121212), RoundedCornerShape(10.dp)).padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.LightGray)
        }
    }
}
