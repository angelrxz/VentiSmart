package cl.inacap.ventismart.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nodo A virtual. Replica el firmware de la Unidad 1 (nodoA_dormitorio.ino):
 * bajo 24 C apagado, entre 24 y 32 C potencia proporcional 35-100 %, desde 32 C
 * maximo y alarma. Emite una trama por segundo y la pasa por el mismo parser y
 * checksum que una trama Bluetooth real, para que la app las trate igual.
 */
class SimulatedDevice : DeviceSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private val _readings = MutableSharedFlow<Reading>(extraBufferCapacity = 8)
    override val readings: SharedFlow<Reading> = _readings
    private val _rejected = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val rejectedFrames: SharedFlow<String> = _rejected
    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    override val link: StateFlow<LinkState> = _link.asStateFlow()

    /** Temperatura que entrega el sensor virtual; se ajusta con el control deslizante. */
    val temperature = MutableStateFlow(22f)
    /** Simula que se corta el enlace (para mostrar la pantalla "Sin senal"). */
    val signalCut = MutableStateFlow(false)

    private var mode = Mode.AUTO

    override suspend fun connect() {
        _link.value = LinkState.CONNECTING
        delay(600)
        _link.value = LinkState.CONNECTED
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                if (!signalCut.value) {
                    val frame = Protocol.encodeState(step(temperature.value))
                    val parsed = Protocol.parse(frame)
                    if (parsed != null) _readings.emit(parsed) else _rejected.emit(frame)
                }
                delay(1000)
            }
        }
    }

    private fun step(t: Float): Reading {
        val power = when {
            mode == Mode.OFF -> 0
            t < Thresholds.VENT_START -> 0
            t >= Thresholds.ALARM -> 100
            else -> {
                val span = Thresholds.ALARM - Thresholds.VENT_START
                (Thresholds.PWM_MIN_PERCENT + (t - Thresholds.VENT_START) / span * (100 - Thresholds.PWM_MIN_PERCENT)).toInt()
            }
        }
        return Reading(t, power, mode, alarm = t >= Thresholds.ALARM)
    }

    override fun send(mode: Mode) {
        // El Arduino valida el checksum de la orden antes de aceptarla.
        val cmd = Protocol.command(mode).trim()
        val payload = cmd.substringBefore('*')
        val sum = cmd.substringAfter('*').toIntOrNull(16)
        if (sum == Protocol.checksum(payload)) this.mode = mode
    }

    override fun close() {
        loop?.cancel()
        _link.value = LinkState.DISCONNECTED
        scope.cancel()
    }
}
