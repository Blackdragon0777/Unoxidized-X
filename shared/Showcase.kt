package com.unoxidized.shared
object Showcase {
    const val BAR = "━━━━━━━━━━━━━━━━━━━━"
    val X = listOf("Shizuku-authorized access gate", "Performance score from real RAM, heat, battery and storage", "4 CODM profiles plus unlimited custom profiles", "Render scale, FPS target and mode through Android Game Mode",
        "Boost and launch with live step-by-step checks", "Automatic CODM launch detection", "Session tracking with real FPS and frame-time (when your phone allows)", "Before and after comparison from real sessions",
        "Live monitor: CPU, RAM, battery and heat", "Import and export profile codes", "History, tools and one-tap restore defaults", "3-day key system with role-based access", "Remote announcements and update alerts", "Auto Boost engine that reads your hardware", "Target game picker with app icon", "Graphics downscaler (game only or whole screen) with a safety timer", "Performance modes and touch optimization", "9-card engine monitor", "Restore points with one-tap recovery", "Pull-to-refresh and swipe gestures")
    val G = listOf("Free 3-day key for every device ID", "Fills in this phone's ID automatically", "Make keys for other devices", "One-tap send to NACHT X", "My Keys vault with live countdowns", "Copy or share keys", "Pull-to-refresh and swipe gestures")
    fun prices(d: Control.Data?) = d?.prices?.takeIf { it.isNotEmpty() } ?: listOf("FREE | 3 days | 3-day key from NACHT X Generator", "ASK THE OWNER | Longer or lifetime access")
    fun payments(d: Control.Data?) = d?.payments?.takeIf { it.isNotEmpty() } ?: listOf("Ask the owner")
    fun contact(d: Control.Data?) = d?.contact?.takeIf { it.isNotBlank() } ?: "@UnoxidizedX"
    fun title(ver: String, mode: Int) = when (mode) { 0 -> "NACHT X V$ver"; 1 -> "NACHT X GENERATOR V$ver"; else -> "NACHT X + GENERATOR V$ver" }
    fun text(ver: String, mode: Int, d: Control.Data?): String {
        val sb = StringBuilder(); sb.append(title(ver, mode)).append("\n\n                  👀\n\n")
        if (mode != 1) { sb.append("NACHT X FEATURES\n\n"); X.forEach { sb.append(" * ").append(it.uppercase()).append("\n") }; sb.append("\n") }
        if (mode != 0) { sb.append("NACHT X GENERATOR FEATURES\n\n"); G.forEach { sb.append(" * ").append(it.uppercase()).append("\n") }; sb.append("\n") }
        sb.append(BAR).append("\nPRICELIST\n"); prices(d).forEach { sb.append("-> ").append(it.uppercase()).append("\n") }
        sb.append(BAR).append("\nPAYMENT METHODS\n"); payments(d).forEach { sb.append(" * ").append(it.uppercase()).append("\n") }
        sb.append("   ").append(BAR).append("\n   WANT TO BUY?\n   BUY KEY : ").append(contact(d)); return sb.toString()
    }
}
