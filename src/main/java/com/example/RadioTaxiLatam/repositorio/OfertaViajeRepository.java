package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Enum.EstadoOferta;
import com.example.RadioTaxiLatam.entidades.OfertaViaje;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    List<OfertaViaje> findByViaje_IdAndEstado(
            Long viajeId,
            EstadoOferta estado
    );

    @Query("SELECT o.viaje.id FROM OfertaViaje o WHERE o.id = :ofertaId")
    Optional<Long> buscarViajeIdPorOfertaId(@Param("ofertaId") Long ofertaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OfertaViaje o WHERE o.id = :id")
    Optional<OfertaViaje> buscarPorIdConBloqueo(
            @Param("id") Long id
    );
}
