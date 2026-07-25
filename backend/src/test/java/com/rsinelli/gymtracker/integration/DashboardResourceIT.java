package com.rsinelli.gymtracker.integration;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DashboardResourceIT {

    @Inject
    UserRepository userRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    private final OneRepMaxCalculator oneRepMaxCalculator = new OneRepMaxCalculator();

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Dashboard Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .extract().path("access_token");
    }

    private UUID currentUserId(String token) {
        return UUID.fromString(
                given().header("Authorization", "Bearer " + token)
                        .when().get("/users/me")
                        .then().statusCode(200)
                        .extract().path("id"));
    }

    private UUID anyMuscleGroupId() {
        return UUID.fromString(
                given().when().get("/muscle-groups")
                        .then().statusCode(200)
                        .extract().path("[0].id"));
    }

    private UUID createExercise(String token, String name) {
        return UUID.fromString(given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(name, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id"));
    }

    private UUID seedSession(UUID userId, Instant startedAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = userRepository.findById(userId);
            WorkoutSessionEntity session = new WorkoutSessionEntity();
            session.setUser(user);
            session.setStartedAt(startedAt);
            session.setFinishedAt(startedAt.plusSeconds(3600));
            workoutSessionRepository.persist(session);
            return session.getId();
        });
    }

    private void seedSet(UUID sessionId, UUID exerciseId, BigDecimal weightKg, int reps) {
        QuarkusTransaction.requiringNew().run(() -> {
            WorkoutSessionEntity session = workoutSessionRepository.findById(sessionId);
            ExerciseEntity exercise = exerciseRepository.findById(exerciseId);
            OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(weightKg, reps);

            SessionSetEntity set = new SessionSetEntity();
            set.setSession(session);
            set.setExercise(exercise);
            set.setSetNumber(1);
            set.setWeightKg(weightKg);
            set.setReps(reps);
            set.setEstimated1rmEpley(oneRepMax.epley());
            set.setEstimated1rmBrzycki(oneRepMax.brzycki());
            set.setEstimated1rmBest(oneRepMax.best());
            sessionSetRepository.persist(set);
        });
    }

    private LocalDate anyMonday() {
        return LocalDate.of(2026, 3, 15).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Instant atUtc(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZoneOffset.UTC).toInstant();
    }

    @Test
    void progressionForExerciseWithNoLoggedSetsReturnsEmptyList() {
        String token = registerAndGetToken("nologhistory");
        UUID exerciseId = createExercise(token, "Levantamento Terra " + System.nanoTime());

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + exerciseId)
                .then().statusCode(200)
                .body("points", empty());
    }

    @Test
    void progressionForNonexistentExerciseReturns404() {
        String token = registerAndGetToken("noexercise");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("error.code", equalTo("EXERCISE_NOT_FOUND"));
    }

    @Test
    void progressionReturnsPointsInChronologicalOrder() {
        String token = registerAndGetToken("progression");
        UUID userId = currentUserId(token);
        UUID exerciseId = createExercise(token, "Supino Reto " + System.nanoTime());
        LocalDate monday = anyMonday();

        UUID olderSession = seedSession(userId, atUtc(monday, 9));
        seedSet(olderSession, exerciseId, new BigDecimal("100"), 5);

        UUID newerSession = seedSession(userId, atUtc(monday.plusWeeks(1), 9));
        seedSet(newerSession, exerciseId, new BigDecimal("110"), 5);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + exerciseId)
                .then().statusCode(200)
                .body("points.size()", equalTo(2))
                .body("points[0].estimated_1rm_best", equalTo(116.67f))
                .body("points[1].estimated_1rm_best", equalTo(128.33f));
    }

    @Test
    void volumeGroupsByMuscleGroupAndWeekIncludingWeekBoundaryCrossing() {
        String token = registerAndGetToken("volume");
        UUID userId = currentUserId(token);
        UUID exerciseId = createExercise(token, "Agachamento " + System.nanoTime());
        LocalDate monday = anyMonday();

        UUID mondaySession = seedSession(userId, atUtc(monday, 9));
        seedSet(mondaySession, exerciseId, new BigDecimal("100"), 10);

        // Same week (Saturday night), same exercise/muscle group — must land in the same bucket as the Monday set.
        UUID saturdaySession = seedSession(userId, atUtc(monday.plusDays(5), 23));
        seedSet(saturdaySession, exerciseId, new BigDecimal("50"), 5);

        // Following week — must land in a separate bucket.
        UUID nextWeekSession = seedSession(userId, atUtc(monday.plusWeeks(1), 9));
        seedSet(nextWeekSession, exerciseId, new BigDecimal("60"), 10);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/volume")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("find { it.week_start_utc == '%s' }.total_volume_kg".formatted(atUtc(monday, 0)), equalTo(1250))
                .body("find { it.week_start_utc == '%s' }.total_volume_kg".formatted(atUtc(monday.plusWeeks(1), 0)), equalTo(600));
    }

    @Test
    void plateausListsOnlyExercisesWithActiveStagnation() {
        String token = registerAndGetToken("plateau");
        UUID userId = currentUserId(token);
        UUID stagnantExercise = createExercise(token, "Rosca Direta " + System.nanoTime());
        UUID progressingExercise = createExercise(token, "Desenvolvimento " + System.nanoTime());
        LocalDate monday = anyMonday();

        // 4 sessions, same weight/reps every time — no new PR after the baseline, streak of 3 -> plateau.
        for (int week = 0; week < 4; week++) {
            UUID sessionId = seedSession(userId, atUtc(monday.plusWeeks(week), 9));
            seedSet(sessionId, stagnantExercise, new BigDecimal("40"), 10);
        }

        // 4 sessions, weight increases every time -> always a new PR, never plateaus.
        for (int week = 0; week < 4; week++) {
            UUID sessionId = seedSession(userId, atUtc(monday.plusWeeks(week), 10));
            seedSet(sessionId, progressingExercise, new BigDecimal(String.valueOf(40 + week * 5)), 10);
        }

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/plateaus")
                .then().statusCode(200)
                .body("exercise_id", hasItem(stagnantExercise.toString()))
                .body("exercise_id", not(hasItem(progressingExercise.toString())));
    }
}
