package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface EquipmentCapacityRepository extends MongoRepository<EquipmentCapacity, String> {

    List<EquipmentCapacity> findAllByServiceTypeAndActiveTrueOrderByDisplayNameAsc(
            ServiceType serviceType);

    List<EquipmentCapacity> findAllByServiceTypeAndEquipmentTypeAndCapacityTypeAndCapacityValueAndCapacityUnitAndVoltage(
            ServiceType serviceType,
            String equipmentType,
            CapacityType capacityType,
            BigDecimal capacityValue,
            CapacityUnit capacityUnit,
            Integer voltage
    );
}