
package com.roadresq.roadresq.vendorOperation.request;

import jakarta.validation.constraints.NotNull;

public record VendorOperationUpdateRequest(

        @NotNull
        Boolean active

) {
}
