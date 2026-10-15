package com.unoxidized.owner
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unoxidized.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Prepared(val bytes: ByteArray, val bmp: Bitmap)
/** Validates an image the owner picked: must decode as a picture, is shrunk and compressed, and stays under the upload limit. */
fun prepareImage(c: Context, uri: Uri, maxDim: Int, png: Boolean): Prepared? {
    return try {
        val raw = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (raw.size > 8_000_000) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / s > maxDim * 2 || o.outHeight / s > maxDim * 2) s *= 2
        val b0 = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val k = minOf(1f, maxDim.toFloat() / maxOf(b0.width, b0.height))
        val bmp = if (k < 1f) Bitmap.createScaledBitmap(b0, (b0.width * k).toInt(), (b0.height * k).toInt(), true) else b0
        var q = 88; var out: ByteArray
        while (true) { val bo = java.io.ByteArrayOutputStream(); bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, q, bo); out = bo.toByteArray(); if (out.size <= 1_500_000 || png || q <= 30) break; q -= 10 }
        if (out.size > 1_900_000) null else Prepared(out, bmp)
    } catch (e: Exception) { null }
}
fun rawUrl(path: String) = "https://raw.githubusercontent.com/${Control.OWNER}/${Control.REPO}/${Control.BRANCH}/$path?v=${System.currentTimeMillis()}"
suspend fun uploadBrandImage(os: OS, uri: Uri, path: String, maxDim: Int, png: Boolean): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
    val r = prepareImage(os.act, uri, maxDim, png)
    if (r == null) Pair<String?, Bitmap?>("That image can't be used (too large or unreadable).", null)
    else { val err = GH.putFile(os.sp, path, r.bytes, "Update branding image"); if (err != null) Pair<String?, Bitmap?>(err, null) else Pair<String?, Bitmap?>(null, r.bmp) }
}
fun hexOf(s: String): Color? = if (Regex("#[0-9A-Fa-f]{6}").matches(s.trim())) Color(android.graphics.Color.parseColor(s.trim())) else null

@Composable fun BrandingPage(os: OS) {
    val e = os.ce; val toast = LocalToast.current; val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var logoPrev by remember { mutableStateOf<ImageBitmap?>(null) }; var bgPrev by remember { mutableStateOf<ImageBitmap?>(null) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch { busy = true
        val (err, bmp) = uploadBrandImage(os, uri, "branding/logo.png", 1024, true)
        if (err == null && bmp != null) { e.bLogo = rawUrl("branding/logo.png"); logoPrev = bmp.asImageBitmap(); toast("Logo uploaded. Publish to apply.") } else toast((err ?: "Upload failed").take(70)); busy = false } }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch { busy = true
        val (err, bmp) = uploadBrandImage(os, uri, "branding/bg.jpg", 1600, false)
        if (err == null && bmp != null) { e.bBg = rawUrl("branding/bg.jpg"); bgPrev = bmp.asImageBitmap(); toast("Background uploaded. Publish to apply.") } else toast((err ?: "Upload failed").take(70)); busy = false } }
    val accent = hexOf(e.bAccent); val glow = hexOf(e.bGlow)
    H("App branding", "Change how NACHT X looks for every user, no new APK needed.")
    Glass { Cap("Name and welcome text")
        OutlinedTextField(e.bTitle, { e.bTitle = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("In-app title (default NACHT X)") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
        OutlinedTextField(e.bWelcome, { e.bWelcome = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Welcome line on Home") }, shape = RoundedCornerShape(14.dp), colors = fieldColors(), maxLines = 3) }
    Glass { Cap("Colors")
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(e.bAccent, { e.bAccent = it.take(7) }, Modifier.weight(1f), label = { Text("Accent #RRGGBB") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors()); Spacer(Modifier.width(10.dp)); Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent ?: Color(0xFF6D28D9))) }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(e.bGlow, { e.bGlow = it.take(7) }, Modifier.weight(1f), label = { Text("Glow #RRGGBB") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors()); Spacer(Modifier.width(10.dp)); Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(glow ?: Color(0xFFA855F7))) }
        Text("Leave a color empty to use the default purple.", color = Dim, fontSize = 11.sp) }
    Glass { Cap("Logo and background")
        Text("Images are checked, shrunk and uploaded to your public repo. Logo max 1024px, background max 1600px.", color = Dim, fontSize = 12.sp)
        logoPrev?.let { Image(it, null, Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
        bgPrev?.let { Image(it, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
        Text("Logo: " + (if (e.bLogo.isBlank()) "default" else "custom set"), fontSize = 13.sp); Text("Background: " + (if (e.bBg.isBlank()) "default" else "custom set"), fontSize = 13.sp)
        Btn("PICK LOGO", false, !busy, busy) { pickLogo.launch("image/*") }; Btn("PICK BACKGROUND", false, !busy, busy) { pickBg.launch("image/*") }
        Btn("REMOVE LOGO AND BACKGROUND", false) { e.bLogo = ""; e.bBg = ""; logoPrev = null; bgPrev = null; toast("Cleared. Publish to apply.") } }
    Btn("PUBLISH BRANDING TO ALL USERS", true, !busy, busy) { confirm = true }
    Btn("RESET TO DEFAULT BRANDING", false) { e.bTitle = ""; e.bWelcome = ""; e.bAccent = ""; e.bGlow = ""; e.bLogo = ""; e.bBg = ""; logoPrev = null; bgPrev = null; toast("Reset. Publish to apply.") }
    Text("The app name and launcher icon are part of the APK. Changing them needs a new build. Everything above updates live.", color = Dim, fontSize = 11.sp)
    if (confirm) Confirm("Publish branding?", "Every user's NACHT X will change the next time it checks in. You can reset it any time.", { confirm = false
        scope.launch { busy = true; val err = withContext(Dispatchers.IO) { GH.publish(os.sp, e.json()) }
            if (err == null) { OLog.add(os.sp, "Published branding"); toast("Branding published") } else toast(err.take(70)); busy = false } }, { confirm = false })
}
