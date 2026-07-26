import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { toCamelCase, toSnakeCase } from './caseConversion'
import { extractApiError } from './errorMessages'
import {
  clearStoredRefreshToken,
  getAccessToken,
  getStoredRefreshToken,
  setAccessToken,
  setStoredRefreshToken,
} from './tokenStore'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

export const apiClient = axios.create({ baseURL })

apiClient.interceptors.request.use((config) => {
  if (config.data) {
    config.data = toSnakeCase(config.data)
  }
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    if (response.data) {
      response.data = toCamelCase(response.data)
    }
    return response
  },
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const apiError = extractApiError(error)
    const isAuthRoute = original?.url?.startsWith('/auth')

    if (
      error.response?.status === 401 &&
      apiError?.code === 'INVALID_TOKEN' &&
      original &&
      !original._retry &&
      !isAuthRoute
    ) {
      original._retry = true
      const newToken = await refreshAccessToken()
      original.headers.set('Authorization', `Bearer ${newToken}`)
      return apiClient(original)
    }

    return Promise.reject(error)
  },
)

let refreshInFlight: Promise<string> | null = null

// Uses a bare `axios` call, not `apiClient`, so refreshing never re-enters
// this same interceptor and doesn't depend on authApi.ts — which itself
// depends on apiClient — avoiding a circular import between the two modules.
export async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) {
    return refreshInFlight
  }

  refreshInFlight = (async () => {
    const refreshToken = getStoredRefreshToken()
    if (!refreshToken) {
      setAccessToken(null)
      throw new Error('Nenhum refresh token salvo.')
    }

    try {
      const response = await axios.post(`${baseURL}/auth/refresh`, toSnakeCase({ refreshToken }))
      const body = toCamelCase(response.data) as {
        accessToken: string
        refreshToken: string
        expiresInSeconds: number
      }
      setAccessToken(body.accessToken)
      setStoredRefreshToken(body.refreshToken)
      return body.accessToken
    } catch (err) {
      setAccessToken(null)
      clearStoredRefreshToken()
      throw err
    }
  })()

  try {
    return await refreshInFlight
  } finally {
    refreshInFlight = null
  }
}
