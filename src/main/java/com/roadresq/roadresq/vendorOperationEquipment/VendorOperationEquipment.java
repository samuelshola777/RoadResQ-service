package com.roadresq.roadresq.vendorOperationEquipment;

import com.roadresq.roadresq.utils.BaseEntity;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("vendor_operations_equipment")
@CompoundIndex(
        name = "vendor_equipment_dispatch_capacity",
        def = "{'vendorOperationId': 1, 'capacityId': 1}"
)
public class VendorOperationEquipment extends BaseEntity {

    private String vendorId;
    private String vendorOperationId;
    private ServiceType serviceType;
    private Set<String> pictures;
    private String capacityId;
}