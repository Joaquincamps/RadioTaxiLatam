package com.example.RadioTaxiLatam.Dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrearConductorDTO {

    private String nombre;

    private String telefono;

    private String password;

    private String matricula;

    private String modeloVehiculo;

}