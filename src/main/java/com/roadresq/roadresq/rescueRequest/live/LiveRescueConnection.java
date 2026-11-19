package com.roadresq.roadresq.rescueRequest.live;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;

@Getter
@RequiredArgsConstructor
class LiveRescueConnection {
    private final WebSocketSession session;
    private final String accountId;
    private final boolean vendor;
    private final RescueLocation center;
    private final String requestId;
    private final Instant expiresAt;

    @Setter
    private Map<String, VendorMapMarker> markers;
    @Setter
    private String lastPayload;
}