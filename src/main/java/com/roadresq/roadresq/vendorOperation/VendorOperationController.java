package com.roadresq.roadresq.vendorOperation;

import com.roadresq.roadresq.vendorOperation.request.VendorOperationAvailabilityRequest;
import com.roadresq.roadresq.vendorOperation.request.VendorOperationCreateRequest;
import com.roadresq.roadresq.vendorOperation.request.VendorOperationUpdateRequest;
import com.roadresq.roadresq.vendorOperation.response.VendorOperationServiceResponse;
import com.roadresq.roadresq.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vendor-operation")
@RequiredArgsConstructor
public class VendorOperationController {
    private final VendorOperationService vendorServiceOperationService;
    private final com.roadresq.roadresq.adminActivity.AdminActivityService adminActivityService;

    @GetMapping("/private/service-types")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<ServiceType>>> getServiceTypes() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service types retrieved successfully", vendorServiceOperationService.getServiceTypes()));
    }

    @GetMapping("/private/available-service-types")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<ServiceType>>> getAvailableServiceTypes() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Available service types retrieved successfully",
                vendorServiceOperationService.getAvailableServiceTypes()));
    }

    @PostMapping("/private/services")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationServiceResponse>> createService(@Valid @RequestBody VendorOperationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true,
                "Service registered successfully", vendorServiceOperationService.createService(request)));
    }

    @GetMapping("/private/services")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<List<VendorOperationServiceResponse>>> getServices() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Services retrieved successfully", vendorServiceOperationService.getCurrentServices()));
    }

    @GetMapping("/private/services/{serviceId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationServiceResponse>> getService(@PathVariable String serviceId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service retrieved successfully", vendorServiceOperationService.getCurrentService(serviceId)));
    }

    @PatchMapping("/private/services/{serviceId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationServiceResponse>> updateService(@PathVariable String serviceId, @Valid @RequestBody VendorOperationUpdateRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service updated successfully", vendorServiceOperationService.updateService(serviceId, request)));
    }

    @PatchMapping("/private/services/{serviceId}/availability")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<VendorOperationServiceResponse>> updateAvailability(@PathVariable String serviceId, @Valid @RequestBody VendorOperationAvailabilityRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service availability updated successfully", vendorServiceOperationService.updateAvailability(serviceId, request)));
    }

    @DeleteMapping("/private/services/{serviceId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable String serviceId) {
        vendorServiceOperationService.deleteService(serviceId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Service removed successfully", null));
    }

    @GetMapping("/private/admin/{vendorId}/services")
    public ResponseEntity<ApiResponse<List<VendorOperationServiceResponse>>> getVendorServices(@PathVariable String vendorId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor services retrieved successfully", vendorServiceOperationService.getVendorServices(vendorId)));
    }


    @GetMapping("/private/admin/breakdown")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> breakdown() {
        return new ApiResponse<>(true, "Operation breakdown retrieved", vendorServiceOperationService.getAdminBreakdown());
    }

    @GetMapping("/private/admin/summary")
    public ApiResponse<java.util.Map<String, Long>> summary() {
        return new ApiResponse<>(true, "Operations summary retrieved", vendorServiceOperationService.getAdminSummary());
    }

    @GetMapping("/private/admin/{vendorId}/equipment")
    public ApiResponse<com.roadresq.roadresq.utils.PaginatedData> equipment(@PathVariable String vendorId,
                                                                            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Vendor equipment retrieved", vendorServiceOperationService.getAdminEquipment(vendorId, page, size));
    }

    @GetMapping("/private/admin/activity")
    public ApiResponse<com.roadresq.roadresq.utils.PaginatedData> activity(@RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(true, "Road service admin activity retrieved", adminActivityService.getActivity(page, size));
    }

}