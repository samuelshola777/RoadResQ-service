package com.roadresq.roadresq.rescueRequest.request;

import jakarta.validation.constraints.NotNull;

public record RescueQuoteDecisionRequest(@NotNull Boolean accepted) {
}