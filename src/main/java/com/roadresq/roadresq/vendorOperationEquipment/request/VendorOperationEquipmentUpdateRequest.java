
package com.roadresq.roadresq.vendorOperationEquipment.request;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record VendorOperationEquipmentUpdateRequest(

        String capacityId,

        @Size(
                max = 5,
                message = "Maximum five equipment pictures are allowed"
        )
        Set<String> pictures

) {
}
