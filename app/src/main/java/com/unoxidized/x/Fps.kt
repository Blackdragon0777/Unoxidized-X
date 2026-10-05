package com.unoxidized.x
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class SessionRec(val start: Long, val durSec: Long, val avgFps: Int?, val minFps: Int?, val p95: Float?, val jank: Int?, val peakTemp: Float?, val batt: Int?, val avgRam: Int, val optimized: Boolean, val profile: String)

object SessStore {
    private fun toJ(r: SessionRec) = JSONObject().put("s", r.start).put("d", r.durSec).put("af", r.avgFps ?: -1).put("mf", r.minFps ?: -1).put("p95", (r.p95 ?: -1f).toDouble())
        .put("jk", r.jank ?: -1).put("pt", (r.peakTemp ?: -1000f).toDouble()).put("bt", r.batt ?: -1).put("ar", r.avgRam).put("op", r.optimized).put("pf", r.profile)
    private fun fromJ(o: JSONObject) = SessionRec(o.getLong("s"), o.getLong("d"), o.getInt("af").takeIf { it >= 0 }, o.getInt("mf").takeIf { it >= 0 }, o.getDouble("p95").toFloat().takeIf { it >= 0f },
        o.getInt("jk").takeIf { it >= 0 }, o.getDouble("pt").toFloat().takeIf { it > -999f }, o.getInt("bt").takeIf { it >= 0 }, o.getInt("ar"), o.getBoolean("op"), o.getString("pf"))
    fun list(sp: SharedPreferences): List<SessionRec> = try { val a = JSONArray(sp.getString("sessions", "[]")); (0 until a.length()).map { fromJ(a.getJSONObject(it)) } } catch (e: Exception) { emptyList() }
    private fun write(sp: SharedPreferences, l: List<SessionRec>) { val n = JSONArray(); l.forEach { n.put(toJ(it)) }; sp.edit().putString("sessions", n.toString()).apply() }
    fun add(sp: SharedPreferences, r: SessionRec) { write(sp, (listOf(r) + list(sp)).take(40)) }
    fun remove(sp: SharedPreferences, start: Long) { write(sp, list(sp).filter { it.start != start }) }
}

/** Frame data read from Android's SurfaceFlinger through Shizuku. Returns null when the phone doesn't expose it. */
object Fps {
    class Frames(val times: List<Long>, val periodNs: Long)
    private var layer: String? = null
    fun reset() { layer = null }
    private fun findLayer(pkg: String): String? {
        val c = Sh.run("dumpsys SurfaceFlinger --list").lines().map { it.trim() }.filter { it.contains(pkg) }
        return c.firstOrNull { it.startsWith("SurfaceView") } ?: c.firstOrNull { !it.contains("Background") } ?: c.firstOrNull()
    }
    private fun latency(l: String): Frames? {
        val ls = Sh.run("dumpsys SurfaceFlinger --latency '" + l.replace("'", "'\\''") + "'").trim().lines()
        if (ls.size < 3) return null
        val period = ls[0].trim().toLongOrNull() ?: return null
        val t = ls.drop(1).mapNotNull { val p = it.trim().split(Regex("\\s+")); if (p.size >= 3) p[1].toLongOrNull() else null }.filter { it in 1L until Long.MAX_VALUE }
        return if (t.size >= 5) Frames(t, period) else null
    }
    fun frames(pkg: String): Frames? {
        layer?.let { val f = latency(it); if (f != null) return f }
        layer = findLayer(pkg); return layer?.let { latency(it) }
    }
}

/** Process-wide live session state. Written by SessionService, read by the UI. */
object Session {
    var watching by mutableStateOf(false); var active by mutableStateOf(false); var status by mutableStateOf("Not watching")
    var start by mutableStateOf(0L); var now by mutableStateOf(0L); var fpsNow by mutableStateOf<Int?>(null); var fpsNote by mutableStateOf("Waiting for the game")
    var tempNow by mutableStateOf<Float?>(null); var battNow by mutableStateOf(-1); var peakTemp by mutableStateOf<Float?>(null)
    val fpsHist = mutableStateListOf<Float>(); var last by mutableStateOf<SessionRec?>(null)
    private var battStart = -1; private var charged = false; private var optimized = false; private var profile = ""
    private var frames = 0L; private var spanNs = 0L; private var lastT = 0L; private var minFps = Int.MAX_VALUE; private var jankN = 0L; private var totalN = 0L
    private val hist = IntArray(101); private var ramSum = 0L; private var ramN = 0

    fun begin(c: Context, sp: SharedPreferences, pkg: String) {
        val s = Sys.snap(c); battStart = s.batt; charged = s.charging; peakTemp = s.tempC; tempNow = s.tempC
        optimized = Sh.run("device_config get game_overlay $pkg").contains("fps=")
        val k = sp.getString("prof", "") ?: ""
        profile = PROFS.firstOrNull { it.key == k }?.name ?: ProfStore.load(sp).firstOrNull { "my:" + it.id == k }?.name ?: (if (optimized) "Custom" else "Default")
        frames = 0; spanNs = 0; lastT = 0; minFps = Int.MAX_VALUE; jankN = 0; totalN = 0; hist.fill(0); ramSum = 0; ramN = 0
        fpsHist.clear(); fpsNow = null; fpsNote = "Measuring..."; start = System.currentTimeMillis(); now = start; active = true; status = "CODM is running"
        sp.edit().putLong("lastlaunch", start).apply(); Hist.add(sp, "CODM launched" + (if (optimized) " (profile active)" else " (no profile)"))
    }

    fun tick(c: Context, pkg: String) {
        val s = Sys.snap(c); now = System.currentTimeMillis(); tempNow = s.tempC; battNow = s.batt; if (s.charging) charged = true
        s.tempC?.let { if ((peakTemp ?: 0f) < it) peakTemp = it }; ramSum += s.ramPct; ramN++
        val f = Fps.frames(pkg)
        if (f == null) { fpsNow = null; fpsNote = "Not available: game surface not found"; return }
        val t = f.times.filter { it > lastT }
        val seq = if (lastT > 0) listOf(lastT) + t else t
        if (seq.size >= 3) {
            val span = seq.last() - seq.first(); val dts = seq.zipWithNext { a, b -> (b - a) / 1e6f }
            if (span > 0) {
                val fps = (dts.size * 1e9 / span).toInt(); fpsNow = fps; fpsNote = "Measured via SurfaceFlinger"; frames += dts.size; spanNs += span; minFps = minOf(minFps, fps)
                fpsHist.add(fps.toFloat()); if (fpsHist.size > 40) fpsHist.removeAt(0)
                val med = dts.sorted()[dts.size / 2]
                for (d in dts) { totalN++; hist[minOf(100, d.toInt())]++; if (d > 2f * med || d > 41.7f) jankN++ }
            }
            lastT = seq.last()
        }
    }

    fun end(c: Context, sp: SharedPreferences) {
        val dur = (System.currentTimeMillis() - start) / 1000; active = false; status = "Watching for CODM"; val s = Sys.snap(c)
        val avgFps = if (spanNs > 0) (frames * 1e9 / spanNs).toInt() else null
        val p95 = if (totalN > 0) { var acc = 0L; var ms = 100; for (i in 0..100) { acc += hist[i]; if (acc >= totalN * 0.95) { ms = i; break } }; ms.toFloat() } else null
        val jank = if (totalN > 0) (jankN * 100 / totalN).toInt() else null
        val batt = if (charged || battStart < 0 || s.batt < 0) null else maxOf(0, battStart - s.batt)
        val rec = SessionRec(start, dur, avgFps, if (minFps == Int.MAX_VALUE) null else minFps, p95, jank, peakTemp, batt, if (ramN > 0) (ramSum / ramN).toInt() else 0, optimized, profile)
        if (dur >= 30) { SessStore.add(sp, rec); last = rec; Hist.add(sp, "Session complete: " + (dur / 60) + " min" + (avgFps?.let { ", avg $it FPS" } ?: "")) }
        fpsNow = null; fpsHist.clear(); Fps.reset()
    }
}

class SessionService : Service() {
    @Volatile private var go = true
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val ch = "ux_session"
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(ch, "Session tracking", NotificationManager.IMPORTANCE_LOW))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        val n = b.setContentTitle("Unoxidized X").setContentText("Watching for CODM sessions").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
        if (!Session.watching) { Session.watching = true; go = true; thread { loop() } }
        return START_STICKY
    }
    private fun loop() {
        val sp = getSharedPreferences("ux", 0)
        while (go) {
            try {
                val pkg = sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG
                if (!Sh.granted()) { Session.status = "Waiting for Shizuku"; Thread.sleep(4000); continue }
                val up = Sys.running(pkg)
                if (up && !Session.active) Session.begin(this, sp, pkg)
                if (up && Session.active) Session.tick(this, pkg)
                if (!up) { if (Session.active) { Session.end(this, sp) } else { Session.status = "Watching for CODM" } }
                Thread.sleep(if (Session.active) 2000L else 3000L)
            } catch (e: Throwable) { Thread.sleep(3000) }
        }
        Session.watching = false
    }
    override fun onDestroy() { go = false; if (Session.active) Session.end(this, getSharedPreferences("ux", 0)); Session.watching = false; super.onDestroy() }
    companion object {
        fun start(c: Context) { val i = Intent(c, SessionService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
        fun stop(c: Context) { c.stopService(Intent(c, SessionService::class.java)) }
    }
}
