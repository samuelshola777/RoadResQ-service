package com.roadresq.roadresq.vendorOperation;

import com.roadresq.roadresq.utils.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("vendor_operations")
public class vendorOperations extends BaseEntity {
    private String vendorId;
    private String userId;
    private boolean active;
    private ServiceType vendorOperationServiceType;


}