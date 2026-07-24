package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SetVolumeInput(BigDecimal weightKg, int reps, UUID muscleGroupId, Instant sessionStartedAt) {
}
