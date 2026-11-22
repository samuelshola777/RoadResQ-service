package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.utils.PaginatedData;
import com.roadresq.roadresq.utils.PaginationService;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.ResourceExistsException;
import com.roadresq.roadresq.utils.exception.ResourceNotFoundException;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityCreateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityUpdateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.response.EquipmentCapacityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class EquipmentCapacityService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "serviceType", "equipmentType", "capacityType", "capacityValue",
            "capacityUnit", "voltage", "displayName", "active", "createdAt", "updatedAt"
    );

    private final EquipmentCapacityRepository equipmentCapacityRepository;
    private final com.roadresq.roadresq.adminActivity.AdminActivityService adminActivityService;
    private final PaginationService paginationService;

    // Admin only: register another selectable catalogue option.
    @org.springframework.transaction.annotation.Transactional
    public EquipmentCapacityResponse createCapacity(EquipmentCapacityCreateRequest request) {
        EquipmentCapacity capacity = new EquipmentCapacity();
        capacity.setServiceType(request.serviceType());
        capacity.setEquipmentType(normalizeEquipmentType(request.equipmentType()));
        capacity.setCapacityType(request.capacityType());
        capacity.setCapacityValue(normalizeValue(request.capacityValue()));
        capacity.setCapacityUnit(request.capacityUnit());
        capacity.setVoltage(request.voltage());
        capacity.setDisplayName(cleanDisplayName(request.displayName()));
        capacity.setPictures(cleanPictures(request.pictures()));
        capacity.setActive(request.active() == null || request.active());

        validateSpecification(capacity);
        applyCapacityGroups(capacity, request.capacityGroups());
        assertNoDuplicate(capacity);
        EquipmentCapacity saved = save(capacity);
        adminActivityService.record("CAPACITY_SAVED", saved.getId(), null, null, saved.getDisplayName());
        log.info("Admin saved equipment capacity: id={}", saved.getId());
        return EquipmentCapacityResponse.from(saved);
    }

    // Admin only: partial update, preserving omitted fields.
    @org.springframework.transaction.annotation.Transactional
    public EquipmentCapacityResponse updateCapacity(String capacityId, EquipmentCapacityUpdateRequest request) {
        EquipmentCapacity capacity = findCapacity(capacityId);
        ServiceType previousServiceType = capacity.getServiceType();

        if (request.serviceType() != null) {
            capacity.setServiceType(request.serviceType());
        }
        if (request.equipmentType() != null) {
            capacity.setEquipmentType(normalizeEquipmentType(request.equipmentType()));
        }
        if (request.capacityType() != null) {
            capacity.setCapacityType(request.capacityType());
        }
        if (request.capacityValue() != null) {
            capacity.setCapacityValue(normalizeValue(request.capacityValue()));
        }
        if (request.capacityUnit() != null) {
            capacity.setCapacityUnit(request.capacityUnit());
        }
        if (request.voltage() != null) {
            capacity.setVoltage(request.voltage());
        }
        if (request.displayName() != null) {
            capacity.setDisplayName(cleanDisplayName(request.displayName()));
        }
        if (request.pictures() != null) {
            capacity.setPictures(cleanPictures(request.pictures()));
        }
        if (request.active() != null) {
            capacity.setActive(request.active());
        }

        // If changing a battery specification into another service, voltage is cleared.
        if (capacity.getServiceType() != ServiceType.BATTERY_JUMP_START) {
            capacity.setVoltage(null);
        }

        validateSpecification(capacity);
        Set<CapacityGroup> groups = request.capacityGroups();
        if (groups == null && capacity.getServiceType() == ServiceType.BATTERY_JUMP_START
                && previousServiceType == capacity.getServiceType()) {
            groups = capacity.getCapacityGroups();
        }
        applyCapacityGroups(capacity, groups);
        assertNoDuplicate(capacity);
        capacity.setUpdatedAt(LocalDateTime.now());
        EquipmentCapacity saved = save(capacity);
        adminActivityService.record("CAPACITY_SAVED", saved.getId(), null, null, saved.getDisplayName());
        log.info("Admin saved equipment capacity: id={}", saved.getId());
        return EquipmentCapacityResponse.from(saved);
    }

    // Vendors can access active options only. Admins can include inactive options.
    public EquipmentCapacityResponse getCapacity(String capacityId, boolean includeInactive) {
        EquipmentCapacity capacity = findCapacity(capacityId);
        if (!includeInactive && !capacity.isActive()) {
            throw new ResourceNotFoundException("Equipment capacity not found");
        }
        return EquipmentCapacityResponse.from(capacity);
    }

    public PaginatedData getCapacities(
            ServiceType serviceType,
            String equipmentType,
            VehicleType vehicleType,
            Boolean active,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        Query query = new Query();

        if (serviceType != null) {
            query.addCriteria(Criteria.where("serviceType").is(serviceType));
        }
        if (equipmentType != null && !equipmentType.isBlank()) {
            query.addCriteria(Criteria.where("equipmentType").is(normalizeEquipmentType(equipmentType)));
        }
        if (vehicleType != null) {
            query.addCriteria(Criteria.where("vehicleTypes").is(vehicleType));
        }
        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        }

        PaginatedData result = paginationService.paginate(
                query, EquipmentCapacity.class, page, size,
                sortBy, sortDirection, ALLOWED_SORT_FIELDS
        );
        List<EquipmentCapacityResponse> capacities = ((List<?>) result.data()).stream()
                .map(EquipmentCapacity.class::cast)
                .map(EquipmentCapacityResponse::from)
                .toList();
        return new PaginatedData(result.totalPage(), result.numberOfElements(), result.totalElements(), capacities);
    }

    // DELETE deactivates instead of removing the row. The startup seeder will not recreate it.
    @org.springframework.transaction.annotation.Transactional
    public void deleteCapacity(String capacityId) {
        EquipmentCapacity capacity = findCapacity(capacityId);
        if (capacity.isActive()) {
            capacity.setActive(false);
            capacity.setUpdatedAt(LocalDateTime.now());
            equipmentCapacityRepository.save(capacity);
            adminActivityService.record("CAPACITY_DEACTIVATED", capacity.getId(), null, "ACTIVE", "INACTIVE");
            log.info("Admin deactivated equipment capacity: id={}", capacity.getId());
        }
    }

    private EquipmentCapacity findCapacity(String capacityId) {
        if (capacityId == null || capacityId.isBlank()) {
            throw new InvalidInputException("Equipment capacity ID is required");
        }
        return equipmentCapacityRepository.findById(capacityId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment capacity not found"));
    }

    private EquipmentCapacity save(EquipmentCapacity capacity) {
        try {
            return equipmentCapacityRepository.save(capacity);
        } catch (DuplicateKeyException exception) {
            throw new ResourceExistsException("This equipment capacity already exists");
        }
    }

    private void assertNoDuplicate(EquipmentCapacity capacity) {
        List<EquipmentCapacity> matches = equipmentCapacityRepository
                .findAllByServiceTypeAndEquipmentTypeAndCapacityTypeAndCapacityValueAndCapacityUnitAndVoltage(
                        capacity.getServiceType(), capacity.getEquipmentType(),
                        capacity.getCapacityType(), capacity.getCapacityValue(),
                        capacity.getCapacityUnit(), capacity.getVoltage()
                );

        boolean duplicate = matches.stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), capacity.getId()));
        if (duplicate) {
            throw new ResourceExistsException("This equipment capacity already exists");
        }
    }

    private static void applyCapacityGroups(EquipmentCapacity capacity, Set<CapacityGroup> groups) {
        Set<CapacityGroup> selectedGroups = groups == null ? Set.of() : Set.copyOf(groups);
        if (capacity.getServiceType() == ServiceType.TOWING) {
            if (capacity.getEquipmentType().contains("MOTORCYCLE")) {
                throw new InvalidInputException("This towing equipment type is retired");
            }
            Set<CapacityGroup> expected = TowingCatalogue.groupsFor(capacity.getCapacityValue());
            if (groups != null && !expected.equals(selectedGroups)) {
                throw new InvalidInputException("The towing group must match the rated capacity band");
            }
            if (capacity.isActive() && expected.isEmpty()) {
                throw new InvalidInputException("This towing rating needs review before activation");
            }
            selectedGroups = expected;
        }
        if (selectedGroups.contains(CapacityGroup.E)) {
            throw new InvalidInputException("Capacity group E is retired");
        }
        if (capacity.getServiceType() == ServiceType.FUEL_DELIVERY && !selectedGroups.isEmpty()) {
            throw new InvalidInputException("Fuel delivery uses litre capacity without vehicle groups");
        }
        capacity.setCapacityGroups(selectedGroups);
        capacity.setVehicleTypes(TowingCatalogue.vehicleTypesFor(capacity.getServiceType(), selectedGroups));
    }

    private static void validateSpecification(EquipmentCapacity capacity) {
        if (capacity.getServiceType() == null || capacity.getCapacityType() == null
                || capacity.getCapacityUnit() == null || capacity.getCapacityValue() == null) {
            throw new InvalidInputException("Service type, capacity type, value and unit are required");
        }
        if (capacity.getCapacityValue().signum() <= 0
                || capacity.getCapacityValue().scale() > 3
                || capacity.getCapacityValue().precision() - capacity.getCapacityValue().scale() > 10) {
            throw new InvalidInputException("Capacity must be positive, with at most 10 digits and 3 decimal places");
        }
        if (capacity.getEquipmentType() == null || capacity.getEquipmentType().isBlank()) {
            throw new InvalidInputException("Equipment type is required");
        }
        if (capacity.getDisplayName() == null || capacity.getDisplayName().isBlank()) {
            throw new InvalidInputException("Display name is required");
        }

        switch (capacity.getServiceType()) {
            case TOWING -> {
                if ((capacity.getCapacityType() != CapacityType.RATED_PAYLOAD
                        && capacity.getCapacityType() != CapacityType.RATED_TOWING_CAPACITY)
                        || capacity.getCapacityUnit() != CapacityUnit.KG || capacity.getVoltage() != null) {
                    throw new InvalidInputException("Towing must use payload/towing capacity in KG, without voltage");
                }
            }
            case FUEL_DELIVERY -> {
                if (capacity.getCapacityType() != CapacityType.FUEL_VOLUME
                        || capacity.getCapacityUnit() != CapacityUnit.LITRES || capacity.getVoltage() != null) {
                    throw new InvalidInputException("Fuel delivery must use FUEL_VOLUME in LITRES, without voltage");
                }
            }
            case BATTERY_JUMP_START -> {
                if (capacity.getCapacityType() != CapacityType.PEAK_STARTING_CURRENT
                        || capacity.getCapacityUnit() != CapacityUnit.AMPS
                        || (capacity.getVoltage() == null
                        || (capacity.getVoltage() != 12 && capacity.getVoltage() != 24))) {
                    throw new InvalidInputException("Battery jump-start must use peak AMPS at 12V or 24V");
                }
            }
        }
    }

    private static BigDecimal normalizeValue(BigDecimal value) {
        if (value == null) {
            throw new InvalidInputException("Capacity value is required");
        }
        return value.stripTrailingZeros();
    }

    private static String normalizeEquipmentType(String type) {
        if (type == null || type.isBlank()) {
            throw new InvalidInputException("Equipment type is required");
        }
        String normalized = type.trim().replaceAll("[\\s-]+", "_").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]*") || normalized.length() > 100) {
            throw new InvalidInputException("Equipment type must contain letters, numbers or underscores");
        }
        return normalized;
    }

    private static String cleanDisplayName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 180) {
            throw new InvalidInputException("Display name is required (max 180 characters)");
        }
        return value.trim();
    }

    private static Set<String> cleanPictures(Set<String> pictures) {
        Set<String> result = new LinkedHashSet<>();
        if (pictures == null) {
            return result;
        }
        if (pictures.size() > 5) {
            throw new InvalidInputException("A capacity can have at most five pictures");
        }
        for (String picture : pictures) {
            if (picture == null || picture.isBlank() || picture.length() > 2048) {
                throw new InvalidInputException("Each picture must be a valid HTTPS URL");
            }
            try {
                URI uri = URI.create(picture.trim());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    throw new InvalidInputException("Each picture must be a valid HTTPS URL");
                }
                result.add(uri.toString());
            } catch (IllegalArgumentException exception) {
                throw new InvalidInputException("Each picture must be a valid HTTPS URL");
            }
        }
        return result;
    }
}