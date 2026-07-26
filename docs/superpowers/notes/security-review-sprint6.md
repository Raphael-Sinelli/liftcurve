# Security Review — Sprint 6, Task 5 (vibesec)

**Date:** 2026-07-26
**Scope:** Final pre-deploy security audit of the backend (Quarkus/Java 21) and,
where relevant to a finding, the frontend (React/TS) token handling. Performed
against the `vibesec` checklist: access control/IDOR, JWT configuration, mass
assignment, and the applicability of CSRF/SQLi/XXE/SSRF/file-upload/path-traversal.

**Result:** No blocking findings. One real, non-blocking finding surfaced beyond
what the task brief anticipated (refresh token in `localStorage`) — documented
below as an accepted risk with reasoning, not fixed, because a proper fix is an
architectural change out of scope for this task (see "Additional finding").
Everything the brief asked to confirm was independently re-verified by reading
the actual code, not assumed from the brief's expected output.

---

## 1. Access control / IDOR audit

### 1.1 `@Authenticated` coverage

```
$ cd backend && grep -rl "@Path" src/main/java/com/rsinelli/gymtracker/resource/ | xargs grep -L "@Authenticated"
src/main/java/com/rsinelli/gymtracker/resource/AuthResource.java
src/main/java/com/rsinelli/gymtracker/resource/GreetingResource.java
src/main/java/com/rsinelli/gymtracker/resource/MuscleGroupResource.java
```

Exactly the 3 expected files, and no others. Verified each is genuinely safe to
be public rather than just accepting the count:

- **`AuthResource`** (`/auth/register`, `/auth/login`, `/auth/refresh`,
  `/auth/logout`) — these are the auth flow itself; a login endpoint that
  required being logged in would be nonsensical. `logout` accepts the refresh
  token as its own credential in the body, not a Bearer header.
- **`GreetingResource`** (`/ping`) — returns a static `"pong"` string, no user
  data, health-check only.
- **`MuscleGroupResource`** (`/muscle-groups`) — a fixed, seeded 10-row catalog
  (`GET` only), used to populate exercise-creation forms. No user-specific or
  sensitive data.

All 5 remaining resources (`ExerciseResource`, `RoutineResource`,
`WorkoutSessionResource`, `DashboardResource`, `UserResource`) were confirmed to
carry `@Authenticated` directly at the class level (immediately above
`public class ...`), not just present somewhere in the file:

```
DashboardResource.java:25:@Authenticated
ExerciseResource.java:30:@Authenticated
RoutineResource.java:31:@Authenticated
UserResource.java:22:@Authenticated
WorkoutSessionResource.java:33:@Authenticated
```

Also confirmed none of these 5 resources accept a client-supplied `userId` (via
`@QueryValue`/`@PathParam`) that could be used to query on another user's
behalf — every `@PathParam` across them is a resource ID (`exerciseId`,
routine/session/exercise `id`), never a user identifier.

### 1.2 `CurrentUser` cannot be spoofed

`security/CurrentUser.java` derives the user ID exclusively from the verified
JWT `sub` claim, injected via MicroProfile JWT (`JsonWebToken`), which Quarkus
only populates after signature/expiry validation succeeds:

```java
public UUID getId() {
    String subject = jwt.getSubject();
    if (subject == null) {
        throw new IllegalStateException(
                "CurrentUser.getId() called outside an authenticated request — is @Authenticated missing on the endpoint?");
    }
    return UUID.fromString(subject);
}
```

This cannot be overridden by a client-controlled header or query parameter.

### 1.3 Per-resource ownership scoping

Read `WorkoutSessionService.java`, `DashboardService.java`, `ExerciseService.java`,
and `RoutineService.java` in full (not re-used from prior sprint notes), plus
the repositories they call into.

**`WorkoutSessionService.java`** — every ID lookup goes through
`findOwnedOrThrow(sessionId)`:

```java
private WorkoutSessionEntity findOwnedOrThrow(UUID sessionId) {
    WorkoutSessionEntity session = workoutSessionRepository.findByIdOptional(sessionId)
            .orElseThrow(() -> new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND));
    if (!session.getUser().getId().equals(currentUser.getId())) {
        throw new ApiException("SESSION_NOT_FOUND", "Sessão não encontrada.", Response.Status.NOT_FOUND);
    }
    return session;
}
```

Always 404 (`SESSION_NOT_FOUND`), never 403, for both "doesn't exist" and
"belongs to someone else" — sessions have no shared/global catalog, so there's
no legitimate case for revealing existence via 403. `list()` scopes via
`workoutSessionRepository.listOwnedBy(currentUser.getId())`. `addSet()` further
validates the referenced exercise via `exerciseRepository.findVisibleTo(exerciseId,
currentUser.getId())`. Repository methods `listBySessionOrderedByCreatedAt` and
`countBySessionAndExercise` take a bare `sessionId` with no user filter, but a
targeted grep confirmed all 3 call sites for these methods only ever run after
`findOwnedOrThrow` has already verified ownership of that `sessionId` — no path
calls them with an unverified ID.

**`DashboardService.java`** — `progression(exerciseId)` scopes the exercise via
`findVisibleTo(exerciseId, currentUser.getId())` (404 `EXERCISE_NOT_FOUND` if not
visible), and its data query `listByUserAndExercise(currentUser.getId(),
exerciseId)` filters by user at the query level. `volume()` and `plateaus()`
both use `listByUser(currentUser.getId())`. No aggregate ever mixes another
user's session-set data.

**`ExerciseService.java`** — `update`/`delete` route through `findOwnedOrThrow`:

```java
private ExerciseEntity findOwnedOrThrow(UUID exerciseId) {
    ExerciseEntity exercise = exerciseRepository.findByIdOptional(exerciseId)
            .orElseThrow(() -> new ApiException("EXERCISE_NOT_FOUND", ..., Response.Status.NOT_FOUND));
    if (exercise.getOwner() == null) {
        throw new ApiException("NOT_EXERCISE_OWNER", "...", Response.Status.FORBIDDEN);
    }
    if (!exercise.getOwner().getId().equals(currentUser.getId())) {
        throw new ApiException("EXERCISE_NOT_FOUND", ..., Response.Status.NOT_FOUND);
    }
    return exercise;
}
```

Note this method *does* throw 403 in one case — but only when the exercise is a
**global catalog exercise** (`owner == null`), whose existence is already public
knowledge via the `/exercises` list endpoint; there is nothing to hide by
returning 403 there. Every "belongs to a different user" case still returns 404
(`EXERCISE_NOT_FOUND`), matching the IDOR requirement. This is a pre-existing,
documented design decision (Sprint 2, Decision 2) and was re-verified against
the current code rather than taken on faith.

**`RoutineService.java`** — same pattern as sessions: `findOwnedOrThrow(routineId)`
always throws 404 (`ROUTINE_NOT_FOUND`), never 403, since routines have no
shared catalog. `persistRoutineExercises` validates every referenced exercise
via `findVisibleTo(..., currentUser.getId())` before attaching it to the routine.

**Conclusion:** No IDOR findings. All four services scope every lookup by
`currentUser.getId()`, and the only 403 in the audited code (`ExerciseService`,
global catalog item) is deliberate and correctly reasoned, not a resource
belonging to another user.

---

## 2. JWT configuration audit

`backend/src/main/resources/application.properties`:

```properties
smallrye.jwt.verify.algorithm=HS256
```

Confirmed present and explicit — verification does not defer to the token
header's `alg` field, closing off the classic `alg: none` / algorithm-confusion
attack class.

`security/TokenService.java` constructor:

```java
if (Base64.getUrlDecoder().decode(jwtSecret).length < 32) {
    throw new IllegalArgumentException("gymtracker.jwt.secret must be at least 32 bytes for HS256");
}
```

Confirmed present — fails fast at application boot (not at first request) if the
configured secret decodes to fewer than 32 bytes (256 bits), which is the
minimum safe key size for HS256.

Sanity-checked the committed dev-fallback secret itself
(`gymtracker.jwt.secret=${JWT_SECRET:Yb6-3fvO5B7NLqSuo9fJ4r_MmCS9rgwIegNlKXqJzgw}`)
against that same check: it decodes to exactly 32 bytes / 256 bits, so local
dev/CI boots pass the check by design, sitting right at the boundary.

**Known, already-flagged item (not a new finding, not fixed here):** the
dev-fallback secret is committed in plaintext in `application.properties`. Its
mitigation is not a code change in this task — it's the mandatory `JWT_SECRET`
environment variable entry in the production deploy checklist, to be captured
in Task 7's `docs/DEPLOY.md`. Whoever executes Task 7 must confirm `JWT_SECRET`
is set to a freshly generated, non-committed secret before the app goes live;
without that, production would silently run on the checked-in dev key.

Additionally confirmed (supporting checks, not required by the brief but part
of a genuine JWT review): `mp.jwt.verify.issuer=gym-progress-tracker` is set,
and `smallrye.jwt.sign.key=NONE` is declared specifically to stop SmallRye's dev
processor from auto-generating an RSA key pair that would silently shadow the
HS256 secret key (documented in the file's own comments, confirmed by reading
them against the actual property list).

---

## 3. Mass assignment / DTO audit

```
$ cd backend && grep -rL "record" src/main/java/com/rsinelli/gymtracker/dto/
(no output)
```

Empty, as expected — all 23 files under `dto/` are declared `public record`.

Went further than the brief's grep and read every `*Request` DTO in full
(`ExerciseRequest`, `RoutineRequest`, `RoutineExerciseItem`, `SessionSetRequest`,
`RegisterRequest`, `WorkoutSessionRequest`, `FinishWorkoutSessionRequest`,
`LoginRequest`, `RefreshRequest`) to confirm the deeper mass-assignment property:
none of them accept a field that should be server-controlled only (`id`,
`ownerId`, `userId`, `role`, timestamps). Every field is an explicitly validated
business input (`@NotBlank`, `@Size`, `@Min`, `@DecimalMin`, etc.). Ownership
(`owner`, `user`) is always set server-side from `currentUser.getId()` in the
service layer, never taken from the request body.

Also grepped the resource layer for any method that binds a JPA `@Entity`
directly as a request or response type (the more severe mass-assignment risk,
since an entity typically exposes every persistent field) — no matches. Every
endpoint goes through a DTO.

**Conclusion:** No mass-assignment findings.

---

## 4. Not applicable, with reasoning

Each of the following was independently verified with a grep against the
current codebase, not just asserted from the brief:

- **CSRF** — this API authenticates exclusively via a Bearer token in the
  `Authorization` header. Confirmed no cookie-based session mechanism exists
  anywhere in the backend (`grep -rln "NewCookie|@CookieParam|Set-Cookie|HttpSession"
  src/main/java` → no matches), and confirmed the frontend only ever attaches
  the token via `config.headers.set('Authorization', ...)` in `apiClient.ts`,
  never `withCredentials` or a cookie. CSRF specifically exploits ambient
  cookie-based auth, which doesn't exist in this app.
- **SQL injection** — `grep -rn "createNativeQuery|Statement(" backend/src/main/java`
  returns no matches. All queries go through Panache/JPA with parameterized
  queries (`list("field = ?1", value)` style); no raw/concatenated SQL exists.
- **XXE** — `grep -rln "javax.xml|jakarta.xml|DocumentBuilder|SAXParser|XMLInputFactory|Unmarshaller"
  src/main/java` returns no matches. No endpoint accepts or parses XML input.
- **SSRF** — `grep -rln "HttpClient|RestClient|@RegisterRestClient|URLConnection|OkHttpClient"
  src/main/java` returns no matches. No endpoint makes an outbound HTTP request
  to a user-supplied URL; no webhook, URL-preview, or import-from-URL feature
  exists.
- **File upload attacks** — `grep -rln "MultipartForm|@FormParam.*File|FileUpload"
  src/main/java` returns no matches. No file upload functionality exists.
- **Path traversal** — `grep -rln "new File\(|Paths.get\(|Files.write|Files.read"
  src/main/java` returns no matches. No endpoint accepts a user-supplied value
  used to construct a filesystem path.

---

## 5. Additional finding beyond the brief: refresh token in `localStorage`

The task brief was scoped to the backend audit and didn't ask about this, but
`vibesec`'s JWT checklist explicitly flags token storage location, so it was
checked: `frontend/src/lib/tokenStore.ts` keeps the **access token in a
module-level in-memory variable** (good — it doesn't survive a page reload and
isn't reachable through any storage API), but the **refresh token is persisted
in `localStorage`** (`gymtracker.refreshToken`, 30-day TTL server-side).

If a stored-XSS vulnerability were ever introduced anywhere in this SPA, an
attacker's injected script could read that key directly and mint fresh access
tokens for up to 30 days, even after the victim closes the tab — a
meaningfully larger blast radius than the 15-minute access token.

**Assessment: not a bug to fix in this task, documented as an accepted
risk.** Reasoning:

1. **No current XSS vector exists.** Grepped the entire frontend for
   `dangerouslySetInnerHTML`, direct `innerHTML` assignment, `eval(`, and
   `new Function(` — zero matches. React's default JSX escaping is in effect
   everywhere; there is no known injection point today for this risk to ride on.
2. **Compensating controls are already in place and were verified in
   `AuthService.java`:** refresh tokens are stored server-side only as a
   SHA-256 hash (not the raw value), rotated on every use (`storedToken.setRevokedAt(...)`
   before issuing a new pair — old token cannot be replayed), and revoked
   best-effort on explicit logout (`AuthContext.tsx`'s `logout()` calls the
   `/auth/logout` endpoint and clears both the in-memory access token and the
   `localStorage` entry regardless of whether the server call succeeds).
3. **The proper fix (httpOnly, Secure, SameSite cookies for the refresh token)
   is an architectural change, not a cheap one.** It would require the backend
   to issue `Set-Cookie`, re-introduce `Access-Control-Allow-Credentials: true`
   on CORS (currently explicitly set to `false` as of this same branch's Task
   2/3 work, specifically to close a credential-leak edge case), and add real
   CSRF protection (double-submit token or `SameSite=Strict` plus Origin
   validation) since cookie-based auth would then be in play. That's a
   meaningfully larger, cross-cutting change than this task's scope ("any file
   where the audit finds a genuine, cheap-to-fix issue") and would need its own
   spec/plan, not a drive-by fix bundled into a documentation task.

**Recommendation for a future sprint:** if this app's threat model changes (more
third-party script dependencies, user-generated rich content, browser
extensions targeting it, etc.), revisit this — either move the refresh token to
an httpOnly cookie with full CSRF protection, or at minimum shorten the refresh
token TTL and/or add device/session listing + revocation UI so a compromised
long-lived token can be killed without waiting 30 days.

---

## Summary

| Area | Result |
|---|---|
| `@Authenticated` coverage | Clean — matches expected 3 public resources exactly, verified class-level placement on the other 5 |
| IDOR / ownership scoping | Clean — all 4 services scope every lookup by `currentUser.getId()`, always 404 (never 403) for another user's resource |
| JWT algorithm | Clean — explicit `HS256`, no header-driven algorithm |
| JWT key length | Clean — constructor enforces ≥256 bits at boot |
| JWT dev-fallback secret | Known, flagged — mitigated via mandatory `JWT_SECRET` env var in Task 7's deploy checklist, not a code change |
| Mass assignment | Clean — all 23 DTOs are records with explicit, validated, server-owned fields |
| CSRF / SQLi / XXE / SSRF / file upload / path traversal | Not applicable — verified via grep, reasoning documented above |
| Refresh token storage | Accepted risk, documented — `localStorage`, mitigated by rotation/hashing/revocation/no current XSS vector; proper fix (httpOnly cookie + CSRF) is out of scope, left as a future-sprint recommendation |

No code changes were required as a result of this audit. Step 6 of the task
brief (fix + re-run `mvn verify`) does not apply.
