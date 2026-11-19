package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Data
@Document("equipment_capacities")
public class EquipmentCapacity {

    @Id
    private String id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt = createdAt;

    private ServiceType serviceType;
    private String equipmentType;
    private CapacityType capacityType;
    private BigDecimal capacityValue;
    private CapacityUnit capacityUnit;
    private Integer voltage;
    private String displayName;
    private Set<CapacityGroup> capacityGroups = new HashSet<>();
    private Set<VehicleType> vehicleTypes = new HashSet<>();
    private Set<String> pictures = new HashSet<>();
    private boolean active = true;
}