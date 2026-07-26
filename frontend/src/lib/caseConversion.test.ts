import { describe, expect, it } from 'vitest'
import { toCamelCase, toSnakeCase } from './caseConversion'

describe('toSnakeCase', () => {
  it('converts flat camelCase keys to snake_case', () => {
    expect(toSnakeCase({ weightKg: 80, plannedSets: 3 })).toEqual({ weight_kg: 80, planned_sets: 3 })
  })

  it('converts nested objects and arrays', () => {
    expect(
      toSnakeCase({
        name: 'Treino A',
        exercises: [{ exerciseId: 'x', plannedLoadKg: 60 }],
      }),
    ).toEqual({
      name: 'Treino A',
      exercises: [{ exercise_id: 'x', planned_load_kg: 60 }],
    })
  })

  it('leaves already-snake_case keys unchanged', () => {
    expect(toSnakeCase({ id: 'x', name: 'y' })).toEqual({ id: 'x', name: 'y' })
  })

  it('passes through null and primitives', () => {
    expect(toSnakeCase(null)).toBeNull()
    expect(toSnakeCase(42)).toBe(42)
    expect(toSnakeCase('text')).toBe('text')
  })

  it('does not mangle a Date instance', () => {
    const date = new Date('2026-01-01T00:00:00.000Z')
    const result = toSnakeCase({ createdAt: date }) as any
    expect(result.created_at).toBeInstanceOf(Date)
    expect(result.created_at.toISOString()).toBe('2026-01-01T00:00:00.000Z')
  })
})

describe('toCamelCase', () => {
  it('converts flat snake_case keys to camelCase', () => {
    expect(toCamelCase({ weight_kg: 80, planned_sets: 3 })).toEqual({ weightKg: 80, plannedSets: 3 })
  })

  it('converts nested objects and arrays', () => {
    expect(
      toCamelCase({
        exercises: [{ exercise_id: 'x', planned_load_kg: 60 }],
      }),
    ).toEqual({
      exercises: [{ exerciseId: 'x', plannedLoadKg: 60 }],
    })
  })
})

describe('round trip', () => {
  it('camelCase -> snake_case -> camelCase returns the original shape', () => {
    const original = { weightKg: 80, plannedSets: 3, exercises: [{ exerciseId: 'x' }] }
    expect(toCamelCase(toSnakeCase(original))).toEqual(original)
  })
})
