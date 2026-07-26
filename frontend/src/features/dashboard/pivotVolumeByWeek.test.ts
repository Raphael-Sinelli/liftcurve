import { describe, expect, it } from 'vitest'
import { pivotVolumeByWeek } from './pivotVolumeByWeek'

const MUSCLE_GROUPS = [
  { id: 'mg-chest', name: 'Peito' },
  { id: 'mg-back', name: 'Costas' },
]

describe('pivotVolumeByWeek', () => {
  it('groups buckets by week, one column per muscle group name', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 },
        { muscleGroupId: 'mg-back', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 800 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result).toHaveLength(1)
    expect(result[0].Peito).toBe(1000)
    expect(result[0].Costas).toBe(800)
  })

  it('sorts weeks chronologically regardless of input order', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-13T00:00:00Z', totalVolumeKg: 500 },
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result.map((week) => week.weekStartUtc)).toEqual(['2026-07-06T00:00:00Z', '2026-07-13T00:00:00Z'])
  })

  it('omits the key for a group with no data that week, rather than defaulting to 0', () => {
    const result = pivotVolumeByWeek(
      [{ muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 1000 }],
      MUSCLE_GROUPS,
    )
    expect(result[0].Costas).toBeUndefined()
  })

  it('falls back to "Outro" for a null or unrecognized muscle group id', () => {
    const result = pivotVolumeByWeek(
      [{ muscleGroupId: null, weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 300 }],
      MUSCLE_GROUPS,
    )
    expect(result[0].Outro).toBe(300)
  })

  it('sums multiple buckets for the same group and week', () => {
    const result = pivotVolumeByWeek(
      [
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 500 },
        { muscleGroupId: 'mg-chest', weekStartUtc: '2026-07-06T00:00:00Z', totalVolumeKg: 300 },
      ],
      MUSCLE_GROUPS,
    )
    expect(result[0].Peito).toBe(800)
  })

  it('returns an empty array for an empty input', () => {
    expect(pivotVolumeByWeek([], MUSCLE_GROUPS)).toEqual([])
  })
})
