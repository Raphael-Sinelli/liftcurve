# Sprint 2 — Exercícios + Rotinas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first authenticated slice of the API — JWT verification actually wired into endpoints (not just issued) — plus CRUD for the exercise catalog (global + user-owned custom exercises) and routine templates (`routines` + ordered `routine_exercises`), with ownership/authorization rules enforced and proven against real Postgres.

**Architecture:** Same layering as Sprint 0-1 (Resource → Service/BO → Panache Repository → JPA entity), extended with a `security.CurrentUser` request-scoped bean that resolves the authenticated user's UUID from the verified JWT (`sub` claim), and three new `ExceptionMapper`s translating Quarkus Security's exception types into the project's `{"error":{...}}` contract. `RoutineEntity`/`RoutineExerciseEntity` are modeled as independent, repository-managed entities — no `@OneToMany` cascade collection — to avoid a known Hibernate flush-ordering hazard when a routine's exercise list is replaced on update.

**Tech Stack:** Same as Sprint 0-1 (Java 21, Quarkus 3.37.3, PostgreSQL 16, Flyway, SmallRye JWT, JUnit 5, Testcontainers, RestAssured). No new Maven dependencies — `quarkus-smallrye-jwt` already provides `@Authenticated`, `io.quarkus.security.*` exception types, and `JsonWebToken`; `io.quarkus.narayana.jta.QuarkusTransaction` (used by tests to seed global fixtures) ships with `quarkus-arc`/`quarkus-hibernate-orm-panache`, already present.

**Design reference:** `docs/superpowers/specs/2026-07-24-sprint2-exercicios-rotinas-design.md` — read it before implementing Task 1; it documents the reasoning behind every non-obvious decision below (the `mp.jwt.*` vs `smallrye.jwt.*` bug, the three exception-mapper types, the 403-vs-404 asymmetry, the delete-conflict handling, and why routines avoid JPA cascade collections).

## Global Constraints

- DB columns/tables: `snake_case`. Java code: `camelCase`. JSON over the wire: `snake_case` — enforced via `quarkus.jackson.property-naming-strategy=SNAKE_CASE`, never `@JsonProperty` per field.
- All timestamps: `TIMESTAMPTZ` in Postgres, `java.time.Instant` in Java — never `TIMESTAMP`/`LocalDateTime`.
- All primary keys: `UUID`, generated in Java via `UUID.randomUUID()` (not a DB or Hibernate generator).
- Every error response body: `{"error": {"code": ..., "message": ..., "status": ..., "details": [...]}}`.
- Integration tests use a real Postgres via Testcontainers — never mock the database. Files ending `IT.java` run under `mvn verify` (failsafe); files ending `Test.java` run under `mvn test` (surefire) and must need zero Docker.
- Protect endpoints with `@Authenticated`, never `@RolesAllowed` — JWTs in this project carry only a `sub` claim (user UUID) and `iss`, no roles/groups.
- Services inject `security.CurrentUser` to resolve "who is asking" — authorization checks (ownership) live in the Service layer, never in the Resource.
- Ownership-check asymmetry (see design Decision 2): denying access to a resource whose existence is *already public* (global exercise catalog) → **403**. Denying access to a resource whose existence is *not otherwise knowable* to the requester (another user's custom exercise, another user's routine) → **404**.
- Any new `ExceptionMapper` for a Quarkus Security exception type must be checked against JAX-RS mapper-resolution/priority quirks before assuming a bare `@Provider` is enough — verify with an integration test, don't assume.
- `org.hibernate.exception.ConstraintViolationException` (DB constraint) vs `jakarta.validation.ConstraintViolationException` (Bean Validation) share a simple name — always double check which one is imported. Match FK violations via `getSQLState().equals("23503")`, not by string-matching constraint names.
- Migrations: one table per file, `Vn__snake_case_description.sql`, `quarkus.hibernate-orm.database.generation=validate` (schema is migration-owned).
- Do not `git push` — commits are created locally only; the user reviews and pushes.

---

### Task 1: JWT Authentication Wiring

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/security/CurrentUser.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/UnauthorizedExceptionMapper.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/AuthenticationFailedExceptionMapper.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/ForbiddenExceptionMapper.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/UserResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/UserResource.java`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/UserResourceIT.java`

**Interfaces:**
- Produces: `CurrentUser.getId(): UUID` — every later Service (`ExerciseService`, `RoutineService`) injects this to resolve the authenticated user.
- Produces: `GET /users/me` (`@Authenticated`) — first protected endpoint, proves the whole auth chain works end-to-end.

- [ ] **Step 1: Fix the JWT verification property names in `application.properties`**

The existing config has a real, never-exercised bug: `mp.jwt.verify.secretkey` is not a real MicroProfile Config property name (MP-JWT's own property for symmetric keys is different, and MicroProfile Config silently ignores unknown properties — no error, it just never worked). The correct SmallRye-specific property is `smallrye.jwt.verify.secretkey`, and the verification algorithm must be pinned to `HS256` explicitly (default is RS256, which would reject every token `TokenService` signs).

Replace:
```properties
mp.jwt.verify.issuer=gym-progress-tracker
mp.jwt.verify.secretkey=${gymtracker.jwt.secret}
```
with:
```properties
mp.jwt.verify.issuer=gym-progress-tracker
smallrye.jwt.verify.secretkey=${gymtracker.jwt.secret}
smallrye.jwt.verify.algorithm=HS256
```

- [ ] **Step 2: Create `security/CurrentUser.java`**

```java
package com.rsinelli.gymtracker.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    public UUID getId() {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new IllegalStateException(
                    "CurrentUser.getId() called outside an authenticated request — is @Authenticated missing on the endpoint?");
        }
        return UUID.fromString(subject);
    }
}
```

- [ ] **Step 3: Create the three security exception mappers**

Three distinct Quarkus Security exception types can surface on an `@Authenticated` endpoint, and they need different mapper treatment — verified by decompiling the actual `quarkus-security`/`quarkus-rest` jars during Sprint 2 design.

`exception/UnauthorizedExceptionMapper.java` (no credentials at all):
```java
package com.rsinelli.gymtracker.exception;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Thrown when an anonymous request (no Authorization header) hits an
 * {@code @Authenticated} endpoint. This happens inside normal JAX-RS request
 * handling, so a plain {@code @Provider} is enough — contrast with
 * {@link AuthenticationFailedExceptionMapper}, which needs an explicit priority.
 */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("UNAUTHORIZED", "Autenticação necessária.",
                        Response.Status.UNAUTHORIZED.getStatusCode()))
                .build();
    }
}
```

`exception/AuthenticationFailedExceptionMapper.java` (credentials present but invalid — expired, malformed, bad signature):
```java
package com.rsinelli.gymtracker.exception;

import io.quarkus.security.AuthenticationFailedException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Quarkus's JWT auth mechanism raises this before normal JAX-RS exception-mapper
 * resolution runs, so a bare {@code @Provider} can be silently skipped in some
 * Quarkus versions — {@code @Priority(Priorities.AUTHENTICATION)} is required for
 * this mapper to actually participate (see quarkusio/quarkus#25732, #29896).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFailedExceptionMapper implements ExceptionMapper<AuthenticationFailedException> {

    @Override
    public Response toResponse(AuthenticationFailedException exception) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INVALID_TOKEN", "Token de acesso inválido ou expirado.",
                        Response.Status.UNAUTHORIZED.getStatusCode()))
                .build();
    }
}
```

`exception/ForbiddenExceptionMapper.java` (403 — not exercised by this sprint's endpoints since none use `@RolesAllowed`, but included for completeness/future use):
```java
package com.rsinelli.gymtracker.exception;

import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("FORBIDDEN", "Acesso negado.",
                        Response.Status.FORBIDDEN.getStatusCode()))
                .build();
    }
}
```

- [ ] **Step 4: Create `dto/UserResponse.java` and `resource/UserResource.java`**

```java
package com.rsinelli.gymtracker.dto;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name) {
}
```

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.UserResponse;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/users")
@Tag(name = "Users", description = "Perfil do usuário autenticado")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserRepository userRepository;

    @GET
    @Path("/me")
    @Operation(summary = "Retorna o perfil do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Perfil do usuário")
    @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
    public Response me() {
        UserEntity user = userRepository.findByIdOptional(currentUser.getId())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado.", Response.Status.NOT_FOUND));
        return Response.ok(new UserResponse(user.getId(), user.getEmail(), user.getName())).build();
    }
}
```

- [ ] **Step 5: Create `integration/UserResourceIT.java`**

```java
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
```

**Troubleshooting note for the implementer:** if `meWithoutTokenReturns401InContractShape` fails by returning a generic 500 body instead of the `UNAUTHORIZED` shape, that means `UnauthorizedException` is hitting the same mapper-resolution problem as `AuthenticationFailedException` in this specific Quarkus version — add `@Priority(Priorities.AUTHENTICATION)` to `UnauthorizedExceptionMapper` too. Don't treat that as an implementation mistake; it's a legitimate, version-dependent JAX-RS resolution quirk, same class of bug fixed at the end of Sprint 1.

- [ ] **Step 6: Run `./mvnw test` then `./mvnw verify`, fix anything red, commit** (`feat(backend): wire JWT authentication into protected endpoints`)

---

### Task 2: `muscle_groups` Catalog

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_muscle_groups.sql`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/MuscleGroupEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/MuscleGroupRepository.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/MuscleGroupResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/MuscleGroupResource.java`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/MuscleGroupResourceIT.java`

**Interfaces:**
- Produces: `MuscleGroupRepository.listAllOrderedByName(): List<MuscleGroupEntity>` — consumed later by `ExerciseResourceIT`/`RoutineResourceIT` test helpers to pick a valid `muscle_group_id`.
- Produces: `GET /muscle-groups` (unauthenticated, public reference data).

- [ ] **Step 1: Create migration `V3__create_muscle_groups.sql`**

`gen_random_uuid()` is a PostgreSQL 13+ built-in (no extension needed), confirmed safe for the project's `postgres:16-alpine` image.

```sql
CREATE TABLE muscle_groups (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

INSERT INTO muscle_groups (id, name) VALUES
    (gen_random_uuid(), 'Peito'),
    (gen_random_uuid(), 'Costas'),
    (gen_random_uuid(), 'Pernas'),
    (gen_random_uuid(), 'Ombros'),
    (gen_random_uuid(), 'Bíceps'),
    (gen_random_uuid(), 'Tríceps'),
    (gen_random_uuid(), 'Core'),
    (gen_random_uuid(), 'Glúteos'),
    (gen_random_uuid(), 'Panturrilha'),
    (gen_random_uuid(), 'Cardio/Outro');
```

- [ ] **Step 2: Create `entity/MuscleGroupEntity.java`**

```java
package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "muscle_groups")
public class MuscleGroupEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String name;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

- [ ] **Step 3: Create `repository/MuscleGroupRepository.java`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MuscleGroupRepository implements PanacheRepositoryBase<MuscleGroupEntity, UUID> {

    public List<MuscleGroupEntity> listAllOrderedByName() {
        return list("ORDER BY name");
    }
}
```

- [ ] **Step 4: Create `dto/MuscleGroupResponse.java` and `resource/MuscleGroupResource.java`**

```java
package com.rsinelli.gymtracker.dto;

import java.util.UUID;

public record MuscleGroupResponse(UUID id, String name) {
}
```

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.MuscleGroupResponse;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/muscle-groups")
@Tag(name = "MuscleGroups", description = "Catálogo fixo de grupos musculares")
@Produces(MediaType.APPLICATION_JSON)
public class MuscleGroupResource {

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @GET
    @Operation(summary = "Lista todos os grupos musculares")
    @APIResponse(responseCode = "200", description = "Lista de grupos musculares")
    public Response list() {
        List<MuscleGroupResponse> response = muscleGroupRepository.listAllOrderedByName().stream()
                .map(mg -> new MuscleGroupResponse(mg.getId(), mg.getName()))
                .toList();
        return Response.ok(response).build();
    }
}
```

- [ ] **Step 5: Create `integration/MuscleGroupResourceIT.java`**

```java
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
```

- [ ] **Step 6: Run `./mvnw verify`, fix anything red, commit** (`feat(backend): add muscle groups catalog with seed data`)

---

### Task 3: `exercises` Schema

No dedicated test in this task — schema-only, exercised by `ExerciseResourceIT` in Task 5 (same precedent as Sprint 0-1's schema-only tasks).

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_exercises.sql`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/ExerciseEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/ExerciseRepository.java`

**Interfaces:**
- Produces: `ExerciseRepository.listVisibleTo(UUID userId): List<ExerciseEntity>` — consumed by `ExerciseService.list()` in Task 4.
- Produces: `ExerciseEntity` with nullable `owner` (`null` = global catalog) — consumed by Task 4's ownership logic and Task 6's `RoutineExerciseEntity` FK.

- [ ] **Step 1: Create migration `V4__create_exercises.sql`**

```sql
CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    muscle_group_id UUID NOT NULL REFERENCES muscle_groups (id),
    owner_id UUID REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_exercises_owner_id ON exercises (owner_id);
CREATE INDEX idx_exercises_muscle_group_id ON exercises (muscle_group_id);
```

- [ ] **Step 2: Create `entity/ExerciseEntity.java`**

```java
package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exercises")
public class ExerciseEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muscle_group_id", nullable = false)
    private MuscleGroupEntity muscleGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MuscleGroupEntity getMuscleGroup() {
        return muscleGroup;
    }

    public void setMuscleGroup(MuscleGroupEntity muscleGroup) {
        this.muscleGroup = muscleGroup;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 3: Create `repository/ExerciseRepository.java`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ExerciseRepository implements PanacheRepositoryBase<ExerciseEntity, UUID> {

    public List<ExerciseEntity> listVisibleTo(UUID userId) {
        return list("owner is null or owner.id = ?1 order by name", userId);
    }
}
```

- [ ] **Step 4: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add exercises schema and entity`)

---

### Task 4: Exercises Service + Resource

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/ExerciseRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/ExerciseResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/ExerciseService.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/ExerciseResource.java`

**Interfaces:**
- Produces: `GET/POST/PUT/DELETE /exercises` (all `@Authenticated`), error codes `MUSCLE_GROUP_NOT_FOUND` (400), `NOT_EXERCISE_OWNER` (403), `EXERCISE_NOT_FOUND` (404), `EXERCISE_IN_USE` (409) — consumed by `ExerciseResourceIT` (Task 5) and `RoutineResourceIT` (Task 8, cross-feature 409 case).

- [ ] **Step 1: Create `dto/ExerciseRequest.java` and `dto/ExerciseResponse.java`**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID muscleGroupId) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record ExerciseResponse(
        UUID id,
        String name,
        UUID muscleGroupId,
        String muscleGroupName,
        UUID ownerId,
        Instant createdAt) {
}
```

- [ ] **Step 2: Create `service/ExerciseService.java`**

The delete-conflict handling walks the exception's cause chain rather than assuming a single wrapping shape: whether Hibernate throws `org.hibernate.exception.ConstraintViolationException` directly or wraps it in `jakarta.persistence.PersistenceException` depends on the exact JPA call path, so the code checks both.

```java
package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.ExerciseResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.MuscleGroupEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.MuscleGroupRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ExerciseService {

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    MuscleGroupRepository muscleGroupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    public List<ExerciseResponse> list() {
        return exerciseRepository.listVisibleTo(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExerciseResponse create(String name, UUID muscleGroupId) {
        MuscleGroupEntity muscleGroup = requireMuscleGroup(muscleGroupId);
        UserEntity owner = userRepository.findById(currentUser.getId());

        ExerciseEntity exercise = new ExerciseEntity();
        exercise.setName(name.trim());
        exercise.setMuscleGroup(muscleGroup);
        exercise.setOwner(owner);
        exerciseRepository.persist(exercise);

        return toResponse(exercise);
    }

    @Transactional
    public ExerciseResponse update(UUID exerciseId, String name, UUID muscleGroupId) {
        ExerciseEntity exercise = findOwnedOrThrow(exerciseId);
        MuscleGroupEntity muscleGroup = requireMuscleGroup(muscleGroupId);

        exercise.setName(name.trim());
        exercise.setMuscleGroup(muscleGroup);

        return toResponse(exercise);
    }

    @Transactional
    public void delete(UUID exerciseId) {
        findOwnedOrThrow(exerciseId);

        try {
            exerciseRepository.deleteById(exerciseId);
        } catch (PersistenceException | ConstraintViolationException e) {
            Throwable cause = e;
            while (cause != null && !(cause instanceof ConstraintViolationException)) {
                cause = cause.getCause();
            }
            if (cause instanceof ConstraintViolationException cve && "23503".equals(cve.getSQLState())) {
                throw new ApiException("EXERCISE_IN_USE", "Este exercício está em uso em uma ou mais rotinas.", Response.Status.CONFLICT);
            }
            throw e;
        }
    }

    private MuscleGroupEntity requireMuscleGroup(UUID muscleGroupId) {
        return muscleGroupRepository.findByIdOptional(muscleGroupId)
                .orElseThrow(() -> new ApiException("MUSCLE_GROUP_NOT_FOUND", "Grupo muscular não encontrado.", Response.Status.BAD_REQUEST));
    }

    /**
     * 403 for a global exercise (existence is already public via the catalog) vs. 404 for
     * another user's custom exercise (existence isn't otherwise knowable to the requester) —
     * see Sprint 2 design Decision 2.
     */
    private ExerciseEntity findOwnedOrThrow(UUID exerciseId) {
        ExerciseEntity exercise = exerciseRepository.findByIdOptional(exerciseId)
                .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND));

        if (exercise.getOwner() == null) {
            throw new ApiException("NOT_EXERCISE_OWNER", "Exercícios do catálogo global não podem ser editados.", Response.Status.FORBIDDEN);
        }
        if (!exercise.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND);
        }
        return exercise;
    }

    private ExerciseResponse toResponse(ExerciseEntity exercise) {
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getMuscleGroup().getId(),
                exercise.getMuscleGroup().getName(),
                exercise.getOwner() != null ? exercise.getOwner().getId() : null,
                exercise.getCreatedAt());
    }
}
```

- [ ] **Step 3: Create `resource/ExerciseResource.java`**

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.ExerciseRequest;
import com.rsinelli.gymtracker.dto.ExerciseResponse;
import com.rsinelli.gymtracker.service.ExerciseService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/exercises")
@Tag(name = "Exercises", description = "Catálogo global e exercícios custom por usuário")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ExerciseResource {

    @Inject
    ExerciseService exerciseService;

    @GET
    @Operation(summary = "Lista exercícios visíveis ao usuário (catálogo global + custom próprios)")
    @APIResponse(responseCode = "200", description = "Lista de exercícios")
    public Response list() {
        List<ExerciseResponse> response = exerciseService.list();
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria um exercício custom pertencente ao usuário autenticado")
    @APIResponse(responseCode = "201", description = "Exercício criado")
    @APIResponse(responseCode = "400", description = "Payload inválido ou grupo muscular inexistente")
    public Response create(@Valid ExerciseRequest request) {
        ExerciseResponse response = exerciseService.create(request.name(), request.muscleGroupId());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Atualiza um exercício custom do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Exercício atualizado")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser editado")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid ExerciseRequest request) {
        ExerciseResponse response = exerciseService.update(id, request.name(), request.muscleGroupId());
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove um exercício custom do usuário autenticado")
    @APIResponse(responseCode = "204", description = "Exercício removido")
    @APIResponse(responseCode = "403", description = "Exercício é do catálogo global, não pode ser removido")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Exercício está em uso em uma ou mais rotinas")
    public Response delete(@PathParam("id") UUID id) {
        exerciseService.delete(id);
        return Response.noContent().build();
    }
}
```

- [ ] **Step 4: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add exercises CRUD with ownership rules`)

---

### Task 5: `ExerciseResourceIT`

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/ExerciseResourceIT.java`

Does **not** cover the `EXERCISE_IN_USE` 409 case — `routine_exercises` doesn't exist until Task 6/7, so that scenario is deferred to Task 8 (`RoutineResourceIT.deletingExerciseInUseByRoutineReturns409`).

- [ ] **Step 1: Create `integration/ExerciseResourceIT.java`**

```java
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
}
```

- [ ] **Step 2: Run `./mvnw verify`, fix anything red, commit** (`test(backend): cover exercise ownership and visibility rules`)

---

### Task 6: `routines` + `routine_exercises` Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_routines.sql`
- Create: `backend/src/main/resources/db/migration/V6__create_routine_exercises.sql`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/RoutineEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/RoutineExerciseEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/RoutineRepository.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/RoutineExerciseRepository.java`

**Interfaces:**
- Produces: `RoutineRepository.listOwnedBy(UUID userId): List<RoutineEntity>` — consumed by `RoutineService.list()` (Task 7).
- Produces: `RoutineExerciseRepository.listByRoutineOrderedByIndex(UUID routineId): List<RoutineExerciseEntity>` and `.deleteByRoutineId(UUID routineId): void` — consumed by `RoutineService` (Task 7).
- No `@OneToMany` collection on `RoutineEntity` — deliberate, see design Decision 4 (avoids a Hibernate flush-ordering hazard on delete-and-reinsert).

- [ ] **Step 1: Create migration `V5__create_routines.sql`**

```sql
CREATE TABLE routines (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_routines_user_id ON routines (user_id);
```

- [ ] **Step 2: Create migration `V6__create_routine_exercises.sql`**

No unique constraint on `(routine_id, order_index)` — deliberately omitted. `order_index` is server-derived and the whole set is recreated on update, so a transient duplicate during delete-then-insert would violate such a constraint even with correct application-level ordering (the same Hibernate flush-ordering hazard Decision 4 avoids by not using a cascade collection).

```sql
CREATE TABLE routine_exercises (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL REFERENCES routines (id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    order_index INT NOT NULL,
    planned_sets INT NOT NULL,
    planned_reps INT NOT NULL,
    planned_load_kg NUMERIC
);

CREATE INDEX idx_routine_exercises_routine_id ON routine_exercises (routine_id);
```

- [ ] **Step 3: Create `entity/RoutineEntity.java`**

```java
package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routines")
public class RoutineEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 4: Create `entity/RoutineExerciseEntity.java`**

```java
package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
public class RoutineExerciseEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id", nullable = false)
    private RoutineEntity routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "planned_sets", nullable = false)
    private int plannedSets;

    @Column(name = "planned_reps", nullable = false)
    private int plannedReps;

    @Column(name = "planned_load_kg")
    private BigDecimal plannedLoadKg;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RoutineEntity getRoutine() {
        return routine;
    }

    public void setRoutine(RoutineEntity routine) {
        this.routine = routine;
    }

    public ExerciseEntity getExercise() {
        return exercise;
    }

    public void setExercise(ExerciseEntity exercise) {
        this.exercise = exercise;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public int getPlannedSets() {
        return plannedSets;
    }

    public void setPlannedSets(int plannedSets) {
        this.plannedSets = plannedSets;
    }

    public int getPlannedReps() {
        return plannedReps;
    }

    public void setPlannedReps(int plannedReps) {
        this.plannedReps = plannedReps;
    }

    public BigDecimal getPlannedLoadKg() {
        return plannedLoadKg;
    }

    public void setPlannedLoadKg(BigDecimal plannedLoadKg) {
        this.plannedLoadKg = plannedLoadKg;
    }
}
```

- [ ] **Step 5: Create `repository/RoutineRepository.java` and `repository/RoutineExerciseRepository.java`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RoutineEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineRepository implements PanacheRepositoryBase<RoutineEntity, UUID> {

    public List<RoutineEntity> listOwnedBy(UUID userId) {
        return list("user.id = ?1 order by name", userId);
    }
}
```

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineExerciseRepository implements PanacheRepositoryBase<RoutineExerciseEntity, UUID> {

    public List<RoutineExerciseEntity> listByRoutineOrderedByIndex(UUID routineId) {
        return list("routine.id = ?1 order by orderIndex", routineId);
    }

    public void deleteByRoutineId(UUID routineId) {
        delete("routine.id = ?1", routineId);
    }
}
```

- [ ] **Step 6: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add routines and routine_exercises schema`)

---

### Task 7: Routines Service + Resource

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RoutineExerciseItem.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RoutineRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RoutineExerciseResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RoutineResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RoutineSummaryResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/RoutineService.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/RoutineResource.java`

**Interfaces:**
- Produces: `GET/POST/PUT/DELETE /routines` + `GET /routines/{id}` (all `@Authenticated`), error codes `ROUTINE_NOT_FOUND` (404), `EXERCISE_NOT_FOUND` (400, when a routine references a nonexistent exercise) — consumed by `RoutineResourceIT` (Task 8).

- [ ] **Step 1: Create the DTOs**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RoutineExerciseItem(
        @NotNull UUID exerciseId,
        @Min(1) int plannedSets,
        @Min(1) int plannedReps,
        @DecimalMin(value = "0", inclusive = true) BigDecimal plannedLoadKg) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoutineRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotEmpty @Valid List<RoutineExerciseItem> exercises) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RoutineExerciseResponse(
        UUID exerciseId,
        String exerciseName,
        int orderIndex,
        int plannedSets,
        int plannedReps,
        BigDecimal plannedLoadKg) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutineResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        List<RoutineExerciseResponse> exercises) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record RoutineSummaryResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        int exerciseCount) {
}
```

- [ ] **Step 2: Create `service/RoutineService.java`**

`order_index` is derived purely from array position, never accepted as client input. `update()` deletes the routine's existing `routine_exercises` rows (`deleteByRoutineId` — immediate bulk `DELETE`, not a managed cascade) and re-inserts the new list inside the same `@Transactional` method — the delete-and-reinsert pattern this codebase intentionally avoids doing via JPA cascade collections (see Task 6, Decision 4).

```java
package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.RoutineExerciseItem;
import com.rsinelli.gymtracker.dto.RoutineExerciseResponse;
import com.rsinelli.gymtracker.dto.RoutineResponse;
import com.rsinelli.gymtracker.dto.RoutineSummaryResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.RoutineExerciseEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoutineService {

    @Inject
    RoutineRepository routineRepository;

    @Inject
    RoutineExerciseRepository routineExerciseRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    public List<RoutineSummaryResponse> list() {
        return routineRepository.listOwnedBy(currentUser.getId()).stream()
                .map(routine -> new RoutineSummaryResponse(
                        routine.getId(),
                        routine.getName(),
                        routine.getDescription(),
                        routine.getCreatedAt(),
                        routineExerciseRepository.listByRoutineOrderedByIndex(routine.getId()).size()))
                .toList();
    }

    public RoutineResponse get(UUID routineId) {
        RoutineEntity routine = findOwnedOrThrow(routineId);
        return toDetailResponse(routine);
    }

    @Transactional
    public RoutineResponse create(String name, String description, List<RoutineExerciseItem> items) {
        UserEntity owner = userRepository.findById(currentUser.getId());

        RoutineEntity routine = new RoutineEntity();
        routine.setUser(owner);
        routine.setName(name.trim());
        routine.setDescription(description);
        routineRepository.persist(routine);

        persistRoutineExercises(routine, items);

        return toDetailResponse(routine);
    }

    @Transactional
    public RoutineResponse update(UUID routineId, String name, String description, List<RoutineExerciseItem> items) {
        RoutineEntity routine = findOwnedOrThrow(routineId);

        routine.setName(name.trim());
        routine.setDescription(description);

        routineExerciseRepository.deleteByRoutineId(routineId);
        persistRoutineExercises(routine, items);

        return toDetailResponse(routine);
    }

    @Transactional
    public void delete(UUID routineId) {
        findOwnedOrThrow(routineId);
        routineRepository.deleteById(routineId);
    }

    private void persistRoutineExercises(RoutineEntity routine, List<RoutineExerciseItem> items) {
        for (int i = 0; i < items.size(); i++) {
            RoutineExerciseItem item = items.get(i);
            ExerciseEntity exercise = exerciseRepository.findByIdOptional(item.exerciseId())
                    .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.BAD_REQUEST));

            RoutineExerciseEntity routineExercise = new RoutineExerciseEntity();
            routineExercise.setRoutine(routine);
            routineExercise.setExercise(exercise);
            routineExercise.setOrderIndex(i);
            routineExercise.setPlannedSets(item.plannedSets());
            routineExercise.setPlannedReps(item.plannedReps());
            routineExercise.setPlannedLoadKg(item.plannedLoadKg());
            routineExerciseRepository.persist(routineExercise);
        }
    }

    /** Routines have no shared catalog — any routine not owned by the current user is a 404, never 403. */
    private RoutineEntity findOwnedOrThrow(UUID routineId) {
        RoutineEntity routine = routineRepository.findByIdOptional(routineId)
                .orElseThrow(() -> new ApiException("ROUTINE_NOT_FOUND", "Rotina não encontrada.", Response.Status.NOT_FOUND));

        if (!routine.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException("ROUTINE_NOT_FOUND", "Rotina não encontrada.", Response.Status.NOT_FOUND);
        }
        return routine;
    }

    private RoutineResponse toDetailResponse(RoutineEntity routine) {
        List<RoutineExerciseResponse> exercises = routineExerciseRepository.listByRoutineOrderedByIndex(routine.getId()).stream()
                .map(re -> new RoutineExerciseResponse(
                        re.getExercise().getId(),
                        re.getExercise().getName(),
                        re.getOrderIndex(),
                        re.getPlannedSets(),
                        re.getPlannedReps(),
                        re.getPlannedLoadKg()))
                .toList();

        return new RoutineResponse(routine.getId(), routine.getName(), routine.getDescription(), routine.getCreatedAt(), exercises);
    }
}
```

- [ ] **Step 3: Create `resource/RoutineResource.java`**

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.RoutineRequest;
import com.rsinelli.gymtracker.dto.RoutineResponse;
import com.rsinelli.gymtracker.dto.RoutineSummaryResponse;
import com.rsinelli.gymtracker.service.RoutineService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/routines")
@Tag(name = "Routines", description = "Rotinas/templates de treino do usuário autenticado")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class RoutineResource {

    @Inject
    RoutineService routineService;

    @GET
    @Operation(summary = "Lista as rotinas do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Lista de rotinas (resumo)")
    public Response list() {
        List<RoutineSummaryResponse> response = routineService.list();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma rotina, incluindo exercícios ordenados")
    @APIResponse(responseCode = "200", description = "Detalhe da rotina")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
        RoutineResponse response = routineService.get(id);
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Cria uma rotina com seus exercícios ordenados")
    @APIResponse(responseCode = "201", description = "Rotina criada")
    @APIResponse(responseCode = "400", description = "Payload inválido ou exercício inexistente")
    public Response create(@Valid RoutineRequest request) {
        RoutineResponse response = routineService.create(request.name(), request.description(), request.exercises());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Substitui nome/descrição e recria a lista de exercícios da rotina")
    @APIResponse(responseCode = "200", description = "Rotina atualizada")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response update(@PathParam("id") UUID id, @Valid RoutineRequest request) {
        RoutineResponse response = routineService.update(id, request.name(), request.description(), request.exercises());
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove uma rotina do usuário autenticado")
    @APIResponse(responseCode = "204", description = "Rotina removida")
    @APIResponse(responseCode = "404", description = "Rotina não encontrada ou pertence a outro usuário")
    public Response delete(@PathParam("id") UUID id) {
        routineService.delete(id);
        return Response.noContent().build();
    }
}
```

- [ ] **Step 4: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add routines CRUD as an ordered-exercise aggregate`)

---

### Task 8: `RoutineResourceIT`

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/RoutineResourceIT.java`

Includes the cross-feature 409 test deferred from Task 5 — deleting an exercise that a routine references.

- [ ] **Step 1: Create `integration/RoutineResourceIT.java`**

```java
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
```

- [ ] **Step 2: Run `./mvnw verify`, fix anything red, commit** (`test(backend): cover routine aggregate flow and cross-feature exercise conflict`)

---

### Task 9: Wrap-up

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the README Status checklist**

Sprint 1 was merged in a previous branch but its checkbox was never updated — fix both Sprint 1 and Sprint 2 in the same edit.

Replace:
```markdown
- [x] Sprint 0 — Fundações
- [ ] Sprint 1 — Schema + Auth
- [ ] Sprint 2 — Exercícios + Rotinas
```
with:
```markdown
- [x] Sprint 0 — Fundações
- [x] Sprint 1 — Schema + Auth
- [x] Sprint 2 — Exercícios + Rotinas
```

- [ ] **Step 2: Run the full backend verification**

```bash
cd backend && ./mvnw test && ./mvnw verify
```

Both must be green — unit tests (surefire, no Docker) and integration tests (failsafe, Testcontainers-backed Postgres).

- [ ] **Step 3: Review OpenAPI tag completeness**

Confirm every `@Tag` used across `AuthResource`, `UserResource`, `MuscleGroupResource`, `ExerciseResource`, `RoutineResource` has a distinct `name` and a one-line `description` (already the case per Tasks 1-7 — this step is a final sanity check, not new code, e.g. by hitting `/q/openapi` in dev mode or grepping for `@Tag` across `resource/`).

- [ ] **Step 4: Commit** (`docs: mark Sprint 1-2 complete in README status`)

---

## Self-Review Checklist (run before starting execution)

- [ ] **Spec coverage** — every section of `docs/superpowers/specs/2026-07-24-sprint2-exercicios-rotinas-design.md` maps to a task: Decision 1 (auth) → Task 1; Decision 2 (403/404 asymmetry) → Task 4 (`findOwnedOrThrow`) and Task 7 (`findOwnedOrThrow`); Decision 3 (delete conflict) → Task 4 (`delete`); Decision 4 (routine aggregate, no cascade) → Tasks 6-7; Decision 5 (`muscle_groups` endpoint) → Task 2. Migrations V3-V6 → Tasks 2, 3, 6.
- [ ] **Placeholder scan** — no "TBD", no "similar to Task N", no vague instructions; every task has complete, compilable code.
- [ ] **Type/name consistency across tasks** — `ExerciseRepository.listVisibleTo(UUID)` (Task 3) matches its use in `ExerciseService.list()` (Task 4); `RoutineExerciseRepository.listByRoutineOrderedByIndex(UUID)` / `.deleteByRoutineId(UUID)` (Task 6) match their use in `RoutineService` (Task 7); DTO record component names (`RoutineExerciseItem.exerciseId/plannedSets/plannedReps/plannedLoadKg`, `RoutineRequest.name/description/exercises`) match their accessors used in `RoutineService`/`RoutineResource`; `ApiException(code, message, status)` and `ErrorResponse.of(...)` signatures match the existing Sprint 0-1 classes (no changes needed there).

## Verification

- `cd backend && ./mvnw test` — unit tests green, no Docker required.
- `cd backend && ./mvnw verify` — integration tests green (Testcontainers Postgres): `AuthResourceIT`, `UserResourceIT`, `MuscleGroupResourceIT`, `ExerciseResourceIT`, `RoutineResourceIT`.
- Manual sanity check (optional): `docker compose up -d && cd backend && ./mvnw quarkus:dev`, register a user, hit `/users/me` with the returned `access_token`, list `/muscle-groups`, create an exercise and a routine referencing it, confirm deleting that exercise returns 409.
