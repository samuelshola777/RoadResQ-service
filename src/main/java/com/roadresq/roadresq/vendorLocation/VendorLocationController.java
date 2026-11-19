package com.roadresq.roadresq.vendorLocation;

import com.roadresq.roadresq.rescueRequest.RescueLocation;
import com.roadresq.roadresq.utils.ApiResponse;
import com.roadresq.roadresq.vendorLocation.request.VendorLocationRequest;
import com.roadresq.roadresq.vendorLocation.response.VendorMapMarker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vendor-location")
@RequiredArgsConstructor
public class VendorLocationController {

    private final VendorLocationService vendorLocationService;

    @PutMapping("/private")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @Valid @RequestBody VendorLocationRequest request) {
        vendorLocationService.updateLocation(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor location updated successfully", null));
    }

    @DeleteMapping("/private")
    public ResponseEntity<ApiResponse<Void>> removeLocation() {
        vendorLocationService.removeLocation();
        return ResponseEntity.ok(new ApiResponse<>(true, "Vendor location removed successfully", null));
    }

    @PostMapping("/private/nearby-vendors")
    public ResponseEntity<ApiResponse<List<VendorMapMarker>>> getNearbyVendors(
            @Valid @RequestBody RescueLocation location) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Nearby vendors retrieved successfully",
                vendorLocationService.getNearbyMarkers(location)));
    }
}