package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.EstadoOferta;
import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.OfertaViaje;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.OfertaViajeRepository;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class OfertaViajeServicio {

    @Autowired
    private OfertaViajeRepository ofertaViajeRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public OfertaViajeDto crearOfertaViaje(Long viajeId, Long conductorId){
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND
                        , "Viaje no encontrado.")
        );

        Usuario usuario = usuarioRepository.findById(conductorId).orElseThrow(
                ()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conductor no encontrado.")
        );

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        if(!usuario.getEstado().equals(EstadoConductor.AVAILABLE)){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El conductor tiene que estar Available."
            );
        }

        if(!viaje.getEstado().equals(EstadoViaje.BUSCANDO_CONDUCTOR)){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tienes que estar buscando viaje."
            );
        }

        if(viaje.getConductor() != null){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El viaje no puede tener un conductor asignado."
            );
        }

        Instant ahora = Instant.now();

        boolean tieneOfertaVigente =
                ofertaViajeRepository.existsByViaje_IdAndEstadoAndExpiraEnAfter(
                        viaje.getId(),
                        EstadoOferta.PENDIENTE,
                        ahora
                );

        if (tieneOfertaVigente) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El viaje ya tiene una oferta pendiente vigente."
            );
        }

        OfertaViaje crearOfertaFinal = new OfertaViaje();
        crearOfertaFinal.setViaje(viaje);
        crearOfertaFinal.setConductor(usuario);
        crearOfertaFinal.setCreadaEn(ahora);
        crearOfertaFinal.setExpiraEn(ahora.plusSeconds(20));
        crearOfertaFinal.setEstado(EstadoOferta.PENDIENTE);

        ofertaViajeRepository.save(crearOfertaFinal);

        return OfertaViajeDto.builder()
                .id(crearOfertaFinal.getId())
                .viaje(crearOfertaFinal.getViaje())

                .build();

    }
}
