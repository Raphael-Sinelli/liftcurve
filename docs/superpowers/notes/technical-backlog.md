# Technical Backlog

Cross-cutting technical debt and environment issues that don't belong to a single
sprint's spec/plan. Not sprint-scoped work — pick up when convenient, or fold into
a sprint's plan when it becomes relevant to that sprint's work.

---

## `quarkus:dev` is broken — Postgres Dev Services crashes on boot

**Resolved:** 2026-07-25, Sprint 3 (Task 9). Root cause was that Testcontainers Java
2.x renamed the `org.testcontainers:postgresql` Maven artifact to
`org.testcontainers:testcontainers-postgresql` — the old `postgresql` artifactId was
never released past `1.21.4`, which made it *look* like `quarkus-bom`'s `2.0.5` pin for
`org.testcontainers:testcontainers` had no matching Postgres module, when in fact the
matching module just has a new name. Fix: removed the `<dependencyManagement>` override
that forced `org.testcontainers:testcontainers` down to `1.21.4`, bumped the
`testcontainers.version` property to `2.0.5`, and switched the test dependency's
artifactId from `org.testcontainers:postgresql` to
`org.testcontainers:testcontainers-postgresql` (version `2.0.5`, matching
`quarkus-bom:3.37.3`'s own managed version — confirmed by inspecting the downloaded
`quarkus-bom-3.37.3.pom` directly). `PostgreSQLContainer`'s API used in
`PostgresTestResource` was unchanged across the major version bump. `./mvnw verify`
stayed fully green (all 7 IT classes, 32 integration + 29 unit tests) and
`./mvnw quarkus:dev` now boots cleanly with no `NoClassDefFoundError` —
`GenericContainer` in Testcontainers 2.x no longer implements the JUnit 4
`org.junit.rules.TestRule` shim that classic-loaded against this project's JUnit
5-only classpath. `docker compose up -d` is still required before `quarkus:dev`/tests,
same as before.

**Found:** 2026-07-24, while starting the project locally after Sprint 2 for manual QA.

**Symptom:** `./mvnw quarkus:dev` fails to boot. Every HTTP request returns 500. The log
shows a repeating build failure:

```
[error]: Build step io.quarkus.devservices.postgresql.deployment.PostgresqlDevServicesProcessor#setupPostgres
threw an exception: java.lang.NoClassDefFoundError: org/junit/rules/TestRule
```

**Root cause:** `backend/pom.xml`'s `<dependencyManagement>` forces
`org.testcontainers:testcontainers` to `1.21.4` (comment explains why: the
`quarkus-bom` pins it to `2.0.5`, but `org.testcontainers:postgresql`'s newest release
is `1.21.4`, and the two need to match). Testcontainers `1.21.4`'s `GenericContainer`
still implements `org.junit.rules.TestRule` (a JUnit 4 compatibility shim). Quarkus's
own Postgres Dev Services build step (`quarkus-postgresql-deployment`, used only in
`quarkus:dev` and `@QuarkusTest` without an explicit datasource URL) loads that same
`testcontainers` class on its build/augmentation classpath — and since this project's
`pom.xml` dependency-management override applies repo-wide (not scoped to just the
project's own test dependencies), it also pins the version Quarkus's *own* deployment
tooling resolves. `junit:junit` (JUnit 4) isn't a dependency anywhere in this project
(it only uses JUnit 5), so the class fails to load — and this happens at JVM class
**verification** time, before any `quarkus.datasource.devservices.enabled=false` config
check ever runs. Confirmed two dead ends:
- Setting `quarkus.datasource.devservices.enabled=false` (via `-D` or env var) does
  **not** help — the crash happens before the flag is read.
- Adding `junit:junit` as a `test`-scope dependency to this project's own `pom.xml`
  does **not** help either — Quarkus's build-step/augmentation classloader resolves
  its own dependency tree independently of the consuming project's declared
  dependencies, so it never sees that addition.

**Historical workaround (no longer needed as of the fix above):** run the packaged app instead of dev mode —
`./mvnw package -DskipTests -Dquarkus.swagger-ui.always-include=true` then
`java -jar backend/target/quarkus-app/quarkus-run.jar` (against the docker-compose
Postgres on `localhost:5432`). This works because a packaged/prod-mode run never loads
`quarkus-postgresql-deployment` at all — Dev Services is a build-time-only concern.
Note `quarkus.swagger-ui.always-include` is a **build-time** property — it must be
passed to `mvnw package`, not to the `java -jar` runtime command, or Swagger UI's
assets won't be in the jar and `/q/swagger-ui` will genuinely 404.

**Actual fix (done — see Resolved note above):** option (a) from the original two
candidates is what worked: `org.testcontainers:postgresql` was renamed to
`org.testcontainers:testcontainers-postgresql` in Testcontainers 2.x, so pinning that
renamed artifact to `2.0.5` (matching `quarkus-bom:3.37.3`'s own pin) and removing the
`1.21.4` override resolved it cleanly — no need for option (b)'s exclude-and-accept-no-
Dev-Services fallback.

**When to pick up:** N/A — resolved in Sprint 3, Task 9.
