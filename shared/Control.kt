package com.unoxidized.shared
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Remote control file hosted on GitHub (no server to run). The Owner app edits it, the other apps read it. */
object Control {
    const val OWNER = "Blackdragon0777"; const val REPO = "Unoxidized-X-Download"; const val BRANCH = "main"; const val PATH = "control.json"
    const val FILE_URL = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$PATH"
    const val MEDIAFIRE = "https://www.mediafire.com/file/unqnjkje1sc17ju/UnoxidizedX.zip/file"
    class Data(val maintenance: Boolean, val maintMsg: String, val announce: String, val announceId: Int, val latest: String, val updateUrl: String, val notes: String,
               val revoked: Set<String>, val prices: List<String>, val payments: List<String>, val contact: String, val raw: String, val bTitle: String = "", val bWelcome: String = "", val bAccent: String = "", val bGlow: String = "", val bLogo: String = "", val bBg: String = "", val tiers: Map<String, String> = emptyMap())
    private fun strs(o: JSONObject, k: String): List<String> = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    fun parse(raw: String): Data? = try {
        val o = JSONObject(raw); val s = o.optJSONObject("showcase"); val br = o.optJSONObject("branding"); val ft = o.optJSONObject("features")
        Data(o.optBoolean("maintenance"), o.optString("maintenanceMsg"), o.optString("announce"), o.optInt("announceId"), o.optString("latest"), o.optString("update", MEDIAFIRE), o.optString("notes"),
            strs(o, "revoked").map { it.uppercase() }.toSet(), if (s != null) strs(s, "prices") else emptyList(), if (s != null) strs(s, "payments") else emptyList(), s?.optString("contact") ?: "", raw, br?.optString("title") ?: "", br?.optString("welcome") ?: "", br?.optString("accent") ?: "", br?.optString("glow") ?: "", br?.optString("logo") ?: "", br?.optString("bg") ?: "", tiers = if (ft == null) emptyMap() else ft.keys().asSequence().associateWith { ft.optString(it) })
    } catch (e: Exception) { null }
    /** Fetches the file; falls back to the last good copy when offline or when the file doesn't exist yet. */
    fun fetch(sp: SharedPreferences): Data? {
        val raw = try { val c = URL("$FILE_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection; c.connectTimeout = 4000; c.readTimeout = 4000; c.setRequestProperty("Cache-Control", "no-cache")
            val rc = c.responseCode; CtlState.noFile = rc == 404; if (rc == 200) c.inputStream.bufferedReader().readText() else null } catch (e: Exception) { null }
        if (raw != null && parse(raw) != null) sp.edit().putString("ctl_raw", raw).putLong("ctl_at", System.currentTimeMillis()).apply()
        CtlState.online = raw != null && parse(raw) != null
        return parse(raw ?: sp.getString("ctl_raw", "") ?: "")
    }
    fun newer(latest: String, cur: String): Boolean { val a = latest.split(".").map { it.toIntOrNull() ?: 0 }; val b = cur.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }; return false }
}
object CtlState {
    var data by mutableStateOf<Control.Data?>(null); var dismissed by mutableStateOf(-1); var online by mutableStateOf(false); var noFile by mutableStateOf(false)
    fun revoked(dev: String) = data?.revoked?.contains(dev.uppercase()) == true
}
