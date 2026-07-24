package com.rsinelli.gymtracker.service;

import java.time.Instant;
import java.util.UUID;

public record VolumeBucketKey(UUID muscleGroupId, Instant weekStartUtc) {
}
