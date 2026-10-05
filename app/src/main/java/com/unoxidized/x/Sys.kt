package com.unoxidized.x
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject

object Sys {
    class Snap(val ramPct: Int, val ramMb: Long, val batt: Int, val tempC: Float?, val charging: Boolean, val storPct: Int, val storGb: Long, val hz: Int)
    fun snap(c: Context): Snap {
        val mi = ActivityManager.MemoryInfo(); (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val t = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val sf = StatFs(Environment.getDataDirectory().path); val tot = sf.totalBytes.coerceAtLeast(1L)
        val hz = try { (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0).refreshRate.toInt() } catch (e: Exception) { 0 }
        return Snap((100 - mi.availMem * 100 / mi.totalMem.coerceAtLeast(1L)).toInt(), mi.totalMem / 1048576, if (lvl < 0) -1 else lvl * 100 / sc, if (t == Int.MIN_VALUE) null else t / 10f,
            (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0, (100 - sf.availableBytes * 100 / tot).toInt(), tot / 1073741824, hz)
    }
    private var last: LongArray? = null
    /** CPU % from /proc/stat (needs Shizuku). null when it can't be measured. */
    fun cpu(): Int? {
        val p = Sh.run("head -n1 /proc/stat").trim().split(Regex("\\s+"))
        if (p.size < 8 || p[0] != "cpu") return null
        val v = try { p.drop(1).take(8).map { it.toLong() } } catch (e: Exception) { return null }
        val total = v.sum(); val idle = v[3] + v[4]; val l = last; last = longArrayOf(total, idle)
        if (l == null) return null
        val dt = total - l[0]; val di = idle - l[1]
        return if (dt <= 0) null else ((dt - di) * 100 / dt).toInt()
    }
    fun installed(pkg: String) = Sh.run("pm path $pkg").contains("package:")
    fun running(pkg: String) = Sh.run("pidof $pkg").trim().firstOrNull()?.isDigit() == true
    fun score(s: Snap, ready: Boolean): Int {
        var x = 100.0; x -= maxOf(0, s.ramPct - 50) * 0.6; s.tempC?.let { x -= maxOf(0f, it - 36f) * 3 }
        if (s.batt in 0..19 && !s.charging) x -= 15; if (s.storPct > 90) x -= 10; if (!ready) x -= 30
        return x.toInt().coerceIn(0, 100)
    }
}

object Hist {
    fun add(sp: SharedPreferences, text: String) {
        val a = try { JSONArray(sp.getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
        val n = JSONArray(); n.put(JSONObject().put("t", System.currentTimeMillis()).put("m", text))
        for (i in 0 until minOf(a.length(), 59)) n.put(a.get(i)); sp.edit().putString("hist", n.toString()).apply()
    }
    fun list(sp: SharedPreferences): List<Pair<Long, String>> = try {
        val a = JSONArray(sp.getString("hist", "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); o.getLong("t") to o.getString("m") }
    } catch (e: Exception) { emptyList() }
    fun clear(sp: SharedPreferences) { sp.edit().remove("hist").apply() }
    fun remove(sp: SharedPreferences, t: Long) { val n = JSONArray(); list(sp).filter { it.first != t }.forEach { n.put(JSONObject().put("t", it.first).put("m", it.second)) }; sp.edit().putString("hist", n.toString()).apply() }
}
