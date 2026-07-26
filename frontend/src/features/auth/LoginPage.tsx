import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { useAuth } from '../../context/AuthContext'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'

const loginSchema = z.object({
  email: z.string().min(1, 'Informe o email.'),
  password: z.string().min(1, 'Informe a senha.'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const { login, loginAsDemo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/exercises'

  async function attemptLogin(action: () => Promise<void>) {
    setFormError(null)
    setIsSubmitting(true)
    try {
      await action()
      navigate(redirectTo, { replace: true })
    } catch (err) {
      const apiError = extractApiError(err)
      setFormError(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function onSubmit(values: LoginFormValues) {
    await attemptLogin(() => login(values.email, values.password))
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm rounded-sm border border-line bg-surface p-8">
        <h1 className="font-display text-3xl font-bold text-ink">Gym Progress Tracker</h1>
        <p className="mt-1 font-body text-sm text-muted">Entre para ver sua progressão.</p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <FormField label="Email" htmlFor="email" error={errors.email?.message}>
            <Input id="email" type="email" hasError={!!errors.email} {...register('email')} />
          </FormField>
          <FormField label="Senha" htmlFor="password" error={errors.password?.message}>
            <Input id="password" type="password" hasError={!!errors.password} {...register('password')} />
          </FormField>

          {formError && (
            <p role="alert" className="font-body text-sm text-accent">
              {formError}
            </p>
          )}

          <Button type="submit" disabled={isSubmitting}>
            Entrar
          </Button>
        </form>

        <Button
          type="button"
          variant="secondary"
          className="mt-3 w-full"
          onClick={() => void attemptLogin(loginAsDemo)}
          disabled={isSubmitting}
        >
          Entrar como visitante (conta demo)
        </Button>

        <p className="mt-6 font-body text-sm text-muted">
          Não tem conta?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Cadastre-se
          </Link>
        </p>
      </div>
    </main>
  )
}
