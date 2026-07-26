import { PlateStat } from '../../components/PlateStat'
import { usePlateaus } from './useDashboardQueries'

export function PlateauAlertsList() {
  const { data: plateaus, isLoading } = usePlateaus()

  if (isLoading) {
    return <p className="mt-4 font-body text-muted">Carregando...</p>
  }

  if (!plateaus || plateaus.length === 0) {
    return <p className="mt-4 font-body text-ink">Nenhum platô ativo no momento — bom trabalho!</p>
  }

  return (
    <ul className="mt-4 flex flex-col gap-2">
      {plateaus.map((alert) => (
        <li key={alert.exerciseId} className="rounded-sm border border-accent bg-surface px-4 py-3">
          <div className="flex items-center justify-between">
            <p className="font-body text-ink">{alert.exerciseName}</p>
            <PlateStat value={alert.currentMax1rm.toFixed(1)} unit="1rm" />
          </div>
          <p className="mt-1 font-body text-xs text-muted">{alert.suggestion}</p>
        </li>
      ))}
    </ul>
  )
}
