import { screen } from '@testing-library/react'
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
  })
})
