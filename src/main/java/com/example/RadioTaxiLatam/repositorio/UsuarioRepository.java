package com.example.RadioTaxiLatam.repositorio;

import com.example.RadioTaxiLatam.Dto.LoginDTO;
import com.example.RadioTaxiLatam.Dto.UsuarioDTO;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
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
public interface UsuarioRepository extends JpaRepository<Usuario,Long> {

    Usuario findByTelefono(String telefono);

    List<Usuario>
    findByTipoAndEstadoAndLatitudIsNotNullAndLongitudIsNotNullAndUltimaUbicacionAfter(
            TipoUsuario tipo,
            EstadoConductor estado,
            Instant fechaLimite
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> buscarPorIdConBloqueo(@Param("id") Long id);
}
