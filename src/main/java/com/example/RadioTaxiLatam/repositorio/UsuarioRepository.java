package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Dto.LoginDTO;
import com.example.RadioTaxiLatam.Dto.UsuarioDTO;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario,Long> {

    Usuario findByTelefono(String telefono);

    List<Usuario>
    findByTipoAndEstadoAndLatitudIsNotNullAndLongitudIsNotNullAndUltimaUbicacionAfter(
            TipoUsuario tipo,
            EstadoConductor estado,
            Instant fechaLimite
    );
}
