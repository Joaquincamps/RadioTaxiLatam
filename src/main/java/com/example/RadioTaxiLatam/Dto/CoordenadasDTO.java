package com.example.RadioTaxiLatam.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoordenadasDTO {

    private Double latitud;
    private Double longitud;
    private String direccionEncontrada;
}
