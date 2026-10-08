# RadioTaxi Latam: App del conductor (Android)

App para taxistas de la centralita de taxis con IA. El backend (Spring Boot + Vapi + Telnyx) lo lleva Joaquín;
el contrato entre ambos está en [API_CONTRACT.md](API_CONTRACT.md).

**Funciones:** login · Disponible / No disponible · recibir carreras (con sonido y cuenta atrás) ·
aceptar / rechazar · flujo de la carrera (llegué → iniciar → finalizar), abrir Maps/Waze y llamar al pasajero ·
historial de servicios.

**Stack:** Kotlin, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, DataStore,
servicio en primer plano con ubicación (FusedLocation). minSdk 26 (Android 8), targetSdk 36.

## Probar sin backend (modo demo)

Por defecto `useMockApi=true` en `gradle.properties`: la app usa un backend simulado con direcciones de Lima.
Inicia sesión con cualquier teléfono y la contraseña `1234`, activa **Disponible** y cada ~15 s llegará una carrera.

## Conectar con el backend real

En `gradle.properties`:

```properties
useMockApi=false
apiBaseUrl=http://10.0.2.2:8080/   # emulador → localhost de tu PC
# con móvil físico usa la IP de tu PC en la wifi, p. ej. http://192.168.1.50:8080/
# en producción, https://api.tudominio.com/
```

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
│   ├── model/Models.kt        # Driver, Ride, RideOffer… (espejo de API_CONTRACT.md)
│   ├── remote/TaxiApi.kt      # endpoints Retrofit
│   ├── mock/MockTaxiApi.kt    # backend simulado
│   ├── DriverRepository.kt    # estado compartido (conductor, carrera, oferta)
│   └── SessionStore.kt        # token JWT
├── service/DriverOnlineService.kt  # en segundo plano: envía ubicación y consulta ofertas
└── ui/                        # pantallas Compose
```
