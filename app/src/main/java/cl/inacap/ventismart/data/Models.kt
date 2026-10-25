package cl.inacap.ventismart.data

/** Modo de trabajo del ventilador, igual que en el Arduino: AUTO regula solo, OFF lo detiene. */
enum class Mode { AUTO, OFF }

/** Estado del enlace con el dispositivo IoT. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, LOST }

/** Una lectura valida recibida del Nodo A (trama con checksum correcto). */
data class Reading(
    val temp: Float,
    val power: Int,
    val mode: Mode,
    val alarm: Boolean,
    val at: Long = System.currentTimeMillis(),
)

/** Dispositivo registrado en la cuenta del usuario. */
data class Device(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val location: String = "",
    val address: String = "",
    val simulated: Boolean = false,
)

/** Estado compartido en la nube: lo publica el telefono conectado y lo leen los demas. */
data class LiveState(
    val temp: Float = 0f,
    val power: Int = 0,
    val mode: Mode = Mode.AUTO,
    val alarm: Boolean = false,
    val linkOk: Boolean = false,
    val updatedAt: Long = 0L,
    val gateway: String = "",
)

enum class EventType { LINK_UP, LINK_LOST, OVERHEAT, MANUAL_OFF, AUTO_ON, VENTILATING, AUTO_OFF_COLD, REMOTE_CMD }

data class EventItem(
    val id: String = "",
    val type: EventType = EventType.LINK_UP,
    val title: String = "",
    val detail: String = "",
    val at: Long = 0L,
) {
    val isAlert get() = type == EventType.OVERHEAT || type == EventType.LINK_LOST
    val isConnection get() = type == EventType.LINK_UP || type == EventType.LINK_LOST || type == EventType.REMOTE_CMD
}

/** Umbrales del firmware (Unidad 1). Se muestran en la app pero se ajustan en el Arduino. */
object Thresholds {
    const val VENT_START = 24f
    const val ALARM = 32f
    const val PWM_MIN_PERCENT = 35
    const val LINK_TIMEOUT_MS = 3000L
}
