import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Input } from './Input'

describe('Input', () => {
  it('renders and accepts typed text', async () => {
    render(<Input aria-label="Nome" />)
    const input = screen.getByLabelText('Nome')
    await userEvent.type(input, 'Supino Reto')
    expect(input).toHaveValue('Supino Reto')
  })

  it('applies the error border class when hasError is true', () => {
    render(<Input aria-label="Nome" hasError />)
    expect(screen.getByLabelText('Nome')).toHaveClass('border-accent')
  })

  it('applies the default border class when hasError is false', () => {
    render(<Input aria-label="Nome" />)
    expect(screen.getByLabelText('Nome')).toHaveClass('border-line')
  })
})
