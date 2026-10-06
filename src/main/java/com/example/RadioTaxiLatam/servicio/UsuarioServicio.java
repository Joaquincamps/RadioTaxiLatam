package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.LoginDTO;
import com.example.RadioTaxiLatam.Dto.UbicacionDTO;
import com.example.RadioTaxiLatam.Dto.UsuarioDTO;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioServicio {

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    public UsuarioDTO login(LoginDTO loginDTO) {
        Usuario usuario = usuarioRepository.findByTelefono(loginDTO.getMovil());
        if (usuario.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario no es conductor");
        }

        if (loginDTO.getPassword().equals(usuario.getPassword())) {
            usuario.setEstado(EstadoConductor.AVAILABLE);
            return UsuarioDTO.builder()
                    .id(usuario.getId())
                    .nombre(usuario.getNombre())
                    .telefono(usuario.getTelefono())
                    .tipo(usuario.getTipo())
                    .build();
        }
        return null;
    }
}
