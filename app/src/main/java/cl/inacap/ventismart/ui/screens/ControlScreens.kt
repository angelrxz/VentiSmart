package cl.inacap.ventismart.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.data.Device
import cl.inacap.ventismart.data.LinkState
import cl.inacap.ventismart.data.LiveState
import cl.inacap.ventismart.data.Mode
import cl.inacap.ventismart.data.Thresholds
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Elige entre las pantallas 07, 08 y 09 segun el estado real, como en el diagrama
 * de flujo: lectura >= 32 C lleva a Sobrecalentamiento y la perdida del enlace a Sin senal.
 */
@Composable
fun ControlRoute(vm: AppViewModel, onBack: () -> Unit, onAlerts: () -> Unit) {
    val d by vm.selected.collectAsState()
    val live by vm.live.collectAsState()
    val gateway by vm.isGateway.collectAsState()
    val link by vm.localLink.collectAsState()
    val now by vm.now.collectAsState()
    val dev = d ?: run { LaunchedEffect(Unit) { onBack() }; return }

    // Ultima lectura conocida, para mostrarla en gris si se corta el enlace.
    var lastGood by remember(dev.id) { mutableStateOf<LiveState?>(null) }
    LaunchedEffect(live) { if (live?.linkOk == true) lastGood = live }

    val l = live
    when {
        gateway && link == LinkState.CONNECTING -> Connecting(dev, onBack)
        l == null || !l.linkOk -> NoSignalScreen(vm, dev, lastGood ?: l, gateway, now, onBack)
        l.alarm && l.mode == Mode.AUTO -> OverheatScreen(vm, dev, l, gateway, now, onBack, onAlerts)
        else -> ControlScreen(vm, dev, l, gateway, now, onBack)
    }
}

// ===================== 07 Control del ventilador =====================
@Composable
private fun ControlScreen(vm: AppViewModel, dev: Device, l: LiveState, gateway: Boolean, now: Long, onBack: () -> Unit) {
    val off = l.mode == Mode.OFF
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Control del ventilador", "${dev.name} · conectado", onBack)
        RoleLine(gateway, l)
        Gap(10)
        VCard(color = V.Mint, border = null) {
            StatusDot(if (off) "MANUAL · apagado" else "AUTO · ${if (l.power > 0) "ventilando" else "en espera"}", if (off) V.Amber else V.Ok)
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${AppViewModel.fmt(l.temp)} °C", style = MaterialTheme.typography.headlineLarge, color = V.Ink, modifier = Modifier.weight(1f))
                FanBadge(spinning = l.power > 0)
            }
            Text("Potencia actual · ${l.power} %", style = MaterialTheme.typography.bodyMedium, color = V.Muted)
            Gap(6)
            PowerBar(l.power)
            Gap(8)
            Text("Lectura actualizada ${ago(now, l.updatedAt)}.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(12)
        if (off) {
            VCard(color = V.AmberBg, border = null) {
                Text("El automatico esta pausado", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Gap(4)
                Text("Apagar manual (M=O) anula la automatizacion hasta que reactives el modo automatico, aunque la temperatura suba.",
                    style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            Gap(12)
            PrimaryButton("Reactivar automatico", Icons.Filled.Autorenew) { vm.sendCommand(Mode.AUTO) }
            FootNote("Envia M=A. A ${AppViewModel.fmt(l.temp)} °C, AUTO regulara la potencia al confirmarse.")
            Gap(10)
            SecondaryButton("Apagar manual · ya apagado", Icons.Filled.PowerSettingsNew, enabled = false) {}
        } else {
            SecondaryButton("Reactivar automatico · ya activo", Icons.Filled.Autorenew, enabled = false) {}
            Gap(10)
            PrimaryButton("Apagar manual", Icons.Filled.PowerSettingsNew) { vm.sendCommand(Mode.OFF) }
            FootNote("Envia M=O. El ventilador se detiene y el automatico queda pausado hasta reactivarlo.")
        }
        Gap(14)
        RuleCard()
        FootNote("En AUTO, \"Apagar manual\" envia M=O. No hay ajuste manual de velocidad ni horarios.")
        SimPanel(vm)
    }
}

// ===================== 08 Sobrecalentamiento =====================
@Composable
private fun OverheatScreen(vm: AppViewModel, dev: Device, l: LiveState, gateway: Boolean, now: Long, onBack: () -> Unit, onAlerts: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Sobrecalentamiento", "${dev.name} · conectado", onBack)
        RoleLine(gateway, l)
        Gap(10)
        VCard(color = V.RedBg, border = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = V.Red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alarma de temperatura", style = MaterialTheme.typography.titleMedium, color = V.Red)
            }
            Gap(6)
            Text("${AppViewModel.fmt(l.temp)} °C", style = MaterialTheme.typography.headlineLarge, color = V.Red)
            StatusDot("Sobre el umbral de ${Thresholds.ALARM.toInt()} °C", V.Red)
            Gap(8)
            Text("Lectura actual · ${ago(now, l.updatedAt)}. Revisa el entorno y el dispositivo.", style = MaterialTheme.typography.bodySmall, color = V.Red)
        }
        Gap(12)
        VCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AUTO · ventilando", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                    Text("Potencia maxima", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                }
                Text("${l.power} %", style = MaterialTheme.typography.headlineSmall, color = V.Ink)
            }
            Gap(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, null, tint = V.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("LED rojo y buzzer activos", style = MaterialTheme.typography.bodySmall, color = V.Red)
            }
        }
        FootNote("Desde ${Thresholds.ALARM.toInt()} °C, el automatico ordena 100 % y activa la alarma. Ventilar no garantiza bajar la temperatura.")
        Gap(12)
        PrimaryButton("Apagar manual", Icons.Filled.PowerSettingsNew, color = V.Red) { vm.sendCommand(Mode.OFF) }
        FootNote("M=O detiene el motor y anula AUTO hasta reactivarlo. La alarma termica sigue destacada mientras la lectura sea >= ${Thresholds.ALARM.toInt()} °C.", V.Red)
        Gap(10)
        SecondaryButton("Ver Alertas e historial", Icons.Filled.NotificationsNone, onClick = onAlerts)
        FootNote("Prototipo domestico: no es una medicion de seguridad certificada ni un sistema de refrigeracion.")
        SimPanel(vm)
    }
}

// ===================== 09 Sin senal =====================
@Composable
private fun NoSignalScreen(vm: AppViewModel, dev: Device, last: LiveState?, gateway: Boolean, now: Long, onBack: () -> Unit) {
    val hour = remember(last?.updatedAt) { last?.let { SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it.updatedAt)) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Sin senal", "${dev.name} · desconectado", onBack)
        VCard(color = V.AmberBg, border = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BluetoothDisabled, null, tint = V.Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("No llegan nuevas lecturas", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            }
            Gap(4)
            Text(
                if (gateway) "3 s sin mensajes validos. Acercate al modulo y comprueba su alimentacion."
                else "Ningun telefono esta compartiendo lecturas de este equipo ahora. Conectalo desde aqui o desde el telefono que esta junto al ventilador.",
                style = MaterialTheme.typography.bodySmall, color = V.Muted,
            )
            Gap(6)
            StatusDot(if (gateway) "Bluetooth sin enlace" else "Sin puente activo", V.Amber)
        }
        Gap(12)
        VCard {
            StatusDot("Ultima lectura · historica", V.Faint)
            Gap(6)
            if (last != null) {
                Text("${AppViewModel.fmt(last.temp)} °C", style = MaterialTheme.typography.headlineLarge, color = V.Faint)
                Text("Recibida hoy a las $hour · ${ago(now, last.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                Gap(6)
                Row {
                    Text("Ultimo modo · ${if (last.mode == Mode.OFF) "MANUAL" else "AUTO"}", style = MaterialTheme.typography.bodySmall, color = V.Muted, modifier = Modifier.weight(1f))
                    Text("Ultima potencia · ${last.power} %", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                }
                Gap(4)
                Text("No representa el estado actual. Sin datos nuevos durante la desconexion.", style = MaterialTheme.typography.bodySmall, color = V.Faint)
            } else {
                Text("-- °C", style = MaterialTheme.typography.headlineLarge, color = V.Faint)
                Text("Aun no se ha recibido ninguna lectura de este equipo.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
        }
        Gap(12)
        VCard(color = V.Mint, border = null) {
            Text("El control local sigue activo", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            Gap(4)
            Text("Ultimo modo: ${if (last?.mode == Mode.OFF) "MANUAL" else "AUTO"}. El Arduino sigue regulando sin la app. Aqui no se conocen la potencia ni la temperatura actuales.",
                style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(12)
        SecondaryButton("Apagar manual", Icons.Filled.PowerSettingsNew, enabled = false) {}
        Gap(8)
        SecondaryButton("Reactivar automatico", Icons.Filled.Autorenew, enabled = false) {}
        FootNote("Controles remotos deshabilitados hasta recuperar el enlace.")
        Gap(10)
        PrimaryButton("Reintentar conexion", Icons.Filled.Bluetooth) { vm.connect(dev) }
        SimPanel(vm)
    }
}

@Composable
private fun Connecting(dev: Device, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Control del ventilador", "${dev.name} · conectando", onBack)
        Gap(40)
        CircularProgressIndicator(color = V.Teal, modifier = Modifier.align(Alignment.CenterHorizontally))
        Gap(16)
        Text("Abriendo el enlace con el dispositivo...", style = MaterialTheme.typography.bodyMedium, color = V.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// ===================== piezas comunes =====================
@Composable
private fun RoleLine(gateway: Boolean, l: LiveState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (gateway) Icons.Filled.Bluetooth else Icons.Filled.Cloud, null, tint = V.Teal, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (gateway) "Conectado desde este telefono · comparte las lecturas con tu cuenta"
            else "Control remoto por la nube · puente: ${l.gateway.ifBlank { "otro telefono" }}",
            style = MaterialTheme.typography.bodySmall, color = V.Teal,
        )
    }
}

@Composable
private fun RuleCard() {
    VCard {
        Text("Regla termica · automatico", style = MaterialTheme.typography.titleMedium, color = V.Ink)
        Gap(8)
        RuleRow("Menos de ${Thresholds.VENT_START.toInt()} °C", "Apagado", V.Ok)
        RuleRow("${Thresholds.VENT_START.toInt()} a menos de ${Thresholds.ALARM.toInt()} °C", "${Thresholds.PWM_MIN_PERCENT}-100 % proporcional", V.Amber)
        RuleRow("${Thresholds.ALARM.toInt()} °C o mas", "100 % + alarma", V.Red)
    }
}

@Composable
private fun RuleRow(range: String, action: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(range, style = MaterialTheme.typography.bodySmall, color = V.Muted, modifier = Modifier.weight(1f))
        StatusDot(action, color)
    }
}

/** Panel solo para el dispositivo simulado: mueve la temperatura y corta el enlace en la demo. */
@Composable
private fun SimPanel(vm: AppViewModel) {
    val sim by vm.sim.collectAsState()
    val s = sim ?: return
    val temp by s.temperature.collectAsState()
    val cut by s.signalCut.collectAsState()
    val rejected by vm.rejectedFrames.collectAsState()
    Gap(18)
    VCard(color = V.AmberBg, border = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Science, null, tint = V.Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Panel de simulacion", style = MaterialTheme.typography.titleMedium, color = V.Ink)
        }
        Text("Equivale al deslizador del TMP36 en Tinkercad.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        Gap(8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Temperatura del sensor", style = MaterialTheme.typography.bodyMedium, color = V.Ink, modifier = Modifier.weight(1f))
            Text("${AppViewModel.fmt(temp)} °C", style = MaterialTheme.typography.titleMedium, color = V.Amber)
        }
        Slider(
            value = temp, onValueChange = { s.temperature.value = (it * 2).toInt() / 2f },
            valueRange = 15f..40f,
            colors = SliderDefaults.colors(thumbColor = V.Amber, activeTrackColor = V.Amber),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cortar el enlace", style = MaterialTheme.typography.bodyMedium, color = V.Ink)
                Text("Deja de enviar tramas para probar \"Sin senal\"", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            Switch(checked = cut, onCheckedChange = { s.signalCut.value = it },
                colors = SwitchDefaults.colors(checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = V.Amber))
        }
        Gap(4)
        Text("Tramas rechazadas por checksum: $rejected", style = MaterialTheme.typography.bodySmall, color = V.Muted)
    }
}
