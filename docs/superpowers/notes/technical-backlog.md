# Technical Backlog

Cross-cutting technical debt and environment issues that don't belong to a single
sprint's spec/plan. Not sprint-scoped work — pick up when convenient, or fold into
a sprint's plan when it becomes relevant to that sprint's work.

---

## `quarkus:dev` is broken — Postgres Dev Services crashes on boot

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

**Current workaround:** run the packaged app instead of dev mode —
`./mvnw package -DskipTests -Dquarkus.swagger-ui.always-include=true` then
`java -jar backend/target/quarkus-app/quarkus-run.jar` (against the docker-compose
Postgres on `localhost:5432`). This works because a packaged/prod-mode run never loads
`quarkus-postgresql-deployment` at all — Dev Services is a build-time-only concern.
Note `quarkus.swagger-ui.always-include` is a **build-time** property — it must be
passed to `mvnw package`, not to the `java -jar` runtime command, or Swagger UI's
assets won't be in the jar and `/q/swagger-ui` will genuinely 404.

**Actual fix (not yet done):** either (a) narrow the `testcontainers` version override
so it doesn't leak into Quarkus's own deployment-time dependency resolution — likely
means finding a way to scope it more tightly than a bare `<dependencyManagement>`
entry, or pinning `org.testcontainers:postgresql` to a version that already matches
`quarkus-bom`'s `2.0.5` line instead of forcing `testcontainers` backward; or
(b) explicitly exclude `org.testcontainers:testcontainers` from whatever pulls in the
Postgres Dev Services processor and accept Dev Services being unavailable, documenting
`docker compose up -d` as the required manual step before `quarkus:dev` (which is
already the workflow today, since `docker-compose.yml` only has Postgres, not the
backend). Needs someone to actually dig into Quarkus's extension dependency resolution
rules to know which approach is viable — didn't want to guess-and-check on a
day-to-day dev-loop dependency during a QA session.

**When to pick up:** flagged for Sprint 7 (README + Polish) at the latest, since a
broken `quarkus:dev` loop is exactly the kind of thing a portfolio reviewer running
`docker compose up` would hit. Could also be picked up opportunistically any sprint if
someone's blocked by wanting hot-reload for domain-logic work (Sprint 3 especially,
given how much back-and-forth iteration 1RM/volume/plateau calculators will need).
