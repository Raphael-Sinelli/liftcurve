package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class PlateauDetectionService {

    private static final int MIN_SESSIONS_REQUIRED = 4;
    private static final int STREAK_THRESHOLD = 3;
    private static final String DELOAD_SUGGESTION =
            "Considere reduzir a carga em ~10% por 1 semana (deload).";

    public PlateauResult detect(List<BigDecimal> chronological1RmBests) {
        if (chronological1RmBests.size() < MIN_SESSIONS_REQUIRED) {
            return new PlateauResult(PlateauStatus.INSUFFICIENT_DATA, null, null);
        }

        BigDecimal runningMax = chronological1RmBests.get(0);
        int streak = 0;
        for (int i = 1; i < chronological1RmBests.size(); i++) {
            BigDecimal current = chronological1RmBests.get(i);
            if (current.compareTo(runningMax) > 0) {
                runningMax = current;
                streak = 0;
            } else {
                streak++;
            }
        }

        if (streak >= STREAK_THRESHOLD) {
            return new PlateauResult(PlateauStatus.PLATEAU_DETECTED, runningMax, DELOAD_SUGGESTION);
        }
        return new PlateauResult(PlateauStatus.NO_PLATEAU, runningMax, null);
    }
}
