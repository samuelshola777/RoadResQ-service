package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.vendorOperation.ServiceType;

import java.math.BigDecimal;

record CapacityOption(
        String id,
        ServiceType serviceType,
        String equipmentType,
        CapacityType capacityType,
        BigDecimal capacityValue,
        CapacityUnit capacityUnit,
        Integer voltage,
        String displayName,
        boolean active
) {}