package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.entidades.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    boolean existsByConductor_IdAndEstadoIn(Long conductorId,
                                            Collection<EstadoViaje> estados);
}
