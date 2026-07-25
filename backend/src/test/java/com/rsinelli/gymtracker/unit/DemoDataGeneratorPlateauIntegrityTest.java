package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.DemoDataGenerator;
import com.rsinelli.gymtracker.service.GeneratedSession;
import com.rsinelli.gymtracker.service.GeneratedSet;
import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import com.rsinelli.gymtracker.service.PlateauDetectionService;
import com.rsinelli.gymtracker.service.PlateauResult;
import com.rsinelli.gymtracker.service.PlateauStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoDataGeneratorPlateauIntegrityTest {

    private static final Instant ANCHOR = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void exactlyOneExercisePlateausAcrossAllSevenWeekdayAnchors() {
        DemoDataGenerator generator = new DemoDataGenerator();
        OneRepMaxCalculator oneRepMaxCalculator = new OneRepMaxCalculator();
        PlateauDetectionService plateauDetectionService = new PlateauDetectionService();

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            Instant anchor = ANCHOR.plusSeconds(dayOffset * 86400L);
            List<GeneratedSession> sessions = generator.generate(anchor);

            for (int e = 0; e < 6; e++) {
                List<BigDecimal> chronologicalBests = bestOneRepMaxPerOccurrence(sessions, e, oneRepMaxCalculator);
                PlateauResult result = plateauDetectionService.detect(chronologicalBests);

                if (e == DemoDataGenerator.PLATEAU_EXERCISE_INDEX) {
                    assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status(),
                            "exercise " + e + " anchor dayOffset=" + dayOffset + " should plateau");
                } else {
                    assertEquals(PlateauStatus.NO_PLATEAU, result.status(),
                            "exercise " + e + " anchor dayOffset=" + dayOffset + " should NOT plateau");
                }
            }
        }
    }

    private List<BigDecimal> bestOneRepMaxPerOccurrence(List<GeneratedSession> sessions, int exerciseIndex, OneRepMaxCalculator calculator) {
        List<BigDecimal> result = new ArrayList<>();
        for (GeneratedSession session : sessions) {
            BigDecimal best = null;
            for (GeneratedSet set : session.sets()) {
                if (set.exerciseIndex() != exerciseIndex) {
                    continue;
                }
                OneRepMaxResult oneRepMax = calculator.calculate(set.weightKg(), set.reps());
                if (best == null || oneRepMax.best().compareTo(best) > 0) {
                    best = oneRepMax.best();
                }
            }
            if (best != null) {
                result.add(best);
            }
        }
        return result;
    }
}
