package com.example.RadioTaxiLatam.Dto;

import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConductorDTO {

    private Long id;

    private String nombre, telefono, matricula, modeloVehiculo;

    private EstadoConductor estado;
}
