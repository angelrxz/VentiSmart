package cl.inacap.ventismart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cl.inacap.ventismart.data.Device
import cl.inacap.ventismart.data.LinkState
import cl.inacap.ventismart.data.LiveState
import cl.inacap.ventismart.data.Mode
import cl.inacap.ventismart.ui.screens.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.ui.theme.VentiTheme

/*
 * Vistas previas para Android Studio (modo "Split" o "Design").
 * Usan datos de ejemplo, así que no necesitan Firebase ni Bluetooth.
 */

private val now = 1_760_000_000_000L
private val demo = Device("1", "VentiSmart_Dormitorio (simulado)", "Ventilador", "Dormitorio", "SIM:00:00:00:7A:2C", simulated = true)
private val normal = LiveState(27.3f, 65, Mode.AUTO, false, true, now - 2_000, "Teléfono 1")
private val hot = LiveState(33.1f, 100, Mode.AUTO, true, true, now - 1_000, "Teléfono 1")
private val off = LiveState(26.0f, 0, Mode.OFF, false, true, now - 3_000, "Teléfono 1")

@Composable
private fun Frame(content: @Composable () -> Unit) = VentiTheme {
    Box(Modifier.fillMaxSize().background(V.Bg)) { content() }
}

@Preview(name = "01 Inicio de sesión", showBackground = true, widthDp = 380, heightDp = 820)
@Composable fun PrevLogin() = Frame { LoginContent(false, null, { _, _ -> }, { _, _, _ -> }, {}) }

@Preview(name = "02 Inicio sin dispositivos", showBackground = true, widthDp = 380, heightDp = 820)
@Composable fun PrevHomeEmpty() = Frame {
    HomeContent("Miguel Cabrera", emptyList(), null, null, false, false, LinkState.DISCONNECTED, now, {}, {}, {}, {})
}

@Preview(name = "03 Inicio conectado", showBackground = true, widthDp = 380, heightDp = 820)
@Composable fun PrevHome() = Frame {
    HomeContent("Miguel Cabrera", listOf(demo), demo, normal, true, true, LinkState.CONNECTED, now, {}, {}, {}, {})
}

@Preview(name = "04 Inicio remoto (segundo teléfono)", showBackground = true, widthDp = 380, heightDp = 820)
@Composable fun PrevHomeRemote() = Frame {
    HomeContent("Pablo", listOf(demo), demo, normal, false, false, LinkState.DISCONNECTED, now, {}, {}, {}, {})
}

@Preview(name = "05 Control automático", showBackground = true, widthDp = 380, heightDp = 1000)
@Composable fun PrevControl() = Frame { ControlScreen(ControlActions(), demo, normal, true, now, {}) }

@Preview(name = "06 Control apagado manual", showBackground = true, widthDp = 380, heightDp = 1000)
@Composable fun PrevControlOff() = Frame { ControlScreen(ControlActions(), demo, off, true, now, {}) }

@Preview(name = "07 Sobrecalentamiento", showBackground = true, widthDp = 380, heightDp = 1000)
@Composable fun PrevOverheat() = Frame { OverheatScreen(ControlActions(), demo, hot, true, now, {}, {}) }

@Preview(name = "08 Sin señal", showBackground = true, widthDp = 380, heightDp = 900)
@Composable fun PrevNoSignal() = Frame { NoSignalScreen(ControlActions(), demo, normal.copy(linkOk = false), true, now, {}) }

@Preview(name = "09 Conectando", showBackground = true, widthDp = 380, heightDp = 600)
@Composable fun PrevConnecting() = Frame { Connecting(demo) {} }

private val tsDemo = List(30) { i ->
    val t = 23.0f + i * 0.38f - (if (i > 24) (i - 24) * 0.9f else 0f)
    cl.inacap.ventismart.data.TsPoint(now - (30 - i) * 16_000L, t,
        when { t < 24f -> 0; t >= 32f -> 100; else -> (35 + (t - 24f) / 8f * 65f).toInt() }, t >= 32f, true)
}

@Preview(name = "10 Nube ThingSpeak", showBackground = true, widthDp = 380, heightDp = 1100)
@Composable fun PrevCloud() = Frame {
    CloudContent(tsDemo, cl.inacap.ventismart.vm.AppViewModel.TsStatus(now - 5_000, 128, 12), false, null, true, now, {}, {}, {})
}
