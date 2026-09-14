package com.mempharma.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * MemPharma palette — warm, friendly and high-contrast for legibility.
 *
 * Roles:
 *  - primary / primaryContainer : the app's warm "burnt orange" brand
 *  - tertiary / tertiaryContainer: the "success / taken" green family
 *  - secondary                    : muted "not now / muted" family
 *  - error                        : overdue / attention
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF9A3412),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF77574E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD2),
    onSecondaryContainer = Color(0xFF2C1510),
    tertiary = Color(0xFF1C6B3C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8F2C9),
    onTertiaryContainer = Color(0xFF00210E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410000),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF5DEDA),
    onSurfaceVariant = Color(0xFF52433D),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BB),
    inverseSurface = Color(0xFF362A24),
    inverseOnSurface = Color(0xFFFFECE4),
    inversePrimary = Color(0xFFFFB596),
    // Warm elevation ramp. Pinned explicitly so cards, dialogs and the bottom bar
    // step up in a predictable, warm way instead of the default cool grey.
    surfaceDim = Color(0xFFE8D6CF),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF2EC),
    surfaceContainer = Color(0xFFFCEBE4),
    surfaceContainerHigh = Color(0xFFF6E4DD),
    surfaceContainerHighest = Color(0xFFF0DED7)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF542100),
    primaryContainer = Color(0xFF7A290D),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE7BDAE),
    onSecondary = Color(0xFF442922),
    secondaryContainer = Color(0xFF5D4038),
    onSecondaryContainer = Color(0xFFFFDBD2),
    tertiary = Color(0xFF9CD5AE),
    onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF00522A),
    onTertiaryContainer = Color(0xFFB8F2C9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF1A120F),
    onBackground = Color(0xFFF0DFD9),
    surface = Color(0xFF1A120F),
    onSurface = Color(0xFFF0DFD9),
    surfaceVariant = Color(0xFF52433D),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08C85),
    outlineVariant = Color(0xFF52433D),
    inverseSurface = Color(0xFFF0DFD9),
    inverseOnSurface = Color(0xFF362A24),
    inversePrimary = Color(0xFF9A3412),
    // Dark end of the same warm ramp — never a neutral grey.
    surfaceDim = Color(0xFF1A120F),
    surfaceBright = Color(0xFF413632),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF231916),
    surfaceContainer = Color(0xFF271D1A),
    surfaceContainerHigh = Color(0xFF322723),
    surfaceContainerHighest = Color(0xFF3D312D)
)
