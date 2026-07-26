import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from './test/renderWithProviders'
import { App } from './App'

describe('App', () => {
  it('redirects an unauthenticated visitor at "/" to /login', async () => {
    renderWithProviders(<App />, { route: '/' })
    await waitFor(() => expect(screen.getByText('LiftCurve')).toBeInTheDocument())
  })

  it('redirects an unknown URL through the root redirect to /login when unauthenticated', async () => {
    renderWithProviders(<App />, { route: '/this-does-not-exist' })
    await waitFor(() => expect(screen.getByText('LiftCurve')).toBeInTheDocument())
  })
})
