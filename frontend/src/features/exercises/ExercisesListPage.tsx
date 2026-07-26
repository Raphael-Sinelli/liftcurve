import { useMemo, useState } from 'react'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import type { Exercise } from '../../api/exercisesApi'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { ExerciseFormModal } from './ExerciseFormModal'
import { useDeleteExercise, useExercises } from './useExercisesQueries'

export function ExercisesListPage() {
  const { data: exercises, isLoading } = useExercises()
  const deleteExercise = useDeleteExercise()
  const { showToast } = useToast()
  const [modalState, setModalState] = useState<{ mode: 'create' } | { mode: 'edit'; exercise: Exercise } | null>(null)
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  const sorted = useMemo(() => [...(exercises ?? [])].sort((a, b) => a.name.localeCompare(b.name)), [exercises])

  async function handleConfirmDelete() {
    if (!pendingDeleteId) return
    try {
      await deleteExercise.mutateAsync(pendingDeleteId)
      showToast('Exercício excluído.')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setPendingDeleteId(null)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando exercícios...</p>
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-2xl font-bold text-ink">Exercícios</h1>
        <Button onClick={() => setModalState({ mode: 'create' })}>Novo exercício</Button>
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {sorted.map((exercise) => (
          <li key={exercise.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{exercise.name}</p>
              <p className="font-body text-xs text-muted">
                {exercise.muscleGroupName}
                {exercise.ownerId === null && ' · Catálogo'}
              </p>
            </div>
            {exercise.ownerId !== null && (
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => setModalState({ mode: 'edit', exercise })}>
                  Editar
                </Button>
                <Button variant="destructive" onClick={() => setPendingDeleteId(exercise.id)}>
                  Excluir
                </Button>
              </div>
            )}
          </li>
        ))}
      </ul>

      {modalState && (
        <ExerciseFormModal
          initialExercise={modalState.mode === 'edit' ? modalState.exercise : null}
          onClose={() => setModalState(null)}
        />
      )}

      <Modal open={pendingDeleteId !== null} onOpenChange={(open) => !open && setPendingDeleteId(null)} title="Excluir exercício">
        <p className="font-body text-sm text-ink">Tem certeza que quer excluir este exercício?</p>
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
