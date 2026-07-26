import type { AxiosError } from 'axios'

export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  status: number
  details: ApiFieldError[]
}

const ERROR_MESSAGES: Record<string, string> = {
  EMAIL_ALREADY_REGISTERED: 'Este email já está cadastrado.',
  INVALID_CREDENTIALS: 'Email ou senha inválidos.',
  INVALID_REFRESH_TOKEN: 'Sua sessão expirou. Faça login novamente.',
  UNAUTHORIZED: 'Você precisa entrar para continuar.',
  INVALID_TOKEN: 'Sua sessão expirou. Faça login novamente.',
  MUSCLE_GROUP_NOT_FOUND: 'Grupo muscular não encontrado.',
  EXERCISE_NOT_FOUND: 'Exercício não encontrado.',
  NOT_EXERCISE_OWNER: 'Este exercício é do catálogo global e não pode ser editado.',
  EXERCISE_IN_USE: 'Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.',
  INVALID_EXERCISE_REFERENCE: 'Um dos exercícios selecionados não é válido.',
  ROUTINE_NOT_FOUND: 'Rotina não encontrada.',
  SESSION_ALREADY_ACTIVE: 'Você já tem um treino em andamento.',
  SESSION_ALREADY_FINISHED: 'Este treino já foi finalizado.',
  SESSION_NOT_FOUND: 'Sessão não encontrada.',
  INVALID_ROUTINE_REFERENCE: 'A rotina selecionada não é válida.',
  VALIDATION_ERROR: 'Verifique os campos preenchidos.',
}

const FALLBACK_MESSAGE = 'Algo deu errado. Tente novamente.'

export function getErrorMessage(code: string): string {
  return ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE
}

export function extractApiError(err: unknown): ApiErrorBody | null {
  const axiosErr = err as AxiosError<{ error?: ApiErrorBody }> | undefined
  const body = axiosErr?.response?.data?.error
  if (!body || typeof body.code !== 'string') {
    return null
  }
  return {
    code: body.code,
    message: body.message,
    status: body.status,
    details: body.details ?? [],
  }
}
