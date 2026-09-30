# Contrato API: App del conductor ↔ Backend (Spring Boot)

Esto es lo que la app Android del taxista espera del backend. Todos los endpoints
(excepto el login) requieren `Authorization: Bearer <token>`.
El código de la app que lo consume está en `app/src/main/java/com/radiotaxilatam/driver/data/remote/TaxiApi.kt`.

- JSON en camelCase. Fechas en ISO-8601 UTC (`2026-09-24T15:30:00Z`).
- Si el backend añade campos que la app no conoce, la app los ignora. Si falta un campo opcional, no pasa nada.
- En los errores, conviene devolver `{"message": "texto para el conductor"}`: la app muestra ese texto tal cual.
- `401` en cualquier endpoint → la app cierra la sesión y vuelve al login.

## Flujo

```
Cliente llama → Vapi (IA) → backend crea Ride → elige conductor AVAILABLE más cercano
→ crea RideOffer (expira en ~20 s) → la app lo ve en GET /offers (consulta cada 4 s)
   ├─ accept → Ride pasa a ACCEPTED, conductor a BUSY
   └─ reject / expira → el backend ofrece la carrera al siguiente conductor
ACCEPTED → ARRIVED → IN_PROGRESS → COMPLETED (conductor vuelve a AVAILABLE)
```

## Modelos

```jsonc
// Driver
{ "id": 1, "name": "Carlos Quispe", "phone": "+51987654321",
  "vehiclePlate": "ABC-123", "vehicleModel": "Toyota Yaris",
  "status": "AVAILABLE" }            // AVAILABLE | BUSY | OFFLINE

// Ride
{ "id": 1001, "status": "ACCEPTED",  // ACCEPTED | ARRIVED | IN_PROGRESS | COMPLETED | CANCELLED
  "pickupAddress": "Av. Larco 345, Miraflores",   // obligatorio
  "pickupLat": -12.1219, "pickupLng": -77.0297,   // opcional (si se geocodificó)
  "dropoffAddress": "Jockey Plaza, Surco",        // opcional
  "dropoffLat": -12.085, "dropoffLng": -76.9772,  // opcional
  "passengerName": "María López",                 // opcional
  "passengerPhone": "+51912345678",               // opcional: número desde el que llamó
  "notes": "Lleva maletas",                        // opcional: lo que dijo al agente IA
  "estimatedFare": 18.0, "finalFare": null, "currency": "PEN",  // opcional
  "createdAt": "2026-09-24T15:30:00Z", "completedAt": null }

// RideOffer
{ "offerId": 555, "ride": { ...Ride }, "expiresInSeconds": 20, "distanceToPickupMeters": 850 }
```

`expiresInSeconds` es relativo (segundos que quedan en el momento de responder), para que
no importe si el reloj del móvil está desajustado.

## Endpoints

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/api/auth/login` | `{"phone","password"}` | `{"token","driver": Driver}` · 401 si falla |
| GET | `/api/drivers/me` | — | `Driver` |
| PUT | `/api/drivers/me/status` | `{"status":"AVAILABLE"\|"OFFLINE"}` | `Driver` · 409 si intenta OFFLINE con carrera activa |
| POST | `/api/drivers/me/location` | `{"lat","lng","accuracy"?,"heading"?,"speed"?}` | 200/204 (cada ~10 s mientras no está OFFLINE) |
| GET | `/api/drivers/me/offers` | — | `RideOffer[]`: solo ofertas vigentes para este conductor (normalmente 0 o 1) |
| POST | `/api/offers/{offerId}/accept` | — | `Ride` (ACCEPTED) · 410 si expiró o ya la tomó otro |
| POST | `/api/offers/{offerId}/reject` | `{"reason"?}` | 200/204 |
| GET | `/api/drivers/me/current-ride` | — | `Ride` activa, o **204** si no hay |
| POST | `/api/rides/{rideId}/status` | `{"status":"ARRIVED"\|"IN_PROGRESS"\|"COMPLETED","finalFare"?}` | `Ride` actualizada |
| GET | `/api/drivers/me/rides?page=0&size=20` | — | `{"content": Ride[], "page", "size", "totalElements", "last"}` (más recientes primero) |

Notas para el backend:
- **Asignación**: guarda la última ubicación de cada conductor y ofrece la carrera al AVAILABLE más cercano.
  Si rechaza o expira, pasa al siguiente.
- **Cancelación por el pasajero**: basta con que `current-ride` devuelva la carrera en `CANCELLED` o que deje
  de devolverla (204). La app lo detecta en la siguiente consulta.
- **Conductores "fantasma"**: si no llega ubicación de un conductor en ~2 min, márcalo OFFLINE.
- Paginación: el formato es el mismo que el `Page` de Spring, pero la app solo lee
  `content` y `last`. Si usáis `Page<Ride>` directamente, comprobad que el JSON tenga esos dos campos.

## Futuro (no necesario para la primera prueba)
- Sustituir la consulta cada 4 s por notificaciones push (FCM). Solo cambiaría cómo se entera la app de las
  ofertas; los endpoints siguen igual.
- Añadir WhatsApp como canal de entrada: como Joaquín comentó que mucha gente pide taxi por WhatsApp, un
  agente IA por WhatsApp crearía el mismo `Ride` y para la app sería transparente.
