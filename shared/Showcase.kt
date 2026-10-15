package com.unoxidized.shared
object Showcase {
    const val BAR = "━━━━━━━━━━━━━━━━━━━━"
    val X = listOf("Game library with every installed app, favorites and recents", "Floating panel that stays on top of your game", "Engine Activation: root, Shizuku or Basic Mode",
        "Premium graphics tuner: downscale, FPS target, frame pacing", "Auto Boost engine that reads your hardware", "Performance modes and touch optimization", "9-card live engine monitor",
        "Session tracking with real FPS when your phone allows", "Restore points with one-tap recovery", "Free 3-day access and Premium keys", "Built-in Information Center and guides", "Owner-controlled announcements and branding")
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
