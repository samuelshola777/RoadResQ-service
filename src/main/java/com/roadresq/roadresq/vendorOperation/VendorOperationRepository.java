package com.roadresq.roadresq.vendorOperation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorOperationRepository extends MongoRepository<vendorOperations, String> {

    List<vendorOperations> findAllByVendorIdOrderByCreatedAtAsc(String vendorId);

    Optional<vendorOperations> findByIdAndVendorId(String id, String vendorId);

    boolean existsByVendorIdAndVendorOperationServiceType(String vendorId, ServiceType serviceType);
}