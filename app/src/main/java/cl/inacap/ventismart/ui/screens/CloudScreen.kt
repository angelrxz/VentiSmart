package cl.inacap.ventismart.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.data.ThingSpeak
import cl.inacap.ventismart.data.Thresholds
import cl.inacap.ventismart.data.TsPoint
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===================== Nube · ThingSpeak =====================
@Composable
fun CloudScreen(vm: AppViewModel, onBack: () -> Unit) {
    val feed by vm.tsFeed.collectAsState()
    val status by vm.tsStatus.collectAsState()
    val loading by vm.tsLoading.collectAsState()
    val error by vm.tsReadError.collectAsState()
    val gateway by vm.isGateway.collectAsState()
    val now by vm.now.collectAsState()
    val ctx = LocalContext.current
    // Mientras la pantalla esta abierta, se vuelve a leer el canal cada 20 s.
    LaunchedEffect(Unit) { while (true) { vm.refreshThingSpeak(); delay(20_000) } }
    CloudContent(
        feed, status, loading, error, gateway, now, onBack,
        onRefresh = { vm.refreshThingSpeak() },
        onOpenWeb = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ThingSpeak.publicUrl))) },
    )
}

@Composable
fun CloudContent(
    feed: List<TsPoint>, status: AppViewModel.TsStatus, loading: Boolean, error: String?,
    gateway: Boolean, now: Long, onBack: () -> Unit, onRefresh: () -> Unit, onOpenWeb: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Nube · ThingSpeak", "Canal ${ThingSpeak.channel} · VentiSmart Dormitorio", onBack)
        Gap(6)

        // Envio (solo el telefono conectado al equipo)
        VCard(color = V.Mint, border = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudUpload, null, tint = V.Teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Envío de datos", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            }
            Gap(6)
            when {
                !gateway -> StatusDot("Este teléfono solo lee el canal", V.Muted)
                status.error != null -> StatusDot(status.error, V.Amber)
                status.lastOkAt == 0L -> StatusDot("Conectado · primer envío en curso", V.Amber)
                else -> StatusDot("Enviando · registro #${status.lastEntry} ${ago(now, status.lastOkAt)}", V.Ok)
            }
            Gap(4)
            Text(
                if (gateway) "Este teléfono está conectado al equipo y sube una lectura cada 16 s (${status.sent} enviadas en esta sesión)."
                else "El teléfono conectado al equipo sube las lecturas. Aquí se consultan desde ThingSpeak.",
                style = MaterialTheme.typography.bodySmall, color = V.Muted,
            )
        }
        Gap(12)

        // Grafico
        VCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Temperatura y potencia", style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
                Text(if (loading) "Actualizando…" else "${feed.size} registros", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            Gap(10)
            if (feed.size < 2) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: if (loading) "Leyendo el canal…" else "Aún no hay datos suficientes en el canal.",
                        style = MaterialTheme.typography.bodyMedium, color = if (error != null) V.Amber else V.Muted)
                }
            } else {
                TempChart(feed, Modifier.fillMaxWidth().height(170.dp))
                Gap(8)
                Row {
                    Legend(V.Teal, "Temperatura (°C)"); Spacer(Modifier.width(14.dp))
                    Legend(V.MintStrong, "Potencia (%)"); Spacer(Modifier.width(14.dp))
                    Legend(V.Red, "Alarma 32 °C")
                }
            }
        }
        Gap(12)

        // Ultimos registros
        if (feed.isNotEmpty()) {
            val last = feed.last()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Última", "${AppViewModel.fmt(last.temp)} °C", Modifier.weight(1f))
                Stat("Máxima", "${AppViewModel.fmt(feed.maxOf { it.temp })} °C", Modifier.weight(1f))
                Stat("Promedio", "${AppViewModel.fmt(feed.map { it.temp }.average().toFloat())} °C", Modifier.weight(1f))
            }
            Gap(12)
            VCard {
                Text("Últimos registros", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Gap(6)
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                feed.takeLast(6).reversed().forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(p.at)), style = MaterialTheme.typography.bodySmall, color = V.Muted, modifier = Modifier.width(70.dp))
                        Text("${AppViewModel.fmt(p.temp)} °C", style = MaterialTheme.typography.bodyMedium, color = if (p.alarm) V.Red else V.Ink, modifier = Modifier.weight(1f))
                        Text("${p.power} %", style = MaterialTheme.typography.bodyMedium, color = V.Teal, modifier = Modifier.width(56.dp))
                        Text(if (!p.auto) "Apagado" else if (p.alarm) "Alarma" else "Auto", style = MaterialTheme.typography.bodySmall,
                            color = if (p.alarm) V.Red else if (!p.auto) V.Amber else V.Ok)
                    }
                }
            }
            Gap(12)
        }
        PrimaryButton("Actualizar ahora", Icons.Filled.Refresh, enabled = !loading, onClick = onRefresh)
        Gap(10)
        SecondaryButton("Abrir canal en ThingSpeak", Icons.Filled.OpenInBrowser, onClick = onOpenWeb)
        FootNote("Campos del canal: 1 temperatura, 2 potencia, 3 alarma, 4 modo. Los datos viajan cifrados (HTTPS) y la clave de escritura solo la usa el teléfono conectado al equipo.")
    }
}

@Composable
private fun TempChart(feed: List<TsPoint>, modifier: Modifier) {
    val minT = minOf(feed.minOf { it.temp }, Thresholds.VENT_START) - 1f
    val maxT = maxOf(feed.maxOf { it.temp }, Thresholds.ALARM) + 1f
    Canvas(modifier) {
        val n = feed.size
        val stepX = size.width / (n - 1)
        val y = { t: Float -> size.height * (1f - (t - minT) / (maxT - minT)) }
        // Potencia como barras suaves al fondo
        val barW = (stepX * 0.6f).coerceAtLeast(2f)
        feed.forEachIndexed { i, p ->
            val h = size.height * p.power / 100f
            drawRect(V.MintStrong, Offset(i * stepX - barW / 2, size.height - h), Size(barW, h))
        }
        // Umbrales del firmware
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        drawLine(V.Red, Offset(0f, y(Thresholds.ALARM)), Offset(size.width, y(Thresholds.ALARM)), 2f, pathEffect = dash)
        drawLine(V.Amber, Offset(0f, y(Thresholds.VENT_START)), Offset(size.width, y(Thresholds.VENT_START)), 2f, pathEffect = dash)
        // Temperatura
        val path = Path()
        feed.forEachIndexed { i, p -> if (i == 0) path.moveTo(0f, y(p.temp)) else path.lineTo(i * stepX, y(p.temp)) }
        drawPath(path, V.Teal, style = Stroke(width = 5f))
        feed.forEachIndexed { i, p -> if (p.alarm) drawCircle(V.Red, 6f, Offset(i * stepX, y(p.temp))) }
    }
}

@Composable
private fun Legend(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(c) }
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = V.Muted)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    VCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = V.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = V.Ink)
    }
}
