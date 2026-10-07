package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.entidades.OfertaViaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfertaViajeRepository extends JpaRepository<OfertaViaje, Long> {
}
