package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExerciseRepository implements PanacheRepositoryBase<ExerciseEntity, UUID> {

    public List<ExerciseEntity> listVisibleTo(UUID userId) {
        return list("owner is null or owner.id = ?1 order by name", userId);
    }

    public Optional<ExerciseEntity> findVisibleTo(UUID exerciseId, UUID userId) {
        return find("id = ?1 and (owner is null or owner.id = ?2)", exerciseId, userId).firstResultOptional();
    }
}
