package com.roadresq.roadresq.vendorOperation.response;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperation.vendorOperations;

import java.time.LocalDateTime;

public record VendorOperationServiceResponse(
        String serviceId,
        String vendorId,
        String userId,
        ServiceType serviceType,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VendorOperationServiceResponse from(vendorOperations operation) {
        return new VendorOperationServiceResponse(
                operation.getId(),
                operation.getVendorId(),
                operation.getUserId(),
                operation.getVendorOperationServiceType(),
                operation.isActive(),
                operation.getCreatedAt(),
                operation.getUpdatedAt()
        );
    }
}