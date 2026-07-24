package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.RoutineExerciseItem;
import com.rsinelli.gymtracker.dto.RoutineExerciseResponse;
import com.rsinelli.gymtracker.dto.RoutineResponse;
import com.rsinelli.gymtracker.dto.RoutineSummaryResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineService {

    @Inject
    RoutineRepository routineRepository;

    @Inject
    RoutineExerciseRepository routineExerciseRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    public List<RoutineSummaryResponse> list() {
        return routineRepository.listOwnedBy(currentUser.getId()).stream()
                .map(routine -> new RoutineSummaryResponse(
                        routine.getId(),
                        routine.getName(),
                        routine.getDescription(),
                        routine.getCreatedAt(),
                        routineExerciseRepository.listByRoutineOrderedByIndex(routine.getId()).size()))
                .toList();
    }

    public RoutineResponse get(UUID routineId) {
        RoutineEntity routine = findOwnedOrThrow(routineId);
        return toDetailResponse(routine);
    }

    @Transactional
    public RoutineResponse create(String name, String description, List<RoutineExerciseItem> items) {
        UserEntity owner = userRepository.findById(currentUser.getId());

        RoutineEntity routine = new RoutineEntity();
        routine.setUser(owner);
        routine.setName(name.trim());
        routine.setDescription(description);
        routineRepository.persist(routine);

        persistRoutineExercises(routine, items);

        return toDetailResponse(routine);
    }

    @Transactional
    public RoutineResponse update(UUID routineId, String name, String description, List<RoutineExerciseItem> items) {
        RoutineEntity routine = findOwnedOrThrow(routineId);

        routine.setName(name.trim());
        routine.setDescription(description);

        routineExerciseRepository.deleteByRoutineId(routineId);
        persistRoutineExercises(routine, items);

        return toDetailResponse(routine);
    }

    @Transactional
    public void delete(UUID routineId) {
        findOwnedOrThrow(routineId);
        routineRepository.deleteById(routineId);
    }

    private void persistRoutineExercises(RoutineEntity routine, List<RoutineExerciseItem> items) {
        for (int i = 0; i < items.size(); i++) {
            RoutineExerciseItem item = items.get(i);
            ExerciseEntity exercise = exerciseRepository.findVisibleTo(item.exerciseId(), currentUser.getId())
                    .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.BAD_REQUEST));

            RoutineExerciseEntity routineExercise = new RoutineExerciseEntity();
            routineExercise.setRoutine(routine);
            routineExercise.setExercise(exercise);
            routineExercise.setOrderIndex(i);
            routineExercise.setPlannedSets(item.plannedSets());
            routineExercise.setPlannedReps(item.plannedReps());
            routineExercise.setPlannedLoadKg(item.plannedLoadKg());
            routineExerciseRepository.persist(routineExercise);
        }
    }

    /** Routines have no shared catalog — any routine not owned by the current user is a 404, never 403. */
    private RoutineEntity findOwnedOrThrow(UUID routineId) {
        RoutineEntity routine = routineRepository.findByIdOptional(routineId)
                .orElseThrow(() -> new ApiException("ROUTINE_NOT_FOUND", "Rotina não encontrada.", Response.Status.NOT_FOUND));

        if (!routine.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException("ROUTINE_NOT_FOUND", "Rotina não encontrada.", Response.Status.NOT_FOUND);
        }
        return routine;
    }

    private RoutineResponse toDetailResponse(RoutineEntity routine) {
        List<RoutineExerciseResponse> exercises = routineExerciseRepository.listByRoutineOrderedByIndex(routine.getId()).stream()
                .map(re -> new RoutineExerciseResponse(
                        re.getExercise().getId(),
                        re.getExercise().getName(),
                        re.getOrderIndex(),
                        re.getPlannedSets(),
                        re.getPlannedReps(),
                        re.getPlannedLoadKg()))
                .toList();

        return new RoutineResponse(routine.getId(), routine.getName(), routine.getDescription(), routine.getCreatedAt(), exercises);
    }
}
