package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SessionSetRequest(
        @NotNull UUID exerciseId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weightKg,
        @Min(1) int reps,
        @DecimalMin(value = "1") @DecimalMax(value = "10") BigDecimal rpe) {
}
