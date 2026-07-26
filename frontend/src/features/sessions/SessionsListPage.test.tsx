import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { SessionsListPage } from './SessionsListPage'

function SessionsUnderTest() {
  return (
    <Routes>
      <Route path="/sessions" element={<SessionsListPage />} />
      <Route path="/sessions/:id" element={<p>Detalhe da sessão</p>} />
    </Routes>
  )
}

describe('SessionsListPage', () => {
  it('lists sessions with the routine name and set count', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    expect(await screen.findByText('Treino A')).toBeInTheDocument()
  })

  it('shows "Iniciar treino" when there is no active session', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    await screen.findByText('Treino A')
    expect(screen.getByRole('button', { name: 'Iniciar treino' })).toBeInTheDocument()
  })

  it('shows "Continuar treino ativo" and navigates to it when a session is active', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions', () =>
        HttpResponse.json([
          { id: 'session-active', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, set_count: 2 },
        ]),
      ),
    )
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    const button = await screen.findByRole('button', { name: 'Continuar treino ativo' })
    await userEvent.click(button)
    await waitFor(() => expect(screen.getByText('Detalhe da sessão')).toBeInTheDocument())
  })

  it('starting a new session (no active one) navigates to its detail page', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/workout-sessions', () => HttpResponse.json([])))
    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })
    await userEvent.click(await screen.findByRole('button', { name: 'Iniciar treino' }))
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar' }))
    await waitFor(() => expect(screen.getByText('Detalhe da sessão')).toBeInTheDocument())
  })

  it('never shows the wrong "Treino livre" label while routines are still resolving', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    let resolveRoutines: () => void = () => {}
    const routinesGate = new Promise<void>((resolve) => {
      resolveRoutines = resolve
    })
    server.use(
      http.get('/routines', async () => {
        await routinesGate
        return HttpResponse.json([
          { id: 'routine-1', name: 'Treino A', description: 'Peito e tríceps', created_at: '2026-01-01T00:00:00Z', exercise_count: 1 },
        ])
      }),
    )

    renderWithProviders(<SessionsUnderTest />, { route: '/sessions' })

    // Sessions resolve quickly (no artificial delay) while routines stay gated. If loading isn't
    // gated on useRoutines() too, the list renders early and shows "Treino livre" instead of the
    // real routine name for a session that does have a routineId.
    await waitFor(() => expect(screen.getByText('Carregando sessões...')).toBeInTheDocument())
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.getByText('Carregando sessões...')).toBeInTheDocument()
    expect(screen.queryByText('Treino livre')).not.toBeInTheDocument()

    resolveRoutines()
    expect(await screen.findByText('Treino A')).toBeInTheDocument()
  })
})
