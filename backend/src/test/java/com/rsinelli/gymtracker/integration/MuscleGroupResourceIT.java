package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MuscleGroupResourceIT {

    @Test
    void listReturnsAllTenSeededMuscleGroups() {
        given()
                .when().get("/muscle-groups")
                .then().statusCode(200)
                .body("size()", equalTo(10))
                .body("name", hasItems("Peito", "Costas", "Pernas", "Ombros", "Bíceps",
                        "Tríceps", "Core", "Glúteos", "Panturrilha", "Cardio/Outro"));
    }
}
