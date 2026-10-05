package com.unoxidized.owner
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the GitHub token with a key that never leaves the phone's secure hardware/keystore. */
object Secret {
    private fun key(): SecretKey { val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; (ks.getKey("own_tok", null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder("own_tok", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return g.generateKey() }
    fun enc(s: String): String { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key()); return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP) }
    fun dec(s: String): String = try { val b = Base64.decode(s, Base64.NO_WRAP); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)); String(c.doFinal(b, 12, b.size - 12)) } catch (e: Exception) { "" }
}

object GH {
    private fun call(url: String, token: String, method: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection; c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Authorization", "Bearer $token"); c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "UnoxidizedOwner")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode; val txt = try { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
        return code to txt
    }
    /** Creates or updates control.json in the public repo. Returns null on success, otherwise a readable error. */
    fun publish(sp: SharedPreferences, json: String): String? = try {
        val tok = Secret.dec(sp.getString("tok", "") ?: ""); if (tok.isBlank()) "No GitHub token saved" else {
            val api = "https://api.github.com/repos/${Control.OWNER}/${Control.REPO}/contents/${Control.PATH}"
            val (gc, gt) = call("$api?ref=${Control.BRANCH}", tok, "GET", null); val sha = if (gc == 200) JSONObject(gt).optString("sha") else null
            if (gc == 401) "GitHub rejected the token" else if (gc == 404 && !gt.contains("Not Found")) "Repo not found" else {
                val body = JSONObject().put("message", "Update control file").put("content", Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)).put("branch", Control.BRANCH).apply { if (sha != null) put("sha", sha) }
                val (pc, pt) = call(api, tok, "PUT", body.toString())
                if (pc == 200 || pc == 201) null else if (pc == 404) "Repo ${Control.REPO} not found. Create it as a public repo first." else "GitHub said $pc: " + (try { JSONObject(pt).optString("message") } catch (e: Exception) { pt.take(80) })
            } }
    } catch (e: Exception) { "Network error: ${e.message}" }
}

class CE {
    var maint by mutableStateOf(false); var maintMsg by mutableStateOf(""); var announce by mutableStateOf(""); var announceId by mutableStateOf(0); private var loadedAnnounce = ""
    var latest by mutableStateOf("6.0"); var update by mutableStateOf(Control.MEDIAFIRE); var notes by mutableStateOf(""); val revoked = mutableStateListOf<String>()
    var prices by mutableStateOf(""); var payments by mutableStateOf(""); var contact by mutableStateOf("@UnoxidizedX"); var status by mutableStateOf("Not loaded"); var busy by mutableStateOf(false)
    fun load(d: Control.Data) { maint = d.maintenance; maintMsg = d.maintMsg; announce = d.announce; loadedAnnounce = d.announce; announceId = d.announceId; latest = d.latest.ifBlank { "6.0" }; update = d.updateUrl; notes = d.notes
        revoked.clear(); revoked.addAll(d.revoked); prices = d.prices.joinToString("\n"); payments = d.payments.joinToString("\n"); if (d.contact.isNotBlank()) contact = d.contact }
    private fun lines(s: String) = JSONArray(s.lines().map { it.trim() }.filter { it.isNotEmpty() })
    fun json(): String { val id = if (announce != loadedAnnounce && announce.isNotBlank()) announceId + 1 else announceId
        return JSONObject().put("maintenance", maint).put("maintenanceMsg", maintMsg).put("announce", announce).put("announceId", id).put("latest", latest).put("update", update).put("notes", notes)
            .put("revoked", JSONArray(revoked.toList())).put("showcase", JSONObject().put("prices", lines(prices)).put("payments", lines(payments)).put("contact", contact)).toString(2) }
}

@Composable fun ControlPage(os: OS) {
    val e = os.ce; val sp = os.sp; val scope = rememberCoroutineScope(); val toast = LocalToast.current
    var tok by remember { mutableStateOf("") }; var newDev by remember { mutableStateOf("") }; val saved = remember(os.tick) { (sp.getString("tok", "") ?: "").isNotEmpty() }
    H("Server control", "One control file every app reads. No server to run.")
    Glass { Row(verticalAlignment = Alignment.CenterVertically) { Cap("Connection"); Spacer(Modifier.weight(1f)); Chip(e.status.uppercase().take(18), if (e.status == "Loaded" || e.status == "Published") Ok else if (e.status.startsWith("Error")) Bad else Dim) }
        Text("${Control.OWNER}/${Control.REPO} · ${Control.BRANCH} · ${Control.PATH}", color = Dim, fontSize = 11.sp)
        Text(if (saved) "GitHub token saved and encrypted on this phone." else "Add a GitHub token (repo scope) to publish changes. Create the public repo ${Control.REPO} first.", color = Dim, fontSize = 12.sp)
        OutlinedTextField(tok, { tok = it }, Modifier.fillMaxWidth(), label = { Text("GitHub token") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), visualTransformation = PasswordVisualTransformation())
        Btn("SAVE TOKEN", false, tok.isNotBlank()) { sp.edit().putString("tok", Secret.enc(tok.trim())).apply(); tok = ""; os.tick++; toast("Token saved") }
        Btn("LOAD FROM SERVER", true, !e.busy, e.busy) { scope.launch { e.busy = true; val d = withContext(Dispatchers.IO) { Control.fetch(sp) }; if (d != null && CtlState.online) { e.load(d); e.status = "Loaded" } else e.status = "Error: no file yet"; e.busy = false } } }
    Glass { Cap("Maintenance"); Toggle("Maintenance mode", "Blocks NACHT X and the Generator with your message", e.maint) { e.maint = it }
        OutlinedTextField(e.maintMsg, { e.maintMsg = it }, Modifier.fillMaxWidth(), label = { Text("Message shown to users") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 3) }
    Glass { Cap("Announcement"); OutlinedTextField(e.announce, { e.announce = it }, Modifier.fillMaxWidth(), label = { Text("Shows on every app's home") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 4)
        Text("A changed announcement shows again for everyone, even if they dismissed the old one.", color = Dim, fontSize = 11.sp) }
    Glass { Cap("Release"); OutlinedTextField(e.latest, { e.latest = it.take(10) }, Modifier.fillMaxWidth(), label = { Text("Latest version, e.g. 6.1") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(e.update, { e.update = it }, Modifier.fillMaxWidth(), label = { Text("Download link") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(e.notes, { e.notes = it }, Modifier.fillMaxWidth(), label = { Text("What's new") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 3) }
    Glass { Cap("Revoked devices"); if (e.revoked.isEmpty()) Text("None. Revoke from the Key Vault or add an ID here.", color = Dim, fontSize = 12.sp)
        e.revoked.toList().forEach { d -> Row(verticalAlignment = Alignment.CenterVertically) { Text(d, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); MiniBtn("REMOVE") { e.revoked.remove(d) } } }
        OutlinedTextField(newDev, { newDev = it.uppercase().filter { c -> !c.isWhitespace() }.take(12) }, Modifier.fillMaxWidth(), label = { Text("Device ID to revoke") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), textStyle = TextStyle(fontFamily = FontFamily.Monospace))
        Btn("ADD TO REVOKE LIST", false, Regex("[A-F0-9]{12}").matches(newDev)) { if (!e.revoked.contains(newDev)) e.revoked.add(newDev); newDev = ""; toast("Added. Publish to apply.") }
        Text("Revoked devices are blocked in NACHT X and the Generator the next time they check in. This is enforced inside the apps, so a modified APK could ignore it.", color = Dim, fontSize = 11.sp) }
    Glass { Cap("Showcase and pricelist"); OutlinedTextField(e.prices, { e.prices = it }, Modifier.fillMaxWidth(), label = { Text("Pricelist, one line each") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 6)
        OutlinedTextField(e.payments, { e.payments = it }, Modifier.fillMaxWidth(), label = { Text("Payment methods, one per line") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 5)
        OutlinedTextField(e.contact, { e.contact = it }, Modifier.fillMaxWidth(), label = { Text("Buy key contact") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors()) }
    Btn("PUBLISH TO ALL APPS", true, saved && !e.busy, e.busy) { scope.launch { e.busy = true; val err = withContext(Dispatchers.IO) { GH.publish(sp, e.json()) }
        e.status = if (err == null) "Published" else "Error: $err"; if (err == null) { OLog.add(sp, "Published control file"); toast("Published. Apps update within minutes.") } else toast(err.take(60)); e.busy = false; os.tick++ } }
    Text("GitHub's raw files can take a few minutes to refresh.", color = Dim, fontSize = 11.sp)
}
