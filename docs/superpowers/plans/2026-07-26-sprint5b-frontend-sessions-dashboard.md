# Sprint 5b — Frontend Sessões + Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the frontend by building workout session logging (start/log-sets/finish) and a Recharts dashboard (1RM progression, weekly volume by muscle group, plateau alerts) on top of the Sprint 5a foundation (routing, API client, design system, Auth/Exercícios/Rotinas — all already merged in `develop`).

**Architecture:** Same layered pattern as Sprint 5a — `api/*.ts` (typed Axios calls via the shared `apiClient`), `features/*/use*Queries.ts` (TanStack Query hooks with cache-patch mutations, no invalidate-flash), `features/*/​*Page.tsx` (composition + RHF/Zod forms where needed). Two new pure, independently-testable modules: `lib/formatDate.ts` (native `Intl.DateTimeFormat`, no new date library) and `features/dashboard/pivotVolumeByWeek.ts` (reshapes the flat volume API response into Recharts' per-week/per-group format). Recharts chart components take already-shaped data as props — no API/query logic inside them.

**Tech Stack:** Same as Sprint 5a (React 19, TS strict, TanStack Query, React Hook Form + Zod, Radix primitives, Vitest + Testing Library + MSW) plus `recharts` (the only new dependency this sprint).

## Global Constraints

- Backend base path has NO prefix — `/workout-sessions/*` and `/dashboard/*` are at root, same as every other resource. No backend changes this sprint.
- JSON over the wire is `snake_case`; TS code is `camelCase`; conversion happens ONLY in `apiClient.ts`'s interceptors (unchanged from Sprint 5a).
- Every API error is `{"error":{"code","message","status","details"}}`. Never render `err.message` from Axios directly — always `extractApiError`/`getErrorMessage`.
- "Only 1 active session" and "`set_number` resets per exercise" are backend-enforced rules (`WorkoutSessionService`) — the frontend derives UI state from them (e.g. showing "Continuar treino ativo" instead of "Iniciar treino"), it never reimplements or second-guesses them. `set_number` is always server-computed, never sent by the client.
- Plateau detection (streak ≥3 sessions, min 4 sessions of history) is fixed backend logic (`PlateauDetectionService`) — not configurable, not reimplemented client-side. The frontend only renders what `/dashboard/plateaus` already returns (a pre-filtered list of active alerts).
- `/dashboard/volume` and `/dashboard/progression/{id}` return already-aggregated/reduced data (one point per session, one row per muscle-group/week) — the frontend reshapes (pivots) this for the chart library, it does not re-aggregate raw `session_sets`.
- No new date-formatting library — use native `Intl.DateTimeFormat` (already the documented convention in this user's global `TIMEZONE.md` rules), wrapped in `lib/formatDate.ts`.
- The new chart color tokens (Decision 5 below) are an EXTENSION of the existing "Ferro & Giz" `@theme` tokens in `frontend/src/index.css` — the existing `bg`/`surface`/`ink`/`muted`/`accent`/`line` tokens are not touched or reopened.
- Recharts' `ResponsiveContainer` does not reliably size itself in jsdom (a well-known limitation, not a bug in this codebase) — tests must not assert on rendered SVG internals (no querying `.recharts-*` classes); they assert on the surrounding component's text/state (empty states, selectors, banners), which render identically regardless of the chart's actual pixel output.
- `AuthContext`'s `logout()` already calls `queryClient.clear()` (Sprint 5a final-review fix) — this wipes the ENTIRE cache regardless of key, so it automatically covers every new query key this sprint introduces. No new clearing logic is needed; this sprint only ADDS a regression test proving the new keys are covered too (Task 7).
- TypeScript strict mode, `npm run lint`/`npm run test`/`npm run build` must all stay green before every commit — same as Sprint 5a.
- Commit convention: `feat(frontend): ...` / `test(frontend): ...` / `fix(frontend): ...` / `docs: ...`.

---

### Task 1: Fundação da sprint — deps, utilitários, tokens, navegação, rotas-esqueleto

**Files:**
- Modify: `frontend/package.json` (via `npm install`)
- Create: `frontend/src/lib/formatDate.ts` + `formatDate.test.ts`
- Modify: `frontend/src/lib/errorMessages.ts` + `errorMessages.test.ts`
- Modify: `frontend/src/index.css`
- Modify: `frontend/src/routes/AppLayout.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/features/auth/LoginPage.tsx`, `RegisterPage.tsx`
- Create: `frontend/src/features/dashboard/DashboardPage.tsx` (placeholder — full version in Task 7)
- Create: `frontend/src/features/sessions/SessionsListPage.tsx` (placeholder — full version in Task 3)
- Create: `frontend/src/features/sessions/SessionDetailPage.tsx` (placeholder — full version in Task 4)

**Interfaces:**
- Produces: `formatDateTime(iso: string): string`, `formatShortDate(iso: string): string` (consumed by Tasks 3, 4, 6, 7).
- Produces: 4 new entries in `errorMessages.ts`'s `ERROR_MESSAGES` map: `SESSION_ALREADY_ACTIVE`, `SESSION_ALREADY_FINISHED`, `SESSION_NOT_FOUND`, `INVALID_ROUTINE_REFERENCE` (consumed by Tasks 3-4).
- Produces: routes `/dashboard`, `/sessions`, `/sessions/:id` in `App.tsx`, reachable and protected — consumed (replaced-in-place) by Tasks 3, 4, 7.

- [ ] **Step 1: Install recharts**

Run from `frontend/`:
```bash
npm install recharts
```
Expected: `recharts` added to `dependencies` in `package.json`, `package-lock.json` regenerated.

- [ ] **Step 2: `formatDate.ts` — write tests, verify fail, implement, verify pass**

Create `frontend/src/lib/formatDate.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { formatDateTime, formatShortDate } from './formatDate'
```

```ts
describe('formatDateTime', () => {
  it('returns a non-empty string containing the year', () => {
    const result = formatDateTime('2026-07-01T12:00:00Z')
    expect(result).toContain('2026')
  })

  it('does not throw for a valid ISO string and returns distinct output for distinct instants', () => {
    const first = formatDateTime('2026-01-15T08:30:00Z')
    const second = formatDateTime('2026-06-20T18:45:00Z')
    expect(first.length).toBeGreaterThan(0)
    expect(second.length).toBeGreaterThan(0)
    expect(first).not.toBe(second)
  })
})

describe('formatShortDate', () => {
  it('returns a short label without the full 4-digit year', () => {
    const result = formatShortDate('2026-07-06T00:00:00Z')
    expect(result).not.toContain('2026')
    expect(result.length).toBeGreaterThan(0)
  })

  it('produces distinct labels for dates in different months', () => {
    const january = formatShortDate('2026-01-06T00:00:00Z')
    const july = formatShortDate('2026-07-06T00:00:00Z')
    expect(january).not.toBe(july)
  })
})
```

Note: these assertions deliberately avoid exact string matches. `Intl.DateTimeFormat(undefined, ...)` uses the runtime's default locale (the correct, documented pattern per this project's global `TIMEZONE.md` rules — UTC on the wire, locale-aware formatting only at display time) — asserting an exact formatted string would make the test depend on the test runner's default ICU locale, which is not something this codebase should pin down just to make a test deterministic.

Run: `cd frontend && npm run test -- formatDate.test.ts` — expected FAIL (`./formatDate` doesn't exist).

Create `frontend/src/lib/formatDate.ts`:
```ts
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatShortDate(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short' }).format(new Date(iso))
}
```

Run: `cd frontend && npm run test -- formatDate.test.ts` — expected PASS (4 tests).

- [ ] **Step 3: New error codes — write tests, verify fail, implement, verify pass**

In `frontend/src/lib/errorMessages.test.ts`, add these cases inside the existing `describe('getErrorMessage', ...)` block:
```ts
  it('maps SESSION_ALREADY_ACTIVE', () => {
    expect(getErrorMessage('SESSION_ALREADY_ACTIVE')).toBe('Você já tem um treino em andamento.')
  })

  it('maps SESSION_ALREADY_FINISHED', () => {
    expect(getErrorMessage('SESSION_ALREADY_FINISHED')).toBe('Este treino já foi finalizado.')
  })

  it('maps SESSION_NOT_FOUND', () => {
    expect(getErrorMessage('SESSION_NOT_FOUND')).toBe('Sessão não encontrada.')
  })

  it('maps INVALID_ROUTINE_REFERENCE', () => {
    expect(getErrorMessage('INVALID_ROUTINE_REFERENCE')).toBe('A rotina selecionada não é válida.')
  })
```

Run: `cd frontend && npm run test -- errorMessages.test.ts` — expected FAIL (4 new assertions fail, existing ones still pass).

In `frontend/src/lib/errorMessages.ts`, add these 4 entries to the `ERROR_MESSAGES` record (anywhere inside the object, e.g. right after `ROUTINE_NOT_FOUND`):
```ts
  SESSION_ALREADY_ACTIVE: 'Você já tem um treino em andamento.',
  SESSION_ALREADY_FINISHED: 'Este treino já foi finalizado.',
  SESSION_NOT_FOUND: 'Sessão não encontrada.',
  INVALID_ROUTINE_REFERENCE: 'A rotina selecionada não é válida.',
```

Run: `cd frontend && npm run test -- errorMessages.test.ts` — expected PASS (all cases, old + new).

- [ ] **Step 4: Chart color tokens**

In `frontend/src/index.css`, inside the existing `@theme { ... }` block, add these 5 lines after the existing `--color-line: #38352c;` line (do not touch any existing token):
```css
  --color-chart-iron: #7a8b99;
  --color-chart-brass: #b08d57;
  --color-chart-olive: #6b7355;
  --color-chart-copper: #9c6b4f;
  --color-chart-gold: #c9a961;
```

- [ ] **Step 5: Placeholder pages for the 3 new routes**

Create `frontend/src/features/dashboard/DashboardPage.tsx`:
```tsx
export function DashboardPage() {
  return <h1 className="font-display text-2xl font-bold text-ink">Dashboard</h1>
}
```

Create `frontend/src/features/sessions/SessionsListPage.tsx`:
```tsx
export function SessionsListPage() {
  return <h1 className="font-display text-2xl font-bold text-ink">Sessões</h1>
}
```

Create `frontend/src/features/sessions/SessionDetailPage.tsx`:
```tsx
export function SessionDetailPage() {
  return <h1 className="font-display text-2xl font-bold text-ink">Sessão</h1>
}
```

- [ ] **Step 6: Wire the 3 new routes into `App.tsx` and change the default post-login target**

Read `frontend/src/App.tsx` first — it currently has routes for `/`, `/login`, `/register`, `/exercises`, `/routines`, `/routines/new`, `/routines/:id`, and a catch-all `path="*"` (added in Sprint 5a's final-review fix) that redirects to `/`. Do not remove or reorder the catch-all — it must stay the LAST `<Route>` child of `<Routes>`.

Add these imports near the existing feature-page imports:
```tsx
import { DashboardPage } from './features/dashboard/DashboardPage'
import { SessionsListPage } from './features/sessions/SessionsListPage'
import { SessionDetailPage } from './features/sessions/SessionDetailPage'
```

Add these 3 routes, following the exact same `ProtectedRoute > AppLayout > Page` wrapping pattern already used by the `/exercises` route — insert them anywhere among the other protected routes, but BEFORE the catch-all `path="*"` route:
```tsx
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <AppLayout>
              <DashboardPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions"
        element={
          <ProtectedRoute>
            <AppLayout>
              <SessionsListPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions/:id"
        element={
          <ProtectedRoute>
            <AppLayout>
              <SessionDetailPage />
            </AppLayout>
          </ProtectedRoute>
        }
      />
```

Then locate the `RedirectRoot` component's ternary — it currently reads `<Navigate to={user ? '/exercises' : '/login'} replace />`. Change `'/exercises'` to `'/dashboard'` (leave `'/login'` untouched):
```tsx
  return <Navigate to={user ? '/dashboard' : '/login'} replace />
```

- [ ] **Step 7: Update the post-login/post-register redirect target**

In `frontend/src/features/auth/LoginPage.tsx`, find the line computing the redirect target after a successful login — it currently reads `(location.state as { from?: string } | null)?.from ?? '/exercises'`. Change the fallback from `'/exercises'` to `'/dashboard'`:
```tsx
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/dashboard'
```

In `frontend/src/features/auth/RegisterPage.tsx`, find the `navigate('/exercises', { replace: true })` call after a successful registration and change the path to `'/dashboard'`:
```tsx
      navigate('/dashboard', { replace: true })
```

Then update the corresponding assertions in `frontend/src/features/auth/LoginPage.test.tsx` and `RegisterPage.test.tsx`: any test route stub currently named for `/exercises` (e.g. `<Route path="/exercises" element={<p>Página de exercícios</p>} />`) and any `waitFor(() => expect(screen.getByText('Página de exercícios')).toBeInTheDocument())` assertion should target `/dashboard` / `"Página de dashboard"` instead — read both test files first to find the exact current route-stub and assertion text before editing, since the literal test copy may differ slightly from this description.

- [ ] **Step 8: Update `AppLayout`'s nav items**

In `frontend/src/routes/AppLayout.tsx`, replace:
```ts
const NAV_ITEMS = [
  { to: '/exercises', label: 'Exercícios' },
  { to: '/routines', label: 'Rotinas' },
]
```
with:
```ts
const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/sessions', label: 'Sessões' },
  { to: '/exercises', label: 'Exercícios' },
  { to: '/routines', label: 'Rotinas' },
]
```

- [ ] **Step 9: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green. Pay attention to `App.test.tsx` — it currently only tests the unauthenticated `/` → `/login` redirect, which is unaffected by this task's changes (the default-target change only applies when `user` is truthy), so it should still pass unmodified.

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/lib/formatDate.ts frontend/src/lib/formatDate.test.ts frontend/src/lib/errorMessages.ts frontend/src/lib/errorMessages.test.ts frontend/src/index.css frontend/src/routes/AppLayout.tsx frontend/src/App.tsx frontend/src/features/auth/LoginPage.tsx frontend/src/features/auth/LoginPage.test.tsx frontend/src/features/auth/RegisterPage.tsx frontend/src/features/auth/RegisterPage.test.tsx frontend/src/features/dashboard/DashboardPage.tsx frontend/src/features/sessions/SessionsListPage.tsx frontend/src/features/sessions/SessionDetailPage.tsx
git commit -m "feat(frontend): fundacao da Sprint 5b - recharts, formatDate, novos codigos de erro, paleta de grafico, rotas de sessoes/dashboard"
```

---

### Task 2: API + hooks de sessões

**Files:**
- Create: `frontend/src/api/sessionsApi.ts`
- Create: `frontend/src/features/sessions/useSessionsQueries.ts`
- Modify: `frontend/src/test/mocks/handlers.ts` (add `/workout-sessions*` fixtures + handlers)
- Modify: `frontend/src/test/setup.ts` (add `resetSessionsFixture()` to the global `afterEach`)

**Interfaces:**
- Consumes: `apiClient` (`lib/apiClient.ts`).
- Produces (consumed by Tasks 3-4):
  - `sessionsApi.ts`: `interface SessionSummary {id, routineId, startedAt, finishedAt, setCount}`, `interface SessionSet {id, exerciseId, exerciseName, setNumber, weightKg, reps, rpe, estimated1rmEpley, estimated1rmBrzycki, estimated1rmBest, createdAt}`, `interface SessionDetail {id, routineId, startedAt, finishedAt, notes, sets: SessionSet[]}`, `interface CreateSessionInput {routineId?, notes?}`, `interface AddSetInput {exerciseId, weightKg, reps, rpe?}`, `interface FinishSessionInput {notes?}`, `listSessions()`, `getSession(id)`, `createSession(input)`, `addSet(sessionId, input)`, `finishSession(id, input)`.
  - `useSessionsQueries.ts`: `useSessions()`, `useSession(id)`, `useCreateSession()`, `useAddSet()`, `useFinishSession()`.
  - `test/mocks/handlers.ts`: exports `FIXED_SESSION_ID` (the one pre-seeded finished session's id) for tests to reference.

- [ ] **Step 1: `sessionsApi.ts` — implement (no dedicated test file; exercised via Tasks 3-4's component tests)**

Create `frontend/src/api/sessionsApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface SessionSummary {
  id: string
  routineId: string | null
  startedAt: string
  finishedAt: string | null
  setCount: number
}

export interface SessionSet {
  id: string
  exerciseId: string
  exerciseName: string
  setNumber: number
  weightKg: number
  reps: number
  rpe: number | null
  estimated1rmEpley: number
  estimated1rmBrzycki: number | null
  estimated1rmBest: number
  createdAt: string
}

export interface SessionDetail {
  id: string
  routineId: string | null
  startedAt: string
  finishedAt: string | null
  notes: string | null
  sets: SessionSet[]
}

export interface CreateSessionInput {
  routineId?: string | null
  notes?: string | null
}

export interface AddSetInput {
  exerciseId: string
  weightKg: number
  reps: number
  rpe?: number | null
}

export interface FinishSessionInput {
  notes?: string | null
}

export async function listSessions(): Promise<SessionSummary[]> {
  const response = await apiClient.get<SessionSummary[]>('/workout-sessions')
  return response.data
}

export async function getSession(id: string): Promise<SessionDetail> {
  const response = await apiClient.get<SessionDetail>(`/workout-sessions/${id}`)
  return response.data
}

export async function createSession(input: CreateSessionInput): Promise<SessionDetail> {
  const response = await apiClient.post<SessionDetail>('/workout-sessions', input)
  return response.data
}

export async function addSet(sessionId: string, input: AddSetInput): Promise<SessionSet> {
  const response = await apiClient.post<SessionSet>(`/workout-sessions/${sessionId}/sets`, input)
  return response.data
}

export async function finishSession(id: string, input: FinishSessionInput): Promise<SessionDetail> {
  const response = await apiClient.patch<SessionDetail>(`/workout-sessions/${id}`, input)
  return response.data
}
```

- [ ] **Step 2: React Query hooks — implement**

Create `frontend/src/features/sessions/useSessionsQueries.ts`:
```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addSet,
  createSession,
  finishSession,
  getSession,
  listSessions,
  type AddSetInput,
  type CreateSessionInput,
  type FinishSessionInput,
  type SessionDetail,
  type SessionSummary,
} from '../../api/sessionsApi'

const SESSIONS_KEY = ['workout-sessions'] as const
const sessionKey = (id: string) => ['workout-sessions', id] as const

export function useSessions() {
  return useQuery({ queryKey: SESSIONS_KEY, queryFn: listSessions })
}

export function useSession(id: string | undefined) {
  return useQuery({
    queryKey: sessionKey(id ?? ''),
    queryFn: () => getSession(id as string),
    enabled: Boolean(id),
  })
}

function toSummary(session: SessionDetail): SessionSummary {
  return {
    id: session.id,
    routineId: session.routineId,
    startedAt: session.startedAt,
    finishedAt: session.finishedAt,
    setCount: session.sets.length,
  }
}

export function useCreateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateSessionInput) => createSession(input),
    onSuccess: (created) => {
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) => [toSummary(created), ...(current ?? [])])
      queryClient.setQueryData(sessionKey(created.id), created)
    },
  })
}

export function useAddSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, input }: { sessionId: string; input: AddSetInput }) => addSet(sessionId, input),
    onSuccess: (createdSet, { sessionId }) => {
      queryClient.setQueryData<SessionDetail>(sessionKey(sessionId), (current) =>
        current ? { ...current, sets: [...current.sets, createdSet] } : current,
      )
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) =>
        (current ?? []).map((session) => (session.id === sessionId ? { ...session, setCount: session.setCount + 1 } : session)),
      )
    },
  })
}

export function useFinishSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: FinishSessionInput }) => finishSession(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(sessionKey(updated.id), updated)
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) =>
        (current ?? []).map((session) => (session.id === updated.id ? toSummary(updated) : session)),
      )
    },
  })
}
```
(New session prepended to the list cache for immediate visual feedback; `SessionsListPage`, built in Task 3, sorts by `startedAt` descending when rendering anyway, so the exact cache-insertion position doesn't need to be authoritative — it just needs to look right immediately, matching the no-flash pattern already established for Exercícios/Rotinas in Sprint 5a.)

- [ ] **Step 3: MSW fixtures and handlers for `/workout-sessions*`**

In `frontend/src/test/mocks/handlers.ts`, add these fixture declarations and helper (place them near the existing `exercisesFixture`/`routinesFixture` declarations):
```ts
export const FIXED_SESSION_ID = 'session-1'

interface SessionFixtureSet {
  id: string
  exercise_id: string
  exercise_name: string
  set_number: number
  weight_kg: number
  reps: number
  rpe: number | null
  estimated_1rm_epley: number
  estimated_1rm_brzycki: number | null
  estimated_1rm_best: number
  created_at: string
}

interface SessionFixture {
  id: string
  routine_id: string | null
  started_at: string
  finished_at: string | null
  notes: string | null
  sets: SessionFixtureSet[]
}

function buildInitialSessionsFixture(): SessionFixture[] {
  return [
    {
      id: FIXED_SESSION_ID,
      routine_id: 'routine-1',
      started_at: '2026-07-01T12:00:00Z',
      finished_at: '2026-07-01T13:00:00Z',
      notes: null,
      sets: [
        {
          id: 'set-1',
          exercise_id: 'ex-global-1',
          exercise_name: 'Supino Reto',
          set_number: 1,
          weight_kg: 80,
          reps: 8,
          rpe: 8,
          estimated_1rm_epley: 101.3,
          estimated_1rm_brzycki: 100,
          estimated_1rm_best: 101.3,
          created_at: '2026-07-01T12:05:00Z',
        },
      ],
    },
  ]
}

let sessionsFixture: SessionFixture[] = buildInitialSessionsFixture()
let nextSessionSetId = 2

export function resetSessionsFixture() {
  sessionsFixture = buildInitialSessionsFixture()
  nextSessionSetId = 2
}
```

Append to the exported `handlers` array:
```ts
  http.get('/workout-sessions', () =>
    HttpResponse.json(
      sessionsFixture.map((session) => ({
        id: session.id,
        routine_id: session.routine_id,
        started_at: session.started_at,
        finished_at: session.finished_at,
        set_count: session.sets.length,
      })),
    ),
  ),

  http.get('/workout-sessions/:id', ({ params }) => {
    const session = sessionsFixture.find((s) => s.id === params.id)
    if (!session) {
      return HttpResponse.json(
        { error: { code: 'SESSION_NOT_FOUND', message: 'Sessão não encontrada.', status: 404, details: [] } },
        { status: 404 },
      )
    }
    return HttpResponse.json(session)
  }),

  http.post('/workout-sessions', async ({ request }) => {
    const active = sessionsFixture.find((s) => s.finished_at === null)
    if (active) {
      return HttpResponse.json(
        { error: { code: 'SESSION_ALREADY_ACTIVE', message: 'Você já tem um treino em andamento.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    const body = (await request.json()) as { routine_id?: string | null; notes?: string | null }
    if (body.routine_id === 'routine-invalid') {
      return HttpResponse.json(
        { error: { code: 'INVALID_ROUTINE_REFERENCE', message: 'A rotina selecionada não é válida.', status: 400, details: [] } },
        { status: 400 },
      )
    }
    const created: SessionFixture = {
      id: `session-new-${sessionsFixture.length + 1}`,
      routine_id: body.routine_id ?? null,
      started_at: '2026-07-25T10:00:00Z',
      finished_at: null,
      notes: body.notes ?? null,
      sets: [],
    }
    sessionsFixture = [...sessionsFixture, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch('/workout-sessions/:id', async ({ params, request }) => {
    const session = sessionsFixture.find((s) => s.id === params.id)
    if (!session) {
      return HttpResponse.json(
        { error: { code: 'SESSION_NOT_FOUND', message: 'Sessão não encontrada.', status: 404, details: [] } },
        { status: 404 },
      )
    }
    if (session.finished_at !== null) {
      return HttpResponse.json(
        { error: { code: 'SESSION_ALREADY_FINISHED', message: 'Este treino já foi finalizado.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    const body = (await request.json()) as { notes?: string | null }
    const updated: SessionFixture = { ...session, finished_at: '2026-07-25T11:00:00Z', notes: body.notes ?? session.notes }
    sessionsFixture = sessionsFixture.map((s) => (s.id === params.id ? updated : s))
    return HttpResponse.json(updated)
  }),

  http.post('/workout-sessions/:id/sets', async ({ params, request }) => {
    const session = sessionsFixture.find((s) => s.id === params.id)
    if (!session) {
      return HttpResponse.json(
        { error: { code: 'SESSION_NOT_FOUND', message: 'Sessão não encontrada.', status: 404, details: [] } },
        { status: 404 },
      )
    }
    if (session.finished_at !== null) {
      return HttpResponse.json(
        { error: { code: 'SESSION_ALREADY_FINISHED', message: 'Este treino já foi finalizado.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    const body = (await request.json()) as { exercise_id: string; weight_kg: number; reps: number; rpe?: number | null }
    if (body.exercise_id === 'ex-invalid') {
      return HttpResponse.json(
        { error: { code: 'INVALID_EXERCISE_REFERENCE', message: 'Um dos exercícios selecionados não é válido.', status: 400, details: [] } },
        { status: 400 },
      )
    }
    const setNumber = session.sets.filter((s) => s.exercise_id === body.exercise_id).length + 1
    const createdSet: SessionFixtureSet = {
      id: `set-${nextSessionSetId++}`,
      exercise_id: body.exercise_id,
      exercise_name: body.exercise_id === 'ex-global-1' ? 'Supino Reto' : 'Exercício',
      set_number: setNumber,
      weight_kg: body.weight_kg,
      reps: body.reps,
      rpe: body.rpe ?? null,
      estimated_1rm_epley: body.weight_kg * (1 + body.reps / 30),
      estimated_1rm_brzycki: body.reps < 37 ? (body.weight_kg * 36) / (37 - body.reps) : null,
      estimated_1rm_best: body.weight_kg * (1 + body.reps / 30),
      created_at: '2026-07-25T10:05:00Z',
    }
    session.sets = [...session.sets, createdSet]
    sessionsFixture = sessionsFixture.map((s) => (s.id === params.id ? session : s))
    return HttpResponse.json(createdSet, { status: 201 })
  }),
```

- [ ] **Step 4: Wire the fixture reset into the global test `afterEach`**

In `frontend/src/test/setup.ts`, add `resetSessionsFixture` to the existing import from `./mocks/handlers`, and add a call to it inside the existing `afterEach` block (alongside `resetExercisesFixture()`/`resetRoutinesFixture()`):
```ts
resetSessionsFixture()
```

- [ ] **Step 5: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green (no new component tests exist yet for this task — `sessionsApi.ts`/`useSessionsQueries.ts` are exercised by Tasks 3-4).

```bash
git add frontend/src/api/sessionsApi.ts frontend/src/features/sessions/useSessionsQueries.ts frontend/src/test/mocks/handlers.ts frontend/src/test/setup.ts
git commit -m "feat(frontend): API e hooks de sessoes de treino com cache-patch sem flash"
```

---

### Task 3: Fluxo de sessão ativa + histórico (`SessionsListPage` + `StartSessionModal`)

**Files:**
- Create: `frontend/src/features/sessions/StartSessionModal.tsx`
- Modify: `frontend/src/features/sessions/SessionsListPage.tsx` (full rewrite, replaces Task 1 stub) + `SessionsListPage.test.tsx`

**Interfaces:**
- Consumes: `useSessions()`/`useCreateSession()` (Task 2), `useRoutines()` (Sprint 5a, `features/routines/useRoutinesQueries.ts`), `Button`/`Modal`/`Select`/`FormField`/`PlateStat` (Sprint 5a design system), `formatDateTime` (Task 1).
- Produces: nothing consumed by later tasks in this sprint.

- [ ] **Step 1: `StartSessionModal` — implement**

Create `frontend/src/features/sessions/StartSessionModal.tsx`:
```tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Modal } from '../../components/Modal'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useRoutines } from '../routines/useRoutinesQueries'
import { useCreateSession } from './useSessionsQueries'

interface StartSessionModalProps {
  onClose: () => void
}

export function StartSessionModal({ onClose }: StartSessionModalProps) {
  const { data: routines } = useRoutines()
  const createSession = useCreateSession()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const [routineId, setRoutineId] = useState('')

  async function handleStart() {
    try {
      const session = await createSession.mutateAsync({ routineId: routineId || null })
      onClose()
      navigate(`/sessions/${session.id}`)
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  return (
    <Modal open onOpenChange={(open) => !open && onClose()} title="Iniciar treino">
      <div className="flex flex-col gap-4">
        <FormField label="Rotina (opcional)" htmlFor="routineId">
          <Select
            id="routineId"
            value={routineId}
            onValueChange={setRoutineId}
            options={(routines ?? []).map((routine) => ({ value: routine.id, label: routine.name }))}
            placeholder="Nenhuma — treino livre"
          />
        </FormField>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => void handleStart()} disabled={createSession.isPending}>
            Iniciar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
```

- [ ] **Step 2: `SessionsListPage` — write tests, verify fail, implement (full rewrite), verify pass**

Create `frontend/src/features/sessions/SessionsListPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { SessionsListPage } from './SessionsListPage'

function SessionsUnderTest() {
  return (
    <Routes>
      <Route path="/sessions" element={<SessionsListPage />} />
      <Route path="/sessions/:id" element={<p>Detalhe da sessão</p>} />
    </Routes>
  )
}

describe('SessionsListPage', () => {
  it('lists sessions with the routine name and set count', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    expect(await screen.findByText('Treino A')).toBeInTheDocument()
  })

  it('shows "Iniciar treino" when there is no active session', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    await screen.findByText('Treino A')
    expect(screen.getByRole('button', { name: 'Iniciar treino' })).toBeInTheDocument()
  })

  it('shows "Continuar treino ativo" and navigates to it when a session is active', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions', () =>
        HttpResponse.json([
          { id: 'session-active', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, set_count: 2 },
        ]),
      ),
    )
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    const button = await screen.findByRole('button', { name: 'Continuar treino ativo' })
    await userEvent.click(button)
    await waitFor(() => expect(screen.getByText('Detalhe da sessão')).toBeInTheDocument())
  })

  it('starting a new session (no active one) navigates to its detail page', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/workout-sessions', () => HttpResponse.json([])))
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    await userEvent.click(await screen.findByRole('button', { name: 'Iniciar treino' }))
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar' }))
    await waitFor(() => expect(screen.getByText('Detalhe da sessão')).toBeInTheDocument())
  })
})
```

Run: `cd frontend && npm run test -- SessionsListPage.test.tsx` — expected FAIL (stub page has no interactive elements).

Replace `frontend/src/features/sessions/SessionsListPage.tsx`:
```tsx
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { PlateStat } from '../../components/PlateStat'
import { formatDateTime } from '../../lib/formatDate'
import { useRoutines } from '../routines/useRoutinesQueries'
import { StartSessionModal } from './StartSessionModal'
import { useSessions } from './useSessionsQueries'

export function SessionsListPage() {
  const { data: sessions, isLoading } = useSessions()
  const { data: routines } = useRoutines()
  const navigate = useNavigate()
  const [showStartModal, setShowStartModal] = useState(false)

  const sorted = useMemo(
    () => [...(sessions ?? [])].sort((a, b) => b.startedAt.localeCompare(a.startedAt)),
    [sessions],
  )
  const activeSession = sorted.find((session) => session.finishedAt === null)

  function routineName(routineId: string | null): string {
    if (!routineId) return 'Treino livre'
    return routines?.find((routine) => routine.id === routineId)?.name ?? 'Treino livre'
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando sessões...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Sessões</h1>
        {activeSession ? (
          <Button onClick={() => navigate(`/sessions/${activeSession.id}`)}>Continuar treino ativo</Button>
        ) : (
          <Button onClick={() => setShowStartModal(true)}>Iniciar treino</Button>
        )}
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {sorted.map((session) => (
          <li
            key={session.id}
            className="flex cursor-pointer items-center justify-between rounded-sm border border-line bg-surface px-4 py-3 hover:border-accent"
            onClick={() => navigate(`/sessions/${session.id}`)}
          >
            <div>
              <p className="font-body text-ink">{formatDateTime(session.startedAt)}</p>
              <p className="font-body text-xs text-muted">
                {routineName(session.routineId)}
                {session.finishedAt === null && ' · Ativa'}
              </p>
            </div>
            <PlateStat value={session.setCount} unit="séries" />
          </li>
        ))}
      </ul>

      {showStartModal && <StartSessionModal onClose={() => setShowStartModal(false)} />}
    </div>
  )
}
```

Run: `cd frontend && npm run test -- SessionsListPage.test.tsx` — expected PASS (4 tests).

- [ ] **Step 3: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/features/sessions/StartSessionModal.tsx frontend/src/features/sessions/SessionsListPage.tsx frontend/src/features/sessions/SessionsListPage.test.tsx
git commit -m "feat(frontend): historico de sessoes e fluxo de iniciar/continuar treino"
```

---

### Task 4: Tela de log de treino (`SessionDetailPage`)

**Files:**
- Modify: `frontend/src/features/sessions/SessionDetailPage.tsx` (full rewrite, replaces Task 1 stub) + `SessionDetailPage.test.tsx`

**Interfaces:**
- Consumes: `useSession()`/`useAddSet()`/`useFinishSession()` (Task 2), `useExercises()` (Sprint 5a), `Button`/`Modal`/`Select`/`FormField`/`Input`/`PlateStat`/`useToast` (Sprint 5a design system), `formatDateTime` (Task 1).
- Produces: nothing consumed by later tasks in this sprint.

- [ ] **Step 1: `SessionDetailPage` — write tests, verify fail, implement (full rewrite), verify pass**

Sets render in flat chronological order (the exact order the API already returns them in) rather than grouped by exercise — a deliberate choice: in a real workout, sets of one exercise are logged in a row before moving to the next, so the chronological list already visually shows `set_number` restarting each time the user switches exercises, without any client-side grouping logic.

Create `frontend/src/features/sessions/SessionDetailPage.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { FIXED_SESSION_ID, VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { SessionDetailPage } from './SessionDetailPage'

function DetailUnderTest() {
  return (
    <Routes>
      <Route path="/sessions/:id" element={<SessionDetailPage />} />
      <Route path="/sessions" element={<p>Lista de sessões</p>} />
    </Routes>
  )
}

describe('SessionDetailPage', () => {
  it('renders a finished session read-only, without an add-set form', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DetailUnderTest />, { route: `/sessions/${FIXED_SESSION_ID}` })
    expect(await screen.findByText('Treino finalizado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Adicionar série' })).not.toBeInTheDocument()
  })

  it('shows "Sessão não encontrada" for a missing session id', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/does-not-exist' })
    expect(await screen.findByText('Sessão não encontrada.')).toBeInTheDocument()
  })

  it('an active session shows the add-set form, and the new set shows "Série 1"', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions/active-1', () =>
        HttpResponse.json({ id: 'active-1', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, notes: null, sets: [] }),
      ),
      http.post('/workout-sessions/active-1/sets', async ({ request }) => {
        const body = (await request.json()) as { exercise_id: string; weight_kg: number; reps: number; rpe: number | null }
        return HttpResponse.json(
          {
            id: 'set-new-1',
            exercise_id: body.exercise_id,
            exercise_name: 'Supino Reto',
            set_number: 1,
            weight_kg: body.weight_kg,
            reps: body.reps,
            rpe: body.rpe,
            estimated_1rm_epley: body.weight_kg * (1 + body.reps / 30),
            estimated_1rm_brzycki: null,
            estimated_1rm_best: body.weight_kg * (1 + body.reps / 30),
            created_at: '2026-07-25T09:05:00Z',
          },
          { status: 201 },
        )
      }),
    )
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/active-1' })
    expect(await screen.findByText('Treino em andamento')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByRole('option', { name: 'Supino Reto' }))
    await userEvent.clear(screen.getByLabelText('Peso (kg)'))
    await userEvent.type(screen.getByLabelText('Peso (kg)'), '80')
    await userEvent.clear(screen.getByLabelText('Reps'))
    await userEvent.type(screen.getByLabelText('Reps'), '8')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar série' }))

    await waitFor(() => expect(screen.getByText('Série 1')).toBeInTheDocument())
  })

  it('finishing a session navigates back to the sessions list', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions/active-2', () =>
        HttpResponse.json({ id: 'active-2', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, notes: null, sets: [] }),
      ),
      http.patch('/workout-sessions/active-2', () =>
        HttpResponse.json({
          id: 'active-2',
          routine_id: null,
          started_at: '2026-07-25T09:00:00Z',
          finished_at: '2026-07-25T10:00:00Z',
          notes: null,
          sets: [],
        }),
      ),
    )
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/active-2' })
    await screen.findByText('Treino em andamento')

    await userEvent.click(screen.getByRole('button', { name: 'Finalizar treino' }))
    await userEvent.click(screen.getByRole('button', { name: 'Finalizar' }))

    await waitFor(() => expect(screen.getByText('Lista de sessões')).toBeInTheDocument())
  })
})
```

Run: `cd frontend && npm run test -- SessionDetailPage.test.tsx` — expected FAIL (stub page has no interactive elements).

Replace `frontend/src/features/sessions/SessionDetailPage.tsx`:
```tsx
import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { Modal } from '../../components/Modal'
import { PlateStat } from '../../components/PlateStat'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { formatDateTime } from '../../lib/formatDate'
import { useExercises } from '../exercises/useExercisesQueries'
import { useAddSet, useFinishSession, useSession } from './useSessionsQueries'

const addSetSchema = z.object({
  exerciseId: z.string().min(1, 'Selecione um exercício.'),
  weightKg: z.coerce.number().min(0, 'Não pode ser negativo.'),
  reps: z.coerce.number().int().min(1, 'Mínimo 1.'),
  rpe: z.string(),
})

type AddSetFormValues = z.infer<typeof addSetSchema>

function toNullableRpe(raw: string): number | null {
  const trimmed = raw.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: session, isLoading, isError } = useSession(id)
  const { data: exercises } = useExercises()
  const addSet = useAddSet()
  const finishSession = useFinishSession()
  const [showFinishModal, setShowFinishModal] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<AddSetFormValues>({
    resolver: zodResolver(addSetSchema),
    defaultValues: { exerciseId: '', weightKg: 0, reps: 8, rpe: '' },
  })

  async function onAddSet(values: AddSetFormValues) {
    if (!id) return
    try {
      await addSet.mutateAsync({
        sessionId: id,
        input: {
          exerciseId: values.exerciseId,
          weightKg: values.weightKg,
          reps: values.reps,
          rpe: toNullableRpe(values.rpe),
        },
      })
      reset({ exerciseId: values.exerciseId, weightKg: values.weightKg, reps: values.reps, rpe: '' })
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  async function handleFinish(notes: string) {
    if (!id) return
    try {
      await finishSession.mutateAsync({ id, input: { notes: notes.trim() === '' ? null : notes } })
      showToast('Treino finalizado.')
      navigate('/sessions')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setShowFinishModal(false)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando sessão...</p>
  }

  if (isError || !session) {
    return (
      <div>
        <p className="font-body text-muted">Sessão não encontrada.</p>
        <Link to="/sessions" className="mt-2 inline-block text-accent hover:underline">
          Voltar pra sessões
        </Link>
      </div>
    )
  }

  const isActive = session.finishedAt === null
  const exerciseOptions = (exercises ?? []).map((exercise) => ({ value: exercise.id, label: exercise.name }))

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">
            {isActive ? 'Treino em andamento' : 'Treino finalizado'}
          </h1>
          <p className="font-body text-xs text-muted">{formatDateTime(session.startedAt)}</p>
        </div>
        {isActive && (
          <Button variant="secondary" onClick={() => setShowFinishModal(true)}>
            Finalizar treino
          </Button>
        )}
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {session.sets.map((set) => (
          <li key={set.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{set.exerciseName}</p>
              <p className="font-body text-xs text-muted">
                Série {set.setNumber}
                {set.rpe !== null && ` · RPE ${set.rpe}`}
              </p>
            </div>
            <div className="flex gap-2">
              <PlateStat value={set.weightKg} unit="kg" />
              <PlateStat value={set.reps} unit="reps" />
              <PlateStat value={set.estimated1rmBest.toFixed(1)} unit="1rm" />
            </div>
          </li>
        ))}
      </ul>

      {isActive && (
        <form
          className="mt-6 flex flex-col gap-4 rounded-sm border border-line bg-surface p-4"
          onSubmit={handleSubmit(onAddSet)}
          noValidate
        >
          <p className="font-body text-xs font-semibold uppercase tracking-wide text-muted">Adicionar série</p>
          <FormField label="Exercício" htmlFor="exerciseId" error={errors.exerciseId?.message}>
            <Select
              id="exerciseId"
              value={watch('exerciseId')}
              onValueChange={(value) => setValue('exerciseId', value, { shouldValidate: true })}
              options={exerciseOptions}
              hasError={!!errors.exerciseId}
            />
          </FormField>
          <div className="flex gap-3">
            <FormField label="Peso (kg)" htmlFor="weightKg" error={errors.weightKg?.message}>
              <Input id="weightKg" type="number" step="0.01" hasError={!!errors.weightKg} {...register('weightKg')} />
            </FormField>
            <FormField label="Reps" htmlFor="reps" error={errors.reps?.message}>
              <Input id="reps" type="number" hasError={!!errors.reps} {...register('reps')} />
            </FormField>
            <FormField label="RPE (opcional)" htmlFor="rpe" error={errors.rpe?.message}>
              <Input id="rpe" type="number" step="0.5" hasError={!!errors.rpe} {...register('rpe')} />
            </FormField>
          </div>
          <Button type="submit" disabled={isSubmitting}>
            Adicionar série
          </Button>
        </form>
      )}

      {session.notes && <p className="mt-6 font-body text-sm text-muted">Notas: {session.notes}</p>}

      <Modal open={showFinishModal} onOpenChange={(open) => !open && setShowFinishModal(false)} title="Finalizar treino">
        <FinishSessionForm
          onConfirm={(notes) => void handleFinish(notes)}
          onCancel={() => setShowFinishModal(false)}
          isSubmitting={finishSession.isPending}
        />
      </Modal>
    </div>
  )
}

interface FinishSessionFormProps {
  onConfirm: (notes: string) => void
  onCancel: () => void
  isSubmitting: boolean
}

function FinishSessionForm({ onConfirm, onCancel, isSubmitting }: FinishSessionFormProps) {
  const [notes, setNotes] = useState('')
  return (
    <div className="flex flex-col gap-4">
      <FormField label="Notas (opcional)" htmlFor="finish-notes">
        <Input id="finish-notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
      </FormField>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button onClick={() => onConfirm(notes)} disabled={isSubmitting}>
          Finalizar
        </Button>
      </div>
    </div>
  )
}
```

Run: `cd frontend && npm run test -- SessionDetailPage.test.tsx` — expected PASS (4 tests).

- [ ] **Step 2: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/features/sessions/SessionDetailPage.tsx frontend/src/features/sessions/SessionDetailPage.test.tsx
git commit -m "feat(frontend): tela de log de treino - adicionar series, ver 1RM, finalizar"
```

---

### Task 5: API + hooks de dashboard + `pivotVolumeByWeek`

**Files:**
- Create: `frontend/src/api/dashboardApi.ts`
- Create: `frontend/src/features/dashboard/useDashboardQueries.ts`
- Create: `frontend/src/features/dashboard/pivotVolumeByWeek.ts` + `pivotVolumeByWeek.test.ts`

**Interfaces:**
- Consumes: `apiClient`, `formatShortDate` (Task 1), `MuscleGroup` type (`api/muscleGroupsApi.ts`, Sprint 5a).
- Produces (consumed by Tasks 6-7):
  - `dashboardApi.ts`: `interface ProgressionPoint {sessionStartedAt, estimated1rmBest}`, `interface Progression {exerciseId, exerciseName, points: ProgressionPoint[]}`, `interface VolumeBucket {muscleGroupId, weekStartUtc, totalVolumeKg}`, `interface PlateauAlert {exerciseId, exerciseName, currentMax1rm, suggestion}`, `getProgression(exerciseId)`, `getVolume()`, `getPlateaus()`.
  - `useDashboardQueries.ts`: `useProgression(exerciseId)`, `useVolume()`, `usePlateaus()`.
  - `pivotVolumeByWeek.ts`: `interface PivotedWeek {week, weekStartUtc, [muscleGroupName]: string | number}`, `pivotVolumeByWeek(buckets, muscleGroups): PivotedWeek[]`.

- [ ] **Step 1: `dashboardApi.ts` — implement (no dedicated test file; exercised via Tasks 6-7's component tests)**

Create `frontend/src/api/dashboardApi.ts`:
```ts
import { apiClient } from '../lib/apiClient'

export interface ProgressionPoint {
  sessionStartedAt: string
  estimated1rmBest: number
}

export interface Progression {
  exerciseId: string
  exerciseName: string
  points: ProgressionPoint[]
}

export interface VolumeBucket {
  muscleGroupId: string | null
  weekStartUtc: string
  totalVolumeKg: number
}

export interface PlateauAlert {
  exerciseId: string
  exerciseName: string
  currentMax1rm: number
  suggestion: string
}

export async function getProgression(exerciseId: string): Promise<Progression> {
  const response = await apiClient.get<Progression>(`/dashboard/progression/${exerciseId}`)
  return response.data
}

export async function getVolume(): Promise<VolumeBucket[]> {
  const response = await apiClient.get<VolumeBucket[]>('/dashboard/volume')
  return response.data
}

export async function getPlateaus(): Promise<PlateauAlert[]> {
  const response = await apiClient.get<PlateauAlert[]>('/dashboard/plateaus')
  return response.data
}
```

- [ ] **Step 2: React Query hooks — implement**

Create `frontend/src/features/dashboard/useDashboardQueries.ts`:
```ts
import { useQuery } from '@tanstack/react-query'
import { getPlateaus, getProgression, getVolume } from '../../api/dashboardApi'

export function useProgression(exerciseId: string | undefined) {
  return useQuery({
    queryKey: ['dashboard', 'progression', exerciseId ?? ''],
    queryFn: () => getProgression(exerciseId as string),
    enabled: Boolean(exerciseId),
  })
}

export function useVolume() {
  return useQuery({ queryKey: ['dashboard', 'volume'], queryFn: getVolume })
}

export function usePlateaus() {
  return useQuery({ queryKey: ['dashboard', 'plateaus'], queryFn: getPlateaus })
}
```

- [ ] **Step 3: `pivotVolumeByWeek` — write tests, verify fail, implement, verify pass**

`GET /dashboard/volume` returns a flat array (one row per muscle-group-per-week) with only `muscleGroupId` (no name). This function reshapes it into "1 row per week, 1 column per muscle-group NAME" — the exact shape Recharts' stacked `<Bar>` needs — resolving each id to its display name via the already-fetched muscle-group list. A week with no data for a given group simply omits that key from the pivoted row (not an explicit `0`) — Recharts renders a missing `dataKey` on a `<Bar>` as nothing for that segment, so this is both simpler code and correct behavior, not a shortcut.

Create `frontend/src/features/dashboard/pivotVolumeByWeek.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { pivotVolumeByWeek } from './pivotVolumeByWeek'

const MUSCLE_GROUPS = [
  { id: 'mg-chest', name: 'Peito' },
  { id: 'mg-back', name: 'Costas' },
]

describe('pivotVolumeByWeek', () => {
  it('groups buckets by week, one column per muscle group name', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 },
        { muscleGroupId: 'mg-back', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 800 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result).toHaveLength(1)
    expect(result[0].Peito).toBe(1000)
    expect(result[0].Costas).toBe(800)
  })

  it('sorts weeks chronologically regardless of input order', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-13T00:00:00Z', totalVolumeKg: 500 },
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result.map((week) => week.weekStartUtc)).toEqual(['2026-07-06T00:00:00Z', '2026-07-13T00:00:00Z'])
  })

  it('omits the key for a group with no data that week, rather than defaulting to 0', () => {
    const result = pivotVolumeByWeek(
      [{ muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 }],
      MUSCLE_GROUPS,
    )
    expect(result[0].Costas).toBeUndefined()
  })

  it('falls back to "Outro" for a null or unrecognized muscle group id', () => {
    const result = pivotVolumeByWeek(
      [{ muscleGroupId: null, weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 300 }],
      MUSCLE_GROUPS,
    )
    expect(result[0].Outro).toBe(300)
  })

  it('sums multiple buckets for the same group and week', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 500 },
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 300 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result[0].Peito).toBe(800)
  })

  it('returns an empty array for an empty input', () => {
    expect(pivotVolumeByWeek([], MUSCLE_GROUPS)).toEqual([])
  })
})
```

Run: `cd frontend && npm run test -- pivotVolumeByWeek.test.ts` — expected FAIL (`./pivotVolumeByWeek` doesn't exist).

Create `frontend/src/features/dashboard/pivotVolumeByWeek.ts`:
```ts
import type { VolumeBucket } from '../../api/dashboardApi'
import type { MuscleGroup } from '../../api/muscleGroupsApi'
import { formatShortDate } from '../../lib/formatDate'

export interface PivotedWeek {
  week: string
  weekStartUtc: string
  [muscleGroupName: string]: string | number
}

export function pivotVolumeByWeek(buckets: VolumeBucket[], muscleGroups: MuscleGroup[]): PivotedWeek[] {
  const nameById = new Map(muscleGroups.map((group) => [group.id, group.name]))
  const weeks = new Map<string, PivotedWeek>()

  for (const bucket of buckets) {
    const groupName = bucket.muscleGroupId ? (nameById.get(bucket.muscleGroupId) ?? 'Outro') : 'Outro'
    const existing = weeks.get(bucket.weekStartUtc)
    if (existing) {
      existing[groupName] = ((existing[groupName] as number) ?? 0) + bucket.totalVolumeKg
    } else {
      weeks.set(bucket.weekStartUtc, {
        week: formatShortDate(bucket.weekStartUtc),
        weekStartUtc: bucket.weekStartUtc,
        [groupName]: bucket.totalVolumeKg,
      })
    }
  }

  return Array.from(weeks.values()).sort((a, b) => a.weekStartUtc.localeCompare(b.weekStartUtc))
}
```

Run: `cd frontend && npm run test -- pivotVolumeByWeek.test.ts` — expected PASS (6 tests).

- [ ] **Step 4: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/api/dashboardApi.ts frontend/src/features/dashboard/useDashboardQueries.ts frontend/src/features/dashboard/pivotVolumeByWeek.ts frontend/src/features/dashboard/pivotVolumeByWeek.test.ts
git commit -m "feat(frontend): API e hooks de dashboard, pivotVolumeByWeek com cobertura completa"
```

---

### Task 6: Dashboard — progressão de 1RM (`ProgressionChart` + `ProgressionSection`)

**Files:**
- Create: `frontend/src/features/dashboard/ProgressionChart.tsx`
- Create: `frontend/src/features/dashboard/ProgressionSection.tsx` + `ProgressionSection.test.tsx`
- Modify: `frontend/src/test/mocks/handlers.ts` (add `/dashboard/progression/:exerciseId` and `/dashboard/plateaus` handlers)

**Interfaces:**
- Consumes: `useProgression()`/`usePlateaus()` (Task 5), `useExercises()` (Sprint 5a), `Select` (Sprint 5a design system), `formatShortDate` (Task 1).
- Produces: `ProgressionSection` (consumed by Task 7's `DashboardPage`).

- [ ] **Step 1: Extend MSW handlers with `/dashboard/progression/:exerciseId` and `/dashboard/plateaus`**

Both dashboard read endpoints are stateless (no create/update/delete in this app), so no mutable fixture or reset function is needed — just static handlers. Append to the exported `handlers` array in `frontend/src/test/mocks/handlers.ts`:
```ts
  http.get('/dashboard/progression/:exerciseId', ({ params }) => {
    if (params.exerciseId === 'ex-global-1') {
      return HttpResponse.json({
        exercise_id: 'ex-global-1',
        exercise_name: 'Supino Reto',
        points: [
          { session_started_at: '2026-06-01T12:00:00Z', estimated_1rm_best: 90 },
          { session_started_at: '2026-06-15T12:00:00Z', estimated_1rm_best: 95 },
          { session_started_at: '2026-07-01T12:00:00Z', estimated_1rm_best: 101.3 },
        ],
      })
    }
    return HttpResponse.json({ exercise_id: params.exerciseId, exercise_name: 'Exercício', points: [] })
  }),

  http.get('/dashboard/plateaus', () =>
    HttpResponse.json([
      {
        exercise_id: 'ex-custom-1',
        exercise_name: 'Supino Inclinado Halteres',
        current_max_1rm: 60,
        suggestion: 'Considere reduzir a carga em ~10% por 1 semana (deload).',
      },
    ]),
  ),
```
This gives 2 useful fixture states to test against: `ex-global-1` ("Supino Reto") has real progression data and is NOT in the plateau list; `ex-custom-1` ("Supino Inclinado Halteres") has no progression data configured (falls through to the empty-`points` default) and IS in the plateau list. Since `ExercisesListPage`'s established sort is alphabetical and "Supino Inclinado Halteres" < "Supino Reto" alphabetically, `ex-custom-1` is also the DEFAULT-selected exercise in `ProgressionSection` (Step 3) — exercising both the empty-state and plateau-banner paths by default, with switching to "Supino Reto" exercising the populated-chart, no-banner path.

- [ ] **Step 2: `ProgressionChart` — implement (Recharts wrapper, no dedicated test — see note on Recharts+jsdom below)**

Create `frontend/src/features/dashboard/ProgressionChart.tsx`:
```tsx
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface ProgressionChartPoint {
  date: string
  value: number
}

interface ProgressionChartProps {
  data: ProgressionChartPoint[]
}

export function ProgressionChart({ data }: ProgressionChartProps) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data}>
        <XAxis dataKey="date" stroke="var(--color-muted)" fontSize={12} />
        <YAxis stroke="var(--color-muted)" fontSize={12} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-line)', color: 'var(--color-ink)' }}
        />
        <Line type="monotone" dataKey="value" stroke="var(--color-accent)" strokeWidth={2} dot={{ fill: 'var(--color-accent)' }} />
      </LineChart>
    </ResponsiveContainer>
  )
}
```

`ResponsiveContainer` measures its parent via DOM APIs that don't reliably report real dimensions in jsdom — this is a known Recharts+jsdom limitation, not something to work around here. `ProgressionSection`'s test (Step 3) does not assert on anything inside this component's rendered SVG; it only asserts on the surrounding text (empty state, banner, selector), which renders correctly regardless of the chart's actual pixel dimensions in the test environment.

- [ ] **Step 3: `ProgressionSection` — write tests, verify fail, implement, verify pass**

Create `frontend/src/features/dashboard/ProgressionSection.test.tsx`:
```tsx
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { ProgressionSection } from './ProgressionSection'

describe('ProgressionSection', () => {
  it('shows the plateau banner and empty state for the default (plateaued, dataless) exercise', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ProgressionSection />)
    expect(await screen.findByText(/Platô ativo/)).toBeInTheDocument()
    expect(screen.getByText('Nenhum dado registrado ainda pra esse exercício.')).toBeInTheDocument()
  })

  it('switching to an exercise with data hides the banner and the empty state', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ProgressionSection />)
    await screen.findByText(/Platô ativo/)

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByRole('option', { name: 'Supino Reto' }))

    await waitFor(() => expect(screen.queryByText(/Platô ativo/)).not.toBeInTheDocument())
    expect(screen.queryByText('Nenhum dado registrado ainda pra esse exercício.')).not.toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- ProgressionSection.test.tsx` — expected FAIL (`./ProgressionSection` doesn't exist).

Create `frontend/src/features/dashboard/ProgressionSection.tsx`:
```tsx
import { useState } from 'react'
import { Select } from '../../components/Select'
import { formatShortDate } from '../../lib/formatDate'
import { useExercises } from '../exercises/useExercisesQueries'
import { ProgressionChart, type ProgressionChartPoint } from './ProgressionChart'
import { usePlateaus, useProgression } from './useDashboardQueries'

export function ProgressionSection() {
  const { data: exercises } = useExercises()
  const sortedExercises = [...(exercises ?? [])].sort((a, b) => a.name.localeCompare(b.name))
  const [selectedExerciseId, setSelectedExerciseId] = useState('')
  const effectiveExerciseId = selectedExerciseId || sortedExercises[0]?.id

  const { data: progression, isLoading } = useProgression(effectiveExerciseId)
  const { data: plateaus } = usePlateaus()

  const activePlateau = plateaus?.find((alert) => alert.exerciseId === effectiveExerciseId)

  const chartData: ProgressionChartPoint[] =
    progression?.points.map((point) => ({ date: formatShortDate(point.sessionStartedAt), value: point.estimated1rmBest })) ?? []

  return (
    <section>
      <div className="flex items-center justify-between">
        <h2 className="font-display text-xl font-bold text-ink">Progressão de 1RM</h2>
        <Select
          value={effectiveExerciseId}
          onValueChange={setSelectedExerciseId}
          options={sortedExercises.map((exercise) => ({ value: exercise.id, label: exercise.name }))}
        />
      </div>

      {activePlateau && (
        <p role="alert" className="mt-3 rounded-sm border border-accent bg-surface px-4 py-2 font-body text-sm text-accent">
          ⚠ Platô ativo — {activePlateau.suggestion}
        </p>
      )}

      {isLoading ? (
        <p className="mt-4 font-body text-muted">Carregando...</p>
      ) : chartData.length === 0 ? (
        <p className="mt-4 font-body text-muted">Nenhum dado registrado ainda pra esse exercício.</p>
      ) : (
        <div className="mt-4">
          <ProgressionChart data={chartData} />
        </div>
      )}
    </section>
  )
}
```

Run: `cd frontend && npm run test -- ProgressionSection.test.tsx` — expected PASS (2 tests).

- [ ] **Step 4: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/test/mocks/handlers.ts frontend/src/features/dashboard/ProgressionChart.tsx frontend/src/features/dashboard/ProgressionSection.tsx frontend/src/features/dashboard/ProgressionSection.test.tsx
git commit -m "feat(frontend): grafico de progressao de 1RM com seletor de exercicio e banner de plato"
```

---

### Task 7: Dashboard — volume + platôs (`VolumeChart` + `VolumeSection` + `PlateauAlertsList` + `DashboardPage`)

**Files:**
- Create: `frontend/src/features/dashboard/VolumeChart.tsx`
- Create: `frontend/src/features/dashboard/VolumeSection.tsx` + `VolumeSection.test.tsx`
- Create: `frontend/src/features/dashboard/PlateauAlertsList.tsx` + `PlateauAlertsList.test.tsx`
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx` (full rewrite, replaces Task 1 stub) + `DashboardPage.test.tsx`
- Modify: `frontend/src/test/mocks/handlers.ts` (add `/dashboard/volume` handler)
- Modify: `frontend/src/context/AuthContext.test.tsx` (extend the cache-isolation regression test to cover this sprint's new query keys)

**Interfaces:**
- Consumes: `useVolume()`/`usePlateaus()` (Task 5), `useMuscleGroups()` (Sprint 5a), `pivotVolumeByWeek()` (Task 5), `PlateStat` (Sprint 5a design system), `ProgressionSection` (Task 6).
- Produces: `DashboardPage` (consumed by `App.tsx`'s `/dashboard` route, already wired in Task 1).

- [ ] **Step 1: Extend MSW handlers with `/dashboard/volume`**

Append to the exported `handlers` array in `frontend/src/test/mocks/handlers.ts`:
```ts
  http.get('/dashboard/volume', () =>
    HttpResponse.json([
      { muscle_group_id: 'mg-chest', week_start_utc: '2026-07-06T00:00:00Z', total_volume_kg: 1200 },
      { muscle_group_id: 'mg-back', week_start_utc: '2026-07-06T00:00:00Z', total_volume_kg: 900 },
      { muscle_group_id: 'mg-chest', week_start_utc: '2026-07-13T00:00:00Z', total_volume_kg: 1300 },
    ]),
  ),
```
(`mg-chest`/`mg-back` already exist in the `MUSCLE_GROUPS` fixture from Sprint 5a — no changes needed there.)

- [ ] **Step 2: `VolumeChart` — implement (Recharts wrapper, no dedicated test — same jsdom rationale as `ProgressionChart`)**

Create `frontend/src/features/dashboard/VolumeChart.tsx`:
```tsx
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PivotedWeek } from './pivotVolumeByWeek'

const CHART_COLORS = [
  'var(--color-chart-iron)',
  'var(--color-chart-brass)',
  'var(--color-chart-olive)',
  'var(--color-chart-copper)',
  'var(--color-chart-gold)',
]

interface VolumeChartProps {
  data: PivotedWeek[]
  groupNames: string[]
}

export function VolumeChart({ data, groupNames }: VolumeChartProps) {
  return (
    <ResponsiveContainer width="100%" height={320}>
      <BarChart data={data}>
        <XAxis dataKey="week" stroke="var(--color-muted)" fontSize={12} />
        <YAxis stroke="var(--color-muted)" fontSize={12} />
        <Tooltip
          contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-line)', color: 'var(--color-ink)' }}
        />
        {groupNames.map((name, index) => (
          <Bar key={name} dataKey={name} stackId="volume" fill={CHART_COLORS[index % CHART_COLORS.length]} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}
```

- [ ] **Step 3: `VolumeSection` — write tests, verify fail, implement, verify pass**

Create `frontend/src/features/dashboard/VolumeSection.test.tsx`:
```tsx
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { VolumeSection } from './VolumeSection'

describe('VolumeSection', () => {
  it('renders without the empty state when volume data exists', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<VolumeSection />)
    await screen.findByText('Volume semanal por grupo muscular')
    expect(screen.queryByText('Nenhum volume registrado ainda.')).not.toBeInTheDocument()
  })

  it('shows an empty state when there is no volume data', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/dashboard/volume', () => HttpResponse.json([])))
    renderWithProviders(<VolumeSection />)
    expect(await screen.findByText('Nenhum volume registrado ainda.')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- VolumeSection.test.tsx` — expected FAIL (`./VolumeSection` doesn't exist).

Create `frontend/src/features/dashboard/VolumeSection.tsx`:
```tsx
import { useMemo } from 'react'
import { useMuscleGroups } from '../exercises/useExercisesQueries'
import { pivotVolumeByWeek } from './pivotVolumeByWeek'
import { useVolume } from './useDashboardQueries'
import { VolumeChart } from './VolumeChart'

export function VolumeSection() {
  const { data: buckets, isLoading: isLoadingVolume } = useVolume()
  const { data: muscleGroups, isLoading: isLoadingGroups } = useMuscleGroups()

  const pivoted = useMemo(() => pivotVolumeByWeek(buckets ?? [], muscleGroups ?? []), [buckets, muscleGroups])

  const groupNames = useMemo(() => {
    const names = new Set<string>()
    pivoted.forEach((week) => {
      Object.keys(week).forEach((key) => {
        if (key !== 'week' && key !== 'weekStartUtc') names.add(key)
      })
    })
    return Array.from(names)
  }, [pivoted])

  const isLoading = isLoadingVolume || isLoadingGroups

  return (
    <section className="mt-8">
      <h2 className="font-display text-xl font-bold text-ink">Volume semanal por grupo muscular</h2>
      {isLoading ? (
        <p className="mt-4 font-body text-muted">Carregando...</p>
      ) : pivoted.length === 0 ? (
        <p className="mt-4 font-body text-muted">Nenhum volume registrado ainda.</p>
      ) : (
        <div className="mt-4">
          <VolumeChart data={pivoted} groupNames={groupNames} />
        </div>
      )}
    </section>
  )
}
```

Run: `cd frontend && npm run test -- VolumeSection.test.tsx` — expected PASS (2 tests).

- [ ] **Step 4: `PlateauAlertsList` — write tests, verify fail, implement, verify pass**

Create `frontend/src/features/dashboard/PlateauAlertsList.test.tsx`:
```tsx
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { PlateauAlertsList } from './PlateauAlertsList'

describe('PlateauAlertsList', () => {
  it('lists active plateau alerts with the suggestion text', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<PlateauAlertsList />)
    expect(await screen.findByText('Supino Inclinado Halteres')).toBeInTheDocument()
    expect(screen.getByText('Considere reduzir a carga em ~10% por 1 semana (deload).')).toBeInTheDocument()
  })

  it('shows a positive empty state when there are no active plateaus', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/dashboard/plateaus', () => HttpResponse.json([])))
    renderWithProviders(<PlateauAlertsList />)
    expect(await screen.findByText('Nenhum platô ativo no momento — bom trabalho!')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- PlateauAlertsList.test.tsx` — expected FAIL (`./PlateauAlertsList` doesn't exist).

Create `frontend/src/features/dashboard/PlateauAlertsList.tsx`:
```tsx
import { PlateStat } from '../../components/PlateStat'
import { usePlateaus } from './useDashboardQueries'

export function PlateauAlertsList() {
  const { data: plateaus, isLoading } = usePlateaus()

  if (isLoading) {
    return <p className="mt-4 font-body text-muted">Carregando...</p>
  }

  if (!plateaus || plateaus.length === 0) {
    return <p className="mt-4 font-body text-ink">Nenhum platô ativo no momento — bom trabalho!</p>
  }

  return (
    <ul className="mt-4 flex flex-col gap-2">
      {plateaus.map((alert) => (
        <li key={alert.exerciseId} className="rounded-sm border border-accent bg-surface px-4 py-3">
          <div className="flex items-center justify-between">
            <p className="font-body text-ink">{alert.exerciseName}</p>
            <PlateStat value={alert.currentMax1rm.toFixed(1)} unit="1rm" />
          </div>
          <p className="mt-1 font-body text-xs text-muted">{alert.suggestion}</p>
        </li>
      ))}
    </ul>
  )
}
```

Run: `cd frontend && npm run test -- PlateauAlertsList.test.tsx` — expected PASS (2 tests).

- [ ] **Step 5: `DashboardPage` — write tests, verify fail, implement (full rewrite), verify pass**

Create `frontend/src/features/dashboard/DashboardPage.test.tsx`:
```tsx
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  it('composes all 3 sections', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DashboardPage />)
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Progressão de 1RM')).toBeInTheDocument()
    expect(screen.getByText('Volume semanal por grupo muscular')).toBeInTheDocument()
    expect(screen.getByText('Alertas de platô')).toBeInTheDocument()
  })
})
```

Run: `cd frontend && npm run test -- DashboardPage.test.tsx` — expected FAIL (stub page has no such headings).

Replace `frontend/src/features/dashboard/DashboardPage.tsx`:
```tsx
import { PlateauAlertsList } from './PlateauAlertsList'
import { ProgressionSection } from './ProgressionSection'
import { VolumeSection } from './VolumeSection'

export function DashboardPage() {
  return (
    <div className="flex flex-col gap-8">
      <h1 className="font-display text-2xl font-bold text-ink">Dashboard</h1>
      <ProgressionSection />
      <VolumeSection />
      <section>
        <h2 className="font-display text-xl font-bold text-ink">Alertas de platô</h2>
        <PlateauAlertsList />
      </section>
    </div>
  )
}
```

Run: `cd frontend && npm run test -- DashboardPage.test.tsx` — expected PASS (1 test).

- [ ] **Step 6: Extend the cache-isolation regression test to cover this sprint's new query keys**

`AuthContext.test.tsx` already has a test (added in Sprint 5a's final-review fix) proving `logout()` clears the TanStack Query cache. This step adds a new, fully self-contained test (it constructs its own local `QueryClient` rather than depending on the exact internal structure of the existing test, so it can't drift out of sync with it) proving the same mechanism covers this sprint's new query keys too.

Open `frontend/src/context/AuthContext.test.tsx` and check its existing imports — add `QueryClient` and `QueryClientProvider` to the existing `@tanstack/react-query` import line if they aren't already imported (the existing cache-clear test from Sprint 5a likely already imports one or both; do not add a duplicate import if so), and confirm `render`, `screen`, `waitFor` (from `@testing-library/react`) and `userEvent` (from `@testing-library/user-event`) are already imported (they are, used by the file's other tests) — reuse them, don't re-import.

Add this test inside the existing top-level `describe` block, anywhere after the existing cache-clear test:
```tsx
  it('logout also clears session and dashboard query keys introduced in Sprint 5b', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['workout-sessions'], [{ id: 'seeded' }])
    queryClient.setQueryData(['dashboard', 'volume'], [{ seeded: true }])
    expect(queryClient.getQueryCache().getAll().length).toBeGreaterThan(0)

    function Probe() {
      const { logout } = useAuth()
      return (
        <button type="button" onClick={() => void logout()}>
          logout
        </button>
      )
    }

    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'logout' }))

    await waitFor(() => expect(queryClient.getQueryData(['workout-sessions'])).toBeUndefined())
    expect(queryClient.getQueryData(['dashboard', 'volume'])).toBeUndefined()
  })
```
(The `Probe` component here is deliberately declared inside this test's own function body, not at module scope, to avoid any naming collision with a similarly-named helper the existing cache-clear test may already define at module scope.)

This is a regression test, not new application logic — there is no separate "make it pass" implementation step, since `queryClient.clear()` (already wired into `logout()` by Sprint 5a's final-review fix) clears every key unconditionally. The test either passes immediately, confirming the existing fix generalizes to this sprint's new keys, or it reveals that something in this sprint bypassed the shared `queryClient` — which would be a real bug to fix before proceeding, not a test to weaken.

Run: `cd frontend && npm run test -- AuthContext.test.tsx` — expected PASS (including the new test).

- [ ] **Step 7: Full verification + commit**

Run: `cd frontend && npm run test && npm run lint && npm run build`
Expected: all green.

```bash
git add frontend/src/test/mocks/handlers.ts frontend/src/features/dashboard/VolumeChart.tsx frontend/src/features/dashboard/VolumeSection.tsx frontend/src/features/dashboard/VolumeSection.test.tsx frontend/src/features/dashboard/PlateauAlertsList.tsx frontend/src/features/dashboard/PlateauAlertsList.test.tsx frontend/src/features/dashboard/DashboardPage.tsx frontend/src/features/dashboard/DashboardPage.test.tsx frontend/src/context/AuthContext.test.tsx
git commit -m "feat(frontend): grafico de volume por grupo muscular, lista de platos, composicao do dashboard"
```

---

### Task 8: Wrap-up

**Files:**
- Modify: `README.md`

**Interfaces:** none — documentation and verification only.

- [ ] **Step 1: Full automated verification**

Run from `frontend/`:
```bash
npm run test && npm run lint && npm run build
```
Expected: all three green.

- [ ] **Step 2: Manual smoke test (golden path + edge cases) — record pass/fail per step**

Bring up Postgres + backend (packaged jar, demo seed enabled — `quarkus:dev` has historically fought with this project's background-process tooling, prefer `mvnw package` + running the jar directly, same as prior sprints) + frontend dev server, then walk through:
1. Login with the demo account → lands on `/dashboard` (not `/exercises` — confirms the default-landing change from Task 1).
2. Dashboard shows: a 1RM progression chart for the default-selected exercise, a weekly volume stacked bar chart with multiple muscle-group colors, and the plateau alert for "Rosca Direta" with its deload suggestion text.
3. Switch the progression exercise selector to a few different exercises — chart updates, plateau banner appears only for "Rosca Direta".
4. Nav to "Sessões" — since the demo seed's 48 sessions are all finished, "Iniciar treino" should be the button (no active session).
5. Click "Iniciar treino" → optionally pick a routine → starts a new session, navigates to its detail page.
6. Add 2-3 sets across 2 different exercises → confirm `set_number` restarts at 1 for the second exercise, confirm the 1RM value shown per set looks plausible.
7. Finalize the session → redirected to `/sessions`, new session appears in the history list as the most recent entry.
8. Navigate to the dashboard again → the just-added session's data is reflected (may require a fresh query — confirm no stale flash of pre-session data).
9. Log out → log back in as the demo account → dashboard/session data is the demo account's own data again, no leftover state from the brief real session logged in step 5-7 causing any visual glitch (it should just show up as new history, not corrupt anything).
10. Register a brand-new account → dashboard shows correctly empty states for all 3 sections (no progression data for any exercise since none exist yet — actually a new account has zero custom exercises but sees the same global catalog; progression selector should still work using a global exercise, showing the empty-points state) → confirm no data leaked from the demo account session logged in earlier steps (this is the concrete check for Decision 10 in the live app, not just the automated regression test from Task 7).

If any step fails, fix the root cause before proceeding — do not mark the task done with a known-broken smoke test step.

- [ ] **Step 3: Update README**

In `README.md`, update the Status checklist:
```markdown
- [x] Sprint 5b — Frontend Sessões + Dashboard (Recharts)
```
(replace the existing `- [ ] Sprint 5b — Frontend Sessões + Dashboard (Recharts)` line)

Add a short paragraph to the existing "Conta demo" section (after the existing bullet list of "o que você vai ver") noting the dashboard is now live:
```markdown
O dashboard (`/dashboard`, tela inicial após login) mostra esses dados de verdade — gráfico
de progressão de 1RM por exercício, volume semanal por grupo muscular, e a lista de alertas
de platô — construído com Recharts sobre os mesmos endpoints.
```

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: marcar Sprint 5b completa, documentar dashboard no README"
```

---

## Verification (whole branch)

- `cd backend && ./mvnw test && ./mvnw verify` — still green, unchanged from the `develop` baseline (no backend files touched this sprint).
- `cd frontend && npm run test && npm run lint && npm run build` — all green.
- Manual smoke test (Task 8, Step 2) — all 10 steps pass.
- `git log --oneline develop..HEAD` shows 8 commits, one per task, each with a working, independently-testable state.

