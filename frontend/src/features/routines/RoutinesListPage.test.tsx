import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { RoutinesListPage } from './RoutinesListPage'

function RoutinesUnderTest() {
  return (
    <Routes>
      <Route path="/routines" element={<RoutinesListPage />} />
      <Route path="/routines/new" element={<p>Construtor de rotina</p>} />
      <Route path="/routines/:id" element={<p>Editar rotina</p>} />
    </Routes>
  )
}

describe('RoutinesListPage', () => {
  it('lists routines with their exercise count', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    expect(await screen.findByText('Treino A')).toBeInTheDocument()
    expect(screen.getByText('1 exercícios')).toBeInTheDocument()
  })

  it('navigates to the builder when "Nova rotina" is clicked', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    await screen.findByText('Treino A')
    await userEvent.click(screen.getByRole('link', { name: 'Nova rotina' }))
    await waitFor(() => expect(screen.getByText('Construtor de rotina')).toBeInTheDocument())
  })

  it('deletes a routine after confirmation, without a page reload', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<RoutinesUnderTest />, { route: '/routines' })
    await screen.findByText('Treino A')

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))
    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))

    await waitFor(() => expect(screen.queryByText('Treino A')).not.toBeInTheDocument())
  })
})
