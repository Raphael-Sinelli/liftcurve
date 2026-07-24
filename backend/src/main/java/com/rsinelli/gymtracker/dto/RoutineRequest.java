package com.rsinelli.gymtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoutineRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotEmpty @Valid List<RoutineExerciseItem> exercises) {
}
