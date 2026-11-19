package com.roadresq.roadresq.rescueRequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.roadresq.roadresq.rescueRequest.order.BatteryJumpStartOrder;
import com.roadresq.roadresq.rescueRequest.order.FuelDeliveryOrder;
import com.roadresq.roadresq.rescueRequest.order.TowingOrder;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("rescue_requests")
@CompoundIndex(
        name = "rescue_request_customer_retry",
        def = "{'customerId': 1, 'idempotencyKey': 1}",
        unique = true
)
@CompoundIndex(
        name = "rescue_request_customer_history",
        def = "{'customerId': 1, 'createdAt': -1, '_id': 1}"
)
@CompoundIndex(
        name = "rescue_dispatch_due",
        def = "{'status': 1, 'dispatchDueAt': 1, 'dispatchPublishAfter': 1}"
)
@CompoundIndex(
        name = "rescue_open_pickups",
        def = "{'status': 1, 'pickupLocation.latitude': 1, 'searchExpiresAt': 1}"
)
@CompoundIndex(
        name = "rescue_payment_expiry",
        def = "{'status': 1, 'paymentDueAt': 1}"
)
@Data
public class RescueRequest {

    @Id
    private String id;
    private String customerId;
    private ServiceType serviceType;
    private RescueLocation requestLocation;
    private RescueLocation pickupLocation;
    private Instant locationCapturedAt;
    private RescueRequestStatus status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RescueJobStage jobStage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RescueLocation fuelStationLocation;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TowingOrder towingOrder;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private FuelDeliveryOrder fuelDeliveryOrder;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BatteryJumpStartOrder batteryJumpStartOrder;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RescueQuote quote;

    @JsonIgnore
    private String idempotencyKey;

    @JsonIgnore
    private String creationFingerprint;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Indexed(
            name = "rescue_active_paid_vendor",
            unique = true,
            partialFilter = "{'status': 'ASSIGNED'}"
    )
    private String assignedVendorId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant paymentDueAt;

    @JsonIgnore
    @Indexed(
            name = "rescue_verified_payment",
            unique = true,
            partialFilter = "{'verifiedPaymentReference': {'$type': 'string'}}"
    )
    private String verifiedPaymentReference;

    @JsonIgnore
    private Instant searchExpiresAt;

    @JsonIgnore
    private Instant dispatchDueAt;

    @JsonIgnore
    private Instant dispatchPublishAfter;

    @JsonIgnore
    private int dispatchRound;

    @JsonIgnore
    private int searchRadiusKm;

    @JsonIgnore
    private Instant offerExpiresAt;

    private Instant createdAt;
    private Instant updatedAt;
}