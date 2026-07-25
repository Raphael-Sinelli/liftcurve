package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class WorkoutSessionResourceIT {

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Session Test"}
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

    private String startSession(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/workout-sessions")
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void startLogSetsFinishFullFlowWithPerExerciseSetNumbering() {
        String token = registerAndGetToken("flow");
        String exerciseA = createExercise(token, "Supino " + System.nanoTime());
        String exerciseB = createExercise(token, "Agachamento " + System.nanoTime());
        String sessionId = startSession(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 100, "reps": 8}
                        """.formatted(exerciseA))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(201)
                .body("set_number", equalTo(1))
                .body("estimated_1rm_epley", notNullValue());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 105, "reps": 6}
                        """.formatted(exerciseA))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(201)
                .body("set_number", equalTo(2));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 80, "reps": 10}
                        """.formatted(exerciseB))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(201)
                .body("set_number", equalTo(1));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/workout-sessions/" + sessionId)
                .then().statusCode(200)
                .body("sets.size()", equalTo(3))
                .body("finished_at", nullValue());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"notes": "Treino concluído"}
                        """)
                .when().patch("/workout-sessions/" + sessionId)
                .then().statusCode(200)
                .body("finished_at", notNullValue())
                .body("notes", equalTo("Treino concluído"));
    }

    @Test
    void creatingSecondActiveSessionReturns409() {
        String token = registerAndGetToken("active");
        startSession(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/workout-sessions")
                .then().statusCode(409)
                .body("error.code", equalTo("SESSION_ALREADY_ACTIVE"));
    }

    @Test
    void finishingAlreadyFinishedSessionReturns409() {
        String token = registerAndGetToken("finished");
        String sessionId = startSession(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().patch("/workout-sessions/" + sessionId)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().patch("/workout-sessions/" + sessionId)
                .then().statusCode(409)
                .body("error.code", equalTo("SESSION_ALREADY_FINISHED"));
    }

    @Test
    void addingSetToFinishedSessionReturns409() {
        String token = registerAndGetToken("finishedset");
        String exerciseId = createExercise(token, "Remada " + System.nanoTime());
        String sessionId = startSession(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().patch("/workout-sessions/" + sessionId)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 50, "reps": 10}
                        """.formatted(exerciseId))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(409)
                .body("error.code", equalTo("SESSION_ALREADY_FINISHED"));
    }

    @Test
    void invalidRoutineReferenceReturns400() {
        String token = registerAndGetToken("badroutine");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"routine_id": "%s"}
                        """.formatted(UUID.randomUUID()))
                .when().post("/workout-sessions")
                .then().statusCode(400)
                .body("error.code", equalTo("INVALID_ROUTINE_REFERENCE"));
    }

    @Test
    void invalidExerciseReferenceReturns400() {
        String token = registerAndGetToken("badexercise");
        String sessionId = startSession(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"exercise_id": "%s", "weight_kg": 50, "reps": 10}
                        """.formatted(UUID.randomUUID()))
                .when().post("/workout-sessions/" + sessionId + "/sets")
                .then().statusCode(400)
                .body("error.code", equalTo("INVALID_EXERCISE_REFERENCE"));
    }

    @Test
    void anotherUsersSessionReturns404() {
        String ownerToken = registerAndGetToken("sowner");
        String sessionId = startSession(ownerToken);

        String otherToken = registerAndGetToken("sother");
        given()
                .header("Authorization", "Bearer " + otherToken)
                .when().get("/workout-sessions/" + sessionId)
                .then().statusCode(404)
                .body("error.code", equalTo("SESSION_NOT_FOUND"));
    }
}
