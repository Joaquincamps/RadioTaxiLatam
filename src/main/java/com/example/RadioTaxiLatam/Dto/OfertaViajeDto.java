package com.example.RadioTaxiLatam.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OfertaViajeDto {

    private Long id;

    private ViajeDTO viaje;

    private  Integer segundosRestantes,distanciaRecogidaMetros;

}
