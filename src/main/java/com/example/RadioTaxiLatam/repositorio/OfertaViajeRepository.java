package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Enum.EstadoOferta;
import com.example.RadioTaxiLatam.entidades.OfertaViaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OfertaViajeRepository extends JpaRepository<OfertaViaje, Long> {

    boolean existsByViaje_IdAndEstadoAndExpiraEnAfter(Long viajeId, EstadoOferta estadoOferta, Instant ahora);

    List<OfertaViaje> findByConductor_IdAndEstadoAndExpiraEnAfter(
            Long conductorId,
            EstadoOferta estado,
            Instant ahora
    );

    boolean existsByViaje_IdAndConductor_Id(
            Long viajeId,
            Long conductorId
    );

    List<OfertaViaje> findByEstadoAndExpiraEnLessThanEqual(
            EstadoOferta estado,
            Instant ahora
    );
}
