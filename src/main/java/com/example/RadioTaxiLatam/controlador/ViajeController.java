package com.example.RadioTaxiLatam.controlador;

import com.example.RadioTaxiLatam.Dto.CancelarViajeDTO;
import com.example.RadioTaxiLatam.Dto.ViajeDTO;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.servicio.ViajeServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/viaje")
public class ViajeController {

    @Autowired
    private ViajeServicio viajeServicio;

    @PostMapping("/crear")
    public ResponseEntity<ViajeDTO> crearViaje(@RequestBody ViajeDTO viajeDTO) {
        return ResponseEntity.ok(viajeServicio.crearViaje(viajeDTO));
    }

    @GetMapping("/obtenerViajePor/{id}")
    public ResponseEntity<ViajeDTO> obtenerViajePorId(@PathVariable Long id) {
        return ResponseEntity.ok(viajeServicio.obtenerViajePorId(id));
    }

    @GetMapping("/listar/viajes")
    public ResponseEntity<List<Viaje>> obtenerTodosLosViajes() {
        return ResponseEntity.ok(viajeServicio.obtenerTodosLosViajes());
    }

    @PostMapping("/asignar/viaje/{viajeId}/asignar/conductor/{conductorId}")
    public ResponseEntity<ViajeDTO> asignarConductor(@PathVariable Long viajeId,
                                                     @PathVariable Long conductorId) {
        return ResponseEntity.ok(viajeServicio.asignarConductor(viajeId, conductorId));
    }

    @PostMapping("/aceptar/viaje/{viajeId}/conductor/asignado/{conductorId}")
    public ResponseEntity<ViajeDTO> aceptarViaje(@PathVariable Long viajeId,
                                                     @PathVariable Long conductorId) {
        return ResponseEntity.ok(viajeServicio.aceptarViaje(viajeId, conductorId));
    }

    @PostMapping("/iniciar/viaje/{viajeId}/conductor/iniciaViaje/{conductorId}")
    public ResponseEntity<ViajeDTO> iniciarViaje(@PathVariable Long viajeId,
                                                 @PathVariable Long conductorId) {
        return ResponseEntity.ok(viajeServicio.iniciarViaje(viajeId, conductorId));
    }

    @PostMapping("/finalizar/viaje/{viajeId}/conductor/finalizaViaje/{conductorId}")
    public ResponseEntity<ViajeDTO> finalizarViaje(@PathVariable Long viajeId,
                                                 @PathVariable Long conductorId) {
        return ResponseEntity.ok(viajeServicio.finalizarViaje(viajeId, conductorId));
    }

    @PostMapping("/cancelar/viaje/{viajeId}/conductor/cancelaViaje/{conductorId}")
    public ResponseEntity<ViajeDTO> cancelarViaje(@PathVariable Long viajeId,
                                                   @PathVariable Long conductorId) {
        return ResponseEntity.ok(viajeServicio.cancelarViaje(viajeId, conductorId));
    }

    @PostMapping("/{viajeId}/cancelar-pasajero")
    public ResponseEntity<ViajeDTO> cancelarViajePorPasajero(
            @PathVariable("viajeId") Long viajeId,
            @RequestBody CancelarViajeDTO solicitud
    ) {
        return ResponseEntity.ok(
                viajeServicio.cancelarViajePorPasajero(
                        viajeId,
                        solicitud.getLlamadaVapiId()
                )
        );
    }

    @PostMapping("/{viajeId}/llegada")
    public ResponseEntity<ViajeDTO> registrarLlegada(
            @PathVariable("viajeId") Long viajeId,
            @RequestParam("conductorId") Long conductorId
    ) {
        return ResponseEntity.ok(
                viajeServicio.registrarLlegada(viajeId, conductorId)
        );
    }
}
