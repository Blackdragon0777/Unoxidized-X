package com.unoxidized.gen
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun GenBoot(c: Context) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope(); val sp = remember { c.getSharedPreferences("gen", 0) }; val me = remember { Lic.deviceId(c) }
    LaunchedEffect(Unit) {
        status = "Loading key vault"; val n = (sp.getStringSet("ids", emptySet()) ?: emptySet()).size; prog = .25f; delay(350)
        status = "Checking NACHT X"; val x = try { c.packageManager.getPackageInfo("com.unoxidized.x", 0); true } catch (e: Exception) { false }; prog = .5f; delay(300)
        status = "Fetching announcements"; CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; prog = .85f; delay(350)
        status = (if (x) "NACHT X found" else "NACHT X not installed") + " · $n device(s)"; prog = 1f; delay(450); ready = true
    }
    SplashSwitch(ready, { CoolSplash(painterResource(R.drawable.logo), "NACHT X", "SHADOW KEY GENERATOR", 1, status, prog) { KeyGlyph(30) } }) {
        val d = CtlState.data
        if (CtlState.revoked(me)) Blocked(painterResource(R.drawable.logo), "ACCESS REVOKED", "This device was removed by the owner. Contact @UnoxidizedX on Telegram.", null)
        else if (d?.maintenance == true) Blocked(painterResource(R.drawable.logo), "MAINTENANCE", d.maintMsg.ifBlank { "Back soon." }, { scope.launch { CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) } } })
        else GenApp(c)
    }
}

class GenSync : SyncProvider() {
    override fun data(c: Context): Bundle {
        val sp = c.getSharedPreferences("gen", 0); val ids = (sp.getStringSet("ids", emptySet()) ?: emptySet()).toList().sorted(); val t = Lic.today()
        val lefts = ids.map { maxOf(0L, sp.getLong("i_$it", 0L) + Lic.DAYS - t) }
        return Bundle().apply {
            putString("ver", try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" })
            putInt("total", ids.size); putInt("active", lefts.count { it > 0 }); putInt("expired", lefts.count { it <= 0 })
            putStringArrayList("ids", ArrayList(ids.mapIndexed { i, d -> d + ":" + lefts[i] }))
        }
    }
}

@Composable fun LinkedMain(c: Context) {
    var b by remember { mutableStateOf<Bundle?>(null) }
    LaunchedEffect(Unit) { while (true) { b = withContext(Dispatchers.IO) { SyncClient.pull(c, "com.unoxidized.x.sync") }; delay(5000) } }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Linked app · NACHT X"); Spacer(Modifier.weight(1f)); Chip(if (b != null) "LINKED" else "NOT LINKED", if (b != null) Ok else Dim) }
        val x = b
        if (x == null) Text("Install NACHT X on this phone to see your key status here.", color = Dim, fontSize = 12.sp)
        else { StatRow("KEY IN NACHT X" to (if (x.getBoolean("lic")) "Valid" else "Needs a key"), "DAYS LEFT" to (if (!x.getBoolean("lic")) "n/a" else if (x.getLong("left") > 9000) "Lifetime" else x.getLong("left").toString()))
            StatRow("SESSIONS" to x.getInt("sessions").toString(), "SHIZUKU" to (if (x.getBoolean("shizuku")) "Authorized" else "Not connected"))
            if (!x.getBoolean("lic")) Text("Generate a key below and tap Send to NACHT X.", color = Warn, fontSize = 12.sp) }
    }
}
