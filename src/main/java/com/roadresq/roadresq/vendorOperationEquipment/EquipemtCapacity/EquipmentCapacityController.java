package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.roadresq.roadresq.utils.ApiResponse;
import com.roadresq.roadresq.utils.PaginatedData;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityCreateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.request.EquipmentCapacityUpdateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.response.EquipmentCapacityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/private/equipment-capacities")
    public ResponseEntity<ApiResponse<PaginatedData>> getActiveCapacities(
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacities retrieved successfully",
                equipmentCapacityService.getCapacities(serviceType, equipmentType, vehicleType, true,
                        page, size, sortBy, sortDirection)));
    }

    @GetMapping("/private/equipment-capacities/{capacityId}")
    public ResponseEntity<ApiResponse<EquipmentCapacityResponse>> getActiveCapacity(@PathVariable String capacityId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity retrieved successfully",
                equipmentCapacityService.getCapacity(capacityId, false)));
    }

    @GetMapping("/private/admin/equipment-capacities")
    public ResponseEntity<ApiResponse<PaginatedData>> getAllCapacities(
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacities retrieved successfully",
                equipmentCapacityService.getCapacities(serviceType, equipmentType, vehicleType, active,
                        page, size, sortBy, sortDirection)));
    }

    @GetMapping("/private/admin/equipment-capacities/{capacityId}")
    public ResponseEntity<ApiResponse<EquipmentCapacityResponse>> getCapacity(@PathVariable String capacityId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity retrieved successfully",
                equipmentCapacityService.getCapacity(capacityId, true)));
    }

    @PostMapping("/private/admin/equipment-capacities")
    public ResponseEntity<ApiResponse<EquipmentCapacityResponse>> createCapacity(
            @Valid @RequestBody EquipmentCapacityCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Equipment capacity created successfully",
                        equipmentCapacityService.createCapacity(request)));
    }

    @PatchMapping("/private/admin/equipment-capacities/{capacityId}")
    public ResponseEntity<ApiResponse<EquipmentCapacityResponse>> updateCapacity(
            @PathVariable String capacityId,
            @Valid @RequestBody EquipmentCapacityUpdateRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity updated successfully",
                equipmentCapacityService.updateCapacity(capacityId, request)));
    }

    @DeleteMapping("/private/admin/equipment-capacities/{capacityId}")
    public ResponseEntity<ApiResponse<Void>> deleteCapacity(@PathVariable String capacityId) {
        equipmentCapacityService.deleteCapacity(capacityId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacity deactivated successfully", null));
    }
}