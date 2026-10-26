package cl.inacap.ventismart.data

import cl.inacap.ventismart.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Un registro del canal de ThingSpeak. */
data class TsPoint(val at: Long, val temp: Float, val power: Int, val alarm: Boolean, val auto: Boolean)

/**
 * Cliente de ThingSpeak (API REST, siempre por HTTPS).
 * Campos del canal: 1 temperatura, 2 potencia, 3 alarma (0/1), 4 modo (1 AUTO, 0 apagado).
 */
object ThingSpeak {
    const val BASE = "https://api.thingspeak.com"
    val channel: Long get() = BuildConfig.TS_CHANNEL
    /** La cuenta gratuita acepta un envio cada 15 s; se deja margen. */
    const val MIN_INTERVAL_MS = 16_000L

    /** Envia una lectura. Devuelve el numero de registro creado (0 si ThingSpeak la rechazo). */
    suspend fun upload(r: Reading): Long = withContext(Dispatchers.IO) {
        val body = listOf(
            "api_key" to BuildConfig.TS_WRITE_KEY,
            "field1" to String.format(Locale.US, "%.1f", r.temp),
            "field2" to r.power.toString(),
            "field3" to if (r.alarm) "1" else "0",
            "field4" to if (r.mode == Mode.AUTO) "1" else "0",
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val c = URL("$BASE/update").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
            c.inputStream.bufferedReader().readText().trim().toLongOrNull() ?: 0L
        } finally { c.disconnect() }
    }

    /** Lee los ultimos registros del canal (los consume cualquier telefono de la cuenta). */
    suspend fun fetch(results: Int = 40): List<TsPoint> = withContext(Dispatchers.IO) {
        val url = URL("$BASE/channels/${channel}/feeds.json?api_key=${BuildConfig.TS_READ_KEY}&results=$results")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            parse(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    fun parse(json: String): List<TsPoint> {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val feeds = JSONObject(json).optJSONArray("feeds") ?: return emptyList()
        return (0 until feeds.length()).mapNotNull { i ->
            val f = feeds.getJSONObject(i)
            val t = f.optString("field1").toFloatOrNull() ?: return@mapNotNull null
            TsPoint(
                at = runCatching { iso.parse(f.getString("created_at"))!!.time }.getOrDefault(0L),
                temp = t,
                power = f.optString("field2").toFloatOrNull()?.toInt() ?: 0,
                alarm = f.optString("field3") == "1",
                auto = f.optString("field4") != "0",
            )
        }
    }

    val publicUrl: String get() = "https://thingspeak.mathworks.com/channels/$channel"
}
