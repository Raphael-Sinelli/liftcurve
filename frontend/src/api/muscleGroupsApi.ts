import { apiClient } from '../lib/apiClient'

export interface MuscleGroup {
  id: string
  name: string
}

export async function listMuscleGroups(): Promise<MuscleGroup[]> {
  const response = await apiClient.get<MuscleGroup[]>('/muscle-groups')
  return response.data
}
