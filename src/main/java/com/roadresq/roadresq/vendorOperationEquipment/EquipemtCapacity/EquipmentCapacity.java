
package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.utils.BaseEntity;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("equipment_capacities")
public class EquipmentCapacity extends BaseEntity {

    private ServiceType serviceType;

    private String equipmentType;

    private CapacityType capacityType;

    private BigDecimal capacityValue;

    private CapacityUnit capacityUnit;

    private Integer voltage;

    private String displayName;

    private Set<String> pictures = new HashSet<>();

    private boolean active = true;
}
