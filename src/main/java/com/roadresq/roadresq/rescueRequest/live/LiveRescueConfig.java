package com.roadresq.roadresq.rescueRequest.live;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.rescueRequest.RescueRequestRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.Instant;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class LiveRescueConfig implements WebSocketConfigurer, HandshakeInterceptor {

    private static final String VENDOR_LIVE = "/api/v1/vendor/private/live";
    private static final String CUSTOMER_LIVE = "/api/v1/customer/private/live";

    @Value("${app.live-websocket-origins:*}")
    private String[] allowedOrigins;

    private final LiveRescueHandler liveRescueHandler;
    private final RescueRequestRepository rescueRequestRepository;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveRescueHandler, VENDOR_LIVE, CUSTOMER_LIVE).addInterceptors(this)
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)
                || !(servlet.getServletRequest().getAttribute("validatedClaims") instanceof Claims claims)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        boolean vendor = VENDOR_LIVE.equals(servlet.getServletRequest().getRequestURI());
        String accountId = claims.get(vendor ? "vendorId" : "customerId", String.class);
        String role = claims.get("userType", String.class);
        if (!(vendor ? "VENDOR" : "CUSTOMER").equals(role) || accountId == null || accountId.isBlank()
                || claims.getExpiration() == null) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        Instant expiresAt = claims.getExpiration().toInstant();
        Instant reconnectAt = Instant.now().plusSeconds(300);
        attributes.put("accountId", accountId);
        attributes.put("vendor", vendor);
        attributes.put("expiresAt", expiresAt.isBefore(reconnectAt) ? expiresAt : reconnectAt);
        if (vendor) {
            return true;
        }

        String requestId = servlet.getServletRequest().getParameter("requestId");
        if (requestId != null) {
            if (rescueRequestRepository.findByIdAndCustomerId(requestId, accountId).isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return false;
            }
            attributes.put("requestId", requestId);
            return true;
        }

        try {
            double latitude = Double.parseDouble(servlet.getServletRequest().getParameter("latitude"));
            double longitude = Double.parseDouble(servlet.getServletRequest().getParameter("longitude"));
            if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                    || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }
            // Nearby map sessions in the same area share a bounded search.
            attributes.put("center", new RescueLocation(Math.round(latitude * 100) / 100.0,
                    Math.round(longitude * 100) / 100.0));
            return true;
        } catch (NullPointerException | NumberFormatException exception) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}