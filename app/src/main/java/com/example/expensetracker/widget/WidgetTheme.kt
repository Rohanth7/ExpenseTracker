package com.example.expensetracker.widget

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.util.Locale

object WidgetTheme {
    val Deep         = Color(0xFF171511)   // background
    val Paper        = Color(0xFF2A2823)   // card surface (matches app)
    val Ink          = Color(0xFFF2EBD9)   // primary text
    val Muted        = Color(0xFFA09B8E)   // secondary text
    val Hairline     = Color(0x1AF4EFE1)   // 10% off-white
    val HairlineFaint= Color(0x14F4EFE1)   // 8% — donut track

    val Jade         = Color(0xFF7DC9A5)   // primary accent
    val JadeInk      = Color(0xFFC9EBD7)   // pale jade text
    val JadeSoftBg   = Color(0x267DD3BC)   // 15% jade — pill bg (rgba 125,211,188,0.15)

    val Coral        = Color(0xFFEE9A6E)   // over-budget accent
    val CoralInk     = Color(0xFFF5C5AC)   // over-budget pill text
    val CoralSoftBg  = Color(0x2EEE9A6E)   // 18% coral — pill bg

    val Amber        = Color(0xFFE5B84F)   // watch-pace accent
    val AmberInk     = Color(0xFFF2D9A1)   // watch-pace pill text
    val AmberSoftBg  = Color(0x26E5B84F)   // 15% amber — pill bg
}

fun fmtINR(n: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
        .apply { maximumFractionDigits = 0 }
        .format(n.toLong())
