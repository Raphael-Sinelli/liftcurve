import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRoutine,
  deleteRoutine,
  getRoutine,
  listRoutines,
  updateRoutine,
  type Routine,
  type RoutineInput,
  type RoutineSummary,
} from '../../api/routinesApi'

const ROUTINES_KEY = ['routines'] as const
const routineKey = (id: string) => ['routines', id] as const

function toSummary(routine: Routine): RoutineSummary {
  return {
    id: routine.id,
    name: routine.name,
    description: routine.description,
    createdAt: routine.createdAt,
    exerciseCount: routine.exercises.length,
  }
}

export function useRoutines() {
  return useQuery({ queryKey: ROUTINES_KEY, queryFn: listRoutines })
}

export function useRoutine(id: string | undefined) {
  return useQuery({
    queryKey: routineKey(id ?? ''),
    queryFn: () => getRoutine(id as string),
    enabled: Boolean(id),
  })
}

export function useCreateRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RoutineInput) => createRoutine(input),
    onSuccess: (created) => {
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) => [...(current ?? []), toSummary(created)])
      queryClient.setQueryData(routineKey(created.id), created)
    },
  })
}

export function useUpdateRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: RoutineInput }) => updateRoutine(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) =>
        (current ?? []).map((routine) => (routine.id === updated.id ? toSummary(updated) : routine)),
      )
      queryClient.setQueryData(routineKey(updated.id), updated)
    },
  })
}

export function useDeleteRoutine() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteRoutine,
    onMutate: async (id: string) => {
      await queryClient.cancelQueries({ queryKey: ROUTINES_KEY })
      const previous = queryClient.getQueryData<RoutineSummary[]>(ROUTINES_KEY)
      queryClient.setQueryData<RoutineSummary[]>(ROUTINES_KEY, (current) => (current ?? []).filter((routine) => routine.id !== id))
      return { previous }
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(ROUTINES_KEY, context.previous)
      }
    },
    onSuccess: (_data, id) => {
      // Deletion is confirmed server-side — safe to definitively drop the detail cache entry now
      // (unlike the list, which is updated optimistically in onMutate).
      queryClient.removeQueries({ queryKey: routineKey(id) })
    },
  })
}
