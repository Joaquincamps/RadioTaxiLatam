package com.example.RadioTaxiLatam.servicio;

import com.example.RadioTaxiLatam.Dto.CoordenadasDTO;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GeocodificacionServicio {

    @Value("${geocoding.url}")
    private String url;

    @Value("${geocoding.country-codes:es}")
    private String paises;

    private final RestClient cliente;

    private final Map<String, List<Map<String, Object>>> cache =
            new HashMap<>();

    private long ultimaPeticion;

    public GeocodificacionServicio() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(10));

        cliente = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public synchronized CoordenadasDTO obtenerCoordenadas(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes indicar una dirección de recogida."
            );
        }

        String direccionLimpia = direccion.trim().replaceAll("\\s+", " ");

        String claveCache = url + "|" + paises + "|"
                + direccionLimpia.toLowerCase(Locale.ROOT);

        List<Map<String, Object>> resultados = cache.get(claveCache);

        if (resultados == null) {
            String consulta = URLEncoder.encode(
                    direccionLimpia,
                    StandardCharsets.UTF_8
            );

            String paisesCodificados = URLEncoder.encode(
                    paises,
                    StandardCharsets.UTF_8
            );

            URI uri = URI.create(
                    url
                            + "?format=jsonv2"
                            + "&limit=2"
                            + "&countrycodes=" + paisesCodificados
                            + "&q=" + consulta
            );

            try {
                // Separa las peticiones externas al menos 1,1 segundos.
                long espera = 1100
                        - (System.currentTimeMillis() - ultimaPeticion);

                if (espera > 0) {
                    Thread.sleep(espera);
                }

                ultimaPeticion = System.currentTimeMillis();

                resultados = cliente.get()
                        .uri(uri)
                        .header("User-Agent", "RadioTaxiLatam/2VersionMVP")
                        .header("Accept-Language", "es")
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                List<Map<String, Object>>>() {});

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "La búsqueda de la dirección fue interrumpida."
                );

            } catch (RestClientException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "No se pudo consultar el servicio de direcciones."
                );
            }

            if (resultados == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "El servicio de direcciones devolvió una respuesta vacía."
                );
            }

            cache.put(claveCache, resultados);
        }

        if (resultados.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontró la dirección. Indica calle, número y ciudad."
            );
        }

        if (resultados.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Hay varias coincidencias. Indica una dirección más precisa."
            );
        }

        Map<String, Object> resultado = resultados.getFirst();

        try {
            double latitud = Double.parseDouble(
                    String.valueOf(resultado.get("lat"))
            );

            double longitud = Double.parseDouble(
                    String.valueOf(resultado.get("lon"))
            );

            String direccionEncontrada =
                    (String) resultado.get("display_name");

            if (!Double.isFinite(latitud)
                    || !Double.isFinite(longitud)
                    || latitud < -90 || latitud > 90
                    || longitud < -180 || longitud > 180
                    || direccionEncontrada == null
                    || direccionEncontrada.isBlank()) {
                throw new IllegalArgumentException();
            }

            return CoordenadasDTO.builder()
                    .latitud(latitud)
                    .longitud(longitud)
                    .direccionEncontrada(direccionEncontrada)
                    .build();

        } catch (IllegalArgumentException | ClassCastException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio devolvió unas coordenadas incorrectas."
            );
        }
    }
}
