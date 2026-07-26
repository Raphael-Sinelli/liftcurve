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
]
