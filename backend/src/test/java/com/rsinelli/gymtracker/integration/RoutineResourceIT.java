package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RoutineResourceIT {

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Routine Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .extract().path("access_token");
    }

    private UUID anyMuscleGroupId() {
        return UUID.fromString(
                given().when().get("/muscle-groups")
                        .then().statusCode(200)
                        .extract().path("[0].id"));
    }

    private String createExercise(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(name, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void createGetUpdateDeleteFullFlowWithOrderedExercises() {
        String token = registerAndGetToken("flow");
        String exerciseA = createExercise(token, "Supino " + System.nanoTime());
        String exerciseB = createExercise(token, "Agachamento " + System.nanoTime());

        String routineId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Treino A", "description": "Peito e pernas",
                         "exercises": [
                           {"exercise_id": "%s", "planned_sets": 3, "planned_reps": 10, "planned_load_kg": 60},
                           {"exercise_id": "%s", "planned_sets": 4, "planned_reps": 8, "planned_load_kg": 80}
                         ]}
                        """.formatted(exerciseA, exerciseB))
                .when().post("/routines")
                .then().statusCode(201)
                .body("exercises[0].order_index", equalTo(0))
                .body("exercises[1].order_index", equalTo(1))
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/routines/" + routineId)
                .then().statusCode(200)
                .body("exercises.size()", equalTo(2));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Treino A Revisado", "description": "Só peito",
                         "exercises": [
                           {"exercise_id": "%s", "planned_sets": 5, "planned_reps": 5, "planned_load_kg": 100}
                         ]}
                        """.formatted(exerciseA))
                .when().put("/routines/" + routineId)
                .then().statusCode(200)
                .body("name", equalTo("Treino A Revisado"))
                .body("exercises.size()", equalTo(1))
                .body("exercises[0].planned_sets", equalTo(5));

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete("/routines/" + routineId)
                .then().statusCode(204);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/routines/" + routineId)
                .then().statusCode(404)
                .body("error.code", equalTo("ROUTINE_NOT_FOUND"));
    }

    @Test
    void anotherUsersRoutineReturns404() {
        String ownerToken = registerAndGetToken("rowner");
        String exerciseId = createExercise(ownerToken, "Exercicio Rotina " + System.nanoTime());

        String routineId = given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Privada", "exercises": [
                           {"exercise_id": "%s", "planned_sets": 3, "planned_reps": 10}
                         ]}
                        """.formatted(exerciseId))
                .when().post("/routines")
                .then().statusCode(201)
                .extract().path("id");

        String otherToken = registerAndGetToken("rother");
        given()
                .header("Authorization", "Bearer " + otherToken)
                .when().get("/routines/" + routineId)
                .then().statusCode(404)
                .body("error.code", equalTo("ROUTINE_NOT_FOUND"));
    }

    @Test
    void creatingRoutineWithoutExercisesReturnsValidationError() {
        String token = registerAndGetToken("noexerc");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Vazia", "exercises": []}
                        """)
                .when().post("/routines")
                .then().statusCode(400)
                .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void deletingExerciseInUseByRoutineReturns409() {
        String token = registerAndGetToken("inuse");
        String exerciseId = createExercise(token, "Em Uso " + System.nanoTime());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "Usa Exercicio", "exercises": [
                           {"exercise_id": "%s", "planned_sets": 3, "planned_reps": 10}
                         ]}
                        """.formatted(exerciseId))
                .when().post("/routines")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete("/exercises/" + exerciseId)
                .then().statusCode(409)
                .body("error.code", equalTo("EXERCISE_IN_USE"));
    }
}
