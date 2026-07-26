package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Mínimo 8 caracteres, máximo 72 (limite do BCrypt).")
        String password,
        @NotBlank @Size(max = 120) String name) {
}
