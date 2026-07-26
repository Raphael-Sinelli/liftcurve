import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { ExercisesListPage } from './ExercisesListPage'

describe('ExercisesListPage', () => {
  it('lists global and custom exercises, marking the global one as catalog-only', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ExercisesListPage />)
    expect(await screen.findByText('Supino Reto')).toBeInTheDocument()
    expect(screen.getByText('Supino Inclinado Halteres')).toBeInTheDocument()
    const globalRow = screen.getByText('Supino Reto').closest('li') as HTMLElement
    expect(within(globalRow).queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument()
    const customRow = screen.getByText('Supino Inclinado Halteres').closest('li') as HTMLElement
    expect(within(customRow).getByRole('button', { name: 'Excluir' })).toBeInTheDocument()
  })

  it('creates a new exercise and shows it in the list without a page reload', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ExercisesListPage />)
    await screen.findByText('Supino Reto')

    await userEvent.click(screen.getByRole('button', { name: 'Novo exercício' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Rosca Direta')
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByText('Peito'))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(screen.getByText('Rosca Direta')).toBeInTheDocument())
  })

  it('shows the EXERCISE_IN_USE message when deletion is blocked', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(
      http.get('/exercises', () =>
        HttpResponse.json([
          { id: 'ex-in-use', name: 'Agachamento Livre', muscle_group_id: 'mg-legs', muscle_group_name: 'Pernas', owner_id: 'demo-user-id', created_at: '2026-01-01T00:00:00Z' },
        ]),
      ),
    )
    renderWithProviders(<ExercisesListPage />)
    await screen.findByText('Agachamento Livre')

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))
    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByText('Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.')).toBeInTheDocument()
  })
})
