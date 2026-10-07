package com.example.RadioTaxiLatam.controlador;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.servicio.OfertaViajeServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OfertaViajeControlador {

    @Autowired
    private OfertaViajeServicio ofertaViajeServicio;

    @GetMapping("/api/drivers/{id}/offers")
    public ResponseEntity<List<OfertaViajeDto>> obtenerOfertas(@PathVariable Long id){
        return ResponseEntity.ok(ofertaViajeServicio.obtenerOfertas(id));
    }
}
