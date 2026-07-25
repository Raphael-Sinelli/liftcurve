package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record PlateauAlertResponse(
        UUID exerciseId,
        String exerciseName,
        @JsonProperty("current_max_1rm") BigDecimal currentMax1rm,
        String suggestion) {
}
