package com.unoxidized.shared
import android.content.Context
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Key format: UX-<DEVICEID>-<ISSUE DAY base36>-<10 char tag>. Valid for 7 days. */
object Lic {
    const val DAYS = 7
    private fun secret() = "UnoxidizedX|" + "@UnoxidizedX|" + "wk-key|v3|" + "c41f7a9e02"
    fun deviceId(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID).take(12).uppercase()
    fun today() = System.currentTimeMillis() / 86400000L
    private fun tag(dev: String, day: Long): String {
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(secret().toByteArray(), "HmacSHA256"))
        return m.doFinal("$dev|$day".toByteArray()).joinToString("") { "%02X".format(it) }.take(10)
    }
    fun make(dev: String, day: Long) = "UX-${dev.uppercase()}-${java.lang.Long.toString(day, 36).uppercase()}-${tag(dev.uppercase(), day)}"
    fun until(day: Long): String = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date((day + DAYS) * 86400000L))
    fun clean(k: String) = k.filter { !it.isWhitespace() }.uppercase()
    class R(val ok: Boolean, val msg: String, val left: Long = 0, val day: Long = 0)
    fun check(dev: String, raw: String): R {
        val p = clean(raw).split("-")
        if (p.size != 4 || p[0] != "UX") return R(false, "That doesn't look like a key. Keys look like UX-DEVICEID-XXXX-XXXXXXXXXX")
        val day = p[2].toLongOrNull(36) ?: return R(false, "Invalid key")
        if (p[3] != tag(p[1], day)) return R(false, "Invalid key (copy the whole key again)")
        if (p[1] != dev.uppercase()) return R(false, "This key is for device ${p[1]}. Your device ID is $dev")
        val left = day + DAYS - today()
        if (left <= 0) return R(false, "This key expired. Open Unoxidized Generator for a new one")
        return R(true, "OK", left, day)
    }
}
