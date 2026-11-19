
package com.roadresq.roadresq.vendorOperationEquipment;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorOperationEquipmentRepository
        extends MongoRepository<VendorOperationEquipment, String> {

    List<VendorOperationEquipment> findAllByVendorOperationIdInAndCapacityIdIn(
            List<String> operationIds, List<String> capacityIds
    );

    List<VendorOperationEquipment> findAllByVendorIdOrderByCreatedAtAsc(
            String vendorId
    );

    List<VendorOperationEquipment> findAllByVendorIdAndVendorOperationIdOrderByCreatedAtAsc(
            String vendorId,
            String vendorOperationId
    );

    Optional<VendorOperationEquipment> findByIdAndVendorId(
            String id,
            String vendorId
    );

    boolean existsByVendorOperationId(String vendorOperationId);
}