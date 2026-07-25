package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record ProgressionPointResponse(
        Instant sessionStartedAt,
        @JsonProperty("estimated_1rm_best") BigDecimal estimated1rmBest) {
}
