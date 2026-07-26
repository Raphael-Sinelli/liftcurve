import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { queryClient } from './lib/queryClient'

describe('App', () => {
  it('renders the placeholder heading', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>,
    )
    expect(screen.getByRole('heading', { name: 'gym-progress-tracker' })).toBeInTheDocument()
  })
})
