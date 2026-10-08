# RadioTaxi Latam: App del conductor (Android)

App para taxistas de la centralita de taxis con IA. El backend (Spring Boot + Vapi + Telnyx) lo lleva Joaquín
y **la app se rige por él** (repo `Joaquincamps/RadioTaxiLatam`, rama `desarrollo`): mismas rutas, mismos DTOs y
mismos nombres en español. [API_CONTRACT.md](API_CONTRACT.md) detalla qué ruta usa cada función.

**Funciones:** login · Disponible / No disponible · recibir carreras (con sonido y cuenta atrás) ·
aceptar / rechazar · flujo de la carrera (llegué → iniciar → finalizar), abrir Maps/Waze y llamar al pasajero ·
historial de servicios.

**Stack:** Kotlin, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, DataStore,
servicio en primer plano con ubicación (FusedLocation). minSdk 26 (Android 8), targetSdk 36.

## Probar sin backend (modo demo)

Por defecto `useMockApi=true` en `gradle.properties`: la app usa un backend simulado con direcciones de Lima.
Inicia sesión con cualquier teléfono y la contraseña `1234`, activa **Disponible** y cada ~15 s llegará una carrera.

## Conectar con el backend de Joaquín

En `gradle.properties`:

```properties
useMockApi=false
apiBaseUrl=http://10.0.2.2:8080/   # emulador → localhost de tu PC
# con móvil físico usa la IP de tu PC en la wifi, p. ej. http://192.168.1.50:8080/
# con ngrok, la URL https://....ngrok-free.dev/ que te pase Joaquín
```

`data/remote/TaxiApi.kt` tiene sus rutas tal cual y `data/model/Modelos.kt` sus DTOs (`ConductorDTO`, `ViajeDTO`,
`OfertaViajeDto`, `EstadoViaje`...). Lo que su backend aún no tiene se resuelve en `ConductorRepositorio.kt`:

| Función de la app | Backend de Joaquín |
|---|---|
| Login (teléfono + contraseña) | `POST /usuarios/api/auth/login` con `movil` y `password`. No hay token: se guarda el id del conductor. Al entrar, su backend lo pone `AVAILABLE`. |
| Disponible / No disponible | `PUT /usuarios/api/drivers/{id}/status` con `estado` |
| Ubicación cada ~10 s | `POST /usuarios/api/drivers/{id}/location` con `latitud` y `longitud` |
| Mis datos | Se busca en `GET /usuarios/listar` |
| Ofertas (cada ~4 s) | `GET /api/drivers/{id}/offers` |
| Aceptar / Rechazar | `POST /api/offers/{ofertaId}/accept` y `/reject` con `?conductorId=` |
| Oferta sin respuesta | La app la rechaza 2 s antes de que caduque para que su backend la pase al siguiente conductor |
| Viaje actual | Viaje `ACEPTADO` o `EN_CURSO` de este conductor en `GET /viaje/listar/viajes` |
| Llegué | Solo en el móvil (no existe en su backend) |
| Iniciar / Finalizar | `POST /viaje/iniciar/...` y `/viaje/finalizar/...`. Al finalizar, la app vuelve a poner al conductor `AVAILABLE` (su backend lo deja en `BUSY`). |
| Historial | Viajes `FINALIZADO`/`CANCELADO` de este conductor en `GET /viaje/listar/viajes` |

Para probar hace falta un usuario con `tipo = CONDUCTOR`, `estado` y `password` en la base de datos.

HTTP sin TLS solo está permitido en la build debug.

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
│   ├── model/Modelos.kt       # DTOs del backend de Joaquín (ConductorDTO, ViajeDTO, OfertaViajeDto…)
│   ├── remote/TaxiApi.kt      # rutas del backend de Joaquín
│   ├── mock/MockTaxiApi.kt    # backend simulado que imita al de Joaquín
│   ├── ConductorRepositorio.kt # estado compartido (conductor, viaje, oferta) y lo que falta en el backend
│   └── SessionStore.kt        # id del conductor (el backend no tiene token)
├── service/DriverOnlineService.kt  # en segundo plano: envía ubicación y consulta ofertas
└── ui/                        # pantallas Compose
```
