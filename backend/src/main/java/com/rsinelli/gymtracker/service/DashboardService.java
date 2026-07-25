package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.PlateauAlertResponse;
import com.rsinelli.gymtracker.dto.ProgressionPointResponse;
import com.rsinelli.gymtracker.dto.ProgressionResponse;
import com.rsinelli.gymtracker.dto.VolumeBucketResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DashboardService {

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    CurrentUser currentUser;

    @Inject
    VolumeCalculator volumeCalculator;

    @Inject
    PlateauDetectionService plateauDetectionService;

    public ProgressionResponse progression(UUID exerciseId) {
        ExerciseEntity exercise = exerciseRepository.findVisibleTo(exerciseId, currentUser.getId())
                .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND));

        List<SessionSetEntity> sets = sessionSetRepository.listByUserAndExercise(currentUser.getId(), exerciseId);
        List<ProgressionPointResponse> points = chronologicalSessionBests(sets).stream()
                .map(sb -> new ProgressionPointResponse(sb.sessionStartedAt(), sb.best()))
                .toList();

        return new ProgressionResponse(exercise.getId(), exercise.getName(), points);
    }

    public List<VolumeBucketResponse> volume() {
        List<SessionSetEntity> sets = sessionSetRepository.listByUser(currentUser.getId());

        List<SetVolumeInput> inputs = sets.stream()
                .map(set -> new SetVolumeInput(
                        set.getWeightKg(),
                        set.getReps(),
                        // muscle_group_id is NOT NULL on today's schema, but the calculator's
                        // contract is intentionally defensive — see design Decision 4.
                        set.getExercise().getMuscleGroup() != null ? set.getExercise().getMuscleGroup().getId() : null,
                        set.getSession().getStartedAt()))
                .toList();

        Map<VolumeBucketKey, BigDecimal> totals = volumeCalculator.calculate(inputs);

        return totals.entrySet().stream()
                .map(entry -> new VolumeBucketResponse(
                        entry.getKey().muscleGroupId(),
                        entry.getKey().weekStartUtc(),
                        entry.getValue()))
                .sorted(Comparator.comparing(VolumeBucketResponse::weekStartUtc))
                .toList();
    }

    public List<PlateauAlertResponse> plateaus() {
        List<SessionSetEntity> sets = sessionSetRepository.listByUser(currentUser.getId());

        Map<UUID, List<SessionSetEntity>> byExercise = sets.stream()
                .collect(Collectors.groupingBy(set -> set.getExercise().getId()));

        List<PlateauAlertResponse> alerts = new ArrayList<>();
        for (Map.Entry<UUID, List<SessionSetEntity>> entry : byExercise.entrySet()) {
            List<BigDecimal> chronologicalBests = chronologicalSessionBests(entry.getValue()).stream()
                    .map(SessionBest::best)
                    .toList();

            PlateauResult result = plateauDetectionService.detect(chronologicalBests);
            if (result.status() == PlateauStatus.PLATEAU_DETECTED) {
                String exerciseName = entry.getValue().get(0).getExercise().getName();
                alerts.add(new PlateauAlertResponse(entry.getKey(), exerciseName, result.currentMax(), result.suggestion()));
            }
        }
        return alerts;
    }

    /** Reduz várias séries pra 1 valor por sessão: o maior estimated_1rm_best entre as séries daquele exercício naquela sessão, em ordem cronológica. */
    private List<SessionBest> chronologicalSessionBests(List<SessionSetEntity> sets) {
        Map<UUID, Instant> startedAtBySession = new LinkedHashMap<>();
        Map<UUID, BigDecimal> bestBySession = new LinkedHashMap<>();
        for (SessionSetEntity set : sets) {
            UUID sessionId = set.getSession().getId();
            startedAtBySession.put(sessionId, set.getSession().getStartedAt());
            bestBySession.merge(sessionId, set.getEstimated1rmBest(), BigDecimal::max);
        }
        return bestBySession.entrySet().stream()
                .map(e -> new SessionBest(startedAtBySession.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(SessionBest::sessionStartedAt))
                .toList();
    }

    private record SessionBest(Instant sessionStartedAt, BigDecimal best) {
    }
}
