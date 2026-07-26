import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { VolumeSection } from './VolumeSection'

describe('VolumeSection', () => {
  it('renders without the empty state when volume data exists', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<VolumeSection />)
    await screen.findByText('Volume semanal por grupo muscular')
    expect(screen.queryByText('Nenhum volume registrado ainda.')).not.toBeInTheDocument()
  })

  it('shows an empty state when there is no volume data', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/dashboard/volume', () => HttpResponse.json([])))
    renderWithProviders(<VolumeSection />)
    expect(await screen.findByText('Nenhum volume registrado ainda.')).toBeInTheDocument()
  })
})
