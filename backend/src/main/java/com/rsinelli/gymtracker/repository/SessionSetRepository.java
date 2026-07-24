package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.SessionSetEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionSetRepository implements PanacheRepositoryBase<SessionSetEntity, UUID> {

    public List<SessionSetEntity> listBySessionOrderedByCreatedAt(UUID sessionId) {
        return list("session.id = ?1 order by createdAt", sessionId);
    }

    public long countBySessionAndExercise(UUID sessionId, UUID exerciseId) {
        return count("session.id = ?1 and exercise.id = ?2", sessionId, exerciseId);
    }

    public List<SessionSetEntity> listByUserAndExercise(UUID userId, UUID exerciseId) {
        return list("session.user.id = ?1 and exercise.id = ?2 order by session.startedAt", userId, exerciseId);
    }

    public List<SessionSetEntity> listByUser(UUID userId) {
        return list("session.user.id = ?1 order by session.startedAt", userId);
    }
}
