package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AuthResourceIT {

    @Test
    void registerLoginRefreshLogoutFullFlow() {
        String email = "athlete+" + System.nanoTime() + "@example.com";

        String refreshToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Athlete Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .body("access_token", notNullValue())
                .body("refresh_token", notNullValue())
                .extract().path("refresh_token");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123"}
                        """.formatted(email))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("access_token", notNullValue());

        String newRefreshToken = given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(refreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(200)
                .body("refresh_token", notNullValue())
                .extract().path("refresh_token");

        given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(refreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(401)
                .body("error.code", equalTo("INVALID_REFRESH_TOKEN"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(newRefreshToken))
                .when().post("/auth/logout")
                .then().statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(newRefreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(401)
                .body("error.code", equalTo("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void registerRejectsInvalidPayloadWithValidationErrorContract() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "not-an-email", "password": "short", "name": ""}
                        """)
                .when().post("/auth/register")
                .then().statusCode(400)
                .body("error.code", equalTo("VALIDATION_ERROR"))
                .body("error.details", notNullValue());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        String email = "dup+" + System.nanoTime() + "@example.com";
        String body = """
                {"email": "%s", "password": "supersecret123", "name": "Dup Test"}
                """.formatted(email);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/auth/register")
                .then().statusCode(201);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/auth/register")
                .then().statusCode(409)
                .body("error.code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginRejectsWrongPassword() {
        String email = "wrongpass+" + System.nanoTime() + "@example.com";

        given().contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Wrong Pass"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "totally-wrong"}
                        """.formatted(email))
                .when().post("/auth/login")
                .then().statusCode(401)
                .body("error.code", equalTo("INVALID_CREDENTIALS"));
    }
}
