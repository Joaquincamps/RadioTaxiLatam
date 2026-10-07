package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Enum.EstadoOferta;
import com.example.RadioTaxiLatam.entidades.OfertaViaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface OfertaViajeRepository extends JpaRepository<OfertaViaje, Long> {

    boolean existsByViaje_IdAndEstadoAndExpiraEnAfter(Long viajeId, EstadoOferta estadoOferta, Instant ahora);
}
