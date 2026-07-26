import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { getMe, login as loginRequest, logout as logoutRequest, register as registerRequest, type User } from '../api/authApi'
import { refreshAccessToken } from '../lib/apiClient'
import {
  clearStoredRefreshToken,
  getAccessToken,
  getStoredRefreshToken,
  setAccessToken,
  setStoredRefreshToken,
  subscribeAccessToken,
} from '../lib/tokenStore'

export const DEMO_EMAIL = 'demo@gymtracker.app'
export const DEMO_PASSWORD = 'DemoGymTracker2026!'

interface AuthContextValue {
  user: User | null
  accessToken: string | null
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  loginAsDemo: () => Promise<void>
  register: (email: string, password: string, name: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(getAccessToken())
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    return subscribeAccessToken((newToken) => {
      setToken(newToken)
      if (!newToken) {
        setUser(null)
      }
    })
  }, [])

  useEffect(() => {
    async function bootSession() {
      const storedRefreshToken = getStoredRefreshToken()
      if (!storedRefreshToken) {
        setIsLoading(false)
        return
      }
      try {
        await refreshAccessToken()
        const profile = await getMe()
        setUser(profile)
      } catch {
        // refreshAccessToken já limpa os tokens em caso de falha
      } finally {
        setIsLoading(false)
      }
    }
    void bootSession()
  }, [])

  const applySession = useCallback(async (tokens: { accessToken: string; refreshToken: string }) => {
    setAccessToken(tokens.accessToken)
    setStoredRefreshToken(tokens.refreshToken)
    const profile = await getMe()
    setUser(profile)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await loginRequest(email, password)
      await applySession(tokens)
    },
    [applySession],
  )

  const loginAsDemo = useCallback(() => login(DEMO_EMAIL, DEMO_PASSWORD), [login])

  const register = useCallback(
    async (email: string, password: string, name: string) => {
      const tokens = await registerRequest(email, password, name)
      await applySession(tokens)
    },
    [applySession],
  )

  const logout = useCallback(async () => {
    const refreshToken = getStoredRefreshToken()
    if (refreshToken) {
      try {
        await logoutRequest(refreshToken)
      } catch {
        // logout no backend é idempotente/best-effort — sessão local é limpa de qualquer forma
      }
    }
    setAccessToken(null)
    clearStoredRefreshToken()
  }, [])

  return (
    <AuthContext.Provider value={{ user, accessToken: token, isLoading, login, loginAsDemo, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider')
  }
  return context
}
