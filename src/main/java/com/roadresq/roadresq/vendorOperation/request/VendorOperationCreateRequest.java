package com.roadresq.roadresq.vendorOperation.request;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record VendorOperationCreateRequest(
        @NotNull(message = "Service type is required")
        ServiceType serviceType,


        @Size(max = 20, message = "Register at most 20 equipment items per request")
        List<EquipmentInput> equipment
) {
        public record EquipmentInput(
                @NotBlank(message = "Equipment capacity ID is required")
                String capacityId,


                Set<String> pictures
        ) {
        }
}