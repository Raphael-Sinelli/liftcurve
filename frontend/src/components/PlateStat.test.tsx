import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PlateStat } from './PlateStat'

describe('PlateStat', () => {
  it('renders the value and unit', () => {
    render(<PlateStat value={80} unit="kg" />)
    expect(screen.getByText('80')).toBeInTheDocument()
    expect(screen.getByText('kg')).toBeInTheDocument()
  })

  it('renders a string value as-is (e.g. a placeholder dash)', () => {
    render(<PlateStat value="-" unit="kg" />)
    expect(screen.getByText('-')).toBeInTheDocument()
  })
})
