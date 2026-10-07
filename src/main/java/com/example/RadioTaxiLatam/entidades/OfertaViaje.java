package com.example.RadioTaxiLatam.entidades;


import com.example.RadioTaxiLatam.Enum.EstadoOferta;
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
public class OfertaViaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;

    @ManyToOne
    @JoinColumn(name = "viaje_id", nullable = false)
    private Viaje viaje;

    @ManyToOne
    @JoinColumn(name = "conductor_id", nullable = false)
    private  Usuario conductor;

    private Instant creadaEn, expiraEn;

    @Enumerated(EnumType.STRING)
    private EstadoOferta estado;


}
