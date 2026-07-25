package com.rsinelli.gymtracker.dto;

import java.util.List;
import java.util.UUID;

public record ProgressionResponse(UUID exerciseId, String exerciseName, List<ProgressionPointResponse> points) {
}
