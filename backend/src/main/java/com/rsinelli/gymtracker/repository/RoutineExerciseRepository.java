package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineExerciseRepository implements PanacheRepositoryBase<RoutineExerciseEntity, UUID> {

    public List<RoutineExerciseEntity> listByRoutineOrderedByIndex(UUID routineId) {
        return list("routine.id = ?1 order by orderIndex", routineId);
    }

    public void deleteByRoutineId(UUID routineId) {
        delete("routine.id = ?1", routineId);
    }
}
