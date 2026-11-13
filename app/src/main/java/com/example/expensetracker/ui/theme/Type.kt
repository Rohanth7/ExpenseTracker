package com.example.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.expensetracker.R

// Anek Latin is one variable font file. Android reads its weight axis from API 26; on 24 and 25
// every weight draws as the file's default (Medium), which stays readable.
@OptIn(ExperimentalTextApi::class)
private fun anek(weight: FontWeight) = Font(
    R.font.anek_latin,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(100f)),
)

val AnekLatin = FontFamily(
    anek(FontWeight.Normal),
    anek(FontWeight.Medium),
    anek(FontWeight.SemiBold),
    anek(FontWeight.Bold),
    anek(FontWeight.ExtraBold),
)

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = AnekLatin),
    displayMedium = Base.displayMedium.copy(fontFamily = AnekLatin),
    displaySmall = Base.displaySmall.copy(fontFamily = AnekLatin),
    headlineLarge = Base.headlineLarge.copy(fontFamily = AnekLatin),
    headlineMedium = Base.headlineMedium.copy(fontFamily = AnekLatin),
    headlineSmall = Base.headlineSmall.copy(fontFamily = AnekLatin),
    titleLarge = Base.titleLarge.copy(fontFamily = AnekLatin),
    titleMedium = Base.titleMedium.copy(fontFamily = AnekLatin),
    titleSmall = Base.titleSmall.copy(fontFamily = AnekLatin),
    bodyLarge = Base.bodyLarge.copy(fontFamily = AnekLatin),
    bodyMedium = Base.bodyMedium.copy(fontFamily = AnekLatin),
    bodySmall = Base.bodySmall.copy(fontFamily = AnekLatin),
    labelLarge = Base.labelLarge.copy(fontFamily = AnekLatin),
    labelMedium = Base.labelMedium.copy(fontFamily = AnekLatin),
    labelSmall = Base.labelSmall.copy(fontFamily = AnekLatin),
)
