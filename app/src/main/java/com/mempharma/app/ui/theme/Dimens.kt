package com.mempharma.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing and sizing tokens.
 *
 * Two reasons this exists rather than magic numbers on each screen:
 *
 * 1. **Consistency** — every screen steps through the same rhythm, which is most
 *    of what makes a UI feel "clean" rather than assembled.
 * 2. **Localization safety** — the app is translated, and translations are rarely
 *    the same length as the source. Anything that carries text gets a *minimum*
 *    height (never a fixed one) so a longer Portuguese or English label grows the
 *    control instead of being clipped. See [MinTouchTarget] for the accessibility
 *    floor that every tappable thing must respect.
 */
object Dimens {

    /** 4dp — hairline gaps, e.g. between an icon and its label. */
    val SpaceXs = 4.dp

    /** 8dp — tight gap inside a group. */
    val SpaceS = 8.dp

    /** 12dp — gap between related elements. */
    val SpaceM = 12.dp

    /** 16dp — the default padding around content and between blocks. */
    val SpaceL = 16.dp

    /** 24dp — breathing room between sections. */
    val SpaceXl = 24.dp

    /** 32dp — extra separation before a primary action. */
    val SpaceXxl = 32.dp

    /** Standard horizontal page padding. */
    val ScreenPadding = 16.dp

    /**
     * Minimum size of anything tappable. Android's accessibility guidance is
     * 48x48dp; going below it is the most common way an app becomes hard to use
     * for shaky or imprecise hands, which is exactly this app's audience.
     */
    val MinTouchTarget = 48.dp

    /**
     * Minimum height for a normal button. Deliberately a *minimum*: translated
     * labels wrap onto a second line instead of being cut off.
     */
    val ControlMinHeight = 56.dp

    /** Minimum height for a full-width primary action ("Adicionar medicamento"). */
    val PrimaryActionMinHeight = 64.dp

    /** Minimum height for the confirmation button on the full-screen alarm. */
    val AlarmPrimaryMinHeight = 92.dp

    /** Minimum height for the secondary ("Agora não") alarm button. */
    val AlarmSecondaryMinHeight = 80.dp

    /** Medicine avatar in the Today list. */
    val AvatarLarge = 56.dp

    /** Medicine avatar in the Medicines list. */
    val AvatarMedium = 52.dp

    /** Medicine avatar in the History list. */
    val AvatarSmall = 44.dp
}
