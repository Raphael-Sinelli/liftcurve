package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RoutineEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineRepository implements PanacheRepositoryBase<RoutineEntity, UUID> {

    public List<RoutineEntity> listOwnedBy(UUID userId) {
        return list("user.id = ?1 order by name", userId);
    }
}
