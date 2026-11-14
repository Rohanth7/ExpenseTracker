package com.example.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// ── Marigold palette: indigo night, marigold gold, kumkum pink ─────────────

// Surfaces (indigo scale)
val Canvas      = Color(0xFF11153A)   // page background
val Paper       = Color(0xFF1A1F52)   // card surface
val Deep        = Color(0xFF0C0F2B)   // hero / CTA extra-dark surface; text on gold

// Text
val Ink         = Color(0xFFF4EEDD)   // primary text, warm cream
val InkSoft     = Color(0xFFD9D6E6)   // secondary text on hero
val Muted       = Color(0xFF8E94C6)   // label / secondary text
val Whisper     = Color(0xFF5A609A)   // placeholder text
val Field       = Color(0xFF232A66)   // input fields, secondary buttons, cards inside sheets

// Hairlines
val Hairline         = Color(0xFF2B3178)
val HairlineSoft     = Color(0xFF22285F)

// Primary: marigold gold
val Gold        = Color(0xFFF6A723)   // primary action (FAB, CTA, confirm), money left
val GoldDeep    = Color(0xFF5A3F12)   // gold-tinted dark surface
val GoldSoft    = Color(0xFF40364A)   // subtle gold background
val GoldInk     = Color(0xFFFFD98A)   // pale gold text on dark

// Pink: errors, pending, over-budget
val Pink        = Color(0xFFF2668C)
val PinkSoft    = Color(0xFF3F1D45)   // dark pink background

// Sky: secondary highlights (bills due, busiest month)
val Sky         = Color(0xFF7FB6FF)
val SkySoft     = Color(0xFF1F2F66)   // dark sky background

// Mint: income and good news
val Mint        = Color(0xFF7FD8B0)
val MintSoft    = Color(0xFF1C3A52)   // dark mint background

// Budget tones (with Gold for "at the alert level" and Pink for "over")
val Leaf        = Color(0xFF5FC08C)   // comfortable
val Orange      = Color(0xFFF08A3C)   // limit reached
