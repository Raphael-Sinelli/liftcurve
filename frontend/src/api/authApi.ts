import { apiClient } from '../lib/apiClient'

export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface User {
  id: string
  email: string
  name: string
}

export async function register(email: string, password: string, name: string): Promise<AuthTokens> {
  const response = await apiClient.post<AuthTokens>('/auth/register', { email, password, name })
  return response.data
}

export async function login(email: string, password: string): Promise<AuthTokens> {
  const response = await apiClient.post<AuthTokens>('/auth/login', { email, password })
  return response.data
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}

export async function getMe(): Promise<User> {
  const response = await apiClient.get<User>('/users/me')
  return response.data
}
