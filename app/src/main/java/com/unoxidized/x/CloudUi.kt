package com.unoxidized.x
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Account actions against Firebase (see shared/Cloud.kt). The server's security rules decide what a user may do, never this app. */
object Acct {
    private fun profile(c: Context, sp: SharedPreferences, name: String, tg: String, mail: String): Map<String, Any?> =
        mapOf("name" to name, "gmail" to mail, "telegram" to tg, "device" to Lic.deviceId(c), "status" to "pending", "role" to "F", "key" to "", "createdAt" to System.currentTimeMillis())

    /** Returns an empty string on success, otherwise a readable error. */
    suspend fun register(c: Context, sp: SharedPreferences, name: String, tg: String, mail: String, pw: String): String = withContext(Dispatchers.IO) {
        val (s, e) = Cloud.signUp(mail, pw)
        if (s == null) return@withContext e
        Cloud.keep(sp, s)
        sp.edit().putString("reg_name", name).putString("reg_mail", mail).putString("reg_tg", tg).apply()
        val r = Cloud.commit(s, listOf(Cloud.op("users/${s.uid}", profile(c, sp, name, tg, mail), true)))
        if (r.ok) "" else "Account created, but the server refused the profile: " + r.msg()
    }

    suspend fun login(c: Context, sp: SharedPreferences, mail: String, pw: String): String = withContext(Dispatchers.IO) {
        val (s, e) = Cloud.signIn(mail, pw)
        if (s == null) return@withContext e
        Cloud.keep(sp, s)
        val g = Cloud.getDoc(s, "users/${s.uid}")
        if (g.code == 404) {
            val name = sp.getString("reg_name", "") ?: ""; val tg = sp.getString("reg_tg", "") ?: ""
            val r = Cloud.commit(s, listOf(Cloud.op("users/${s.uid}", profile(c, sp, name.ifBlank { mail.substringBefore('@') }, tg, mail), true)))
            if (!r.ok) return@withContext "Signed in, but the server refused the profile: " + r.msg()
        }
        ""
    }
    fun signOut(sp: SharedPreferences) { Cloud.forget(sp); sp.edit().putLong("reg_at", 0L).apply() }
}

/** Create-account / sign-in form. Call it inside a Glass card. */
@Composable fun CloudAuthCard(c: Context, sp: SharedPreferences, onDone: () -> Unit) {
    var mode by remember { mutableStateOf("Create account") }; val create = mode == "Create account"
    var name by remember { mutableStateOf(sp.getString("reg_name", "") ?: "") }; var tg by remember { mutableStateOf(sp.getString("reg_tg", "") ?: "") }
    var mail by remember { mutableStateOf(sp.getString("reg_mail", "") ?: "") }; var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    Cap("Account")
    Pick(listOf("Create account", "Sign in"), mode) { mode = it; err = "" }
    if (create) {
        OutlinedTextField(name, { name = it.take(40); err = "" }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(tg, { tg = it.take(40); err = "" }, Modifier.fillMaxWidth(), label = { Text("Telegram username") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
    }
    OutlinedTextField(mail, { mail = it.trim().take(60); err = "" }, Modifier.fillMaxWidth(), label = { Text("Gmail / email") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
    OutlinedTextField(pw, { pw = it.take(60); err = "" }, Modifier.fillMaxWidth(), label = { Text("Password (6+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp), colors = fieldColors())
    if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
    Btn(if (create) "CREATE ACCOUNT" else "SIGN IN", true, !busy, busy) {
        if (!mail.contains("@") || pw.length < 6) { err = "Enter a valid email and a password of at least 6 characters."; return@Btn }
        if (create && (name.isBlank() || tg.isBlank())) { err = "Enter your name and Telegram username."; return@Btn }
        scope.launch {
            busy = true
            val e = if (create) Acct.register(c, sp, name.trim(), tg.trim(), mail, pw) else Acct.login(c, sp, mail, pw)
            busy = false
            if (e.isEmpty()) { pw = ""; onDone() } else err = e
        }
    }
    Text("Your account lives on the NACHT X server. Premium is only switched on by the owner after reviewing your payment.", color = Dim, fontSize = 11.sp)
}
