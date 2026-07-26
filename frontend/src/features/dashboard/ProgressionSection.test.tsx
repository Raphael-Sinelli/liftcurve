import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { ProgressionSection } from './ProgressionSection'

describe('ProgressionSection', () => {
  it('shows the plateau banner and empty state for the default (plateaued, dataless) exercise', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ProgressionSection />)
    expect(await screen.findByText(/Platô ativo/)).toBeInTheDocument()
    expect(await screen.findByText('Nenhum dado registrado ainda pra esse exercício.')).toBeInTheDocument()
  })

  it('switching to an exercise with data hides the banner and the empty state', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ProgressionSection />)
    await screen.findByText(/Platô ativo/)

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByRole('option', { name: 'Supino Reto' }))

    await waitFor(() => expect(screen.queryByText(/Platô ativo/)).not.toBeInTheDocument())
    expect(screen.queryByText('Nenhum dado registrado ainda pra esse exercício.')).not.toBeInTheDocument()
  })

  it('shows the loading state (never the false empty state) while exercises are still resolving', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    let resolveExercises: () => void = () => {}
    const exercisesGate = new Promise<void>((resolve) => {
      resolveExercises = resolve
    })
    server.use(
      http.get('/exercises', async () => {
        await exercisesGate
        return HttpResponse.json([
          {
            id: 'ex-custom-1',
            name: 'Supino Inclinado Halteres',
            muscle_group_id: 'mg-chest',
            muscle_group_name: 'Peito',
            owner_id: 'demo-user-id',
            created_at: '2026-01-02T00:00:00Z',
          },
        ])
      }),
    )

    renderWithProviders(<ProgressionSection />)

    // effectiveExerciseId is still '' at this point (exercises haven't resolved), so the
    // progression query is disabled. If loading isn't gated on useExercises() too, this would
    // incorrectly render the empty state instead of the loading indicator.
    expect(screen.getByText('Carregando...')).toBeInTheDocument()
    expect(screen.queryByText('Nenhum dado registrado ainda pra esse exercício.')).not.toBeInTheDocument()

    resolveExercises()
    await waitFor(() => expect(screen.queryByText('Carregando...')).not.toBeInTheDocument())
  })
})
