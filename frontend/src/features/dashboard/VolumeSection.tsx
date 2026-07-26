import { useMemo } from 'react'
import { useMuscleGroups } from '../exercises/useExercisesQueries'
import { pivotVolumeByWeek } from './pivotVolumeByWeek'
import { useVolume } from './useDashboardQueries'
import { VolumeChart } from './VolumeChart'

export function VolumeSection() {
  const { data: buckets, isLoading: isLoadingVolume } = useVolume()
  const { data: muscleGroups, isLoading: isLoadingGroups } = useMuscleGroups()

  const pivoted = useMemo(() => pivotVolumeByWeek(buckets ?? [], muscleGroups ?? []), [buckets, muscleGroups])

  const groupNames = useMemo(() => {
    const names = new Set<string>()
    pivoted.forEach((week) => {
      Object.keys(week).forEach((key) => {
        if (key !== 'week' && key !== 'weekStartUtc') names.add(key)
      })
    })
    return Array.from(names)
  }, [pivoted])

  const isLoading = isLoadingVolume || isLoadingGroups

  return (
    <section className="mt-8">
      <h2 className="font-display text-xl font-bold text-ink">Volume semanal por grupo muscular</h2>
      {isLoading ? (
        <p className="mt-4 font-body text-muted">Carregando...</p>
      ) : pivoted.length === 0 ? (
        <p className="mt-4 font-body text-muted">Nenhum volume registrado ainda.</p>
      ) : (
        <div className="mt-4">
          <VolumeChart data={pivoted} groupNames={groupNames} />
        </div>
      )}
    </section>
  )
}
