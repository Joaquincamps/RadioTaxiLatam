package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.Dto.ViajeDTO;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        if(usuario.getEstado() !=EstadoConductor.AVAILABLE){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El conductor tiene que estar Available."
            );
        }

        if(viaje.getEstado() != EstadoViaje.BUSCANDO_CONDUCTOR){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El viaje no está buscando conductor."
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

        ViajeDTO viajeDTO = ViajeDTO.builder()
                .id(viaje.getId())
                .clienteId(
                        viaje.getCliente() != null
                        ? viaje.getCliente().getId() : null
                )
                .conductorId(
                        viaje.getConductor() != null
                        ? viaje.getConductor().getId() : null
                )
                .origen(viaje.getOrigen())
                .destino(viaje.getDestino())
                .estado(viaje.getEstado())
                .latitudOrigen(viaje.getLatitudOrigen())
                .longitudOrigen(viaje.getLongitudOrigen())
                .telefonoPasajero(viaje.getTelefonoPasajero())
                .nombrePasajero(viaje.getNombrePasajero())
                .llamadaVapiId(viaje.getLlamadaVapiId())
                .build();

        return OfertaViajeDto.builder()
                .id(crearOfertaFinal.getId())
                .viaje(viajeDTO)
                .segundosRestantes(20)
                .distanciaRecogidaMetros(null)
                .build();

    }

    public List<OfertaViajeDto> obtenerOfertas(Long id){
        Usuario usuario = usuarioRepository.findById(id).orElseThrow(
                ()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conductor no encontrado.")
        );

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        Instant ahora = Instant.now();
        List<OfertaViaje> consultarSusOfertas =
                ofertaViajeRepository.findByConductor_IdAndEstadoAndExpiraEnAfter(usuario.getId(),EstadoOferta.PENDIENTE,ahora);

        List<OfertaViajeDto> resultado = new ArrayList<>();
        for(OfertaViaje oferta :consultarSusOfertas){
            Viaje viaje = oferta.getViaje();

            ViajeDTO viajeDTO = ViajeDTO.builder()
                    .id(viaje.getId())
                    .clienteId(
                            viaje.getCliente() != null
                                    ? viaje.getCliente().getId() : null
                    )
                    .conductorId(
                            viaje.getConductor() != null
                                    ? viaje.getConductor().getId() : null
                    )
                    .origen(viaje.getOrigen())
                    .destino(viaje.getDestino())
                    .estado(viaje.getEstado())
                    .latitudOrigen(viaje.getLatitudOrigen())
                    .longitudOrigen(viaje.getLongitudOrigen())
                    .telefonoPasajero(viaje.getTelefonoPasajero())
                    .nombrePasajero(viaje.getNombrePasajero())
                    .llamadaVapiId(viaje.getLlamadaVapiId())
                    .build();
            int segundosRestantes = (int) Duration.between(
                    ahora,
                    oferta.getExpiraEn()
            ).getSeconds();

            OfertaViajeDto ofertaViajeDto = OfertaViajeDto.builder()
                    .id(oferta.getId())
                    .viaje(viajeDTO)
                    .segundosRestantes(segundosRestantes)
                    .distanciaRecogidaMetros(null)
                    .build();

            resultado.add(ofertaViajeDto);
        }

        return resultado;
    }

    @Transactional
    public ViajeDTO aceptarOferta(Long ofertaId, Long conductorId){

        Long viajeId = ofertaViajeRepository
                .buscarViajeIdPorOfertaId(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));

        Viaje viaje = viajeRepository.buscarPorIdConBloqueo(viajeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Viaje no encontrado."
                ));

        Usuario usuario = usuarioRepository
                .buscarPorIdConBloqueo(conductorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conductor no encontrado."
                ));

        OfertaViaje oferta = ofertaViajeRepository
                .buscarPorIdConBloqueo(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        if(!oferta.getConductor().getId().equals(conductorId)){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El conductor no es el asignado al viaje."
            );
        }

        if(oferta.getEstado() != EstadoOferta.PENDIENTE){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "La oferta ya no está pendiente."
            );
        }

        if(!oferta.getExpiraEn().isAfter(Instant.now())){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "La oferta ha caducado."
            );
        }

        if(usuario.getEstado() != EstadoConductor.AVAILABLE){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El conductor no está disponible."
            );
        }

        if(viaje.getEstado() !=EstadoViaje.BUSCANDO_CONDUCTOR){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "El viaje ya no está buscando conductor."
            );
        }

        if(viaje.getConductor() !=null){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "El viaje ya tiene un conductor asignado."
            );
        }
        viaje.setConductor(usuario);
        viaje.setEstado(EstadoViaje.ACEPTADO);

        oferta.setEstado(EstadoOferta.ACEPTADA);
        usuario.setEstado(EstadoConductor.BUSY);

        ofertaViajeRepository.save(oferta);


        return ViajeDTO.builder()
                .id(viaje.getId())
                .clienteId(
                        viaje.getCliente() != null
                                ? viaje.getCliente().getId() : null
                )
                .conductorId(
                        viaje.getConductor() != null
                                ? viaje.getConductor().getId() : null
                )
                .origen(viaje.getOrigen())
                .destino(viaje.getDestino())
                .estado(viaje.getEstado())
                .latitudOrigen(viaje.getLatitudOrigen())
                .longitudOrigen(viaje.getLongitudOrigen())
                .telefonoPasajero(viaje.getTelefonoPasajero())
                .nombrePasajero(viaje.getNombrePasajero())
                .llamadaVapiId(viaje.getLlamadaVapiId())
                .build();

    }

    @Transactional
    public Long rechazarOferta(Long ofertaId, Long conductorId){
        Long viajeId = ofertaViajeRepository
                .buscarViajeIdPorOfertaId(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));

        Viaje viaje = viajeRepository.buscarPorIdConBloqueo(viajeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Viaje no encontrado."
                ));

        Usuario usuario = usuarioRepository.buscarPorIdConBloqueo(conductorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conductor no encontrado."
                ));

        OfertaViaje ofertaViaje = ofertaViajeRepository
                .buscarPorIdConBloqueo(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));

        if (!usuario.getTipo().equals(TipoUsuario.CONDUCTOR)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario tiene que ser conductor."
            );
        }

        if(!ofertaViaje.getConductor().getId().equals(usuario.getId())){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El conductor no es el asignado al viaje."
            );
        }

        if(ofertaViaje.getEstado() != EstadoOferta.PENDIENTE){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "La oferta ya no está pendiente."
            );
        }

        if(!ofertaViaje.getExpiraEn().isAfter(Instant.now())){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "La oferta ha caducado."
            );
        }

        ofertaViaje.setEstado(EstadoOferta.RECHAZADA);
        ofertaViajeRepository.save(ofertaViaje);

        return viaje.getId();
    }

    @Transactional
    public Long expiraOferta(Long ofertaId){
        Long viajeId = ofertaViajeRepository
                .buscarViajeIdPorOfertaId(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));
        Viaje viaje = viajeRepository.buscarPorIdConBloqueo(viajeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Viaje no encontrado."
                ));
        OfertaViaje ofertaViaje = ofertaViajeRepository
                .buscarPorIdConBloqueo(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Oferta de viaje no encontrada."
                ));
        if(ofertaViaje.getEstado() != EstadoOferta.PENDIENTE){
            return  null;
        }

        if (ofertaViaje.getExpiraEn().isAfter(Instant.now())) {
            return null;
        }

        ofertaViaje.setEstado(EstadoOferta.EXPIRADA);
        ofertaViajeRepository.save(ofertaViaje);

        if (viaje.getEstado() == EstadoViaje.BUSCANDO_CONDUCTOR
                && viaje.getConductor() == null) {
            return viaje.getId();
        }

        return null;
    }
}
