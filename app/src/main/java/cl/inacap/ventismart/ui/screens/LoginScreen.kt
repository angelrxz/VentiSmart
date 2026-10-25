package cl.inacap.ventismart.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.inacap.ventismart.ui.components.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel) {
    val busy by vm.authBusy.collectAsState()
    val error by vm.authError.collectAsState()
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    val emailOk = email.contains('@') && email.contains('.')
    val canSubmit = !busy && emailOk && pass.length >= 6 && (!register || name.isNotBlank())

    Column(
        Modifier.fillMaxSize().background(V.Bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 48.dp, bottom = 24.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(V.Mint), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Toys, null, tint = V.Teal, modifier = Modifier.size(30.dp))
        }
        Gap(16)
        Text("VentiSmart", style = MaterialTheme.typography.headlineSmall, color = V.Ink)
        Text("Tu dormitorio, en equilibrio", style = MaterialTheme.typography.bodyMedium, color = V.Muted)
        Gap(28)

        // Selector Iniciar sesion / Crear cuenta
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(V.Mint).padding(4.dp)) {
            Tab("Iniciar sesion", !register, Modifier.weight(1f)) { register = false; vm.clearAuthError() }
            Tab("Crear cuenta", register, Modifier.weight(1f)) { register = true; vm.clearAuthError() }
        }
        Gap(20)

        if (register) {
            Field("Nombre", name, { name = it }, "Ej: Lucia Martinez")
            Gap(12)
        }
        Field("Correo", email, { email = it.trim() }, "tu@correo.cl", KeyboardType.Email)
        Gap(12)
        Text("Contrasena", style = MaterialTheme.typography.labelSmall, color = V.Muted)
        Gap(6)
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            placeholder = { Text("Minimo 6 caracteres") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (show) "Ocultar" else "Mostrar", tint = V.Teal)
                }
            },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )

        if (error != null) {
            Gap(14)
            VCard(color = V.RedBg, border = null) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = V.Red)
            }
        }

        Gap(22)
        PrimaryButton(
            text = if (busy) "Verificando..." else if (register) "Crear cuenta" else "Ingresar",
            enabled = canSubmit,
        ) { if (register) vm.signUp(name, email, pass) else vm.signIn(email, pass) }

        Gap(20)
        VCard(color = V.Mint, border = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = V.Teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Acceso protegido", style = MaterialTheme.typography.titleMedium, color = V.Ink)
            }
            Gap(6)
            Text(
                "Tu contrasena se valida en Firebase por una conexion cifrada (TLS). La app no la guarda en el telefono, y cada cuenta solo puede ver sus propios dispositivos.",
                style = MaterialTheme.typography.bodySmall, color = V.Muted,
            )
        }
        FootNote("Usa la misma cuenta en dos telefonos para ver y controlar el mismo ventilador desde ambos.")
    }
}

@Composable
private fun Tab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) V.Teal else V.Muted)
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String, type: KeyboardType = KeyboardType.Text) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = V.Muted)
    Gap(6)
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
    focusedBorderColor = V.Teal, unfocusedBorderColor = V.Line,
    focusedTextColor = V.Ink, unfocusedTextColor = V.Ink,
    unfocusedPlaceholderColor = V.Faint, focusedPlaceholderColor = V.Faint,
)
