package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.ViajeDTO;
import com.example.RadioTaxiLatam.Enum.DriverStatus;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
import com.example.RadioTaxiLatam.entidades.Usuario;
import com.example.RadioTaxiLatam.entidades.Viaje;
import com.example.RadioTaxiLatam.repositorio.UsuarioRepository;
import com.example.RadioTaxiLatam.repositorio.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViajeServicio {

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public ViajeDTO crearViaje(ViajeDTO viajeDTO) {
        Usuario cliente = usuarioRepository.findById(viajeDTO.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (cliente.getTipo() != TipoUsuario.CLIENTE) {
            throw new RuntimeException("El usuario indicado no es un cliente");
        }

        Viaje viaje = Viaje.builder()
                .cliente(cliente)
                .origen(viajeDTO.getOrigen())
                .destino(viajeDTO.getDestino())
                .estado(DriverStatus.BUSCANDO_CONDUCTOR)
                .build();

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder()
                .id(viajeGuardado.getId())
                .clienteId(viajeGuardado.getCliente().getId())
                .origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino())
                .estado(viajeGuardado.getEstado())
                .build();
    }

    public ViajeDTO obtenerViajePorId(Long id) {
        Viaje viaje = viajeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Viaje no encontrado"));

        return ViajeDTO.builder()
                .id(viaje.getId())
                .clienteId(viaje.getCliente().getId())
                .conductorId(viaje.getConductor() != null ? viaje.getConductor().getId() : null)
                .origen(viaje.getOrigen())
                .destino(viaje.getDestino())
                .estado(viaje.getEstado())
                .build();
    }

    public List<Viaje> obtenerTodosLosViajes() {
        return viajeRepository.findAll();
    }

    public ViajeDTO asignarConductor(Long viajeId, Long conductorId) {
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> new RuntimeException("El viaje no existe."));

        Usuario conductor = usuarioRepository.findById(conductorId)
                .orElseThrow(() -> new RuntimeException("El conductor no existe."));

        if (conductor.getTipo() != TipoUsuario.CONDUCTOR) {
            throw new RuntimeException("El usuario indicado no es un conductor.");
        }

        if (viaje.getEstado() != DriverStatus.BUSCANDO_CONDUCTOR) {
            throw new RuntimeException("El viaje no está buscando conductor.");
        }

        viaje.setConductor(conductor);
        viaje.setEstado(DriverStatus.ASIGNADO);

        Viaje viajeGuardado = viajeRepository.save(viaje);

        return ViajeDTO.builder()
                .id(viajeGuardado.getId())
                .clienteId(viajeGuardado.getCliente().getId())
                .conductorId(viajeGuardado.getConductor().getId())
                .origen(viajeGuardado.getOrigen())
                .destino(viajeGuardado.getDestino())
                .estado(viajeGuardado.getEstado())
                .build();
    }

    public ViajeDTO aceptarViaje(Long viajeId){

    }

    public ViajeDTO iniciarViaje(Long viajeId){

    }

    public ViajeDTO finalizarViaje(Long viajeId){

    }

    public ViajeDTO cancelarViaje(Long viajeId){
        
    }
}
