# RadioTaxi Latam: App del conductor (Android)

App para taxistas de la centralita de taxis con IA. El backend (Spring Boot + Vapi + Telnyx) lo lleva Joaquín;
el contrato entre ambos está en [API_CONTRACT.md](API_CONTRACT.md).

**Funciones:** login · Disponible / No disponible · recibir carreras (con sonido y cuenta atrás) ·
aceptar / rechazar · flujo de la carrera (llegué → iniciar → finalizar), abrir Maps/Waze y llamar al pasajero ·
historial de servicios.

**Stack:** Kotlin, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, DataStore,
servicio en primer plano con ubicación (FusedLocation). minSdk 26 (Android 8), targetSdk 36.

## Elegir backend

En `gradle.properties`, `apiMode` decide con qué habla la app (recompila tras cambiarlo):

| `apiMode` | Backend | Login |
|---|---|---|
| `mock` | Simulado dentro de la app, con direcciones de Lima | Cualquier teléfono, contraseña `1234` |
| `mvp` | El backend actual de Joaquín (`/usuarios`, `/viaje`) | Teléfono de un conductor registrado, sin contraseña |
| `contract` | Backend completo según [API_CONTRACT.md](API_CONTRACT.md) | Teléfono y contraseña |

`apiBaseUrl` es la URL del servidor (la `/` final es obligatoria). Emulador contra tu PC: `http://10.0.2.2:8080/`.
HTTP sin TLS solo está permitido en la build debug.

### Modo `mvp`: qué se simula en el móvil

El backend de Joaquín aún no tiene todo lo que usa la app; `data/mvp/JoaquinTaxiApi.kt` lo traduce:

- **Viajes nuevos** = viajes en estado `ASIGNADO` a ese conductor (hay que asignarlos con `/viaje/asignar/...`).
- **Aceptar / Iniciar / Finalizar** llaman a sus rutas reales.
- **Disponible**, **Rechazar** y **Llegué** solo existen en el móvil; Rechazar oculta el viaje, no lo cancela.
- La ubicación no se envía.

## Compilar

Ábrelo con Android Studio y dale a Run. También se puede compilar por consola:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`; puedes pasárselo a los taxistas de prueba por WhatsApp.

> En este PC, Gradle falla con `Unable to establish loopback connection` porque la ruta temporal de Windows
> tiene un espacio (el nombre de usuario). Solución: definir la variable de entorno
> `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\RadioTaxiLatam\.gradle-tmp` (o cualquier carpeta sin espacios).
> Android Studio no suele tener este problema.

## Estructura

```
app/src/main/java/com/radiotaxilatam/driver/
├── TaxiApp.kt                 # arranque, elige API real o simulada, canales de notificación
├── MainActivity.kt            # navegación: login → inicio → historial
├── data/
│   ├── model/Models.kt        # Driver, Ride, RideOffer… (espejo de API_CONTRACT.md)
│   ├── remote/TaxiApi.kt      # endpoints Retrofit
│   ├── mock/MockTaxiApi.kt    # backend simulado
│   ├── DriverRepository.kt    # estado compartido (conductor, carrera, oferta)
│   └── SessionStore.kt        # token JWT
├── service/DriverOnlineService.kt  # en segundo plano: envía ubicación y consulta ofertas
└── ui/                        # pantallas Compose
```
