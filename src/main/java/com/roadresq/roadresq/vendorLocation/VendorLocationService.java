package com.roadresq.roadresq.vendorLocation;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.rescueRequest.RescueRequest;
import com.roadresq.roadresq.rescueRequest.RescueRequestRepository;
import com.roadresq.roadresq.rescueRequest.RescueRequestStatus;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.UnauthorizedException;
import com.roadresq.roadresq.vendorLocation.request.VendorLocationRequest;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import com.roadresq.roadresq.vendorOperation.VendorOperationRepository;
import com.roadresq.roadresq.vendorOperation.vendorOperations;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorLocationService {

    private static final String GEO_KEY = "roadresq:{vendor-location}:geo";
    private static final String SEEN_KEY = "roadresq:{vendor-location}:seen";
    private static final String CAPTURED_KEY = "roadresq:{vendor-location}:captured";
    private static final List<String> KEYS = List.of(GEO_KEY, SEEN_KEY, CAPTURED_KEY);

    private static final long FRESHNESS_MILLIS = 60_000;
    private static final double GEO_MAX_LATITUDE = 85.05112878;
    private static final int SEARCH_LIMIT = 200;

    private static final DefaultRedisScript<Long> UPDATE_LOCATION = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REMOVE_LOCATIONS = new DefaultRedisScript<>();

    static {
        UPDATE_LOCATION.setLocation(new ClassPathResource("redis/update-vendor-location.lua"));
        UPDATE_LOCATION.setResultType(Long.class);
        REMOVE_LOCATIONS.setLocation(new ClassPathResource("redis/remove-vendor-locations.lua"));
        REMOVE_LOCATIONS.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final VendorOperationRepository vendorOperationRepository;
    private final RescueRequestRepository rescueRequestRepository;
    private final HttpServletRequest httpServletRequest;

    public void updateLocation(VendorLocationRequest request) {
        updateLocation(getCurrentVendorId(), request);
    }

    public void removeLocation() {
        removeLocation(getCurrentVendorId());
    }

    public void updateLocation(String vendorId, VendorLocationRequest request) {
        long now = System.currentTimeMillis();

        if (request.capturedAt() < now - 30_000 || request.capturedAt() > now + 10_000) {
            throw new InvalidInputException("Location capture time is outside the allowed range");
        }

        redis.execute(UPDATE_LOCATION, KEYS, vendorId, request.longitude().toString(),
                request.latitude().toString(), request.capturedAt().toString(), Long.toString(now));
    }

    public void removeLocation(String vendorId) {
        redis.execute(REMOVE_LOCATIONS, KEYS, "", vendorId);
    }

    public List<VendorMapMarker> getNearbyMarkers(RescueLocation center) {
        List<VendorMapMarker> nearby = findNearby(center, 5);
        var ids = nearby.stream().map(VendorMapMarker::vendorId).toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        var activeVendors = vendorOperationRepository.findAllByVendorIdInAndActiveTrue(ids)
                .stream()
                .map(vendorOperations::getVendorId)
                .collect(Collectors.toSet());

        rescueRequestRepository.findByStatusAndAssignedVendorIdIn(RescueRequestStatus.ASSIGNED, ids)
                .stream()
                .map(RescueRequest::getAssignedVendorId)
                .forEach(activeVendors::remove);

        return nearby.stream()
                .filter(marker -> activeVendors.contains(marker.vendorId()))
                .map(marker -> new VendorMapMarker(
                        marker.vendorId(),
                        roundMarker(marker.latitude()),
                        roundMarker(marker.longitude())))
                .toList();
    }

    public List<VendorMapMarker> findNearby(RescueLocation center, int radiusKm) {
        if (Math.abs(center.latitude()) > GEO_MAX_LATITUDE) {
            return List.of();
        }

        var results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(center.longitude(), center.latitude()),
                new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(SEARCH_LIMIT));

        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        var vendorIds = results.getContent().stream()
                .map(result -> result.getContent().getName())
                .toArray(String[]::new);

        List<Double> seen = redis.opsForZSet().score(SEEN_KEY, (Object[]) vendorIds);
        if (seen == null) {
            return List.of();
        }

        long cutoff = System.currentTimeMillis() - FRESHNESS_MILLIS;
        var fresh = new ArrayList<VendorMapMarker>();

        for (int i = 0; i < vendorIds.length; i++) {
            Double timestamp = seen.get(i);
            Point point = results.getContent().get(i).getContent().getPoint();

            if (timestamp != null && timestamp > cutoff && point != null) {
                fresh.add(new VendorMapMarker(vendorIds[i], point.getY(), point.getX()));
            }
        }

        return fresh;
    }

    public boolean isOnline(String vendorId) {
        Double seen = redis.opsForZSet().score(SEEN_KEY, vendorId);
        return seen != null && seen > System.currentTimeMillis() - FRESHNESS_MILLIS;
    }

    public VendorMapMarker getCurrentLocation(String vendorId) {
        if (!isOnline(vendorId)) {
            return null;
        }

        List<Point> positions = redis.opsForGeo().position(GEO_KEY, vendorId);
        Point point = positions == null
                ? null
                : positions.stream().filter(Objects::nonNull).findFirst().orElse(null);

        return point == null ? null : new VendorMapMarker(vendorId, point.getY(), point.getX());
    }

    @Scheduled(fixedDelay = 15_000)
    public void removeStaleLocations() {
        redis.execute(REMOVE_LOCATIONS, KEYS,
                Long.toString(System.currentTimeMillis() - FRESHNESS_MILLIS));
    }

    private String getCurrentVendorId() {
        Object value = httpServletRequest.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims) || !"VENDOR".equals(claims.get("userType", String.class))) {
            throw new UnauthorizedException("Vendor authentication is required");
        }

        String vendorId = claims.get("vendorId", String.class);
        if (vendorId == null || vendorId.isBlank()) {
            throw new UnauthorizedException("Vendor identity is required");
        }

        return vendorId;
    }

    private double roundMarker(double coordinate) {
        return Math.round(coordinate * 1000) / 1000.0;
    }
}