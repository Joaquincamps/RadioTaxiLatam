# API que usa la app del conductor

La app sigue el backend de Joaquín tal como está (repo `Joaquincamps/RadioTaxiLatam`, rama `desarrollo`,
revisado en el commit `505a089` del 13/11/2026). Los nombres de rutas, campos y enums son los suyos.

Código de la app: `app/src/main/java/com/radiotaxilatam/driver/data/remote/TaxiApi.kt` (rutas) y
`data/model/Modelos.kt` (DTOs). Si Joaquín cambia una ruta o un DTO, se cambia ahí igual.

## Rutas

| Función | Método y ruta | Envía | Recibe |
|---|---|---|---|
| Login | `POST /usuarios/api/auth/login` | `LoginDTO {movil, password}` | `ConductorDTO` |
| Disponibilidad | `PUT /usuarios/api/drivers/{id}/status` | `EstadoConductorDTO {estado}` (`AVAILABLE` u `OFFLINE`) | `ConductorDTO` |
| Ubicación (cada ~10 s) | `POST /usuarios/api/drivers/{id}/location` | `UbicacionDTO {latitud, longitud}` | `UbicacionDTO` |
| Mis datos | `GET /usuarios/listar` | — | `Usuario[]` (la app busca su id) |
| Ofertas (cada ~4 s) | `GET /api/drivers/{id}/offers` | — | `OfertaViajeDto[]` |
| Aceptar oferta | `POST /api/offers/{ofertaId}/accept?conductorId={id}` | — | `ViajeDTO` |
| Rechazar oferta | `POST /api/offers/{ofertaId}/reject?conductorId={id}` | — | 200 sin cuerpo |
| Viaje actual e historial | `GET /viaje/listar/viajes` | — | `Viaje[]` (la app filtra los suyos) |
| Llegué al punto de recogida | `POST /viaje/{viajeId}/llegada?conductorId={id}` | — | `ViajeDTO` (estado `LLEGADO`) |
| Iniciar viaje (exige `LLEGADO`) | `POST /viaje/iniciar/viaje/{viajeId}/conductor/iniciaViaje/{id}` | — | `ViajeDTO` |
| Finalizar viaje | `POST /viaje/finalizar/viaje/{viajeId}/conductor/finalizaViaje/{id}` | — | `ViajeDTO` |
| Cancelar viaje (antes de iniciar) | `POST /viaje/cancelar/viaje/{viajeId}/conductor/cancelaViaje/{id}` | — | `ViajeDTO` (404 si no existe, 403 si no es suyo) |
| Devolver viaje (antes de iniciar) | `POST /viaje/{viajeId}/devolver?conductorId={id}` | — | 204 sin cuerpo (403 si no es suyo, 409 si no está `ACEPTADO`/`LLEGADO`) |

`{id}` es el id del conductor que devolvió el login. No hay token.

## DTOs

- **ConductorDTO**: `id`, `nombre`, `telefono`, `matricula`, `modeloVehiculo`, `estado`.
- **EstadoConductor**: `AVAILABLE`, `BUSY`, `OFFLINE`.
- **ViajeDTO**: `id`, `clienteId`, `conductorId`, `origen`, `destino`, `estado`, `latitudOrigen`, `longitudOrigen`.
- **OfertaViajeDto**: `id`, `viaje` (ViajeDTO), `segundosRestantes`, `distanciaRecogidaMetros`.
- **Viaje** (entidad, de `/viaje/listar/viajes`): como ViajeDTO pero con `cliente` y `conductor` completos
  y además `latitudDestino`, `longitudDestino`, `creadoEn` y `finalizadoEn` (`Instant`, texto ISO-8601).
  De `cliente` la app saca el nombre y teléfono del pasajero. `creadoEn` solo existe en los viajes creados
  desde el commit `c4b44ac`; los anteriores lo traen a `null`.
- **EstadoViaje**: `PENDIENTE`, `BUSCANDO_CONDUCTOR`, `ASIGNADO`, `ACEPTADO`, `LLEGADO`, `EN_CURSO`, `FINALIZADO`, `CANCELADO`.

## Flujo

```
Viaje creado (BUSCANDO_CONDUCTOR) → backend crea OfertaViaje al conductor AVAILABLE más cercano (20 s)
  → la app la ve en GET /api/drivers/{id}/offers
       ├─ accept → viaje ACEPTADO, conductor BUSY
       ├─ reject → el backend la ofrece al siguiente conductor
       └─ caduca → el backend la marca EXPIRADA y la ofrece al siguiente
  → ACEPTADO → (Llegué) LLEGADO → EN_CURSO → FINALIZADO (conductor vuelve a AVAILABLE)
       ACEPTADO/LLEGADO ├─ cancelar → CANCELADO (el conductor se queda en BUSY)
                        └─ devolver → BUSCANDO_CONDUCTOR, oferta DEVUELTA, se ofrece a otro conductor
                                      (nunca al mismo); el conductor vuelve a AVAILABLE
```

## Lo que la app resuelve por su cuenta (mientras el backend no lo tenga)

| Falta en el backend | Qué hace la app |
|---|---|
| Token / sesión | Guarda el id del conductor y lo manda en cada ruta |
| `GET` de un conductor | Lo busca en `GET /usuarios/listar` |
| Viaje actual e historial | Filtra `GET /viaje/listar/viajes` (todos los viajes de todos) |
| Horas de aceptar e iniciar (solo guarda `creadoEn` y `finalizadoEn`) | El móvil las anota él mismo (`RegistroViajes`) |
| Al cancelar, el conductor sigue en `BUSY` | Lo pone `AVAILABLE` con `PUT .../status` (también tras finalizar, por si acaso) |
| La oferta caducada solo pasa al siguiente cuando corre su tarea programada | La rechaza 1 s antes de caducar |
| Errores de login como 500 sin mensaje | Muestra "Teléfono o contraseña incorrectos" |
