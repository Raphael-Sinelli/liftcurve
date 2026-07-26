import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { FIXED_SESSION_ID, VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { SessionDetailPage } from './SessionDetailPage'

function DetailUnderTest() {
  return (
    <Routes>
      <Route path="/sessions/:id" element={<SessionDetailPage />} />
      <Route path="/sessions" element={<p>Lista de sessões</p>} />
    </Routes>
  )
}

describe('SessionDetailPage', () => {
  it('renders a finished session read-only, without an add-set form', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DetailUnderTest />, { route: `/sessions/${FIXED_SESSION_ID}` })
    expect(await screen.findByText('Treino finalizado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Adicionar série' })).not.toBeInTheDocument()
  })

  it('shows "Sessão não encontrada" for a missing session id', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/does-not-exist' })
    expect(await screen.findByText('Sessão não encontrada.')).toBeInTheDocument()
  })

  it('an active session shows the add-set form, and the new set shows "Série 1"', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions/active-1', () =>
        HttpResponse.json({ id: 'active-1', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, notes: null, sets: [] }),
      ),
      http.post('/workout-sessions/active-1/sets', async ({ request }) => {
        const body = (await request.json()) as { exercise_id: string; weight_kg: number; reps: number; rpe: number | null }
        return HttpResponse.json(
          {
            id: 'set-new-1',
            exercise_id: body.exercise_id,
            exercise_name: 'Supino Reto',
            set_number: 1,
            weight_kg: body.weight_kg,
            reps: body.reps,
            rpe: body.rpe,
            estimated_1rm_epley: body.weight_kg * (1 + body.reps / 30),
            estimated_1rm_brzycki: null,
            estimated_1rm_best: body.weight_kg * (1 + body.reps / 30),
            created_at: '2026-07-25T09:05:00Z',
          },
          { status: 201 },
        )
      }),
    )
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/active-1' })
    expect(await screen.findByText('Treino em andamento')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByRole('option', { name: 'Supino Reto' }))
    await userEvent.clear(screen.getByLabelText('Peso (kg)'))
    await userEvent.type(screen.getByLabelText('Peso (kg)'), '80')
    await userEvent.clear(screen.getByLabelText('Reps'))
    await userEvent.type(screen.getByLabelText('Reps'), '8')
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar série' }))

    await waitFor(() => expect(screen.getByText('Série 1')).toBeInTheDocument())
  })

  it('finishing a session navigates back to the sessions list', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/workout-sessions/active-2', () =>
        HttpResponse.json({ id: 'active-2', routine_id: null, started_at: '2026-07-25T09:00:00Z', finished_at: null, notes: null, sets: [] }),
      ),
      http.patch('/workout-sessions/active-2', () =>
        HttpResponse.json({
          id: 'active-2',
          routine_id: null,
          started_at: '2026-07-25T09:00:00Z',
          finished_at: '2026-07-25T10:00:00Z',
          notes: null,
          sets: [],
        }),
      ),
    )
    renderWithProviders(<DetailUnderTest />, { route: '/sessions/active-2' })
    await screen.findByText('Treino em andamento')

    await userEvent.click(screen.getByRole('button', { name: 'Finalizar treino' }))
    await userEvent.click(screen.getByRole('button', { name: 'Finalizar' }))

    await waitFor(() => expect(screen.getByText('Lista de sessões')).toBeInTheDocument())
  })
})
