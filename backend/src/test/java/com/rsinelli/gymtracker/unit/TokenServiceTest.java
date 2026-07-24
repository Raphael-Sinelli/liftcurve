package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.security.TokenService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private final TokenService tokenService =
            new TokenService("Yb6-3fvO5B7NLqSuo9fJ4r_MmCS9rgwIegNlKXqJzgw", 15, 30);

    @Test
    void generatesNonBlankAccessTokenWithThreeJwtSegments() {
        String token = tokenService.generateAccessToken(UUID.randomUUID());

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void generatesUniqueRefreshTokensEachCall() {
        String first = tokenService.generateRefreshToken();
        String second = tokenService.generateRefreshToken();

        assertNotEquals(first, second);
    }

    @Test
    void hashRefreshTokenIsDeterministicForSameInput() {
        String raw = tokenService.generateRefreshToken();

        assertEquals(tokenService.hashRefreshToken(raw), tokenService.hashRefreshToken(raw));
    }

    @Test
    void hashRefreshTokenDiffersForDifferentInput() {
        assertNotEquals(tokenService.hashRefreshToken("token-a"), tokenService.hashRefreshToken("token-b"));
    }

    @Test
    void accessTokenTtlMatchesConfiguredMinutes() {
        assertEquals(15, tokenService.accessTokenTtl().toMinutes());
    }

    @Test
    void constructorRejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new TokenService("too-short-secret", 15, 30));
    }
}
