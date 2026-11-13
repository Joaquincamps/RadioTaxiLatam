package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.entidades.Viaje;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    boolean existsByConductor_IdAndEstadoIn(Long conductorId,
                                            Collection<EstadoViaje> estados);

    Optional<Viaje> findByLlamadaVapiId(String llamadaVapiId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Viaje v WHERE v.id = :id")
    Optional<Viaje> buscarPorIdConBloqueo(@Param("id") Long id);
}
