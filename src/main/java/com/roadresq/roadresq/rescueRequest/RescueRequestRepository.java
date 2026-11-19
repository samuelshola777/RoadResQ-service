package com.roadresq.roadresq.rescueRequest;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RescueRequestRepository extends MongoRepository<RescueRequest, String> {

    Optional<RescueRequest> findByAssignedVendorIdAndStatus(
            String vendorId, RescueRequestStatus status
    );

    Optional<RescueRequest> findByIdAndAssignedVendorId(String id, String vendorId);

    @Query("{ '_id': ?0, 'assignedVendorId': ?1, 'status': 'ASSIGNED', 'jobStage': ?2 }")
    @Update("""
            { '$set': { 'jobStage': ?3, 'status': ?4, 'fuelStationLocation': ?5, 'updatedAt': ?6 } }
            """)
    long updateJobStage(
            String requestId, String vendorId, RescueJobStage previous, RescueJobStage next,
            RescueRequestStatus status, RescueLocation fuelStationLocation, Instant now
    );

    String PENDING_REQUEST = "{ '_id': ?0, 'customerId': ?1, 'status': 'PENDING' }";

    Optional<RescueRequest> findByIdAndCustomerId(String id, String customerId);

    Optional<RescueRequest> findByCustomerIdAndIdempotencyKey(
            String customerId, String idempotencyKey
    );

    @Query(PENDING_REQUEST)
    @Update("""
            { '$set': {
                'serviceType': ?2,
                'pickupLocation': { 'latitude': ?3, 'longitude': ?4 },
                'updatedAt': ?5
            } }
            """)
    long updatePending(
            String requestId, String customerId, String serviceType,
            double latitude, double longitude, Instant updatedAt
    );

    @Query("""
            { '_id': ?0, 'customerId': ?1,
              'status': { '$in': ['PENDING', 'AWAITING_APPROVAL', 'SEARCHING', 'AWAITING_PAYMENT'] } }
            """)
    @Update("""
            { '$set': { 'status': 'CANCELLED', 'updatedAt': ?2 },
              '$unset': { 'paymentDueAt': '' } }
            """)
    void cancelPending(String requestId, String customerId, Instant updatedAt);

    @Query("{ '_id': ?0, 'customerId': ?1, 'status': 'AWAITING_APPROVAL' }")
    @Update("{ '$set': { 'status': ?2, 'updatedAt': ?3 } }")
    void updateQuoteDecision(String requestId, String customerId, String status, Instant updatedAt);

    @Query("{ '_id': ?0, 'customerId': ?1, 'status': 'AWAITING_APPROVAL' }")
    @Update("""
            { '$set': { 'status': 'SEARCHING', 'updatedAt': ?2, 'dispatchDueAt': ?2,
                'dispatchPublishAfter': ?2, 'searchExpiresAt': ?3, 'dispatchRound': 0 } }
            """)
    void startDispatch(String requestId, String customerId, Instant now, Instant searchExpiresAt);

    List<RescueRequest> findTop50ByStatusAndDispatchDueAtLessThanEqualAndDispatchPublishAfterLessThanEqualOrderByDispatchDueAtAsc(
            RescueRequestStatus status, Instant dueAt, Instant publishAfter
    );

    @Query("""
            { '_id': ?0, 'status': 'SEARCHING', 'dispatchRound': ?1,
              'dispatchDueAt': { '$lte': ?2 }, 'dispatchPublishAfter': { '$lte': ?2 } }
            """)
    @Update("{ '$set': { 'dispatchPublishAfter': ?3 } }")
    long claimDispatchPublication(String requestId, int round, Instant now, Instant retryAt);

    @Query("{ '_id': ?0, 'status': 'SEARCHING', 'dispatchRound': ?1, 'searchExpiresAt': { '$gt': ?2 } }")
    @Update("""
            { '$set': { 'searchRadiusKm': ?3, 'offerExpiresAt': ?4, 'dispatchDueAt': ?4,
                        'dispatchPublishAfter': ?4, 'updatedAt': ?2 }, '$inc': { 'dispatchRound': 1 } }
            """)
    long openSearchArea(String id, int round, Instant now, int radius, Instant nextAttempt);

    @Query("{ '_id': ?0, 'status': 'SEARCHING', 'searchExpiresAt': { '$lte': ?1 } }")
    @Update("{ '$set': { 'status': 'NO_VENDOR_FOUND', 'updatedAt': ?1 } }")
    long expireSearch(String id, Instant now);

    @Query("""
            { 'status': 'SEARCHING', 'offerExpiresAt': { '$gt': ?0 },
              'pickupLocation.latitude': { '$gte': ?1, '$lte': ?2 } }
            """)
    List<RescueRequest> findOpenRequests(Instant now, double south, double north);

    List<RescueRequest> findByStatusAndAssignedVendorIdIn(
            RescueRequestStatus status, List<String> vendorIds
    );

    @Query("{ '_id': ?0, 'status': 'SEARCHING', 'offerExpiresAt': { '$gt': ?1 }, 'searchExpiresAt': { '$gt': ?1 } }")
    @Update("{ '$set': { 'status': 'AWAITING_PAYMENT', 'updatedAt': ?1, 'paymentDueAt': ?2 } }")
    long awaitPayment(String requestId, Instant now, Instant deadline);

    @Query("{ 'status': 'AWAITING_PAYMENT', 'paymentDueAt': { '$lte': ?0 } }")
    @Update("{ '$set': { 'status': 'PAYMENT_EXPIRED', 'updatedAt': ?0 } }")
    void expireVendorReservations(Instant now);
}