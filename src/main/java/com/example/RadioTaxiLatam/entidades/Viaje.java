package com.example.RadioTaxiLatam.entidades;

import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne
    @JoinColumn(name = "conductor_id", nullable = true)
    private Usuario conductor;

    @Column(nullable = false)
    private String origen, destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoViaje estado;


}
