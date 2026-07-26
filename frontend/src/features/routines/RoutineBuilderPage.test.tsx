import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { RoutineBuilderPage } from './RoutineBuilderPage'

function BuilderUnderTest() {
  return (
    <Routes>
      <Route path="/routines/new" element={<RoutineBuilderPage />} />
      <Route path="/routines/:id" element={<RoutineBuilderPage />} />
      <Route path="/routines" element={<p>Lista de rotinas</p>} />
    </Routes>
  )
}

describe('RoutineBuilderPage', () => {
  it('blocks submit with no exercise rows removed down to zero', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/new' })
    await screen.findByText('Nova rotina')

    await userEvent.type(screen.getByLabelText('Nome'), 'Treino C')
    await userEvent.click(screen.getByRole('button', { name: 'Remover' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar rotina' }))

    expect(await screen.findByText('Adicione pelo menos 1 exercício.')).toBeInTheDocument()
  })

  it('adds a row, fills it out, and creates the routine', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/new' })
    await screen.findByText('Nova rotina')

    await userEvent.type(screen.getByLabelText('Nome'), 'Treino C')
    await userEvent.click(screen.getAllByRole('combobox')[0])
    await userEvent.click(await screen.findByRole('option', { name: 'Supino Reto' }))

    await userEvent.click(screen.getByRole('button', { name: 'Salvar rotina' }))
    await waitFor(() => expect(screen.getByText('Lista de rotinas')).toBeInTheDocument())
  })

  it('loads an existing routine into the form for editing', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<BuilderUnderTest />, { route: '/routines/routine-1' })
    await waitFor(() => expect(screen.getByDisplayValue('Treino A')).toBeInTheDocument())
    // Verify the correct exercise is selected in the visible combobox trigger (not just present
    // somewhere in the DOM — Radix's hidden native <select> fallback always lists every option).
    await waitFor(() => {
      expect(within(screen.getByRole('combobox')).getByText('Supino Reto')).toBeInTheDocument()
    })
  })
})
