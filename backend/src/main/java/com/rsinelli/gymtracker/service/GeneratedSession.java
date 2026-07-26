package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GeneratedSession(Instant startedAt, Instant finishedAt, List<GeneratedSet> sets) {
}
