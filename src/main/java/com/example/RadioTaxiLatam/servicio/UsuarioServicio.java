package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.UsuarioDTO;
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
                .build();
    }

    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                () -> new RuntimeException("No existe el usuario")
        );
        usuarioRepository.deleteById(id);
    }
}
