package com.unoxidized.x
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun Boot(c: Context, incoming: String?, consume: () -> Unit) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val sp = c.getSharedPreferences("ux", 0)
        status = "Loading profiles"; withContext(Dispatchers.IO) { ProfStore.load(sp) }; prog = .3f; delay(350)
        status = "Checking license"; val k = sp.getString("k", "") ?: ""; if (k.isNotEmpty()) Lic.check(Lic.deviceId(c), k); prog = .6f; delay(350)
        status = "Checking Shizuku"; val z = withContext(Dispatchers.IO) { Sh.running() }; prog = .9f; delay(350)
        status = if (z) "Shizuku connected" else "Shizuku not running"; prog = 1f; delay(450); ready = true
    }
    if (!ready) Splash(painterResource(R.drawable.logo), "UNOXIDIZED X", "CODM PERFORMANCE CENTER", 0, status, prog) else App(c, incoming, consume)
}
