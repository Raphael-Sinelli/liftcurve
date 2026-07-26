import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { Modal } from '../../components/Modal'
import { Select } from '../../components/Select'
import { useToast } from '../../components/Toast'
import type { Exercise } from '../../api/exercisesApi'
import { extractApiError, getErrorMessage } from '../../lib/errorMessages'
import { useCreateExercise, useMuscleGroups, useUpdateExercise } from './useExercisesQueries'

const exerciseSchema = z.object({
  name: z.string().min(1, 'Informe o nome.').max(120, 'Nome muito longo.'),
  muscleGroupId: z.string().min(1, 'Selecione um grupo muscular.'),
})

type ExerciseFormValues = z.infer<typeof exerciseSchema>

interface ExerciseFormModalProps {
  initialExercise: Exercise | null
  onClose: () => void
}

export function ExerciseFormModal({ initialExercise, onClose }: ExerciseFormModalProps) {
  const { data: muscleGroups } = useMuscleGroups()
  const createExercise = useCreateExercise()
  const updateExercise = useUpdateExercise()
  const { showToast } = useToast()

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ExerciseFormValues>({
    resolver: zodResolver(exerciseSchema),
    defaultValues: {
      name: initialExercise?.name ?? '',
      muscleGroupId: initialExercise?.muscleGroupId ?? '',
    },
  })

  async function onSubmit(values: ExerciseFormValues) {
    try {
      if (initialExercise) {
        await updateExercise.mutateAsync({ id: initialExercise.id, input: values })
        showToast('Exercício atualizado.')
      } else {
        await createExercise.mutateAsync(values)
        showToast('Exercício criado.')
      }
      onClose()
    } catch (err) {
      const apiError = extractApiError(err)
      showToast(apiError ? getErrorMessage(apiError.code) : getErrorMessage('UNKNOWN'), 'error')
    }
  }

  return (
    <Modal open onOpenChange={(open) => !open && onClose()} title={initialExercise ? 'Editar exercício' : 'Novo exercício'}>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Nome" htmlFor="name" error={errors.name?.message}>
          <Input id="name" hasError={!!errors.name} {...register('name')} />
        </FormField>
        <FormField label="Grupo muscular" htmlFor="muscleGroupId" error={errors.muscleGroupId?.message}>
          <Select
            id="muscleGroupId"
            value={watch('muscleGroupId')}
            onValueChange={(value) => setValue('muscleGroupId', value, { shouldValidate: true })}
            options={(muscleGroups ?? []).map((group) => ({ value: group.id, label: group.name }))}
            hasError={!!errors.muscleGroupId}
          />
        </FormField>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            Salvar
          </Button>
        </div>
      </form>
    </Modal>
  )
}
