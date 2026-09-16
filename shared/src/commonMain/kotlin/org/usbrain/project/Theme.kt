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
    success = Color(0xFF1F8F5C),
    warning = Color(0xFF8A5900),
    baseline = Color(0xFFD6493A),
    intervention = Color(0xFF6A54B0),
    grid = Color(0xFFDCD2C0),
)

private val DarkSemantic = UsBrainSemantic(
    success = Color(0xFF43C685),
    warning = Color(0xFFE6AC4F),
    baseline = Color(0xFFFF7D66),
    intervention = Color(0xFFC7BAF0),
    grid = Color(0xFF2C4058),
)

val LocalUsBrainSemantic = staticCompositionLocalOf { LightSemantic }

private val LightColors = lightColorScheme(
    primary = Brand.Orange,
    onPrimary = Color(0xFF2A1400),
    primaryContainer = Color(0xFFFFC98A),
    onPrimaryContainer = Color(0xFF4A2400),
    secondary = Color(0xFF6A54B0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCCBF5),
    onSecondaryContainer = Color(0xFF241A45),
    tertiary = Color(0xFFE5503A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFC9BC),
    onTertiaryContainer = Color(0xFF3B0A02),
    // Fondo de página blanco puro, sin el tinte cálido/beige de antes; las tarjetas usan
    // `surface` un paso más gris para distinguirse del fondo aunque no se mire el borde.
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF16181C),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF16181C),
    surfaceVariant = Color(0xFFEDEEEF),
    onSurfaceVariant = Color(0xFF53575C),
    outline = Color(0xFFC7CACD),
    outlineVariant = Color(0xFFE4E6E8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFFFB77C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A47),
    onPrimary = Color(0xFF3A1D00),
    primaryContainer = Color(0xFFA34E00),
    onPrimaryContainer = Color(0xFFFFE3C4),
    secondary = Color(0xFFC7BAF0),
    onSecondary = Color(0xFF1F1147),
    secondaryContainer = Color(0xFF4E4380),
    onSecondaryContainer = Color(0xFFEDE6FA),
    tertiary = Color(0xFFFF9C86),
    onTertiary = Color(0xFF5C1A0C),
    tertiaryContainer = Color(0xFF8A3A28),
    onTertiaryContainer = Color(0xFFFFDAD1),
    // Fondo de página negro real (no navy); las tarjetas usan `surface` un paso más claro
    // (gris carbón, el estándar de Material en oscuro) para no fundirse con el fondo.
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F3F4),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F3F4),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFC7CBCF),
    outline = Color(0xFF6B6F73),
    outlineVariant = Color(0xFF303234),
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
