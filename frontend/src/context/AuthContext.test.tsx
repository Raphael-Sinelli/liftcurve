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

describe('AuthProvider', () => {
  it('finishes loading with no user when there is no stored refresh token', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })

  it('silently restores the session on boot when a valid refresh token is stored', async () => {
    setStoredRefreshToken(VALID_REFRESH_TOKEN)
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('loginAsDemo populates the user', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))
  })

  it('logout clears the user and the stored refresh token', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByText('demo-login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Conta Demo'))

    await userEvent.click(screen.getByText('logout'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(getAccessToken()).toBeNull()
  })
})
