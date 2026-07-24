package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RoutineExerciseItem(
        @NotNull UUID exerciseId,
        @Min(1) int plannedSets,
        @Min(1) int plannedReps,
        @DecimalMin(value = "0", inclusive = true) BigDecimal plannedLoadKg) {
}
