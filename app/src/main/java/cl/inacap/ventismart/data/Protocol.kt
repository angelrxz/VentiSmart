package cl.inacap.ventismart.data

/**
 * Protocolo de texto del Nodo A, el mismo de la Unidad 1:
 *   estado:  T=27.3,F=65,M=A,A=0*58
 *   orden:   M=O*3F  /  M=A*31
 * Cada trama termina con un checksum XOR en hexadecimal. Si no coincide, la trama
 * se descarta: asi se evita mostrar datos corruptos o inyectados (ISO 27400).
 */
object Protocol {

    fun checksum(payload: String): Int {
        var c = 0
        for (ch in payload) {
            if (ch == '*') break
            c = c xor ch.code
        }
        return c and 0xFF
    }

    fun command(mode: Mode): String {
        val payload = if (mode == Mode.OFF) "M=O" else "M=A"
        return "%s*%02X\n".format(payload, checksum(payload))
    }

    fun encodeState(r: Reading): String {
        val payload = "T=%.1f,F=%d,M=%s,A=%d".format(
            java.util.Locale.US, r.temp, r.power, if (r.mode == Mode.OFF) "O" else "A", if (r.alarm) 1 else 0,
        )
        return "%s*%02X".format(payload, checksum(payload))
    }

    /** Devuelve la lectura si la trama es valida, o null si esta corrupta o incompleta. */
    fun parse(line: String): Reading? {
        val clean = line.trim()
        val star = clean.indexOf('*')
        if (star <= 0) return null
        val payload = clean.substring(0, star)
        val given = clean.substring(star + 1).toIntOrNull(16) ?: return null
        if (given != checksum(payload)) return null
        val fields = payload.split(',').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val t = fields["T"]?.toFloatOrNull() ?: return null
        val f = fields["F"]?.toIntOrNull() ?: return null
        val m = when (fields["M"]) { "A" -> Mode.AUTO; "O" -> Mode.OFF; else -> return null }
        val a = fields["A"] == "1"
        return Reading(t, f.coerceIn(0, 100), m, a)
    }
}
