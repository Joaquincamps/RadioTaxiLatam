# API que usa la app del conductor

La app sigue el backend de Joaquín tal como está (repo `Joaquincamps/RadioTaxiLatam`, rama `desarrollo`,
revisado en el commit `a9a0851` del 8/10/2026). Los nombres de rutas, campos y enums son los suyos.

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
| Iniciar viaje | `POST /viaje/iniciar/viaje/{viajeId}/conductor/iniciaViaje/{id}` | — | `ViajeDTO` |
| Finalizar viaje | `POST /viaje/finalizar/viaje/{viajeId}/conductor/finalizaViaje/{id}` | — | `ViajeDTO` |

`{id}` es el id del conductor que devolvió el login. No hay token.

## DTOs

- **ConductorDTO**: `id`, `nombre`, `telefono`, `matricula`, `modeloVehiculo`, `estado`.
- **EstadoConductor**: `AVAILABLE`, `BUSY`, `OFFLINE`.
- **ViajeDTO**: `id`, `clienteId`, `conductorId`, `origen`, `destino`, `estado`, `latitudOrigen`, `longitudOrigen`.
- **OfertaViajeDto**: `id`, `viaje` (ViajeDTO), `segundosRestantes`, `distanciaRecogidaMetros`.
- **Viaje** (entidad, de `/viaje/listar/viajes`): como ViajeDTO pero con `cliente` y `conductor` completos
  y además `latitudDestino`, `longitudDestino`. De `cliente` la app saca el nombre y teléfono del pasajero.
- **EstadoViaje**: `PENDIENTE`, `BUSCANDO_CONDUCTOR`, `ASIGNADO`, `ACEPTADO`, `EN_CURSO`, `FINALIZADO`, `CANCELADO`.

## Flujo

```
Viaje creado (BUSCANDO_CONDUCTOR) → backend crea OfertaViaje al conductor AVAILABLE más cercano (20 s)
  → la app la ve en GET /api/drivers/{id}/offers
       ├─ accept → viaje ACEPTADO, conductor BUSY
       └─ reject → el backend la ofrece al siguiente conductor
  → ACEPTADO → (Llegué, solo en la app) → EN_CURSO → FINALIZADO
```

## Lo que la app resuelve por su cuenta (mientras el backend no lo tenga)

| Falta en el backend | Qué hace la app |
|---|---|
| Token / sesión | Guarda el id del conductor y lo manda en cada ruta |
| `GET` de un conductor | Lo busca en `GET /usuarios/listar` |
| Viaje actual e historial | Filtra `GET /viaje/listar/viajes` (todos los viajes de todos) |
| Estado "llegué al punto de recogida" | Paso local entre `ACEPTADO` e iniciar viaje |
| Al finalizar o cancelar, el conductor sigue en `BUSY` | Lo pone `AVAILABLE` con `PUT .../status` |
| Oferta que caduca sin respuesta no pasa al siguiente | La rechaza 2 s antes de caducar |
| Errores de login como 500 sin mensaje | Muestra "Teléfono o contraseña incorrectos" |
