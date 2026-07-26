package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DemoDataGenerator {

    public static final String[] EXERCISE_NAMES = {
            "Supino Reto", "Agachamento Livre", "Levantamento Terra",
            "Desenvolvimento Militar", "Rosca Direta", "Puxada Alta"
    };

    public static final String[] EXERCISE_MUSCLE_GROUPS = {
            "Peito", "Pernas", "Costas", "Ombros", "Bíceps", "Costas"
    };

    public static final int PLATEAU_EXERCISE_INDEX = 4;

    private static final long SEED = 424242L;
    private static final int WEEKS = 16;
    private static final int[] GROUP_A = {0, 1, 2};
    private static final int[] GROUP_B = {3, 4, 5};
    private static final double[] BASE_WEIGHT_KG = {40, 60, 70, 30, 20, 45};
    private static final int[] BASE_REPS = {8, 8, 6, 8, 10, 8};
    private static final int PLATEAU_FROZEN_FROM_OCCURRENCE = 19;

    public List<GeneratedSession> generate(Instant anchorNow) {
        Random rng = new Random(SEED);
        int[] occurrenceIndexByExercise = new int[6];
        BigDecimal[] frozenWeightByExercise = new BigDecimal[6];
        int[] frozenRepsByExercise = new int[6];
        BigDecimal[] currentWeight = new BigDecimal[6];
        for (int e = 0; e < 6; e++) {
            currentWeight[e] = BigDecimal.valueOf(BASE_WEIGHT_KG[e]).setScale(2, RoundingMode.HALF_UP);
        }

        LocalDate today = anchorNow.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate firstMonday = today.minusWeeks(WEEKS).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<GeneratedSession> sessions = new ArrayList<>();
        for (int week = 0; week < WEEKS; week++) {
            LocalDate monday = firstMonday.plusWeeks(week);
            int[][] slots = week % 2 == 0
                    ? new int[][]{GROUP_A, GROUP_B, GROUP_A}
                    : new int[][]{GROUP_B, GROUP_A, GROUP_B};
            LocalDate[] days = {monday, monday.plusDays(2), monday.plusDays(4)};
            int[] baseHour = {19, 19, 19};
            int[] baseMinute = {0, 0, 30};

            for (int slot = 0; slot < 3; slot++) {
                Instant startedAt = days[slot]
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusHours(baseHour[slot])
                        .plusMinutes(baseMinute[slot])
                        .toInstant()
                        .plusSeconds((rng.nextInt(91) - 45) * 60L);
                Instant finishedAt = startedAt.plusSeconds(3000 + rng.nextInt(1200));

                List<GeneratedSet> sets = new ArrayList<>();
                for (int exerciseIndex : slots[slot]) {
                    int occurrence = occurrenceIndexByExercise[exerciseIndex]++;
                    boolean frozen = exerciseIndex == PLATEAU_EXERCISE_INDEX && occurrence >= PLATEAU_FROZEN_FROM_OCCURRENCE;

                    BigDecimal weightKg;
                    int reps;
                    if (frozen) {
                        weightKg = frozenWeightByExercise[exerciseIndex];
                        reps = frozenRepsByExercise[exerciseIndex];
                    } else {
                        weightKg = nextWeight(currentWeight, exerciseIndex, rng);
                        reps = repsForOccurrence(exerciseIndex, rng);
                        if (exerciseIndex == PLATEAU_EXERCISE_INDEX && occurrence == PLATEAU_FROZEN_FROM_OCCURRENCE - 1) {
                            frozenWeightByExercise[exerciseIndex] = weightKg;
                            frozenRepsByExercise[exerciseIndex] = reps;
                        }
                    }

                    int setCount = 3 + rng.nextInt(2);
                    for (int s = 0; s < setCount; s++) {
                        BigDecimal rpe = rng.nextDouble() < 0.7
                                ? BigDecimal.valueOf(6.5 + rng.nextInt(7) * 0.5).setScale(1, RoundingMode.HALF_UP)
                                : null;
                        sets.add(new GeneratedSet(exerciseIndex, weightKg, reps, rpe));
                    }
                }

                sessions.add(new GeneratedSession(startedAt, finishedAt, sets));
            }
        }

        return sessions;
    }

    /**
     * Advances the exercise's running weight by a non-negative increment and returns the new
     * total. Weight is tracked as running state (not recomputed independently per occurrence)
     * so it can only ever go up or stay flat — this guarantees non-plateau exercises never
     * produce a spurious non-improving streak from negative noise (see PlateauDetectionService).
     */
    private BigDecimal nextWeight(BigDecimal[] currentWeight, int exerciseIndex, Random rng) {
        BigDecimal increment = BigDecimal.valueOf(rng.nextInt(3) * 0.625).setScale(2, RoundingMode.HALF_UP);
        currentWeight[exerciseIndex] = currentWeight[exerciseIndex].add(increment);
        return currentWeight[exerciseIndex];
    }

    private int repsForOccurrence(int exerciseIndex, Random rng) {
        int reps = BASE_REPS[exerciseIndex] + rng.nextInt(3);
        return Math.min(10, reps);
    }
}
