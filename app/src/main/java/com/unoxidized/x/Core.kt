package com.unoxidized.x
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import rikka.shizuku.Shizuku
import java.security.*
import java.security.spec.X509EncodedKeySpec

object License {
    // Paste the PUBLIC KEY printed by tools/keygen.py here
    const val PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEZ9ir9pfMV/XNyZ9DOc04Y9Tf8mnVxVKTQUZC246F/1aXFNJiu4TMj5cn9I2HxXYnfwjOIxdlWlEQeX+ZiaK/wA=="
    fun deviceId(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID).take(12).uppercase()
    /** returns null if valid, otherwise an error message */
    fun check(c: Context, key: String): String? = try {
        val k = key.trim()
        if (!k.startsWith("UX-")) "Invalid key" else {
            val (p, s) = k.drop(3).split(".")
            val d = { x: String -> Base64.decode(x, Base64.URL_SAFE or Base64.NO_PADDING) }
            val pk = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUB, Base64.DEFAULT)))
            val sg = Signature.getInstance("SHA256withECDSA").apply { initVerify(pk); update(d(p)) }
            val (dev, exp, _) = String(d(p)).split("|")
            when {
                !sg.verify(d(s)) -> "Invalid key"
                dev != deviceId(c) -> "This key belongs to another device"
                exp.toLong() < System.currentTimeMillis() / 86400000 -> "Key expired"
                else -> null
            }
        }
    } catch (e: Exception) { "Invalid key" }
    fun info(key: String) = try { String(Base64.decode(key.drop(3).split(".")[0], Base64.URL_SAFE or Base64.NO_PADDING)).split("|")[2] } catch (e: Exception) { "?" }
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
