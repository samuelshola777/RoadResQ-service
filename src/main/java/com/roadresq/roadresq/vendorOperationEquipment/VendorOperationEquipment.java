package com.roadresq.roadresq.vendorOperationEquipment;

import com.roadresq.roadresq.utils.BaseEntity;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("vendor_operations_equipment")
public class VendorOperationEquipment extends BaseEntity {

    private String vendorId;
    private String vendorOperationId;
    private ServiceType serviceType;
    private Set<String> pictures;
    private String capacityId;
}
