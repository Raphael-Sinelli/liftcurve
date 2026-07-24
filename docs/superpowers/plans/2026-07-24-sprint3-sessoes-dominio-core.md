# Sprint 3 — Sessões + Domínio Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship workout session tracking (start → log sets → finish, one active session per user) plus the domain layer that makes this project's portfolio case — pure, heavily-tested `OneRepMaxCalculator` (Epley/Brzycki), `VolumeCalculator` (weekly volume by muscle group), and `PlateauDetectionService` (streak-based stagnation detection) — wired into three read-only dashboard endpoints.

**Architecture:** Same layering as Sprint 2 (Resource → Service/BO → Panache Repository → JPA entity). Three new pure, CDI-bean-but-directly-instantiable calculator classes live in `service/` alongside the stateful services — they take plain records in, return plain records out, and are unit-tested with zero Quarkus/CDI/Postgres (same convention as `PasswordHasherTest`/`TokenServiceTest` from Sprint 1). `WorkoutSessionService` owns session lifecycle (single-active-session rule) and incremental set logging in one cohesive class (they share the "is this session still open" check, splitting them would just duplicate that check across two services). `DashboardService` is the only place raw `session_sets` rows get reduced from "many sets" to "one best-1RM-per-session" before being handed to the pure calculators — repositories stay simple, calculators stay pure, this reduction step is the one piece of glue logic that has to live somewhere impure.

**Tech Stack:** Same as Sprint 0-2 (Java 21, Quarkus 3.37.3, PostgreSQL 16, Flyway, JUnit 5, Testcontainers, RestAssured). No new Maven dependencies.

**Design reference:** `docs/superpowers/specs/2026-07-24-sprint3-sessoes-dominio-core-design.md` — read it before implementing Task 1. It documents the reasoning behind every non-obvious decision below (why Brzycki's result column must be nullable, why volume grouping doesn't multiply by "number of sets" again, the exact plateau-streak algorithm, the `ON DELETE SET NULL` choice for `routine_id`, and why dashboard endpoints accept the N+1 query pattern).

## Global Constraints

- DB columns/tables: `snake_case`. Java code: `camelCase`. JSON over the wire: `snake_case` — enforced via `quarkus.jackson.property-naming-strategy=SNAKE_CASE`, never `@JsonProperty` per field.
- All timestamps: `TIMESTAMPTZ` in Postgres, `java.time.Instant` in Java — never `TIMESTAMP`/`LocalDateTime`.
- All primary keys: `UUID`, generated in Java via `UUID.randomUUID()` (not a DB or Hibernate generator).
- All monetary/weight quantities: `NUMERIC` in Postgres, `java.math.BigDecimal` in Java — never `double`/`float` for weight or 1RM values. Round only at the final step of a calculation (`RoundingMode.HALF_UP`, scale 2), never round an intermediate result.
- Every error response body: `{"error": {"code": ..., "message": ..., "status": ..., "details": [...]}}`.
- Integration tests use a real Postgres via Testcontainers — never mock the database. Files ending `IT.java` run under `mvn verify` (failsafe, needs Docker); files ending `Test.java` run under `mvn test` (surefire, zero Docker) — the three calculator test classes are `Test.java` files, not `IT.java`.
- Protect endpoints with `@Authenticated`, never `@RolesAllowed` — JWTs in this project carry only a `sub` claim (user UUID) and `iss`, no roles/groups.
- Services inject `security.CurrentUser` to resolve "who is asking" — authorization checks (ownership) live in the Service layer, never in the Resource.
- Sessions have no shared/public catalog (same as routines) — a session that doesn't exist or belongs to another user is always **404** `SESSION_NOT_FOUND`, never 403.
- A reference embedded inside a request payload (a `routine_id` or `exercise_id` field, not a path param) that turns out invalid/invisible is **400**, not 404 — 404 is reserved for the primary resource identified by the URL path. This project already uses this distinction for `INVALID_EXERCISE_REFERENCE` in Sprint 2; Sprint 3 adds `INVALID_ROUTINE_REFERENCE` following the identical rule.
- Migrations: one table per file, `Vn__snake_case_description.sql`, `quarkus.hibernate-orm.database.generation=validate` (schema is migration-owned).
- Jackson's `SNAKE_CASE` naming strategy inserts `_` before an uppercase letter, never before a digit — a Java field like `estimated1rmEpley` auto-converts to `estimated1rm_epley`, not `estimated_1rm_epley`. Any DTO field whose intended wire name has a digit adjacent to a word boundary (the `estimated_1rm_*` / `current_max_1rm` family in this sprint) needs an explicit `@JsonProperty` pinning the exact wire name — this is the one legitimate use of `@JsonProperty` in this codebase outside external-API mapping.
- Calculators (`OneRepMaxCalculator`, `VolumeCalculator`, `PlateauDetectionService`) are `@ApplicationScoped` beans (so services can `@Inject` them) but take no CDI-managed dependencies themselves and have a public no-arg constructor — unit tests instantiate them directly with `new`, bypassing CDI entirely, exactly like `PasswordHasherTest`/`TokenServiceTest` do with `PasswordHasher`/`TokenService`.
- Do not `git push` — commits are created locally only; the user reviews and pushes.

---

### Task 1: Schema — `workout_sessions` + `session_sets`

No dedicated test in this task — schema-only, exercised by `WorkoutSessionResourceIT` in Task 6 (same precedent as Sprint 2's schema-only tasks).

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_workout_sessions.sql`
- Create: `backend/src/main/resources/db/migration/V8__create_session_sets.sql`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/WorkoutSessionEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/SessionSetEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/WorkoutSessionRepository.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/SessionSetRepository.java`

**Interfaces:**
- Produces: `WorkoutSessionRepository.listOwnedBy(UUID userId): List<WorkoutSessionEntity>`, `.findActiveByUser(UUID userId): Optional<WorkoutSessionEntity>` — consumed by `WorkoutSessionService` (Task 5).
- Produces: `SessionSetRepository.listBySessionOrderedByCreatedAt(UUID sessionId): List<SessionSetEntity>`, `.countBySessionAndExercise(UUID sessionId, UUID exerciseId): long`, `.listByUserAndExercise(UUID userId, UUID exerciseId): List<SessionSetEntity>`, `.listByUser(UUID userId): List<SessionSetEntity>` — consumed by `WorkoutSessionService` (Task 5) and `DashboardService` (Task 7).
- Produces: `WorkoutSessionEntity` with nullable `routine` field (`ON DELETE SET NULL`) and nullable `finishedAt` (null = active) — consumed by Task 5's single-active-session check.
- Produces: `SessionSetEntity` with nullable `estimated1rmBrzycki` (see design Decision 3) — consumed by Task 5's set-creation logic and Task 7's dashboard reduction.

- [ ] **Step 1: Create migration `V7__create_workout_sessions.sql`**

`routine_id` uses `ON DELETE SET NULL`, not CASCADE or RESTRICT: a session is a historical fact ("I trained this, possibly based on a routine that no longer exists") that must survive the routine being deleted later, and routine deletion must never be permanently blocked just because someone once trained from it.

```sql
CREATE TABLE workout_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    routine_id UUID REFERENCES routines (id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    notes TEXT
);

CREATE INDEX idx_workout_sessions_user_id ON workout_sessions (user_id);
```

- [ ] **Step 2: Create migration `V8__create_session_sets.sql`**

`estimated_1rm_brzycki` is nullable — Brzycki is mathematically undefined for `reps >= 37` (see design Decision 3), so the column must be able to hold that absence. `estimated_1rm_epley` and `estimated_1rm_best` stay `NOT NULL` — Epley is defined for any `reps >= 1`, and `best` always has at least Epley to fall back on. `exercise_id` has no cascade (same pattern as `routine_exercises.exercise_id`) — this means `ExerciseService.delete`'s existing generic `SQLState 23503` catch (Sprint 2) automatically now also protects against deleting an exercise with logged workout history, with zero new code.

```sql
CREATE TABLE session_sets (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workout_sessions (id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    set_number INT NOT NULL,
    weight_kg NUMERIC NOT NULL,
    reps INT NOT NULL,
    rpe NUMERIC,
    estimated_1rm_epley NUMERIC NOT NULL,
    estimated_1rm_brzycki NUMERIC,
    estimated_1rm_best NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_sets_session_id ON session_sets (session_id);
CREATE INDEX idx_session_sets_exercise_id ON session_sets (exercise_id);
```

- [ ] **Step 3: Create `entity/WorkoutSessionEntity.java`**

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
@Table(name = "workout_sessions")
public class WorkoutSessionEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private RoutineEntity routine;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column
    private String notes;

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

    public RoutineEntity getRoutine() {
        return routine;
    }

    public void setRoutine(RoutineEntity routine) {
        this.routine = routine;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
```

- [ ] **Step 4: Create `entity/SessionSetEntity.java`**

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_sets")
public class SessionSetEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int reps;

    @Column
    private BigDecimal rpe;

    @Column(name = "estimated_1rm_epley", nullable = false)
    private BigDecimal estimated1rmEpley;

    @Column(name = "estimated_1rm_brzycki")
    private BigDecimal estimated1rmBrzycki;

    @Column(name = "estimated_1rm_best", nullable = false)
    private BigDecimal estimated1rmBest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkoutSessionEntity getSession() {
        return session;
    }

    public void setSession(WorkoutSessionEntity session) {
        this.session = session;
    }

    public ExerciseEntity getExercise() {
        return exercise;
    }

    public void setExercise(ExerciseEntity exercise) {
        this.exercise = exercise;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(int setNumber) {
        this.setNumber = setNumber;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public BigDecimal getRpe() {
        return rpe;
    }

    public void setRpe(BigDecimal rpe) {
        this.rpe = rpe;
    }

    public BigDecimal getEstimated1rmEpley() {
        return estimated1rmEpley;
    }

    public void setEstimated1rmEpley(BigDecimal estimated1rmEpley) {
        this.estimated1rmEpley = estimated1rmEpley;
    }

    public BigDecimal getEstimated1rmBrzycki() {
        return estimated1rmBrzycki;
    }

    public void setEstimated1rmBrzycki(BigDecimal estimated1rmBrzycki) {
        this.estimated1rmBrzycki = estimated1rmBrzycki;
    }

    public BigDecimal getEstimated1rmBest() {
        return estimated1rmBest;
    }

    public void setEstimated1rmBest(BigDecimal estimated1rmBest) {
        this.estimated1rmBest = estimated1rmBest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 5: Create `repository/WorkoutSessionRepository.java`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WorkoutSessionRepository implements PanacheRepositoryBase<WorkoutSessionEntity, UUID> {

    public List<WorkoutSessionEntity> listOwnedBy(UUID userId) {
        return list("user.id = ?1 order by startedAt desc", userId);
    }

    public Optional<WorkoutSessionEntity> findActiveByUser(UUID userId) {
        return find("user.id = ?1 and finishedAt is null", userId).firstResultOptional();
    }
}
```

- [ ] **Step 6: Create `repository/SessionSetRepository.java`**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.SessionSetEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SessionSetRepository implements PanacheRepositoryBase<SessionSetEntity, UUID> {

    public List<SessionSetEntity> listBySessionOrderedByCreatedAt(UUID sessionId) {
        return list("session.id = ?1 order by createdAt", sessionId);
    }

    public long countBySessionAndExercise(UUID sessionId, UUID exerciseId) {
        return count("session.id = ?1 and exercise.id = ?2", sessionId, exerciseId);
    }

    public List<SessionSetEntity> listByUserAndExercise(UUID userId, UUID exerciseId) {
        return list("session.user.id = ?1 and exercise.id = ?2 order by session.startedAt", userId, exerciseId);
    }

    public List<SessionSetEntity> listByUser(UUID userId) {
        return list("session.user.id = ?1 order by session.startedAt", userId);
    }
}
```

- [ ] **Step 7: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add workout_sessions and session_sets schema`)

---

### Task 2: `OneRepMaxCalculator`

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/OneRepMaxResult.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/OneRepMaxCalculator.java`
- Test: `backend/src/test/java/com/rsinelli/gymtracker/unit/OneRepMaxCalculatorTest.java`

**Interfaces:**
- Produces: `OneRepMaxCalculator.calculate(BigDecimal weightKg, int reps): OneRepMaxResult` — consumed by `WorkoutSessionService.addSet` (Task 5).
- Produces: `record OneRepMaxResult(BigDecimal epley, BigDecimal brzycki, BigDecimal best)` where `brzycki` may be `null` — consumed by Task 5 and by `SessionSetEntity` field population.

- [ ] **Step 1: Write the failing tests**

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneRepMaxCalculatorTest {

    private final OneRepMaxCalculator calculator = new OneRepMaxCalculator();

    @Test
    void repsOfOneMakesBothFormulasApproximatelyEqualToWeight() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("50"), 1);

        assertEquals(new BigDecimal("51.67"), result.epley());
        assertEquals(new BigDecimal("50.00"), result.brzycki());
        assertEquals(new BigDecimal("51.67"), result.best());
    }

    @Test
    void lowRepsEpleyWinsOverBrzycki() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 3);

        assertEquals(new BigDecimal("110.00"), result.epley());
        assertEquals(new BigDecimal("105.88"), result.brzycki());
        assertEquals(new BigDecimal("110.00"), result.best());
    }

    @Test
    void higherRepsBrzyckiCanWinOverEpley() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 15);

        assertEquals(new BigDecimal("150.00"), result.epley());
        assertEquals(new BigDecimal("163.64"), result.brzycki());
        assertEquals(new BigDecimal("163.64"), result.best());
    }

    @Test
    void repsAboveTwelveStillCalculatesWithoutError() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("80"), 20);

        assertTrue(result.epley().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.best().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void repsAtThirtySevenGuardsBrzyckiToNull() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 37);

        assertEquals(new BigDecimal("223.33"), result.epley());
        assertNull(result.brzycki());
        assertEquals(new BigDecimal("223.33"), result.best());
    }

    @Test
    void repsWellAboveThirtySevenAlsoGuardsBrzyckiToNull() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 50);

        assertEquals(new BigDecimal("266.67"), result.epley());
        assertNull(result.brzycki());
        assertEquals(new BigDecimal("266.67"), result.best());
    }

    @Test
    void repsJustBelowThirtySevenStillComputesBrzycki() {
        OneRepMaxResult result = calculator.calculate(new BigDecimal("100"), 36);

        assertEquals(new BigDecimal("220.00"), result.epley());
        assertEquals(new BigDecimal("3600.00"), result.brzycki());
        assertEquals(new BigDecimal("3600.00"), result.best());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=OneRepMaxCalculatorTest`
Expected: FAIL — `OneRepMaxCalculator` and `OneRepMaxResult` don't exist yet (compile error).

- [ ] **Step 3: Create `service/OneRepMaxResult.java`**

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;

public record OneRepMaxResult(BigDecimal epley, BigDecimal brzycki, BigDecimal best) {
}
```

- [ ] **Step 4: Create `service/OneRepMaxCalculator.java`**

The intermediate `reps / 30` division in Epley uses 10 fractional digits of precision (far more than the final scale-2 result needs) so that only the *final* multiply-and-round step introduces rounding — never an intermediate one. Brzycki has no intermediate division to worry about (it's one multiply, one divide, done).

```java
package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class OneRepMaxCalculator {

    private static final int BRZYCKI_UNDEFINED_REPS_THRESHOLD = 37;
    private static final int RESULT_SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 10;

    public OneRepMaxResult calculate(BigDecimal weightKg, int reps) {
        BigDecimal epley = calculateEpley(weightKg, reps);
        BigDecimal brzycki = reps >= BRZYCKI_UNDEFINED_REPS_THRESHOLD ? null : calculateBrzycki(weightKg, reps);
        BigDecimal best = brzycki == null ? epley : epley.max(brzycki);
        return new OneRepMaxResult(epley, brzycki, best);
    }

    private BigDecimal calculateEpley(BigDecimal weightKg, int reps) {
        BigDecimal repsOverThirty = BigDecimal.valueOf(reps)
                .divide(BigDecimal.valueOf(30), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal repsFactor = BigDecimal.ONE.add(repsOverThirty);
        return weightKg.multiply(repsFactor).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBrzycki(BigDecimal weightKg, int reps) {
        BigDecimal denominator = BigDecimal.valueOf(BRZYCKI_UNDEFINED_REPS_THRESHOLD - reps);
        return weightKg.multiply(BigDecimal.valueOf(36))
                .divide(denominator, RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=OneRepMaxCalculatorTest`
Expected: PASS — all 7 tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/service/OneRepMaxCalculator.java backend/src/main/java/com/rsinelli/gymtracker/service/OneRepMaxResult.java backend/src/test/java/com/rsinelli/gymtracker/unit/OneRepMaxCalculatorTest.java
git commit -m "feat(backend): add OneRepMaxCalculator with Epley/Brzycki edge cases"
```

---

### Task 3: `VolumeCalculator`

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/SetVolumeInput.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/VolumeBucketKey.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/VolumeCalculator.java`
- Test: `backend/src/test/java/com/rsinelli/gymtracker/unit/VolumeCalculatorTest.java`

**Interfaces:**
- Produces: `VolumeCalculator.calculate(List<SetVolumeInput>): Map<VolumeBucketKey, BigDecimal>` — consumed by `DashboardService.volume()` (Task 7).
- Produces: `record SetVolumeInput(BigDecimal weightKg, int reps, UUID muscleGroupId, Instant sessionStartedAt)` — `muscleGroupId` may be `null` (defensive, see design Decision 4; today's schema never actually produces null here).
- Produces: `record VolumeBucketKey(UUID muscleGroupId, Instant weekStartUtc)` — `weekStartUtc` is always Monday 00:00 UTC of the ISO week containing `sessionStartedAt`.

- [ ] **Step 1: Write the failing tests**

Every test computes its own reference Monday via `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)` instead of hardcoding a real calendar date's weekday — this makes the tests correct regardless of what day of the week any particular date actually falls on.

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.SetVolumeInput;
import com.rsinelli.gymtracker.service.VolumeBucketKey;
import com.rsinelli.gymtracker.service.VolumeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCalculatorTest {

    private final VolumeCalculator calculator = new VolumeCalculator();

    private static final UUID CHEST = UUID.randomUUID();
    private static final UUID BACK = UUID.randomUUID();

    private LocalDate anyMonday() {
        return LocalDate.of(2026, 3, 15).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Instant atUtc(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZoneOffset.UTC).toInstant();
    }

    @Test
    void singleSetContributesRepsTimesWeightToItsBucket() {
        LocalDate monday = anyMonday();
        SetVolumeInput input = new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(List.of(input));

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("1000"), result.get(key));
    }

    @Test
    void multipleSetsSameMuscleGroupSameWeekSumCorrectly() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("50"), 8, CHEST, atUtc(monday.plusDays(2), 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("1400"), result.get(key));
    }

    @Test
    void differentMuscleGroupsStayInSeparateBuckets() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("100"), 10, BACK, atUtc(monday, 10)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(monday, 0))));
        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(BACK, atUtc(monday, 0))));
    }

    @Test
    void differentWeeksStayInSeparateBuckets() {
        LocalDate week1Monday = anyMonday();
        LocalDate week2Monday = week1Monday.plusWeeks(1);
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(week1Monday, 9)),
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(week2Monday, 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(week1Monday, 0))));
        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(week2Monday, 0))));
        assertEquals(2, result.size());
    }

    @Test
    void sessionCrossingWeekBoundaryBucketsUnderStartedAtWeek() {
        LocalDate monday = anyMonday();
        // Session "started" Saturday 23:00 UTC of week 1 — must bucket under week 1's Monday,
        // not roll forward just because it's near the end of the week.
        Instant saturdayNight = atUtc(monday.plusDays(5), 23);
        SetVolumeInput input = new SetVolumeInput(new BigDecimal("60"), 12, CHEST, saturdayNight);

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(List.of(input));

        VolumeBucketKey key = new VolumeBucketKey(CHEST, atUtc(monday, 0));
        assertEquals(new BigDecimal("720"), result.get(key));
    }

    @Test
    void nullMuscleGroupBucketsSeparatelyWithoutThrowing() {
        LocalDate monday = anyMonday();
        List<SetVolumeInput> inputs = List.of(
                new SetVolumeInput(new BigDecimal("100"), 10, CHEST, atUtc(monday, 9)),
                new SetVolumeInput(new BigDecimal("40"), 10, null, atUtc(monday, 9)));

        Map<VolumeBucketKey, BigDecimal> result = calculator.calculate(inputs);

        assertEquals(new BigDecimal("1000"), result.get(new VolumeBucketKey(CHEST, atUtc(monday, 0))));
        VolumeBucketKey nullGroupKey = new VolumeBucketKey(null, atUtc(monday, 0));
        assertEquals(new BigDecimal("400"), result.get(nullGroupKey));
        assertNull(new VolumeCalculator().calculate(List.of()).get(nullGroupKey));
        assertTrue(result.containsKey(nullGroupKey));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=VolumeCalculatorTest`
Expected: FAIL — `SetVolumeInput`, `VolumeBucketKey`, `VolumeCalculator` don't exist yet.

- [ ] **Step 3: Create `service/SetVolumeInput.java` and `service/VolumeBucketKey.java`**

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SetVolumeInput(BigDecimal weightKg, int reps, UUID muscleGroupId, Instant sessionStartedAt) {
}
```

```java
package com.rsinelli.gymtracker.service;

import java.time.Instant;
import java.util.UUID;

public record VolumeBucketKey(UUID muscleGroupId, Instant weekStartUtc) {
}
```

- [ ] **Step 4: Create `service/VolumeCalculator.java`**

Each `session_sets` row already represents one executed set (not a planned block of several), so its contribution to volume is simply `reps × weight` — summed across rows. The "sets" term in the original `Σ(sets × reps × peso)` formula is the row count being summed over, not an extra per-row multiplier (see design Decision 4).

```java
package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VolumeCalculator {

    public Map<VolumeBucketKey, BigDecimal> calculate(List<SetVolumeInput> inputs) {
        Map<VolumeBucketKey, BigDecimal> totals = new HashMap<>();
        for (SetVolumeInput input : inputs) {
            VolumeBucketKey key = new VolumeBucketKey(input.muscleGroupId(), weekStartUtc(input.sessionStartedAt()));
            BigDecimal setVolume = input.weightKg().multiply(BigDecimal.valueOf(input.reps()));
            totals.merge(key, setVolume, BigDecimal::add);
        }
        return totals;
    }

    private Instant weekStartUtc(Instant instant) {
        ZonedDateTime zoned = instant.atZone(ZoneOffset.UTC);
        return zoned.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=VolumeCalculatorTest`
Expected: PASS — all 6 tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/service/VolumeCalculator.java backend/src/main/java/com/rsinelli/gymtracker/service/SetVolumeInput.java backend/src/main/java/com/rsinelli/gymtracker/service/VolumeBucketKey.java backend/src/test/java/com/rsinelli/gymtracker/unit/VolumeCalculatorTest.java
git commit -m "feat(backend): add VolumeCalculator grouped by muscle group and ISO week"
```

---

### Task 4: `PlateauDetectionService`

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/PlateauStatus.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/PlateauResult.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/PlateauDetectionService.java`
- Test: `backend/src/test/java/com/rsinelli/gymtracker/unit/PlateauDetectionServiceTest.java`

**Interfaces:**
- Produces: `PlateauDetectionService.detect(List<BigDecimal> chronological1RmBests): PlateauResult` — consumed by `DashboardService.plateaus()` (Task 7). Input must already be one value per session (oldest first) — the service does not group or deduplicate.
- Produces: `enum PlateauStatus { INSUFFICIENT_DATA, NO_PLATEAU, PLATEAU_DETECTED }`, `record PlateauResult(PlateauStatus status, BigDecimal currentMax, String suggestion)` — `currentMax`/`suggestion` are `null` when `status == INSUFFICIENT_DATA`; `suggestion` is `null` unless `status == PLATEAU_DETECTED`.

- [ ] **Step 1: Write the failing tests**

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.service.PlateauDetectionService;
import com.rsinelli.gymtracker.service.PlateauResult;
import com.rsinelli.gymtracker.service.PlateauStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlateauDetectionServiceTest {

    private final PlateauDetectionService service = new PlateauDetectionService();

    private static List<BigDecimal> bigDecimals(int... values) {
        return java.util.Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void emptyHistoryReturnsInsufficientData() {
        PlateauResult result = service.detect(List.of());

        assertEquals(PlateauStatus.INSUFFICIENT_DATA, result.status());
        assertNull(result.currentMax());
        assertNull(result.suggestion());
    }

    @Test
    void fewerThanFourSessionsReturnsInsufficientData() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100));

        assertEquals(PlateauStatus.INSUFFICIENT_DATA, result.status());
    }

    @Test
    void exactlyThreeStagnantSessionsAfterBaselineTriggersPlateau() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
        assertEquals(BigDecimal.valueOf(100), result.currentMax());
        assertNotNull(result.suggestion());
    }

    @Test
    void streakOfTwoDoesNotTriggerPlateau() {
        // baseline 80 -> new PR 100 (streak resets to 0) -> 95 (streak 1) -> 95 (streak 2, never reaches 3)
        PlateauResult result = service.detect(bigDecimals(80, 100, 95, 95));

        assertEquals(PlateauStatus.NO_PLATEAU, result.status());
        assertEquals(BigDecimal.valueOf(100), result.currentMax());
    }

    @Test
    void newPrInTheMiddleResetsTheStreak() {
        // 100 (baseline) -> 100 (streak 1) -> 100 (streak 2) -> 110 (new PR, streak resets to 0)
        // -> 100 (streak 1) -> 100 (streak 2, final streak is 2, not 5 — does NOT trigger)
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 110, 100, 100));

        assertEquals(PlateauStatus.NO_PLATEAU, result.status());
        assertEquals(BigDecimal.valueOf(110), result.currentMax());
    }

    @Test
    void tiedValuesCountAsStagnantNotAsNewRecord() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
    }

    @Test
    void moreThanThreeConsecutiveStagnantSessionsStillTriggers() {
        PlateauResult result = service.detect(bigDecimals(100, 100, 100, 100, 100, 100));

        assertEquals(PlateauStatus.PLATEAU_DETECTED, result.status());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=PlateauDetectionServiceTest`
Expected: FAIL — `PlateauDetectionService`, `PlateauResult`, `PlateauStatus` don't exist yet.

- [ ] **Step 3: Create `service/PlateauStatus.java` and `service/PlateauResult.java`**

```java
package com.rsinelli.gymtracker.service;

public enum PlateauStatus {
    INSUFFICIENT_DATA,
    NO_PLATEAU,
    PLATEAU_DETECTED
}
```

```java
package com.rsinelli.gymtracker.service;

import java.math.BigDecimal;

public record PlateauResult(PlateauStatus status, BigDecimal currentMax, String suggestion) {
}
```

- [ ] **Step 4: Create `service/PlateauDetectionService.java`**

Scans chronologically, tracking a running max and a streak of consecutive sessions with no new record. A tie (`current <= runningMax`) counts as stagnant, not as a new record. The streak that matters is the one ending at the most recent session — a plateau that was later broken by a new PR must not still show up as "active" (see design Decision 5).

```java
package com.rsinelli.gymtracker.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class PlateauDetectionService {

    private static final int MIN_SESSIONS_REQUIRED = 4;
    private static final int STREAK_THRESHOLD = 3;
    private static final String DELOAD_SUGGESTION =
            "Considere reduzir a carga em ~10% por 1 semana (deload).";

    public PlateauResult detect(List<BigDecimal> chronological1RmBests) {
        if (chronological1RmBests.size() < MIN_SESSIONS_REQUIRED) {
            return new PlateauResult(PlateauStatus.INSUFFICIENT_DATA, null, null);
        }

        BigDecimal runningMax = chronological1RmBests.get(0);
        int streak = 0;
        for (int i = 1; i < chronological1RmBests.size(); i++) {
            BigDecimal current = chronological1RmBests.get(i);
            if (current.compareTo(runningMax) > 0) {
                runningMax = current;
                streak = 0;
            } else {
                streak++;
            }
        }

        if (streak >= STREAK_THRESHOLD) {
            return new PlateauResult(PlateauStatus.PLATEAU_DETECTED, runningMax, DELOAD_SUGGESTION);
        }
        return new PlateauResult(PlateauStatus.NO_PLATEAU, runningMax, null);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=PlateauDetectionServiceTest`
Expected: PASS — all 7 tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/service/PlateauDetectionService.java backend/src/main/java/com/rsinelli/gymtracker/service/PlateauResult.java backend/src/main/java/com/rsinelli/gymtracker/service/PlateauStatus.java backend/src/test/java/com/rsinelli/gymtracker/unit/PlateauDetectionServiceTest.java
git commit -m "feat(backend): add PlateauDetectionService with streak-based detection"
```

---

### Task 5: Sessions + Sets (service + resource)

Session lifecycle and set-logging live in one service (`WorkoutSessionService`) — both need the same "is this session still open" check, splitting them would just duplicate it.

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/WorkoutSessionRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/FinishWorkoutSessionRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/SessionSetRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/SessionSetResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/WorkoutSessionResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/WorkoutSessionSummaryResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/WorkoutSessionService.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/WorkoutSessionResource.java`

**Interfaces:**
- Produces: `GET/POST /workout-sessions`, `GET /workout-sessions/{id}`, `PATCH /workout-sessions/{id}`, `POST /workout-sessions/{id}/sets` (all `@Authenticated`), error codes `SESSION_NOT_FOUND` (404), `SESSION_ALREADY_ACTIVE` (409), `SESSION_ALREADY_FINISHED` (409), `INVALID_ROUTINE_REFERENCE` (400), `INVALID_EXERCISE_REFERENCE` (400, reuses Sprint 2's code) — consumed by `WorkoutSessionResourceIT` (Task 6) and `DashboardResourceIT` (Task 8, indirectly, since dashboard fixtures are seeded directly rather than through these endpoints).

- [ ] **Step 1: Create the request DTOs**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkoutSessionRequest(UUID routineId, @Size(max = 2000) String notes) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Size;

public record FinishWorkoutSessionRequest(@Size(max = 2000) String notes) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SessionSetRequest(
        @NotNull UUID exerciseId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weightKg,
        @Min(1) int reps,
        @DecimalMin(value = "1") @DecimalMax(value = "10") BigDecimal rpe) {
}
```

- [ ] **Step 2: Create the response DTOs**

Jackson's `SNAKE_CASE` strategy only inserts `_` before an uppercase letter, never before a digit — so a Java field like `estimated1rmEpley` would auto-convert to `estimated1rm_epley`, not the intended `estimated_1rm_epley`. `@JsonProperty` explicitly pins the wire name for these three fields only; this is the "field names must differ from the automatic conversion" carve-out in the project's naming rules, not a stylistic override.

```java
package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SessionSetResponse(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        int setNumber,
        BigDecimal weightKg,
        int reps,
        BigDecimal rpe,
        @JsonProperty("estimated_1rm_epley") BigDecimal estimated1rmEpley,
        @JsonProperty("estimated_1rm_brzycki") BigDecimal estimated1rmBrzycki,
        @JsonProperty("estimated_1rm_best") BigDecimal estimated1rmBest,
        Instant createdAt) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkoutSessionResponse(
        UUID id,
        UUID routineId,
        Instant startedAt,
        Instant finishedAt,
        String notes,
        List<SessionSetResponse> sets) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkoutSessionSummaryResponse(
        UUID id,
        UUID routineId,
        Instant startedAt,
        Instant finishedAt,
        int setCount) {
}
```

- [ ] **Step 3: Create `service/WorkoutSessionService.java`**

```java
package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.SessionSetResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionSummaryResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.RoutineEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.RoutineRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WorkoutSessionService {

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    RoutineRepository routineRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CurrentUser currentUser;

    @Inject
    OneRepMaxCalculator oneRepMaxCalculator;

    public List<WorkoutSessionSummaryResponse> list() {
        return workoutSessionRepository.listOwnedBy(currentUser.getId()).stream()
                .map(session -> new WorkoutSessionSummaryResponse(
                        session.getId(),
                        session.getRoutine() != null ? session.getRoutine().getId() : null,
                        session.getStartedAt(),
                        session.getFinishedAt(),
                        sessionSetRepository.listBySessionOrderedByCreatedAt(session.getId()).size()))
                .toList();
    }

    public WorkoutSessionResponse get(UUID sessionId) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        return toDetailResponse(session);
    }

    @Transactional
    public WorkoutSessionResponse create(UUID routineId, String notes) {
        UUID userId = currentUser.getId();

        if (workoutSessionRepository.findActiveByUser(userId).isPresent()) {
            throw new ApiException("SESSION_ALREADY_ACTIVE",
                    "Já existe uma sessão de treino em andamento.", Response.Status.CONFLICT);
        }

        RoutineEntity routine = null;
        if (routineId != null) {
            routine = routineRepository.findByIdOptional(routineId)
                    .filter(r -> r.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ApiException("INVALID_ROUTINE_REFERENCE",
                            "Rotina não encontrada ou não pertence a este usuário.", Response.Status.BAD_REQUEST));
        }

        UserEntity user = userRepository.findById(userId);

        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setUser(user);
        session.setRoutine(routine);
        session.setNotes(notes);
        workoutSessionRepository.persist(session);

        return toDetailResponse(session);
    }

    @Transactional
    public WorkoutSessionResponse finish(UUID sessionId, String notes) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        requireActive(session);

        session.setFinishedAt(Instant.now());
        if (notes != null) {
            session.setNotes(notes);
        }

        return toDetailResponse(session);
    }

    @Transactional
    public SessionSetResponse addSet(UUID sessionId, UUID exerciseId, BigDecimal weightKg, int reps, BigDecimal rpe) {
        WorkoutSessionEntity session = findOwnedOrThrow(sessionId);
        requireActive(session);

        ExerciseEntity exercise = exerciseRepository.findVisibleTo(exerciseId, currentUser.getId())
                .orElseThrow(() -> new ApiException("INVALID_EXERCISE_REFERENCE",
                        "Exercício não encontrado ou não visível para este usuário.", Response.Status.BAD_REQUEST));

        int setNumber = (int) sessionSetRepository.countBySessionAndExercise(sessionId, exerciseId) + 1;
        OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(weightKg, reps);

        SessionSetEntity set = new SessionSetEntity();
        set.setSession(session);
        set.setExercise(exercise);
        set.setSetNumber(setNumber);
        set.setWeightKg(weightKg);
        set.setReps(reps);
        set.setRpe(rpe);
        set.setEstimated1rmEpley(oneRepMax.epley());
        set.setEstimated1rmBrzycki(oneRepMax.brzycki());
        set.setEstimated1rmBest(oneRepMax.best());
        sessionSetRepository.persist(set);

        return toSetResponse(set);
    }

    private void requireActive(WorkoutSessionEntity session) {
        if (session.getFinishedAt() != null) {
            throw new ApiException("SESSION_ALREADY_FINISHED",
                    "Esta sessão já foi finalizada.", Response.Status.CONFLICT);
        }
    }

    /** Sessões não têm catálogo compartilhado — sempre 404 se não for do usuário atual. */
    private WorkoutSessionEntity findOwnedOrThrow(UUID sessionId) {
        WorkoutSessionEntity session = workoutSessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND));

        if (!session.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND);
        }
        return session;
    }

    private WorkoutSessionResponse toDetailResponse(WorkoutSessionEntity session) {
        List<SessionSetResponse> sets = sessionSetRepository.listBySessionOrderedByCreatedAt(session.getId()).stream()
                .map(this::toSetResponse)
                .toList();

        return new WorkoutSessionResponse(
                session.getId(),
                session.getRoutine() != null ? session.getRoutine().getId() : null,
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getNotes(),
                sets);
    }

    private SessionSetResponse toSetResponse(SessionSetEntity set) {
        return new SessionSetResponse(
                set.getId(),
                set.getExercise().getId(),
                set.getExercise().getName(),
                set.getSetNumber(),
                set.getWeightKg(),
                set.getReps(),
                set.getRpe(),
                set.getEstimated1rmEpley(),
                set.getEstimated1rmBrzycki(),
                set.getEstimated1rmBest(),
                set.getCreatedAt());
    }
}
```

- [ ] **Step 4: Create `resource/WorkoutSessionResource.java`**

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.FinishWorkoutSessionRequest;
import com.rsinelli.gymtracker.dto.SessionSetRequest;
import com.rsinelli.gymtracker.dto.SessionSetResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionRequest;
import com.rsinelli.gymtracker.dto.WorkoutSessionResponse;
import com.rsinelli.gymtracker.dto.WorkoutSessionSummaryResponse;
import com.rsinelli.gymtracker.service.WorkoutSessionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
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

@Path("/workout-sessions")
@Tag(name = "WorkoutSessions", description = "Registro de sessões de treino e séries executadas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class WorkoutSessionResource {

    @Inject
    WorkoutSessionService workoutSessionService;

    @GET
    @Operation(summary = "Lista as sessões de treino do usuário autenticado")
    @APIResponse(responseCode = "200", description = "Lista de sessões (resumo)")
    public Response list() {
        List<WorkoutSessionSummaryResponse> response = workoutSessionService.list();
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retorna o detalhe de uma sessão, incluindo séries registradas")
    @APIResponse(responseCode = "200", description = "Detalhe da sessão")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    public Response get(@PathParam("id") UUID id) {
        WorkoutSessionResponse response = workoutSessionService.get(id);
        return Response.ok(response).build();
    }

    @POST
    @Operation(summary = "Inicia uma nova sessão de treino")
    @APIResponse(responseCode = "201", description = "Sessão iniciada")
    @APIResponse(responseCode = "400", description = "Referência de rotina inválida")
    @APIResponse(responseCode = "409", description = "Já existe uma sessão ativa")
    public Response create(@Valid WorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.create(request.routineId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Finaliza uma sessão de treino")
    @APIResponse(responseCode = "200", description = "Sessão finalizada")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já estava finalizada")
    public Response finish(@PathParam("id") UUID id, @Valid FinishWorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutSessionService.finish(id, request.notes());
        return Response.ok(response).build();
    }

    @POST
    @Path("/{id}/sets")
    @Operation(summary = "Registra uma série executada na sessão")
    @APIResponse(responseCode = "201", description = "Série registrada")
    @APIResponse(responseCode = "400", description = "Referência de exercício inválida ou payload inválido")
    @APIResponse(responseCode = "404", description = "Sessão não encontrada ou pertence a outro usuário")
    @APIResponse(responseCode = "409", description = "Sessão já finalizada")
    public Response addSet(@PathParam("id") UUID id, @Valid SessionSetRequest request) {
        SessionSetResponse response = workoutSessionService.addSet(
                id, request.exerciseId(), request.weightKg(), request.reps(), request.rpe());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
```

- [ ] **Step 5: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add workout session lifecycle and set logging`)

---

### Task 6: `WorkoutSessionResourceIT`

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/WorkoutSessionResourceIT.java`

- [ ] **Step 1: Create `integration/WorkoutSessionResourceIT.java`**

```java
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
```

- [ ] **Step 2: Run `./mvnw verify`, fix anything red, commit** (`test(backend): cover session lifecycle and set logging rules`)

---

### Task 7: Dashboard (service + resource)

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/ProgressionPointResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/ProgressionResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/VolumeBucketResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/PlateauAlertResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/DashboardService.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/DashboardResource.java`

**Interfaces:**
- Produces: `GET /dashboard/progression/{exerciseId}`, `GET /dashboard/volume`, `GET /dashboard/plateaus` (all `@Authenticated`) — consumed by `DashboardResourceIT` (Task 8).
- Consumes: `VolumeCalculator.calculate(List<SetVolumeInput>)` (Task 3), `PlateauDetectionService.detect(List<BigDecimal>)` (Task 4), `ExerciseRepository.findVisibleTo(UUID, UUID)` (Sprint 2), `SessionSetRepository.listByUserAndExercise`/`.listByUser` (Task 1).

- [ ] **Step 1: Create the response DTOs**

Same digit/underscore caveat as `SessionSetResponse` (Task 5) — `@JsonProperty` pins the wire name explicitly instead of relying on Jackson's automatic conversion.

```java
package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record ProgressionPointResponse(
        Instant sessionStartedAt,
        @JsonProperty("estimated_1rm_best") BigDecimal estimated1rmBest) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.util.List;
import java.util.UUID;

public record ProgressionResponse(UUID exerciseId, String exerciseName, List<ProgressionPointResponse> points) {
}
```

```java
package com.rsinelli.gymtracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VolumeBucketResponse(UUID muscleGroupId, Instant weekStartUtc, BigDecimal totalVolumeKg) {
}
```

Same digit/underscore caveat again — `currentMax1rm` would otherwise auto-convert to `current_max1rm` instead of `current_max_1rm`.

```java
package com.rsinelli.gymtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record PlateauAlertResponse(
        UUID exerciseId,
        String exerciseName,
        @JsonProperty("current_max_1rm") BigDecimal currentMax1rm,
        String suggestion) {
}
```

- [ ] **Step 2: Create `service/DashboardService.java`**

The reduction from "many `session_sets` rows" to "one best-1RM-per-session" happens here, in plain Java, and is shared by both `progression()` and `plateaus()` — the calculators stay pure and never see raw set rows, only the already-reduced values they were designed to consume (design Decision 7).

```java
package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.PlateauAlertResponse;
import com.rsinelli.gymtracker.dto.ProgressionPointResponse;
import com.rsinelli.gymtracker.dto.ProgressionResponse;
import com.rsinelli.gymtracker.dto.VolumeBucketResponse;
import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DashboardService {

    @Inject
    SessionSetRepository sessionSetRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    CurrentUser currentUser;

    @Inject
    VolumeCalculator volumeCalculator;

    @Inject
    PlateauDetectionService plateauDetectionService;

    public ProgressionResponse progression(UUID exerciseId) {
        ExerciseEntity exercise = exerciseRepository.findVisibleTo(exerciseId, currentUser.getId())
                .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", "Exercício não encontrado.", Response.Status.NOT_FOUND));

        List<SessionSetEntity> sets = sessionSetRepository.listByUserAndExercise(currentUser.getId(), exerciseId);
        List<ProgressionPointResponse> points = chronologicalSessionBests(sets).stream()
                .map(sb -> new ProgressionPointResponse(sb.sessionStartedAt(), sb.best()))
                .toList();

        return new ProgressionResponse(exercise.getId(), exercise.getName(), points);
    }

    public List<VolumeBucketResponse> volume() {
        List<SessionSetEntity> sets = sessionSetRepository.listByUser(currentUser.getId());

        List<SetVolumeInput> inputs = sets.stream()
                .map(set -> new SetVolumeInput(
                        set.getWeightKg(),
                        set.getReps(),
                        // muscle_group_id is NOT NULL on today's schema, but the calculator's
                        // contract is intentionally defensive — see design Decision 4.
                        set.getExercise().getMuscleGroup() != null ? set.getExercise().getMuscleGroup().getId() : null,
                        set.getSession().getStartedAt()))
                .toList();

        Map<VolumeBucketKey, BigDecimal> totals = volumeCalculator.calculate(inputs);

        return totals.entrySet().stream()
                .map(entry -> new VolumeBucketResponse(
                        entry.getKey().muscleGroupId(),
                        entry.getKey().weekStartUtc(),
                        entry.getValue()))
                .sorted(Comparator.comparing(VolumeBucketResponse::weekStartUtc))
                .toList();
    }

    public List<PlateauAlertResponse> plateaus() {
        List<SessionSetEntity> sets = sessionSetRepository.listByUser(currentUser.getId());

        Map<UUID, List<SessionSetEntity>> byExercise = sets.stream()
                .collect(Collectors.groupingBy(set -> set.getExercise().getId()));

        List<PlateauAlertResponse> alerts = new ArrayList<>();
        for (Map.Entry<UUID, List<SessionSetEntity>> entry : byExercise.entrySet()) {
            List<BigDecimal> chronologicalBests = chronologicalSessionBests(entry.getValue()).stream()
                    .map(SessionBest::best)
                    .toList();

            PlateauResult result = plateauDetectionService.detect(chronologicalBests);
            if (result.status() == PlateauStatus.PLATEAU_DETECTED) {
                String exerciseName = entry.getValue().get(0).getExercise().getName();
                alerts.add(new PlateauAlertResponse(entry.getKey(), exerciseName, result.currentMax(), result.suggestion()));
            }
        }
        return alerts;
    }

    /** Reduz várias séries pra 1 valor por sessão: o maior estimated_1rm_best entre as séries daquele exercício naquela sessão, em ordem cronológica. */
    private List<SessionBest> chronologicalSessionBests(List<SessionSetEntity> sets) {
        Map<UUID, Instant> startedAtBySession = new LinkedHashMap<>();
        Map<UUID, BigDecimal> bestBySession = new LinkedHashMap<>();
        for (SessionSetEntity set : sets) {
            UUID sessionId = set.getSession().getId();
            startedAtBySession.put(sessionId, set.getSession().getStartedAt());
            bestBySession.merge(sessionId, set.getEstimated1rmBest(), BigDecimal::max);
        }
        return bestBySession.entrySet().stream()
                .map(e -> new SessionBest(startedAtBySession.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(SessionBest::sessionStartedAt))
                .toList();
    }

    private record SessionBest(Instant sessionStartedAt, BigDecimal best) {
    }
}
```

- [ ] **Step 3: Create `resource/DashboardResource.java`**

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.PlateauAlertResponse;
import com.rsinelli.gymtracker.dto.ProgressionResponse;
import com.rsinelli.gymtracker.dto.VolumeBucketResponse;
import com.rsinelli.gymtracker.service.DashboardService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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

@Path("/dashboard")
@Tag(name = "Dashboard", description = "Insights derivados: progressão de 1RM, volume por grupo/semana, alertas de platô")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @GET
    @Path("/progression/{exerciseId}")
    @Operation(summary = "Série temporal de 1RM estimado para um exercício")
    @APIResponse(responseCode = "200", description = "Pontos de progressão (pode ser lista vazia)")
    @APIResponse(responseCode = "404", description = "Exercício não encontrado ou não visível para este usuário")
    public Response progression(@PathParam("exerciseId") UUID exerciseId) {
        ProgressionResponse response = dashboardService.progression(exerciseId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/volume")
    @Operation(summary = "Volume de treino agregado por grupo muscular e semana")
    @APIResponse(responseCode = "200", description = "Buckets de volume semanal")
    public Response volume() {
        List<VolumeBucketResponse> response = dashboardService.volume();
        return Response.ok(response).build();
    }

    @GET
    @Path("/plateaus")
    @Operation(summary = "Alertas de platô ativos (exercícios estagnados)")
    @APIResponse(responseCode = "200", description = "Lista de alertas ativos")
    public Response plateaus() {
        List<PlateauAlertResponse> response = dashboardService.plateaus();
        return Response.ok(response).build();
    }
}
```

- [ ] **Step 4: Run `./mvnw compile`, confirm no errors, commit** (`feat(backend): add dashboard endpoints for progression, volume, and plateaus`)

---

### Task 8: `DashboardResourceIT`

The API always uses `Instant.now()` for `started_at`, so precise multi-week/multi-session fixtures can't be built purely through HTTP calls — this test seeds `WorkoutSessionEntity`/`SessionSetEntity` directly via repositories inside `QuarkusTransaction.requiringNew().call(...)`, the same pattern `ExerciseResourceIT` already uses to seed global exercises (Sprint 2).

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/DashboardResourceIT.java`

- [ ] **Step 1: Create `integration/DashboardResourceIT.java`**

```java
package com.rsinelli.gymtracker.integration;

import com.rsinelli.gymtracker.entity.ExerciseEntity;
import com.rsinelli.gymtracker.entity.SessionSetEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.entity.WorkoutSessionEntity;
import com.rsinelli.gymtracker.repository.ExerciseRepository;
import com.rsinelli.gymtracker.repository.SessionSetRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.repository.WorkoutSessionRepository;
import com.rsinelli.gymtracker.service.OneRepMaxCalculator;
import com.rsinelli.gymtracker.service.OneRepMaxResult;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DashboardResourceIT {

    @Inject
    UserRepository userRepository;

    @Inject
    ExerciseRepository exerciseRepository;

    @Inject
    WorkoutSessionRepository workoutSessionRepository;

    @Inject
    SessionSetRepository sessionSetRepository;

    private final OneRepMaxCalculator oneRepMaxCalculator = new OneRepMaxCalculator();

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "supersecret123", "name": "Dashboard Test"}
                        """.formatted(email))
                .when().post("/auth/register")
                .then().statusCode(201)
                .extract().path("access_token");
    }

    private UUID currentUserId(String token) {
        return UUID.fromString(
                given().header("Authorization", "Bearer " + token)
                        .when().get("/users/me")
                        .then().statusCode(200)
                        .extract().path("id"));
    }

    private UUID anyMuscleGroupId() {
        return UUID.fromString(
                given().when().get("/muscle-groups")
                        .then().statusCode(200)
                        .extract().path("[0].id"));
    }

    private UUID createExercise(String token, String name) {
        return UUID.fromString(given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "muscle_group_id": "%s"}
                        """.formatted(name, anyMuscleGroupId()))
                .when().post("/exercises")
                .then().statusCode(201)
                .extract().path("id"));
    }

    private UUID seedSession(UUID userId, Instant startedAt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = userRepository.findById(userId);
            WorkoutSessionEntity session = new WorkoutSessionEntity();
            session.setUser(user);
            session.setStartedAt(startedAt);
            session.setFinishedAt(startedAt.plusSeconds(3600));
            workoutSessionRepository.persist(session);
            return session.getId();
        });
    }

    private void seedSet(UUID sessionId, UUID exerciseId, BigDecimal weightKg, int reps) {
        QuarkusTransaction.requiringNew().run(() -> {
            WorkoutSessionEntity session = workoutSessionRepository.findById(sessionId);
            ExerciseEntity exercise = exerciseRepository.findById(exerciseId);
            OneRepMaxResult oneRepMax = oneRepMaxCalculator.calculate(weightKg, reps);

            SessionSetEntity set = new SessionSetEntity();
            set.setSession(session);
            set.setExercise(exercise);
            set.setSetNumber(1);
            set.setWeightKg(weightKg);
            set.setReps(reps);
            set.setEstimated1rmEpley(oneRepMax.epley());
            set.setEstimated1rmBrzycki(oneRepMax.brzycki());
            set.setEstimated1rmBest(oneRepMax.best());
            sessionSetRepository.persist(set);
        });
    }

    private LocalDate anyMonday() {
        return LocalDate.of(2026, 3, 15).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Instant atUtc(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZoneOffset.UTC).toInstant();
    }

    @Test
    void progressionForExerciseWithNoLoggedSetsReturnsEmptyList() {
        String token = registerAndGetToken("nologhistory");
        UUID exerciseId = createExercise(token, "Levantamento Terra " + System.nanoTime());

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + exerciseId)
                .then().statusCode(200)
                .body("points", empty());
    }

    @Test
    void progressionForNonexistentExerciseReturns404() {
        String token = registerAndGetToken("noexercise");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("error.code", equalTo("EXERCISE_NOT_FOUND"));
    }

    @Test
    void progressionReturnsPointsInChronologicalOrder() {
        String token = registerAndGetToken("progression");
        UUID userId = currentUserId(token);
        UUID exerciseId = createExercise(token, "Supino Reto " + System.nanoTime());
        LocalDate monday = anyMonday();

        UUID olderSession = seedSession(userId, atUtc(monday, 9));
        seedSet(olderSession, exerciseId, new BigDecimal("100"), 5);

        UUID newerSession = seedSession(userId, atUtc(monday.plusWeeks(1), 9));
        seedSet(newerSession, exerciseId, new BigDecimal("110"), 5);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/progression/" + exerciseId)
                .then().statusCode(200)
                .body("points.size()", equalTo(2))
                .body("points[0].estimated_1rm_best", equalTo(116.67f))
                .body("points[1].estimated_1rm_best", equalTo(128.33f));
    }

    @Test
    void volumeGroupsByMuscleGroupAndWeekIncludingWeekBoundaryCrossing() {
        String token = registerAndGetToken("volume");
        UUID userId = currentUserId(token);
        UUID exerciseId = createExercise(token, "Agachamento " + System.nanoTime());
        LocalDate monday = anyMonday();

        UUID mondaySession = seedSession(userId, atUtc(monday, 9));
        seedSet(mondaySession, exerciseId, new BigDecimal("100"), 10);

        // Same week (Saturday night), same exercise/muscle group — must land in the same bucket as the Monday set.
        UUID saturdaySession = seedSession(userId, atUtc(monday.plusDays(5), 23));
        seedSet(saturdaySession, exerciseId, new BigDecimal("50"), 5);

        // Following week — must land in a separate bucket.
        UUID nextWeekSession = seedSession(userId, atUtc(monday.plusWeeks(1), 9));
        seedSet(nextWeekSession, exerciseId, new BigDecimal("60"), 10);

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/volume")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("find { it.week_start_utc == '%s' }.total_volume_kg".formatted(atUtc(monday, 0)), equalTo(1250))
                .body("find { it.week_start_utc == '%s' }.total_volume_kg".formatted(atUtc(monday.plusWeeks(1), 0)), equalTo(600));
    }

    @Test
    void plateausListsOnlyExercisesWithActiveStagnation() {
        String token = registerAndGetToken("plateau");
        UUID userId = currentUserId(token);
        UUID stagnantExercise = createExercise(token, "Rosca Direta " + System.nanoTime());
        UUID progressingExercise = createExercise(token, "Desenvolvimento " + System.nanoTime());
        LocalDate monday = anyMonday();

        // 4 sessions, same weight/reps every time — no new PR after the baseline, streak of 3 -> plateau.
        for (int week = 0; week < 4; week++) {
            UUID sessionId = seedSession(userId, atUtc(monday.plusWeeks(week), 9));
            seedSet(sessionId, stagnantExercise, new BigDecimal("40"), 10);
        }

        // 4 sessions, weight increases every time -> always a new PR, never plateaus.
        for (int week = 0; week < 4; week++) {
            UUID sessionId = seedSession(userId, atUtc(monday.plusWeeks(week), 10));
            seedSet(sessionId, progressingExercise, new BigDecimal(String.valueOf(40 + week * 5)), 10);
        }

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/dashboard/plateaus")
                .then().statusCode(200)
                .body("exercise_id", hasItem(stagnantExercise.toString()))
                .body("exercise_id", not(hasItem(progressingExercise.toString())));
    }
}
```

**Note for the implementer on the volume assertion:** `total_volume_kg` is serialized as a JSON number from a `BigDecimal` — Groovy/JsonPath's `equalTo(1250)` compares as `BigDecimal`/`Integer` via `compareTo`-style equality in RestAssured's JSON path matchers, which tolerates the trailing-zero scale difference (`1250` vs `1250.00`). If this comparison turns out to be brittle in practice, replace it with `.body(...).closeTo(1250, 0.01)` (Hamcrest numeric matcher) instead of chasing exact string/scale equality.

- [ ] **Step 2: Run `./mvnw verify`, fix anything red, commit** (`test(backend): cover dashboard progression, volume, and plateau endpoints`)

---

### Task 9: `quarkus:dev`/Testcontainers backlog — time-boxed fix attempt

This is a single time-boxed attempt at candidate fix (a) from `docs/superpowers/notes/technical-backlog.md`: stop forcing `testcontainers` backward and instead bring `org.testcontainers:postgresql` forward to match what `quarkus-bom` already expects. **Hard rule: one attempt, then stop** — if it doesn't resolve cleanly, revert every change from this task and leave the backlog note exactly as it is. Do not leave the repo in a half-fixed or broken state for this.

**Files:**
- Possibly modify: `backend/pom.xml`
- Possibly modify: `docs/superpowers/notes/technical-backlog.md`
- Possibly modify: `README.md`

- [ ] **Step 1: Check what `quarkus-bom` (3.37.3) actually pins `testcontainers` to, and what `org.testcontainers:postgresql` versions exist**

```bash
cd backend
./mvnw dependency:tree -Dincludes=org.testcontainers 2>&1 | head -30
./mvnw versions:display-dependency-updates 2>&1 | grep -i testcontainers
```

Also check available `org.testcontainers:postgresql` releases directly: https://mvnrepository.com/artifact/org.testcontainers/postgresql — look specifically for a release whose own `testcontainers-bom`/core dependency matches (or is compatible with) whatever version `quarkus-bom:3.37.3` pins for `org.testcontainers:testcontainers` (currently `2.0.5` per the existing comment in `pom.xml`'s `<dependencyManagement>`).

- [ ] **Step 2: If a compatible `org.testcontainers:postgresql` version exists, try the fix**

Remove the `<dependencyManagement>` entry in `backend/pom.xml` that forces `org.testcontainers:testcontainers` down to `1.21.4`, and bump the `org.testcontainers:postgresql` test dependency (and its version property) to the compatible release found in Step 1. Then verify both things this repo actually needs to work:

```bash
./mvnw verify
```
Expected: still green — `AuthResourceIT`, `UserResourceIT`, `MuscleGroupResourceIT`, `ExerciseResourceIT`, `RoutineResourceIT`, `WorkoutSessionResourceIT`, `DashboardResourceIT` all pass against the new Testcontainers version.

```bash
docker compose up -d
./mvnw quarkus:dev &
sleep 15
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/q/swagger-ui/
```
Expected: `200`, and the terminal log shows no `NoClassDefFoundError: org/junit/rules/TestRule`. Kill the `quarkus:dev` process afterward (`kill %1` or find the PID and `kill` it).

- [ ] **Step 3a: If both checks in Step 2 passed — the fix worked**

Update `docs/superpowers/notes/technical-backlog.md`: keep the entry (useful historical record of a real bug that was diagnosed and fixed, don't delete it), but add a closing note at the top of the entry stating it was resolved in Sprint 3, what the fix was (the exact version bump), and the date. Update `README.md`'s "Como rodar localmente" section if it currently documents any packaged-jar workaround — restore the plain `./mvnw quarkus:dev` instruction as the primary path.

```bash
git add backend/pom.xml docs/superpowers/notes/technical-backlog.md README.md
git commit -m "fix(backend): resolve quarkus:dev Testcontainers version conflict"
```

- [ ] **Step 3b: If Step 1 found no compatible version, or Step 2's checks failed — stop and revert**

```bash
git checkout -- backend/pom.xml
git status
```

Confirm `git status` shows no pending changes from this task. Leave `docs/superpowers/notes/technical-backlog.md` untouched. Do not create a commit for this task — move directly to Task 10. This is not a failure to fix mid-task; it's the designed outcome of the time-box (see design Decision 8).

---

### Task 10: Wrap-up

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the README Status checklist**

Replace:
```markdown
- [x] Sprint 2 — Exercícios + Rotinas
- [ ] Sprint 3 — Sessões + Domínio Core (1RM, volume, platô)
```
with:
```markdown
- [x] Sprint 2 — Exercícios + Rotinas
- [x] Sprint 3 — Sessões + Domínio Core (1RM, volume, platô)
```

- [ ] **Step 2: Run the full backend verification**

```bash
cd backend && ./mvnw test && ./mvnw verify
```

Both must be green — unit tests (surefire, no Docker, including the three new calculator test classes) and integration tests (failsafe, Testcontainers-backed Postgres, including `WorkoutSessionResourceIT` and `DashboardResourceIT`).

- [ ] **Step 3: Review OpenAPI tag completeness**

Confirm `WorkoutSessionResource` and `DashboardResource` each have a `@Tag` with a distinct `name` and one-line `description` (already the case per Task 5 and Task 7 — this is a final sanity check, not new code). Grep for `@Tag` across `resource/` to confirm no duplicate tag names exist across all 7 resources now in the project.

- [ ] **Step 4: Commit** (`docs: mark Sprint 3 complete in README status`)

---

## Self-Review Checklist (run before starting execution)

- [ ] **Spec coverage** — every section of `docs/superpowers/specs/2026-07-24-sprint3-sessoes-dominio-core-design.md` maps to a task: Decision 1 (session lifecycle, `ON DELETE SET NULL`) → Task 1 (migration) + Task 5 (`WorkoutSessionService.create/finish`); Decision 2 (incremental set logging, per-exercise `set_number`) → Task 5 (`addSet`); Decision 3 (`OneRepMaxCalculator`, nullable Brzycki) → Task 1 (nullable column) + Task 2; Decision 4 (`VolumeCalculator`, week grouping, defensive null muscle group) → Task 3; Decision 5 (`PlateauDetectionService`, streak algorithm) → Task 4; Decision 6 (dashboard endpoints, scoping, empty-list vs 404) → Task 7; Decision 7 (reduction lives in `DashboardService`) → Task 7 (`chronologicalSessionBests`); Decision 8 (backlog investigation) → Task 9. Migrations V7-V8 → Task 1.
- [ ] **Placeholder scan** — no "TBD", no "similar to Task N", no vague instructions; every task has complete, compilable code. Task 9 is the one task with a conditional outcome, but both branches (fix succeeds / fix reverts) have complete, concrete steps — this is a designed time-box, not a placeholder.
- [ ] **Type/name consistency across tasks** — `OneRepMaxCalculator.calculate(BigDecimal, int): OneRepMaxResult` (Task 2) matches its use in `WorkoutSessionService.addSet` (Task 5) and `DashboardResourceIT`'s seeding helper (Task 8); `VolumeCalculator.calculate(List<SetVolumeInput>): Map<VolumeBucketKey, BigDecimal>` (Task 3) matches its use in `DashboardService.volume()` (Task 7); `PlateauDetectionService.detect(List<BigDecimal>): PlateauResult` (Task 4) matches its use in `DashboardService.plateaus()` (Task 7); `WorkoutSessionRepository.findActiveByUser(UUID)` (Task 1) matches its use in `WorkoutSessionService.create` (Task 5); `SessionSetRepository.countBySessionAndExercise`/`.listByUserAndExercise`/`.listByUser` (Task 1) match their uses in Task 5 and Task 7; `ExerciseRepository.findVisibleTo(UUID, UUID)` (Sprint 2, unchanged) is reused as-is in Task 5 and Task 7 — no new method needed there.

## Verification

- `cd backend && ./mvnw test` — unit tests green, no Docker required, includes `OneRepMaxCalculatorTest`, `VolumeCalculatorTest`, `PlateauDetectionServiceTest`.
- `cd backend && ./mvnw verify` — integration tests green (Testcontainers Postgres): all Sprint 0-2 IT classes plus `WorkoutSessionResourceIT`, `DashboardResourceIT`.
- Manual sanity check (optional): `docker compose up -d`, start the backend (packaged jar or `quarkus:dev` if Task 9 fixed it), register a user, `POST /workout-sessions`, `POST /workout-sessions/{id}/sets` a couple of times for the same exercise (confirm `set_number` increments), `PATCH /workout-sessions/{id}` to finish, then `GET /dashboard/volume` and `GET /dashboard/plateaus` to confirm they respond without needing any dashboard-specific setup.
