package com.unoxidized.x
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.unoxidized.shared.CtlState
import com.unoxidized.shared.Lic
import kotlin.concurrent.thread

/** NACHT X DECK colours: obsidian with silver lines and an ice-blue accent. */
object DK {
    val bg1 = 0xF21B1F29.toInt(); val bg2 = 0xF20B0D12.toInt(); val card = 0xFF171B24.toInt(); val line = 0x889AA5B8.toInt()
    val text = 0xFFE9EDF5.toInt(); val dim = 0xFF8B93A5.toInt(); val accent = 0xFF9CC4FF.toInt(); val ok = 0xFF5BD6A0.toInt(); val warn = 0xFFF2B84B.toInt(); val bad = 0xFFFF6B6B.toInt(); val dark = 0xFF0B0D12.toInt()
}

/** Round gauge drawn with a canvas arc. */
class GaugeView(c: Context) : View(c) {
    var pct = 0f; var big = "--"; var small = ""; var accent = DK.accent
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(50, 255, 255, 255) }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val t1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val t2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DK.dim; textAlign = Paint.Align.CENTER }
    private val box = RectF()
    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density; val sw = 7f * d
        track.strokeWidth = sw; arc.strokeWidth = sw; arc.color = accent
        val s = minOf(width, height).toFloat(); val l = (width - s) / 2f + sw; val t = (height - s) / 2f + sw
        box.set(l, t, l + s - 2f * sw, t + s - 2f * sw)
        cv.drawArc(box, 135f, 270f, false, track)
        cv.drawArc(box, 135f, 270f * pct.coerceIn(0f, 100f) / 100f, false, arc)
        t1.textSize = s * 0.24f; t2.textSize = s * 0.12f
        cv.drawText(big, width / 2f, height / 2f + s * 0.06f, t1)
        cv.drawText(small, width / 2f, height / 2f + s * 0.27f, t2)
    }
}

/** Centre-of-screen aiming mark: 0 = dot, 1 = cross, 2 = ring. */
class CrosshairView(c: Context) : View(c) {
    var style = 0; var sizeDp = 18; var color = Color.RED
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density; val cx = width / 2f; val cy = height / 2f; val s = sizeDp * d
        p.color = color; p.strokeWidth = 2f * d
        if (style == 0) { p.style = Paint.Style.FILL; cv.drawCircle(cx, cy, s * 0.18f, p) }
        else if (style == 1) { p.style = Paint.Style.STROKE; cv.drawLine(cx - s, cy, cx - s * 0.3f, cy, p); cv.drawLine(cx + s * 0.3f, cy, cx + s, cy, p); cv.drawLine(cx, cy - s, cx, cy - s * 0.3f, p); cv.drawLine(cx, cy + s * 0.3f, cx, cy + s, p) }
        else { p.style = Paint.Style.STROKE; cv.drawCircle(cx, cy, s * 0.6f, p); p.style = Paint.Style.FILL; cv.drawCircle(cx, cy, s * 0.1f, p) }
    }
}

/** The two small overlays that live outside the panel: the crosshair and the FPS/temperature pill. */
object DeckOverlays {
    private var cross: CrosshairView? = null
    private var hud: TextView? = null
    @Volatile private var hudRun = false
    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    private fun type() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    fun crossShown() = cross != null
    fun hudRunning() = hud != null && hudRun

    fun crossOn(c: Context, sp: SharedPreferences): Boolean {
        if (cross != null) { crossStyle(sp); return true }
        if (!Settings.canDrawOverlays(c)) return false
        val d = c.resources.displayMetrics.density; val v = CrosshairView(c)
        val p = WindowManager.LayoutParams((160 * d).toInt(), (160 * d).toInt(), type(), flags, PixelFormat.TRANSLUCENT); p.gravity = Gravity.CENTER
        return try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(v, p); cross = v; crossStyle(sp); true } catch (e: Exception) { false }
    }
    fun crossStyle(sp: SharedPreferences) { val v = cross ?: return; v.style = sp.getInt("cx_style", 0); v.sizeDp = sp.getInt("cx_size", 18); v.color = sp.getInt("cx_color", Color.RED); v.invalidate() }
    fun crossOff(c: Context) { val v = cross; if (v != null) { try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { } }; cross = null }

    private fun readFps(sp: SharedPreferences): Int {
        val f = Fps.frames(sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG) ?: return -1
        val t = f.times; if (t.size < 5) return -1
        val span = t.last() - t.first(); if (span <= 0L) return -1
        return Math.round((t.size - 1) * 1.0e9 / span).toInt().coerceIn(0, 240)
    }
    fun hudOn(c: Context, sp: SharedPreferences, ui: Handler): Boolean {
        if (hud != null && hudRun) return true
        if (!Settings.canDrawOverlays(c)) return false
        val d = c.resources.displayMetrics.density
        val v = TextView(c).apply { text = "-- FPS  |  --°C"; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { setColor(0xCC0B0D12.toInt()); cornerRadius = 40f * d; setStroke((1 * d).toInt(), DK.accent) } }
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type(), flags, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.END; p.x = (12 * d).toInt(); p.y = (28 * d).toInt()
        try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(v, p) } catch (e: Exception) { return false }
        hud = v; hudRun = true
        thread { while (hudRun) {
            val t = Sys.snap(c).tempC; val f = if (Sh.granted()) readFps(sp) else -1
            val txt = (if (f >= 0) "$f FPS" else "-- FPS") + "  |  " + (if (t != null) String.format("%.1f°C", t) else "--°C")
            ui.post { hud?.text = txt }
            try { Thread.sleep(1000L) } catch (e: Exception) { } } }
        return true
    }
    fun hudOff(c: Context) { hudRun = false; val v = hud; if (v != null) { try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { } }; hud = null }
    fun removeAll(c: Context) { crossOff(c); hudOff(c) }
}

/** The expanded floating panel: header, favourite apps, six tabs. Built from plain views so it works as a system overlay. */
class DeckUi(private val c: Context, private val sp: SharedPreferences, private val ui: Handler, private val say: (String) -> Unit, private val collapse: () -> Unit,
             private val stop: () -> Unit, private val drag: (View) -> Unit, private val setAlpha: (Float) -> Unit) {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT; private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val names = listOf("PULSE", "QUICK", "COOL", "GUARD", "AIM", "LAB")
    private var live: Runnable? = null
    private var reshow: () -> Unit = { }
    private val rates: List<Int> = Env.hz(c)
    private val maxHz: Int get() = rates.maxOrNull() ?: 60
    private val minHz: Int get() = rates.minOrNull() ?: 60

    private fun dp(v: Int) = (v * c.resources.displayMetrics.density).toInt()
    private fun bg(color: Int, r: Int, stroke: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke) }
    private fun tv(t: String, size: Float, color: Int = DK.text, bold: Boolean = false) = TextView(c).apply { text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun chip(t: String, sel: Boolean = false, onClick: () -> Unit) = TextView(c).apply { text = t; textSize = 11f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (sel) DK.dark else DK.text); setPadding(dp(8), dp(10), dp(8), dp(10)); background = if (sel) bg(DK.accent, 12, DK.accent) else bg(DK.card, 12, DK.line); setOnClickListener { onClick() } }
    private fun row(vararg views: View): LinearLayout {
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (v in views) { v.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }; r.addView(v) }
        return r
    }
    private fun label(t: String) = tv(t, 10f, DK.dim, true).apply { setPadding(dp(4), dp(10), 0, dp(4)) }
    private fun note(t: String) = tv(t, 10f, DK.dim).apply { setPadding(dp(4), dp(6), dp(4), dp(2)) }
    private fun stopLive() { val r = live; if (r != null) ui.removeCallbacks(r); live = null }

    private fun premium(feature: String): Boolean {
        val r = Lic.check(Lic.deviceId(c), sp.getString("k", "") ?: "")
        val tier = CtlState.data?.tiers?.get(feature) ?: "premium"
        return r.ok && (r.role != "F" || tier != "premium")
    }
    private fun needShell(): Boolean { if (Sh.granted()) return true; say("Needs root or Shizuku. Open NACHT X and activate the engine."); return false }
    private fun shell(cmd: String, ok: String): Boolean {
        if (!needShell()) return false
        thread { val r = Sh.run(cmd); say(if (bad(r)) r.trim().take(60) else ok) }
        return true
    }
    private fun hzCmd(hz: Int?) = if (hz != null) "settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz" else "settings delete system min_refresh_rate; settings delete system peak_refresh_rate"
    private fun open(action: String) { try { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() } catch (e: Exception) { say("Not available on this phone") } }
    private fun launchTarget() { val e = launchApp(c, sp, sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG); if (e != null) say(e) else collapse() }

    /** A tappable on/off row. The lambda returns true when the change was accepted, so a failed action flips back. Premium rows pass the tier name in [star]. */
    private fun toggle(title: String, sub: String, key: String, star: String? = null, onChange: (Boolean) -> Boolean): View {
        val box = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(10), dp(10)); background = bg(DK.card, 14, DK.line) }
        val txt = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        txt.addView(tv(title + (if (star != null) "  ★" else ""), 13f, DK.text, true)); txt.addView(tv(sub, 10f, DK.dim))
        val pill = TextView(c).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(dp(12), dp(6), dp(12), dp(6)) }
        fun paint(v: Boolean) { pill.text = if (v) "ON" else "OFF"; pill.setTextColor(if (v) DK.dark else DK.dim); pill.background = if (v) bg(DK.ok, 10, DK.ok) else bg(0xFF10131A.toInt(), 10, DK.line) }
        paint(sp.getBoolean(key, false))
        box.setOnClickListener {
            if (star != null && !premium(star)) { say("$title is Premium. Open NACHT X > Premium."); return@setOnClickListener }
            val nv = !sp.getBoolean(key, false); val accepted = onChange(nv); val fin = if (accepted) nv else !nv
            sp.edit().putBoolean(key, fin).apply(); paint(fin)
        }
        box.addView(txt); box.addView(pill); box.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        return box
    }

    fun build(): View {
        sp.edit().putBoolean("hud_on", DeckOverlays.hudRunning()).putBoolean("cx_on", DeckOverlays.crossShown()).apply()
        val w = minOf(dp(listOf(310, 350, 390)[sp.getInt("fl_size", 1).coerceIn(0, 2)]), c.resources.displayMetrics.widthPixels - dp(16))
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(12)); layoutParams = LinearLayout.LayoutParams(w, WRAP)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(DK.bg1, DK.bg2)).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), DK.line) } }
        col.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener { override fun onViewAttachedToWindow(v: View) {}; override fun onViewDetachedFromWindow(v: View) { stopLive() } })

        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(c).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)); clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) } } }
        val titles = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        titles.addView(tv("NACHT X  DECK", 15f, DK.text, true))
        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()); val sn = Sys.snap(c)
        titles.addView(tv((if (premium("")) "PREMIUM" else "FREE") + "  ·  " + clock + "  ·  " + (if (sn.batt < 0) "n/a" else "${sn.batt}%"), 9f, DK.dim))
        head.addView(logo); head.addView(titles)
        head.addView(chip("—") { collapse() }.apply { setPadding(dp(12), dp(7), dp(12), dp(7)) }); head.addView(chip("✕") { stop() }.apply { setPadding(dp(12), dp(7), dp(12), dp(7)) })
        drag(head); col.addView(head)

        // favourite apps
        val favs = LibStore.favs(sp); val apps = (favs + LibStore.recents(sp).filter { it !in favs }).take(4)
        val appRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(2)) }
        for (pkg in apps) {
            val ic = ImageView(c).apply { try { setImageDrawable(c.packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { setImageResource(R.drawable.logo) }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(0, 0, dp(10), 0) }
                setOnClickListener { val e = launchApp(c, sp, pkg); if (e != null) say(e) else collapse() } }
            appRow.addView(ic)
        }
        appRow.addView(chip("+ ADD") { val i = c.packageManager.getLaunchIntentForPackage(c.packageName); if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() })
        col.addView(appRow)

        val tabs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(6)) }
        val tabViews = ArrayList<TextView>()
        for (n in names) { val t = TextView(c).apply { text = n; textSize = 10f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(9), 0, dp(9))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(2), 0, dp(2), 0) } }; tabViews.add(t); tabs.addView(t) }
        col.addView(tabs)
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        fun show(i: Int) {
            sp.edit().putInt("fl_tab", i).apply(); stopLive(); body.removeAllViews()
            for (k in tabViews.indices) { val t = tabViews[k]; t.setTextColor(if (k == i) DK.dark else DK.dim); t.background = if (k == i) bg(DK.accent, 12, DK.accent) else bg(0x00000000, 12, 0x00000000) }
            when (i) { 0 -> pulse(body); 1 -> quick(body); 2 -> cool(body); 3 -> guard(body); 4 -> aim(body); else -> lab(body) }
        }
        for (k in tabViews.indices) tabViews[k].setOnClickListener { show(k) }
        reshow = { show(sp.getInt("fl_tab", 0).coerceIn(0, 5)) }
        val sc = MaxScroll(c, (c.resources.displayMetrics.heightPixels * 0.5f).toInt()); sc.addView(body); col.addView(sc)
        col.addView(tv("NACHT X  ·  Made by @UnoxidizedX", 9f, DK.dim).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        reshow()
        return col
    }

    // ---------- PULSE ----------
    private fun pulse(b: LinearLayout) {
        val gc = GaugeView(c); val gr = GaugeView(c); val gt = GaugeView(c)
        gc.accent = DK.accent; gr.accent = DK.ok; gt.accent = DK.warn; gc.small = "CPU"; gr.small = "RAM"; gt.small = "TEMP"
        for (g in listOf(gc, gr, gt)) g.layoutParams = LinearLayout.LayoutParams(0, dp(98), 1f)
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }; r.addView(gc); r.addView(gr); r.addView(gt); b.addView(r)
        val info = note(""); b.addView(info)
        val tip = tv("", 11f).apply { setPadding(dp(10), dp(8), dp(10), dp(8)); background = bg(DK.card, 12, DK.line); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(6)) } }; b.addView(tip)
        b.addView(toggle("Live HUD", "FPS and temperature pill on screen", "hud_on") { on ->
            if (on) { val ok = DeckOverlays.hudOn(c, sp, ui); if (!ok) say("Allow 'Display over other apps' for NACHT X first."); ok } else { DeckOverlays.hudOff(c); true } })
        b.addView(note("FPS needs root or Shizuku and the game running. Temperature is the battery sensor."))
        val upd = object : Runnable { override fun run() {
            val s = Sys.snap(c); gr.pct = s.ramPct.toFloat(); gr.big = "${s.ramPct}%"
            val t = s.tempC
            if (t != null) { gt.pct = (t / 50f * 100f).coerceIn(0f, 100f); gt.big = String.format("%.0f°", t) } else { gt.pct = 0f; gt.big = "--" }
            info.text = "Refresh ${s.hz} Hz  ·  Battery " + (if (s.batt < 0) "n/a" else "${s.batt}%") + (if (s.charging) " ⚡" else "") + "  ·  RAM ${s.ramMb} MB"
            tip.text = if (s.ramPct >= 85) "RAM is high. Try PURGE RAM in the Lab tab." else if (t != null && t >= 41f) "The phone is hot. Open the Cool tab." else "All good. Everything looks healthy."
            gr.invalidate(); gt.invalidate()
            if (Sh.granted()) thread { val cp = Sys.cpu(); ui.post { if (cp != null) { gc.pct = cp.toFloat(); gc.big = "$cp%" } else { gc.big = "…" }; gc.invalidate() } } else { gc.big = "--"; gc.invalidate() }
            ui.postDelayed(this, 1500L) } }
        live = upd; upd.run()
    }

    // ---------- QUICK ----------
    private fun quick(b: LinearLayout) {
        b.addView(label("QUICK ACCESS"))
        b.addView(toggle("Steady refresh rate", "Lock the screen at $maxHz Hz", "q_hz") { on -> if (on) shell(hzCmd(maxHz), "Refresh locked at $maxHz Hz") else shell(hzCmd(null), "Refresh rate released") })
        val items: List<Pair<String, () -> Unit>> = listOf("Purge RAM" to { shell("am kill-all", "Background apps closed"); Unit }, "Game" to { launchTarget() }, "Home" to { c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() },
            "Wi-Fi" to { open(Settings.ACTION_WIFI_SETTINGS) }, "Bluetooth" to { open(Settings.ACTION_BLUETOOTH_SETTINGS) }, "Airplane" to { open(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
            "Display" to { open(Settings.ACTION_DISPLAY_SETTINGS) }, "Sound" to { open(Settings.ACTION_SOUND_SETTINGS) }, "Battery" to { open(Settings.ACTION_BATTERY_SAVER_SETTINGS) })
        for (rw in items.chunked(3)) { val views = ArrayList<View>(); for ((n, f) in rw) views.add(chip(n) { f() }); b.addView(row(*views.toTypedArray())) }
        b.addView(label("PANEL TRANSPARENCY"))
        b.addView(SeekBar(c).apply { max = 100; progress = (((sp.getFloat("fl_alpha", 1f) - 0.4f) / 0.6f) * 100).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { val a = 0.4f + 0.6f * p / 100f; setAlpha(a); sp.edit().putFloat("fl_alpha", a).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) })
    }

    // ---------- COOL ----------
    private fun cool(b: LinearLayout) {
        val big = tv("--", 42f, DK.text, true).apply { gravity = Gravity.CENTER }; val st = tv("", 12f, DK.dim).apply { gravity = Gravity.CENTER }
        val card = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(14), dp(12), dp(14)); background = bg(DK.card, 16, DK.line); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) } }
        card.addView(big); card.addView(st); card.addView(tv("Battery sensor: the closest reading Android shares", 9f, DK.dim).apply { gravity = Gravity.CENTER }); b.addView(card)
        val upd = object : Runnable { override fun run() {
            val t = Sys.snap(c).tempC
            if (t == null) { big.text = "--"; st.text = "No temperature sensor available" }
            else { big.text = String.format("%.1f°C", t); big.setTextColor(if (t >= 41f) DK.bad else if (t >= 37f) DK.warn else DK.ok); st.text = if (t >= 41f) "Hot. Pick COOL or run COOLDOWN in the Lab tab." else if (t >= 37f) "Warm. Fine for most games." else "Cool. Plenty of headroom." }
            ui.postDelayed(this, 2000L) } }
        live = upd; upd.run()
        b.addView(label("COOLING PROFILE")); val cur = sp.getInt("fl_prof", 1).coerceIn(0, 2)
        val sel = fun(i: Int, msg: String, cmd: String) { if (needShell()) { sp.edit().putInt("fl_prof", i).apply(); shell(cmd, msg); reshow() } }
        b.addView(row(chip("COOL", cur == 0) { sel(0, "Cool profile: $minHz Hz and battery saver", "am kill-all; ${hzCmd(minHz)}; settings put global low_power 1") },
            chip("BALANCED", cur == 1) { sel(1, "Balanced: system defaults", "${hzCmd(null)}; settings put global low_power 0") },
            chip("PERFORMANCE", cur == 2) { sel(2, "Performance profile: $maxHz Hz", "${hzCmd(maxHz)}; settings put global low_power 0") }))
        b.addView(note("COOL: closes background apps, lowers the refresh rate and turns on battery saver.\nBALANCED: Android defaults.\nPERFORMANCE: highest refresh rate, no battery saver."))
    }

    // ---------- GUARD ----------
    private fun guard(b: LinearLayout) {
        b.addView(label("ANTI-INTERFERENCE"))
        b.addView(toggle("Quiet Mode", "Silence notifications and calls", "g_quiet") { on -> shell("cmd notification set_dnd " + (if (on) "priority" else "off"), if (on) "Quiet Mode on" else "Quiet Mode off") })
        b.addView(toggle("No pop-ups", "Hide heads-up banners while you play", "g_heads") { on -> shell("settings put global heads_up_notifications_enabled " + (if (on) "0" else "1"), if (on) "Pop-ups hidden" else "Pop-ups back") })
        b.addView(toggle("Match Mode", "Quiet + purge RAM + steady refresh", "g_match", "matchMode") { on ->
            if (on) shell("cmd notification set_dnd priority; am kill-all; ${hzCmd(maxHz)}", "Match Mode on") else shell("cmd notification set_dnd off; ${hzCmd(null)}", "Match Mode off") })
        b.addView(note("These use root or Shizuku. ON/OFF shows what NACHT X last set."))
    }

    // ---------- AIM ----------
    private fun aim(b: LinearLayout) {
        b.addView(label("CROSSHAIR OVERLAY"))
        b.addView(toggle("Crosshair", "A small mark in the centre of the screen", "cx_on") { on ->
            if (on) { val ok = DeckOverlays.crossOn(c, sp); if (!ok) say("Allow 'Display over other apps' for NACHT X first."); ok } else { DeckOverlays.crossOff(c); true } })
        val st = sp.getInt("cx_style", 0)
        b.addView(row(chip("DOT", st == 0) { sp.edit().putInt("cx_style", 0).apply(); DeckOverlays.crossStyle(sp); reshow() }, chip("CROSS", st == 1) { sp.edit().putInt("cx_style", 1).apply(); DeckOverlays.crossStyle(sp); reshow() }, chip("RING", st == 2) { sp.edit().putInt("cx_style", 2).apply(); DeckOverlays.crossStyle(sp); reshow() }))
        b.addView(label("SIZE"))
        b.addView(SeekBar(c).apply { max = 40; progress = (sp.getInt("cx_size", 18) - 8).coerceIn(0, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { sp.edit().putInt("cx_size", p + 8).apply(); DeckOverlays.crossStyle(sp) }
                override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) })
        b.addView(label("COLOUR"))
        val cols = intArrayOf(0xFFFF3B3B.toInt(), 0xFF39E27D.toInt(), 0xFF3FD0FF.toInt(), 0xFFFFD43B.toInt(), 0xFFFFFFFF.toInt()); val cur = sp.getInt("cx_color", cols[0]); val sw = ArrayList<View>()
        for (col in cols) sw.add(TextView(c).apply { text = if (col == cur) "✓" else ""; gravity = Gravity.CENTER; setTextColor(DK.dark); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(12)); background = bg(col, 12, DK.line)
            setOnClickListener { sp.edit().putInt("cx_color", col).apply(); DeckOverlays.crossStyle(sp); reshow() } })
        b.addView(row(*sw.toTypedArray()))
        b.addView(note("Heads up: some online games treat any overlay as cheating and can ban accounts. Check the game's rules before using it."))
    }

    // ---------- LAB ----------
    private fun lab(b: LinearLayout) {
        b.addView(label("ONE-TAP PRESETS"))
        b.addView(row(
            chip("MAX FPS  ★") { if (!premium("autoBoost")) say("Max FPS is Premium. Open NACHT X > Premium.") else if (shell("am kill-all; cmd notification set_dnd priority; ${hzCmd(maxHz)}; settings put global low_power 0", "Max FPS preset on")) ui.postDelayed({ launchTarget() }, 1200L) },
            chip("PURGE RAM") { shell("am kill-all", "Background apps closed") }))
        b.addView(row(
            chip("COOLDOWN") { shell("am kill-all; ${hzCmd(minHz)}; settings put global low_power 1", "Cooldown: apps closed, $minHz Hz, battery saver") },
            chip("RESET ALL") {
                DeckOverlays.removeAll(c); sp.edit().putBoolean("cx_on", false).putBoolean("hud_on", false).putBoolean("q_hz", false).putBoolean("g_quiet", false).putBoolean("g_heads", false).putBoolean("g_match", false).putInt("fl_prof", 1).apply()
                shell("${hzCmd(null)}; settings put global low_power 0; cmd notification set_dnd off; settings put global heads_up_notifications_enabled 1", "Everything is back to normal"); reshow() }))
        b.addView(label("GAME TARGET"))
        val pkg = sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG
        b.addView(row(tv(LibStore.name(sp, pkg, labelOf(c, pkg)), 12f, DK.text, true).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }, chip("LAUNCH") { launchTarget() }))
        b.addView(note("Presets use root or Shizuku and only change normal Android settings. Nothing here touches game files."))
    }
}
