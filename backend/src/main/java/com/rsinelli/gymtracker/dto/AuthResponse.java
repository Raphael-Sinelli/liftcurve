package com.rsinelli.gymtracker.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
