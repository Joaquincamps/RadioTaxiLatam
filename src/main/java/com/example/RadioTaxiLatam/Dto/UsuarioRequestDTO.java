package com.example.RadioTaxiLatam.Dto;

import com.example.RadioTaxiLatam.Enum.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioRequestDTO {

    private String nombre;

    private String telefono;

    private UserType tipo;
}
