package com.unoxidized.owner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun planDays(plan: String): Long = if (plan.contains("Lifetime", true)) 0L else (Regex("(\\d+)\\s*day").find(plan)?.groupValues?.get(1)?.toLongOrNull() ?: 30L)
private fun durDays(d: String): Long = when (d) { "7 days" -> 7L; "30 days" -> 30L; "90 days" -> 90L; else -> 0L }
private fun roleCode(r: String): String = when (r) { "Premium" -> "M"; "Premium+" -> "A"; else -> "F" }

/**
 * Live accounts and payments on the NACHT X server (Firebase). The server's security rules only let an account listed under /admins write
 * these records, so hiding this page is not the protection: Google's servers refuse everyone else.
 */
@Composable fun CloudPage(os: OS) {
    val sp = os.sp; val toast = LocalToast.current; val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(sp.getString("ow_mail", "") ?: "") }; var pw by remember { mutableStateOf("") }
    var ses by remember { mutableStateOf<Cloud.Session?>(null) }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }
    var pays by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }; var users by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }
    var role by remember { mutableStateOf("Premium") }; var dur by remember { mutableStateOf("30 days") }

    val reload: suspend () -> Unit = {
        val s = ses
        if (s != null) {
            busy = true
            val p = withContext(Dispatchers.IO) { Cloud.query(s, "payments", "status", "pending", 100) }
            val u = withContext(Dispatchers.IO) { Cloud.query(s, "users", null, null, 100) }
            busy = false
            if (p == null || u == null) msg = "The server refused the request. Make sure this account is listed under admins in Firestore and the rules are published."
            else { pays = p; users = u; msg = "" }
        }
    }
    val signIn: suspend () -> Unit = {
        busy = true
        val pass = if (pw.isNotBlank()) pw else Secret.dec(sp.getString("ow_pw", "") ?: "")
        val r = withContext(Dispatchers.IO) { Cloud.signIn(email.trim(), pass) }
        busy = false
        val s = r.first
        if (s == null) msg = r.second else {
            ses = s; Cloud.session = s; sp.edit().putString("ow_mail", email.trim()).putString("ow_pw", Secret.enc(pass)).apply(); pw = ""; msg = ""; reload()
        }
    }
    LaunchedEffect(Unit) { if ((sp.getString("ow_pw", "") ?: "").isNotEmpty() && email.isNotBlank()) signIn() }
    LaunchedEffect(ses) { while (ses != null) { kotlinx.coroutines.delay(15000); reload() } }

    H("Accounts and payments", "Live from the NACHT X server. Approving signs a key for the user's device and delivers it instantly.")
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Server session"); Spacer(Modifier.weight(1f)); Chip(if (ses != null) "OWNER SIGNED IN" else "SIGNED OUT", if (ses != null) Ok else Dim) }
        if (ses == null) {
            OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Owner email (Firebase account)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
            OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), colors = fieldColors())
            Btn("SIGN IN", true, !busy && email.isNotBlank() && pw.isNotBlank(), busy) { scope.launch { signIn() } }
        } else {
            Text("Signed in as ${ses?.email}. The list refreshes every 15 seconds.", color = Dim, fontSize = 12.sp)
            Btn("REFRESH NOW", false, !busy, busy) { scope.launch { reload() } }
            Btn("SIGN OUT", false) { ses = null; Cloud.session = null; sp.edit().remove("ow_pw").apply(); pays = emptyList(); users = emptyList() }
        }
        if (msg.isNotEmpty()) Text(msg, color = Bad, fontSize = 12.sp)
    }
    if (ses != null) {
        Glass {
            Cap("Approval settings (used by the buttons below)")
            Cap("Access"); Pick(listOf("Free", "Premium", "Premium+"), role) { role = it }
            Cap("Duration for account approvals (payments use the plan's duration)"); Pick(listOf("7 days", "30 days", "90 days", "Lifetime"), dur) { dur = it }
        }
        Cap("Payments waiting for review (${pays.size})")
        if (pays.isEmpty()) Text("No pending payments.", color = Dim, fontSize = 12.sp)
        pays.forEach { (id, f) ->
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(id, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Chip("PENDING", Warn) }
                Text("${f["plan"] ?: ""}  ·  Amount ${f["amount"] ?: ""}  ·  Paid ${f["date"] ?: ""}", fontSize = 12.sp)
                Text("Device ${f["device"] ?: ""}  ·  Sender ${f["who"] ?: ""}", color = Dim, fontSize = 11.sp)
                Text("Check this reference in your GCash history before approving.", color = Dim, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniBtn("APPROVE") {
                        scope.launch {
                            val s = ses ?: return@launch; val uid = f["uid"] ?: ""; val dev = (f["device"] ?: "").uppercase()
                            if (!Regex("[A-F0-9]{12}").matches(dev) || uid.isEmpty()) { toast("This payment has no valid device or account"); return@launch }
                            val code = if (role == "Free") "M" else roleCode(role); val days = planDays(f["plan"] ?: ""); val key = Mint.key(dev, days, code); val now = System.currentTimeMillis()
                            busy = true
                            val r = withContext(Dispatchers.IO) { Cloud.commit(s, listOf(Cloud.op("payments/$id", mapOf("status" to "approved", "reviewedAt" to now), false), Cloud.op("users/$uid", mapOf("key" to key, "role" to code, "status" to "approved"), false), Cloud.op("refs/$id", mapOf("uid" to uid, "at" to now), true))) }
                            busy = false
                            if (r.ok) { Vault.add(sp, VKey(now, dev, code, now, days, key, "GCash $id")); OLog.add(sp, "Approved payment $id for $dev"); os.tick++; toast("Approved. The user gets it instantly."); reload() } else toast(r.msg().take(70))
                        }
                    }
                    MiniBtn("REJECT") {
                        scope.launch {
                            val s = ses ?: return@launch; busy = true
                            val r = withContext(Dispatchers.IO) { Cloud.commit(s, listOf(Cloud.op("payments/$id", mapOf("status" to "rejected", "reviewedAt" to System.currentTimeMillis()), false))) }
                            busy = false; if (r.ok) { OLog.add(sp, "Rejected payment $id"); toast("Rejected"); reload() } else toast(r.msg().take(70))
                        }
                    }
                }
            }
        }
        Cap("Accounts (${users.size})")
        if (users.isEmpty()) Text("No accounts yet.", color = Dim, fontSize = 12.sp)
        users.forEach { (uid, f) ->
            val st = f["status"] ?: "pending"; val rl = f["role"] ?: "F"
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(f["name"] ?: "", Modifier.weight(1f), fontWeight = FontWeight.Bold); Chip(st.uppercase(), if (st == "approved") Ok else if (st == "suspended") Bad else Warn) }
                Text("${f["gmail"] ?: ""}  ·  ${f["telegram"] ?: ""}", fontSize = 12.sp)
                Text("Device ${f["device"] ?: ""}  ·  Access ${Lic.roleName(rl)}", color = Dim, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniBtn("APPROVE") {
                        scope.launch {
                            val s = ses ?: return@launch; val dev = (f["device"] ?: "").uppercase()
                            if (!Regex("[A-F0-9]{12}").matches(dev)) { toast("This account has no valid device ID"); return@launch }
                            val code = roleCode(role); val days = durDays(dur); val key = Mint.key(dev, days, code); val now = System.currentTimeMillis()
                            busy = true; val r = withContext(Dispatchers.IO) { Cloud.commit(s, listOf(Cloud.op("users/$uid", mapOf("key" to key, "role" to code, "status" to "approved"), false))) }; busy = false
                            if (r.ok) { Vault.add(sp, VKey(now, dev, code, now, days, key, "Account " + (f["gmail"] ?: ""))); OLog.add(sp, "Approved $role for ${f["gmail"] ?: dev}"); os.tick++; toast("Approved"); reload() } else toast(r.msg().take(70))
                        }
                    }
                    MiniBtn(if (st == "suspended") "RESTORE" else "SUSPEND") {
                        scope.launch {
                            val s = ses ?: return@launch; val next = if (st == "suspended") "approved" else "suspended"; busy = true
                            val r = withContext(Dispatchers.IO) { Cloud.commit(s, listOf(Cloud.op("users/$uid", mapOf("status" to next), false))) }; busy = false
                            if (r.ok) { OLog.add(sp, "$next account ${f["gmail"] ?: uid}"); toast(if (next == "suspended") "Suspended" else "Restored"); reload() } else toast(r.msg().take(70))
                        }
                    }
                }
            }
        }
    }
}
