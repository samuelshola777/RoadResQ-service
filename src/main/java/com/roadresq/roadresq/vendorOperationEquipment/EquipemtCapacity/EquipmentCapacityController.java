package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.utils.ApiResponse;
import com.roadresq.roadresq.utils.PaginatedData;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacityService;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityCreateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment-capacity")
@RequiredArgsConstructor
public class EquipmentCapacityController {

    private final EquipmentCapacityService equipmentCapacityService;

    // Vendors see only active options that they can select during equipment registration.
    @GetMapping("/private/equipment-capacities")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<PaginatedData>> getActiveCapacities(
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacities retrieved successfully",
                equipmentCapacityService.getCapacities(serviceType, equipmentType, true,
                        page, size, sortBy, sortDirection)));
    }

    @GetMapping("/private/equipment-capacities/{capacityId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<EquipmentCapacity>> getActiveCapacity(@PathVariable String capacityId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity retrieved successfully",
                equipmentCapacityService.getCapacity(capacityId, false)));
    }

    // Admin reads include inactive records for catalogue management.
    @GetMapping("/private/admin/equipment-capacities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedData>> getAllCapacities(
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacities retrieved successfully",
                equipmentCapacityService.getCapacities(serviceType, equipmentType, active,
                        page, size, sortBy, sortDirection)));
    }

    @GetMapping("/private/admin/equipment-capacities/{capacityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EquipmentCapacity>> getCapacity(@PathVariable String capacityId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity retrieved successfully",
                equipmentCapacityService.getCapacity(capacityId, true)));
    }

    @PostMapping("/private/admin/equipment-capacities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EquipmentCapacity>> createCapacity(
            @Valid @RequestBody EquipmentCapacityCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Equipment capacity created successfully",
                        equipmentCapacityService.createCapacity(request)));
    }

    @PatchMapping("/private/admin/equipment-capacities/{capacityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EquipmentCapacity>> updateCapacity(
            @PathVariable String capacityId,
            @Valid @RequestBody EquipmentCapacityUpdateRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity updated successfully",
                equipmentCapacityService.updateCapacity(capacityId, request)));
    }

    @DeleteMapping("/private/admin/equipment-capacities/{capacityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCapacity(@PathVariable String capacityId) {
        equipmentCapacityService.deleteCapacity(capacityId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity deactivated successfully", null));
    }
}