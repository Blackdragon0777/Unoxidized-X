package com.unoxidized.owner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun field(t: String, k: String): String = Regex("(?im)^\\s*" + k + "\\s*[:=]\\s*(.+)\$").find(t)?.groupValues?.get(1)?.trim() ?: ""

/**
 * Approve or reject an access / payment request. Approving mints an owner-signed key bound to the user's device ID and publishes it in the control file;
 * the user's app picks it up on "Refresh status". Payment is checked by the owner in GCash first: nothing here is automatic.
 */
@Composable fun RequestsPage(os: OS) {
    val e = os.ce; val sp = os.sp; val cb = LocalClipboardManager.current; val toast = LocalToast.current; val scope = rememberCoroutineScope()
    var paste by remember { mutableStateOf("") }; var dev by remember { mutableStateOf("") }; var ref by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Premium") }; var dur by remember { mutableStateOf("30 days") }; var custom by remember { mutableStateOf("") }
    val ready = e.status == "Loaded" || e.status == "Published"
    H("Requests and payments", "Check the payment in your GCash first, then approve or reject. Nothing is automatic.")
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Server link"); Spacer(Modifier.weight(1f)); Chip(if (ready) "READY" else "LOAD FIRST", if (ready) Ok else Warn) }
        Text("Load the current control file before approving so nothing already published is overwritten.", color = Dim, fontSize = 12.sp)
        Btn("LOAD LATEST FROM SERVER", !ready, !e.busy, e.busy) { scope.launch { e.busy = true; val d = withContext(Dispatchers.IO) { Control.fetch(sp) }; if (d != null && CtlState.online) { e.load(d); e.status = "Loaded" } else e.status = "Error: no file yet"; e.busy = false } }
    }
    Glass {
        Cap("Paste the user's message")
        OutlinedTextField(paste, { paste = it }, Modifier.fillMaxWidth(), placeholder = { Text("NACHT X PREMIUM REQUEST ...") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniBtn("PASTE") { paste = cb.getText()?.text ?: "" }
            MiniBtn("READ MESSAGE") {
                dev = field(paste, "DEVICE").uppercase().filter { c -> c.isLetterOrDigit() }.take(12); ref = field(paste, "GCASH REF").uppercase().filter { c -> c.isLetterOrDigit() }
                val dm = Regex("(\\d+)\\s*day").find(field(paste, "PLAN")); custom = dm?.groupValues?.get(1) ?: ""
                if (field(paste, "PLAN").contains("Lifetime", true)) { dur = "Lifetime"; custom = "" }
                if (paste.contains("PREMIUM REQUEST")) role = "Premium" else if (paste.contains("ACCESS REQUEST")) role = "Free"
                toast(if (dev.length == 12) "Message read" else "No device ID found")
            }
        }
    }
    Glass {
        Cap("Decision")
        OutlinedTextField(dev, { dev = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, Modifier.fillMaxWidth(), label = { Text("Device ID") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), textStyle = TextStyle(fontFamily = FontFamily.Monospace))
        OutlinedTextField(ref, { ref = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(30) }, Modifier.fillMaxWidth(), label = { Text("GCash reference (blank for free registration)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(), textStyle = TextStyle(fontFamily = FontFamily.Monospace))
        Cap("Access"); Pick(listOf("Free", "Premium", "Premium+"), role) { role = it }
        Cap("Duration"); Pick(listOf("7 days", "30 days", "90 days", "Lifetime"), dur) { dur = it; custom = "" }
        OutlinedTextField(custom, { custom = it.filter { c -> c.isDigit() }.take(4) }, Modifier.fillMaxWidth(), label = { Text("Custom days (optional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Btn("APPROVE AND PUBLISH", true, ready && !e.busy, e.busy) {
            scope.launch {
                if (!Regex("[A-F0-9]{12}").matches(dev)) { toast("Device ID must be 12 characters (0-9, A-F)"); return@launch }
                if (ref.isNotBlank() && e.refs.containsKey(ref)) { toast("That payment reference was already used"); return@launch }
                val r = when (role) { "Premium" -> "M"; "Premium+" -> "A"; else -> "F" }
                val days = custom.toLongOrNull()?.takeIf { it in 1..3650 } ?: when (dur) { "7 days" -> 7L; "30 days" -> 30L; "90 days" -> 90L; else -> 0L }
                val key = Mint.key(dev, days, r)
                val prevG = e.grants[dev]; val prevR = e.rejected[dev]
                e.grants[dev] = key; e.rejected.remove(dev); if (ref.isNotBlank()) e.refs[ref] = dev
                e.busy = true; val err = withContext(Dispatchers.IO) { GH.publish(sp, e.json()) }; e.busy = false
                if (err == null) {
                    Vault.add(sp, VKey(System.currentTimeMillis(), dev, r, System.currentTimeMillis(), days, key, if (ref.isBlank()) "Registration" else "GCash $ref"))
                    OLog.add(sp, "Approved $role for $dev" + (if (ref.isBlank()) "" else " (ref $ref)")); e.status = "Published"; os.tick++; toast("Approved and published")
                } else {
                    if (prevG == null) e.grants.remove(dev) else e.grants[dev] = prevG
                    if (prevR != null) e.rejected[dev] = prevR; if (ref.isNotBlank()) e.refs.remove(ref); toast(err.take(70))
                }
            }
        }
        Btn("REJECT AND PUBLISH", false, ready && !e.busy) {
            scope.launch {
                if (!Regex("[A-F0-9]{12}").matches(dev)) { toast("Device ID must be 12 characters (0-9, A-F)"); return@launch }
                val prev = e.rejected[dev]; e.rejected[dev] = ref.ifBlank { "*" }
                e.busy = true; val err = withContext(Dispatchers.IO) { GH.publish(sp, e.json()) }; e.busy = false
                if (err == null) { OLog.add(sp, "Rejected request from $dev"); e.status = "Published"; os.tick++; toast("Rejected and published") } else { if (prev == null) e.rejected.remove(dev) else e.rejected[dev] = prev; toast(err.take(70)) }
            }
        }
        Text("Approving signs a key for that device only. The user's app verifies the signature on the phone. Reusing a payment reference is blocked.", color = Dim, fontSize = 11.sp)
    }
    Glass {
        Cap("Premium plans and GCash")
        OutlinedTextField(e.premPlans, { e.premPlans = it }, Modifier.fillMaxWidth(), label = { Text("Plans, one per line: Name|days|Price (0 days = lifetime)") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 5)
        OutlinedTextField(e.gcash, { e.gcash = it.filter { c -> c.isDigit() }.take(13) }, Modifier.fillMaxWidth(), label = { Text("GCash number shown to buyers") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        Btn("PUBLISH PLANS", false, ready && !e.busy) { scope.launch { e.busy = true; val err = withContext(Dispatchers.IO) { GH.publish(sp, e.json()) }; e.busy = false; if (err == null) { e.status = "Published"; toast("Plans published") } else toast(err.take(70)) } }
    }
    Glass {
        Cap("Approved devices (${e.grants.size})")
        if (e.grants.isEmpty()) Text("None yet.", color = Dim, fontSize = 12.sp)
        e.grants.toMap().forEach { (d, k) ->
            val i = Lic.inspect(k)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(d, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text((if (i == null) "?" else Lic.roleName(i.role)) + " · until " + (i?.untilText ?: "?"), color = Dim, fontSize = 11.sp) }
                MiniBtn("REMOVE") { e.grants.remove(d); toast("Removed. Press PUBLISH below to apply.") }
            }
        }
        Cap("Rejected (${e.rejected.size})")
        e.rejected.toMap().forEach { (d, r) -> Row(verticalAlignment = Alignment.CenterVertically) { Text("$d  ·  $r", Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 12.sp); MiniBtn("CLEAR") { e.rejected.remove(d) } } }
        Btn("PUBLISH LIST CHANGES", false, ready && !e.busy) { scope.launch { e.busy = true; val err = withContext(Dispatchers.IO) { GH.publish(sp, e.json()) }; e.busy = false; if (err == null) { e.status = "Published"; toast("Published") } else toast(err.take(70)) } }
        Text("Removing an entry stops the user from re-activating from the file. To block a device completely use Server Control > Revoked devices.", color = Dim, fontSize = 11.sp)
    }
}
