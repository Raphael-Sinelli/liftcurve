package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SessionSetResponse(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        int setNumber,
        BigDecimal weightKg,
        int reps,
        BigDecimal rpe,
        @JsonProperty("estimated_1rm_epley") BigDecimal estimated1rmEpley,
        @JsonProperty("estimated_1rm_brzycki") BigDecimal estimated1rmBrzycki,
        @JsonProperty("estimated_1rm_best") BigDecimal estimated1rmBest,
        Instant createdAt) {
}
