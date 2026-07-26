import { apiClient } from '../lib/apiClient'

export interface ProgressionPoint {
  sessionStartedAt: string
  estimated1rmBest: number
}

export interface Progression {
  exerciseId: string
  exerciseName: string
  points: ProgressionPoint[]
}

export interface VolumeBucket {
  muscleGroupId: string | null
  weekStartUtc: string
  totalVolumeKg: number
}

export interface PlateauAlert {
  exerciseId: string
  exerciseName: string
  currentMax1rm: number
  suggestion: string
}

export async function getProgression(exerciseId: string): Promise<Progression> {
  const response = await apiClient.get<Progression>(`/dashboard/progression/${exerciseId}`)
  return response.data
}

export async function getVolume(): Promise<VolumeBucket[]> {
  const response = await apiClient.get<VolumeBucket[]>('/dashboard/volume')
  return response.data
}

export async function getPlateaus(): Promise<PlateauAlert[]> {
  const response = await apiClient.get<PlateauAlert[]>('/dashboard/plateaus')
  return response.data
}
