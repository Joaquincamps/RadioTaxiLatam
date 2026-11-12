package com.example.RadioTaxiLatam.entidades;

import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    @JoinColumn(name = "cliente_id", nullable = true)
    private Usuario cliente;

    @ManyToOne
    @JoinColumn(name = "conductor_id", nullable = true)
    private Usuario conductor;

    @Column(nullable = false)
    private String origen, destino;

    private Double latitudOrigen, longitudOrigen;

    private Double latitudDestino, longitudDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoViaje estado;

    private String telefonoPasajero;
    private String nombrePasajero;

    @Column(unique = true)
    private String llamadaVapiId;

    private Instant finalizadoEn;


}
