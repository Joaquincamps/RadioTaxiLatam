package com.example.RadioTaxiLatam.Dto;

import com.example.RadioTaxiLatam.Enum.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private DriverStatus estado;
}
