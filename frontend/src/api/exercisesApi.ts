import { apiClient } from '../lib/apiClient'

export interface Exercise {
  id: string
  name: string
  muscleGroupId: string
  muscleGroupName: string
  ownerId: string | null
  createdAt: string
}

export interface ExerciseInput {
  name: string
  muscleGroupId: string
}

export async function listExercises(): Promise<Exercise[]> {
  const response = await apiClient.get<Exercise[]>('/exercises')
  return response.data
}

export async function createExercise(input: ExerciseInput): Promise<Exercise> {
  const response = await apiClient.post<Exercise>('/exercises', input)
  return response.data
}

export async function updateExercise(id: string, input: ExerciseInput): Promise<Exercise> {
  const response = await apiClient.put<Exercise>(`/exercises/${id}`, input)
  return response.data
}

export async function deleteExercise(id: string): Promise<void> {
  await apiClient.delete(`/exercises/${id}`)
}
