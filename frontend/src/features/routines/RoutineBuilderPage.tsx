import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { PlateStat } from '../../components/PlateStat'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useExercises } from '../exercises/useExercisesQueries'
import { useCreateRoutine, useRoutine, useUpdateRoutine } from './useRoutinesQueries'

const routineExerciseSchema = z.object({
  exerciseId: z.string().min(1, 'Selecione um exercício.'),
  plannedSets: z.coerce.number().int().min(1, 'Mínimo 1.'),
  plannedReps: z.coerce.number().int().min(1, 'Mínimo 1.'),
  plannedLoadKg: z.string(),
})

const routineSchema = z.object({
  name: z.string().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  description: z.string().max(2000, 'Descrição muito longa.').nullable(),
  exercises: z.array(routineExerciseSchema).min(1, 'Adicione pelo menos 1 exercício.'),
})

type RoutineFormValues = z.infer<typeof routineSchema>

const EMPTY_ROW: RoutineFormValues['exercises'][number] = {
  exerciseId: '',
  plannedSets: 3,
  plannedReps: 10,
  plannedLoadKg: '',
}

function toNullableLoad(raw: string): number | null {
  const trimmed = raw.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function RoutineBuilderPage() {
  const { id } = useParams<{ id: string }>()
  const isEditing = Boolean(id)
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { data: exercises } = useExercises()
  const { data: existingRoutine, isLoading: isLoadingRoutine } = useRoutine(id)
  const createRoutine = useCreateRoutine()
  const updateRoutine = useUpdateRoutine()

  const {
    register,
    control,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<z.input<typeof routineSchema>, unknown, RoutineFormValues>({
    resolver: zodResolver(routineSchema),
    defaultValues: { name: '', description: null, exercises: [EMPTY_ROW] },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'exercises' })

  useEffect(() => {
    if (existingRoutine) {
      reset({
        name: existingRoutine.name,
        description: existingRoutine.description,
        exercises: existingRoutine.exercises
          .slice()
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map((entry) => ({
            exerciseId: entry.exerciseId,
            plannedSets: entry.plannedSets,
            plannedReps: entry.plannedReps,
            plannedLoadKg: entry.plannedLoadKg === null ? '' : String(entry.plannedLoadKg),
          })),
      })
    }
  }, [existingRoutine, reset])

  async function onSubmit(values: RoutineFormValues) {
    const payload = {
      name: values.name,
      description: values.description,
      exercises: values.exercises.map((row) => ({
        exerciseId: row.exerciseId,
        plannedSets: row.plannedSets,
        plannedReps: row.plannedReps,
        plannedLoadKg: toNullableLoad(row.plannedLoadKg),
      })),
    }
    try {
      if (isEditing && id) {
        await updateRoutine.mutateAsync({ id, input: payload })
        showToast('Rotina atualizada.')
      } else {
        await createRoutine.mutateAsync(payload)
        showToast('Rotina criada.')
      }
      navigate('/routines')
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  if (isEditing && isLoadingRoutine) {
    return <p className="font-body text-muted">Carregando rotina...</p>
  }

  const exerciseOptions = (exercises ?? []).map((exercise) => ({ value: exercise.id, label: exercise.name }))

  return (
    <div>
      <h1 className="font-display text-2xl font-bold text-ink">{isEditing ? 'Editar rotina' : 'Nova rotina'}</h1>

      <form className="mt-6 flex flex-col gap-6" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
          <Input id="name" hasError={!!errors.name} {...register('name')} />
        </FormField>
        <FormField label="Descrição" htmlFor="description" error={errors.description?.message}>
          <Input id="description" hasError={!!errors.description} {...register('description')} />
        </FormField>

        <div>
          <div className="flex items-center justify-between">
            <p className="font-body text-xs font-semibold uppercase tracking-wide text-muted">Exercícios</p>
            <Button type="button" variant="secondary" onClick={() => append(EMPTY_ROW)}>
              Adicionar exercício
            </Button>
          </div>
          {errors.exercises?.root && (
            <p role="alert" className="mt-2 font-body text-xs text-accent">
              {errors.exercises.root.message}
            </p>
          )}
          {typeof errors.exercises?.message === 'string' && (
            <p role="alert" className="mt-2 font-body text-xs text-accent">
              {errors.exercises.message}
            </p>
          )}

          <ul className="mt-3 flex flex-col gap-3">
            {fields.map((field, index) => (
              <li key={field.id} className="rounded-sm border border-line bg-surface p-4">
                <div className="flex items-start gap-3">
                  <div className="flex-1">
                    <FormField
                      label="Exercício"
                      htmlFor={`exercises.${index}.exerciseId`}
                      error={errors.exercises?.[index]?.exerciseId?.message}
                    >
                      <Select
                        id={`exercises.${index}.exerciseId`}
                        value={watch(`exercises.${index}.exerciseId`)}
                        onValueChange={(value) => setValue(`exercises.${index}.exerciseId`, value, { shouldValidate: true })}
                        options={exerciseOptions}
                        hasError={!!errors.exercises?.[index]?.exerciseId}
                      />
                    </FormField>
                  </div>
                  <Button type="button" variant="destructive" onClick={() => remove(index)}>
                    Remover
                  </Button>
                </div>

                <div className="mt-3 flex gap-3">
                  <FormField
                    label="Séries"
                    htmlFor={`exercises.${index}.plannedSets`}
                    error={errors.exercises?.[index]?.plannedSets?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedSets`}
                      type="number"
                      hasError={!!errors.exercises?.[index]?.plannedSets}
                      {...register(`exercises.${index}.plannedSets`)}
                    />
                  </FormField>
                  <FormField
                    label="Reps"
                    htmlFor={`exercises.${index}.plannedReps`}
                    error={errors.exercises?.[index]?.plannedReps?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedReps`}
                      type="number"
                      hasError={!!errors.exercises?.[index]?.plannedReps}
                      {...register(`exercises.${index}.plannedReps`)}
                    />
                  </FormField>
                  <FormField
                    label="Carga (kg)"
                    htmlFor={`exercises.${index}.plannedLoadKg`}
                    error={errors.exercises?.[index]?.plannedLoadKg?.message}
                  >
                    <Input
                      id={`exercises.${index}.plannedLoadKg`}
                      type="number"
                      step="0.01"
                      hasError={!!errors.exercises?.[index]?.plannedLoadKg}
                      {...register(`exercises.${index}.plannedLoadKg`)}
                    />
                  </FormField>
                  <div className="flex items-end pb-2">
                    <PlateStat value={watch(`exercises.${index}.plannedLoadKg`) || '-'} unit="kg" />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => navigate('/routines')}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            Salvar rotina
          </Button>
        </div>
      </form>
    </div>
  )
}
