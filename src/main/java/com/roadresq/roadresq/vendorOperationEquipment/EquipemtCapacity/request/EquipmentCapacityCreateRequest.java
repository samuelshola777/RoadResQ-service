package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record EquipmentCapacityCreateRequest(
        @NotNull ServiceType serviceType,
        @NotBlank @Size(max = 100) String equipmentType,
        @NotNull CapacityType capacityType,
        @NotNull @Positive @Digits(integer = 10, fraction = 3) BigDecimal capacityValue,
        @NotNull CapacityUnit capacityUnit,
        Integer voltage,
        @NotBlank @Size(max = 180) String displayName,
        @Size(max = 5) Set<@NotBlank String> pictures,
        Boolean active
) {
}