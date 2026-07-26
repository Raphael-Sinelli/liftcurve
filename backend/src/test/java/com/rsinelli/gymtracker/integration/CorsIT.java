package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CorsIT {

    @Test
    void allowedOriginReceivesCorsHeader() {
        given()
            .header("Origin", "http://localhost:5173")
        .when()
            .get("/muscle-groups")
        .then()
            .statusCode(200)
            .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"))
            // A app só autentica via Bearer token (nunca cookies), então este header
            // precisa ficar explicitamente false. Sem quarkus.http.cors.access-control-allow-credentials=false
            // no application.properties, o Quarkus 3.37.3 computa "originMatched" (true)
            // como default quando cors.origins é uma lista concreta — ver application.properties.
            .header("Access-Control-Allow-Credentials", equalTo("false"));
    }

    @Test
    void disallowedOriginDoesNotReceiveCorsHeader() {
        // Quarkus's built-in CORSFilter (quarkus-vertx-http 3.37.3) rejects requests
        // from an origin outside quarkus.http.cors.origins with a 403, rather than
        // simply omitting the CORS header on an otherwise-200 response — confirmed
        // by decompiling io.quarkus.vertx.http.runtime.cors.CORSFilter, which sets
        // HttpServerResponse.setStatusCode(403) when the Origin doesn't match.
        given()
            .header("Origin", "https://evil.example.com")
        .when()
            .get("/muscle-groups")
        .then()
            .statusCode(403)
            .header("Access-Control-Allow-Origin", nullValue());
    }
}
