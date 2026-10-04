package com.unoxidized.shared
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ln = Color(0xFF2A2A2A); val Dim = Color(0xFF8C8C8C); val Ok = Color(0xFF3DDC84); val Bad = Color(0xFFFF6B6B)
val WhiteGrad = Brush.horizontalGradient(listOf(Color.White, Color(0xFFB8B8B8)))

@Composable fun Glass(m: Modifier = Modifier, c: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(22.dp)
    Column(m.fillMaxWidth().clip(sh).background(Brush.verticalGradient(listOf(Color(0xFF181818), Color(0xFF0B0B0B)))).border(1.dp, Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF1A1A1A))), sh).padding(18.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = c)
}
@Composable fun H(t: String, s: String) = Column { Text(t, fontSize = 24.sp, fontWeight = FontWeight.Black); Text(s, color = Dim, fontSize = 13.sp) }
@Composable fun Cap(t: String) = Text(t.uppercase(), color = Dim, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
@Composable fun Btn(t: String, primary: Boolean = true, onClick: () -> Unit) {
    val hf = LocalHapticFeedback.current; val sh = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(54.dp).clip(sh).then(if (primary) Modifier.background(WhiteGrad) else Modifier.background(Color(0xFF111111)).border(1.dp, Ln, sh))
        .clickable { hf.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, Alignment.Center) {
        Text(t, color = if (primary) Color.Black else Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
    }
}
@Composable fun MiniBtn(t: String, onClick: () -> Unit) = Text(t, Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF222222)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
@Composable fun CopyChip(text: String) { val cb = LocalClipboardManager.current; MiniBtn("COPY") { cb.setText(AnnotatedString(text)) } }
@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Ln, cursorColor = Color.White)
@Composable fun LogoImg(p: Painter, sz: Int) = Image(p, null, Modifier.size(sz.dp).clip(CircleShape))
@Composable fun Watermark() { val c = LocalContext.current
    Text("Made by @UnoxidizedX  ·  Telegram", Modifier.clickable { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/UnoxidizedX")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }.padding(8.dp), color = Dim, fontSize = 11.sp, letterSpacing = 1.sp) }
