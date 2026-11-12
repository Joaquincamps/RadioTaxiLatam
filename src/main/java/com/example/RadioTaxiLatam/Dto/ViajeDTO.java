package com.example.RadioTaxiLatam.Dto;

import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ViajeDTO {

    private Long id;

    private Long clienteId;

    private Long conductorId;

    private String origen;

    private String destino;

    private EstadoViaje estado;

    private Double latitudOrigen;

    private Double longitudOrigen;

    private String telefonoPasajero;
    private String nombrePasajero;
    private String llamadaVapiId;

    private Instant finalizadoEn;
}
