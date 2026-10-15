package com.unoxidized.x
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Payment and access-request logic.
 * Premium is NEVER granted from anything the user types or uploads. It is granted only when the Owner app
 * publishes an owner-signed key for this device in the control file ("grants"), and the key's signature verifies on the phone.
 * This is manual owner approval, not automatic payment detection.
 */
object Pay {
    class Plan(val name: String, val days: String, val price: String) { fun duration() = if (days == "0") "Lifetime" else "$days days"; fun label() = "$name · ${duration()} · $price" }
    class Req(val ref: String, val plan: String, val amount: String, val date: String, val at: Long, val status: String)
    class Rf(val status: String, val key: String?, val msg: String)

    private fun prem(d: Control.Data?): JSONObject? = try { JSONObject(d?.raw ?: "{}").optJSONObject("premium") } catch (e: Exception) { null }
    fun gcash(d: Control.Data?): String { val v = prem(d)?.optString("gcash", "") ?: ""; return if (v.isBlank()) "09686894877" else v }
    fun plans(d: Control.Data?): List<Plan> {
        val out = ArrayList<Plan>()
        try { val a = prem(d)?.optJSONArray("plans"); if (a != null) for (i in 0 until a.length()) { val p = a.getString(i).split("|").map { it.trim() }; if (p.size >= 3 && p[0].isNotEmpty()) out.add(Plan(p[0], p[1].filter { it.isDigit() }.ifEmpty { "30" }, p[2])) } } catch (e: Exception) { }
        if (out.isEmpty()) out.add(Plan("Premium", "30", "PHP 150"))
        return out
    }
    private fun map(d: Control.Data?, k: String, dev: String): String? = try { val v = JSONObject(d?.raw ?: "{}").optJSONObject(k)?.optString(dev.uppercase(), "") ?: ""; if (v.isBlank()) null else v } catch (e: Exception) { null }

    fun hist(sp: SharedPreferences): List<Req> = try { val a = JSONArray(sp.getString("pay_hist", "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); Req(o.getString("r"), o.getString("p"), o.getString("a"), o.getString("d"), o.getLong("t"), o.getString("s")) } } catch (e: Exception) { emptyList() }
    fun save(sp: SharedPreferences, l: List<Req>) { val a = JSONArray(); l.forEach { a.put(JSONObject().put("r", it.ref).put("p", it.plan).put("a", it.amount).put("d", it.date).put("t", it.at).put("s", it.status)) }; sp.edit().putString("pay_hist", a.toString()).apply() }
    private fun setLatest(sp: SharedPreferences, st: String) { val l = hist(sp); if (l.isNotEmpty()) { val f = l[0]; save(sp, listOf(Req(f.ref, f.plan, f.amount, f.date, f.at, st)) + l.drop(1)) } }

    fun message(dev: String, plan: Plan, ref: String, amount: String, date: String, who: String, gcash: String): String =
        "NACHT X PREMIUM REQUEST\nDEVICE: $dev\nPLAN: ${plan.label()}\nAMOUNT: $amount\nGCASH REF: $ref\nPAID TO: $gcash\nDATE OF PAYMENT: $date\nSENDER: $who\n(Payment screenshot attached in this chat)"

    /** Opens Telegram's share sheet when installed, otherwise the normal Android share sheet. The user picks the owner and sends it. */
    fun sendToOwner(c: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try { c.startActivity(Intent(i).setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { c.startActivity(Intent.createChooser(i, "Send to the owner").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { } }
    }

    /** Server account status ("pending", "approved", "suspended") or "" when unknown. */
    fun accountStatus(c: Context, sp: SharedPreferences): String {
        val s = Cloud.restore(sp) ?: return ""
        val g = Cloud.getDoc(s, "users/${s.uid}"); if (!g.ok) return ""
        return Cloud.decode(g.json())["status"] ?: ""
    }

    /** Server first (instant), then the older GitHub control file as a fallback. Premium only ever comes from an owner-signed key. */
    suspend fun refresh(c: Context, sp: SharedPreferences): Rf {
        val cloud = withContext(Dispatchers.IO) { cloudRefresh(c, sp) }
        if (cloud != null) return cloud
        return legacyRefresh(c, sp)
    }

    private fun cloudRefresh(c: Context, sp: SharedPreferences): Rf? {
        val s = Cloud.restore(sp) ?: return null
        val u = Cloud.getDoc(s, "users/${s.uid}"); if (!u.ok) return null
        val f = Cloud.decode(u.json()); val dev = Lic.deviceId(c)
        val ps = Cloud.query(s, "payments", "uid", s.uid, 50)
        if (ps != null) { val m = ps.associate { it.first to (it.second["status"] ?: "pending") }; save(sp, hist(sp).map { h -> Req(h.ref, h.plan, h.amount, h.date, h.at, m[h.ref] ?: h.status) }) }
        if (f["status"] == "suspended") return Rf("SUSPENDED", null, "This account was suspended by the owner. Contact @UnoxidizedX.")
        val k = f["key"] ?: ""
        if (k.isNotBlank()) {
            val r = Lic.check(dev, k)
            if (!r.ok) return Rf("PENDING", null, r.msg)
            val same = Lic.clean(k) == (sp.getString("k", "") ?: "")
            return Rf(if (same) "ACTIVE" else "APPROVED", Lic.clean(k), (if (same) "Active: " else "Approved: ") + Lic.roleName(r.role) + " until " + r.untilText)
        }
        val last = hist(sp).firstOrNull()
        if (last != null && last.status == "rejected") return Rf("REJECTED", null, "The owner rejected this payment. Message @UnoxidizedX with your proof.")
        return Rf("PENDING", null, "Waiting for the owner to review. This page updates automatically.")
    }

    private suspend fun legacyRefresh(c: Context, sp: SharedPreferences): Rf {
        val d = withContext(Dispatchers.IO) { Control.fetch(sp) }
        if (d == null || !CtlState.online) return Rf("OFFLINE", null, "Could not reach the server. Check your internet and try again.")
        CtlState.data = d
        val dev = Lic.deviceId(c); val last = hist(sp).firstOrNull(); val g = map(d, "grants", dev)
        if (g != null) {
            val r = Lic.check(dev, g)
            val sameAsActive = Lic.clean(g) == (sp.getString("k", "") ?: "") && last != null && last.status == "pending"
            if (r.ok && !sameAsActive) { setLatest(sp, "approved"); return Rf("APPROVED", Lic.clean(g), "Approved: ${Lic.roleName(r.role)} until ${r.untilText}") }
        }
        val rj = map(d, "rejected", dev)
        if (rj != null && (last == null || rj == "*" || rj == last.ref)) { setLatest(sp, "rejected"); return Rf("REJECTED", null, "The owner rejected this request. Message @UnoxidizedX with your proof of payment.") }
        return Rf("PENDING", null, "Still waiting for the owner. Try again shortly.")
    }
}

private const val QR_RATIO = 1000f / 1410f

@Composable fun PremiumPage(a: AS) {
    val c = a.c; val sp = a.sp; val d = CtlState.data; val dev = remember { Lic.deviceId(c) }; val cb = LocalClipboardManager.current; val toast = LocalToast.current; val scope = rememberCoroutineScope()
    val plans = remember(d) { Pay.plans(d) }; var sel by remember { mutableStateOf(0) }; val plan = plans.getOrElse(sel) { plans[0] }; val gcash = Pay.gcash(d)
    var ref by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var pdate by remember { mutableStateOf("") }; var who by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    val hist = remember(tick) { Pay.hist(sp) }
    val cur = Lic.check(dev, sp.getString("k", "") ?: "")
    H("Premium", "Unlock every feature. Payments are checked by the owner, not automatically.")
    BannerImg(painterResource(R.drawable.nachtx_banner), 120, "NACHT X PREMIUM")
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Your membership"); Spacer(Modifier.weight(1f)); Chip(if (a.keyRole != "F") Lic.roleName(a.keyRole).uppercase() else "FREE", if (a.keyRole != "F") Ok else Glow) }
        Text(if (cur.ok) "Expires: ${cur.untilText}" + (if (cur.left > 9000) "" else "  (${cur.left} days left)") else "No active key", color = Dim, fontSize = 12.sp)
    }
    Glass {
        Cap("Premium benefits")
        listOf("Graphics tuner, game profiles and session tracking", "Auto Boost engine", "Import and export", "Every future Premium tool", "Premium badge on your account").forEach { Text("•  $it", fontSize = 13.sp) }
    }
    Glass {
        Cap("Choose a plan")
        Pick(plans.map { it.label() }, plan.label()) { s -> sel = plans.indexOfFirst { it.label() == s }.coerceAtLeast(0) }
        Text("${plan.price}  ·  ${plan.duration()}", fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
    Glass {
        Cap("How to pay")
        Text("1. Scan the GCash QR below or send to the number.\n2. Send exactly ${plan.price}.\n3. Keep the receipt and copy its reference number.\n4. Fill in the proof form below and submit.\n5. The owner checks the payment in GCash and approves it. Your status changes from Pending to Approved.", color = Dim, fontSize = 12.sp)
        Image(painterResource(R.drawable.gcash_qr), "GCash payment QR", Modifier.fillMaxWidth(0.8f).aspectRatio(QR_RATIO).clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Cap("GCash number"); Text(gcash, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp) }; CopyChip(gcash) }
        Text("Account name on the QR: NACHT X. Transfer fees may apply.", color = Dim, fontSize = 11.sp)
    }
    if (!Cloud.signedIn(sp)) Glass { CloudAuthCard(c, sp) { tick++; toast("Signed in") } }
    LaunchedEffect(tick) { if (Cloud.signedIn(sp) && Pay.hist(sp).any { it.status == "pending" }) while (true) { delay(10000); val rf = Pay.refresh(c, sp); tick++; val k = rf.key; if (rf.status == "APPROVED" && k != null) { sp.edit().putString("k", k).apply(); (c as? Activity)?.recreate(); break } } }
    Glass {
        Cap("Proof of payment")
        OutlinedTextField(ref, { ref = it.take(30); err = "" }, Modifier.fillMaxWidth(), label = { Text("GCash reference number") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(amt, { amt = it.take(12); err = "" }, Modifier.fillMaxWidth(), label = { Text("Amount sent") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(pdate, { pdate = it.take(20); err = "" }, Modifier.fillMaxWidth(), label = { Text("Date of payment, e.g. 2026-10-15") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(who, { who = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Your GCash name or number") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
        Btn("SUBMIT PAYMENT FOR REVIEW") {
            val r = ref.trim().uppercase().filter { it.isLetterOrDigit() }
            if (r.length < 8) { err = "Enter the reference number from your GCash receipt (at least 8 characters)."; return@Btn }
            if (amt.isBlank() || pdate.isBlank()) { err = "Enter the amount sent and the date of payment."; return@Btn }
            if (Pay.hist(sp).any { it.ref == r }) { err = "You already submitted this reference number."; return@Btn }
            val msg = Pay.message(dev, plan, r, amt.trim(), pdate.trim(), who.trim(), gcash)
            val now = System.currentTimeMillis(); val label = plan.label(); val a2 = amt.trim(); val d2 = pdate.trim(); val w2 = who.trim()
            scope.launch {
                busy = true
                val res = withContext(Dispatchers.IO) { val s = Cloud.restore(sp); if (s == null) null else Cloud.commit(s, listOf(Cloud.op("payments/$r", mapOf("uid" to s.uid, "device" to dev, "plan" to label, "amount" to a2, "date" to d2, "who" to w2, "status" to "pending", "createdAt" to now), true))) }
                busy = false
                if (res != null && !res.ok && (res.code == 409 || res.code == 403)) { err = if (res.code == 409) "That reference number was already submitted." else res.msg(); return@launch }
                Pay.save(sp, listOf(Pay.Req(r, label, a2, d2, now, "pending")) + Pay.hist(sp))
                tick++; err = ""; cb.setText(AnnotatedString(msg))
                if (res != null && res.ok) toast("Sent to the owner. Status: Pending verification")
                else { Pay.sendToOwner(c, msg); toast("Server unavailable: opened Telegram instead") }
            }
        }
        Text("Send the message in Telegram and attach your payment screenshot. A screenshot or this form alone never unlocks Premium: only the owner's approval does.", color = Dim, fontSize = 11.sp)
    }
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) { Cap("Payment status"); Spacer(Modifier.weight(1f)); Chip("MANUAL OWNER APPROVAL", Warn) }
        if (hist.isEmpty()) Text("No payments submitted yet.", color = Dim, fontSize = 12.sp)
        hist.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(h.ref, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("${h.plan}  ·  ${h.amount}  ·  ${h.date}", color = Dim, fontSize = 11.sp) }
                Chip(when (h.status) { "approved" -> "APPROVED"; "rejected" -> "REJECTED"; else -> "PENDING VERIFICATION" }, when (h.status) { "approved" -> Ok; "rejected" -> Bad; else -> Warn })
            }
        }
        Btn("CHECK STATUS", false, !busy, busy) {
            scope.launch {
                busy = true; val rf = Pay.refresh(c, sp); busy = false; tick++; toast(rf.msg.take(70))
                val k = rf.key; if (rf.status == "APPROVED" && k != null) { sp.edit().putString("k", k).apply(); (c as? Activity)?.recreate() }
            }
        }
    }
}
