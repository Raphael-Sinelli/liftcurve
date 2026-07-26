# Sprint 6 — Testes, CI/CD, Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the gap between "runs on my machine with just Postgres in Docker" and "runs for real in production" — complete Docker Compose (3 services), production CORS, security headers, a final security review, complete CI, and full deploy documentation for Render (backend) + Vercel (frontend).

**Architecture:** No new domain logic this sprint — pure infrastructure, configuration, and documentation on top of an already-complete product (backend + frontend both feature-complete since Sprint 5b). Backend Dockerfile is a multi-stage JVM build (Maven build stage → JRE runtime stage, no GraalVM native). Frontend Dockerfile is a multi-stage build (Node build stage → Nginx static-serve stage, with SPA fallback routing). CORS/headers are Quarkus config properties, not new Java code. CI gets a trigger fix (it has never actually fired for this project's real integration branch) plus a new Docker-build-validation job.

**Tech Stack:** Docker (multi-stage builds), Nginx (static file serving + SPA fallback), Quarkus config properties (`quarkus.http.cors.*`, `quarkus.http.header.*`), GitHub Actions, Render (Docker web service + managed Postgres), Vercel (static site + environment variables).

## Global Constraints

- No GraalVM native image — backend Dockerfile is JVM mode only (confirmed: `backend/pom.xml` has no native profile).
- `docker-compose.yml`'s 3-service topology is for LOCAL dev/demo convenience only — it is NOT the same topology as production (Render backend + Vercel frontend are separate platforms, not a shared Docker network). Don't conflate the two when reasoning about hostnames/URLs.
- `VITE_API_BASE_URL` is a Vite **build-time** environment variable — it gets baked into the static JS bundle when `npm run build` runs. It has no effect if set only at container/server runtime. This applies to both the Docker frontend image (must be passed as a build `ARG`) and Vercel (must be set before/during the build step, not as a runtime-only env var).
- `CORS_ALLOWED_ORIGINS` in production must be the exact frontend origin — never `*` (explicit user requirement).
- `JWT_SECRET` already has a committed dev-fallback value in `application.properties` (`Yb6-3fvO5B7NLqSuo9fJ4r_MmCS9rgwIegNlKXqJzgw`) — this is fine for local dev/Docker Compose, but production MUST set a freshly-generated value and must never rely on this fallback silently applying.
- No backend domain-logic changes this sprint — only `application.properties` (CORS, headers, port) and new test files.
- Commit convention: `chore(docker): ...` for Dockerfiles/compose, `fix(backend): ...` for CORS/headers/property changes, `test(backend): ...` for new IT tests, `ci: ...` for workflow changes, `docs: ...` for documentation.
- Never push to the remote (`origin`, already configured to `https://github.com/Raphael-Sinelli/liftcurve.git`) without explicit user approval — this applies with extra weight this sprint since the remote and a real deploy are now actually in play, not hypothetical.
- The actual Render/Vercel deploy (creating services, pasting env vars into dashboards, clicking deploy) is NOT a task in this plan — it's an interactive phase done directly with the user after every task here is complete, reviewed, and merged, and only after the user explicitly approves a push. `docs/DEPLOY.md` (Task 7) is the reference the two of you follow when that happens.

---

### Task 1: Backend Dockerfile + `docker-compose.yml` (serviço backend)

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: a `backend` Docker image listening on port 8080, consumed by `docker-compose.yml`'s `frontend` service (Task 2) via `depends_on`, and by CI's `docker-build` job (Task 6).

- [ ] **Step 1: Create the backend Dockerfile**

Create `backend/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /deployments

COPY --from=build /workspace/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /deployments/
COPY --from=build /workspace/target/quarkus-app/app/ /deployments/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

Note: this Dockerfile does NOT use the project's `./mvnw` wrapper — the `maven:3.9-eclipse-temurin-21` build-stage image already has its own `mvn`, and tests already run separately in CI (`mvn -B test`/`mvn -B verify`), so `-DskipTests` here is intentional (not a shortcut — re-running the full Testcontainers integration suite inside every Docker build would be slow and redundant, not safer).

- [ ] **Step 2: Create `.dockerignore`**

Create `backend/.dockerignore`:
```
target/
*.log
.git
```

- [ ] **Step 3: Verify the image builds standalone**

Run:
```bash
cd backend && docker build -t gym-progress-tracker-backend .
```
Expected: build succeeds (this will take a couple of minutes the first time — downloads the Maven/JDK and JRE base images, then runs the full `mvn package`). No errors.

- [ ] **Step 4: Add the backend service to `docker-compose.yml`**

Replace the full content of `docker-compose.yml` (currently only has the `postgres` service) with:
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

  backend:
    build: ./backend
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/gymtracker
      DB_USERNAME: gymtracker
      DB_PASSWORD: gymtracker
      CORS_ALLOWED_ORIGINS: http://localhost:5173,http://localhost:8081
      GYMTRACKER_SEED_DEMO: "true"
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

Note: `DB_URL` points at `postgres` (the Compose service name, resolved via Docker's internal DNS) — NOT `localhost`. This is different from `application.properties`'s own default (`jdbc:postgresql://localhost:5432/gymtracker`), which stays correct for running the backend directly on the host via `./mvnw quarkus:dev` (unchanged, still talks to the host-mapped Postgres port). Both paths keep working; this Compose override only applies when the backend itself also runs inside a container. `CORS_ALLOWED_ORIGINS` here includes both the Vite dev server port (`5173`) and the port the Dockerized frontend will use (`8081`, wired in Task 2) — Quarkus's `quarkus.http.cors.origins` property accepts a comma-separated list, so both work simultaneously. `GYMTRACKER_SEED_DEMO=true` is set here deliberately (Compose is meant to be a "clone the repo, run one command, see a populated demo" experience) — this does not change `application.properties`'s own default of `false` for any other way of running the app.

- [ ] **Step 5: Verify Postgres + backend come up together and the backend responds**

Run:
```bash
docker compose up -d --build postgres backend
```
Wait for both to report healthy/running, then:
```bash
curl -sf http://localhost:8080/muscle-groups
```
Expected: a JSON array of muscle groups (200 OK) — this is a public, unauthenticated endpoint, so a bare `curl` without a token proves the whole boot sequence (Flyway migration, demo seed since `GYMTRACKER_SEED_DEMO=true`) completed successfully.

Then tear down before moving to Task 2 (which will bring the full 3-service stack up again):
```bash
docker compose down
```

- [ ] **Step 6: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore docker-compose.yml
git commit -m "chore(docker): adicionar Dockerfile do backend e servico no docker-compose"
```

---

### Task 2: Frontend Dockerfile (Nginx + fallback de SPA) + `docker-compose.yml` (serviço frontend)

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: the `backend` service from Task 1 (via `depends_on`, no health-gate needed — see rationale below).
- Produces: a `frontend` Docker image serving the built SPA on port 80, consumed by CI's `docker-build` job (Task 6).

- [ ] **Step 1: Create the frontend Dockerfile**

Create `frontend/Dockerfile`:
```dockerfile
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`VITE_API_BASE_URL` MUST be passed as a build `ARG` (not a runtime env var) — Vite bakes it into the JS bundle at `npm run build` time. Since the actual API caller is the **browser**, not the Nginx container, this value must be a URL the browser can reach (`http://localhost:8080` for local Docker Compose use, wired in Step 4 below) — a Docker-internal hostname like `http://backend:8080` would never resolve from the browser.

- [ ] **Step 2: Create the Nginx config with SPA fallback routing**

Create `frontend/nginx.conf`:
```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Without `try_files ... /index.html`, refreshing the browser on a client-side route like `/dashboard` or `/sessions/{id}` would hit Nginx looking for a physical file at that path — which doesn't exist — and return a 404, instead of falling through to `index.html` so React Router can take over and render the right screen.

- [ ] **Step 3: Create `.dockerignore`**

Create `frontend/.dockerignore`:
```
node_modules/
dist/
.env
.env.local
```

- [ ] **Step 4: Add the frontend service to `docker-compose.yml`**

Add this service to `docker-compose.yml` (after the `backend` service, before the closing `volumes:` key):
```yaml
  frontend:
    build:
      context: ./frontend
      args:
        VITE_API_BASE_URL: http://localhost:8080
    ports:
      - "8081:80"
    depends_on:
      - backend
```

`depends_on: [backend]` here is a plain dependency (start-order hint only, no `condition:`) — unlike the backend's dependency on Postgres, Nginx doesn't need the backend to be up to start serving static files; the browser makes the actual API calls later, independent of the frontend container's own boot sequence.

- [ ] **Step 5: Verify the full 3-service stack, including the SPA fallback**

Run:
```bash
docker compose up -d --build
```
Wait for all 3 services, then:
```bash
curl -sf http://localhost:8081/ | grep -o '<div id="root">'
```
Expected: matches (confirms the built `index.html` is served at the root path).

Then the actual SPA-fallback check — this is the one that would silently break without the `nginx.conf` from Step 2:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/dashboard
```
Expected: `200` (NOT `404`) — proves a direct request to a client-side route falls through to `index.html` instead of Nginx returning its default 404 page.

Tear down:
```bash
docker compose down
```

- [ ] **Step 6: Commit**

```bash
git add frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore docker-compose.yml
git commit -m "chore(docker): adicionar Dockerfile do frontend com Nginx e fallback de SPA"
```

---

### Task 3: CORS de produção + `PORT`

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/CorsIT.java`

**Interfaces:** none new — this is a config-only change plus a test proving it, no new Java classes consumed elsewhere.

- [ ] **Step 1: Add CORS and `PORT` configuration**

Append to the end of `backend/src/main/resources/application.properties`:
```properties

quarkus.http.port=${PORT:8080}

quarkus.http.cors=true
quarkus.http.cors.origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Content-Type,Authorization
```
`quarkus.http.port=${PORT:8080}` lets Render (or any platform that injects a `PORT` env var) control which port the app binds to, while defaulting to `8080` for local/Compose use — unchanged behavior everywhere except a real Render deploy. `Authorization` must be explicitly listed in `quarkus.http.cors.headers` — it is not one of the browser's CORS-safelisted headers, and it's exactly where the Bearer token travels on every authenticated request. No `Access-Control-Allow-Credentials` — this app never uses cookies for auth, only Bearer tokens, so it isn't needed.

- [ ] **Step 2: Write the CORS integration test**

First, read any existing IT class under `backend/src/test/java/com/rsinelli/gymtracker/integration/` (e.g. `MuscleGroupResourceIT` or similar) to confirm the exact `@QuarkusTestResource` class name/import used in this project (it should be something like `PostgresTestResource` in the same `integration` package, established since Sprint 1) — match that exact import/annotation rather than assuming. If it differs from what's shown below, use the project's actual convention.

Create `backend/src/test/java/com/rsinelli/gymtracker/integration/CorsIT.java`:
```java
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
            .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"));
    }

    @Test
    void disallowedOriginDoesNotReceiveCorsHeader() {
        given()
            .header("Origin", "https://evil.example.com")
        .when()
            .get("/muscle-groups")
        .then()
            .statusCode(200)
            .header("Access-Control-Allow-Origin", nullValue());
    }
}
```
`/muscle-groups` is used as the target because it's public and unauthenticated (confirmed in `MuscleGroupResource.java`), which isolates CORS behavior from any auth concern — both tests hit the same real, always-200 endpoint, varying only the `Origin` header. The test relies on the DEFAULT CORS origin (`http://localhost:5173`, from the property's own fallback) since IT tests don't set the `CORS_ALLOWED_ORIGINS` env var.

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test -Dtest=CorsIT`
Expected: `Tests run: 2, Failures: 0, Errors: 0` — both pass on the first run, since this test proves NEW config, not a bug fix (no red step expected here beyond confirming the class compiles before the property existed — if you want a strict red/green cycle, run the test once before Step 1's property change and confirm it fails with a null/mismatched header, then apply Step 1 and re-run to confirm it passes).

- [ ] **Step 4: Run the full backend suite to confirm nothing else broke**

Run: `cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify`
Expected: `BUILD SUCCESS`, all existing unit + integration tests still green alongside the 2 new `CorsIT` tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.properties backend/src/test/java/com/rsinelli/gymtracker/integration/CorsIT.java
git commit -m "fix(backend): configurar CORS de producao e PORT configuravel"
```

---

### Task 4: Headers de segurança (backend + `vercel.json`)

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/test/java/com/rsinelli/gymtracker/integration/SecurityHeadersIT.java`
- Create: `frontend/vercel.json`

**Interfaces:** none new.

- [ ] **Step 1: Add security header configuration**

Append to the end of `backend/src/main/resources/application.properties`:
```properties

quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."X-Frame-Options".value=DENY
quarkus.http.header."Referrer-Policy".value=strict-origin-when-cross-origin
quarkus.http.header."Strict-Transport-Security".value=max-age=31536000; includeSubDomains
```
Content-Security-Policy is deliberately NOT added on the backend — this API serves only JSON to authenticated clients (no HTML rendered to end users), so CSP wouldn't protect anything meaningful here. It belongs on the frontend instead (Step 3), where HTML/JS actually reaches the browser.

- [ ] **Step 2: Write the security headers integration test**

Create `backend/src/test/java/com/rsinelli/gymtracker/integration/SecurityHeadersIT.java` (same `@QuarkusTestResource` caveat as `CorsIT` in Task 3 — match the project's actual convention):
```java
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
```

Run: `cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw test -Dtest=SecurityHeadersIT`
Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 3: Create `vercel.json` with frontend security headers, including CSP**

Create `frontend/vercel.json`:
```json
{
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "X-Frame-Options", "value": "DENY" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
        { "key": "Content-Security-Policy", "value": "default-src 'self'; connect-src 'self' https://SUBSTITUA-PELA-URL-DO-BACKEND-NO-RENDER; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'" }
      ]
    }
  ]
}
```
`https://SUBSTITUA-PELA-URL-DO-BACKEND-NO-RENDER` is a deliberate, obviously-fake placeholder — the real Render backend URL doesn't exist yet at this point in the plan (it's only assigned once the backend is actually deployed). `docs/DEPLOY.md` (Task 7) explicitly tells the user to come back and replace this exact string once the real URL is known, as one of the last steps of the deploy walkthrough. `style-src 'self' 'unsafe-inline'` is needed because Tailwind's compiled CSS and some inline styles from Recharts require it; `font-src 'self'` is sufficient since all fonts are self-hosted via `@fontsource` (no external font CDN, confirmed since Sprint 5a) — no `fonts.googleapis.com`/`fonts.gstatic.com` needed in the policy.

- [ ] **Step 4: Run the full backend suite to confirm nothing else broke**

Run: `cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application.properties backend/src/test/java/com/rsinelli/gymtracker/integration/SecurityHeadersIT.java frontend/vercel.json
git commit -m "fix(backend): adicionar headers de seguranca no backend e CSP no vercel.json do frontend"
```

---

### Task 5: Revisão de segurança final (vibesec)

**Files:**
- Create: `docs/superpowers/notes/security-review-sprint6.md`
- Modify: any file where the audit finds a genuine, cheap-to-fix issue (none expected beyond what's already flagged below, but the audit must actually run, not assume)

**Interfaces:** none — this is an audit-and-document task.

- [ ] **Step 1: Access control / IDOR audit**

Run this grep to list every `@Path`-annotated resource class and check which ones carry `@Authenticated` at the class level:
```bash
cd backend && grep -rl "@Path" src/main/java/com/rsinelli/gymtracker/resource/ | xargs grep -L "@Authenticated"
```
Expected output: exactly `AuthResource.java`, `MuscleGroupResource.java`, `GreetingResource.java` (all 3 are deliberately public — auth endpoints themselves, the public muscle-group catalog, and the `/ping` health check). Every OTHER resource file (`ExerciseResource`, `RoutineResource`, `WorkoutSessionResource`, `DashboardResource`, `UserResource`) must NOT appear in this output — if any of them do, that's a real finding to fix immediately (add `@Authenticated` to that class) before writing the review doc.

Then read `WorkoutSessionService.java` and `DashboardService.java` in full and confirm every method that looks up a resource by ID scopes the query by `currentUser.getId()` (via `CurrentUser` injection) and throws a 404-style exception (never 403) when the resource belongs to a different user or doesn't exist. Do the same for `ExerciseService.java` and `RoutineService.java` (already audited in Sprints 2-3, but re-confirm here rather than assuming past audits still hold against the current code).

- [ ] **Step 2: JWT configuration audit**

Confirm `backend/src/main/resources/application.properties` has `smallrye.jwt.verify.algorithm=HS256` (explicit algorithm, no reliance on token-header-supplied algorithm) and that `TokenService.java`'s constructor rejects a JWT secret shorter than 256 bits (`Base64.getUrlDecoder().decode(jwtSecret).length < 32` check). Both already exist — this step confirms, it doesn't add new code.

Note in the review doc: the JWT secret's committed dev-fallback (`gymtracker.jwt.secret=${JWT_SECRET:...}`) is a known, already-flagged item — its mitigation is the mandatory `JWT_SECRET` entry in the deploy environment-variable checklist (Task 7's `docs/DEPLOY.md`), not a code change here.

- [ ] **Step 3: Mass assignment / DTO audit**

Run:
```bash
cd backend && grep -rL "record" src/main/java/com/rsinelli/gymtracker/dto/
```
Expected: empty output (every DTO file is a `record` with explicit named fields — no class accepts a generic `Map`/raw JSON body and persists it directly). If anything appears in this output, read that file and confirm it's genuinely not a mass-assignment risk before excluding it.

- [ ] **Step 4: Document what does NOT apply, and why**

In the review doc (Step 5), explicitly record these as "not applicable" findings with their reasoning — this is deliberate documentation, not a skipped section:
- **CSRF**: this API authenticates exclusively via Bearer token in the `Authorization` header, never cookies — CSRF specifically exploits ambient cookie-based auth, which doesn't exist here.
- **SQL injection**: all queries go through Panache/JPA with parameterized queries; no raw/concatenated SQL exists anywhere in the codebase (spot-check: `grep -rn "createNativeQuery\|Statement(" backend/src/main/java` should return nothing).
- **XXE**: no endpoint accepts or parses XML input anywhere in this application.
- **SSRF**: no endpoint makes an outbound HTTP request to a user-supplied URL (no webhook, URL-preview, or import-from-URL feature exists).
- **File upload attacks**: no file upload functionality exists anywhere in this application.
- **Path traversal**: no endpoint accepts a user-supplied value that's used to construct a filesystem path.

- [ ] **Step 5: Write the review document**

Create `docs/superpowers/notes/security-review-sprint6.md` summarizing the findings from Steps 1-4 (confirmed-clean items, the JWT-secret-fallback mitigation note, and the explicit not-applicable list with reasoning). If Step 1 surfaced a real missing-`@Authenticated` finding, document the fix alongside it (file, what was added, why).

- [ ] **Step 6: If Step 1 found a real issue, fix it and re-run the full suite**

Only applicable if a genuine finding turned up. If so:
```bash
cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify
```
Expected: `BUILD SUCCESS` after the fix.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/notes/security-review-sprint6.md
git commit -m "docs: revisao de seguranca final (vibesec) - CORS, headers, controle de acesso, validacao de input"
```
(If Step 6 applied, also `git add` the fixed file(s) in the same commit, and mention the fix explicitly in the commit message.)

---

### Task 6: CI completo

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:** none — this task consumes the Dockerfiles from Tasks 1-2 (they must already exist and build successfully, which was proven in those tasks) but doesn't change their content.

- [ ] **Step 1: Fix the CI trigger and add the Docker build validation job**

Replace the full content of `.github/workflows/ci.yml` with:
```yaml
name: CI

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]

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
      - run: npm run lint
      - run: npm run test
      - run: npm run build

  docker-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build backend image
        run: docker build -t gym-progress-tracker-backend ./backend
      - name: Build frontend image
        run: docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t gym-progress-tracker-frontend ./frontend
```
The only changes from the current file: (1) `branches: [main]` becomes `branches: [main, develop]` on both `pull_request` and `push` triggers — this is the real, previously-undiscovered gap: CI has never fired for this project's actual integration branch (`develop`), only for `main`, which this project's local-merge workflow has never pushed to (yet); (2) the new `docker-build` job, running in parallel with the existing 2 jobs (no `needs:` — same as the existing two, which also have no dependency on each other), building both Dockerfiles from Tasks 1-2 to catch a broken Dockerfile before it's discovered at actual deploy time.

- [ ] **Step 2: Sanity-check the YAML and the Docker build commands independently**

This workflow only truly executes on GitHub's runners after a push — which this plan does not do without separate explicit approval (see Global Constraints). Verify what CAN be verified locally:
```bash
docker build -t gym-progress-tracker-backend ./backend
docker build --build-arg VITE_API_BASE_URL=http://localhost:8080 -t gym-progress-tracker-frontend ./frontend
```
Expected: both succeed (these are the exact same commands the new `docker-build` job runs) — this was already proven once in Tasks 1 and 2's own verification steps, so this step is a final confirmation nothing about the Dockerfiles changed since then.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: disparar CI tambem em develop e adicionar job de validacao de build Docker"
```

---

### Task 7: Variáveis de ambiente + `docs/DEPLOY.md`

**Files:**
- Create: `docs/DEPLOY.md`
- Modify: `README.md`

**Interfaces:** none — pure documentation.

- [ ] **Step 1: Write the full deploy walkthrough**

Create `docs/DEPLOY.md`:
```markdown
# Deploy — gym-progress-tracker

Backend no Render (Docker), frontend na Vercel. Passo a passo completo — sem CLI, só os
painéis web dos dois serviços.

## 1. Banco de dados (Render Postgres)

1. No painel do Render (render.com), clique em **New +** → **PostgreSQL**.
2. Nome: `gym-progress-tracker-db` (ou o que preferir). Região: a mesma que vai usar pro
   backend (latência menor). Plano: Free.
3. Depois de criado, abra a instância e copie os valores da seção **Connections**:
   - **Hostname**
   - **Port** (normalmente `5432`)
   - **Database**
   - **Username**
   - **Password**

   Você vai usar esses 4 valores no passo 2 (não a "Connection String" combinada — o app
   espera host/porta/banco separados do usuário/senha).

## 2. Backend (Render Web Service, Docker)

1. No painel do Render, **New +** → **Web Service**.
2. Conecte o repositório GitHub `Raphael-Sinelli/liftcurve` (autorize o Render a acessar
   sua conta GitHub se ainda não tiver feito isso).
3. **Root Directory**: `backend`
4. **Runtime**: Docker (Render detecta o `Dockerfile` automaticamente dentro de `backend/`).
5. **Instance Type**: Free (ou o plano que preferir).
6. Em **Environment Variables**, adicione (uma de cada vez, "Add Environment Variable"):

   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<Hostname do passo 1>:<Port do passo 1>/<Database do passo 1>` |
   | `DB_USERNAME` | `<Username do passo 1>` |
   | `DB_PASSWORD` | `<Password do passo 1>` |
   | `JWT_SECRET` | gere um valor novo com `openssl rand -base64 32` no seu terminal — **nunca reuse o valor que já está no `application.properties`, esse já está público no histórico do git** |
   | `CORS_ALLOWED_ORIGINS` | deixe em branco por enquanto — volte aqui depois do passo 3 (frontend) com a URL real da Vercel |
   | `GYMTRACKER_SEED_DEMO` | `true` (só precisa rodar uma vez — pode deixar `true` permanentemente, é idempotente) |

7. Clique em **Create Web Service**. O Render vai puxar o repo, buildar a imagem Docker
   (usando `backend/Dockerfile`), e subir o container. Acompanhe os logs na aba **Logs** —
   procure pela linha do Flyway confirmando as migrations e, se `GYMTRACKER_SEED_DEMO=true`,
   a linha do `DemoSeeder`.
8. Depois que o deploy terminar, o Render mostra a URL pública do serviço (algo como
   `https://gym-progress-tracker-backend.onrender.com`). **Anote essa URL** — vai precisar
   dela nos passos 3 e 4.
9. Teste rápido: abra `<URL do passo 8>/muscle-groups` no browser — deve devolver uma lista
   JSON de grupos musculares (200 OK, sem autenticação).

## 3. Frontend (Vercel)

1. No painel da Vercel (vercel.com), **Add New** → **Project**.
2. Importe o repositório GitHub `Raphael-Sinelli/liftcurve`.
3. **Root Directory**: `frontend` (clique em "Edit" ao lado de Root Directory pra mudar).
4. Framework Preset: Vercel deve detectar **Vite** automaticamente. Se não detectar:
   - Build Command: `npm run build`
   - Output Directory: `dist`
   - Install Command: `npm install`
5. Em **Environment Variables**, adicione:

   | Key | Value | Environment |
   |---|---|---|
   | `VITE_API_BASE_URL` | `<URL do backend, passo 2.8>` | Production |

   **Importante**: essa variável só faz efeito no momento do *build* (Vite grava isso no
   bundle estático) — se você mudar depois, precisa fazer um novo deploy (redeploy), não
   basta salvar a variável.
6. Clique em **Deploy**. Depois que terminar, a Vercel mostra a URL pública (algo como
   `https://liftcurve.vercel.app`, ou um domínio customizado se você configurar um depois).
   **Anote essa URL.**

## 4. Fechar o laço: CORS + CSP com as URLs reais

Agora que as duas URLs existem de verdade:

1. **Volta no Render** (backend → Environment): edite `CORS_ALLOWED_ORIGINS` pra ser
   exatamente a URL da Vercel do passo 3.6 (ex.: `https://liftcurve.vercel.app`, sem barra
   no final). Salve — o Render vai fazer redeploy automático com a variável nova.
2. **No repositório**, edite `frontend/vercel.json` — troque o placeholder
   `https://SUBSTITUA-PELA-URL-DO-BACKEND-NO-RENDER` no `Content-Security-Policy` pela URL
   real do backend (passo 2.8). Commite e dê push — a Vercel redesploya automaticamente a
   partir do repositório.

## 5. Verificação final

- Abra a URL da Vercel, faça login com a conta demo (`demo@gymtracker.app` /
  `DemoGymTracker2026!`), confirme que o dashboard carrega com dado de verdade (prova que
  CORS + `VITE_API_BASE_URL` estão certos).
- Abra o DevTools do browser → aba Network → confirme que as chamadas de API vão pra URL do
  Render, não `localhost`.
- Confirme que não há erro de CORS no console do browser.

## Variáveis de ambiente — referência rápida

| Serviço | Variável | Obrigatória? | Observação |
|---|---|---|---|
| Backend (Render) | `DB_URL` | Sim | `jdbc:postgresql://<host>:<porta>/<banco>` |
| Backend (Render) | `DB_USERNAME` | Sim | |
| Backend (Render) | `DB_PASSWORD` | Sim | |
| Backend (Render) | `JWT_SECRET` | Sim | Gerar novo com `openssl rand -base64 32` — nunca reusar o valor de dev commitado |
| Backend (Render) | `CORS_ALLOWED_ORIGINS` | Sim | URL exata do frontend na Vercel, sem wildcard |
| Backend (Render) | `GYMTRACKER_SEED_DEMO` | Opcional | `true` popula a conta demo pública uma vez (idempotente) |
| Backend (Render) | `PORT` | Não | Render injeta automaticamente na maioria dos planos |
| Frontend (Vercel) | `VITE_API_BASE_URL` | Sim | URL pública do backend — só tem efeito em build time |
```

- [ ] **Step 2: Add a short "Deploy" section to `README.md`**

Add this section to `README.md` (place it after the existing "Conta demo" section, before "Testes"):
```markdown
## Deploy

Backend no [Render](https://render.com) (Docker), frontend na [Vercel](https://vercel.com).
Passo a passo completo de configuração (variáveis de ambiente, CORS, CSP) em
[`docs/DEPLOY.md`](docs/DEPLOY.md).

<!-- Depois do deploy real, preencher: -->
<!-- - Frontend: https://... -->
<!-- - Backend (API): https://... -->
```

- [ ] **Step 3: Commit**

```bash
git add docs/DEPLOY.md README.md
git commit -m "docs: adicionar guia completo de deploy (Render + Vercel) e inventario de variaveis de ambiente"
```

---

### Task 8: Wrap-up

**Files:**
- Modify: `README.md` (status checklist)

**Interfaces:** none — verification and documentation only.

- [ ] **Step 1: Full backend verification**

Run: `cd backend && JAVA_HOME=/c/tools/jdk-21.0.11.10-hotspot ./mvnw verify`
Expected: `BUILD SUCCESS`, all unit + integration tests green (including the new `CorsIT` and `SecurityHeadersIT` from Tasks 3-4).

- [ ] **Step 2: Full frontend verification**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all three green, unchanged from Sprint 5b's baseline (this sprint adds no frontend test files).

- [ ] **Step 3: Full 3-service Docker Compose smoke test**

Run:
```bash
docker compose up -d --build
```
Wait for all 3 services to report running, then:
```bash
curl -sf http://localhost:8080/muscle-groups
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/dashboard
```
Expected: first command returns a JSON array (200 implicit via `-f`), second and third both print `200` (root path AND the client-side `/dashboard` route, proving the Nginx SPA fallback from Task 2 still works end-to-end with the real backend behind it).

Tear down:
```bash
docker compose down
```

- [ ] **Step 4: Update the README status checklist**

In `README.md`, replace:
```markdown
- [ ] Sprint 6 — Testes, CI/CD, Deploy
```
with:
```markdown
- [x] Sprint 6 — Testes, CI/CD, Deploy
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: marcar Sprint 6 completa"
```

---

## Verification (whole branch)

- `cd backend && ./mvnw verify` — green, including 2 new IT classes (`CorsIT`, `SecurityHeadersIT`).
- `cd frontend && npm run test && npm run lint && npm run build` — green, unchanged from Sprint 5b.
- `docker compose up -d --build` — all 3 services come up; backend responds on 8080; frontend responds on 8081 including the `/dashboard` client-side route (SPA fallback).
- `git log --oneline develop..HEAD` shows 8 commits, one per task, each independently working.
- Actual Render/Vercel deployment is explicitly NOT part of this verification — it happens afterward, interactively, only once the user approves a push (see Global Constraints and Task 7's `docs/DEPLOY.md`).
