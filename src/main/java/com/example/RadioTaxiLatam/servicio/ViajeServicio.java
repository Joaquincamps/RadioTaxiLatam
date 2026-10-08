package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.ViajeDTO;
import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ViajeServicio {

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsignacionViajeServicio asignacionViajeServicio;

    public ViajeDTO crearViaje(ViajeDTO viajeDTO) {
        Usuario cliente = usuarioRepository.findById(viajeDTO.getClienteId()).orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (cliente.getTipo() != TipoUsuario.CLIENTE) {
            throw new RuntimeException("El usuario indicado no es un cliente");
        }

        if(viajeDTO.getLongitudOrigen() == null || viajeDTO.getLatitudOrigen() == null){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Las corrdenadas no pueden ser nulas."
            );
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

        Viaje viaje = Viaje.builder().
                cliente(cliente).origen(viajeDTO.getOrigen()).destino(viajeDTO.getDestino()).estado(EstadoViaje.BUSCANDO_CONDUCTOR)
                .latitudOrigen(viajeDTO.getLatitudOrigen())
                .longitudOrigen(viajeDTO.getLongitudOrigen())
                .build();

        Viaje viajeGuardado = viajeRepository.save(viaje);

        asignacionViajeServicio.ofrecerViaje(viajeGuardado.getId());

        return ViajeDTO.builder()
                .id(viajeGuardado.getId()).clienteId(viajeGuardado.getCliente().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeDTO.getLatitudOrigen())
                .longitudOrigen(viajeDTO.getLongitudOrigen())
                .build();
    }

    public ViajeDTO obtenerViajePorId(Long id) {
        Viaje viaje = viajeRepository.findById(id).orElseThrow(() -> new RuntimeException("Viaje no encontrado"));

        return ViajeDTO.builder()
                .id(viaje.getId()).clienteId(viaje.getCliente().getId()).conductorId(viaje.getConductor() != null ? viaje.getConductor().getId() : null)
                .origen(viaje.getOrigen()).destino(viaje.getDestino()).estado(viaje.getEstado())
                .latitudOrigen(viaje.getLatitudOrigen())
                .longitudOrigen(viaje.getLongitudOrigen())
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
                            v.getEstado() == EstadoViaje.EN_CURSO) {
                        throw new RuntimeException("No se puede asignar el viaje");
                    }
                }
            }
        }

        viaje.setConductor(conductor);
        viaje.setEstado(EstadoViaje.ASIGNADO);

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId()).clienteId(viajeGuardado.getCliente().getId())
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
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

        return ViajeDTO.builder().id(viajeGuardado.getId()).clienteId(viajeGuardado.getCliente().getId())
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .build();
    }

    public ViajeDTO iniciarViaje(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("El viaje no existe."));
        Usuario conductor = usuarioRepository.findById(conductorId).orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != EstadoViaje.ACEPTADO) {
            throw new RuntimeException("El estado del viaje tiene que ser aceptado");
        }

        if (!viaje.getConductor().getId().equals(conductor.getId())) {
            throw new RuntimeException("El conductor asignado no coincide con la aceptación.");
        }

        viaje.setEstado(EstadoViaje.EN_CURSO);
        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId()).clienteId(viajeGuardado.getCliente().getId())
                .conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .build();

    }

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
        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId()).clienteId(viajeGuardado.getCliente()
                .getId()).conductorId(viajeGuardado.getConductor().getId()).origen(viajeGuardado
                .getOrigen()).destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
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
                viaje.getEstado() == EstadoViaje.EN_CURSO ||
                viaje.getEstado() == EstadoViaje.ASIGNADO) {
            viaje.setEstado(EstadoViaje.CANCELADO);
        } else {
            throw new RuntimeException("El viaje no se puede cancelar.");
        }

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder().id(viajeGuardado.getId()).clienteId(viajeGuardado
                .getCliente().getId()).conductorId(viajeGuardado.getConductor().getId())
                .origen(viajeGuardado.getOrigen()).destino(viajeGuardado.getDestino()).estado(viajeGuardado.getEstado())
                .latitudOrigen(viajeGuardado.getLatitudOrigen())
                .longitudOrigen(viajeGuardado.getLongitudOrigen())
                .build();

    }
}
