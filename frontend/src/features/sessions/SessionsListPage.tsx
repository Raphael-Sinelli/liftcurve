import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { PlateStat } from '../../components/PlateStat'
import { formatDateTime } from '../../lib/formatDate'
import { useRoutines } from '../routines/useRoutinesQueries'
import { StartSessionModal } from './StartSessionModal'
import { useSessions } from './useSessionsQueries'

export function SessionsListPage() {
  const { data: sessions, isLoading } = useSessions()
  const { data: routines } = useRoutines()
  const navigate = useNavigate()
  const [showStartModal, setShowStartModal] = useState(false)

  const sorted = useMemo(
    () => [...(sessions ?? [])].sort((a, b) => b.startedAt.localeCompare(a.startedAt)),
    [sessions],
  )
  const activeSession = sorted.find((session) => session.finishedAt === null)

  function routineName(routineId: string | null): string {
    if (!routineId) return 'Treino livre'
    return routines?.find((routine) => routine.id === routineId)?.name ?? 'Treino livre'
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando sessões...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Sessões</h1>
        {activeSession ? (
          <Button onClick={() => navigate(`/sessions/${activeSession.id}`)}>Continuar treino ativo</Button>
        ) : (
          <Button onClick={() => setShowStartModal(true)}>Iniciar treino</Button>
        )}
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {sorted.map((session) => (
          <li
            key={session.id}
            className="flex cursor-pointer items-center justify-between rounded-sm border border-line bg-surface px-4 py-3 hover:border-accent"
            onClick={() => navigate(`/sessions/${session.id}`)}
          >
            <div>
              <p className="font-body text-ink">{formatDateTime(session.startedAt)}</p>
              <p className="font-body text-xs text-muted">
                {routineName(session.routineId)}
                {session.finishedAt === null && ' · Ativa'}
              </p>
            </div>
            <PlateStat value={session.setCount} unit="séries" />
          </li>
        ))}
      </ul>

      {showStartModal && <StartSessionModal onClose={() => setShowStartModal(false)} />}
    </div>
  )
}
