package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AsignacionViajeServicio {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    public OfertaViajeDto ofrecerViaje(Long viajeId){
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(
                ()-> new ResponseStatusException(HttpStatus.NOT_FOUND ,
                        "El viaje no fue encontrada")
        );

        List<Usuario> candidatos = usuarioRepository.findByTipoAndEstadoAndLatitudIsNotNullAndLongitudIsNotNullAndUltimaUbicacionAfter(
                TipoUsuario.CONDUCTOR, EstadoConductor.AVAILABLE, Instant.now().minusSeconds(120)
        );
        for(Usuario candidato :candidatos){

        }
    }
}
