package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;

public record PlateauResult(PlateauStatus status, BigDecimal currentMax, String suggestion) {
}
