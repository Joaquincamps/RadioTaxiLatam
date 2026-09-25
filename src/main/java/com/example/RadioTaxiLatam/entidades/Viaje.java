package com.example.RadioTaxiLatam.entidades;

import com.example.RadioTaxiLatam.Enum.DriverStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne
    @JoinColumn(name = "conductor_id", nullable = false)
    private Usuario conductor;

    @Column(nullable = false)
    private String origen, destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus estado;


}
