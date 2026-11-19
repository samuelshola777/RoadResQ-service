package com.roadresq.roadresq.rescueRequest.live;

import com.roadresq.roadresq.rescueRequest.RescueJobStage;
import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.rescueRequest.RescueRequestRepository;
import com.roadresq.roadresq.rescueRequest.RescueRequestStatus;
import com.roadresq.roadresq.rescueRequest.dispatch.RescueDispatchService;
import com.roadresq.roadresq.vendorLocation.VendorLocationService;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LiveRescueHandler implements WebSocketHandler {

    private final RescueRequestRepository rescueRequestRepository;
    private final RescueDispatchService rescueDispatchService;
    private final VendorLocationService vendorLocationService;
    private final ObjectMapper objectMapper;
    private final RescueEtaService rescueEtaService;

    private final Map<String, LiveRescueConnection> connections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(1024);
        Map<String, Object> attributes = session.getAttributes();

        connections.put(session.getId(), new LiveRescueConnection(
                new ConcurrentWebSocketSessionDecorator(session, 3_000, 64 * 1024),
                (String) attributes.get("accountId"),
                (boolean) attributes.get("vendor"),
                (RescueLocation) attributes.get("center"),
                (String) attributes.get("requestId"),
                (Instant) attributes.get("expiresAt")
        ));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
        if (!(message instanceof PongMessage)) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        connections.remove(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Scheduled(fixedDelay = 2_000)
    public void sendOffers() {
        var vendors = activeConnections(true).stream()
                .collect(Collectors.groupingBy(LiveRescueConnection::getAccountId));

        vendors.forEach((vendorId, sessions) -> {
            var payload = Map.of(
                    "type", "rescue-offers",
                    "offers", rescueDispatchService.getOffers(vendorId)
            );
            sessions.forEach(connection -> sendIfChanged(connection, payload));
        });
    }

    @Scheduled(fixedRate = 5_000)
    public void sendJobTracking() {
        for (LiveRescueConnection connection : activeConnections(false)) {
            if (connection.getRequestId() != null) {
                sendTracking(connection);
            }
        }
    }

    @Scheduled(fixedDelay = 3_000)
    public void sendCustomerUpdates() {
        var customers = activeConnections(false);
        var areas = new HashMap<RescueLocation, Map<String, VendorMapMarker>>();

        for (LiveRescueConnection customer : customers) {
            if (customer.getRequestId() == null) {
                var markers = areas.computeIfAbsent(customer.getCenter(), center ->
                        vendorLocationService.getNearbyMarkers(center).stream()
                                .collect(Collectors.toMap(VendorMapMarker::vendorId, marker -> marker)));
                sendMarkers(customer, markers);
            }
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void keepConnectionsAlive() {
        for (LiveRescueConnection connection : connections.values()) {
            try {
                connection.getSession().sendMessage(new PingMessage());
            } catch (IOException | RuntimeException exception) {
                close(connection, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private List<LiveRescueConnection> activeConnections(boolean vendor) {
        Instant now = Instant.now();
        return connections.values().stream()
                .filter(connection -> connection.isVendor() == vendor)
                .filter(connection -> {
                    if (!connection.getSession().isOpen() || !connection.getExpiresAt().isAfter(now)) {
                        close(connection, CloseStatus.POLICY_VIOLATION);
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private void sendTracking(LiveRescueConnection connection) {
        var request = rescueRequestRepository
                .findByIdAndCustomerId(connection.getRequestId(), connection.getAccountId())
                .orElse(null);

        if (request == null) {
            close(connection, CloseStatus.POLICY_VIOLATION);
            return;
        }

        var payload = new HashMap<String, Object>();
        payload.put("type", "rescue-tracking");
        payload.put("requestId", request.getId());
        payload.put("status", request.getStatus());
        payload.put("jobStage", request.getJobStage());
        payload.put("etaMinutes", null);
        payload.put("vendorLocation", null);

        if (request.getStatus() == RescueRequestStatus.AWAITING_PAYMENT
                && request.getPaymentDueAt().isAfter(Instant.now())) {
            payload.put("paymentDueAt", request.getPaymentDueAt());
        } else if (request.getStatus() == RescueRequestStatus.ASSIGNED) {
            RescueJobStage stage = request.getJobStage();

            if (stage == null || stage == RescueJobStage.COLLECTING_FUEL
                    || stage == RescueJobStage.EN_ROUTE_TO_CUSTOMER
                    || stage == RescueJobStage.TOWING_TO_DESTINATION) {
                var marker = vendorLocationService.getCurrentLocation(request.getAssignedVendorId());
                payload.put("vendorLocation", marker);

                if (stage != null) {
                    RescueLocation destination = switch (stage) {
                        case COLLECTING_FUEL -> request.getFuelStationLocation();
                        case EN_ROUTE_TO_CUSTOMER -> request.getPickupLocation();
                        case TOWING_TO_DESTINATION -> request.getTowingOrder().destination();
                        default -> null;
                    };
                    payload.put("etaMinutes", rescueEtaService.getEtaMinutes(request, marker, destination));
                }
            }
        }

        sendIfChanged(connection, payload);
    }

    private void sendMarkers(
            LiveRescueConnection connection, Map<String, VendorMapMarker> current
    ) {
        var previous = connection.getMarkers();

        if (previous == null) {
            send(connection, objectMapper.writeValueAsString(Map.of(
                    "type", "nearby-vendors",
                    "upsert", current.values(),
                    "removed", List.of()
            )));
        } else if (!previous.equals(current)) {
            var upsert = current.values().stream()
                    .filter(marker -> !marker.equals(previous.get(marker.vendorId())))
                    .toList();

            var removed = previous.keySet().stream()
                    .filter(id -> !current.containsKey(id))
                    .toList();

            send(connection, objectMapper.writeValueAsString(Map.of(
                    "type", "nearby-vendors",
                    "upsert", upsert,
                    "removed", removed
            )));
        }

        connection.setMarkers(current);
    }

    private void sendIfChanged(LiveRescueConnection connection, Object value) {
        String text = objectMapper.writeValueAsString(value);
        if (!Objects.equals(text, connection.getLastPayload())) {
            send(connection, text);
            connection.setLastPayload(text);
        }
    }

    private void send(LiveRescueConnection connection, String payload) {
        try {
            connection.getSession().sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException exception) {
            close(connection, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(LiveRescueConnection connection, CloseStatus status) {
        connections.remove(connection.getSession().getId(), connection);
        try {
            connection.getSession().close(status);
        } catch (IOException exception) {
            log.debug("Live rescue socket closed: {}", exception.getClass().getSimpleName());
        }
    }
}