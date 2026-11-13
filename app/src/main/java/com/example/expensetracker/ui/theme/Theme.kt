package com.example.expensetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary              = Gold,
    onPrimary            = Deep,
    primaryContainer     = GoldDeep,
    onPrimaryContainer   = GoldInk,
    secondary            = Sky,
    onSecondary          = Deep,
    secondaryContainer   = SkySoft,
    onSecondaryContainer = Sky,
    tertiary             = Pink,
    onTertiary           = Canvas,
    tertiaryContainer    = PinkSoft,
    onTertiaryContainer  = Pink,
    background           = Canvas,
    onBackground         = Ink,
    surface              = Paper,
    onSurface            = Ink,
    surfaceVariant       = Deep,
    onSurfaceVariant     = Muted,
    outline              = Hairline,
    outlineVariant       = HairlineSoft,
    error                = Pink,
    onError              = Canvas,
    errorContainer       = PinkSoft,
    onErrorContainer     = Pink,
    inverseSurface       = Ink,
    inverseOnSurface     = Canvas,
)

@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
