package com.roadresq.roadresq.rescueRequest.dispatch.response;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.rescueRequest.RescueRequest;
import com.roadresq.roadresq.vendorOperation.ServiceType;

import java.time.Instant;

public record RescueDispatchOffer(
        String requestId, ServiceType serviceType, String capacityId,
        RescueLocation pickupArea, Instant expiresAt
) {
    public static RescueDispatchOffer from(RescueRequest request, String capacityId, boolean accepted) {
        RescueLocation pickup = request.getPickupLocation();
        if (!accepted) {
            pickup = new RescueLocation(round(pickup.latitude()), round(pickup.longitude()));
        }
        return new RescueDispatchOffer(request.getId(), request.getServiceType(), capacityId, pickup,
                accepted ? request.getPaymentDueAt() : request.getOfferExpiresAt());
    }

    private static double round(double coordinate) {
        return Math.round(coordinate * 1000) / 1000.0;
    }
}