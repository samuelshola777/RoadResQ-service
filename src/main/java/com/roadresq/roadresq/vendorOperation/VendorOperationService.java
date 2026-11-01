package com.roadresq.roadresq.vendorOperation;

import com.roadresq.roadresq.utils.appsecurity.JwtService;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.ResourceExistsException;
import com.roadresq.roadresq.utils.exception.ResourceNotFoundException;
import com.roadresq.roadresq.utils.exception.UnauthorizedException;
import com.roadresq.roadresq.vendorOperation.request.VendorOperationAvailabilityRequest;
import com.roadresq.roadresq.vendorOperation.request.VendorOperationCreateRequest;
import com.roadresq.roadresq.vendorOperation.request.VendorOperationUpdateRequest;
import com.roadresq.roadresq.vendorOperation.response.VendorOperationServiceResponse;
import com.roadresq.roadresq.vendorOperationEquipment.VendorOperationEquipmentRepository;
import com.roadresq.roadresq.vendorOperationEquipment.VendorOperationEquipmentService;
import com.roadresq.roadresq.vendorOperationEquipment.request.VendorOperationEquipmentCreateRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorOperationService {

    private final VendorOperationRepository vendorOperationRepository;
    private final VendorOperationEquipmentRepository equipmentRepository;
    private final VendorOperationEquipmentService equipmentService;
    private final MongoTemplate mongoTemplate;
    private final JwtService jwtService;
    private final HttpServletRequest httpServletRequest;

    @PostConstruct
    public void ensureUniqueVendorOperationIndex() {
        // Exactly one operation for each (vendorId, serviceType), including inactive operations.
        // Resolve pre-existing duplicates before starting the application.
        mongoTemplate.indexOps(vendorOperations.class).ensureIndex(
                new Index()
                        .on("vendorId", Sort.Direction.ASC)
                        .on("vendorOperationServiceType", Sort.Direction.ASC)
                        .unique()
                        .named("uk_vendor_operation_service_type")
        );
    }

    public List<ServiceType> getServiceTypes() {
        return Arrays.asList(ServiceType.values());
    }

    public List<ServiceType> getAvailableServiceTypes() {
        String vendorId = currentVendor().vendorId();
        Set<ServiceType> registered = vendorOperationRepository
                .findAllByVendorIdOrderByCreatedAtAsc(vendorId)
                .stream()
                .map(vendorOperations::getVendorOperationServiceType)
                .filter(type -> type != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ServiceType.class)));

        return Arrays.stream(ServiceType.values())
                .filter(type -> !registered.contains(type))
                .toList();
    }

    @Transactional
    public VendorOperationServiceResponse createService(VendorOperationCreateRequest request) {
        VendorIdentity identity = currentVendor();
        if (request == null || request.serviceType() == null) {
            throw new InvalidInputException("Service type is required");
        }

        // The operation can exist without equipment. Inactive operations also reserve the type.
        if (vendorOperationRepository.existsByVendorIdAndVendorOperationServiceType(
                identity.vendorId(), request.serviceType())) {
            throw new ResourceExistsException("You have already registered this service type");
        }

        vendorOperations operation = new vendorOperations();
        operation.setVendorId(identity.vendorId());
        operation.setUserId(identity.userId());
        operation.setVendorOperationServiceType(request.serviceType());
        operation.setActive(false);

        try {
            operation = vendorOperationRepository.save(operation);
        } catch (DuplicateKeyException exception) {
            // Compound unique MongoDB index protects concurrent requests too.
            throw new ResourceExistsException("You have already registered this service type");
        }

        // Optional same-request equipment registration. These writes share the MongoDB
        // transaction, so a bad capacity rolls back the new operation and its equipment.
        if (request.equipment() != null) {
            for (VendorOperationCreateRequest.EquipmentInput item : request.equipment()) {
                if (item == null) {
                    throw new InvalidInputException("Equipment cannot be null");
                }
                equipmentService.createEquipment(new VendorOperationEquipmentCreateRequest(
                        operation.getId(), item.capacityId(), item.pictures()
                ));
            }
        }

        return VendorOperationServiceResponse.from(operation);
    }

    public List<VendorOperationServiceResponse> getCurrentServices() {
        return vendorOperationRepository
                .findAllByVendorIdOrderByCreatedAtAsc(currentVendor().vendorId())
                .stream()
                .map(VendorOperationServiceResponse::from)
                .toList();
    }

    public VendorOperationServiceResponse getCurrentService(String serviceId) {
        return VendorOperationServiceResponse.from(findCurrentService(serviceId));
    }

    public VendorOperationServiceResponse updateService(
            String serviceId, VendorOperationUpdateRequest request) {
        if (request == null || request.active() == null) {
            throw new InvalidInputException("Active status is required");
        }
        vendorOperations operation = findCurrentService(serviceId);
        operation.setActive(request.active());
        operation.setUpdatedAt(LocalDateTime.now());
        return VendorOperationServiceResponse.from(vendorOperationRepository.save(operation));
    }

    public VendorOperationServiceResponse updateAvailability(
            String serviceId, VendorOperationAvailabilityRequest request) {
        if (request == null || request.available() == null) {
            throw new InvalidInputException("Availability is required");
        }
        vendorOperations operation = findCurrentService(serviceId);
        operation.setActive(request.available());
        operation.setUpdatedAt(LocalDateTime.now());
        return VendorOperationServiceResponse.from(vendorOperationRepository.save(operation));
    }

    public void deleteService(String serviceId) {
        vendorOperations operation = findCurrentService(serviceId);
        if (equipmentRepository.existsByVendorOperationId(operation.getId())) {
            throw new InvalidInputException(
                    "Remove this operation's equipment before deleting the operation");
        }
        // Physical removal releases this service type for a new operation.
        vendorOperationRepository.delete(operation);
    }

    public List<VendorOperationServiceResponse> getVendorServices(String vendorId) {
        if (vendorId == null || vendorId.isBlank()) {
            throw new InvalidInputException("Vendor ID is required");
        }
        return vendorOperationRepository
                .findAllByVendorIdOrderByCreatedAtAsc(vendorId.trim())
                .stream()
                .map(VendorOperationServiceResponse::from)
                .toList();
    }

    private vendorOperations findCurrentService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidInputException("Vendor operation ID is required");
        }
        return vendorOperationRepository
                .findByIdAndVendorId(serviceId.trim(), currentVendor().vendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor operation not found"));
    }

    private VendorIdentity currentVendor() {
        String authorization = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Vendor authentication is required");
        }

        final Claims claims;
        try {
            claims = jwtService.parse(authorization.substring(7).trim());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid or expired vendor session");
        }

        String vendorId = claims.get("vendorId", String.class);
        String userId = claims.get("userId", String.class);
        String userType = claims.get("userType", String.class);
        if (!"VENDOR".equals(userType) || vendorId == null || vendorId.isBlank()
                || userId == null || userId.isBlank()) {
            throw new UnauthorizedException("A valid vendor account is required");
        }
        return new VendorIdentity(vendorId, userId);
    }

    private record VendorIdentity(String vendorId, String userId) {
    }
}