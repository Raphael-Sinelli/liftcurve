package com.rsinelli.gymtracker.integration;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ExerciseResourceIT {

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Exercise Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .extract().path("access_token");
    }

    private UUID anyMuscleGroupId() {
        return muscleGroupRepository.listAllOrderedByName().get(0).getId();
    }

    /** Global exercises (owner_id NULL) are never created via the API — only through seed migrations in real usage. */
    private UUID seedGlobalExercise(String name) {
        return QuarkusTransaction.requiringNew().call(() -> {
            MuscleGroupEntity muscleGroup = muscleGroupRepository.listAllOrderedByName().get(0);
            ExerciseEntity exercise = new ExerciseEntity();
            exercise.setName(name);
            exercise.setMuscleGroup(muscleGroup);
            exercise.setOwner(null);
            exerciseRepository.persist(exercise);
            return exercise.getId();
        });
    }

    @Test
    void listIncludesGlobalCatalogAndOwnCustomExercises() {
        String globalName = "Supino Reto Global " + System.nanoTime();
        seedGlobalExercise(globalName);

        String token = registerAndGetToken("list");
        String customName = "Rosca Custom " + System.nanoTime();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(customName, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/exercises")
                .then().statusCode(200)
                .body("name", hasItem(globalName))
                .body("name", hasItem(customName));
    }

    @Test
    void customExerciseIsNotVisibleToOtherUsers() {
        String ownerToken = registerAndGetToken("owner");
        String customName = "Exercicio Privado " + System.nanoTime();

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(customName, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201);

        String otherToken = registerAndGetToken("other");
        given()
                .header("Authorization", "Bearer " + otherToken)
                .when().get("/exercises")
                .then().statusCode(200)
                .body("name", not(hasItem(customName)));
    }

    @Test
    void ownerCanUpdateAndDeleteOwnExercise() {
        String token = registerAndGetToken("crud");
        UUID muscleGroupId = anyMuscleGroupId();

        String exerciseId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Original", "muscle_group_id": "%s"}
                        """.formatted(muscleGroupId))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Atualizado", "muscle_group_id": "%s"}
                        """.formatted(muscleGroupId))
                .when().put("/exercises/" + exerciseId)
                .then().statusCode(200)
                .body("name", equalTo("Atualizado"));

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete("/exercises/" + exerciseId)
                .then().statusCode(204);
    }

    @Test
    void editingGlobalExerciseReturns403() {
        String globalId = seedGlobalExercise("Global Imutável " + System.nanoTime()).toString();
        String token = registerAndGetToken("editglobal");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Tentativa", "muscle_group_id": "%s"}
                        """.formatted(anyMuscleGroupId()))
                .when().put("/exercises/" + globalId)
                .then().statusCode(403)
                .body("error.code", equalTo("NOT_EXERCISE_OWNER"));
    }

    @Test
    void editingAnotherUsersCustomExerciseReturns404() {
        String ownerToken = registerAndGetToken("owner2");
        String customName = "Alheio " + System.nanoTime();

        String exerciseId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(customName, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id");

        String otherToken = registerAndGetToken("other2");
        given()
                .header("Authorization", "Bearer " + otherToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Tentativa", "muscle_group_id": "%s"}
                        """.formatted(anyMuscleGroupId()))
                .when().put("/exercises/" + exerciseId)
                .then().statusCode(404)
                .body("error.code", equalTo("EXERCISE_NOT_FOUND"));
    }

    @Test
    void createWithNonexistentMuscleGroupReturns400() {
        String token = registerAndGetToken("badgroup");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Exercicio", "muscle_group_id": "%s"}
                        """.formatted(UUID.randomUUID()))
                .when().post("/exercises")
                .then().statusCode(400)
                .body("error.code", equalTo("MUSCLE_GROUP_NOT_FOUND"));
    }

    @Test
    void createWithBlankNameReturnsValidationError() {
        String token = registerAndGetToken("blankname");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "", "muscle_group_id": "%s"}
                        """.formatted(anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(400)
                .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    /**
     * Sprint 3 added session_sets.exercise_id (no cascade) as a second FK referencing exercises,
     * alongside routine_exercises. Both trigger the same EXERCISE_IN_USE/409 catch-and-translate
     * in ExerciseService.delete() — this covers the session-set-FK source specifically, since the
     * existing coverage (RoutineResourceIT#deletingExerciseInUseByRoutineReturns409) only exercises
     * the routine-FK source.
     */
    @Test
    void deletingExerciseInUseBySessionSetReturns409() {
        String token = registerAndGetToken("inusesession");
        String exerciseName = "Em Uso Sessao " + System.nanoTime();

        String exerciseId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(exerciseName, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id");

        String sessionId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/workout-sessions")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 60, "reps": 10}
                        """.formatted(exerciseId))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete("/exercises/" + exerciseId)
                .then().statusCode(409)
                .body("error.code", equalTo("EXERCISE_IN_USE"));
    }
}
