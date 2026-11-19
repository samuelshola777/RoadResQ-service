package com.roadresq.roadresq.rescueRequest.request;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RescueRequestUpdateRequest(
        @NotNull ServiceType serviceType,
        @NotNull @Valid RescueLocation pickupLocation
) {}