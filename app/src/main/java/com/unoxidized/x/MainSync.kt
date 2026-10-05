package com.unoxidized.x
import android.content.Context
import android.os.Bundle
import com.unoxidized.shared.*

class MainSync : SyncProvider() {
    override fun data(c: Context): Bundle {
        val sp = c.getSharedPreferences("ux", 0); val dev = Lic.deviceId(c); val k = sp.getString("k", "") ?: ""
        val r = if (k.isEmpty()) null else Lic.check(dev, k); val s = SessStore.list(sp)
        return Bundle().apply {
            putString("ver", try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }); putString("dev", dev)
            putBoolean("lic", r?.ok == true); putString("licMsg", if (k.isEmpty()) "No key entered" else (r?.msg ?: "")); putString("role", r?.role ?: ""); putLong("left", r?.left ?: 0L)
            putLong("lastopt", sp.getLong("lastopt", 0L)); putLong("lastlaunch", sp.getLong("lastlaunch", 0L)); putInt("sessions", s.size); putInt("lastFps", s.firstOrNull()?.avgFps ?: -1)
            putBoolean("shizuku", Sh.granted()); putBoolean("tracking", Session.watching)
        }
    }
}
