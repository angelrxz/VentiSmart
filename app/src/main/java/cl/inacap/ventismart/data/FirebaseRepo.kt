package cl.inacap.ventismart.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Acceso a Firebase.
 * - Authentication: correo y contrasena. Las contrasenas nunca pasan por la app en
 *   texto plano hacia un servidor propio; Firebase las guarda con hash (scrypt).
 * - Firestore: cada usuario solo puede leer y escribir dentro de users/{suUid}
 *   (ver firestore.rules). Todo el trafico va por HTTPS/TLS.
 *
 * Esquema:
 *   users/{uid}/devices/{id}          datos del equipo + "live" + "command"
 *   users/{uid}/devices/{id}/events   historial de eventos
 */
class FirebaseRepo {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // ---------- Autenticacion ----------
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(name: String, email: String, password: String) {
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = res.user ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        db.collection("users").document(user.uid).set(
            mapOf("displayName" to name.trim(), "email" to email.trim(), "createdAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun updateName(name: String) {
        val user = auth.currentUser ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        db.collection("users").document(user.uid).set(mapOf("displayName" to name.trim()), SetOptions.merge()).await()
    }

    fun signOut() = auth.signOut()

    // ---------- Dispositivos ----------
    private fun devices(uid: String) = db.collection("users").document(uid).collection("devices")

    fun observeDevices(uid: String): Flow<List<Device>> = callbackFlow {
        val reg = devices(uid).orderBy("createdAtMs", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map { it.toDevice() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun addDevice(uid: String, d: Device): String {
        val ref = devices(uid).document()
        ref.set(
            mapOf(
                "name" to d.name, "type" to d.type, "location" to d.location,
                "address" to d.address, "simulated" to d.simulated,
                "createdAt" to FieldValue.serverTimestamp(),
                "createdAtMs" to System.currentTimeMillis(),
            ),
        ).await()
        return ref.id
    }

    suspend fun deleteDevice(uid: String, id: String) {
        devices(uid).document(id).delete().await()
    }

    // ---------- Estado en vivo (lo publica el telefono conectado) ----------
    fun publishLive(uid: String, id: String, s: LiveState) {
        devices(uid).document(id).set(
            mapOf(
                "live" to mapOf(
                    "temp" to s.temp.toDouble(), "power" to s.power, "mode" to s.mode.name,
                    "alarm" to s.alarm, "linkOk" to s.linkOk, "updatedAt" to s.updatedAt,
                    "gateway" to s.gateway,
                ),
            ),
            SetOptions.merge(),
        )
    }

    fun observeLive(uid: String, id: String): Flow<LiveState?> = callbackFlow {
        val reg = devices(uid).document(id).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val m = snap?.get("live") as? Map<String, Any?>
            trySend(
                m?.let {
                    LiveState(
                        temp = (it["temp"] as? Number)?.toFloat() ?: 0f,
                        power = (it["power"] as? Number)?.toInt() ?: 0,
                        mode = if (it["mode"] == "OFF") Mode.OFF else Mode.AUTO,
                        alarm = it["alarm"] as? Boolean ?: false,
                        linkOk = it["linkOk"] as? Boolean ?: false,
                        updatedAt = (it["updatedAt"] as? Number)?.toLong() ?: 0L,
                        gateway = it["gateway"] as? String ?: "",
                    )
                },
            )
        }
        awaitClose { reg.remove() }
    }

    // ---------- Ordenes remotas (de un telefono a otro) ----------
    fun sendCommand(uid: String, id: String, mode: Mode, by: String) {
        devices(uid).document(id).set(
            mapOf("command" to mapOf("mode" to mode.name, "by" to by, "at" to System.currentTimeMillis())),
            SetOptions.merge(),
        )
    }

    /** Emite (modo, quien, cuando) de la ultima orden pendiente. */
    fun observeCommand(uid: String, id: String): Flow<Triple<Mode, String, Long>?> = callbackFlow {
        val reg = devices(uid).document(id).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val m = snap?.get("command") as? Map<String, Any?>
            trySend(
                m?.let {
                    Triple(
                        if (it["mode"] == "OFF") Mode.OFF else Mode.AUTO,
                        it["by"] as? String ?: "",
                        (it["at"] as? Number)?.toLong() ?: 0L,
                    )
                },
            )
        }
        awaitClose { reg.remove() }
    }

    fun clearCommand(uid: String, id: String) {
        devices(uid).document(id).update("command", FieldValue.delete())
    }

    // ---------- Historial ----------
    fun logEvent(uid: String, id: String, e: EventItem) {
        devices(uid).document(id).collection("events").add(
            mapOf("type" to e.type.name, "title" to e.title, "detail" to e.detail, "at" to e.at),
        )
    }

    fun observeEvents(uid: String, id: String): Flow<List<EventItem>> = callbackFlow {
        val reg = devices(uid).document(id).collection("events")
            .orderBy("at", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.map { d ->
                        EventItem(
                            id = d.id,
                            type = runCatching { EventType.valueOf(d.getString("type") ?: "") }.getOrDefault(EventType.LINK_UP),
                            title = d.getString("title") ?: "",
                            detail = d.getString("detail") ?: "",
                            at = d.getLong("at") ?: 0L,
                        )
                    } ?: emptyList(),
                )
            }
        awaitClose { reg.remove() }
    }

    private fun DocumentSnapshot.toDevice() = Device(
        id = id,
        name = getString("name") ?: "",
        type = getString("type") ?: "",
        location = getString("location") ?: "",
        address = getString("address") ?: "",
        simulated = getBoolean("simulated") ?: false,
    )
}
