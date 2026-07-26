import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const OPTIONS = [
  { value: 'chest', label: 'Peito' },
  { value: 'back', label: 'Costas' },
]

describe('Select', () => {
  it('shows the placeholder when no value is selected', () => {
    render(<Select value={undefined} onValueChange={vi.fn()} options={OPTIONS} placeholder="Selecione" />)
    expect(screen.getByText('Selecione')).toBeInTheDocument()
  })

  it('calls onValueChange with the chosen option value', async () => {
    const onValueChange = vi.fn()
    render(<Select value={undefined} onValueChange={onValueChange} options={OPTIONS} />)
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(await screen.findByText('Costas'))
    expect(onValueChange).toHaveBeenCalledWith('back')
  })
})
