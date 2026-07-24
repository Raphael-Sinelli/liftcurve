package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkoutSessionRequest(UUID routineId, @Size(max = 2000) String notes) {
}
