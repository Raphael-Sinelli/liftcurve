package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.SetVolumeInput;
import com.rsinelli.gymtracker.service.VolumeBucketKey;
import com.rsinelli.gymtracker.service.VolumeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCalculatorTest {

    private final VolumeCalculator calculator = new VolumeCalculator();

    private static final UUID CHEST = UUID.randomUUID();
    private static final UUID BACK = UUID.randomUUID();

    private LocalDate anyMonday() {
        return LocalDate.of(2026, 3, 15).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Instant atUtc(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZoneOffset.UTC).toInstant();
    }

    @Test
    void singleSetContributesRepsTimesWeightToItsBucket() {
        LocalDate monday = anyMonday();
        SetVolumeInput input = new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(List.of(input));

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("1000"), result.get(key));
    }

    @Test
    void multipleSetsSameMuscleGroupSameWeekSumCorrectly() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("50"), 8, CHEST, atUtc(monday.plusDays(2), 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("1400"), result.get(key));
    }

    @Test
    void differentMuscleGroupsStayInSeparateBuckets() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("100"), 10, BACK, atUtc(monday, 10)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(monday, 0))));
        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(BACK, atUtc(monday, 0))));
    }

    @Test
    void differentWeeksStayInSeparateBuckets() {
        LocalDate week1Monday = anyMonday();
        LocalDate week2Monday = week1Monday.plusWeeks(1);
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(week1Monday, 9)),
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(week2Monday, 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(week1Monday, 0))));
        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(week2Monday, 0))));
        assertEquals(2, result.size());
    }

    @Test
    void sessionCrossingWeekBoundaryBucketsUnderStartedAtWeek() {
        LocalDate monday = anyMonday();
        // Session "started" Saturday 23:00 UTC of week 1 — must bucket under week 1's Monday,
        // not roll forward just because it's near the end of the week.
        Instant saturdayNight = atUtc(monday.plusDays(5), 23);
        SetVolumeInput input = new SetVolumeInput(new BigDecimal("60"), 12, CHEST, saturdayNight);

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(List.of(input));

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("720"), result.get(key));
    }

    @Test
    void nullMuscleGroupBucketsSeparatelyWithoutThrowing() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("40"), 10, null, atUtc(monday, 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(monday, 0))));
        VolumeBucketKey nullGroupKey = new VolumeBucketKey(null, atUtc(monday, 0));
        assertEquals(new BigDecimal("400"), result.get(nullGroupKey));
        assertNull(new VolumeCalculator().calculate(List.of()).get(nullGroupKey));
        assertTrue(result.containsKey(nullGroupKey));
    }
}
