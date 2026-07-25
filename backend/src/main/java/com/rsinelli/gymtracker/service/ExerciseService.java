package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.ExerciseResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ExerciseService {

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    public List<ExerciseResponse> list() {
        return exerciseRepository.listVisibleTo(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExerciseResponse create(String name, UUID muscleGroupId) {
        MuscleGroupEntity muscleGroup = requireMuscleGroup(muscleGroupId);
        UserEntity owner = userRepository.findById(currentUser.getId());

        ExerciseEntity exercise = new ExerciseEntity();
        exercise.setName(name.trim());
        exercise.setMuscleGroup(muscleGroup);
        exercise.setOwner(owner);
        exerciseRepository.persist(exercise);

        return toResponse(exercise);
    }

    @Transactional
    public ExerciseResponse update(UUID exerciseId, String name, UUID muscleGroupId) {
        ExerciseEntity exercise = findOwnedOrThrow(exerciseId);
        MuscleGroupEntity muscleGroup = requireMuscleGroup(muscleGroupId);

        exercise.setName(name.trim());
        exercise.setMuscleGroup(muscleGroup);

        return toResponse(exercise);
    }

    @Transactional
    public void delete(UUID exerciseId) {
        findOwnedOrThrow(exerciseId);

        try {
            exerciseRepository.deleteById(exerciseId);
            // Panache's deleteById resolves to entityManager.remove(), which Hibernate defers to
            // flush/commit time rather than executing the DELETE immediately — without this flush,
            // an FK violation would surface after this try/catch has already returned, past the
            // @Transactional interceptor's commit, and escape as an uncaught 500 instead of 409.
            exerciseRepository.flush();
        } catch (PersistenceException e) {
            // In this Hibernate version, ConstraintViolationException is itself a PersistenceException
            // (ConstraintViolationException -> JDBCException -> HibernateException -> PersistenceException),
            // so catching PersistenceException alone already covers both the direct-throw and wrapped-cause
            // shapes; a separate ConstraintViolationException catch alternative would be a compile error
            // (subclass of an already-caught alternative).
            Throwable cause = e;
            while (cause != null && !(cause instanceof ConstraintViolationException)) {
                cause = cause.getCause();
            }
            if (cause instanceof ConstraintViolationException cve && "23503".equals(cve.getSQLState())) {
                throw new ApiException("EXERCISE_IN_USE", "Este exercício está em uso e não pode ser excluído.", Response.Status.CONFLICT);
            }
            throw e;
        }
    }

    private MuscleGroupEntity requireMuscleGroup(UUID muscleGroupId) {
        return muscleGroupRepository.findByIdOptional(muscleGroupId)
                .orElseThrow(() -> new ApiException("MUSCLE_GROUP_NOT_FOUND", "Grupo muscular não encontrado.", Response.Status.BAD_REQUEST));
    }

    /**
     * 403 for a global exercise (existence is already public via the catalog) vs. 404 for
     * another user's custom exercise (existence isn't otherwise knowable to the requester) —
     * see Sprint 2 design Decision 2.
     */
    private ExerciseEntity findOwnedOrThrow(UUID exerciseId) {
        ExerciseEntity exercise = exerciseRepository.findByIdOptional(exerciseId)
                .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND));

        if (exercise.getOwner() == null) {
            throw new ApiException("NOT_EXERCISE_OWNER", "Exercícios do catálogo global não podem ser editados.", Response.Status.FORBIDDEN);
        }
        if (!exercise.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND);
        }
        return exercise;
    }

    private ExerciseResponse toResponse(ExerciseEntity exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getMuscleGroup().getId(),
                exercise.getMuscleGroup().getName(),
                exercise.getOwner() != null ? exercise.getOwner().getId() : null,
                exercise.getCreatedAt());
    }
}
