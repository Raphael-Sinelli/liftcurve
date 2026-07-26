import type { VolumeBucket } from '../../api/dashboardApi'
import type { MuscleGroup } from '../../api/muscleGroupsApi'
import { formatShortDate } from '../../lib/formatDate'

export interface PivotedWeek {
  week: string
  weekStartUtc: string
  [muscleGroupName: string]: string | number
}

export function pivotVolumeByWeek(buckets: VolumeBucket[], muscleGroups: MuscleGroup[]): PivotedWeek[] {
  const nameById = new Map(muscleGroups.map((group) => [group.id, group.name]))
  const weeks = new Map<string, PivotedWeek>()

  for (const bucket of buckets) {
    const groupName = bucket.muscleGroupId ? (nameById.get(bucket.muscleGroupId) ?? 'Outro') : 'Outro'
    const existing = weeks.get(bucket.weekStartUtc)
    if (existing) {
      existing[groupName] = ((existing[groupName] as number) ?? 0) + bucket.totalVolumeKg
    } else {
      weeks.set(bucket.weekStartUtc, {
        week: formatShortDate(bucket.weekStartUtc),
        weekStartUtc: bucket.weekStartUtc,
        [groupName]: bucket.totalVolumeKg,
      })
    }
  }

  return Array.from(weeks.values()).sort((a, b) => a.weekStartUtc.localeCompare(b.weekStartUtc))
}
