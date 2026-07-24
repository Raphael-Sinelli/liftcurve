package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UserResourceIT {

    @ConfigProperty(name = "gymtracker.jwt.secret")
    String jwtSecret;

    @Test
    void meWithoutTokenReturns401InContractShape() {
        given()
                .when().get("/users/me")
                .then().statusCode(401)
                .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void meWithValidTokenReturns200WithProfile() {
        String email = "profile+" + System.nanoTime() + "@example.com";

        String accessToken = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Profile Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .extract().path("access_token");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get("/users/me")
                .then().statusCode(200)
                .body("email", equalTo(email))
                .body("name", equalTo("Profile Test"))
                .body("id", notNullValue());
    }

    @Test
    void meWithExpiredTokenReturns401InContractShape() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String expiredToken = Jwt.claims()
                .issuer("gym-progress-tracker")
                .subject(UUID.randomUUID().toString())
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5)))
                .sign(key);

        given()
                .header("Authorization", "Bearer " + expiredToken)
                .when().get("/users/me")
                .then().statusCode(401)
                .body("error.code", equalTo("INVALID_TOKEN"));
    }

    @Test
    void meWithMalformedTokenReturns401InContractShape() {
        given()
                .header("Authorization", "Bearer not-a-real-jwt")
                .when().get("/users/me")
                .then().statusCode(401)
                .body("error.code", equalTo("INVALID_TOKEN"));
    }
}
