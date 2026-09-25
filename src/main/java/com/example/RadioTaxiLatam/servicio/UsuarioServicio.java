package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.UsuarioRequestDTO;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsuarioServicio {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public UsuarioRequestDTO crearUsuario(UsuarioRequestDTO usuarioDto) {
        return usuarioRepository.save(usuarioDto);
    }
}
