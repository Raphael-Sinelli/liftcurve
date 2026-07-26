import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { getAccessToken, setStoredRefreshToken } from '../lib/tokenStore'
import { AuthProvider, useAuth } from './AuthContext'

function Probe() {
  const { user, isLoading, login, loginAsDemo, logout } = useAuth()
  return (
    <div>
      <p data-testid="loading">{String(isLoading)}</p>
      <p data-testid="user">{user?.name ?? 'none'}</p>
      <button onClick={() => void loginAsDemo()}>demo-login</button>
      <button onClick={() => void login('wrong@x.com', 'wrong')}>bad-login</button>
      <button onClick={() => void logout()}>logout</button>
    </div>
  )
}

function renderWithAuth(queryClient: QueryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return queryClient
}

describe('AuthProvider', () => {
  it('finishes loading with no user when there is no stored refresh token', async () => {
    renderWithAuth()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })

  it('silently restores the session on boot when a valid refresh token is stored', async () => {
    setStoredRefreshToken(VALID_REFRESH_TOKEN)
    renderWithAuth()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('loginAsDemo populates the user', async () => {
    renderWithAuth()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('logout clears the user and the stored refresh token', async () => {
    renderWithAuth()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))

    await userEvent.click(screen.getByText('logout'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(getAccessToken()).toBeNull()
  })

  it('logout clears the React Query cache so the next session never sees stale data', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    // Seed the cache with a dummy query, simulating data fetched during the previous user's session
    // (e.g. ['exercises'] or ['routines']), which are not user-scoped query keys.
    queryClient.setQueryData(['exercises'], [{ id: 'previous-user-exercise' }])
    expect(queryClient.getQueryCache().getAll().length).toBeGreaterThan(0)

    renderWithAuth(queryClient)
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))

    await userEvent.click(screen.getByText('logout'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(queryClient.getQueryCache().getAll().length).toBe(0)
  })

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
})
