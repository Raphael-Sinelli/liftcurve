import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { useAuth } from '../../context/AuthContext'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'

const registerSchema = z.object({
  name: z.string().min(1, 'Informe seu nome.').max(120, 'Nome muito longo.'),
  email: z.string().email('Email inválido.'),
  password: z.string().min(8, 'A senha precisa ter pelo menos 8 caracteres.').max(72, 'Senha muito longa.'),
})

type RegisterFormValues = z.infer<typeof registerSchema>

export function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null)
    try {
      await registerUser(values.email, values.password, values.name)
      navigate('/exercises', { replace: true })
    } catch (err) {
      const apiError = extractApiError(err)
      setFormError(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'))
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-4">
      <div className="w-full max-w-sm rounded-sm border border-line bg-surface p-8">
        <h1 className="font-display text-3xl font-bold text-ink">Criar conta</h1>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
            <Input id="name" hasError={!!errors.name} {...register('name')} />
          </FormField>
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
            Cadastrar
          </Button>
        </form>
      </div>
    </main>
  )
}
