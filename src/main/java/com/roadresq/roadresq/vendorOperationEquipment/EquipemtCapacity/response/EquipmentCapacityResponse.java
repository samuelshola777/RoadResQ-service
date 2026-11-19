package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityGroup;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record EquipmentCapacityResponse(
        String id,
        ServiceType serviceType,
        String equipmentType,
        CapacityType capacityType,
        BigDecimal capacityValue,
        CapacityUnit capacityUnit,
        Integer voltage,
        String displayName,
        Set<CapacityGroup> capacityGroups,
        Set<VehicleType> vehicleTypes,
        Set<String> pictures,
        boolean active,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt
) {
    public static EquipmentCapacityResponse from(EquipmentCapacity capacity) {
        return new EquipmentCapacityResponse(
                capacity.getId(), capacity.getServiceType(), capacity.getEquipmentType(),
                capacity.getCapacityType(), capacity.getCapacityValue(), capacity.getCapacityUnit(),
                capacity.getVoltage(), capacity.getDisplayName(), capacity.getCapacityGroups(),
                capacity.getVehicleTypes(), capacity.getPictures(), capacity.isActive(),
                capacity.getCreatedAt(), capacity.getUpdatedAt()
        );
    }
}