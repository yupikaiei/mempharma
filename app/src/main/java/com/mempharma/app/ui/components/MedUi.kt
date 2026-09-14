package com.mempharma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mempharma.app.ui.theme.Dimens
import com.mempharma.app.util.rememberAppLocale

/**
 * Coloured circles used for each medicine's avatar so a glance tells medicines
 * apart without reading. Every colour is chosen for high contrast with white
 * initials.
 */
object MedPalette {
    private val tones = listOf(
        Color(0xFFB0401E), // burnt orange
        Color(0xFF1E6B44), // green
        Color(0xFF1E5FA6), // blue
        Color(0xFF8A5A2B), // brown
        Color(0xFF6E4AC0), // purple
        Color(0xFFB23B6E), // magenta
        Color(0xFF2E7D7B), // teal
        Color(0xFF9A6A00)  // amber
    )

    fun colorFor(index: Int): Color = tones[Math.floorMod(index, tones.size)]

    /** All colours in order, for building a colour picker. */
    fun listColors(): List<Color> = tones
}

/** Round avatar showing the medicine's first letter on its brand colour. */
@Composable
fun MedicationAvatar(
    name: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.AvatarLarge
) {
    // Locale-aware upper-casing: the Turkish dotless-i rules would otherwise turn
    // an innocent "i" into "İ" for a medicine name typed in another language.
    val locale = rememberAppLocale()
    val initial = name.trim().firstOrNull()?.uppercase(locale) ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .background(MedPalette.colorFor(colorIndex), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Small rounded status pill (e.g. "Tomado", "Restam apenas 3 comprimidos").
 *
 * Translations are rarely the same length as the English source, so the pill
 * wraps onto a second line and only then truncates, instead of being cropped or
 * pushing its neighbours off the screen.
 */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    Box(
        modifier = modifier.background(container, RoundedCornerShape(percent = 50))
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * What to show when a list has nothing in it yet: a quiet icon, a clear title
 * and one sentence explaining the next tap. Used by Today, Medicines and
 * History so all three empty states look and read the same.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXl, horizontal = Dimens.SpaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Icon + title used at the top of each settings card. */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Dimens.SpaceS))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
