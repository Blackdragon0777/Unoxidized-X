package com.unoxidized.gen
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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

class GenActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black, surface = Color(0xFF0B0B0B), onSurface = Color.White)) { Surface(Modifier.fillMaxSize(), color = Color.Black) { GenApp(this) } } }
    }
}

@Composable fun GenApp(c: Context) {
    val sp = remember { c.getSharedPreferences("gen", 0) }
    var id by remember { mutableStateOf(Lic.deviceId(c)) }; var key by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }; var good by remember { mutableStateOf(true) }; var day by remember { mutableStateOf(0L) }; var tick by remember { mutableStateOf(0) }
    val cb = LocalClipboardManager.current
    fun gen() {
        val dev = id.trim().uppercase()
        if (!Regex("[A-F0-9]{12}").matches(dev)) { key = ""; good = false; msg = "Device ID must be 12 letters/numbers (0-9, A-F). Copy it from Unoxidized X."; return }
        val issued = sp.getLong("i_$dev", -1L); val t = Lic.today()
        val d = if (issued >= 0 && issued + Lic.DAYS > t) issued else t
        if (d == t) sp.edit().putLong("i_$dev", t).putStringSet("ids", (sp.getStringSet("ids", emptySet()) ?: emptySet()) + dev).apply()
        key = Lic.make(dev, d); day = d; good = true; tick++
        msg = if (d == t) "New key created. It works for 7 days." else "You already have an active key for this ID. A new one unlocks in ${d + Lic.DAYS - t} day(s)."
    }
    fun send() { try { c.startActivity(Intent().setClassName("com.unoxidized.x", "com.unoxidized.x.MainActivity").putExtra("ux_key", key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } catch (e: Exception) { good = false; msg = "Install Unoxidized X first." } }
    val ids = remember(tick) { (sp.getStringSet("ids", emptySet()) ?: emptySet()).toList().sorted() }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp)); LogoImg(painterResource(R.drawable.logo), 130)
        Text("UNOXIDIZED GENERATOR", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text("Weekly access keys for Unoxidized X", color = Dim, fontSize = 13.sp)
        Glass {
            Cap("Device ID"); OutlinedTextField(id, { id = it.uppercase().filter { ch -> !ch.isWhitespace() } }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniBtn("PASTE") { id = (cb.getText()?.text ?: "").uppercase().filter { ch -> !ch.isWhitespace() } }; MiniBtn("THIS PHONE") { id = Lic.deviceId(c) } }
            Text("This phone's ID is filled in automatically. Paste another ID to make a key for a different device.", color = Dim, fontSize = 12.sp)
            Btn("GENERATE KEY") { gen() }
        }
        if (msg.isNotEmpty()) Text(msg, color = if (good) Ok else Bad, fontSize = 13.sp)
        if (key.isNotEmpty()) Glass {
            Cap("Your key"); SelectionContainer { Text(key, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Text("Valid until ${Lic.until(day)}", color = Dim, fontSize = 12.sp)
            Btn("COPY KEY") { cb.setText(AnnotatedString(key)) }; Btn("SEND TO UNOXIDIZED X", false) { send() }
        }
        if (ids.isNotEmpty()) Glass {
            Cap("Devices")
            ids.forEach { d -> val i = sp.getLong("i_$d", 0L); val left = i + Lic.DAYS - Lic.today()
                Row(verticalAlignment = Alignment.CenterVertically) { Text(d, Modifier.weight(1f), fontFamily = FontFamily.Monospace); Text(if (left > 0) "$left d left" else "expired", color = if (left > 0) Ok else Bad, fontSize = 12.sp) } }
        }
        Watermark()
    }
}
