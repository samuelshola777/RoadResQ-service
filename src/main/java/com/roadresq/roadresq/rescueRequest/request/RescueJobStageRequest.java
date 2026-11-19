package com.roadresq.roadresq.rescueRequest.request;

import com.roadresq.roadresq.rescueRequest.RescueJobStage;
import com.roadresq.roadresq.rescueRequest.RescueLocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RescueJobStageRequest(
        @NotNull RescueJobStage stage,
        @Valid RescueLocation fuelStationLocation
) {}