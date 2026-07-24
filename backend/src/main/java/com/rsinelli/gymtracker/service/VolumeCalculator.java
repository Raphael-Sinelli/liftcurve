package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VolumeCalculator {

    public Map<VolumeBucketKey, BigDecimal> calculate(List<SetVolumeInput> inputs) {
        Map<VolumeBucketKey, BigDecimal> totals = new HashMap<>();
        for (SetVolumeInput input : inputs) {
            VolumeBucketKey key = new VolumeBucketKey(input.muscleGroupId(), weekStartUtc(input.sessionStartedAt()));
            BigDecimal setVolume = input.weightKg().multiply(BigDecimal.valueOf(input.reps()));
            totals.merge(key, setVolume, BigDecimal::add);
        }
        return totals;
    }

    private Instant weekStartUtc(Instant instant) {
        ZonedDateTime zoned = instant.atZone(ZoneOffset.UTC);
        return zoned.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
