package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.DemoDataGenerator;
import com.rsinelli.gymtracker.service.GeneratedSession;
import com.rsinelli.gymtracker.service.GeneratedSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataGeneratorTest {

    private static final Instant ANCHOR = Instant.parse("2026-07-25T12:00:00Z");

    private final DemoDataGenerator generator = new DemoDataGenerator();

    @Test
    void generatesFortyEightSessionsAcrossSixteenWeeks() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        assertEquals(48, sessions.size());
    }

    @Test
    void sessionsAreInChronologicalOrder() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        for (int i = 1; i < sessions.size(); i++) {
            assertTrue(sessions.get(i).startedAt().isAfter(sessions.get(i - 1).startedAt()),
                    "sessão " + i + " deveria ser depois da sessão " + (i - 1));
        }
    }

    @Test
    void eachExerciseAppearsInTwentyFourSessions() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);
        Map<Integer, Integer> occurrencesByExercise = new HashMap<>();

        for (GeneratedSession session : sessions) {
            for (int exerciseIndex : distinctExerciseIndexes(session)) {
                occurrencesByExercise.merge(exerciseIndex, 1, Integer::sum);
            }
        }

        for (int e = 0; e < 6; e++) {
            assertEquals(24, occurrencesByExercise.getOrDefault(e, 0), "exercício " + e);
        }
    }

    @Test
    void plateauExerciseHasIdenticalWeightAndRepsInLastFiveOccurrences() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);
        List<GeneratedSet> plateauOccurrences = firstSetPerOccurrence(sessions, DemoDataGenerator.PLATEAU_EXERCISE_INDEX);

        assertEquals(24, plateauOccurrences.size());
        List<GeneratedSet> lastFive = plateauOccurrences.subList(19, 24);
        BigDecimal frozenWeight = lastFive.get(0).weightKg();
        int frozenReps = lastFive.get(0).reps();

        for (GeneratedSet occurrence : lastFive) {
            assertEquals(0, frozenWeight.compareTo(occurrence.weightKg()), "peso deveria estar congelado");
            assertEquals(frozenReps, occurrence.reps(), "reps deveriam estar congeladas");
        }
    }

    @Test
    void nonPlateauExercisesTrendUpwardOverTime() {
        List<GeneratedSession> sessions = generator.generate(ANCHOR);

        for (int e = 0; e < 6; e++) {
            if (e == DemoDataGenerator.PLATEAU_EXERCISE_INDEX) {
                continue;
            }
            List<GeneratedSet> occurrences = firstSetPerOccurrence(sessions, e);
            double avgFirstFour = occurrences.subList(0, 4).stream()
                    .mapToDouble(s -> s.weightKg().doubleValue()).average().orElseThrow();
            double avgLastFour = occurrences.subList(occurrences.size() - 4, occurrences.size()).stream()
                    .mapToDouble(s -> s.weightKg().doubleValue()).average().orElseThrow();

            assertTrue(avgLastFour > avgFirstFour, "exercício " + e + " deveria progredir: primeiras=" + avgFirstFour + " últimas=" + avgLastFour);
        }
    }

    @Test
    void generationIsDeterministic() {
        List<GeneratedSession> first = generator.generate(ANCHOR);
        List<GeneratedSession> second = generator.generate(ANCHOR);

        assertEquals(first, second);
    }

    private static List<Integer> distinctExerciseIndexes(GeneratedSession session) {
        List<Integer> result = new ArrayList<>();
        for (GeneratedSet set : session.sets()) {
            if (!result.contains(set.exerciseIndex())) {
                result.add(set.exerciseIndex());
            }
        }
        return result;
    }

    /** 1 elemento por sessão em que o exercício apareceu — pega o primeiro set daquela sessão pra esse exercício. */
    private static List<GeneratedSet> firstSetPerOccurrence(List<GeneratedSession> sessions, int exerciseIndex) {
        List<GeneratedSet> result = new ArrayList<>();
        for (GeneratedSession session : sessions) {
            session.sets().stream()
                    .filter(s -> s.exerciseIndex() == exerciseIndex)
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }
}
