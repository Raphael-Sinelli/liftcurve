package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record RoutineSummaryResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        int exerciseCount) {
}
