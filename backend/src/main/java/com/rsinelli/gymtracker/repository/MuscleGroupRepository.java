package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MuscleGroupRepository implements PanacheRepositoryBase<MuscleGroupEntity, UUID> {

    public List<MuscleGroupEntity> listAllOrderedByName() {
        return list("ORDER BY name");
    }

    public Optional<MuscleGroupEntity> findByName(String name) {
        return find("name", name).firstResultOptional();
    }
}
