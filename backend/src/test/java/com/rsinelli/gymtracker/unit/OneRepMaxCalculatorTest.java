package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneRepMaxCalculatorTest {

    private final OneRepMaxCalculator calculator = new OneRepMaxCalculator();

    @Test
    void repsOfOneMakesBothFormulasApproximatelyEqualToWeight() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("50"), 1);

        assertEquals(new BigDecimal("51.67"), result.epley());
        assertEquals(new BigDecimal("50.00"), result.brzycki());
        assertEquals(new BigDecimal("51.67"), result.best());
    }

    @Test
    void lowRepsEpleyWinsOverBrzycki() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 3);

        assertEquals(new BigDecimal("110.00"), result.epley());
        assertEquals(new BigDecimal("105.88"), result.brzycki());
        assertEquals(new BigDecimal("110.00"), result.best());
    }

    @Test
    void higherRepsBrzyckiCanWinOverEpley() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 15);

        assertEquals(new BigDecimal("150.00"), result.epley());
        assertEquals(new BigDecimal("163.64"), result.brzycki());
        assertEquals(new BigDecimal("163.64"), result.best());
    }

    @Test
    void repsAboveTwelveStillCalculatesWithoutError() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("80"), 20);

        assertTrue(result.epley().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.best().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void repsAtThirtySevenGuardsBrzyckiToNull() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 37);

        assertEquals(new BigDecimal("223.33"), result.epley());
        assertNull(result.brzycki());
        assertEquals(new BigDecimal("223.33"), result.best());
    }

    @Test
    void repsWellAboveThirtySevenAlsoGuardsBrzyckiToNull() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 50);

        assertEquals(new BigDecimal("266.67"), result.epley());
        assertNull(result.brzycki());
        assertEquals(new BigDecimal("266.67"), result.best());
    }

    @Test
    void repsJustBelowThirtySevenStillComputesBrzycki() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 36);

        assertEquals(new BigDecimal("336.00"), result.epley());
        assertEquals(new BigDecimal("3600.00"), result.brzycki());
        assertEquals(new BigDecimal("3600.00"), result.best());
    }
}
