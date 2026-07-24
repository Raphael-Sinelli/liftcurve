package com.rsinelli.gymtracker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RoutineExerciseResponse(
        UUID exerciseId,
        String exerciseName,
        int orderIndex,
        int plannedSets,
        int plannedReps,
        BigDecimal plannedLoadKg) {
}
