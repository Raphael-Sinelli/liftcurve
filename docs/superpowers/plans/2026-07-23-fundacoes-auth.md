# Fundações + Schema + Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the gym-progress-tracker monorepo (backend Quarkus + frontend Vite/React) and ship a fully working, tested register/login/refresh/logout flow backed by real PostgreSQL.

**Architecture:** Quarkus REST resource → Service (BO) → Panache Repository (DAO) → JPA entity, with Flyway-managed schema. Access tokens are short-lived JWTs (HS256, SmallRye JWT build API); refresh tokens are opaque random secrets hashed with SHA-256 and stored in Postgres so logout/rotation can really revoke them. Frontend is scaffolded but not wired to the API yet (that starts in the next plan, Sprint 2+).

**Tech Stack:** Java 21 (Temurin), Quarkus 3.37.3 (Maven), PostgreSQL 16, Flyway, SmallRye JWT, BCrypt (quarkus-elytron-security-common), JUnit 5, Testcontainers 1.21.4, RestAssured 6.0.0. Frontend: React 19, Vite 8, TypeScript, Tailwind v4.

## Global Constraints

- DB columns/tables: `snake_case`. Java code: `camelCase`. JSON over the wire: `snake_case` — enforced via `quarkus.jackson.property-naming-strategy=SNAKE_CASE`, never `@JsonProperty` per field.
- All timestamps: `TIMESTAMPTZ` in Postgres, `java.time.Instant` in Java — never `TIMESTAMP`/`LocalDateTime`.
- All primary keys: `UUID`, generated in Java via `UUID.randomUUID()` (not a DB or Hibernate generator).
- Every error response body: `{"error": {"code": ..., "message": ..., "status": ..., "details": [...]}}`.
- Integration tests use a real Postgres via Testcontainers — never mock the database. Files ending `IT.java` run under `mvn verify` (failsafe); files ending `Test.java` run under `mvn test` (surefire) and must need zero Docker.
- BCrypt truncates input silently past 72 bytes — password fields must be validated `@Size(max = 72)`.
- Do not `git push` — commits are created locally only; the user reviews and pushes.

---

### Task 1: Backend Maven/Quarkus Scaffold

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/GreetingResource.java`
- Create (generated): `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/*`

**Interfaces:**
- Produces: Maven project at `backend/` buildable with `mvn compile`; base package `com.rsinelli.gymtracker`.

- [ ] **Step 1: Create the directory and pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.rsinelli</groupId>
  <artifactId>gym-progress-tracker-backend</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <compiler-plugin.version>3.13.0</compiler-plugin.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.version>3.37.3</quarkus.platform.version>
    <surefire-plugin.version>3.5.2</surefire-plugin.version>
    <testcontainers.version>1.21.4</testcontainers.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>${quarkus.platform.artifact-id}</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <!-- Quarkus BOM pins org.testcontainers:testcontainers to 2.0.5, but the
           org.testcontainers:postgresql module's newest release is 1.21.4 —
           force both back to the matching 1.21.4 line so the two modules
           don't end up on incompatible major versions of Testcontainers. -->
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-rest</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-rest-jackson</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-flyway</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-jwt</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-jwt-build</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-elytron-security-common</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-hibernate-validator</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-smallrye-openapi</artifactId>
    </dependency>

    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-junit5</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.rest-assured</groupId>
      <artifactId>rest-assured</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <version>${testcontainers.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.platform.version}</version>
        <extensions>true</extensions>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
              <goal>generate-code</goal>
              <goal>generate-code-tests</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>${compiler-plugin.version}</version>
        <configuration>
          <parameters>true</parameters>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>${surefire-plugin.version}</version>
        <configuration>
          <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
          </systemPropertyVariables>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <version>${surefire-plugin.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
            <configuration>
              <systemPropertyVariables>
                <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
              </systemPropertyVariables>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create `application.properties`**

```properties
quarkus.application.name=gym-progress-tracker-backend

quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME:gymtracker}
quarkus.datasource.password=${DB_PASSWORD:gymtracker}
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/gymtracker}

quarkus.hibernate-orm.database.generation=validate
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true

quarkus.jackson.property-naming-strategy=SNAKE_CASE

gymtracker.jwt.secret=${JWT_SECRET:dev-only-insecure-secret-change-in-prod-minimum-32-bytes-long}
gymtracker.jwt.access-token-ttl-minutes=15
gymtracker.jwt.refresh-token-ttl-days=30

mp.jwt.verify.issuer=gym-progress-tracker
mp.jwt.verify.secretkey=${gymtracker.jwt.secret}
```

- [ ] **Step 3: Create a trivial smoke-test resource**

```java
package com.rsinelli.gymtracker.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/ping")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "pong";
    }
}
```

- [ ] **Step 4: Compile to verify the scaffold**

Run (from `backend/`): `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Generate the Maven Wrapper for portability**

Run (from `backend/`): `mvn -N wrapper:wrapper -Dmaven=3.9.16`
Expected: creates `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/`. Verify with: `./mvnw -version` (or `mvnw.cmd -version` on Windows) → prints Maven 3.9.16 / Java 21.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src backend/mvnw backend/mvnw.cmd backend/.mvn
git commit -m "feat(backend): scaffold Quarkus project with Maven wrapper"
```

---

### Task 2: Frontend Vite/React/TS Scaffold + Tailwind v4

**Files:**
- Create: `frontend/` (via `npm create vite@latest`)
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/index.css`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Produces: Vite project at `frontend/` buildable with `npm run build`; Tailwind utility classes available in any `.tsx`.

- [ ] **Step 1: Scaffold the Vite project**

Run (from repo root): `npm create vite@latest frontend -- --template react-ts`
Expected: creates `frontend/` with a working React+TS Vite template, non-interactively.

- [ ] **Step 2: Install dependencies and Tailwind v4**

Run (from `frontend/`):
```bash
npm install
npm install tailwindcss @tailwindcss/vite
```

- [ ] **Step 3: Wire the Tailwind Vite plugin**

Replace `frontend/vite.config.ts` with:

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
})
```

- [ ] **Step 4: Replace the stylesheet**

Replace the full contents of `frontend/src/index.css` with:

```css
@import "tailwindcss";
```

- [ ] **Step 5: Replace App.tsx with a Tailwind smoke test**

Replace the full contents of `frontend/src/App.tsx` with:

```tsx
function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-50">
      <h1 className="text-3xl font-semibold tracking-tight">
        gym-progress-tracker
      </h1>
    </main>
  )
}

export default App
```

- [ ] **Step 6: Build to verify**

Run (from `frontend/`): `npm run build`
Expected: `vite build` completes with no TypeScript errors, `dist/` created.

- [ ] **Step 7: Commit**

```bash
git add frontend
git commit -m "feat(frontend): scaffold Vite/React/TS project with Tailwind v4"
```

---

### Task 3: Docker Compose (Postgres only)

**Files:**
- Create: `docker-compose.yml`

**Interfaces:**
- Produces: a `postgres` service reachable at `localhost:5432` with database `gymtracker` / user `gymtracker` / password `gymtracker` — matches the defaults in `backend/src/main/resources/application.properties` from Task 1.

- [ ] **Step 1: Create docker-compose.yml**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: gymtracker
      POSTGRES_USER: gymtracker
      POSTGRES_PASSWORD: gymtracker
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gymtracker"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  postgres_data:
```

- [ ] **Step 2: Verify the compose file is valid**

Run: `docker compose config`
Expected: prints the resolved config with no errors. (If Docker Desktop hasn't finished its first-launch setup yet, this step may fail with a daemon-connection error — note it and move on; it isn't required to unblock later tasks in this plan.)

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(infra): add docker-compose with Postgres for local dev"
```

---

### Task 4: GitHub Actions CI Skeleton

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: two CI jobs, `backend` and `frontend`, triggered on PRs and pushes to `main`.

- [ ] **Step 1: Create the workflow file**

```yaml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Run unit tests
        run: mvn -B test
      - name: Run integration tests (Testcontainers)
        run: mvn -B verify

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
      - run: npm run build
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for backend and frontend"
```

*(This workflow will only actually run once the repo has a GitHub remote and a pushed branch/PR — nothing to verify locally beyond YAML validity.)*

---

### Task 5: README

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write the README**

```markdown
# Gym Progress Tracker

Projeto de portfólio: rastreador de progressão de treino com estimativa de 1RM (Epley +
Brzycki), cálculo de volume por grupo muscular e detecção automática de platô.

> Em desenvolvimento. Ver `docs/superpowers/specs/` para o design completo e
> `docs/superpowers/plans/` para os planos de implementação sprint a sprint.

## Stack

- **Backend:** Java 21, Quarkus, PostgreSQL, JWT (access token) + refresh token opaco, Flyway
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS, Recharts
- **Testes:** JUnit 5 (unitário) + Testcontainers/RestAssured (integração) no backend, Vitest no frontend
- **Infra:** Docker Compose, GitHub Actions

## Como rodar localmente

Pré-requisitos: Java 21, Node 20+, Docker Desktop.

```bash
# sobe o Postgres
docker compose up -d

# backend (http://localhost:8080)
cd backend
./mvnw quarkus:dev      # Windows: mvnw.cmd quarkus:dev

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

## Testes

```bash
# backend — unitários (sem Docker)
cd backend && ./mvnw test

# backend — integração (precisa de Docker rodando)
cd backend && ./mvnw verify

# frontend
cd frontend && npm run build
```

## Status

- [x] Sprint 0 — Fundações
- [ ] Sprint 1 — Schema + Auth
- [ ] Sprint 2 — Exercícios + Rotinas
- [ ] Sprint 3 — Sessões + Domínio Core (1RM, volume, platô)
- [ ] Sprint 4 — API polish + Seed
- [ ] Sprint 5 — Frontend Core
- [ ] Sprint 6 — Testes, CI/CD, Deploy
- [ ] Sprint 7 — README final + Polish
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add initial README"
```

---

### Task 6: Flyway Migrations + Entities + Repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_users.sql`
- Create: `backend/src/main/resources/db/migration/V2__create_refresh_tokens.sql`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/UserEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/entity/RefreshTokenEntity.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/UserRepository.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/repository/RefreshTokenRepository.java`

**Interfaces:**
- Produces: `UserRepository.findByEmail(String): Optional<UserEntity>`, `RefreshTokenRepository.findByTokenHash(String): Optional<RefreshTokenEntity>` — both consumed by `AuthService` in Task 10.

- [ ] **Step 1: Migration V1 — users**

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
```

- [ ] **Step 2: Migration V2 — refresh_tokens**

```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
```

- [ ] **Step 3: UserEntity**

```java
package com.rsinelli.gymtracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 4: RefreshTokenEntity**

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
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 5: UserRepository**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

    public Optional<UserEntity> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
```

- [ ] **Step 6: RefreshTokenRepository**

```java
package com.rsinelli.gymtracker.repository;

import com.rsinelli.gymtracker.entity.RefreshTokenEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepositoryBase<RefreshTokenEntity, UUID> {

    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }
}
```

- [ ] **Step 7: Compile to verify**

Run (from `backend/`): `./mvnw compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/main/java/com/rsinelli/gymtracker/entity backend/src/main/java/com/rsinelli/gymtracker/repository
git commit -m "feat(backend): add users/refresh_tokens schema, entities, and repositories"
```

---

### Task 7: PasswordHasher (TDD)

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/unit/PasswordHasherTest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/security/PasswordHasher.java`

**Interfaces:**
- Produces: `PasswordHasher.hash(String): String`, `PasswordHasher.matches(String, String): boolean` — consumed by `AuthService` in Task 10.

- [ ] **Step 1: Write the failing test**

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesPasswordAndVerifiesMatch() {
        String hash = passwordHasher.hash("correct-horse-battery-staple");

        assertTrue(passwordHasher.matches("correct-horse-battery-staple", hash));
    }

    @Test
    void rejectsWrongPassword() {
        String hash = passwordHasher.hash("correct-horse-battery-staple");

        assertFalse(passwordHasher.matches("wrong-password", hash));
    }

    @Test
    void producesDifferentHashesForSamePasswordDueToSalt() {
        String hash1 = passwordHasher.hash("same-password");
        String hash2 = passwordHasher.hash("same-password");

        assertNotEquals(hash1, hash2);
    }
}
```

- [ ] **Step 2: Run to verify it fails (class doesn't exist yet)**

Run: `./mvnw test -Dtest=PasswordHasherTest`
Expected: compile error — `PasswordHasher` cannot be resolved.

- [ ] **Step 3: Implement PasswordHasher**

```java
package com.rsinelli.gymtracker.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordHasher {

    public String hash(String plainPassword) {
        return BcryptUtil.bcryptHash(plainPassword);
    }

    public boolean matches(String plainPassword, String hash) {
        return BcryptUtil.matches(plainPassword, hash);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=PasswordHasherTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/security/PasswordHasher.java backend/src/test/java/com/rsinelli/gymtracker/unit/PasswordHasherTest.java
git commit -m "feat(backend): add BCrypt password hasher with unit tests"
```

---

### Task 8: TokenService (TDD)

**Files:**
- Create: `backend/src/test/java/com/rsinelli/gymtracker/unit/TokenServiceTest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/security/TokenService.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `TokenService(String jwtSecret, long accessTokenTtlMinutes, long refreshTokenTtlDays)` constructor (CDI-injected via `@ConfigProperty` in production, called directly with literals in unit tests). Methods: `generateAccessToken(UUID userId): String`, `generateRefreshToken(): String`, `hashRefreshToken(String rawToken): String`, `accessTokenTtl(): Duration`, `refreshTokenTtl(): Duration`. All consumed by `AuthService` in Task 10.

- [ ] **Step 1: Write the failing test**

```java
package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.security.TokenService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TokenServiceTest {

    private final TokenService tokenService =
            new TokenService("unit-test-secret-must-be-long-enough-for-hs256", 15, 30);

    @Test
    void generatesNonBlankAccessTokenWithThreeJwtSegments() {
        String token = tokenService.generateAccessToken(UUID.randomUUID());

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void generatesUniqueRefreshTokensEachCall() {
        String first = tokenService.generateRefreshToken();
        String second = tokenService.generateRefreshToken();

        assertNotEquals(first, second);
    }

    @Test
    void hashRefreshTokenIsDeterministicForSameInput() {
        String raw = tokenService.generateRefreshToken();

        assertEquals(tokenService.hashRefreshToken(raw), tokenService.hashRefreshToken(raw));
    }

    @Test
    void hashRefreshTokenDiffersForDifferentInput() {
        assertNotEquals(tokenService.hashRefreshToken("token-a"), tokenService.hashRefreshToken("token-b"));
    }

    @Test
    void accessTokenTtlMatchesConfiguredMinutes() {
        assertEquals(15, tokenService.accessTokenTtl().toMinutes());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=TokenServiceTest`
Expected: compile error — `TokenService` cannot be resolved.

- [ ] **Step 3: Implement TokenService**

```java
package com.rsinelli.gymtracker.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class TokenService {

    private static final String ISSUER = "gym-progress-tracker";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String jwtSecret;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    @Inject
    public TokenService(
            @ConfigProperty(name = "gymtracker.jwt.secret") String jwtSecret,
            @ConfigProperty(name = "gymtracker.jwt.access-token-ttl-minutes") long accessTokenTtlMinutes,
            @ConfigProperty(name = "gymtracker.jwt.refresh-token-ttl-days") long refreshTokenTtlDays) {
        this.jwtSecret = jwtSecret;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(UUID userId) {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwt.claims()
                .issuer(ISSUER)
                .subject(userId.toString())
                .expiresIn(Duration.ofMinutes(accessTokenTtlMinutes))
                .sign(key);
    }

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public Duration refreshTokenTtl() {
        return Duration.ofDays(refreshTokenTtlDays);
    }

    /** Opaque bearer secret, not a JWT — see hashRefreshToken() for why it's hashed differently than passwords. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt: this token is already 256 bits of random entropy so it
     * needs no slow key-derivation, and BCrypt would silently truncate the base64
     * string past 72 bytes anyway.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=TokenServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/security/TokenService.java backend/src/test/java/com/rsinelli/gymtracker/unit/TokenServiceTest.java
git commit -m "feat(backend): add TokenService for JWT access + opaque refresh tokens"
```

---

### Task 9: DTOs + Centralized Error Handling

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/RefreshRequest.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/ErrorResponse.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/ApiException.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/exception/ApiExceptionMapper.java`

**Interfaces:**
- Produces: `RegisterRequest(String email, String password, String name)`, `LoginRequest(String email, String password)`, `RefreshRequest(String refreshToken)`, `AuthResponse(String accessToken, String refreshToken, long expiresInSeconds)` — all consumed by `AuthResource`/`AuthService` in Task 10. `ApiException(String code, String message, Response.Status status)` — thrown by `AuthService` in Task 10, caught by `ApiExceptionMapper` here.

- [ ] **Step 1: RegisterRequest**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String name) {
}
```

- [ ] **Step 2: LoginRequest**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
```

- [ ] **Step 3: RefreshRequest**

```java
package com.rsinelli.gymtracker.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
```

- [ ] **Step 4: AuthResponse**

```java
package com.rsinelli.gymtracker.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
```

- [ ] **Step 5: ErrorResponse**

```java
package com.rsinelli.gymtracker.exception;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, int status, List<FieldError> details) {
    }

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(new ErrorBody(code, message, status, List.of()));
    }

    public static ErrorResponse of(String code, String message, int status, List<FieldError> details) {
        return new ErrorResponse(new ErrorBody(code, message, status, details));
    }
}
```

- [ ] **Step 6: ApiException**

```java
package com.rsinelli.gymtracker.exception;

import jakarta.ws.rs.core.Response;

public class ApiException extends RuntimeException {

    private final String code;
    private final Response.Status status;

    public ApiException(String code, String message, Response.Status status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public Response.Status getStatus() {
        return status;
    }
}
```

- [ ] **Step 7: ApiExceptionMapper**

```java
package com.rsinelli.gymtracker.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof ApiException apiException) {
            return Response.status(apiException.getStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of(apiException.getCode(), apiException.getMessage(),
                            apiException.getStatus().getStatusCode()))
                    .build();
        }

        if (exception instanceof ConstraintViolationException constraintViolationException) {
            List<ErrorResponse.FieldError> details = constraintViolationException.getConstraintViolations().stream()
                    .map(this::toFieldError)
                    .toList();
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorResponse.of("VALIDATION_ERROR", "Dados inválidos.",
                            Response.Status.BAD_REQUEST.getStatusCode(), details))
                    .build();
        }

        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErrorResponse.of("INTERNAL_ERROR", "Erro interno inesperado.",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
                .build();
    }

    private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        return new ErrorResponse.FieldError(field, violation.getMessage());
    }
}
```

- [ ] **Step 8: Compile to verify**

Run: `./mvnw compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/dto backend/src/main/java/com/rsinelli/gymtracker/exception
git commit -m "feat(backend): add auth DTOs and centralized error handling"
```

---

### Task 10: AuthService + AuthResource + Integration Tests

**Files:**
- Create: `backend/src/main/java/com/rsinelli/gymtracker/service/AuthService.java`
- Create: `backend/src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/PostgresTestResource.java`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/AuthResourceIT.java`

**Interfaces:**
- Consumes: `UserRepository`, `RefreshTokenRepository` (Task 6), `PasswordHasher`, `TokenService` (Tasks 7-8), `RegisterRequest`/`LoginRequest`/`RefreshRequest`/`AuthResponse`/`ApiException` (Task 9).
- Produces: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`.

This task has no separate "unit test first" step — `AuthService`/`AuthResource` correctness is inherently about DB + HTTP wiring, so the integration test *is* the red/green cycle here (matches the project's own testing philosophy: no DB mocks).

- [ ] **Step 1: Write AuthService**

```java
package com.rsinelli.gymtracker.service;

import com.rsinelli.gymtracker.dto.AuthResponse;
import com.rsinelli.gymtracker.entity.RefreshTokenEntity;
import com.rsinelli.gymtracker.entity.UserEntity;
import com.rsinelli.gymtracker.exception.ApiException;
import com.rsinelli.gymtracker.repository.RefreshTokenRepository;
import com.rsinelli.gymtracker.repository.UserRepository;
import com.rsinelli.gymtracker.security.PasswordHasher;
import com.rsinelli.gymtracker.security.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.time.Instant;

@ApplicationScoped
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    TokenService tokenService;

    @Transactional
    public AuthResponse register(String email, String password, String name) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException("EMAIL_ALREADY_REGISTERED", "Este email já está cadastrado.", Response.Status.CONFLICT);
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setName(name.trim());
        userRepository.persist(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS", "Email ou senha inválidos.", Response.Status.UNAUTHORIZED));

        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new ApiException("INVALID_CREDENTIALS", "Email ou senha inválidos.", Response.Status.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException("INVALID_REFRESH_TOKEN", "Refresh token inválido.", Response.Status.UNAUTHORIZED));

        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("INVALID_REFRESH_TOKEN", "Refresh token inválido ou expirado.", Response.Status.UNAUTHORIZED);
        }

        storedToken.setRevokedAt(Instant.now());

        return issueTokens(storedToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private AuthResponse issueTokens(UserEntity user) {
        String accessToken = tokenService.generateAccessToken(user.getId());
        String rawRefreshToken = tokenService.generateRefreshToken();

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(tokenService.hashRefreshToken(rawRefreshToken));
        refreshTokenEntity.setExpiresAt(Instant.now().plus(tokenService.refreshTokenTtl()));
        refreshTokenRepository.persist(refreshTokenEntity);

        return new AuthResponse(accessToken, rawRefreshToken, tokenService.accessTokenTtl().toSeconds());
    }
}
```

*(Note: entities fetched via `userRepository`/`refreshTokenRepository` inside an active `@Transactional` method are managed — mutating them via setters, as `refresh()` and `logout()` do, is enough for Hibernate to flush the change; no explicit `persist()` call needed on an already-managed entity.)*

- [ ] **Step 2: Write AuthResource**

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.AuthResponse;
import com.rsinelli.gymtracker.dto.LoginRequest;
import com.rsinelli.gymtracker.dto.RefreshRequest;
import com.rsinelli.gymtracker.dto.RegisterRequest;
import com.rsinelli.gymtracker.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.name());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(@Valid RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return Response.ok(response).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Response.noContent().build();
    }
}
```

- [ ] **Step 3: Write the Testcontainers resource manager**

```java
package com.rsinelli.gymtracker.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("gymtracker_test")
                .withUsername("gymtracker_test")
                .withPassword("gymtracker_test");
        postgres.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword());
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
```

- [ ] **Step 4: Write the integration test**

```java
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
                .then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(newRefreshToken))
                .when().post("/auth/logout")
                .then().statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body("{\"refresh_token\": \"%s\"}".formatted(newRefreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(401);
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
```

- [ ] **Step 5: Run the integration tests**

Run (from `backend/`, requires Docker Desktop running): `./mvnw verify`
Expected: `Tests run: 3, Failures: 0, Errors: 0` for `AuthResourceIT`, and `BUILD SUCCESS` overall.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/service backend/src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java backend/src/test/java/com/rsinelli/gymtracker/integration
git commit -m "feat(backend): implement register/login/refresh/logout with full integration test coverage"
```

---

### Task 11: OpenAPI Annotations + Sprint Wrap-up

**Files:**
- Modify: `backend/src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java`

- [ ] **Step 1: Add OpenAPI annotations to AuthResource**

Replace the full contents of `AuthResource.java` with:

```java
package com.rsinelli.gymtracker.resource;

import com.rsinelli.gymtracker.dto.AuthResponse;
import com.rsinelli.gymtracker.dto.LoginRequest;
import com.rsinelli.gymtracker.dto.RefreshRequest;
import com.rsinelli.gymtracker.dto.RegisterRequest;
import com.rsinelli.gymtracker.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Tag(name = "Auth", description = "Registro, login e ciclo de vida de tokens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    @Operation(summary = "Cria uma nova conta de usuário")
    @APIResponse(responseCode = "201", description = "Conta criada, tokens emitidos")
    @APIResponse(responseCode = "409", description = "Email já cadastrado")
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request.email(), request.password(), request.name());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    @Operation(summary = "Autentica um usuário existente")
    @APIResponse(responseCode = "200", description = "Login bem-sucedido, tokens emitidos")
    @APIResponse(responseCode = "401", description = "Credenciais inválidas")
    public Response login(@Valid LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Rotaciona o refresh token e emite novo access token")
    @APIResponse(responseCode = "200", description = "Novo par de tokens emitido")
    @APIResponse(responseCode = "401", description = "Refresh token inválido, expirado ou já revogado")
    public Response refresh(@Valid RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return Response.ok(response).build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Revoga o refresh token, encerrando a sessão")
    @APIResponse(responseCode = "204", description = "Sessão encerrada")
    public Response logout(@Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Response.noContent().build();
    }
}
```

- [ ] **Step 2: Full verification**

Run (from `backend/`): `./mvnw verify`
Expected: `BUILD SUCCESS`, all `*Test.java` and `*Test.IT.java` green.

Manual smoke test (optional, requires `docker compose up -d` + `./mvnw quarkus:dev` running):
```bash
curl http://localhost:8080/q/openapi
```
Expected: OpenAPI YAML/JSON document listing the four `/auth/*` operations with the descriptions above.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java
git commit -m "docs(backend): annotate auth endpoints for OpenAPI"
```

---

## Self-Review Notes

- **Spec coverage:** Sprint 0 (scaffold, docker-compose Postgres-only, CI skeleton, README) and Sprint 1 (schema, JWT+refresh auth, BCrypt, Testcontainers integration tests) from the design spec are both fully covered by Tasks 1-11.
- **Deferred to the next plan (Sprint 2+):** exercises/routines CRUD, `muscle_groups` seed, workout sessions, the 1RM/volume/plateau domain services, frontend-to-API wiring, full 3-service docker-compose, backend/frontend Dockerfiles, and deploy — these depend on schema and endpoints this plan doesn't touch yet, so they're scoped to a follow-up plan rather than bolted on here.
- **Type consistency check:** `TokenService` constructor signature `(String, long, long)` used identically in Task 8's test and Task 8's implementation. `AuthService` field names (`userRepository`, `refreshTokenRepository`, `passwordHasher`, `tokenService`) match the class names produced in Tasks 6-8. DTO record accessors (`request.email()`, `request.password()`, `request.name()`, `request.refreshToken()`) match the record component names declared in Task 9.
