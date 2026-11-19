package com.roadresq.roadresq.rescueRequest.dispatch;

import com.roadresq.roadresq.rescueRequest.RescueRequest;
import com.roadresq.roadresq.rescueRequest.dispatch.response.RescueDispatchOffer;
import com.roadresq.roadresq.rescueRequest.request.RescueJobStageRequest;
import com.roadresq.roadresq.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rescue-dispatch")
@RequiredArgsConstructor
public class RescueDispatchController {

    private final RescueDispatchService rescueDispatchService;

    @GetMapping("/private/current-job")
    public ResponseEntity<ApiResponse<RescueRequest>> getCurrentJob() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Current rescue job retrieved successfully",
                rescueDispatchService.getCurrentJob()));
    }

    @PutMapping("/private/jobs/{requestId}/stage")
    public ResponseEntity<ApiResponse<RescueRequest>> updateJobStage(
            @PathVariable String requestId,
            @Valid @RequestBody RescueJobStageRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue job stage updated successfully",
                rescueDispatchService.updateJobStage(requestId, request)));
    }

    @GetMapping("/private/offers")
    public ResponseEntity<ApiResponse<List<RescueDispatchOffer>>> getRescueOffers() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue offers retrieved successfully",
                rescueDispatchService.getRescueOffers()));
    }

    @PostMapping("/private/offers/{requestId}/accept")
    public ResponseEntity<ApiResponse<RescueDispatchOffer>> acceptRescueOffer(
            @PathVariable String requestId
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rescue offer accepted successfully",
                rescueDispatchService.acceptRescueOffer(requestId)));
    }
}