package org.usbrain.project

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colores oficiales de la identidad USBrain. */
object Brand {
    val Orange = Color(0xFFFF7A00)
    val Coral = Color(0xFFFF6B56)
    val Violet = Color(0xFF7B66C2)
    val Navy = Color(0xFF0F2540)
}

/** Colores semánticos que Material no cubre (éxito, fases del caso único, grilla). */
@Immutable
data class UsBrainSemantic(
    val success: Color,
    val warning: Color,
    val baseline: Color,
    val intervention: Color,
    val grid: Color,
)

private val LightSemantic = UsBrainSemantic(
    success = Color(0xFF2FA36B),
    warning = Color(0xFF9A6410),
    baseline = Color(0xFFD65540),
    intervention = Color(0xFF6D5BA8),
    grid = Color(0xFFE4DCD0),
)

private val DarkSemantic = UsBrainSemantic(
    success = Color(0xFF54CC90),
    warning = Color(0xFFE0A54A),
    baseline = Color(0xFFFF8B78),
    intervention = Color(0xFFC7BAF0),
    grid = Color(0xFF26384C),
)

val LocalUsBrainSemantic = staticCompositionLocalOf { LightSemantic }

private val LightColors = lightColorScheme(
    primary = Brand.Orange,
    onPrimary = Color(0xFF2A1400),
    primaryContainer = Color(0xFFFFDDBD),
    onPrimaryContainer = Color(0xFF2A1400),
    secondary = Color(0xFF6D5BA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E1F7),
    onSecondaryContainer = Color(0xFF241A45),
    tertiary = Color(0xFFCE4E3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3B0A02),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C2836),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2836),
    surfaceVariant = Color(0xFFEFEAE1),
    onSurfaceVariant = Color(0xFF544B3E),
    outline = Color(0xFFB6AB9B),
    outlineVariant = Color(0xFFE1DACE),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFFFB77C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A47),
    onPrimary = Color(0xFF3A1D00),
    primaryContainer = Color(0xFF8A4200),
    onPrimaryContainer = Color(0xFFFFDDBD),
    secondary = Color(0xFFC7BAF0),
    onSecondary = Color(0xFF1F1147),
    secondaryContainer = Color(0xFF423A6B),
    onSecondaryContainer = Color(0xFFE8E1F7),
    tertiary = Color(0xFFFFB4A4),
    onTertiary = Color(0xFF5C1A0C),
    tertiaryContainer = Color(0xFF7A2E1E),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF0C1B2C),
    onBackground = Color(0xFFE7EDF3),
    surface = Color(0xFF102337),
    onSurface = Color(0xFFE7EDF3),
    surfaceVariant = Color(0xFF1B3148),
    onSurfaceVariant = Color(0xFFC0CCD9),
    outline = Color(0xFF3E5064),
    outlineVariant = Color(0xFF26384C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inversePrimary = Color(0xFFB2560B),
)

private val UsBrainShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun UsBrainTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantic = if (darkTheme) DarkSemantic else LightSemantic
    CompositionLocalProvider(LocalUsBrainSemantic provides semantic) {
        MaterialTheme(colorScheme = colors, shapes = UsBrainShapes, content = content)
    }
}
