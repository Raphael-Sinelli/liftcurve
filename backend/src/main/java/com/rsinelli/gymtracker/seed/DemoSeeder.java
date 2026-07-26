package com.rsinelli.gymtracker.seed;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import com.rsinelli.gymtracker.repository.RoutineExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.service.AuthService;
import com.rsinelli.gymtracker.service.DemoDataGenerator;
import com.rsinelli.gymtracker.service.GeneratedSession;
import com.rsinelli.gymtracker.service.GeneratedSet;
import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DemoSeeder {

    public static final String DEMO_EMAIL = "demo@gymtracker.app";
    public static final String DEMO_PASSWORD = "DemoGymTracker2026!";
    public static final String DEMO_NAME = "Conta Demo";

    private static final Logger LOG = Logger.getLogger(DemoSeeder.class);

    @ConfigProperty(name = "gymtracker.seed.demo.enabled", defaultValue = "false")
    boolean seedEnabled;

    @Inject
    UserRepository userRepository;

    @Inject
    AuthService authService;

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    RoutineRepository routineRepository;

    @Inject
    RoutineExerciseRepository routineExerciseRepository;

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    OneRepMaxCalculator oneRepMaxCalculator;

    // DemoDataGenerator (Task 2) is a plain stateless class with no CDI bean-defining
    // annotation — DemoDataGeneratorTest instantiates it the same way (`new DemoDataGenerator()`).
    // @Inject here would fail Arc's build-time validation (UnsatisfiedResolutionException),
    // caught only by `./mvnw verify` (full Quarkus build), not by `./mvnw test` (surefire only).
    private final DemoDataGenerator demoDataGenerator = new DemoDataGenerator();

    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled) {
            return;
        }
        if (userRepository.findByEmail(DEMO_EMAIL).isPresent()) {
            LOG.info("Conta demo já existe, seed ignorado.");
            return;
        }
        seed();
        LOG.infof("Conta demo semeada com sucesso: %s", DEMO_EMAIL);
    }

    @Transactional
    void seed() {
        authService.register(DEMO_EMAIL, DEMO_PASSWORD, DEMO_NAME);
        UserEntity demoUser = userRepository.findByEmail(DEMO_EMAIL).orElseThrow();

        List<ExerciseEntity> exercises = createGlobalExercises();
        List<RoutineEntity> routines = createDemoRoutines(demoUser, exercises);
        seedWorkoutHistory(demoUser, exercises, routines.get(0), routines.get(1));
    }

    private List<ExerciseEntity> createGlobalExercises() {
        List<ExerciseEntity> exercises = new ArrayList<>();
        for (int i = 0; i < DemoDataGenerator.EXERCISE_NAMES.length; i++) {
            String muscleGroupName = DemoDataGenerator.EXERCISE_MUSCLE_GROUPS[i];
            MuscleGroupEntity muscleGroup = muscleGroupRepository.findByName(muscleGroupName)
                    .orElseThrow(() -> new IllegalStateException(
                            "Grupo muscular '" + muscleGroupName + "' não encontrado — a migration V3 rodou?"));

            ExerciseEntity exercise = new ExerciseEntity();
            exercise.setName(DemoDataGenerator.EXERCISE_NAMES[i]);
            exercise.setMuscleGroup(muscleGroup);
            exercise.setOwner(null);
            exerciseRepository.persist(exercise);
            exercises.add(exercise);
        }
        return exercises;
    }

    private List<RoutineEntity> createDemoRoutines(UserEntity owner, List<ExerciseEntity> exercises) {
        RoutineEntity routineA = createRoutine(owner, "Treino A — Peito/Pernas/Costas",
                "Treino de força, foco em grandes compostos.",
                List.of(exercises.get(0), exercises.get(1), exercises.get(2)));
        RoutineEntity routineB = createRoutine(owner, "Treino B — Ombros/Bíceps/Costas",
                "Treino complementar, foco em membros superiores.",
                List.of(exercises.get(3), exercises.get(4), exercises.get(5)));
        return List.of(routineA, routineB);
    }

    private RoutineEntity createRoutine(UserEntity owner, String name, String description, List<ExerciseEntity> items) {
        RoutineEntity routine = new RoutineEntity();
        routine.setUser(owner);
        routine.setName(name);
        routine.setDescription(description);
        routineRepository.persist(routine);

        for (int i = 0; i < items.size(); i++) {
            RoutineExerciseEntity routineExercise = new RoutineExerciseEntity();
            routineExercise.setRoutine(routine);
            routineExercise.setExercise(items.get(i));
            routineExercise.setOrderIndex(i);
            routineExercise.setPlannedSets(4);
            routineExercise.setPlannedReps(8);
            routineExercise.setPlannedLoadKg(null);
            routineExerciseRepository.persist(routineExercise);
        }
        return routine;
    }

    private void seedWorkoutHistory(UserEntity demoUser, List<ExerciseEntity> exercises,
                                     RoutineEntity routineA, RoutineEntity routineB) {
        List<GeneratedSession> generatedSessions = demoDataGenerator.generate(Instant.now());

        for (GeneratedSession generatedSession : generatedSessions) {
            WorkoutSessionEntity session = new WorkoutSessionEntity();
            session.setUser(demoUser);
            session.setStartedAt(generatedSession.startedAt());
            session.setFinishedAt(generatedSession.finishedAt());
            session.setRoutine(isGroupA(generatedSession) ? routineA : routineB);
            workoutSessionRepository.persist(session);

            Map<Integer, Integer> setNumberByExercise = new HashMap<>();
            for (GeneratedSet generatedSet : generatedSession.sets()) {
                ExerciseEntity exercise = exercises.get(generatedSet.exerciseIndex());
                int setNumber = setNumberByExercise.merge(generatedSet.exerciseIndex(), 1, Integer::sum);
                OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(generatedSet.weightKg(), generatedSet.reps());

                SessionSetEntity set = new SessionSetEntity();
                set.setSession(session);
                set.setExercise(exercise);
                set.setSetNumber(setNumber);
                set.setWeightKg(generatedSet.weightKg());
                set.setReps(generatedSet.reps());
                set.setRpe(generatedSet.rpe());
                set.setEstimated1rmEpley(oneRepMax.epley());
                set.setEstimated1rmBrzycki(oneRepMax.brzycki());
                set.setEstimated1rmBest(oneRepMax.best());
                sessionSetRepository.persist(set);
            }
        }
    }

    /**
     * Every set in a generated session belongs to the same exercise group by construction
     * (see DemoDataGenerator.GROUP_A / GROUP_B), so checking the first set's exercise index
     * is enough to tell which of the two seeded routines this session belongs to.
     */
    private boolean isGroupA(GeneratedSession generatedSession) {
        int firstExerciseIndex = generatedSession.sets().get(0).exerciseIndex();
        return firstExerciseIndex == 0 || firstExerciseIndex == 1 || firstExerciseIndex == 2;
    }
}
