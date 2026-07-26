package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record SessionSetRequest(
        @NotNull UUID exerciseId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weightKg,
        @Min(1) int reps,
        @DecimalMin(value = "1") @DecimalMax(value = "10")
        @Schema(description = "Escala de esforço percebido (RPE), 1 a 10, aceita meio-ponto (ex. 7.5). Opcional.")
        BigDecimal rpe) {
}
