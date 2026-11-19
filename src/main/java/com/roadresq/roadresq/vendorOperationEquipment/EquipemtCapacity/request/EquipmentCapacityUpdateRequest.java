package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityGroup;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;


public record EquipmentCapacityUpdateRequest(
        ServiceType serviceType,
        @Size(min = 1, max = 100) String equipmentType,
        CapacityType capacityType,
        @Positive @Digits(integer = 10, fraction = 3) BigDecimal capacityValue,
        CapacityUnit capacityUnit,
        Integer voltage,
        @Size(min = 1, max = 180) String displayName,
        @Size(max = 5) Set<String> pictures,
        Boolean active,
        Set<@NotNull CapacityGroup> capacityGroups
) {
}