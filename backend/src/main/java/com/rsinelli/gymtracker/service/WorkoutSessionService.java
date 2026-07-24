package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.SessionSetResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionSummaryResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WorkoutSessionService {

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    RoutineRepository routineRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    @Inject
    OneRepMaxCalculator oneRepMaxCalculator;

    public List<WorkoutSessionSummaryResponse> list() {
        return workoutSessionRepository.listOwnedBy(currentUser.getId()).stream()
                .map(session -> new WorkoutSessionSummaryResponse(
                        session.getId(),
                        session.getRoutine() != null ? session.getRoutine().getId() : null,
                        session.getStartedAt(),
                        session.getFinishedAt(),
                        sessionSetRepository.listBySessionOrderedByCreatedAt(session.getId()).size()))
                .toList();
    }

    public WorkoutSessionResponse get(UUID sessionId) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        return toDetailResponse(session);
    }

    @Transactional
    public WorkoutSessionResponse create(UUID routineId, String notes) {
        UUID userId = currentUser.getId();

        if (workoutSessionRepository.findActiveByUser(userId).isPresent()) {
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "Já existe uma sessão de treino em andamento.", Response.Status.CONFLICT);
        }

        RoutineEntity routine = null;
        if (routineId != null) {
            routine = routineRepository.findByIdOptional(routineId)
                    .filter(r -> r.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ApiException("INVALID_ROUTINE_REFERENCE",
                            "Rotina não encontrada ou não pertence a este usuário.", Response.Status.BAD_REQUEST));
        }

        UserEntity user = userRepository.findById(userId);

        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setUser(user);
        session.setRoutine(routine);
        session.setNotes(notes);
        workoutSessionRepository.persist(session);

        return toDetailResponse(session);
    }

    @Transactional
    public WorkoutSessionResponse finish(UUID sessionId, String notes) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        requireActive(session);

        session.setFinishedAt(Instant.now());
        if (notes != null) {
            session.setNotes(notes);
        }

        return toDetailResponse(session);
    }

    @Transactional
    public SessionSetResponse addSet(UUID sessionId, UUID exerciseId, BigDecimal weightKg, int reps, BigDecimal rpe) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        requireActive(session);

        ExerciseEntity exercise = exerciseRepository.findVisibleTo(exerciseId, currentUser.getId())
                .orElseThrow(() -> new ApiException("INVALID_EXERCISE_REFERENCE",
                        "Exercício não encontrado ou não visível para este usuário.", Response.Status.BAD_REQUEST));

        int setNumber = (int) sessionSetRepository.countBySessionAndExercise(sessionId, exerciseId) + 1;
        OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(weightKg, reps);

        SessionSetEntity set = new SessionSetEntity();
        set.setSession(session);
        set.setExercise(exercise);
        set.setSetNumber(setNumber);
        set.setWeightKg(weightKg);
        set.setReps(reps);
        set.setRpe(rpe);
        set.setEstimated1rmEpley(oneRepMax.epley());
        set.setEstimated1rmBrzycki(oneRepMax.brzycki());
        set.setEstimated1rmBest(oneRepMax.best());
        sessionSetRepository.persist(set);

        return toSetResponse(set);
    }

    private void requireActive(WorkoutSessionEntity session) {
        if (session.getFinishedAt() != null) {
            throw new ApiException("SESSION_ALREADY_FINISHED",
                    "Esta sessão já foi finalizada.", Response.Status.CONFLICT);
        }
    }

    /** Sessões não têm catálogo compartilhado — sempre 404 se não for do usuário atual. */
    private WorkoutSessionEntity findOwnedOrThrow(UUID sessionId) {
        WorkoutSessionEntity session = workoutSessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND));

        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND);
        }
        return session;
    }

    private WorkoutSessionResponse toDetailResponse(WorkoutSessionEntity session) {
        List<SessionSetResponse> sets = sessionSetRepository.listBySessionOrderedByCreatedAt(session.getId()).stream()
                .map(this::toSetResponse)
                .toList();

        return new WorkoutSessionResponse(
                session.getId(),
                session.getRoutine() != null ? session.getRoutine().getId() : null,
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getNotes(),
                sets);
    }

    private SessionSetResponse toSetResponse(SessionSetEntity set) {
        return new SessionSetResponse(
                set.getId(),
                set.getExercise().getId(),
                set.getExercise().getName(),
                set.getSetNumber(),
                set.getWeightKg(),
                set.getReps(),
                set.getRpe(),
                set.getEstimated1rmEpley(),
                set.getEstimated1rmBrzycki(),
                set.getEstimated1rmBest(),
                set.getCreatedAt());
    }
}
