package cl.inacap.ventismart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.ui.theme.V

/** Encabezado de pantalla: flecha atras, titulo y subtitulo, como en los mockups. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = V.Ink) }
            Spacer(Modifier.width(4.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = V.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
    }
}

@Composable
fun VCard(
    modifier: Modifier = Modifier,
    color: Color = V.Card,
    border: Color? = V.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color)
            .then(if (border != null) Modifier.borderLine(border) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

private fun Modifier.borderLine(c: Color) = this.border(BorderStroke(1.dp, c), RoundedCornerShape(18.dp))

@Composable
fun StatusDot(text: String, color: Color, bold: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color)
    }
}

@Composable
fun PrimaryButton(
    text: String, icon: ImageVector? = null, enabled: Boolean = true,
    color: Color = V.Teal, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = Color.White,
            disabledContainerColor = V.Disabled, disabledContentColor = V.DisabledText,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String, icon: ImageVector? = null, enabled: Boolean = true,
    color: Color = V.Teal, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) V.Line else V.Disabled),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (enabled) V.Card else V.Disabled, contentColor = color,
            disabledContainerColor = V.Disabled, disabledContentColor = V.DisabledText,
        ),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Circulo blanco con el aspa del ventilador. */
@Composable
fun FanBadge(spinning: Boolean, size: Int = 64, tint: Color = V.Teal) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Toys, null, tint = if (spinning) tint else V.Faint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun PowerBar(percent: Int, color: Color = V.Teal) {
    LinearProgressIndicator(
        progress = { percent.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color, trackColor = Color.White, strokeCap = StrokeCap.Round,
    )
}

@Composable
fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = V.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = V.Ink)
    }
}

@Composable
fun FootNote(text: String, color: Color = V.Muted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))
