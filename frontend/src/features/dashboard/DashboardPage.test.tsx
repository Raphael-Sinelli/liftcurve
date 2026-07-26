import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  it('composes all 3 sections', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<DashboardPage />)
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Progressão de 1RM')).toBeInTheDocument()
    expect(screen.getByText('Volume semanal por grupo muscular')).toBeInTheDocument()
    expect(screen.getByText('Alertas de platô')).toBeInTheDocument()
    // Data-derived assertions: these can only pass if the plateau alert actually loaded and
    // rendered, and if every section's loading indicator actually resolved — unlike the 4
    // assertions above, which render unconditionally even when every dashboard endpoint 500s.
    expect(await screen.findByText('Supino Inclinado Halteres')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Carregando...')).not.toBeInTheDocument())
  })
})
