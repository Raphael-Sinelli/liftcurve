import { describe, expect, it } from 'vitest'
import { formatDateTime, formatShortDate } from './formatDate'

describe('formatDateTime', () => {
  it('returns a non-empty string containing the year', () => {
    const result = formatDateTime('2026-07-01T12:00:00Z')
    expect(result).toContain('2026')
  })

  it('does not throw for a valid ISO string and returns distinct output for distinct instants', () => {
    const first = formatDateTime('2026-01-15T08:30:00Z')
    const second = formatDateTime('2026-06-20T18:45:00Z')
    expect(first.length).toBeGreaterThan(0)
    expect(second.length).toBeGreaterThan(0)
    expect(first).not.toBe(second)
  })
})

describe('formatShortDate', () => {
  it('returns a short label without the full 4-digit year', () => {
    const result = formatShortDate('2026-07-06T00:00:00Z')
    expect(result).not.toContain('2026')
    expect(result.length).toBeGreaterThan(0)
  })

  it('produces distinct labels for dates in different months', () => {
    const january = formatShortDate('2026-01-06T00:00:00Z')
    const july = formatShortDate('2026-07-06T00:00:00Z')
    expect(january).not.toBe(july)
  })
})
