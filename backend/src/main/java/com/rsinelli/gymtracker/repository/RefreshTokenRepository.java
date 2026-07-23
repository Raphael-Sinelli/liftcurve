package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RefreshTokenEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepositoryBase<RefreshTokenEntity, UUID> {

    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }
}
