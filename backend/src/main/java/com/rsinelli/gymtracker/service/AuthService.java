package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.AuthResponse;
import com.rsinelli.gymtracker.entity.RefreshTokenEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.RefreshTokenRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.PasswordHasher;
import com.rsinelli.gymtracker.security.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.time.Instant;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    TokenService tokenService;

    @Transactional
    public AuthResponse register(String email, String password, String name) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED", "Este email já está cadastrado.", Response.Status.CONFLICT);
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setName(name.trim());
        userRepository.persist(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS", "Email ou senha inválidos.", Response.Status.UNAUTHORIZED));

        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new ApiException("INVALID_CREDENTIALS", "Email ou senha inválidos.", Response.Status.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException("INVALID_REFRESH_TOKEN", "Refresh token inválido.", Response.Status.UNAUTHORIZED));

        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("INVALID_REFRESH_TOKEN", "Refresh token inválido ou expirado.", Response.Status.UNAUTHORIZED);
        }

        storedToken.setRevokedAt(Instant.now());

        return issueTokens(storedToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private AuthResponse issueTokens(UserEntity user) {
        String accessToken = tokenService.generateAccessToken(user.getId());
        String rawRefreshToken = tokenService.generateRefreshToken();

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(tokenService.hashRefreshToken(rawRefreshToken));
        refreshTokenEntity.setExpiresAt(Instant.now().plus(tokenService.refreshTokenTtl()));
        refreshTokenRepository.persist(refreshTokenEntity);

        return new AuthResponse(accessToken, rawRefreshToken, tokenService.accessTokenTtl().toSeconds());
    }
}
