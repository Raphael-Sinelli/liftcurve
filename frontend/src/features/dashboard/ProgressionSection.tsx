import { useState } from 'react'
import { Select } from '../../components/Select'
import { formatShortDate } from '../../lib/formatDate'
import { useExercises } from '../exercises/useExercisesQueries'
import { ProgressionChart, type ProgressionChartPoint } from './ProgressionChart'
import { usePlateaus, useProgression } from './useDashboardQueries'

export function ProgressionSection() {
  const { data: exercises, isLoading: isLoadingExercises } = useExercises()
  const sortedExercises = [...(exercises ?? [])].sort((a, b) => a.name.localeCompare(b.name))
  const [selectedExerciseId, setSelectedExerciseId] = useState('')
  const effectiveExerciseId = selectedExerciseId || sortedExercises[0]?.id || ''

  const { data: progression, isLoading: isLoadingProgression } = useProgression(effectiveExerciseId)
  const { data: plateaus } = usePlateaus()

  const isLoading = isLoadingExercises || isLoadingProgression

  const activePlateau = plateaus?.find((alert) => alert.exerciseId === effectiveExerciseId)

  const chartData: ProgressionChartPoint[] =
    progression?.points.map((point) => ({ date: formatShortDate(point.sessionStartedAt), value: point.estimated1rmBest })) ?? []

  return (
    <section>
      <div className="flex items-center justify-between">
        <h2 className="font-display text-xl font-bold text-ink">Progressão de 1RM</h2>
        <Select
          value={effectiveExerciseId}
          onValueChange={setSelectedExerciseId}
          options={sortedExercises.map((exercise) => ({ value: exercise.id, label: exercise.name }))}
        />
      </div>

      {activePlateau && (
        <p role="alert" className="mt-3 rounded-sm border border-accent bg-surface px-4 py-2 font-body text-sm text-accent">
          ⚠ Platô ativo — {activePlateau.suggestion}
        </p>
      )}

      {isLoading ? (
        <p className="mt-4 font-body text-muted">Carregando...</p>
      ) : chartData.length === 0 ? (
        <p className="mt-4 font-body text-muted">Nenhum dado registrado ainda pra esse exercício.</p>
      ) : (
        <div className="mt-4">
          <ProgressionChart data={chartData} />
        </div>
      )}
    </section>
  )
}
