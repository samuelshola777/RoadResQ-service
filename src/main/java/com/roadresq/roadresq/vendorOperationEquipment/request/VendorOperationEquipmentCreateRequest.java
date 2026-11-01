
package com.roadresq.roadresq.vendorOperationEquipment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record VendorOperationEquipmentCreateRequest(

        @NotBlank(message = "Vendor operation ID is required")
        String vendorOperationId,

        @NotBlank(message = "Equipment capacity ID is required")
        String capacityId,

        @Size(
                max = 5,
                message = "Maximum five equipment pictures are allowed"
        )
        Set<@NotBlank String> pictures

) {
}
