package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class OneRepMaxCalculator {

    private static final int BRZYCKI_UNDEFINED_REPS_THRESHOLD = 37;
    private static final int RESULT_SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 10;

    public OneRepMaxResult calculate(BigDecimal weightKg, int reps) {
        BigDecimal epley = calculateEpley(weightKg, reps);
        BigDecimal brzycki = reps >= BRZYCKI_UNDEFINED_REPS_THRESHOLD ? null : calculateBrzycki(weightKg, reps);
        BigDecimal best = brzycki == null ? epley : epley.max(brzycki);
        return new OneRepMaxResult(epley, brzycki, best);
    }

    private BigDecimal calculateEpley(BigDecimal weightKg, int reps) {
        BigDecimal repsOverThirty = BigDecimal.valueOf(reps)
                .divide(BigDecimal.valueOf(30), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal repsFactor = BigDecimal.ONE.add(repsOverThirty);
        return weightKg.multiply(repsFactor).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBrzycki(BigDecimal weightKg, int reps) {
        BigDecimal denominator = BigDecimal.valueOf(BRZYCKI_UNDEFINED_REPS_THRESHOLD - reps);
        return weightKg.multiply(BigDecimal.valueOf(36))
                .divide(denominator, RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
