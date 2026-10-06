package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.*;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class UsuarioServicio {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    public UsuarioDTO crearUsuario(UsuarioDTO usuarioDto) {
        Usuario usuario = Usuario.builder()
                .nombre(usuarioDto.getNombre())
                .telefono(usuarioDto.getTelefono())
                .tipo(usuarioDto.getTipo())
                .build();
        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        return UsuarioDTO.builder()
                .id(usuarioGuardado.getId())
                .nombre(usuarioGuardado.getNombre())
                .telefono(usuarioGuardado.getTelefono())
                .tipo(usuarioGuardado.getTipo())
                .build();
    }

    public UsuarioDTO obtenerUsuarioPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new RuntimeException("No existe el usuario")
        );
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .telefono(usuario.getTelefono())
                .tipo(usuario.getTipo())
                .latitud(usuario.getLatitud())
                .longitud(usuario.getLongitud())
                .build();
    }

    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    public void eliminarUsuarioPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new RuntimeException("No existe el usuario")
        );
        usuarioRepository.deleteById(id);
    }

    public ConductorDTO login(LoginDTO loginDTO) {
        Usuario usuario = usuarioRepository.findByTelefono(loginDTO.getMovil());

        if (usuario == null) {
            throw new RuntimeException("Telefono o contraseña incorrecta.");
        }

        if (usuario.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario no es conductor");
        }

        if (!loginDTO.getPassword().equals(usuario.getPassword())) {
            throw new RuntimeException("Contraseña o telefono incorrecto.");
        }

        if (usuario.getEstado() != EstadoConductor.BUSY) {
            usuario.setEstado(EstadoConductor.AVAILABLE);
        }
        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        return ConductorDTO.builder()
                .id(usuarioGuardado.getId())
                .nombre(usuarioGuardado.getNombre())
                .telefono(usuarioGuardado.getTelefono())
                .matricula(usuarioGuardado.getMatricula())
                .modeloVehiculo(usuarioGuardado.getModeloVehiculo())
                .estado(usuarioGuardado.getEstado())
                .build();
    }

    public ConductorDTO cambiarDisponibilidad(Long id, EstadoConductorDTO estadoConductorDTO) {
        if (estadoConductorDTO == null ||
                estadoConductorDTO.getEstado() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST
                    , "Debes indicar el estado del conductor");
        }

        EstadoConductor nuevoEstado = estadoConductorDTO.getEstado();

        if (nuevoEstado != EstadoConductor.AVAILABLE
                && nuevoEstado != EstadoConductor.OFFLINE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo puedes cambiar a Available o Offline."
            );
        }

        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND
                        , "Usuario no encontrado.")
        );

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        boolean tieneCarreraActiva =
                viajeRepository.existsByConductor_IdAndEstadoIn(
                        usuario.getId(),
                        List.of(
                                EstadoViaje.ASIGNADO,
                                EstadoViaje.ACEPTADO,
                                EstadoViaje.EN_CURSO
                        )
                );
        if (tieneCarreraActiva) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede cambiar tu disponibilidad con una carrera activa"
            );
        }
        usuario.setEstado(nuevoEstado);

        Usuario usuarioGuardado = usuarioRepository.save(usuario);

        return ConductorDTO.builder()
                .id(usuarioGuardado.getId())
                .nombre(usuarioGuardado.getNombre())
                .telefono(usuarioGuardado.getTelefono())
                .matricula(usuarioGuardado.getMatricula())
                .modeloVehiculo(usuarioGuardado.getModeloVehiculo())
                .estado(usuarioGuardado.getEstado())
                .build();
    }

    public UbicacionDTO enviarUbicacion(Long id, UbicacionDTO ubicacionDTO) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND
                        , "Usuario no encontrado.")
        );

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        if (ubicacionDTO.getLatitud() == null || ubicacionDTO.getLongitud() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las coordenadas no tienen que ser nulas."
            );
        }

        if (ubicacionDTO.getLatitud() < -90 || ubicacionDTO.getLatitud() > 90) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La latitud tiene que ser entre -90 y 90."
            );
        }

        if (ubicacionDTO.getLongitud() < -180 || ubicacionDTO.getLongitud() > 180) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La longitud tiene que ser entre -180 y 180."
            );
        }

        usuario.setLatitud(ubicacionDTO.getLatitud());
        usuario.setLongitud(ubicacionDTO.getLongitud());
        usuario.setUltimaUbicacion(Instant.now());
        usuarioRepository.save(usuario);
        return ubicacionDTO;
    }
}
