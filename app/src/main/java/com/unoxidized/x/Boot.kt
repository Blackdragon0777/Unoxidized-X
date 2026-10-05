package com.unoxidized.x
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun Boot(c: Context, incoming: String?, consume: () -> Unit) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope(); val sp = remember { c.getSharedPreferences("ux", 0) }; val dev = remember { Lic.deviceId(c) }
    LaunchedEffect(Unit) {
        status = "Loading profiles"; withContext(Dispatchers.IO) { ProfStore.load(sp) }; prog = .2f; delay(300)
        status = "Checking license"; val k = sp.getString("k", "") ?: ""; if (k.isNotEmpty()) Lic.check(dev, k); prog = .4f; delay(300)
        status = "Checking Shizuku"; val z = withContext(Dispatchers.IO) { Sh.running() }; prog = .6f; delay(300)
        status = "Fetching announcements"; CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) }; prog = .9f; delay(350)
        status = if (z) "Shizuku connected" else "Shizuku not running"; prog = 1f; delay(450); ready = true
    }
    LaunchedEffect(ready) { if (ready) while (true) { delay(600000); CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) } } }
    SplashSwitch(ready, { CoolSplash(painterResource(R.drawable.logo), "NACHT X", "SHADOW SYSTEM", 0, status, prog) }) {
        val d = CtlState.data
        if (CtlState.revoked(dev)) Blocked(painterResource(R.drawable.logo), "ACCESS REVOKED", "This device was removed by the owner. Contact @UnoxidizedX on Telegram.", null)
        else if (d?.maintenance == true) Blocked(painterResource(R.drawable.logo), "MAINTENANCE", d.maintMsg.ifBlank { "Back soon." }, { scope.launch { CtlState.data = withContext(Dispatchers.IO) { Control.fetch(sp) } } })
        else App(c, incoming, consume)
    }
}
