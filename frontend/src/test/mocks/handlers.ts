import { http, HttpResponse } from 'msw'

export const DEMO_EMAIL = 'demo@gymtracker.app'
export const DEMO_PASSWORD = 'DemoGymTracker2026!'
export const VALID_ACCESS_TOKEN = 'valid-access-token'
export const VALID_REFRESH_TOKEN = 'valid-refresh-token'
export const ROTATED_ACCESS_TOKEN = 'rotated-access-token'
export const ROTATED_REFRESH_TOKEN = 'rotated-refresh-token'

interface LoginBody {
  email: string
  password: string
}

interface RegisterBody extends LoginBody {
  name: string
}

interface RefreshBody {
  refresh_token: string
}

export const MUSCLE_GROUPS = [
  { id: 'mg-chest', name: 'Peito' },
  { id: 'mg-back', name: 'Costas' },
]

let exercisesFixture = [
  { id: 'ex-global-1', name: 'Supino Reto', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: null, created_at: '2026-01-01T00:00:00Z' },
  { id: 'ex-custom-1', name: 'Supino Inclinado Halteres', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: 'demo-user-id', created_at: '2026-01-02T00:00:00Z' },
]

export function resetExercisesFixture() {
  exercisesFixture = [
    { id: 'ex-global-1', name: 'Supino Reto', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: null, created_at: '2026-01-01T00:00:00Z' },
    { id: 'ex-custom-1', name: 'Supino Inclinado Halteres', muscle_group_id: 'mg-chest', muscle_group_name: 'Peito', owner_id: 'demo-user-id', created_at: '2026-01-02T00:00:00Z' },
  ]
}

interface RoutineFixture {
  id: string
  name: string
  description: string | null
  created_at: string
  exercises: Array<{
    exercise_id: string
    exercise_name: string
    order_index: number
    planned_sets: number
    planned_reps: number
    planned_load_kg: number | null
  }>
}

let routinesFixture: RoutineFixture[] = [
  {
    id: 'routine-1',
    name: 'Treino A',
    description: 'Peito e tríceps',
    created_at: '2026-01-01T00:00:00Z',
    exercises: [
      { exercise_id: 'ex-global-1', exercise_name: 'Supino Reto', order_index: 0, planned_sets: 3, planned_reps: 10, planned_load_kg: 60 },
    ],
  },
]

export function resetRoutinesFixture() {
  routinesFixture = [
    {
      id: 'routine-1',
      name: 'Treino A',
      description: 'Peito e tríceps',
      created_at: '2026-01-01T00:00:00Z',
      exercises: [
        { exercise_id: 'ex-global-1', exercise_name: 'Supino Reto', order_index: 0, planned_sets: 3, planned_reps: 10, planned_load_kg: 60 },
      ],
    },
  ]
}

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

function toSummary(routine: RoutineFixture) {
  return {
    id: routine.id,
    name: routine.name,
    description: routine.description,
    created_at: routine.created_at,
    exercise_count: routine.exercises.length,
  }
}

export const handlers = [
  http.post('/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginBody
    if (body.email === DEMO_EMAIL && body.password === DEMO_PASSWORD) {
      return HttpResponse.json({
        access_token: VALID_ACCESS_TOKEN,
        refresh_token: VALID_REFRESH_TOKEN,
        expires_in_seconds: 900,
      })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_CREDENTIALS', message: 'Email ou senha inválidos.', status: 401, details: [] } },
      { status: 401 },
    )
  }),

  http.post('/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterBody
    if (body.email === 'taken@gymtracker.app') {
      return HttpResponse.json(
        { error: { code: 'EMAIL_ALREADY_REGISTERED', message: 'Este email já está cadastrado.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    return HttpResponse.json(
      { access_token: VALID_ACCESS_TOKEN, refresh_token: VALID_REFRESH_TOKEN, expires_in_seconds: 900 },
      { status: 201 },
    )
  }),

  http.post('/auth/refresh', async ({ request }) => {
    const body = (await request.json()) as RefreshBody
    if (body.refresh_token === VALID_REFRESH_TOKEN) {
      return HttpResponse.json({
        access_token: ROTATED_ACCESS_TOKEN,
        refresh_token: ROTATED_REFRESH_TOKEN,
        expires_in_seconds: 900,
      })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_REFRESH_TOKEN', message: 'Sua sessão expirou. Faça login novamente.', status: 401, details: [] } },
      { status: 401 },
    )
  }),

  http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })),

  http.get('/users/me', ({ request }) => {
    const auth = request.headers.get('authorization')
    if (auth === `Bearer ${VALID_ACCESS_TOKEN}` || auth === `Bearer ${ROTATED_ACCESS_TOKEN}`) {
      return HttpResponse.json({ id: 'demo-user-id', email: DEMO_EMAIL, name: 'Conta Demo' })
    }
    return HttpResponse.json(
      { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou. Faça login novamente.', status: 401, details: [] } },
      { status: 401 },
    )
  }),

  http.get('/muscle-groups', () => HttpResponse.json(MUSCLE_GROUPS)),

  http.get('/exercises', () => HttpResponse.json(exercisesFixture)),

  http.post('/exercises', async ({ request }) => {
    const body = (await request.json()) as { name: string; muscle_group_id: string }
    const created = {
      id: `ex-new-${exercisesFixture.length + 1}`,
      name: body.name,
      muscle_group_id: body.muscle_group_id,
      muscle_group_name: MUSCLE_GROUPS.find((g) => g.id === body.muscle_group_id)?.name ?? 'Desconhecido',
      owner_id: 'demo-user-id',
      created_at: '2026-07-25T00:00:00Z',
    }
    exercisesFixture = [...exercisesFixture, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/exercises/:id', async ({ params, request }) => {
    const body = (await request.json()) as { name: string; muscle_group_id: string }
    const existing = exercisesFixture.find((e) => e.id === params.id)
    if (!existing) {
      return HttpResponse.json({ error: { code: 'EXERCISE_NOT_FOUND', message: 'Exercício não encontrado.', status: 404, details: [] } }, { status: 404 })
    }
    const updated = { ...existing, name: body.name, muscle_group_id: body.muscle_group_id }
    exercisesFixture = exercisesFixture.map((e) => (e.id === params.id ? updated : e))
    return HttpResponse.json(updated)
  }),

  http.delete('/exercises/:id', ({ params }) => {
    if (params.id === 'ex-in-use') {
      return HttpResponse.json(
        { error: { code: 'EXERCISE_IN_USE', message: 'Este exercício está em uso e não pode ser excluído.', status: 409, details: [] } },
        { status: 409 },
      )
    }
    exercisesFixture = exercisesFixture.filter((e) => e.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/routines', () => HttpResponse.json(routinesFixture.map(toSummary))),

  http.get('/routines/:id', ({ params }) => {
    const routine = routinesFixture.find((r) => r.id === params.id)
    if (!routine) {
      return HttpResponse.json({ error: { code: 'ROUTINE_NOT_FOUND', message: 'Rotina não encontrada.', status: 404, details: [] } }, { status: 404 })
    }
    return HttpResponse.json(routine)
  }),

  http.post('/routines', async ({ request }) => {
    const body = (await request.json()) as {
      name: string
      description: string | null
      exercises: { exercise_id: string; planned_sets: number; planned_reps: number; planned_load_kg: number | null }[]
    }
    if (body.exercises.some((e) => e.exercise_id === 'ex-invalid')) {
      return HttpResponse.json(
        { error: { code: 'INVALID_EXERCISE_REFERENCE', message: 'Um dos exercícios selecionados não é válido.', status: 400, details: [] } },
        { status: 400 },
      )
    }
    const created: RoutineFixture = {
      id: `routine-new-${routinesFixture.length + 1}`,
      name: body.name,
      description: body.description,
      created_at: '2026-07-25T00:00:00Z',
      exercises: body.exercises.map((e, index) => ({
        exercise_id: e.exercise_id,
        exercise_name: 'Exercício',
        order_index: index,
        planned_sets: e.planned_sets,
        planned_reps: e.planned_reps,
        planned_load_kg: e.planned_load_kg,
      })),
    }
    routinesFixture = [...routinesFixture, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/routines/:id', async ({ params, request }) => {
    const body = (await request.json()) as {
      name: string
      description: string | null
      exercises: { exercise_id: string; planned_sets: number; planned_reps: number; planned_load_kg: number | null }[]
    }
    const existing = routinesFixture.find((r) => r.id === params.id)
    if (!existing) {
      return HttpResponse.json({ error: { code: 'ROUTINE_NOT_FOUND', message: 'Rotina não encontrada.', status: 404, details: [] } }, { status: 404 })
    }
    const updated: RoutineFixture = {
      ...existing,
      name: body.name,
      description: body.description,
      exercises: body.exercises.map((e, index) => ({
        exercise_id: e.exercise_id,
        exercise_name: 'Exercício',
        order_index: index,
        planned_sets: e.planned_sets,
        planned_reps: e.planned_reps,
        planned_load_kg: e.planned_load_kg,
      })),
    }
    routinesFixture = routinesFixture.map((r) => (r.id === params.id ? updated : r))
    return HttpResponse.json(updated)
  }),

  http.delete('/routines/:id', ({ params }) => {
    routinesFixture = routinesFixture.filter((r) => r.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),

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
]
