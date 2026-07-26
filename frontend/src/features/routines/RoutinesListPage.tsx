import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useDeleteRoutine, useRoutines } from './useRoutinesQueries'

export function RoutinesListPage() {
  const { data: routines, isLoading } = useRoutines()
  const deleteRoutine = useDeleteRoutine()
  const { showToast } = useToast()
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  async function handleConfirmDelete() {
    if (!pendingDeleteId) return
    try {
      await deleteRoutine.mutateAsync(pendingDeleteId)
      showToast('Rotina excluída.')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setPendingDeleteId(null)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando rotinas...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Rotinas</h1>
        <Link to="/routines/new">
          <Button>Nova rotina</Button>
        </Link>
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {(routines ?? []).map((routine) => (
          <li key={routine.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{routine.name}</p>
              <p className="font-body text-xs text-muted">{routine.exerciseCount} exercícios</p>
            </div>
            <div className="flex gap-2">
              <Link to={`/routines/${routine.id}`}>
                <Button variant="secondary">Editar</Button>
              </Link>
              <Button variant="destructive" onClick={() => setPendingDeleteId(routine.id)}>
                Excluir
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <Modal open={pendingDeleteId !== null} onOpenChange={(open) => !open && setPendingDeleteId(null)} title="Excluir rotina">
        <p className="font-body text-sm text-ink">Tem certeza que quer excluir esta rotina?</p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDeleteId(null)}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={() => void handleConfirmDelete()}>
            Excluir
          </Button>
        </div>
      </Modal>
    </div>
  )
}
