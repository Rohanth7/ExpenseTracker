package com.example.expensetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary              = Jade,
    onPrimary            = Deep,
    primaryContainer     = JadeDeep,
    onPrimaryContainer   = JadeInk,
    secondary            = Amber,
    onSecondary          = Deep,
    secondaryContainer   = AmberSoft,
    onSecondaryContainer = Amber,
    tertiary             = Coral,
    onTertiary           = Canvas,
    tertiaryContainer    = CoralSoft,
    onTertiaryContainer  = Coral,
    background           = Canvas,
    onBackground         = Ink,
    surface              = Paper,
    onSurface            = Ink,
    surfaceVariant       = Deep,
    onSurfaceVariant     = Muted,
    outline              = Hairline,
    outlineVariant       = HairlineSoft,
    error                = Coral,
    onError              = Canvas,
    errorContainer       = CoralSoft,
    onErrorContainer     = Coral,
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
