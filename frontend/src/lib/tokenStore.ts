const REFRESH_TOKEN_STORAGE_KEY = 'gymtracker.refreshToken'

type AccessTokenListener = (token: string | null) => void

let accessToken: string | null = null
const listeners = new Set<AccessTokenListener>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
  listeners.forEach((listener) => listener(token))
}

export function subscribeAccessToken(listener: AccessTokenListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)
}

export function setStoredRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token)
}

export function clearStoredRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY)
}
