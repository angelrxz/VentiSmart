package cl.inacap.ventismart.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.data.EventItem
import cl.inacap.ventismart.data.EventType
import cl.inacap.ventismart.data.Thresholds
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===================== 05 Alertas e historial =====================
@Composable
fun AlertsScreen(vm: AppViewModel, onBack: () -> Unit, onControl: () -> Unit) {
    val d by vm.selected.collectAsState()
    val events by vm.events.collectAsState()
    val live by vm.live.collectAsState()
    var filter by remember { mutableIntStateOf(0) }

    val shown = when (filter) {
        1 -> events.filter { it.isAlert }
        2 -> events.filter { it.isConnection }
        else -> events
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Alertas e historial", d?.name ?: "Sin dispositivo", onBack)
        val l = live
        when {
            l == null -> VCard(color = V.Mint, border = null) { StatusDot("Sin lecturas todavia", V.Muted) }
            !l.linkOk -> VCard(color = V.AmberBg, border = null) {
                StatusDot("Enlace perdido · se conservan los eventos", V.Amber)
                Gap(4)
                Text("Ultimo estado: ${AppViewModel.fmt(l.temp)} °C · ${l.power} %.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            l.alarm -> VCard(color = V.RedBg, border = null) {
                StatusDot("Alerta activa · sobrecalentamiento", V.Red)
                Gap(4)
                Text("Ahora: ${AppViewModel.fmt(l.temp)} °C · AUTO · ${l.power} %.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
            else -> VCard(color = V.Mint, border = null) {
                StatusDot("Sin alertas activas · enlace estable", V.Ok)
                Gap(4)
                Text("Ahora: ${AppViewModel.fmt(l.temp)} °C · ${modeText(l)} · ${l.power} %. Los eventos se guardan en tu cuenta y los ve cualquier telefono con tu sesion.",
                    style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
        }
        Gap(14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Todos", filter == 0) { filter = 0 }
            Chip("Alertas", filter == 1) { filter = 1 }
            Chip("Conexion", filter == 2) { filter = 2 }
        }
        Gap(12)
        Row {
            Text("Hoy · ${SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CL")).format(Date())}",
                style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
            Text("${shown.size}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(8)
        if (shown.isEmpty()) {
            VCard { Text("Todavia no hay eventos en esta categoria.", style = MaterialTheme.typography.bodyMedium, color = V.Muted) }
        } else {
            VCard {
                shown.forEachIndexed { i, e ->
                    EventRow(e)
                    if (i < shown.lastIndex) HorizontalDivider(color = V.Line, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
        Gap(14)
        Row(Modifier.fillMaxWidth().clickable(onClick = onControl).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Abrir Control del ventilador", style = MaterialTheme.typography.labelLarge, color = V.Teal, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = V.Teal)
        }
        FootNote("Registro de eventos recibidos; no se inventan lecturas durante la desconexion.")
    }
}

@Composable
private fun EventRow(e: EventItem) {
    val (icon, tint) = eventIcon(e.type)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(V.Bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, color = if (e.type == EventType.OVERHEAT) V.Red else V.Ink)
            Text(e.detail, style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Text(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(e.at)), style = MaterialTheme.typography.bodySmall, color = V.Faint)
    }
}

private fun eventIcon(t: EventType): Pair<ImageVector, Color> = when (t) {
    EventType.LINK_UP -> Icons.Filled.Bluetooth to V.Teal
    EventType.LINK_LOST -> Icons.Filled.BluetoothDisabled to V.Amber
    EventType.OVERHEAT -> Icons.Filled.Warning to V.Red
    EventType.MANUAL_OFF -> Icons.Filled.PowerSettingsNew to V.Muted
    EventType.AUTO_ON -> Icons.Filled.Autorenew to V.Teal
    EventType.VENTILATING -> Icons.Filled.Toys to V.Teal
    EventType.AUTO_OFF_COLD -> Icons.Filled.Thermostat to V.Teal
    EventType.REMOTE_CMD -> Icons.Filled.Cloud to V.Teal
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (active) V.Teal else Color.White)
            .then(if (active) Modifier else Modifier.border(1.dp, V.Line, RoundedCornerShape(50)))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else V.Ink)
    }
}

// ===================== 06 Configuracion =====================
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val user by vm.user.collectAsState()
    val devices by vm.devices.collectAsState()
    var overheat by remember { mutableStateOf(vm.prefs.notifyOverheat) }
    var signal by remember { mutableStateOf(vm.prefs.notifySignal) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotifications() { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val name = user?.displayName?.takeIf { it.isNotBlank() } ?: "Usuario"
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Configuracion", "Preferencias de la app", onBack)
        VCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(V.Mint), contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.titleMedium, color = V.Teal)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = V.Ink)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Editar perfil", tint = V.Teal) }
            }
        }
        Gap(12)
        VCard {
            Text("Temperatura y apariencia", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            Gap(8)
            SettingRow("Unidad de temperatura", "Celsius · °C")
            SettingRow("Tema", "Claro")
        }
        Gap(12)
        VCard(color = V.AmberBg, border = null) {
            StatusDot("Umbrales · definidos en el firmware", V.Amber)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LockedValue("Inicio de ventilacion", "${Thresholds.VENT_START.toInt()} °C", Modifier.weight(1f))
                LockedValue("Alarma termica", "${Thresholds.ALARM.toInt()} °C", Modifier.weight(1f))
            }
            Gap(8)
            Text("Se muestran como referencia. Hoy se ajustan en el Arduino; cambiarlos desde la app requiere un comando de umbral que el protocolo aun no tiene.",
                style = MaterialTheme.typography.bodySmall, color = V.Amber)
        }
        Gap(12)
        VCard {
            Text("Notificaciones del telefono", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            Gap(4)
            ToggleRow("Sobrecalentamiento", overheat) { overheat = it; vm.prefs.notifyOverheat = it; if (it) askNotifications() }
            ToggleRow("Perdida de senal", signal) { signal = it; vm.prefs.notifySignal = it; if (it) askNotifications() }
            Text("Activas con permiso de Android. No sustituyen el LED rojo ni el buzzer del dispositivo.", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(12)
        VCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = V.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Seguridad · ISO/IEC 27400", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            }
            Gap(6)
            SecurityItem("Acceso con cuenta propia (Firebase Authentication)")
            SecurityItem("Cada cuenta solo ve sus equipos (reglas de Firestore)")
            SecurityItem("Trafico a la nube solo por HTTPS/TLS")
            SecurityItem("PIN del modulo cifrado con AES-256 en el telefono")
            SecurityItem("Tramas verificadas con checksum XOR; se descartan las corruptas")
            SecurityItem("Permisos de Bluetooth y notificaciones pedidos solo al usarlos")
        }
        if (devices.isNotEmpty()) {
            Gap(12)
            VCard {
                Text("Dispositivos vinculados", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Gap(4)
                devices.forEach { dv ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dv.name, style = MaterialTheme.typography.bodyLarge, color = V.Ink)
                            Text(if (dv.simulated) "Simulado" else "HC-05 · ${dv.address.takeLast(5)}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
                        }
                        IconButton(onClick = { confirmDelete = dv.id }) { Icon(Icons.Filled.DeleteOutline, "Desvincular", tint = V.Red) }
                    }
                }
            }
        }
        Gap(16)
        SecondaryButton("Cerrar sesion", Icons.AutoMirrored.Filled.Logout, color = V.Red) { vm.signOut() }
        FootNote("Tramas verificadas con checksum XOR: se descartan las corruptas. El enlace Bluetooth SPP no es cifrado.")
    }

    if (editing) {
        var newName by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Editar perfil") },
            text = { Column { Field("Nombre", newName, { newName = it }, "Tu nombre") } },
            confirmButton = { TextButton(onClick = { vm.updateName(newName); editing = false }, enabled = newName.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Desvincular dispositivo") },
            text = { Text("Se borrara de tu cuenta junto con su historial. El Arduino sigue funcionando por su cuenta.") },
            confirmButton = { TextButton(onClick = { vm.deleteDevice(id); confirmDelete = null }) { Text("Desvincular", color = V.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = V.Ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = V.Teal)
        Icon(Icons.Filled.ChevronRight, null, tint = V.Teal, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LockedValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = V.Amber)
        Gap(4)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Lock, null, tint = V.Amber, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = V.Ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = V.Teal))
    }
}

@Composable
private fun SecurityItem(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(V.Teal))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = V.Muted)
    }
}
