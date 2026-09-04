package com.mempharma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    size: Dp = 64.dp
) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
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

/** Small rounded status pill (e.g. "Taken", "Muted", "Due now"). */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(container, MaterialTheme.shapes.small)
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
