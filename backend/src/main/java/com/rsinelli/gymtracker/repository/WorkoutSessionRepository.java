package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WorkoutSessionRepository implements PanacheRepositoryBase<WorkoutSessionEntity, UUID> {

    public List<WorkoutSessionEntity> listOwnedBy(UUID userId) {
        return list("user.id = ?1 order by startedAt desc", userId);
    }

    public Optional<WorkoutSessionEntity> findActiveByUser(UUID userId) {
        return find("user.id = ?1 and finishedAt is null", userId).firstResultOptional();
    }
}
