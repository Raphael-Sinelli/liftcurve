package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkoutSessionSummaryResponse(
        UUID id,
        UUID routineId,
        Instant startedAt,
        Instant finishedAt,
        int setCount) {
}
