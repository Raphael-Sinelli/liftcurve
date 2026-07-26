import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { VALID_REFRESH_TOKEN } from '../test/mocks/handlers'
import { setStoredRefreshToken } from '../lib/tokenStore'
import { renderWithProviders } from '../test/renderWithProviders'
import { ProtectedRoute } from './ProtectedRoute'

function Guarded() {
  return (
    <Routes>
      <Route path="/login" element={<p>Tela de login</p>} />
      <Route
        path="/exercises"
        element={
          <ProtectedRoute>
            <p>Conteúdo protegido</p>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

describe('ProtectedRoute', () => {
  it('redirects to /login when there is no session', async () => {
    renderWithProviders(<Guarded />, { route: '/exercises' })
    await waitFor(() => expect(screen.getByText('Tela de login')).toBeInTheDocument())
  })

  it('renders the protected content when a session is restored', async () => {
    setStoredRefreshToken(VALID_REFRESH_TOKEN)
    renderWithProviders(<Guarded />, { route: '/exercises' })
    await waitFor(() => expect(screen.getByText('Conteúdo protegido')).toBeInTheDocument())
  })
})
