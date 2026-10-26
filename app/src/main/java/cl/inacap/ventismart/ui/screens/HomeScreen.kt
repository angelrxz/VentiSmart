package cl.inacap.ventismart.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.data.Device
import cl.inacap.ventismart.data.LinkState
import cl.inacap.ventismart.data.LiveState
import cl.inacap.ventismart.data.Mode
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel, onAdd: () -> Unit, onControl: () -> Unit) {
    val user by vm.user.collectAsState()
    val devices by vm.devices.collectAsState()
    val selected by vm.selected.collectAsState()
    val live by vm.live.collectAsState()
    val gateway by vm.isGateway.collectAsState()
    val gatewayFor by vm.gatewayFor.collectAsState()
    val localLink by vm.localLink.collectAsState()
    val now by vm.now.collectAsState()
    HomeContent(
        userName = user?.displayName, devices = devices, selected = selected, live = live,
        gateway = gateway, localActive = gatewayFor != null, localLink = localLink, now = now,
        onAdd = onAdd, onControl = onControl, onConnect = { vm.connect(it) }, onSelect = { vm.select(it) },
    )
}

/** Contenido de Inicio sin dependencias de Firebase (permite la vista previa). */
@Composable
fun HomeContent(
    userName: String?, devices: List<Device>, selected: Device?, live: LiveState?,
    gateway: Boolean, localActive: Boolean, localLink: LinkState, now: Long,
    onAdd: () -> Unit, onControl: () -> Unit, onConnect: (Device) -> Unit, onSelect: (String) -> Unit,
) {
    val first = (userName ?: "").split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "de nuevo"
    val connLabel = when {
        localActive -> if (selected?.simulated == true) "simulación local" else "conexión Bluetooth local"
        live != null -> "en vivo desde la nube"
        else -> "sin enlace"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V.Mint), contentAlignment = Alignment.Center) {
                Icon(FanIcon, null, tint = V.Teal, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("VentiSmart", style = MaterialTheme.typography.titleLarge, color = V.Ink)
                Text("Tu dormitorio, en equilibrio", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
        }
        Gap(18)
        Text("${greeting()}, $first", style = MaterialTheme.typography.titleLarge, color = V.Ink)
        Text("${today()} · $connLabel", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        Gap(14)

        val d = selected
        if (d == null) {
            EmptyState(onAdd)
        } else {
            HeroCard(d, live, gateway, localLink, now, onControl, onConnect = { onConnect(d) })
            Gap(20)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mis dispositivos", style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
                Text("${devices.size} vinculado${if (devices.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            Gap(10)
            devices.forEach { dev ->
                DeviceRow(dev, if (dev.id == d.id) live else null) { onSelect(dev.id); onControl() }
                Gap(10)
            }
            SecondaryButton("Agregar dispositivo", Icons.Filled.Add, onClick = onAdd)
            FootNote(
                if (gateway) "Bluetooth cercano · este teléfono comparte las lecturas con tu cuenta. El control automático se ejecuta en el dispositivo."
                else "Este teléfono ve el ventilador en vivo a través de la nube. El control automático se ejecuta en el dispositivo.",
            )
        }
    }
}

@Composable
private fun HeroCard(
    d: Device, live: LiveState?, gateway: Boolean, link: LinkState, now: Long,
    onControl: () -> Unit, onConnect: () -> Unit,
) {
    VCard(color = V.Mint, border = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
            Icon(
                if (live?.linkOk == true) (if (gateway) Icons.Filled.Bluetooth else Icons.Filled.Cloud) else Icons.Filled.BluetoothDisabled,
                null, tint = V.Teal, modifier = Modifier.size(18.dp),
            )
        }
        Gap(4)
        when {
            gateway && link == LinkState.CONNECTING -> StatusDot("Conectando...", V.Amber)
            live == null -> StatusDot("Sin lecturas todavía", V.Muted)
            !live.linkOk -> StatusDot("Sin señal · última lectura guardada", V.Amber)
            else -> StatusDot("Conectado · ${modeText(live)}", if (live.alarm) V.Red else V.Ok)
        }
        Gap(10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                live?.let { "${AppViewModel.fmt(it.temp)} °C" } ?: "-- °C",
                style = MaterialTheme.typography.headlineLarge,
                color = if (live?.alarm == true) V.Red else if (live?.linkOk == false) V.Faint else V.Ink,
                modifier = Modifier.weight(1f),
            )
            FanBadge(spinning = (live?.power ?: 0) > 0 && live?.linkOk == true)
        }
        Text(live?.let { "Actualizado ${ago(now, it.updatedAt)}" } ?: "Esperando datos del dispositivo",
            style = MaterialTheme.typography.bodySmall, color = V.Muted)
        Gap(10)
        Row {
            Text(if (live?.mode == Mode.OFF) "Potencia · apagado manual" else "Potencia automática",
                style = MaterialTheme.typography.bodyMedium, color = V.Muted, modifier = Modifier.weight(1f))
            Text("${live?.power ?: 0} %", style = MaterialTheme.typography.titleMedium, color = V.Teal)
        }
        Gap(6)
        PowerBar(live?.power ?: 0, if (live?.alarm == true) V.Red else V.Teal)
        Gap(14)
        if (!gateway && (live == null || !live.linkOk)) {
            PrimaryButton(
                if (d.simulated) "Iniciar simulación en este teléfono" else "Conectar desde este teléfono",
                Icons.Filled.Bluetooth, onClick = onConnect,
            )
            Gap(8)
        }
        PrimaryButton("Control del ventilador", Icons.Filled.Tune, onClick = onControl)
    }
}

@Composable
private fun DeviceRow(d: Device, live: LiveState?, onClick: () -> Unit) {
    VCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bed, null, tint = V.Teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Text("${d.location} · ${d.type}${if (d.simulated) " · simulado" else ""}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                Gap(4)
                if (live != null && live.linkOk) {
                    StatusDot("${if (live.alarm) "Alarma" else "Normal"} · ${AppViewModel.fmt(live.temp)} °C · ${live.power} %", if (live.alarm) V.Red else V.Ok)
                } else {
                    StatusDot("Sin lecturas recientes", V.Faint)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = V.Muted)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    VCard(color = V.Mint, border = null) {
        Text("Aún no tienes dispositivos", style = MaterialTheme.typography.titleMedium, color = V.Ink)
        Gap(6)
        Text(
            "Vincula tu ventilador por Bluetooth o agrega el dispositivo de demostración para probar la app sin hardware.",
            style = MaterialTheme.typography.bodyMedium, color = V.Muted,
        )
        Gap(14)
        PrimaryButton("Agregar dispositivo", Icons.Filled.Add, onClick = onAdd)
    }
}

fun modeText(l: LiveState) = when {
    l.mode == Mode.OFF -> "MANUAL · apagado"
    l.alarm -> "AUTO · alarma"
    l.power > 0 -> "AUTO · ventilando"
    else -> "AUTO · normal"
}

fun ago(now: Long, at: Long): String {
    if (at <= 0) return "sin registro"
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 2 -> "hace 1 s"
        s < 60 -> "hace $s s"
        s < 3600 -> "hace ${s / 60} min"
        else -> "hace ${s / 3600} h"
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 12 -> "Buenos días"; h < 20 -> "Buenas tardes"; else -> "Buenas noches" }
}

private fun today(): String =
    SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "CL")).format(Date()).replaceFirstChar { it.uppercase() }
