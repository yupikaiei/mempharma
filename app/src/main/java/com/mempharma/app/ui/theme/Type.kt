package com.mempharma.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mempharma.app.R

/**
 * Atkinson Hyperlegible (Braille Institute, SIL OFL — see
 * `licenses/ATKINSON_HYPERLEGIBLE_OFL.txt`).
 *
 * It was designed for readers with low vision: tall x-height, wide apertures and
 * deliberately distinct letter shapes (I/l/1, O/0). It also covers Portuguese
 * diacritics (Á Ã Ç É Õ) without clipping, which matters because Compose draws
 * text with `includeFontPadding = false` by default.
 */
val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold)
)

/**
 * Type scale tuned up from Material defaults so body text is comfortable for
 * older eyes. The Settings screen can scale everything further via fontScale.
 *
 * Line heights sit at ~1.35x the font size on purpose. Compose draws glyphs
 * without extra font padding, so tighter ratios clip the accents and cedillas
 * that Portuguese needs (Á, Ã, Ç, É, Õ) — and they make long translated strings
 * feel cramped once the text size is turned up.
 *
 * [AtkinsonHyperlegible] is applied to every style below, including
 * `displaySmall` (used by the full-screen alarm) — Material 3's `Typography` has
 * no `defaultFontFamily` parameter, so each style names the family itself.
 */
private val Family = AtkinsonHyperlegible

val MemPharmaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Family, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp),
    displayMedium = TextStyle(fontFamily = Family, fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = Family, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Family, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Family, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Family, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Family, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Family, fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Family, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Family, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Family, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Family, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Family, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
)
