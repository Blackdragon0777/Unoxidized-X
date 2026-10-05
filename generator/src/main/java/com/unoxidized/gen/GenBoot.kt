package com.unoxidized.gen
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import com.unoxidized.shared.*
import kotlinx.coroutines.delay

@Composable fun GenBoot(c: Context) {
    var ready by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Starting") }; var prog by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val sp = c.getSharedPreferences("gen", 0)
        status = "Loading key vault"; val n = (sp.getStringSet("ids", emptySet()) ?: emptySet()).size; prog = .35f; delay(400)
        status = "Checking Unoxidized X"; val x = try { c.packageManager.getPackageInfo("com.unoxidized.x", 0); true } catch (e: Exception) { false }; prog = .7f; delay(400)
        status = (if (x) "Unoxidized X found" else "Unoxidized X not installed") + " · $n device(s)"; prog = 1f; delay(450); ready = true
    }
    if (!ready) Splash(painterResource(R.drawable.logo), "UNOXIDIZED", "KEY GENERATOR", 1, status, prog, "🗝️") else GenApp(c)
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
