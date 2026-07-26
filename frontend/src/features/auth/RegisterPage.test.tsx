import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { RegisterPage } from './RegisterPage'

function RegisterUnderTest() {
  return (
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/exercises" element={<p>Página de exercícios</p>} />
    </Routes>
  )
}

describe('RegisterPage', () => {
  it('shows a validation message for a short password', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'raphael@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    expect(await screen.findByText('A senha precisa ter pelo menos 8 caracteres.')).toBeInTheDocument()
  })

  it('shows a friendly message when the email is already registered', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'taken@gymtracker.app')
    await userEvent.type(screen.getByLabelText('Senha'), 'longenoughpassword')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    expect(await screen.findByText('Este email já está cadastrado.')).toBeInTheDocument()
  })

  it('navigates to /exercises after a successful registration (auto-login)', async () => {
    renderWithProviders(<RegisterUnderTest />, { route: '/register' })
    await userEvent.type(screen.getByLabelText('Nome'), 'Raphael')
    await userEvent.type(screen.getByLabelText('Email'), 'new@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'longenoughpassword')
    await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }))
    await waitFor(() => expect(screen.getByText('Página de exercícios')).toBeInTheDocument())
  })
})
