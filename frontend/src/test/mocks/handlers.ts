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
]
