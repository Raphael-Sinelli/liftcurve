package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SecurityHeadersIT {

    @Test
    void responsesIncludeSecurityHeaders() {
        given()
        .when()
            .get("/muscle-groups")
        .then()
            .statusCode(200)
            .header("X-Content-Type-Options", equalTo("nosniff"))
            .header("X-Frame-Options", equalTo("DENY"))
            .header("Referrer-Policy", equalTo("strict-origin-when-cross-origin"))
            .header("Strict-Transport-Security", equalTo("max-age=31536000; includeSubDomains"));
    }
}
