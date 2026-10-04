package com.unoxidized.x
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import rikka.shizuku.Shizuku
import java.security.*
import java.security.spec.X509EncodedKeySpec

object License {
    fun deviceId(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID).take(12).uppercase()
    private fun d(x: String) = Base64.decode(x, Base64.URL_SAFE or Base64.NO_PADDING)
    fun clean(k: String) = k.filter { !it.isWhitespace() }
    private fun parts(key: String) = String(d(clean(key).drop(3).split(".")[0])).split("|")
    /** null = valid, otherwise a readable error */
    fun check(c: Context, raw: String): String? = try {
        val k = clean(raw)
        if (!k.startsWith("UX-") || !k.contains(".")) "That doesn't look like a key. Keys start with UX-" else {
            val (p, s) = k.drop(3).split(".")
            val pk = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(Pub.KEY, Base64.DEFAULT)))
            val sg = Signature.getInstance("SHA256withECDSA").apply { initVerify(pk); update(d(p)) }
            val (dev, exp, _) = String(d(p)).split("|")
            when {
                !sg.verify(d(s)) -> "Invalid key (copy it again, the whole thing)"
                dev != deviceId(c) -> "This key is for device $dev. Your device ID is ${deviceId(c)}"
                exp.toLong() < System.currentTimeMillis() / 86400000 -> "This key has expired"
                else -> null
            }
        }
    } catch (e: Exception) { "Invalid key (${e.javaClass.simpleName})" }
    fun info(key: String) = try { parts(key)[2] } catch (e: Exception) { "?" }
    fun expiry(key: String): String = try {
        val exp = parts(key)[1].toLong(); val left = exp - System.currentTimeMillis() / 86400000
        if (left > 36500) "Lifetime" else java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(exp * 86400000)) + "  ($left days left)"
    } catch (e: Exception) { "?" }
}

object Sh {
    fun ready() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    fun ask() = try { Shizuku.requestPermission(0) } catch (e: Throwable) {}
    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).apply { isAccessible = true }
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val o = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor(); o.ifBlank { "OK" }
    } catch (e: Throwable) { "Error: ${e.message}" }
}
