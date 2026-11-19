package com.roadresq.roadresq.rescueRequest;

import com.roadresq.roadresq.rescueRequest.dispatch.VendorPaymentHold;
import com.roadresq.roadresq.rescueRequest.order.BatteryJumpStartOrder;
import com.roadresq.roadresq.rescueRequest.order.FuelDeliveryOrder;
import com.roadresq.roadresq.rescueRequest.order.TowingOrder;
import com.roadresq.roadresq.rescueRequest.request.RescueQuoteDecisionRequest;
import com.roadresq.roadresq.rescueRequest.request.RescueRequestCreateRequest;
import com.roadresq.roadresq.rescueRequest.request.RescueRequestUpdateRequest;
import com.roadresq.roadresq.utils.PaginatedData;
import com.roadresq.roadresq.utils.PaginationService;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.ResourceExistsException;
import com.roadresq.roadresq.utils.exception.ResourceNotFoundException;
import com.roadresq.roadresq.utils.exception.UnauthorizedException;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacityRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RescueRequestService {

    private static final Set<RescueRequestStatus> APPROVED_QUOTE_STATUSES = Set.of(
            RescueRequestStatus.ACCEPTED,
            RescueRequestStatus.SEARCHING,
            RescueRequestStatus.AWAITING_PAYMENT,
            RescueRequestStatus.NO_VENDOR_FOUND,
            RescueRequestStatus.ASSIGNED,
            RescueRequestStatus.COMPLETED,
            RescueRequestStatus.PAYMENT_EXPIRED
    );

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final VendorPaymentHold paymentHold;
    private final RescueRequestRepository rescueRequestRepository;
    private final EquipmentCapacityRepository equipmentCapacityRepository;
    private final RescuePricingService rescuePricingService;
    private final PaginationService paginationService;
    private final HttpServletRequest httpServletRequest;

    public RescueRequestCreationResult createRescueRequest(
            String key, RescueRequestCreateRequest request
    ) {
        String customerId = getCurrentCustomerId();
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidInputException(
                    "Idempotency-Key must contain 1 to 128 letters, digits, dots, underscores or hyphens"
            );
        }

        RescueLocation pickup = Objects.requireNonNullElse(
                request.pickupLocation(), request.requestLocation()
        );
        Instant capturedAt = request.locationCapturedAt().truncatedTo(ChronoUnit.MILLIS);
        String fingerprint = getCreationFingerprint(request, pickup, capturedAt);

        var existing = rescueRequestRepository.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        EquipmentCapacity capacity = getOrderCapacity(request);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        RescueRequest rescueRequest = new RescueRequest();
        rescueRequest.setCustomerId(customerId);
        rescueRequest.setServiceType(request.serviceType());
        rescueRequest.setRequestLocation(request.requestLocation());
        rescueRequest.setPickupLocation(pickup);
        rescueRequest.setLocationCapturedAt(capturedAt);
        rescueRequest.setStatus(RescueRequestStatus.AWAITING_APPROVAL);

        switch (request.serviceType()) {
            case TOWING -> rescueRequest.setTowingOrder(
                    new TowingOrder(capacity.getId(), request.destination())
            );
            case FUEL_DELIVERY -> rescueRequest.setFuelDeliveryOrder(
                    new FuelDeliveryOrder(capacity.getId(), request.litres())
            );
            case BATTERY_JUMP_START -> rescueRequest.setBatteryJumpStartOrder(
                    new BatteryJumpStartOrder(capacity == null ? null : capacity.getId())
            );
        }

        rescueRequest.setQuote(rescuePricingService.quote());
        rescueRequest.setIdempotencyKey(key);
        rescueRequest.setCreationFingerprint(fingerprint);
        rescueRequest.setCreatedAt(now);
        rescueRequest.setUpdatedAt(now);

        try {
            return new RescueRequestCreationResult(rescueRequestRepository.insert(rescueRequest), true);
        } catch (DuplicateKeyException exception) {
            RescueRequest saved = rescueRequestRepository
                    .findByCustomerIdAndIdempotencyKey(customerId, key)
                    .orElseThrow(() -> exception);
            return replay(saved, fingerprint);
        }
    }

    public PaginatedData getRescueRequests(int page, int size) {
        Query query = Query.query(Criteria.where("customerId").is(getCurrentCustomerId()));
        return paginationService.paginate(
                query, RescueRequest.class, page, size, "createdAt", "DESC", Set.of("createdAt")
        );
    }

    public RescueRequest getRescueRequest(String requestId) {
        return findRescueRequest(requestId, getCurrentCustomerId());
    }

    public RescueRequest updateRescueRequest(String requestId, RescueRequestUpdateRequest request) {
        String customerId = getCurrentCustomerId();
        RescueLocation pickup = request.pickupLocation();

        long modified = rescueRequestRepository.updatePending(
                requestId, customerId, request.serviceType().name(),
                pickup.latitude(), pickup.longitude(), Instant.now()
        );

        RescueRequest rescueRequest = findRescueRequest(requestId, customerId);
        // An identical update within the same millisecond can modify zero fields.
        if (modified == 0 && (rescueRequest.getStatus() != RescueRequestStatus.PENDING
                || rescueRequest.getServiceType() != request.serviceType()
                || !pickup.equals(rescueRequest.getPickupLocation()))) {
            throw new ResourceExistsException(
                    "Only unquoted pending requests can be updated. Create a new request for a new quote."
            );
        }
        return rescueRequest;
    }

    public RescueRequest updateQuoteDecision(String requestId, RescueQuoteDecisionRequest request) {
        String customerId = getCurrentCustomerId();
        Instant now = Instant.now();

        if (request.accepted()) {
            rescueRequestRepository.startDispatch(requestId, customerId, now, now.plusSeconds(120));
        } else {
            rescueRequestRepository.updateQuoteDecision(
                    requestId, customerId, RescueRequestStatus.REJECTED.name(), now
            );
        }

        RescueRequest rescueRequest = findRescueRequest(requestId, customerId);
        boolean decisionRecorded = request.accepted()
                ? APPROVED_QUOTE_STATUSES.contains(rescueRequest.getStatus())
                : rescueRequest.getStatus() == RescueRequestStatus.REJECTED;

        if (!decisionRecorded) {
            throw new ResourceExistsException("Only a quote awaiting approval can be accepted or rejected");
        }
        return rescueRequest;
    }

    public RescueRequest cancelRescueRequest(String requestId) {
        String customerId = getCurrentCustomerId();
        rescueRequestRepository.cancelPending(requestId, customerId, Instant.now());

        RescueRequest rescueRequest = findRescueRequest(requestId, customerId);
        if (rescueRequest.getStatus() != RescueRequestStatus.CANCELLED) {
            throw new ResourceExistsException(
                    "Only pending, searching or unpaid rescue requests can be cancelled"
            );
        }

        paymentHold.release(requestId);
        return rescueRequest;
    }

    private RescueRequest findRescueRequest(String requestId, String customerId) {
        return rescueRequestRepository.findByIdAndCustomerId(requestId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Rescue request not found"));
    }

    private String getCurrentCustomerId() {
        Object value = httpServletRequest.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims) || !"CUSTOMER".equals(claims.get("userType", String.class))) {
            throw new UnauthorizedException("Customer authentication is required");
        }

        String customerId = claims.get("customerId", String.class);
        if (customerId == null || customerId.isBlank()) {
            throw new UnauthorizedException("A valid customer account is required");
        }
        return customerId;
    }

    private RescueRequestCreationResult replay(RescueRequest request, String fingerprint) {
        if (!fingerprint.equals(request.getCreationFingerprint())) {
            throw new ResourceExistsException("Idempotency-Key has already been used for a different request");
        }
        return new RescueRequestCreationResult(request, false);
    }

    private EquipmentCapacity getOrderCapacity(RescueRequestCreateRequest input) {
        if ((input.serviceType() == ServiceType.TOWING) != (input.destination() != null)) {
            throw new InvalidInputException("A destination is required for towing only");
        }

        if (input.capacityId() == null || input.capacityId().isBlank()) {
            if (input.serviceType() == ServiceType.BATTERY_JUMP_START && input.litres() == null) {
                return null;
            }

            if (input.serviceType() == ServiceType.FUEL_DELIVERY
                    && input.litres() != null && input.litres().signum() > 0) {
                return equipmentCapacityRepository
                        .findAllByServiceTypeAndActiveTrueOrderByDisplayNameAsc(input.serviceType())
                        .stream()
                        .filter(item -> item.getCapacityType() == CapacityType.FUEL_VOLUME
                                && item.getCapacityUnit() == CapacityUnit.LITRES
                                && item.getCapacityValue() != null
                                && item.getCapacityValue().compareTo(input.litres()) >= 0)
                        .min(Comparator.comparing(EquipmentCapacity::getCapacityValue))
                        .orElseThrow(() -> new InvalidInputException(
                                "No active container fits the requested litres"
                        ));
            }

            throw new InvalidInputException(switch (input.serviceType()) {
                case FUEL_DELIVERY -> "Enter a positive number of litres";
                case BATTERY_JUMP_START -> "Litres apply to fuel delivery only";
                case TOWING -> "Equipment capacity is required";
            });
        }

        EquipmentCapacity capacity = equipmentCapacityRepository.findById(input.capacityId().trim())
                .filter(item -> item.isActive() && item.getServiceType() == input.serviceType())
                .orElseThrow(() -> new InvalidInputException("Select an active capacity for this service"));

        if (input.serviceType() == ServiceType.FUEL_DELIVERY) {
            if (input.litres() == null || input.litres().signum() <= 0
                    || capacity.getCapacityValue() == null
                    || input.litres().compareTo(capacity.getCapacityValue()) > 0) {
                throw new InvalidInputException("Fuel litres must be positive and fit the selected container");
            }
        } else if (input.litres() != null) {
            throw new InvalidInputException("Litres apply to fuel delivery only");
        }
        return capacity;
    }

    private String getCreationFingerprint(
            RescueRequestCreateRequest input, RescueLocation pickup, Instant capturedAt
    ) {
        RescueLocation origin = input.requestLocation();
        String payload = String.join(
                "|",
                input.serviceType().name(),
                origin.latitude().toString(),
                origin.longitude().toString(),
                pickup.latitude().toString(),
                pickup.longitude().toString(),
                capturedAt.toString()
        );

        // Preserve fingerprints for retries of requests created before quoting was added.
        if (input.capacityId() != null || input.destination() != null || input.litres() != null) {
            String capacityId = Objects.toString(input.capacityId(), "").trim();
            RescueLocation destination = input.destination();
            String destinationKey = destination == null ? "" : destination.latitude() + "," + destination.longitude();
            String litresKey = input.litres() == null ? "" : input.litres().stripTrailingZeros().toPlainString();
            payload += "|" + capacityId.length() + ":" + capacityId + "|" + destinationKey + "|" + litresKey;
        }

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}