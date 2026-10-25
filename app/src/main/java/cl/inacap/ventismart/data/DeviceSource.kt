package cl.inacap.ventismart.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Origen de lecturas: el modulo Bluetooth real o el dispositivo simulado. */
interface DeviceSource {
    val readings: SharedFlow<Reading>
    val rejectedFrames: SharedFlow<String>
    val link: StateFlow<LinkState>
    suspend fun connect()
    fun send(mode: Mode)
    fun close()
}
