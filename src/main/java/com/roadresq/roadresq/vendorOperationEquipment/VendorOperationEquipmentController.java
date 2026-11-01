
package com.roadresq.roadresq.vendorOperationEquipment;

import com.roadresq.roadresq.utils.ApiResponse;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import com.roadresq.roadresq.vendorOperationEquipment.request.VendorOperationEquipmentCreateRequest;
import com.roadresq.roadresq.vendorOperationEquipment.request.VendorOperationEquipmentUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vendor-operation-equipment")
@RequiredArgsConstructor
public class VendorOperationEquipmentController {

    private final VendorOperationEquipmentService vendorOperationEquipmentService;

    @PostMapping("/private")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationEquipment>> createEquipment(
            @Valid @RequestBody VendorOperationEquipmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true,
                "Equipment registered successfully",
                vendorOperationEquipmentService.createEquipment(request)));
    }

    @GetMapping("/private")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<VendorOperationEquipment>>> getMyEquipment(
            @RequestParam(required = false) String vendorOperationId) {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Equipment retrieved successfully",
                vendorOperationEquipmentService.getMyEquipment(vendorOperationId)));
    }

    @GetMapping("/private/operations/{vendorOperationId}/capacities")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<EquipmentCapacity>>> getSelectableCapacities(
            @PathVariable String vendorOperationId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Equipment capacities retrieved successfully",
                vendorOperationEquipmentService.getSelectableCapacities(vendorOperationId)));
    }

    @GetMapping("/private/{equipmentId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationEquipment>> getEquipment(
            @PathVariable String equipmentId) {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Equipment retrieved successfully",
                vendorOperationEquipmentService.getMyEquipmentById(equipmentId)));
    }

    @PatchMapping("/private/{equipmentId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationEquipment>> updateEquipment(
            @PathVariable String equipmentId,
            @Valid @RequestBody VendorOperationEquipmentUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Equipment updated successfully",
                vendorOperationEquipmentService.updateEquipment(equipmentId, request)));
    }

    @DeleteMapping("/private/{equipmentId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Void>> deleteEquipment(
            @PathVariable String equipmentId) {
        vendorOperationEquipmentService.deleteEquipment(equipmentId);
        return ResponseEntity.ok(new ApiResponse<>(true,
                "Equipment removed successfully", null));
    }
}