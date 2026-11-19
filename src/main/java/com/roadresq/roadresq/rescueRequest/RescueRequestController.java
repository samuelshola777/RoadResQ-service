package com.roadresq.roadresq.rescueRequest;


import com.roadresq.roadresq.rescueRequest.request.RescueQuoteDecisionRequest;
import com.roadresq.roadresq.rescueRequest.request.RescueRequestCreateRequest;
import com.roadresq.roadresq.rescueRequest.request.RescueRequestUpdateRequest;
import com.roadresq.roadresq.utils.ApiResponse;
import com.roadresq.roadresq.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rescue-requests")
@RequiredArgsConstructor
public class RescueRequestController {

    private final RescueRequestService rescueRequestService;

    @PostMapping("/private")
    public ResponseEntity<ApiResponse<RescueRequest>> createRescueRequest(
            @RequestHeader(name = "Idempotency-Key", defaultValue = "") String key,
            @Valid @RequestBody RescueRequestCreateRequest request
    ) {
        RescueRequestCreationResult result = rescueRequestService.createRescueRequest(key, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ApiResponse<>(true,
                        result.created() ? "Rescue request created successfully" : "Rescue request retrieved successfully",
                        result.request()));
    }

    @GetMapping("/private")
    public ResponseEntity<ApiResponse<PaginatedData>> getRescueRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue requests retrieved successfully",
                rescueRequestService.getRescueRequests(page, size)));
    }

    @GetMapping("/private/{requestId}")
    public ResponseEntity<ApiResponse<RescueRequest>> getRescueRequest(@PathVariable String requestId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue request retrieved successfully",
                rescueRequestService.getRescueRequest(requestId)));
    }

    @PutMapping("/private/{requestId}")
    public ResponseEntity<ApiResponse<RescueRequest>> updateRescueRequest(
            @PathVariable String requestId, @Valid @RequestBody RescueRequestUpdateRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue request updated successfully",
                rescueRequestService.updateRescueRequest(requestId, request)));
    }

    @PatchMapping("/private/{requestId}/quote")
    public ResponseEntity<ApiResponse<RescueRequest>> updateQuoteDecision(
            @PathVariable String requestId, @Valid @RequestBody RescueQuoteDecisionRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Quote decision updated successfully",
                rescueRequestService.updateQuoteDecision(requestId, request)));
    }

    @DeleteMapping("/private/{requestId}")
    public ResponseEntity<ApiResponse<RescueRequest>> cancelRescueRequest(@PathVariable String requestId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue request cancelled successfully",
                rescueRequestService.cancelRescueRequest(requestId)));
    }
}