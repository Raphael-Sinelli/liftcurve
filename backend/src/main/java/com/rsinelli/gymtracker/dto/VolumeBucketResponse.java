package com.rsinelli.gymtracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VolumeBucketResponse(UUID muscleGroupId, Instant weekStartUtc, BigDecimal totalVolumeKg) {
}
