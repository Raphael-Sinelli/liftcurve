import { apiClient } from '../lib/apiClient'

export interface RoutineSummary {
  id: string
  name: string
  description: string | null
  createdAt: string
  exerciseCount: number
}

export interface RoutineExerciseEntry {
  exerciseId: string
  exerciseName: string
  orderIndex: number
  plannedSets: number
  plannedReps: number
  plannedLoadKg: number | null
}

export interface Routine {
  id: string
  name: string
  description: string | null
  createdAt: string
  exercises: RoutineExerciseEntry[]
}

export interface RoutineExerciseInput {
  exerciseId: string
  plannedSets: number
  plannedReps: number
  plannedLoadKg: number | null
}

export interface RoutineInput {
  name: string
  description: string | null
  exercises: RoutineExerciseInput[]
}

export async function listRoutines(): Promise<RoutineSummary[]> {
  const response = await apiClient.get<RoutineSummary[]>('/routines')
  return response.data
}

export async function getRoutine(id: string): Promise<Routine> {
  const response = await apiClient.get<Routine>(`/routines/${id}`)
  return response.data
}

export async function createRoutine(input: RoutineInput): Promise<Routine> {
  const response = await apiClient.post<Routine>('/routines', input)
  return response.data
}

export async function updateRoutine(id: string, input: RoutineInput): Promise<Routine> {
  const response = await apiClient.put<Routine>(`/routines/${id}`, input)
  return response.data
}

export async function deleteRoutine(id: string): Promise<void> {
  await apiClient.delete(`/routines/${id}`)
}
