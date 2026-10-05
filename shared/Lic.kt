package com.unoxidized.shared
import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** v1 (free, 7 days, made by the Generator): UX-<DEVICEID>-<DAY>-<TAG>
 *  v2 (made only by the Owner app, signed): UX2-<DEVICEID>-<DAY>-<DURATION>-<ROLE>.<SIGNATURE>  (duration 0 = lifetime) */
object Lic {
    const val DAYS = 7
    private const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtNzJpC7rdxlSDdqPeaOY1NsgnKG4LN+cWm4HvRTAsBIvOQyKLokT0am5N6/MEs42WH2ggTNOYgqTasFrL1AmWQ=="
    private fun secret() = "UnoxidizedX|" + "@UnoxidizedX|" + "wk-key|v3|" + "c41f7a9e02"
    fun deviceId(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID).take(12).uppercase()
    fun today() = System.currentTimeMillis() / 86400000L
    private fun tag(dev: String, day: Long): String {
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(secret().toByteArray(), "HmacSHA256"))
        return m.doFinal("$dev|$day".toByteArray()).joinToString("") { "%02X".format(it) }.take(10)
    }
    fun b36(v: Long) = java.lang.Long.toString(v, 36).uppercase()
    fun make(dev: String, day: Long) = "UX-${dev.uppercase()}-${b36(day)}-${tag(dev.uppercase(), day)}"
    fun dateText(day: Long): String = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(day * 86400000L))
    fun until(day: Long): String = dateText(day + DAYS)
    fun clean(k: String) = k.filter { !it.isWhitespace() }
    fun roleName(r: String) = when (r) { "M" -> "Moderator"; "A" -> "Administrator"; else -> "Free user" }
    class R(val ok: Boolean, val msg: String, val left: Long = 0, val day: Long = 0, val role: String = "F", val untilText: String = "", val frac: Float = 0f)
    class Info(val ver: Int, val dev: String, val day: Long, val dur: Long, val role: String, val sigOk: Boolean, val expired: Boolean, val untilText: String)
    private fun sigOk(header: String, sig: String): Boolean = try {
        val pk = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT)))
        val s = Signature.getInstance("SHA256withECDSA"); s.initVerify(pk); s.update(header.toByteArray())
        s.verify(Base64.decode(sig, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
    } catch (e: Exception) { false }
    fun inspect(raw: String): Info? {
        val k = clean(raw)
        if (k.startsWith("UX2-")) {
            val h = k.substringBefore('.'); val p = h.split("-"); if (p.size != 5) return null
            val day = p[2].toLongOrNull(36) ?: return null; val dur = p[3].toLongOrNull(36) ?: return null
            return Info(2, p[1], day, dur, p[4], sigOk(h, k.substringAfter('.', "")), dur != 0L && day + dur <= today(), if (dur == 0L) "Lifetime" else dateText(day + dur))
        }
        val p = k.uppercase().split("-"); if (p.size != 4 || p[0] != "UX") return null
        val day = p[2].toLongOrNull(36) ?: return null
        return Info(1, p[1], day, DAYS.toLong(), "F", p[3] == tag(p[1], day), day + DAYS <= today(), until(day))
    }
    fun check(dev: String, raw: String): R {
        val i = inspect(raw) ?: return R(false, "That doesn't look like a key. Keys start with UX- or UX2-")
        if (!i.sigOk) return R(false, "Invalid key (copy the whole key again)")
        if (i.dev != dev.uppercase()) return R(false, "This key is for device ${i.dev}. Your device ID is $dev")
        if (i.expired) return R(false, "This key expired. Get a new one from the Generator or the owner")
        val left = if (i.dur == 0L) 99999L else i.day + i.dur - today()
        return R(true, "OK", left, i.day, i.role, i.untilText, if (i.dur == 0L) 1f else (left.toFloat() / i.dur).coerceIn(0f, 1f))
    }
}
