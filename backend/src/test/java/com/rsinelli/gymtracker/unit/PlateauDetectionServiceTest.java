package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.PlateauDetectionService;
import com.rsinelli.gymtracker.service.PlateauResult;
import com.rsinelli.gymtracker.service.PlateauStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlateauDetectionServiceTest {

    private final PlateauDetectionService service = new PlateauDetectionService();

    private static List<BigDecimal> bigDecimals(int... values) {
        return java.util.Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void emptyHistoryReturnsInsufficientData() {
        PlateauResult result = service.detect(List.of());

        assertEquals(PlateauStatus.INSUFFICIENT_DATA, result.status());
        assertNull(result.currentMax());
        assertNull(result.suggestion());
    }

    @Test
    void fewerThanFourSessionsReturnsInsufficientData() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100));

        assertEquals(PlateauStatus.INSUFFICIENT_DATA, result.status());
    }

    @Test
    void exactlyThreeStagnantSessionsAfterBaselineTriggersPlateau() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
        assertEquals(BigDecimal.valueOf(100), result.currentMax());
        assertNotNull(result.suggestion());
    }

    @Test
    void streakOfTwoDoesNotTriggerPlateau() {
        // baseline 80 -> new PR 100 (streak resets to 0) -> 95 (streak 1) -> 95 (streak 2, never reaches 3)
        PlateauResult result = service.detect(bigDecimals(80, 100, 95, 95));

        assertEquals(PlateauStatus.NO_PLATEAU, result.status());
        assertEquals(BigDecimal.valueOf(100), result.currentMax());
    }

    @Test
    void newPrInTheMiddleResetsTheStreak() {
        // 100 (baseline) -> 100 (streak 1) -> 100 (streak 2) -> 110 (new PR, streak resets to 0)
        // -> 100 (streak 1) -> 100 (streak 2, final streak is 2, not 5 — does NOT trigger)
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 110, 100, 100));

        assertEquals(PlateauStatus.NO_PLATEAU, result.status());
        assertEquals(BigDecimal.valueOf(110), result.currentMax());
    }

    @Test
    void tiedValuesCountAsStagnantNotAsNewRecord() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
    }

    @Test
    void moreThanThreeConsecutiveStagnantSessionsStillTriggers() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
    }
}
