import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { Modal } from '../../components/Modal'
import { PlateStat } from '../../components/PlateStat'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { formatDateTime } from '../../lib/formatDate'
import { useExercises } from '../exercises/useExercisesQueries'
import { useAddSet, useFinishSession, useSession } from './useSessionsQueries'

const addSetSchema = z.object({
  exerciseId: z.string().min(1, 'Selecione um exercício.'),
  weightKg: z.coerce.number().min(0, 'Não pode ser negativo.'),
  reps: z.coerce.number().int().min(1, 'Mínimo 1.'),
  rpe: z.string(),
})

type AddSetFormValues = z.infer<typeof addSetSchema>

function toNullableRpe(raw: string): number | null {
  const trimmed = raw.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: session, isLoading, isError } = useSession(id)
  const { data: exercises } = useExercises()
  const addSet = useAddSet()
  const finishSession = useFinishSession()
  const [showFinishModal, setShowFinishModal] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof addSetSchema>, unknown, AddSetFormValues>({
    resolver: zodResolver(addSetSchema),
    defaultValues: { exerciseId: '', weightKg: 0, reps: 8, rpe: '' },
  })

  async function onAddSet(values: AddSetFormValues) {
    if (!id) return
    try {
      await addSet.mutateAsync({
        sessionId: id,
        input: {
          exerciseId: values.exerciseId,
          weightKg: values.weightKg,
          reps: values.reps,
          rpe: toNullableRpe(values.rpe),
        },
      })
      reset({ exerciseId: values.exerciseId, weightKg: values.weightKg, reps: values.reps, rpe: '' })
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  async function handleFinish(notes: string) {
    if (!id) return
    try {
      await finishSession.mutateAsync({ id, input: { notes: notes.trim() === '' ? null : notes } })
      showToast('Treino finalizado.')
      navigate('/sessions')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    } finally {
      setShowFinishModal(false)
    }
  }

  if (isLoading) {
    return <p className="font-body text-muted">Carregando sessão...</p>
  }

  if (isError || !session) {
    return (
      <div>
        <p className="font-body text-muted">Sessão não encontrada.</p>
        <Link to="/sessions" className="mt-2 inline-block text-accent hover:underline">
          Voltar pra sessões
        </Link>
      </div>
    )
  }

  const isActive = session.finishedAt === null
  const exerciseOptions = (exercises ?? []).map((exercise) => ({ value: exercise.id, label: exercise.name }))

  return (
    <div>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">
            {isActive ? 'Treino em andamento' : 'Treino finalizado'}
          </h1>
          <p className="font-body text-xs text-muted">{formatDateTime(session.startedAt)}</p>
        </div>
        {isActive && (
          <Button variant="secondary" onClick={() => setShowFinishModal(true)}>
            Finalizar treino
          </Button>
        )}
      </div>

      <ul className="mt-6 flex flex-col gap-2">
        {session.sets.map((set) => (
          <li key={set.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3">
            <div>
              <p className="font-body text-ink">{set.exerciseName}</p>
              <p className="font-body text-xs text-muted">
                Série {set.setNumber}
                {set.rpe !== null && ` · RPE ${set.rpe}`}
              </p>
            </div>
            <div className="flex gap-2">
              <PlateStat value={set.weightKg} unit="kg" />
              <PlateStat value={set.reps} unit="reps" />
              <PlateStat value={set.estimated1rmBest.toFixed(1)} unit="1rm" />
            </div>
          </li>
        ))}
      </ul>

      {isActive && (
        <form
          className="mt-6 flex flex-col gap-4 rounded-sm border border-line bg-surface p-4"
          onSubmit={handleSubmit(onAddSet)}
          noValidate
        >
          <p className="font-body text-xs font-semibold uppercase tracking-wide text-muted">Adicionar série</p>
          <FormField label="Exercício" htmlFor="exerciseId" error={errors.exerciseId?.message}>
            <Select
              id="exerciseId"
              value={watch('exerciseId')}
              onValueChange={(value) => setValue('exerciseId', value, { shouldValidate: true })}
              options={exerciseOptions}
              hasError={!!errors.exerciseId}
            />
          </FormField>
          <div className="flex gap-3">
            <FormField label="Peso (kg)" htmlFor="weightKg" error={errors.weightKg?.message}>
              <Input id="weightKg" type="number" step="0.01" hasError={!!errors.weightKg} {...register('weightKg')} />
            </FormField>
            <FormField label="Reps" htmlFor="reps" error={errors.reps?.message}>
              <Input id="reps" type="number" hasError={!!errors.reps} {...register('reps')} />
            </FormField>
            <FormField label="RPE (opcional)" htmlFor="rpe" error={errors.rpe?.message}>
              <Input id="rpe" type="number" step="0.5" hasError={!!errors.rpe} {...register('rpe')} />
            </FormField>
          </div>
          <Button type="submit" disabled={isSubmitting}>
            Adicionar série
          </Button>
        </form>
      )}

      {session.notes && <p className="mt-6 font-body text-sm text-muted">Notas: {session.notes}</p>}

      <Modal open={showFinishModal} onOpenChange={(open) => !open && setShowFinishModal(false)} title="Finalizar treino">
        <FinishSessionForm
          onConfirm={(notes) => void handleFinish(notes)}
          onCancel={() => setShowFinishModal(false)}
          isSubmitting={finishSession.isPending}
        />
      </Modal>
    </div>
  )
}

interface FinishSessionFormProps {
  onConfirm: (notes: string) => void
  onCancel: () => void
  isSubmitting: boolean
}

function FinishSessionForm({ onConfirm, onCancel, isSubmitting }: FinishSessionFormProps) {
  const [notes, setNotes] = useState('')
  return (
    <div className="flex flex-col gap-4">
      <FormField label="Notas (opcional)" htmlFor="finish-notes">
        <Input id="finish-notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
      </FormField>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button onClick={() => onConfirm(notes)} disabled={isSubmitting}>
          Finalizar
        </Button>
      </div>
    </div>
  )
}
