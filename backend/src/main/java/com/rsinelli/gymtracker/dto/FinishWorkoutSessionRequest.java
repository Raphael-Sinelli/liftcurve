package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Size;

public record FinishWorkoutSessionRequest(@Size(max = 2000) String notes) {
}
