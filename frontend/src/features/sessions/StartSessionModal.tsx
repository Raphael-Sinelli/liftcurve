import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Modal } from '../../components/Modal'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useRoutines } from '../routines/useRoutinesQueries'
import { useCreateSession } from './useSessionsQueries'

interface StartSessionModalProps {
  onClose: () => void
}

export function StartSessionModal({ onClose }: StartSessionModalProps) {
  const { data: routines } = useRoutines()
  const createSession = useCreateSession()
  const { showToast } = useToast()
  const navigate = useNavigate()
  const [routineId, setRoutineId] = useState('')

  async function handleStart() {
    try {
      const session = await createSession.mutateAsync({ routineId: routineId || null })
      onClose()
      navigate(`/sessions/${session.id}`)
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  return (
    <Modal open onOpenChange={(open) => !open && onClose()} title="Iniciar treino">
      <div className="flex flex-col gap-4">
        <FormField label="Rotina (opcional)" htmlFor="routineId">
          <Select
            id="routineId"
            value={routineId}
            onValueChange={setRoutineId}
            options={(routines ?? []).map((routine) => ({ value: routine.id, label: routine.name }))}
            placeholder="Nenhuma — treino livre"
          />
        </FormField>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => void handleStart()} disabled={createSession.isPending}>
            Iniciar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
