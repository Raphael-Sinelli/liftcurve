package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID muscleGroupId) {
}
