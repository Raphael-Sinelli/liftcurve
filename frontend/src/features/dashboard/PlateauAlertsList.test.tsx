import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/mocks/server'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setAccessToken } from '../../lib/tokenStore'
import { VALID_ACCESS_TOKEN } from '../../test/mocks/handlers'
import { PlateauAlertsList } from './PlateauAlertsList'

describe('PlateauAlertsList', () => {
  it('lists active plateau alerts with the suggestion text', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    renderWithProviders(<PlateauAlertsList />)
    expect(await screen.findByText('Supino Inclinado Halteres')).toBeInTheDocument()
    expect(screen.getByText('Considere reduzir a carga em ~10% por 1 semana (deload).')).toBeInTheDocument()
  })

  it('shows a positive empty state when there are no active plateaus', async () => {
    setAccessToken(VALID_ACCESS_TOKEN)
    server.use(http.get('/dashboard/plateaus', () => HttpResponse.json([])))
    renderWithProviders(<PlateauAlertsList />)
    expect(await screen.findByText('Nenhum platô ativo no momento — bom trabalho!')).toBeInTheDocument()
  })
})
