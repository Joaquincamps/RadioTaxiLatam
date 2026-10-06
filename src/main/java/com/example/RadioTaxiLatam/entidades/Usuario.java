package com.example.RadioTaxiLatam.entidades;

import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;

    @Column(nullable = false, unique = true)
    private String telefono, password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario tipo;

    private Double latitud, longitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConductor estado;

    private String matricula, modeloVehiculo;

    private Instant ultimaUbicacion;


}
