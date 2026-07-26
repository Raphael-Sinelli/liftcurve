package com.rsinelli.gymtracker.integration;

import com.rsinelli.gymtracker.seed.DemoSeeder;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(DemoSeederIT.EnableDemoSeedProfile.class)
class DemoSeederIT {

    public static class EnableDemoSeedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gymtracker.seed.demo.enabled", "true");
        }
    }

    @Test
    void demoAccountCanLoginWithDocumentedCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(DemoSeeder.DEMO_EMAIL, DemoSeeder.DEMO_PASSWORD))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("access_token", not(equalTo(null)));
    }

    @Test
    void demoAccountHasPopulatedDashboard() {
        String token = loginAsDemo();

        String plateauExerciseId = given()
                .header("Authorization", "Bearer " + token)
                .when().get("/exercises")
                .then().statusCode(200)
                .extract().path("find { it.name == 'Rosca Direta' }.id");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/plateaus")
                .then().statusCode(200)
                .body("exercise_id", hasItem(plateauExerciseId));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + plateauExerciseId)
                .then().statusCode(200)
                .body("points.size()", greaterThanOrEqualTo(24));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/volume")
                .then().statusCode(200)
                .body("size()", greaterThan(0));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/routines")
                .then().statusCode(200)
                .body("size()", equalTo(2));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/workout-sessions")
                .then().statusCode(200)
                .body("size()", equalTo(48));
    }

    private String loginAsDemo() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(DemoSeeder.DEMO_EMAIL, DemoSeeder.DEMO_PASSWORD))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("access_token");
    }
}
