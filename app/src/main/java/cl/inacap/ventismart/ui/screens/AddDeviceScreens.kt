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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel
import cl.inacap.ventismart.vm.BtCandidate

// ===================== 02 Agregar dispositivo =====================
@Composable
fun ScanScreen(vm: AppViewModel, onBack: () -> Unit, onChosen: () -> Unit) {
    val candidates by vm.candidates.collectAsState()
    val scanning by vm.scanning.collectAsState()
    var permTick by remember { mutableIntStateOf(0) }

    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++; vm.scan()
    }
    LaunchedEffect(Unit) { vm.scan() }
    val hasPerm = remember(permTick) { vm.hasBtPermission() }
    val btOn = remember(permTick, candidates) { vm.bluetoothOn() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Agregar dispositivo", "Buscar por Bluetooth", onBack)
        Steps(1)
        Gap(12)
        VCard(color = V.Mint, border = null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).border(1.dp, V.MintStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.BluetoothSearching, null, tint = V.Teal, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Gap(14)
            Text(if (scanning) "Buscando cerca de ti..." else "Dispositivos disponibles", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            Gap(4)
            Text("Enciende el modulo y acercate a menos de 10 m. Manten Bluetooth activado.", style = MaterialTheme.typography.bodyMedium, color = V.Muted)
            Gap(8)
            when {
                !hasPerm -> StatusDot("Falta permiso de Bluetooth", V.Amber)
                !btOn -> StatusDot("Bluetooth apagado · activalo en Ajustes", V.Amber)
                else -> StatusDot("Bluetooth activo · permiso concedido", V.Ok)
            }
            if (!hasPerm) {
                Gap(10)
                PrimaryButton("Permitir Bluetooth", Icons.Filled.Bluetooth) { launcher.launch(perms) }
            }
        }
        Gap(18)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dispositivos encontrados", style = MaterialTheme.typography.titleMedium, color = V.Ink, modifier = Modifier.weight(1f))
            Text("${candidates.size} disponible${if (candidates.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(10)
        candidates.forEach { c ->
            CandidateCard(c) { vm.choose(c); onChosen() }
            Gap(10)
        }
        SecondaryButton("Buscar de nuevo", Icons.Filled.Refresh) { if (vm.hasBtPermission()) vm.scan() else launcher.launch(perms) }
        FootNote("Vincula solo modulos que reconozcas. No se necesita Wi-Fi, router ni conexion a internet para el enlace Bluetooth.")
    }
}

@Composable
private fun CandidateCard(c: BtCandidate, onSelect: () -> Unit) {
    val sim = c.address == AppViewModel.SIM_ADDRESS
    VCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(if (sim) Icons.Filled.Toys else Icons.Filled.Memory, null, tint = V.Teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Text(if (sim) "Nodo A virtual · mismo protocolo y checksum" else "HC-05 · Bluetooth serial SPP",
                    style = MaterialTheme.typography.bodySmall, color = V.Muted)
                Text(if (sim) "Para demostrar sin hardware" else "Direccion terminada en ${c.address.takeLast(5)}${if (c.bonded) " · ya emparejado" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = V.Faint)
            }
        }
        Gap(12)
        PrimaryButton("Seleccionar dispositivo", Icons.AutoMirrored.Filled.ArrowForward, onClick = onSelect)
    }
}

@Composable
private fun Steps(active: Int) {
    Row(Modifier.fillMaxWidth()) {
        listOf("1 · Buscar", "2 · Vincular", "3 · Listo").forEachIndexed { i, s ->
            Text(
                s, style = MaterialTheme.typography.labelSmall,
                color = if (i + 1 == active) V.Teal else V.Muted, modifier = Modifier.weight(1f),
            )
        }
    }
}

// ===================== 03 Vincular dispositivo =====================
@Composable
fun PairScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val c by vm.pending.collectAsState()
    val saving by vm.saving.collectAsState()
    val cand = c ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val sim = cand.address == AppViewModel.SIM_ADDRESS

    var name by remember { mutableStateOf("Ventilador dormitorio") }
    var type by remember { mutableStateOf("Ventilador DC PWM") }
    var location by remember { mutableStateOf("Dormitorio") }
    var pin by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Vincular dispositivo", "Paso 2 de 3 · configura tu modulo", onBack)
        StatusDot("${cand.name}${if (sim) "" else " · HC-05"}", V.Ok)
        Gap(14)
        Field("Nombre del dispositivo", name, { name = it }, "Ventilador dormitorio")
        Gap(12)
        Dropdown("Tipo", type, listOf("Ventilador DC PWM", "Ventilador de techo", "Extractor")) { type = it }
        Gap(12)
        Dropdown("Ubicacion", location, listOf("Dormitorio", "Living", "Oficina", "Sala de servidores")) { location = it }
        Gap(12)
        Text("Metodo de conexion", style = MaterialTheme.typography.labelSmall, color = V.Muted)
        Gap(6)
        ReadOnlyBox(if (sim) "Simulacion · protocolo SPP" else "Bluetooth · HC-05 / SPP", Icons.Filled.Lock)
        Gap(14)
        if (!sim) {
            VCard {
                Text("Emparejamiento Android", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Gap(4)
                Text("Configura un nombre y un PIN propios en el HC-05 antes de vincular. Introduce aqui ese PIN; Android pedira confirmar el emparejamiento.",
                    style = MaterialTheme.typography.bodySmall, color = V.Muted)
                Gap(10)
                Text("PIN propio del modulo", style = MaterialTheme.typography.labelSmall, color = V.Muted)
                Gap(6)
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = V.Teal)
                        }
                    },
                    colors = fieldColors(),
                )
                FootNote("PIN oculto · se guarda cifrado solo en este telefono. No se muestra ningun valor de fabrica.")
            }
            FootNote("Bluetooth SPP no cifrado. Vincula solo dispositivos autorizados; el PIN no implica cifrado del enlace.")
        } else {
            VCard(color = V.Mint, border = null) {
                Text("Dispositivo de demostracion", style = MaterialTheme.typography.titleMedium, color = V.Ink)
                Gap(4)
                Text("Reproduce el Nodo A de la Unidad 1 con las mismas reglas (24 °C y 32 °C) y el mismo protocolo con checksum. Podras mover la temperatura desde la pantalla de control.",
                    style = MaterialTheme.typography.bodySmall, color = V.Muted)
            }
        }
        Gap(16)
        PrimaryButton(
            text = if (saving) "Vinculando..." else if (sim) "Vincular dispositivo" else "Emparejar y vincular",
            icon = Icons.Filled.Bluetooth,
            enabled = !saving && name.isNotBlank() && (sim || pin.length >= 4),
        ) { vm.registerDevice(name, type, location, pin) { onDone() } }
    }
}

// ===================== 04 Dispositivo vinculado =====================
@Composable
fun PairedScreen(vm: AppViewModel, onBack: () -> Unit, onControl: () -> Unit, onHome: () -> Unit) {
    val d by vm.selected.collectAsState()
    val live by vm.live.collectAsState()
    val dev = d

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        ScreenHeader("Dispositivo vinculado", "Paso 3 de 3 · todo listo", onBack)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(V.Mint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = V.Teal, modifier = Modifier.size(28.dp))
            }
        }
        Gap(12)
        Text("Ya estan conectados", style = MaterialTheme.typography.headlineSmall, color = V.Ink,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Tu dormitorio tiene un nuevo aliado. Las lecturas llegaran cada segundo.",
            style = MaterialTheme.typography.bodyMedium, color = V.Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Gap(16)
        if (dev != null) {
            VCard {
                Text(dev.name, style = MaterialTheme.typography.titleMedium, color = V.Ink)
                StatusDot(if (live?.linkOk == true) (if (dev.simulated) "Simulacion conectada" else "Bluetooth conectado") else "Conectando...",
                    if (live?.linkOk == true) V.Ok else V.Amber)
                Gap(8)
                LabelValue("Ubicacion", dev.location)
                LabelValue("Tipo", dev.type)
                LabelValue("Modulo", if (dev.simulated) "VentiSmart_Dormitorio (simulado)" else "HC-05 · ${dev.address.takeLast(5)}")
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(live?.let { "${AppViewModel.fmt(it.temp)} °C" } ?: "-- °C", style = MaterialTheme.typography.headlineSmall,
                        color = V.Ink, modifier = Modifier.weight(1f))
                    if (live != null) StatusDot("${if (live!!.mode.name == "OFF") "MANUAL" else "AUTO"} · ${live!!.power} %", V.Ok)
                }
            }
        }
        Gap(12)
        VCard(color = V.Mint, border = null) {
            Text("Control cercano, autonomia local", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            Gap(4)
            Text("La app funciona por Bluetooth, a unos 10 m. El modo automatico sigue en el Arduino aunque pierdas el enlace.",
                style = MaterialTheme.typography.bodySmall, color = V.Muted)
        }
        Gap(16)
        PrimaryButton("Abrir Control del ventilador", Icons.Filled.Toys, onClick = onControl)
        Gap(10)
        SecondaryButton("Volver a Inicio", Icons.Filled.Home, onClick = onHome)
    }
}

// ===================== utilidades de formulario =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelSmall, color = V.Muted)
    Gap(6)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, singleLine = true,
            trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null, tint = V.Teal) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@Composable
private fun ReadOnlyBox(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, V.Line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = V.Ink, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = V.Teal, modifier = Modifier.size(18.dp))
    }
}
