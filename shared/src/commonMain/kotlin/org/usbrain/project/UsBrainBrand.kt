package org.usbrain.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import usbrain.shared.generated.resources.Res
import usbrain.shared.generated.resources.usbrain_logo

/**
 * Marca USBrain: el logotipo oficial (cuatro fichas U·S·B) sobre una placa blanca
 * redondeada, de modo que se lee bien en tema claro y oscuro.
 */
@Composable
fun UsBrainMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color.White)
            .padding(size * 0.06f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.usbrain_logo),
            contentDescription = "USBrain",
            modifier = Modifier.size(size * 0.88f),
        )
    }
}

/**
 * Reconstrucción vectorial de la marca (adaptable, nítida a cualquier escala).
 * Se conserva por si se quiere una versión sin placa blanca.
 */
@Composable
fun UsBrainMarkVector(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 120f
        fun p(v: Float) = v * s
        val white = Color(0xFFFAF8F5)
        val r = CornerRadius(p(15f), p(15f))
        drawRoundRect(Brand.Violet, Offset(p(10f), p(10f)), Size(p(46f), p(46f)), r)
        drawRoundRect(Brand.Navy, Offset(p(64f), p(10f)), Size(p(46f), p(46f)), r)
        drawRoundRect(Brand.Coral, Offset(p(10f), p(64f)), Size(p(46f), p(46f)), r)
        drawRoundRect(Brand.Orange, Offset(p(64f), p(64f)), Size(p(46f), p(46f)), r)
        drawCircle(Brand.Violet, p(7.5f), Offset(p(60f), p(30f)))
        drawCircle(Brand.Orange, p(7.5f), Offset(p(60f), p(90f)))
        drawCircle(Brand.Coral, p(7.5f), Offset(p(30f), p(60f)))
        drawCircle(Brand.Navy, p(7.5f), Offset(p(90f), p(60f)))
        val hub = Offset(p(60f), p(60f))
        val nodes = listOf(
            Offset(p(41f), p(42f)), Offset(p(79f), p(42f)),
            Offset(p(43f), p(79f)), Offset(p(79f), p(79f)),
        )
        nodes.forEach { drawLine(white, hub, it, p(3.4f), cap = StrokeCap.Round) }
        drawCircle(white, p(7f), hub)
        nodes.forEach { drawCircle(white, p(4.4f), it) }
    }
}

@Composable
fun UsBrainWordmark(fontSize: TextUnit = 22.sp, showTagline: Boolean = true) {
    Column {
        Row {
            Text(
                "US", color = Brand.Orange, fontWeight = FontWeight.Bold,
                fontSize = fontSize, letterSpacing = (-0.5).sp,
            )
            Text(
                "Brain", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                fontSize = fontSize, letterSpacing = (-0.5).sp,
            )
        }
        if (showTagline) {
            Spacer(Modifier.height(2.dp))
            Text(
                "REGISTRA · ENTIENDE · TRANSFORMA",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun UsBrainLockup(markSize: Dp = 44.dp, showTagline: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UsBrainMark(size = markSize)
        UsBrainWordmark(showTagline = showTagline)
    }
}

/** Placa circular de "éxito" con un check dibujado: fondo neutro + anillo de color, sin lavado pálido. */
@Composable
fun SuccessBadge(size: Dp = 84.dp) {
    val color = LocalUsBrainSemantic.current.success
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color, RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val w = this.size.width
            val path = Path().apply {
                moveTo(w * 0.08f, w * 0.55f)
                lineTo(w * 0.40f, w * 0.84f)
                lineTo(w * 0.94f, w * 0.18f)
            }
            drawPath(path, color, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Íconos simples dibujados para la barra de navegación (sin dependencia de material-icons). */
@Composable
fun NavGlyph(kind: String, selected: Boolean) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "home" -> drawPath(Path().apply {
                moveTo(w * 0.15f, h * 0.52f); lineTo(w * 0.50f, h * 0.15f); lineTo(w * 0.85f, h * 0.52f)
                moveTo(w * 0.25f, h * 0.46f); lineTo(w * 0.25f, h * 0.85f); lineTo(w * 0.75f, h * 0.85f); lineTo(w * 0.75f, h * 0.46f)
            }, tint, style = stroke)
            "chart" -> drawPath(Path().apply {
                moveTo(w * 0.15f, h * 0.15f); lineTo(w * 0.15f, h * 0.85f); lineTo(w * 0.85f, h * 0.85f)
                moveTo(w * 0.28f, h * 0.68f); lineTo(w * 0.45f, h * 0.48f); lineTo(w * 0.60f, h * 0.58f); lineTo(w * 0.82f, h * 0.28f)
            }, tint, style = stroke)
            "person" -> {
                drawCircle(tint, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.32f), style = stroke)
                drawPath(Path().apply {
                    moveTo(w * 0.20f, h * 0.86f)
                    quadraticTo(w * 0.5f, h * 0.52f, w * 0.80f, h * 0.86f)
                }, tint, style = stroke)
            }
            "plus" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            // Chevron simple (como "<", el espejo de ">"): sin vástago, más cercano al ícono de
            // volver de iOS/Android que a una flecha completa — se ve más limpio como botón.
            "back" -> drawPath(Path().apply {
                moveTo(w * 0.62f, h * 0.20f); lineTo(w * 0.34f, h * 0.5f); lineTo(w * 0.62f, h * 0.80f)
            }, tint, style = stroke)
            // Espejo de "back": para llamadas a la acción de "ir a…" en botones/enlaces, en vez
            // de depender del carácter "→" de la fuente.
            "forward" -> drawPath(Path().apply {
                moveTo(w * 0.38f, h * 0.20f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.38f, h * 0.80f)
            }, tint, style = stroke)
            // Tres líneas horizontales: pestaña de "Historial"/lista, distinta de "chart" (que
            // ya se usa para la gráfica de progreso).
            "list" -> {
                listOf(0.28f, 0.5f, 0.72f).forEach { y ->
                    drawLine(tint, Offset(w * 0.16f, h * y), Offset(w * 0.84f, h * y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
