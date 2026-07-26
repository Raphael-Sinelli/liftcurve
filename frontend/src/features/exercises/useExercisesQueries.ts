import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createExercise,
  deleteExercise,
  listExercises,
  updateExercise,
  type Exercise,
  type ExerciseInput,
} from '../../api/exercisesApi'
import { listMuscleGroups } from '../../api/muscleGroupsApi'

const EXERCISES_KEY = ['exercises'] as const
const MUSCLE_GROUPS_KEY = ['muscle-groups'] as const

export function useExercises() {
  return useQuery({ queryKey: EXERCISES_KEY, queryFn: listExercises })
}

export function useMuscleGroups() {
  return useQuery({ queryKey: MUSCLE_GROUPS_KEY, queryFn: listMuscleGroups, staleTime: Infinity })
}

export function useCreateExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createExercise,
    onSuccess: (created) => {
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) => [...(current ?? []), created])
    },
  })
}

export function useUpdateExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: ExerciseInput }) => updateExercise(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) =>
        (current ?? []).map((exercise) => (exercise.id === updated.id ? updated : exercise)),
      )
    },
  })
}

export function useDeleteExercise() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteExercise,
    onMutate: async (id: string) => {
      await queryClient.cancelQueries({ queryKey: EXERCISES_KEY })
      const previous = queryClient.getQueryData<Exercise[]>(EXERCISES_KEY)
      queryClient.setQueryData<Exercise[]>(EXERCISES_KEY, (current) => (current ?? []).filter((exercise) => exercise.id !== id))
      return { previous }
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(EXERCISES_KEY, context.previous)
      }
    },
  })
}
