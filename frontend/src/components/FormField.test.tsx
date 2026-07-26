import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FormField } from './FormField'

describe('FormField', () => {
  it('renders the label and children', () => {
    render(
      <FormField label="Nome" htmlFor="name">
        <input id="name" />
      </FormField>,
    )
    expect(screen.getByText('Nome')).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('renders an alert with the error message when error is set', () => {
    render(
      <FormField label="Nome" htmlFor="name" error="Informe o nome.">
        <input id="name" />
      </FormField>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Informe o nome.')
  })

  it('renders no alert when error is absent', () => {
    render(
      <FormField label="Nome" htmlFor="name">
        <input id="name" />
      </FormField>,
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
