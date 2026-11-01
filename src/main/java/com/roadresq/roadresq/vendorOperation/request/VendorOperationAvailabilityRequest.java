package com.roadresq.roadresq.vendorOperation.request;

import jakarta.validation.constraints.NotNull;

public record VendorOperationAvailabilityRequest(@NotNull Boolean available) {
}