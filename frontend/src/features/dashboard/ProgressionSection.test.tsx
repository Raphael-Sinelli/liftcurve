import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { ProgressionSection } from './ProgressionSection'

describe('ProgressionSection', () => {
  it('shows the plateau banner and empty state for the default (plateaued, dataless) exercise', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<ProgressionSection />)
    expect(await screen.findByText(/Platô ativo/)).toBeInTheDocument()
    expect(screen.getByText('Nenhum dado registrado ainda pra esse exercício.')).toBeInTheDocument()
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
})
