import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { DEMO_EMAIL, DEMO_PASSWORD } from '../../test/mocks/handlers'
import { LoginPage } from './LoginPage'

function LoginUnderTest() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/dashboard" element={<p>Página de dashboard</p>} />
      <Route path="/register" element={<p>Página de registro</p>} />
    </Routes>
  )
}

describe('LoginPage', () => {
  it('shows a validation message when submitting empty fields', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Informe o email.')).toBeInTheDocument()
  })

  it('shows a friendly message on invalid credentials', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), 'wrong@x.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Email ou senha inválidos.')).toBeInTheDocument()
  })

  it('navigates to /exercises after a successful login', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.type(screen.getByLabelText('Email'), DEMO_EMAIL)
    await userEvent.type(screen.getByLabelText('Senha'), DEMO_PASSWORD)
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() => expect(screen.getByText('Página de dashboard')).toBeInTheDocument())
  })

  it('the demo-login button logs in with the documented demo credentials', async () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    await userEvent.click(screen.getByRole('button', { name: 'Entrar como visitante (conta demo)' }))
    await waitFor(() => expect(screen.getByText('Página de dashboard')).toBeInTheDocument())
  })

  it('links to the register page', () => {
    renderWithProviders(<LoginUnderTest />, { route: '/login' })
    expect(screen.getByRole('link', { name: 'Cadastre-se' })).toHaveAttribute('href', '/register')
  })
})
