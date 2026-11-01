
package com.roadresq.roadresq.vendorOperationEquipment;

import com.roadresq.roadresq.utils.appsecurity.JwtService;
import com.roadresq.roadresq.utils.exception.InvalidInputException;
import com.roadresq.roadresq.utils.exception.ResourceNotFoundException;
import com.roadresq.roadresq.utils.exception.UnauthorizedException;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperation.VendorOperationRepository;
import com.roadresq.roadresq.vendorOperation.vendorOperations;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacityRepository;
import com.roadresq.roadresq.vendorOperationEquipment.request.VendorOperationEquipmentCreateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.request.VendorOperationEquipmentUpdateRequest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VendorOperationEquipmentService {

    private final VendorOperationEquipmentRepository equipmentRepository;
    private final VendorOperationRepository operationRepository;
    private final EquipmentCapacityRepository capacityRepository;
    private final JwtService jwtService;
    private final HttpServletRequest httpServletRequest;

    public VendorOperationEquipment createEquipment(VendorOperationEquipmentCreateRequest request) {
        String vendorId = currentVendorId();
        vendorOperations operation = findOwnedOperation(request.vendorOperationId(), vendorId);
        EquipmentCapacity capacity = findSelectableCapacity(request.capacityId());
        requireMatchingServiceType(operation, capacity);

        VendorOperationEquipment equipment = new VendorOperationEquipment();
        equipment.setVendorId(vendorId);
        equipment.setVendorOperationId(operation.getId());
        // Derive serviceType from the owned operation; never trust a client-supplied value.
        equipment.setServiceType(operation.getVendorOperationServiceType());
        equipment.setCapacityId(capacity.getId());
        equipment.setPictures(cleanPictures(request.pictures()));
        return equipmentRepository.save(equipment);
    }

    public List<VendorOperationEquipment> getMyEquipment(String vendorOperationId) {
        String vendorId = currentVendorId();
        if (vendorOperationId == null || vendorOperationId.isBlank()) {
            return equipmentRepository.findAllByVendorIdOrderByCreatedAtAsc(vendorId);
        }
        String operationId = requiredId(vendorOperationId, "Vendor operation ID");
        findOwnedOperation(operationId, vendorId);
        return equipmentRepository.findAllByVendorIdAndVendorOperationIdOrderByCreatedAtAsc(
                vendorId, operationId);
    }

    public List<EquipmentCapacity> getSelectableCapacities(String vendorOperationId) {
        vendorOperations operation = findOwnedOperation(vendorOperationId, currentVendorId());
        if (operation.getVendorOperationServiceType() == null) {
            throw new InvalidInputException("Vendor operation service type is missing");
        }
        return capacityRepository.findAllByServiceTypeAndActiveTrueOrderByDisplayNameAsc(
                operation.getVendorOperationServiceType());
    }

    public VendorOperationEquipment getMyEquipmentById(String equipmentId) {
        return findOwnedEquipment(equipmentId, currentVendorId());
    }

    public VendorOperationEquipment updateEquipment(
            String equipmentId, VendorOperationEquipmentUpdateRequest request) {
        String vendorId = currentVendorId();
        VendorOperationEquipment equipment = findOwnedEquipment(equipmentId, vendorId);

        if (request.capacityId() != null) {
            vendorOperations operation = findOwnedOperation(equipment.getVendorOperationId(), vendorId);
            EquipmentCapacity capacity = findSelectableCapacity(request.capacityId());
            requireMatchingServiceType(operation, capacity);
            equipment.setCapacityId(capacity.getId());
        }
        if (request.pictures() != null) {
            equipment.setPictures(cleanPictures(request.pictures()));
        }

        equipment.setUpdatedAt(LocalDateTime.now());
        return equipmentRepository.save(equipment);
    }

    public void deleteEquipment(String equipmentId) {
        VendorOperationEquipment equipment = findOwnedEquipment(equipmentId, currentVendorId());
        equipmentRepository.delete(equipment);
        // Image files are not deleted from Cloudinary by removing this database record.
    }

    private VendorOperationEquipment findOwnedEquipment(String equipmentId, String vendorId) {
        return equipmentRepository.findByIdAndVendorId(requiredId(equipmentId, "Equipment ID"), vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
    }

    private vendorOperations findOwnedOperation(String operationId, String vendorId) {
        return operationRepository.findByIdAndVendorId(requiredId(operationId, "Vendor operation ID"), vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor operation not found"));
    }

    private EquipmentCapacity findSelectableCapacity(String capacityId) {
        EquipmentCapacity capacity = capacityRepository.findById(requiredId(capacityId, "Capacity ID"))
                .orElseThrow(() -> new ResourceNotFoundException("Equipment capacity not found"));
        if (!capacity.isActive()) {
            throw new InvalidInputException("The selected equipment capacity is inactive");
        }
        return capacity;
    }

    private void requireMatchingServiceType(vendorOperations operation, EquipmentCapacity capacity) {
        ServiceType expected = operation.getVendorOperationServiceType();
        if (expected == null || expected != capacity.getServiceType()) {
            throw new InvalidInputException("Equipment capacity does not match the vendor operation service type");
        }
    }

    private String currentVendorId() {
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
        String userType = claims.get("userType", String.class);
        if (!"VENDOR".equals(userType) || vendorId == null || vendorId.isBlank()) {
            throw new UnauthorizedException("A valid vendor account is required");
        }
        return vendorId;
    }

    private static String requiredId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(label + " is required");
        }
        return value.trim();
    }

    private static Set<String> cleanPictures(Set<String> pictures) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (pictures == null) {
            return cleaned;
        }
        if (pictures.size() > 5) {
            throw new InvalidInputException("Maximum five equipment pictures are allowed");
        }
        for (String picture : pictures) {
            if (picture == null || picture.isBlank() || picture.trim().length() > 2048) {
                throw new InvalidInputException("Each picture must be a valid HTTPS URL");
            }
            try {
                URI uri = URI.create(picture.trim());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    throw new InvalidInputException("Each picture must be a valid HTTPS URL");
                }
                cleaned.add(uri.toString());
            } catch (IllegalArgumentException exception) {
                throw new InvalidInputException("Each picture must be a valid HTTPS URL");
            }
        }
        return cleaned;
    }
}