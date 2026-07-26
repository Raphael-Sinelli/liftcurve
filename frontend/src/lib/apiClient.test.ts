import { http, HttpResponse, delay } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { server } from '../test/mocks/server'
import { ROTATED_ACCESS_TOKEN, VALID_ACCESS_TOKEN, VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { apiClient, refreshAccessToken } from './apiClient'
import { getAccessToken, getStoredRefreshToken, setAccessToken, setStoredRefreshToken } from './tokenStore'

describe('apiClient', () => {
  beforeEach(() => {
    setAccessToken(null)
    localStorage.clear()
  })

  it('converts the request body to snake_case', async () => {
    server.use(
      http.post('/echo', async ({ request }) => HttpResponse.json(await request.json())),
    )
    const response = await apiClient.post('/echo', { weightKg: 80, plannedSets: 3 })
    expect(response.data).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('converts the response body to camelCase', async () => {
    server.use(http.get('/echo-snake', () => HttpResponse.json({ weight_kg: 80, planned_sets: 3 })))
    const response = await apiClient.get('/echo-snake')
    expect(response.data).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('attaches the Authorization header when an access token is set', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    const response = await apiClient.get('/users/me')
    expect(response.data).toEqual({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
  })

  it('on a 401 INVALID_TOKEN from a protected route, refreshes and retries once', async () => {
    setAccessToken('expired-token')
    setStoredRefreshToken(VALID_REFRESH_TOKEN)

    let call = 0
    server.use(
      http.get('/users/me', ({ request }) => {
        call += 1
        if (call === 1) {
          return HttpResponse.json(
            { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou.', status: 401, details: [] } },
            { status: 401 },
          )
        }
        expect(request.headers.get('authorization')).toBe(`Bearer ${ROTATED_ACCESS_TOKEN}`)
        return HttpResponse.json({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
      }),
    )

    const response = await apiClient.get('/users/me')
    expect(response.data.name).toBe('Conta Demo')
    expect(call).toBe(2)
    expect(getAccessToken()).toBe(ROTATED_ACCESS_TOKEN)
  })

  it('deduplicates concurrent refreshes triggered by simultaneous 401s', async () => {
    setAccessToken('expired-token')
    setStoredRefreshToken(VALID_REFRESH_TOKEN)

    let usersMeCalls = 0
    let refreshCalls = 0
    server.use(
      http.get('/users/me', async () => {
        usersMeCalls += 1
        if (usersMeCalls <= 2) {
          return HttpResponse.json(
            { error: { code: 'INVALID_TOKEN', message: 'Sua sessão expirou.', status: 401, details: [] } },
            { status: 401 },
          )
        }
        return HttpResponse.json({ id: 'demo-user-id', email: 'demo@gymtracker.app', name: 'Conta Demo' })
      }),
      http.post('/auth/refresh', async () => {
        refreshCalls += 1
        await delay(20)
        return HttpResponse.json({
          access_token: ROTATED_ACCESS_TOKEN,
          refresh_token: 'rotated-refresh-token',
          expires_in_seconds: 900,
        })
      }),
    )

    const [first, second] = await Promise.all([apiClient.get('/users/me'), apiClient.get('/users/me')])
    expect(first.data.name).toBe('Conta Demo')
    expect(second.data.name).toBe('Conta Demo')
    expect(refreshCalls).toBe(1)
  })

  it('clears tokens when the refresh call itself fails', async () => {
    setStoredRefreshToken('an-invalid-refresh-token')
    await expect(refreshAccessToken()).rejects.toBeTruthy()
    expect(getAccessToken()).toBeNull()
    expect(getStoredRefreshToken()).toBeNull()
  })

  it('does not attempt a refresh for a 401 coming from /auth/* routes', async () => {
    await expect(apiClient.post('/auth/login', { email: 'x@x.com', password: 'wrong' })).rejects.toMatchObject({
      response: { status: 401 },
    })
  })
})
