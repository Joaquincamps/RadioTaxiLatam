package com.example.RadioTaxiLatam.controlador;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.Dto.ViajeDTO;
import com.example.RadioTaxiLatam.servicio.OfertaViajeServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OfertaViajeControlador {

    @Autowired
    private OfertaViajeServicio ofertaViajeServicio;

    @GetMapping("/api/drivers/{id}/offers")
    public ResponseEntity<List<OfertaViajeDto>> obtenerOfertas(@PathVariable Long id){
        return ResponseEntity.ok(ofertaViajeServicio.obtenerOfertas(id));
    }

    @PostMapping("/api/offers/{ofertaId}/accept")
    public ResponseEntity<ViajeDTO> acpetarOferta(@PathVariable Long ofertaId ,
                                                  @RequestParam Long conductorId){
        return ResponseEntity.ok(ofertaViajeServicio.aceptarOferta(ofertaId,conductorId));
    }
}
