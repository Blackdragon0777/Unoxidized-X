package com.unoxidized.x
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.unoxidized.shared.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Applies owner branding from the control file. Images must come from the owner's raw GitHub files, are size-limited and are only decoded as pictures. */
object Branding {
    private fun col(s: String): Color? = if (Regex("#[0-9A-Fa-f]{6}").matches(s.trim())) Color(android.graphics.Color.parseColor(s.trim())) else null
    private fun download(url: String): ByteArray? {
        if (!url.startsWith("https://raw.githubusercontent.com/")) return null
        return try { val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 5000; c.readTimeout = 8000
            if (c.responseCode != 200) null else { val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var total = 0; val ins = c.inputStream
                try { while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > 2_000_000) break; out.write(buf, 0, n) } } finally { ins.close() }
                if (total > 2_000_000) null else out.toByteArray() } } catch (e: Exception) { null }
    }
    private fun decode(b: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(b, 0, b.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 4000 || o.outHeight > 6000) return null
        var s = 1; while (o.outWidth / s > 1400 || o.outHeight / s > 2600) s *= 2
        return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
    private fun loadFile(f: File): ImageBitmap? = try { if (f.exists()) decode(f.readBytes())?.asImageBitmap() else null } catch (e: Exception) { null }
    private fun applyText(d: Control.Data) {
        Brand.title = d.bTitle.trim().take(24).ifBlank { "NACHT X" }; Brand.welcome = d.bWelcome.trim().take(120)
        Brand.acc = col(d.bAccent) ?: Color(0xFF6D28D9); Brand.glow = col(d.bGlow) ?: Color(0xFFA855F7)
    }
    private fun img(c: Context, name: String, url: String): ImageBitmap? {
        val sp = c.getSharedPreferences("ux", 0); val f = File(c.filesDir, "brand_$name.img")
        if (url.isBlank()) { f.delete(); sp.edit().remove("brand_${name}_url").apply(); return null }
        if (sp.getString("brand_${name}_url", "") != url || !f.exists()) { val b = download(url); if (b == null || decode(b) == null) return loadFile(f); f.writeBytes(b); sp.edit().putString("brand_${name}_url", url).apply() }
        return loadFile(f)
    }
    fun apply(c: Context, d: Control.Data?) { if (d == null) return; applyText(d); Brand.logo = img(c, "logo", d.bLogo); Brand.bg = img(c, "bg", d.bBg) }
    fun loadCached(c: Context) { Brand.logo = loadFile(File(c.filesDir, "brand_logo.img")); Brand.bg = loadFile(File(c.filesDir, "brand_bg.img"))
        Control.parse(c.getSharedPreferences("ux", 0).getString("ctl_raw", "") ?: "")?.let { applyText(it) } }
}
@Composable fun BrandLogo(sz: Int) { val b = Brand.logo
    if (b != null) Image(b, null, Modifier.size(sz.dp).clip(CircleShape).border(1.dp, Color(0xFF555555), CircleShape), contentScale = ContentScale.Crop) else LogoImg(painterResource(R.drawable.logo), sz) }
