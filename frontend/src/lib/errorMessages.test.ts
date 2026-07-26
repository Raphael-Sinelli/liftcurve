import type { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { extractApiError, getErrorMessage } from './errorMessages'

describe('getErrorMessage', () => {
  it('returns the mapped Portuguese message for a known code', () => {
    expect(getErrorMessage('EXERCISE_IN_USE')).toBe(
      'Este exercício está em uso em uma rotina ou sessão e não pode ser excluído.',
    )
  })

  it('returns the fallback message for an unknown code', () => {
    expect(getErrorMessage('SOME_UNMAPPED_CODE')).toBe('Algo deu errado. Tente novamente.')
  })

  it('maps SESSION_ALREADY_ACTIVE', () => {
    expect(getErrorMessage('SESSION_ALREADY_ACTIVE')).toBe('Você já tem um treino em andamento.')
  })

  it('maps SESSION_ALREADY_FINISHED', () => {
    expect(getErrorMessage('SESSION_ALREADY_FINISHED')).toBe('Este treino já foi finalizado.')
  })

  it('maps SESSION_NOT_FOUND', () => {
    expect(getErrorMessage('SESSION_NOT_FOUND')).toBe('Sessão não encontrada.')
  })

  it('maps INVALID_ROUTINE_REFERENCE', () => {
    expect(getErrorMessage('INVALID_ROUTINE_REFERENCE')).toBe('A rotina selecionada não é válida.')
  })
})

describe('extractApiError', () => {
  it('extracts the error body from an Axios-error-shaped object', () => {
    const err = {
      response: {
        data: {
          error: { code: 'VALIDATION_ERROR', message: 'Dados inválidos.', status: 400, details: [{ field: 'email', message: 'must not be blank' }] },
        },
      },
    } as unknown as AxiosError<{ error: { code: string; message: string; status: number; details: { field: string; message: string }[] } }>

    expect(extractApiError(err)).toEqual({
      code: 'VALIDATION_ERROR',
      message: 'Dados inválidos.',
      status: 400,
      details: [{ field: 'email', message: 'must not be blank' }],
    })
  })

  it('defaults details to an empty array when absent', () => {
    const err = {
      response: { data: { error: { code: 'EXERCISE_NOT_FOUND', message: 'Não encontrado.', status: 404 } } },
    }
    expect(extractApiError(err)).toEqual({ code: 'EXERCISE_NOT_FOUND', message: 'Não encontrado.', status: 404, details: [] })
  })

  it('returns null when the error has no response body', () => {
    expect(extractApiError(new Error('network error'))).toBeNull()
  })

  it('returns null when the response body has no error.code', () => {
    expect(extractApiError({ response: { data: {} } })).toBeNull()
  })
})
