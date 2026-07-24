package com.rsinelli.gymtracker.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class TokenService {

    private static final String ISSUER = "gym-progress-tracker";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String jwtSecret;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    @Inject
    public TokenService(
            @ConfigProperty(name = "gymtracker.jwt.secret") String jwtSecret,
            @ConfigProperty(name = "gymtracker.jwt.access-token-ttl-minutes") long accessTokenTtlMinutes,
            @ConfigProperty(name = "gymtracker.jwt.refresh-token-ttl-days") long refreshTokenTtlDays) {
        // HS256 needs a key of at least 256 bits; fail fast at boot rather than
        // signing tokens the first request happens to trigger.
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("gymtracker.jwt.secret must be at least 32 bytes for HS256");
        }
        this.jwtSecret = jwtSecret;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(UUID userId) {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwt.claims()
                .issuer(ISSUER)
                .subject(userId.toString())
                .expiresIn(accessTokenTtl())
                .sign(key);
    }

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public Duration refreshTokenTtl() {
        return Duration.ofDays(refreshTokenTtlDays);
    }

    /** Opaque bearer secret, not a JWT — see hashRefreshToken() for why it's hashed differently than passwords. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt: this token is already 256 bits of random entropy so it
     * needs no slow key-derivation, and BCrypt would silently truncate the base64
     * string past 72 bytes anyway.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
