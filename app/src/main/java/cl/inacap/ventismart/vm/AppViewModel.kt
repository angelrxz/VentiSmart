package cl.inacap.ventismart.vm

import cl.inacap.ventismart.data.ThingSpeak
import cl.inacap.ventismart.data.TsPoint

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.inacap.ventismart.data.BluetoothDevice
import cl.inacap.ventismart.data.Device
import cl.inacap.ventismart.data.DeviceSource
import cl.inacap.ventismart.data.EventItem
import cl.inacap.ventismart.data.EventType
import cl.inacap.ventismart.data.FirebaseRepo
import cl.inacap.ventismart.data.LinkState
import cl.inacap.ventismart.data.LiveState
import cl.inacap.ventismart.data.Mode
import cl.inacap.ventismart.data.Reading
import cl.inacap.ventismart.data.SecurePrefs
import cl.inacap.ventismart.data.SimulatedDevice
import cl.inacap.ventismart.data.Thresholds
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un modulo Bluetooth visto en el escaneo. */
data class BtCandidate(val name: String, val address: String, val bonded: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FirebaseRepo()
    val prefs = SecurePrefs(app)
    private val phoneName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** Reloj de la interfaz, para mostrar "actualizado hace X s". */
    val now: StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) { emit(System.currentTimeMillis()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    // ================= Autenticacion =================
    val user: StateFlow<FirebaseUser?> = repo.authState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.currentUser)

    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    fun signIn(email: String, password: String) = authCall { repo.signIn(email, password) }
    fun signUp(name: String, email: String, password: String) = authCall { repo.signUp(name, email, password) }
    fun clearAuthError() { _authError.value = null }

    fun signOut() {
        disconnect()
        repo.signOut()
    }

    fun updateName(name: String) = viewModelScope.launch { runCatching { repo.updateName(name) } }

    private fun authCall(block: suspend () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            try { block() } catch (e: Exception) { _authError.value = friendlyAuthError(e) }
            _authBusy.value = false
        }
    }

    private fun friendlyAuthError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode ?: ""
        return when {
            code.contains("INVALID_EMAIL") -> "El correo no tiene un formato válido."
            code.contains("WEAK_PASSWORD") -> "La contraseña debe tener al menos 6 caracteres."
            code.contains("EMAIL_ALREADY_IN_USE") -> "Ya existe una cuenta con ese correo. Inicia sesión."
            code.contains("INVALID_LOGIN_CREDENTIALS") || code.contains("WRONG_PASSWORD") ||
                code.contains("USER_NOT_FOUND") || code.contains("INVALID_CREDENTIAL") ->
                "Correo o contraseña incorrectos."
            code.contains("TOO_MANY_REQUESTS") -> "Demasiados intentos. Espera unos minutos e intenta de nuevo."
            e.message?.contains("network", true) == true -> "Sin conexión a internet. Revisa tu red."
            e.message?.contains("API key", true) == true -> "Firebase no está configurado en esta compilación (falta google-services.json)."
            else -> "No se pudo completar: ${e.message ?: "error desconocido"}"
        }
    }

    // ================= Dispositivos =================
    val devices: StateFlow<List<Device>> = user.flatMapLatest { u ->
        if (u == null) flowOf(emptyList()) else repo.observeDevices(u.uid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedId = MutableStateFlow<String?>(null)

    val selected: StateFlow<Device?> = combine(devices, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(id: String) { selectedId.value = id }

    // ================= Enlace local (este telefono es el "puente") =================
    private var source: DeviceSource? = null
    private var sourceJobs = mutableListOf<Job>()
    private val _gatewayFor = MutableStateFlow<String?>(null)
    val gatewayFor = _gatewayFor.asStateFlow()
    private val _localLink = MutableStateFlow(LinkState.DISCONNECTED)
    val localLink = _localLink.asStateFlow()
    private val _localReading = MutableStateFlow<Reading?>(null)
    private val _rejected = MutableStateFlow(0)
    val rejectedFrames = _rejected.asStateFlow()
    private var lastValidAt = 0L
    private var lastReading: Reading? = null

    /** Dispositivo simulado activo, para el panel de demostracion. */
    private val _sim = MutableStateFlow<SimulatedDevice?>(null)
    val sim = _sim.asStateFlow()

    fun connect(device: Device) {
        disconnect()
        val src: DeviceSource = if (device.simulated) {
            SimulatedDevice().also { _sim.value = it }
        } else {
            val adapter = btAdapter() ?: return
            BluetoothDevice(adapter, device.address)
        }
        source = src
        _gatewayFor.value = device.id
        lastValidAt = 0L
        lastReading = null
        _rejected.value = 0

        sourceJobs += viewModelScope.launch {
            src.link.collect { st ->
                val prev = _localLink.value
                _localLink.value = st
                if (st == LinkState.CONNECTED && prev != LinkState.CONNECTED) {
                    lastValidAt = System.currentTimeMillis()
                    log(device, EventType.LINK_UP, "Enlace establecido",
                        if (device.simulated) "Dispositivo simulado conectado · modo AUTO" else "HC-05 conectado · modo AUTO")
                }
            }
        }
        sourceJobs += viewModelScope.launch { src.readings.collect { onReading(device, it) } }
        sourceJobs += viewModelScope.launch { src.rejectedFrames.collect { _rejected.value += 1 } }
        // Vigilante: igual que el panel del Arduino, 3 s sin tramas validas = sin senal.
        sourceJobs += viewModelScope.launch {
            while (true) {
                delay(500)
                if (_localLink.value == LinkState.CONNECTED && lastValidAt > 0 &&
                    System.currentTimeMillis() - lastValidAt > Thresholds.LINK_TIMEOUT_MS
                ) {
                    _localLink.value = LinkState.LOST
                    val r = lastReading
                    log(device, EventType.LINK_LOST, "Pérdida de señal",
                        "3 s sin mensajes válidos · Último ${if (r?.mode == Mode.OFF) "APAGADO" else "AUTO"}")
                    publish(device, r, linkOk = false)
                    notify(prefs.notifySignal, "Sin señal de ${device.name}", "No llegan lecturas desde hace 3 segundos.")
                }
            }
        }
        // Ordenes que llegan desde otro telefono a traves de la nube.
        val uid = user.value?.uid
        if (uid != null) {
            sourceJobs += viewModelScope.launch {
                // No se comparan relojes entre telefonos: la primera lectura del documento
                // marca lo que ya habia (orden vieja, se descarta) y desde ahi cada orden
                // distinta se ejecuta una vez y se borra.
                var first = true
                var lastAt = -1L
                repo.observeCommand(uid, device.id).collect { cmd ->
                    if (first) {
                        first = false
                        lastAt = cmd?.third ?: -1L
                        if (cmd != null) repo.clearCommand(uid, device.id)
                        return@collect
                    }
                    if (cmd != null && cmd.third != lastAt) {
                        lastAt = cmd.third
                        source?.send(cmd.first)
                        log(device, EventType.REMOTE_CMD, "Orden remota recibida",
                            "${if (cmd.first == Mode.OFF) "Apagar manual" else "Reactivar automatico"} desde ${cmd.second}")
                        repo.clearCommand(uid, device.id)
                    }
                }
            }
        }
        viewModelScope.launch { src.connect() }
    }

    fun disconnect() {
        sourceJobs.forEach { it.cancel() }
        sourceJobs.clear()
        source?.close()
        source = null
        _sim.value = null
        _gatewayFor.value = null
        _localLink.value = LinkState.DISCONNECTED
        _localReading.value = null
    }

    private fun onReading(device: Device, r: Reading) {
        val prev = lastReading
        lastValidAt = System.currentTimeMillis()
        if (_localLink.value == LinkState.LOST) {
            _localLink.value = LinkState.CONNECTED
            log(device, EventType.LINK_UP, "Enlace recuperado", "Llegan lecturas de nuevo · ${fmt(r.temp)} °C")
        }
        lastReading = r
        _localReading.value = r
        detectEvents(device, prev, r)
        publish(device, r, linkOk = true)
        uploadToThingSpeak(r)
    }

    // ================= ThingSpeak =================
    /** Estado del envio a ThingSpeak que muestra la pantalla "Nube". */
    data class TsStatus(val lastOkAt: Long = 0L, val lastEntry: Long = 0L, val sent: Int = 0, val error: String? = null)

    private val _tsStatus = MutableStateFlow(TsStatus())
    val tsStatus = _tsStatus.asStateFlow()
    private var tsLastTry = 0L

    /** El telefono puente sube una lectura cada 16 s (limite de la cuenta gratuita). */
    private fun uploadToThingSpeak(r: Reading) {
        val t = System.currentTimeMillis()
        if (t - tsLastTry < ThingSpeak.MIN_INTERVAL_MS) return
        tsLastTry = t
        viewModelScope.launch {
            val entry = runCatching { ThingSpeak.upload(r) }.getOrElse { -1L }
            _tsStatus.value = when {
                entry > 0 -> TsStatus(System.currentTimeMillis(), entry, _tsStatus.value.sent + 1, null)
                entry == 0L -> _tsStatus.value.copy(error = "ThingSpeak rechazó el envío (límite de 15 s).")
                else -> _tsStatus.value.copy(error = "Sin conexión con ThingSpeak.")
            }
            if (entry > 0) refreshThingSpeak()
        }
    }

    private val _tsFeed = MutableStateFlow<List<TsPoint>>(emptyList())
    val tsFeed = _tsFeed.asStateFlow()
    private val _tsLoading = MutableStateFlow(false)
    val tsLoading = _tsLoading.asStateFlow()
    private val _tsReadError = MutableStateFlow<String?>(null)
    val tsReadError = _tsReadError.asStateFlow()

    /** Cualquier telefono de la cuenta lee el historial del canal. */
    fun refreshThingSpeak() {
        if (_tsLoading.value) return
        viewModelScope.launch {
            _tsLoading.value = true
            runCatching { ThingSpeak.fetch(40) }
                .onSuccess { _tsFeed.value = it; _tsReadError.value = null }
                .onFailure { _tsReadError.value = "No se pudo leer el canal. Revisa la conexión a internet." }
            _tsLoading.value = false
        }
    }

    /** Registra en el historial los cambios de estado, como en la pantalla de alertas. */
    private fun detectEvents(device: Device, p: Reading?, r: Reading) {
        if (r.alarm && p?.alarm != true) {
            log(device, EventType.OVERHEAT, "Sobrecalentamiento", "${fmt(r.temp)} °C · ${r.power} % · LED rojo y buzzer")
            notify(prefs.notifyOverheat, "Sobrecalentamiento en ${device.name}",
                "${fmt(r.temp)} °C. El ventilador está al máximo y la alarma activa.")
        }
        if (p != null && r.mode == Mode.OFF && p.mode != Mode.OFF) {
            log(device, EventType.MANUAL_OFF, "Apagado manual", "M=O confirmado · ${fmt(r.temp)} °C · 0 %")
        }
        if (p != null && r.mode == Mode.AUTO && p.mode == Mode.OFF) {
            log(device, EventType.AUTO_ON, "Automático reactivado", "M=A confirmado · ${fmt(r.temp)} °C")
        }
        if (r.mode == Mode.AUTO && !r.alarm && r.power > 0 && (p == null || p.power == 0 || p.alarm)) {
            log(device, EventType.VENTILATING, "Automático · ventilando", "${fmt(r.temp)} °C · potencia aproximada de ${r.power} %")
        }
        if (p != null && r.mode == Mode.AUTO && r.power == 0 && p.power > 0 && p.mode == Mode.AUTO) {
            log(device, EventType.AUTO_OFF_COLD, "Apagado automático", "${fmt(r.temp)} °C · 0 % · por debajo de 24 °C")
        }
    }

    private fun publish(device: Device, r: Reading?, linkOk: Boolean) {
        val uid = user.value?.uid ?: return
        val base = r ?: return
        repo.publishLive(uid, device.id, LiveState(base.temp, base.power, base.mode, base.alarm, linkOk, System.currentTimeMillis(), phoneName))
    }

    private fun log(device: Device, type: EventType, title: String, detail: String) {
        val uid = user.value?.uid ?: return
        repo.logEvent(uid, device.id, EventItem(type = type, title = title, detail = detail, at = System.currentTimeMillis()))
    }

    // ================= Estado que ve la interfaz =================
    /** Momento (reloj de ESTE telefono) en que llego la ultima actualizacion desde la nube. */
    private var remoteReceivedAt = 0L

    private val remoteLive: StateFlow<LiveState?> = combine(user, selected) { u, d -> u to d }
        .flatMapLatest { (u, d) ->
            if (u == null || d == null) flowOf(null)
            else repo.observeLive(u.uid, d.id).map { remoteReceivedAt = System.currentTimeMillis(); it }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** true si este telefono es el que esta conectado al dispositivo seleccionado. */
    val isGateway: StateFlow<Boolean> = combine(selected, gatewayFor) { d, g -> d != null && d.id == g }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Estado que se dibuja. En el telefono conectado viene directo del dispositivo;
     * en el otro telefono viene de Firestore, y se considera sin senal si pasan 8 s
     * sin actualizaciones (el puente publica cada segundo).
     */
    val live: StateFlow<LiveState?> = combine(isGateway, _localReading, _localLink, remoteLive, now) { gw, r, link, remote, t ->
        if (gw) {
            r?.let { LiveState(it.temp, it.power, it.mode, it.alarm, link == LinkState.CONNECTED, it.at, phoneName) }
        } else {
            remote?.let { it.copy(linkOk = it.linkOk && t - remoteReceivedAt < 8000) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val events: StateFlow<List<EventItem>> = combine(user, selected) { u, d -> u to d }
        .flatMapLatest { (u, d) -> if (u == null || d == null) emptyFlow() else repo.observeEvents(u.uid, d.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Envia una orden. Si este telefono es el puente va directo; si no, por la nube. */
    fun sendCommand(mode: Mode) {
        val d = selected.value ?: return
        if (isGateway.value) {
            source?.send(mode)
        } else {
            val uid = user.value?.uid ?: return
            repo.sendCommand(uid, d.id, mode, phoneName)
        }
    }

    // ================= Registro de dispositivos =================
    private val _pending = MutableStateFlow<BtCandidate?>(null)
    val pending = _pending.asStateFlow()
    fun choose(c: BtCandidate) { _pending.value = c }

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun registerDevice(name: String, type: String, location: String, pin: String, onDone: (String) -> Unit) {
        val c = _pending.value ?: return
        val uid = user.value?.uid ?: return
        val simulated = c.address == SIM_ADDRESS
        viewModelScope.launch {
            _saving.value = true
            if (!simulated) {
                if (pin.isNotBlank()) prefs.savePin(c.address, pin)
                bondIfNeeded(c.address)
            }
            val id = runCatching {
                repo.addDevice(uid, Device(name = name, type = type, location = location, address = c.address, simulated = simulated))
            }.getOrNull()
            _saving.value = false
            if (id != null) {
                selectedId.value = id
                connect(Device(id, name, type, location, c.address, simulated))
                onDone(id)
            }
        }
    }

    fun deleteDevice(id: String) {
        val uid = user.value?.uid ?: return
        if (gatewayFor.value == id) disconnect()
        viewModelScope.launch { runCatching { repo.deleteDevice(uid, id) } }
    }

    // ================= Bluetooth: escaneo =================
    private val _candidates = MutableStateFlow<List<BtCandidate>>(emptyList())
    val candidates = _candidates.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    fun btAdapter(): BluetoothAdapter? =
        (getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun bluetoothOn() = btAdapter()?.isEnabled == true

    fun hasBtPermission(): Boolean {
        val ctx = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                android.bluetooth.BluetoothDevice.ACTION_FOUND -> {
                    val dev: android.bluetooth.BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= 33)
                            intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    val name = runCatching { dev?.name }.getOrNull() ?: return
                    val addr = dev?.address ?: return
                    if (_candidates.value.none { it.address == addr }) {
                        _candidates.value = _candidates.value + BtCandidate(name, addr, false)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
            }
        }
    }
    private var receiverOn = false

    @SuppressLint("MissingPermission")
    fun scan() {
        val list = mutableListOf(BtCandidate(SIM_NAME, SIM_ADDRESS, true))
        val adapter = btAdapter()
        if (adapter != null && adapter.isEnabled && hasBtPermission()) {
            runCatching { adapter.bondedDevices }.getOrNull()?.forEach {
                list += BtCandidate(runCatching { it.name }.getOrNull() ?: it.address, it.address, true)
            }
            if (!receiverOn) {
                val f = IntentFilter().apply {
                    addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                ContextCompat.registerReceiver(getApplication(), receiver, f, ContextCompat.RECEIVER_EXPORTED)
                receiverOn = true
            }
            runCatching { adapter.cancelDiscovery(); _scanning.value = adapter.startDiscovery() }
        }
        _candidates.value = list
    }

    @SuppressLint("MissingPermission")
    private fun bondIfNeeded(address: String) {
        val adapter = btAdapter() ?: return
        runCatching {
            val dev = adapter.getRemoteDevice(address)
            if (dev.bondState != android.bluetooth.BluetoothDevice.BOND_BONDED) dev.createBond()
        }
    }

    // ================= Notificaciones =================
    private fun notify(enabled: Boolean, title: String, text: String) {
        if (!enabled) return
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alertas de VentiSmart", NotificationManager.IMPORTANCE_HIGH))
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(title.hashCode(), n) }
    }

    override fun onCleared() {
        disconnect()
        if (receiverOn) runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        super.onCleared()
    }

    companion object {
        const val SIM_ADDRESS = "SIM:00:00:00:7A:2C"
        const val SIM_NAME = "VentiSmart_Dormitorio (simulado)"
        private const val CHANNEL = "ventismart_alertas"
        fun fmt(t: Float) = String.format(java.util.Locale.US, "%.1f", t)
    }
}
