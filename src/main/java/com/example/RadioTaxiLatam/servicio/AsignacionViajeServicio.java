package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.OfertaViajeDto;
import com.example.RadioTaxiLatam.Enum.EstadoConductor;
import com.example.RadioTaxiLatam.Enum.EstadoViaje;
import com.example.RadioTaxiLatam.Enum.TipoUsuario;
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
import java.util.List;

@Service
public class AsignacionViajeServicio {

    private static final double RADIO_TIERRA_KM = 6371.0;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private OfertaViajeRepository ofertaViajeRepository;

    @Autowired
    private OfertaViajeServicio ofertaViajeServicio;

    public OfertaViajeDto ofrecerViaje(Long viajeId){
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(
                ()-> new ResponseStatusException(HttpStatus.NOT_FOUND ,
                        "El viaje no fue encontrada")
        );

        if(viaje.getEstado() != EstadoViaje.BUSCANDO_CONDUCTOR){
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "El viaje tiene que estas buscando conductor."
            );
        }

        if(viaje.getLatitudOrigen() ==null  || viaje.getLongitudOrigen() == null){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lac coordenadas de origen no pueden ser nulas."
            );
        }

        if(viaje.getConductor() != null){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El viaje ya tiene un conductor asignado.."
            );
        }

        List<Usuario> candidatos = usuarioRepository.findByTipoAndEstadoAndLatitudIsNotNullAndLongitudIsNotNullAndUltimaUbicacionAfter(
                TipoUsuario.CONDUCTOR, EstadoConductor.AVAILABLE, Instant.now().minusSeconds(120)
        );

        Usuario conductorMasCercano  = null;
        double menorDistancia = Double.POSITIVE_INFINITY;

        for(Usuario candidato :candidatos){
            boolean tieneOferta = ofertaViajeRepository.existsByViaje_IdAndConductor_Id(viajeId, candidato.getId());
            double distancia = calcularDistancia(viaje.getLatitudOrigen(),viaje.getLongitudOrigen(),
                    candidato.getLatitud(),candidato.getLongitud());

            if(tieneOferta){
                continue;
            }
            if(distancia < menorDistancia){
                menorDistancia = distancia;
                conductorMasCercano = candidato;
            }
        }

        if(conductorMasCercano == null){
            return  null;
        }

        return ofertaViajeServicio.crearOfertaViaje(
                viaje.getId(),
                conductorMasCercano.getId()
        );
    }

    private double calcularDistancia(double latitudOrigen, double longitudOrigen,
                                     double latitudConductor, double longitudConductor) {

        double latRadOrigen = Math.toRadians(latitudOrigen);
        double lonRadOrigen = Math.toRadians(longitudOrigen);
        double latRadCon = Math.toRadians(latitudConductor);
        double lonRadCon = Math.toRadians(longitudConductor);

        double deltaLat = latRadCon - latRadOrigen;
        double deltaLon = lonRadCon - lonRadOrigen;

        double a = Math.pow(Math.sin(deltaLat / 2), 2) +
                Math.cos(latRadOrigen) * Math.cos(latRadCon) *
                        Math.pow(Math.sin(deltaLon / 2), 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return RADIO_TIERRA_KM * c * 1000;
    }
}
