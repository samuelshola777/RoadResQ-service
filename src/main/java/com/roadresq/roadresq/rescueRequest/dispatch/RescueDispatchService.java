package com.roadresq.roadresq.rescueRequest.dispatch;

import com.roadresq.roadresq.rescueRequest.RescueJobStage;
import com.roadresq.roadresq.rescueRequest.RescueRequest;
import com.roadresq.roadresq.rescueRequest.RescueRequestRepository;
import com.roadresq.roadresq.rescueRequest.RescueRequestStatus;
import com.roadresq.roadresq.rescueRequest.dispatch.response.RescueDispatchOffer;
import com.roadresq.roadresq.rescueRequest.request.RescueJobStageRequest;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.ResourceExistsException;
import com.roadresq.roadresq.utils.exception.ResourceNotFoundException;
import com.roadresq.roadresq.utils.exception.UnauthorizedException;
import com.roadresq.roadresq.vendorLocation.VendorLocationService;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import com.roadresq.roadresq.vendorOperation.VendorOperationRepository;
import com.roadresq.roadresq.vendorOperation.vendorOperations;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacityRepository;
import com.roadresq.roadresq.vendorOperationEquipment.VendorOperationEquipment;
import com.roadresq.roadresq.vendorOperationEquipment.VendorOperationEquipmentRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.roadresq.roadresq.notification.NotificationPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RescueDispatchService {

    private static final int OFFER_BATCH_SIZE = 5;
    private static final int MAX_RADIUS_KM = 12;
    private static final int OFFER_SECONDS = 15;
    private static final int PAYMENT_SECONDS = 300;

    private final RescueRequestRepository rescueRequestRepository;
    private final VendorPaymentHold paymentHold;
    private final VendorLocationService vendorLocationService;
    private final VendorOperationRepository vendorOperationRepository;
    private final VendorOperationEquipmentRepository equipmentRepository;
    private final EquipmentCapacityRepository capacityRepository;
    private final HttpServletRequest httpServletRequest;

    private final NotificationPublisher notifications;

    @Transactional
    public void dispatch(String requestId, int round) {
        RescueRequest request = rescueRequestRepository.findById(requestId).orElse(null);
        Instant now = Instant.now();

        if (request == null || request.getStatus() != RescueRequestStatus.SEARCHING
                || request.getDispatchRound() != round || request.getDispatchDueAt().isAfter(now)) {
            return;
        }
        if (!request.getSearchExpiresAt().isAfter(now)) {
            rescueRequestRepository.expireSearch(requestId, now);
            return;
        }

        int radius = switch (round) {
            case 0 -> 3;
            case 1 -> 6;
            default -> 12;
        };

        Instant nextAttempt = now.plusSeconds(OFFER_SECONDS);
        if (nextAttempt.isAfter(request.getSearchExpiresAt())) {
            nextAttempt = request.getSearchExpiresAt();
        }
        if (!request.getSearchExpiresAt().isAfter(now)) {
            rescueRequestRepository.expireSearch(requestId, now);
            return;
        }
        if (rescueRequestRepository.openSearchArea(requestId, round, now, radius, nextAttempt) == 0) return;
        List<String> nearby = vendorLocationService.findNearby(request.getPickupLocation(), radius).stream()
                .map(VendorMapMarker::vendorId).toList();
        Set<String> eligible = findEligibleVendors(request, nearby);
        int sent = 0;
        for (String vendorId : nearby) {
            if (!eligible.contains(vendorId) || paymentHold.requestFor(vendorId) != null) continue;
            boolean created = notifications.record("offer:" + requestId, "RescueOfferAvailable", requestId,
                    "VENDOR", "profileId", vendorId, request.getSearchExpiresAt());
            if (created && ++sent == OFFER_BATCH_SIZE) break;
        }
    }

    public List<RescueDispatchOffer> getRescueOffers() {
        return getOffers(getCurrentVendorId());
    }

    public List<RescueDispatchOffer> getOffers(String vendorId) {
        VendorMapMarker point = vendorLocationService.getCurrentLocation(vendorId);
        if (point == null || paymentHold.requestFor(vendorId) != null) {
            return List.of();
        }

        // One latitude degree spans more than 110 km throughout the supported search area.
        double margin = MAX_RADIUS_KM / 110.0;
        return rescueRequestRepository
                .findOpenRequests(Instant.now(), point.latitude() - margin, point.latitude() + margin)
                .stream()
                .filter(request -> canReach(request, point))
                .filter(request -> findEligibleVendors(request, List.of(vendorId)).contains(vendorId))
                .sorted(java.util.Comparator.comparing(RescueRequest::getCreatedAt))
                .map(request -> RescueDispatchOffer.from(request, getCapacityId(request), false))
                .toList();
    }

    boolean canReach(RescueRequest request, VendorMapMarker point) {
        if (point == null) return false;

        var pickup = request.getPickupLocation();
        double lat = Math.toRadians(pickup.latitude() - point.latitude());
        double lon = Math.toRadians(pickup.longitude() - point.longitude());
        double a = Math.pow(Math.sin(lat / 2), 2)
                + Math.cos(Math.toRadians(point.latitude()))
                * Math.cos(Math.toRadians(pickup.latitude()))
                * Math.pow(Math.sin(lon / 2), 2);

        return 12742 * Math.asin(Math.sqrt(Math.min(1, a))) <= request.getSearchRadiusKm();
    }

    @Transactional
    public RescueDispatchOffer acceptRescueOffer(String requestId) {
        return acceptOffer(requestId, getCurrentVendorId());
    }

    @Transactional
    public RescueDispatchOffer acceptOffer(String requestId, String vendorId) {
        RescueRequest request = findRequest(requestId);

        if (request.getStatus() == RescueRequestStatus.AWAITING_PAYMENT
                && vendorId.equals(paymentHold.vendorFor(requestId))) {
            return RescueDispatchOffer.from(request, getCapacityId(request), true);
        }

        if (request.getStatus() != RescueRequestStatus.SEARCHING
                || request.getOfferExpiresAt() == null
                || !request.getOfferExpiresAt().isAfter(Instant.now())
                || !canReach(request, vendorLocationService.getCurrentLocation(vendorId))
                || !findEligibleVendors(request, List.of(vendorId)).contains(vendorId)) {
            throw new ResourceNotFoundException("Active rescue offer not found");
        }

        Instant now = Instant.now();
        Instant deadline = paymentHold.claim(requestId, vendorId, now.plusSeconds(PAYMENT_SECONDS));
        if (deadline == null) {
            throw new ResourceExistsException("Rescue request or vendor is already reserved");
        }

        rescueRequestRepository.awaitPayment(requestId, now, deadline);
        RescueRequest saved = findRequest(requestId);
        if (saved.getStatus() != RescueRequestStatus.AWAITING_PAYMENT
                || !vendorId.equals(paymentHold.vendorFor(requestId))) {
            if (saved.getStatus() != RescueRequestStatus.AWAITING_PAYMENT) {
                paymentHold.release(requestId);
            }
            throw new ResourceExistsException("Rescue request is no longer open");
        }
        notifications.record("accepted:" + requestId, "VendorAccepted", requestId, "CUSTOMER",
                "profileId", saved.getCustomerId(),
                saved.getPaymentDueAt());
        return RescueDispatchOffer.from(saved, getCapacityId(saved), true);
    }

    public RescueRequest getCurrentJob() {
        return rescueRequestRepository
                .findByAssignedVendorIdAndStatus(getCurrentVendorId(), RescueRequestStatus.ASSIGNED)
                .orElseThrow(() -> new ResourceNotFoundException("No assigned rescue request"));
    }

    public RescueRequest updateJobStage(String requestId, RescueJobStageRequest input) {
        String vendorId = getCurrentVendorId();
        RescueRequest request = findVendorJob(requestId, vendorId);
        RescueJobStage stage = input.stage();

        if (input.fuelStationLocation() != null && stage != RescueJobStage.COLLECTING_FUEL) {
            throw new InvalidInputException("Supply the fuel station when starting fuel collection");
        }

        if (stage == request.getJobStage()) {
            if (input.fuelStationLocation() != null
                    && !input.fuelStationLocation().equals(request.getFuelStationLocation())) {
                throw new ResourceExistsException("Fuel collection already started at another location");
            }
            return request;
        }

        if (request.getStatus() != RescueRequestStatus.ASSIGNED
                || RescueJobStage.next(request.getServiceType(), request.getJobStage()) != stage) {
            throw new ResourceExistsException("This stage does not follow the current rescue job stage");
        }

        RescueRequestStatus status = stage == RescueJobStage.COMPLETED
                ? RescueRequestStatus.COMPLETED : RescueRequestStatus.ASSIGNED;
        var station = stage == RescueJobStage.COLLECTING_FUEL
                ? input.fuelStationLocation() : request.getFuelStationLocation();

        // Compare the previous stage in MongoDB so concurrent updates cannot skip a step.
        rescueRequestRepository.updateJobStage(
                requestId, vendorId, request.getJobStage(), stage, status, station, Instant.now()
        );

        RescueRequest saved = findVendorJob(requestId, vendorId);
        if (saved.getJobStage() != stage || !Objects.equals(station, saved.getFuelStationLocation())) {
            throw new ResourceExistsException("The rescue job stage has changed");
        }
        return saved;
    }

    private RescueRequest findVendorJob(String requestId, String vendorId) {
        return rescueRequestRepository.findByIdAndAssignedVendorId(requestId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Assigned rescue request not found"));
    }

    private RescueRequest findRequest(String requestId) {
        return rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Rescue offer not found"));
    }

    private Set<String> findEligibleVendors(RescueRequest request, List<String> vendorIds) {
        if (vendorIds.isEmpty()) {
            return Set.of();
        }

        var reserved = rescueRequestRepository
                .findByStatusAndAssignedVendorIdIn(RescueRequestStatus.ASSIGNED, vendorIds)
                .stream()
                .map(RescueRequest::getAssignedVendorId)
                .collect(Collectors.toSet());

        List<String> available = vendorIds.stream().filter(id -> !reserved.contains(id)).toList();
        if (available.isEmpty()) {
            return Set.of();
        }

        List<vendorOperations> operations = vendorOperationRepository
                .findAllByVendorIdInAndVendorOperationServiceTypeAndActiveTrue(available, request.getServiceType());
        if (operations.isEmpty()) {
            return Set.of();
        }

        List<String> allowedCapacities = getAllowedCapacities(request);
        if (allowedCapacities.isEmpty()) {
            return Set.of();
        }

        var operationVendors = operations.stream()
                .collect(Collectors.toMap(vendorOperations::getId, vendorOperations::getVendorId));

        return equipmentRepository.findAllByVendorOperationIdInAndCapacityIdIn(
                        List.copyOf(operationVendors.keySet()), allowedCapacities)
                .stream()
                .filter(equipment -> request.getServiceType() == equipment.getServiceType()
                        && Objects.equals(
                        equipment.getVendorId(),
                        operationVendors.get(equipment.getVendorOperationId())
                ))
                .map(VendorOperationEquipment::getVendorId)
                .collect(Collectors.toSet());
    }

    private List<String> getAllowedCapacities(RescueRequest request) {
        String capacityId = getCapacityId(request);

        if (capacityId == null && request.getBatteryJumpStartOrder() != null) {
            return capacityRepository.findAllByServiceTypeAndActiveTrueOrderByDisplayNameAsc(request.getServiceType())
                    .stream()
                    .map(EquipmentCapacity::getId)
                    .toList();
        }

        EquipmentCapacity selected = capacityRepository.findById(capacityId)
                .filter(capacity -> capacity.isActive() && capacity.getServiceType() == request.getServiceType())
                .orElse(null);
        if (selected == null) {
            return List.of();
        }

        return capacityRepository.findAllByServiceTypeAndActiveTrueOrderByDisplayNameAsc(request.getServiceType())
                .stream()
                .filter(capacity -> matchesCapacity(request, selected, capacity))
                .map(EquipmentCapacity::getId)
                .toList();
    }

    private boolean matchesCapacity(
            RescueRequest request, EquipmentCapacity selected, EquipmentCapacity capacity
    ) {
        return switch (request.getServiceType()) {
            case FUEL_DELIVERY -> capacity.getCapacityUnit() == selected.getCapacityUnit()
                    && capacity.getCapacityType() == selected.getCapacityType()
                    && capacity.getCapacityValue() != null
                    && capacity.getCapacityValue().compareTo(request.getFuelDeliveryOrder().litres()) >= 0;
            case TOWING, BATTERY_JUMP_START -> selected.getId().equals(capacity.getId())
                    || (selected.getCapacityType() == capacity.getCapacityType()
                    && selected.getCapacityUnit() == capacity.getCapacityUnit()
                    && Objects.equals(selected.getVoltage(), capacity.getVoltage())
                    && selected.getCapacityValue() != null && capacity.getCapacityValue() != null
                    && capacity.getCapacityValue().compareTo(selected.getCapacityValue()) >= 0
                    && selected.getCapacityGroups() != null && capacity.getCapacityGroups() != null
                    && !Collections.disjoint(selected.getCapacityGroups(), capacity.getCapacityGroups()));
        };
    }

    private String getCapacityId(RescueRequest request) {
        return switch (request.getServiceType()) {
            case TOWING -> request.getTowingOrder().capacityId();
            case FUEL_DELIVERY -> request.getFuelDeliveryOrder().capacityId();
            case BATTERY_JUMP_START -> request.getBatteryJumpStartOrder().capacityId();
        };
    }

    private String getCurrentVendorId() {
        Object value = httpServletRequest.getAttribute("validatedClaims");
        if (!(value instanceof Claims claims) || !"VENDOR".equals(claims.get("userType", String.class))) {
            throw new UnauthorizedException("Vendor authentication is required");
        }

        String vendorId = claims.get("vendorId", String.class);
        if (vendorId == null || vendorId.isBlank()) {
            throw new UnauthorizedException("Vendor identity is required");
        }
        return vendorId;
    }
}