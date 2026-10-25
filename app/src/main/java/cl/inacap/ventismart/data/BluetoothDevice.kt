package cl.inacap.ventismart.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Enlace real con el modulo HC-05 por Bluetooth clasico, perfil SPP (puerto serie).
 * Lee lineas de texto, valida cada trama con el checksum y descarta las corruptas.
 * Los permisos BLUETOOTH_CONNECT/SCAN se piden en la interfaz antes de llamar aqui.
 */
@SuppressLint("MissingPermission")
class BluetoothDevice(
    private val adapter: BluetoothAdapter,
    private val address: String,
) : DeviceSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null

    private val _readings = MutableSharedFlow<Reading>(extraBufferCapacity = 16)
    override val readings: SharedFlow<Reading> = _readings
    private val _rejected = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val rejectedFrames: SharedFlow<String> = _rejected
    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    override val link: StateFlow<LinkState> = _link.asStateFlow()

    override suspend fun connect() {
        _link.value = LinkState.CONNECTING
        val ok = withContext(Dispatchers.IO) {
            try {
                adapter.cancelDiscovery()
                val dev = adapter.getRemoteDevice(address)
                val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        if (!ok) { _link.value = LinkState.LOST; return }
        _link.value = LinkState.CONNECTED
        readJob = scope.launch {
            val reader = socket?.inputStream?.bufferedReader() ?: return@launch
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val r = Protocol.parse(line)
                    if (r != null) _readings.emit(r) else _rejected.emit(line)
                }
            } catch (_: IOException) { }
            _link.value = LinkState.LOST
        }
    }

    override fun send(mode: Mode) {
        scope.launch {
            try {
                socket?.outputStream?.write(Protocol.command(mode).toByteArray())
            } catch (_: IOException) {
                _link.value = LinkState.LOST
            }
        }
    }

    override fun close() {
        readJob?.cancel()
        try { socket?.close() } catch (_: IOException) { }
        _link.value = LinkState.DISCONNECTED
        scope.cancel()
    }

    companion object {
        /** UUID estandar del perfil SPP, el que usa el HC-05. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
