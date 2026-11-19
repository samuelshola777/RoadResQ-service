package com.roadresq.roadresq.vendorLocation.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VendorLocationRequest(
        @NotNull @DecimalMin("-85.05112878") @DecimalMax("85.05112878") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Positive Long capturedAt
) {}