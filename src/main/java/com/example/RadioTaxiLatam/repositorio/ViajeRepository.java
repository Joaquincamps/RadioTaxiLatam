package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.entidades.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {
}
