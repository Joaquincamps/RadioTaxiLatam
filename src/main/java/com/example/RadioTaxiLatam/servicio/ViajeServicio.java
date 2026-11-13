package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.CoordenadasDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ViajeServicio {

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsignacionViajeServicio asignacionViajeServicio;

    @Autowired
    private GeocodificacionServicio geocodificacionServicio;

    @Autowired
    private OfertaViajeRepository ofertaViajeRepository;

    public ViajeDTO crearViaje(ViajeDTO viajeDTO) {

        String llamadaVapiId = viajeDTO.getLlamadaVapiId();

        if (llamadaVapiId == null || llamadaVapiId.isBlank()) {
            llamadaVapiId = null;
        } else {
            llamadaVapiId = llamadaVapiId.trim();
        }

        viajeDTO.setLlamadaVapiId(llamadaVapiId);

        if (llamadaVapiId != null && !llamadaVapiId.isBlank()) {
            Optional<Viaje> viajeExistente =
                    viajeRepository.findByLlamadaVapiId(llamadaVapiId);

            if (viajeExistente.isPresent()) {
                return obtenerViajePorId(viajeExistente.get().getId());
            }
        }

        if (viajeDTO.getLatitudOrigen() == null
                || viajeDTO.getLongitudOrigen() == null) {

            CoordenadasDTO coordenadas =
                    geocodificacionServicio.obtenerCoordenadas(
                            viajeDTO.getOrigen()
                    );

            viajeDTO.setLatitudOrigen(coordenadas.getLatitud());
            viajeDTO.setLongitudOrigen(coordenadas.getLongitud());
        }

        if(viajeDTO.getLatitudOrigen() < -90 || viajeDTO.getLatitudOrigen() > 90){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las corrdenadas de latitud no son correctas."
            );
        }

        if(viajeDTO.getLongitudOrigen() < -180 || viajeDTO.getLongitudOrigen() > 180){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las corrdenadas de longitud no son correctas."
            );
        }

        Viaje viaje = Viaje.builder().origen(viajeDTO.getOrigen()).destino(viajeDTO.getDestino()).estado(EstadoViaje.BUSCANDO_CONDUCTOR)
                .latitudOrigen(viajeDTO.getLatitudOrigen())
                .longitudOrigen(viajeDTO.getLongitudOrigen())
                .telefonoPasajero(viajeDTO.getTelefonoPasajero())
                .nombrePasajero(viajeDTO.getNombrePasajero())
                .llamadaVapiId(viajeDTO.getLlamadaVapiId())
                .build();

        Viaje viajeGuardado;

        try {
            viajeGuardado = viajeRepository.saveAndFlush(viaje);

        } catch (DataIntegrityViolationException e) {
            if (llamadaVapiId != null && !llamadaVapiId.isBlank()) {
                Optional<Viaje> existente =
                        viajeRepository.findByLlamadaVapiId(llamadaVapiId);

                if (existente.isPresent()) {
                    return obtenerViajePorId(existente.get().getId());
                }
            }

            throw e;
        }
        asignacionViajeServicio.ofrecerViaje(viajeGuardado.getId());

        return ViajeDTO.builder()
                .id(viajeGuardado.getId())
                .origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeDTO.getLatitudOrigen())
                .longitudOrigen(viajeDTO.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .build();
    }

    public ViajeDTO obtenerViajePorId(Long id) {
        Viaje viaje = viajeRepository.findById(id).orElseThrow(() -> new RuntimeException("Viaje no encontrado"));

        return ViajeDTO.builder()
                .id(viaje.getId())
                .clienteId(viaje.getCliente() != null ? viaje.getCliente().getId() : null)
                .conductorId(viaje.getConductor() != null ? viaje.getConductor().getId() : null)
                .origen(viaje.getOrigen()).destino(viaje.getDestino()).estado(viaje.getEstado())
                .latitudOrigen(viaje.getLatitudOrigen())
                .longitudOrigen(viaje.getLongitudOrigen())
                .telefonoPasajero(viaje.getTelefonoPasajero())
                .nombrePasajero(viaje.getNombrePasajero())
                .llamadaVapiId(viaje.getLlamadaVapiId())
                .finalizadoEn(viaje.getFinalizadoEn())
                .build();
    }

    public List<Viaje> obtenerTodosLosViajes() {
        return viajeRepository.findAll();
    }

    public ViajeDTO asignarConductor(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));

        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != EstadoViaje.BUSCANDO_CONDUCTOR) {
            throw new RuntimeException("El viaje no está buscando conductor.");
        }

        List<Viaje> viajes = viajeRepository.findAll();
        for (Viaje v : viajes) {
            if (v.getConductor() != null) {
                if (v.getConductor().getId().equals(conductorId)) {
                    if (v.getEstado() == EstadoViaje.ASIGNADO ||
                            v.getEstado() == EstadoViaje.ACEPTADO ||
                            v.getEstado() == EstadoViaje.LLEGADO||
                            v.getEstado() == EstadoViaje.EN_CURSO) {
                        throw new RuntimeException("No se puede asignar el viaje");
                    }
                }
            }
        }

        viaje.setConductor(conductor);
        viaje.setEstado(EstadoViaje.ASIGNADO);

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder()
                .id(viajeGuardado.getId())
                .clienteId(
                        viajeGuardado.getCliente() != null
                                ? viajeGuardado.getCliente().getId()
                                : null
                )
                .conductorId(viajeGuardado.getConductor().getId())
                .origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .build();
    }

    public ViajeDTO aceptarViaje(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));
        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != EstadoViaje.ASIGNADO) {
            throw new RuntimeException("El viaje no está asignado a ningún conductor.");
        }

        if (!viaje.getConductor().getId().equals(conductor.getId())) {
            throw new RuntimeException("El conductor asignado no coincide con la aceptación.");
        }

        viaje.setEstado(EstadoViaje.ACEPTADO);

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder()
                .id(viajeGuardado.getId())
                .clienteId(
                        viajeGuardado.getCliente() != null
                                ? viajeGuardado.getCliente().getId()
                                : null
                )
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .build();
    }

    public ViajeDTO iniciarViaje(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));
        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != EstadoViaje.LLEGADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Debes registrar la llegada antes de iniciar el viaje."
            );
        }

        if (!viaje.getConductor().getId().equals(conductor.getId())) {
            throw new RuntimeException("El conductor asignado no coincide con la aceptación.");
        }

        viaje.setEstado(EstadoViaje.EN_CURSO);
        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId())
                .clienteId(
                        viajeGuardado.getCliente() != null
                                ? viajeGuardado.getCliente().getId()
                                : null
                )
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .build();

    }

    @Transactional
    public ViajeDTO finalizarViaje(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));
        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != EstadoViaje.EN_CURSO) {
            throw new RuntimeException("El estado del viaje tiene que ser aceptado");
        }

        if (!viaje.getConductor().getId().equals(conductor.getId())) {
            throw new RuntimeException("El conductor asignado no coincide con la aceptación.");
        }

        viaje.setEstado(EstadoViaje.FINALIZADO);
        viaje.setFinalizadoEn(Instant.now());

        conductor.setEstado(EstadoConductor.AVAILABLE);

        Viaje viajeGuardado = viajeRepository.save(viaje);
        usuarioRepository.save(conductor);

        return ViajeDTO.builder().id(viajeGuardado.getId())
                .clienteId(
                        viajeGuardado.getCliente() != null
                                ? viajeGuardado.getCliente().getId()
                                : null
                )
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado
                .getOrigen()).destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .finalizadoEn(viajeGuardado.getFinalizadoEn())
                .build();

    }

    public ViajeDTO cancelarViaje(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));
        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (!viaje.getConductor().getId().equals(conductor.getId())) {
            throw new RuntimeException("El conductor asignado no coincide con la aceptación.");
        }

        if (viaje.getEstado() == EstadoViaje.ACEPTADO ||
               viaje.getEstado() == EstadoViaje.LLEGADO ||
                viaje.getEstado() == EstadoViaje.EN_CURSO ||
                viaje.getEstado() == EstadoViaje.ASIGNADO) {
            viaje.setEstado(EstadoViaje.CANCELADO);
        } else {
            throw new RuntimeException("El viaje no se puede cancelar.");
        }

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId())
                .clienteId(
                        viajeGuardado.getCliente() != null
                                ? viajeGuardado.getCliente().getId()
                                : null
                )
                .conductorId(viajeGuardado.getConductor().getId())
                .origen(viajeGuardado.getOrigen()).destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .telefonoPasajero(viajeGuardado.getTelefonoPasajero())
                .nombrePasajero(viajeGuardado.getNombrePasajero())
                .llamadaVapiId(viajeGuardado.getLlamadaVapiId())
                .build();

    }

    @Transactional
    public ViajeDTO cancelarViajePorPasajero(Long viajeId, String llamadaVapiId){
        if( llamadaVapiId == null  || llamadaVapiId.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "EL ID de Vapi no puede ser vacío o nulo."
            );
        }
        Viaje viajeBuscar = viajeRepository.buscarPorIdConBloqueo(viajeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El viaje no existe."
                ));
        if (!llamadaVapiId.trim().equals(viajeBuscar.getLlamadaVapiId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,"Los ids de Vapi no coinciden."
            );
        }
        if(viajeBuscar.getEstado() == EstadoViaje.CANCELADO){
            return obtenerViajePorId(viajeId);
        }

        Usuario conductor = null;

        if (viajeBuscar.getConductor() != null) {
            conductor = usuarioRepository.buscarPorIdConBloqueo(
                    viajeBuscar.getConductor().getId()
            ).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Conductor no encontrado."
            ));
        }


        EstadoViaje estado = viajeBuscar.getEstado();

        if (estado != EstadoViaje.BUSCANDO_CONDUCTOR
                && estado != EstadoViaje.ASIGNADO
                && estado != EstadoViaje.ACEPTADO
                && estado != EstadoViaje.LLEGADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El viaje ya no se puede cancelar por esta vía."
            );
        }

        viajeBuscar.setEstado(EstadoViaje.CANCELADO);

        List<OfertaViaje> ofertasPendientes =
                ofertaViajeRepository.findByViaje_IdAndEstado(
                        viajeId,
                        EstadoOferta.PENDIENTE
                );

        for (OfertaViaje oferta : ofertasPendientes) {
            oferta.setEstado(EstadoOferta.CANCELADA);
        }

        ofertaViajeRepository.saveAll(ofertasPendientes);


        if (conductor != null) {
            conductor.setEstado(EstadoConductor.AVAILABLE);
            usuarioRepository.save(conductor);
        }

        viajeRepository.save(viajeBuscar);

        return obtenerViajePorId(viajeId);
    }

    @Transactional
    public ViajeDTO registrarLlegada(Long viajeId, Long conductorId){

        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(
                ()-> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "El viaje no existe"
                )
        );

        if (viaje.getConductor() == null
                || !viaje.getConductor().getId().equals(conductorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El viaje no pertenece a este conductor."
            );
        }


        if(viaje.getEstado() == EstadoViaje.LLEGADO){
            return obtenerViajePorId(viajeId);
        }

        if (viaje.getEstado() != EstadoViaje.ACEPTADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo puedes registrar la llegada de un viaje aceptado."
            );
        }

        viaje.setEstado(EstadoViaje.LLEGADO);
        viajeRepository.save(viaje);

        return obtenerViajePorId(viajeId);
    }
}
