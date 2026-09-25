package com.example.RadioTaxiLatam.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ViajeRequestDTO {

    private Long clienteId;

    private String origen;

    private String destino;
}
