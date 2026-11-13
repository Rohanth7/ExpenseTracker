package com.example.expensetracker.widget

import androidx.compose.ui.graphics.Color

object WidgetTheme {
    val Deep         = Color(0xFF11153A)   // background
    val Paper        = Color(0xFF1A1F52)   // card surface (matches app)
    val Ink          = Color(0xFFF4EEDD)   // primary text
    val Muted        = Color(0xFF8E94C6)   // secondary text
    val Hairline     = Color(0xFF2B3178)
    val HairlineFaint= Color(0xFF22285F)   // donut track

    val Gold         = Color(0xFFF6A723)   // primary accent, on track
    val GoldInk      = Color(0xFFFFD98A)   // pale gold text
    val GoldSoftBg   = Color(0x26F6A723)   // 15% gold, pill bg

    val Pink         = Color(0xFFF2668C)   // over-budget accent
    val PinkInk      = Color(0xFFFFB3C7)   // over-budget pill text
    val PinkSoftBg   = Color(0x2EF2668C)   // 18% pink, pill bg

    val Orange       = Color(0xFFF08A3C)   // watch-pace accent
    val OrangeInk    = Color(0xFFFFC79A)   // watch-pace pill text
    val OrangeSoftBg = Color(0x26F08A3C)   // 15% orange, pill bg
}
