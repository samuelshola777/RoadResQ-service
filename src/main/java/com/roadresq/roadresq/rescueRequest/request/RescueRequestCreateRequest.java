package com.roadresq.roadresq.rescueRequest.request;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record RescueRequestCreateRequest(
        @NotNull ServiceType serviceType,
        @NotNull @Valid RescueLocation requestLocation,
        @Valid RescueLocation pickupLocation,
        @NotNull Instant locationCapturedAt,
        @Size(max = 256) String capacityId,
        @Valid RescueLocation destination,
        @Positive @Digits(integer = 10, fraction = 3) BigDecimal litres
) {}