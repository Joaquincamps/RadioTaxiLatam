package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.*;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Usuario no encontrado.")
        );
        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new RuntimeException("El usuario tiene que ser conductor.");
        }
        if (estadoConductorDTO == null) {
            throw new RuntimeException("El estado del conductor es incorrecto");
        }

        if(estadoConductorDTO.getEstado() == null){
            throw new RuntimeException("El estado del conductor es incorrecto");
        }

        if(usuario.getEstado() == estadoConductorDTO.getEstado()){
            throw new RuntimeException("El estado no puede ser BUSY");
        }

        Viaje viaje = viajeRepository.findById(id).orElseThrow(
                ()-> new RuntimeException("No se encontró el viaje.")
        );

        if(viaje.getConductor().getId().equals(usuario.getId())){

        }
    }
}
