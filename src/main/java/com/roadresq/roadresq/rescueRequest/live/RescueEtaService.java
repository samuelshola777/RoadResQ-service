package com.roadresq.roadresq.rescueRequest.live;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.rescueRequest.RescueRequest;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescueEtaService {

    private static final URI ROUTES = URI.create(
            "https://routes.googleapis.com/directions/v2:computeRoutes"
    );

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Value("${app.google-routes-api-key:${GOOGLE_ROUTES_API_KEY:}}")
    private String apiKey;

    public Integer getEtaMinutes(
            RescueRequest request, VendorMapMarker origin, RescueLocation destination
    ) {
        if (apiKey.isBlank() || origin == null || destination == null) {
            return null;
        }

        String key = "roadresq:eta:" + request.getId() + ":" + request.getJobStage().name();

        // All customer sessions share one lookup per job leg per ten seconds.
        if (Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(key + ":refresh", "1", Duration.ofSeconds(10)))) {

            var body = Map.of(
                    "origin", waypoint(origin.latitude(), origin.longitude()),
                    "destination", waypoint(destination.latitude(), destination.longitude()),
                    "travelMode", "DRIVE",
                    "routingPreference", "TRAFFIC_AWARE"
            );

            HttpRequest query = HttpRequest.newBuilder(ROUTES)
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.duration")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            // Keep GPS updates flowing while the routing service calculates the estimate.
            client.sendAsync(query, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            log.debug("Driving ETA unavailable, HTTP {}", response.statusCode());
                            return;
                        }

                        String duration = mapper.readTree(response.body())
                                .at("/routes/0/duration")
                                .asString("");

                        if (duration.endsWith("s")) {
                            int minutes = new BigDecimal(duration.substring(0, duration.length() - 1))
                                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING)
                                    .intValueExact();
                            if (minutes >= 0) {
                                redis.opsForValue().set(
                                        key, Integer.toString(minutes), Duration.ofSeconds(15)
                                );
                            }
                        }
                    })
                    .exceptionally(error -> {
                        log.debug("Driving ETA lookup failed: {}", error.getClass().getSimpleName());
                        return null;
                    });
        }

        String minutes = redis.opsForValue().get(key);
        return minutes == null ? null : Integer.valueOf(minutes);
    }

    private Map<String, Object> waypoint(double latitude, double longitude) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", latitude,
                "longitude", longitude
        )));
    }
}