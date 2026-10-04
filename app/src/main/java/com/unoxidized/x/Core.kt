package com.unoxidized.x
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import rikka.shizuku.Shizuku

object Sh {
    fun running() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    fun granted() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    fun ask() = try { Shizuku.requestPermission(0) } catch (e: Throwable) {}
    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).apply { isAccessible = true }
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val o = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor(); o.ifBlank { "OK" }
    } catch (e: Throwable) { "Error: ${e.message}" }
}

object Env {
    const val SZ = "moe.shizuku.privileged.api"
    fun installed(c: Context) = try { c.packageManager.getPackageInfo(SZ, 0); true } catch (e: Exception) { false }
    private fun go(c: Context, i: Intent): Boolean = try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    fun openApp(c: Context, pkg: String) { c.packageManager.getLaunchIntentForPackage(pkg)?.let { go(c, it) } }
    fun installShizuku(c: Context) { if (!go(c, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SZ")))) go(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
    fun openDev(c: Context) { go(c, Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    fun hz(c: Context): List<Int> = try { (c.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(0).supportedModes.map { Math.round(it.refreshRate) }.distinct().sorted() } catch (e: Exception) { listOf(60) }
    fun device() = "${Build.MANUFACTURER} ${Build.MODEL}".replaceFirstChar { it.uppercase() }
    fun soc(): String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
    fun access(): String = try { if (Shizuku.getUid() == 0) "Root" else "ADB shell" } catch (e: Throwable) { "?" }
}
