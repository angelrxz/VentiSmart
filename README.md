# VentiSmart – App Android (TI3042, Unidad 2)

Aplicación Android (Kotlin + Jetpack Compose) para monitorear y controlar el sistema de ventilación inteligente VentiSmart (Arduino + HC-05).

## Funciones
- Inicio de sesión y registro con **Firebase Authentication** (correo y contraseña).
- Conexión inalámbrica por **Bluetooth (HC-05)** o con un **dispositivo simulado** para demostración.
- Lectura en tiempo real: temperatura, potencia del ventilador, modo y alarma.
- Control: modo automático o apagado manual.
- **Alertas e historial** guardados en **Cloud Firestore** (sobrecalentamiento, sin señal, cambios de modo).
- **Dos teléfonos interactuando**: el teléfono conectado al equipo actúa como puente (publica las lecturas en la nube) y el segundo teléfono, con la misma cuenta, ve los datos y envía órdenes a distancia.
- Seguridad alineada con **ISO/IEC 27400**: autenticación, reglas de acceso por usuario, tráfico solo cifrado (TLS), PIN guardado cifrado (AES-256), permisos en tiempo de ejecución y validación de tramas con checksum.

## Cómo ejecutarla
1. Abrir la carpeta `VentiSmart` en Android Studio y esperar la sincronización de Gradle.
2. Conectar un teléfono con *Depuración USB* activada (o usar un emulador).
3. Presionar ▶ **Run 'app'**.
4. Crear una cuenta en la pantalla de inicio de sesión.
5. Agregar dispositivo → elegir **"VentiSmart_Dormitorio (simulado)"** → Vincular → Conectar.
6. En el segundo teléfono, iniciar sesión con la misma cuenta: verá el mismo dispositivo en modo remoto.

## Estructura
- `data/` protocolo de tramas, fuentes Bluetooth y simulada, Firebase.
- `vm/` lógica de la aplicación (ViewModel).
- `ui/` pantallas basadas en los mockups.
- `firestore.rules` reglas de seguridad de la base de datos.

Autores: Miguel Cabrera y Pablo – INACAP.
