package com.unoxidized.x
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import com.unoxidized.shared.Lic
import com.unoxidized.shared.CtlState

class MaxScroll(c: Context, private val maxH: Int) : ScrollView(c) { override fun onMeasure(w: Int, h: Int) { super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST)) } }

/** The floating NACHT X bubble and panel. Plain Android views so it works as a system overlay. */
class FloatingService : Service() {
    companion object {
        @Volatile var running = false
        fun start(c: Context) { val i = Intent(c, FloatingService::class.java); if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i) }
        fun refresh(c: Context) { if (running) c.startService(Intent(c, FloatingService::class.java).setAction("refresh")) }
        fun stop(c: Context) { c.stopService(Intent(c, FloatingService::class.java)) }
    }
    private var wm: WindowManager? = null; private var root: LinearLayout? = null; private var lp: WindowManager.LayoutParams? = null
    private var panel: View? = null; private var expanded = false; private lateinit var sp: SharedPreferences; private var pendingMove = false
    private val ui = Handler(Looper.getMainLooper())
    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun say(t: String) { ui.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() } }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        sp = getSharedPreferences("ux", 0); startFg()
        if (!Settings.canDrawOverlays(this)) { say("Allow 'Display over other apps' for NACHT X first."); stopSelf(); return START_NOT_STICKY }
        if (root == null && !build()) { stopSelf(); return START_NOT_STICKY }
        if (i?.action == "refresh") applyPrefs()
        running = true; return START_STICKY
    }
    private fun startFg() {
        val ch = "ux_float"
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(ch, "Floating panel", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        val n = b.setContentTitle("NACHT X").setContentText("Floating panel is on. Tap to open NACHT X.").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(9, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(9, n)
    }
    private fun build(): Boolean {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = sp.getInt("fl_x", dp(8)); p.y = sp.getInt("fl_y", dp(160)); lp = p
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root = r
        val b = ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)); elevation = dp(6).toFloat()
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) } }; clipToOutline = true }
        r.addView(b); drag(b, true)
        return try { wm!!.addView(r, p); applyPrefs(); true } catch (e: Exception) { say("Couldn't show the floating panel: ${e.message}"); root = null; false }
    }
    private fun applyPrefs() { root?.alpha = sp.getFloat("fl_alpha", 1f).coerceIn(0.4f, 1f); if (expanded) { collapse(); expand() } }
    private fun rounded(color: Int, r: Int, stroke: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke) }
    private fun tv(t: String, size: Float, color: Int = 0xFFF5F3FF.toInt(), bold: Boolean = false) = TextView(this).apply { text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun chip(t: String, onClick: () -> Unit) = TextView(this).apply { text = t; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(0xFF1E232D.toInt(), 10, 0x889AA5B8.toInt()); setOnClickListener { onClick() } }

    private fun drag(v: View, bubbleToggle: Boolean) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            val p = lp ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> { val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt(); if (Math.abs(mx) > dp(4) || Math.abs(my) > dp(4)) moved = true
                    p.x = sx + mx; p.y = sy + my; clamp(p)
                    if (!pendingMove) { pendingMove = true; v.postOnAnimation { pendingMove = false; try { wm?.updateViewLayout(root, p) } catch (x: Exception) {} } }
                    true }
                MotionEvent.ACTION_UP -> { if (!moved && bubbleToggle) toggle() else { snap(); save() }; true }
                else -> false
            }
        }
    }
    private fun clamp(p: WindowManager.LayoutParams) { val m = resources.displayMetrics; val w = root?.width ?: dp(52); val h = root?.height ?: dp(52)
        p.x = p.x.coerceIn(0, maxOf(0, m.widthPixels - w)); p.y = p.y.coerceIn(0, maxOf(0, m.heightPixels - h - dp(24))) }
    private fun save() { lp?.let { sp.edit().putInt("fl_x", it.x).putInt("fl_y", it.y).apply() } }
    private fun snap() {
        val p = lp ?: return; if (!sp.getBoolean("fl_snap", true)) return
        val m = resources.displayMetrics; val w = root?.width ?: dp(52); val target = if (p.x + w / 2 < m.widthPixels / 2) 0 else maxOf(0, m.widthPixels - w)
        ValueAnimator.ofInt(p.x, target).apply { duration = 180; addUpdateListener { p.x = it.animatedValue as Int; try { wm?.updateViewLayout(root, p) } catch (x: Exception) {} }; start() }
    }
    private fun toggle() { if (expanded) collapse() else expand() }
    private fun expand() {
        val r = root ?: return; val pv = buildPanel(); panel = pv; r.addView(pv); expanded = true; pv.alpha = 0f; pv.animate().alpha(1f).setDuration(160).start()
        r.post { lp?.let { clamp(it); try { wm?.updateViewLayout(r, it) } catch (x: Exception) {} } }
    }
    private fun collapse() { panel?.let { root?.removeView(it) }; panel = null; expanded = false; root?.post { lp?.let { try { wm?.updateViewLayout(root, it) } catch (x: Exception) {} } } }

    private fun buildPanel(): View = DeckUi(this, sp, ui, { t -> say(t) }, { collapse() }, { stopSelf() }, { v -> drag(v, false) }, { a -> root?.alpha = a }).build()
    private fun section(body: LinearLayout, title: String, pkgs: List<String>) {
        body.addView(tv(title, 10f, 0xFF8B8798.toInt(), true).apply { setPadding(0, dp(10), 0, dp(4)) })
        if (pkgs.isEmpty()) { body.addView(tv("Nothing yet. Add favorites in the NACHT X library.", 11f, 0xFF8B8798.toInt())); return }
        pkgs.take(8).chunked(4).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { pkg ->
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4)); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                val ic = ImageView(this).apply { try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { setImageResource(R.drawable.logo) }; layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }
                cell.addView(ic); cell.addView(tv(LibStore.name(sp, pkg, labelOf(this, pkg)).take(8), 9f, 0xFFCCCCCC.toInt()).apply { gravity = Gravity.CENTER; maxLines = 1 })
                cell.setOnClickListener { val e = launchApp(this, sp, pkg); if (e != null) say(e) else collapse() }; r.addView(cell)
            }
            repeat(4 - row.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }; body.addView(r)
        }
    }
    private fun open(action: String) { try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() } catch (e: Exception) { say("Not available on this phone") } }
    private fun openSelf() { packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; collapse() }
    private fun cleanRam() { if (!Sh.granted()) { say("Needs root or Shizuku. Open NACHT X and activate the engine."); return }
        thread { val r = Sh.run("am kill-all"); say(if (bad(r)) r.trim().take(60) else "Background apps closed") } }
    private fun launchTarget() { val e = launchApp(this, sp, sp.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG); if (e != null) say(e) else collapse() }
    private fun dnd(on: Boolean) { if (!Sh.granted()) { say("Needs root or Shizuku. Open NACHT X and activate the engine."); return }
        thread { val r = Sh.run("cmd notification set_dnd " + (if (on) "priority" else "off")); say(if (bad(r)) r.trim().take(60) else if (on) "Do Not Disturb on" else "Do Not Disturb off") } }
    /** Premium = an owner-signed Premium key valid on this device, or the owner marked the feature as free for everyone. */
    private fun isPremium(feature: String): Boolean {
        val r = Lic.check(Lic.deviceId(this), sp.getString("k", "") ?: "")
        val tier = if (feature.isEmpty()) "premium" else (CtlState.data?.tiers?.get(feature) ?: "premium")
        return r.ok && (r.role != "F" || tier != "premium")
    }
    /** One tap: close background apps, Do Not Disturb on, launch your game. Needs root or Shizuku. Premium. */
    private fun gameBoost() {
        if (!isPremium("autoBoost")) { say("Game Boost is Premium. Open NACHT X > Premium to upgrade."); return }
        if (!Sh.granted()) { say("Needs root or Shizuku. Open NACHT X and activate the engine."); return }
        thread { val a = Sh.run("am kill-all"); val b = Sh.run("cmd notification set_dnd priority")
            say(if (bad(a) && bad(b)) a.trim().take(60) else "Boost done: apps closed, DND on"); ui.post { launchTarget() } }
    }
    private fun goHome() { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); collapse() }
    private fun tools(body: LinearLayout) {
        val items: List<Pair<String, () -> Unit>> = listOf("Display" to { open(Settings.ACTION_DISPLAY_SETTINGS) }, "Sound" to { open(Settings.ACTION_SOUND_SETTINGS) }, "DND" to { open(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS) }, "Network" to { open(Settings.ACTION_WIRELESS_SETTINGS) },
            "Battery" to { open(Settings.ACTION_BATTERY_SAVER_SETTINGS) }, "Storage" to { open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }, "Clean RAM" to { cleanRam() }, "NACHT X" to { openSelf() }, "Game" to { launchTarget() }, "DND on" to { dnd(true) }, "DND off" to { dnd(false) }, "Home" to { goHome() },
            "Boost ★" to { gameBoost() }, "Wi-Fi" to { open(Settings.ACTION_WIFI_SETTINGS) }, "Bluetooth" to { open(Settings.ACTION_BLUETOOTH_SETTINGS) }, "Airplane" to { open(Settings.ACTION_AIRPLANE_MODE_SETTINGS) })
        items.chunked(4).forEach { row -> val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (n, f) -> r.addView(chip(n) { f() }.apply { textSize = 10f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) } }) }; body.addView(r) }
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); lp?.let { clamp(it); try { wm?.updateViewLayout(root, it) } catch (e: Exception) {} } }
    override fun onDestroy() { DeckOverlays.removeAll(this); try { root?.let { wm?.removeView(it) } } catch (e: Exception) {}; root = null; running = false; super.onDestroy() }
}
