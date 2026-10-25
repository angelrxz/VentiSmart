package cl.inacap.ventismart.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Almacen cifrado (AES-256-GCM, clave en el Android Keystore) para datos sensibles
 * locales: el PIN propio de cada modulo HC-05. Nunca se guarda en texto plano ni
 * se sube a la nube. Ademas guarda preferencias simples del telefono.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = try {
        open(context)
    } catch (e: Exception) {
        // Si la clave del Keystore quedo invalida (p. ej. tras restaurar el telefono),
        // se borra el archivo cifrado y se crea de nuevo en vez de cerrar la app.
        context.deleteSharedPreferences(FILE)
        open(context)
    }

    private fun open(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private companion object { const val FILE = "ventismart_secure" }

    fun savePin(address: String, pin: String) = prefs.edit().putString("pin_$address", pin).apply()
    fun hasPin(address: String) = prefs.contains("pin_$address")

    var notifyOverheat: Boolean
        get() = prefs.getBoolean("notify_overheat", true)
        set(v) = prefs.edit().putBoolean("notify_overheat", v).apply()

    var notifySignal: Boolean
        get() = prefs.getBoolean("notify_signal", true)
        set(v) = prefs.edit().putBoolean("notify_signal", v).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(v) = prefs.edit().putBoolean("dark_theme", v).apply()
}
