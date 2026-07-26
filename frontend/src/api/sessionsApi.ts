import { apiClient } from '../lib/apiClient'

export interface SessionSummary {
  id: string
  routineId: string | null
  startedAt: string
  finishedAt: string | null
  setCount: number
}

export interface SessionSet {
  id: string
  exerciseId: string
  exerciseName: string
  setNumber: number
  weightKg: number
  reps: number
  rpe: number | null
  estimated1rmEpley: number
  estimated1rmBrzycki: number | null
  estimated1rmBest: number
  createdAt: string
}

export interface SessionDetail {
  id: string
  routineId: string | null
  startedAt: string
  finishedAt: string | null
  notes: string | null
  sets: SessionSet[]
}

export interface CreateSessionInput {
  routineId?: string | null
  notes?: string | null
}

export interface AddSetInput {
  exerciseId: string
  weightKg: number
  reps: number
  rpe?: number | null
}

export interface FinishSessionInput {
  notes?: string | null
}

export async function listSessions(): Promise<SessionSummary[]> {
  const response = await apiClient.get<SessionSummary[]>('/workout-sessions')
  return response.data
}

export async function getSession(id: string): Promise<SessionDetail> {
  const response = await apiClient.get<SessionDetail>(`/workout-sessions/${id}`)
  return response.data
}

export async function createSession(input: CreateSessionInput): Promise<SessionDetail> {
  const response = await apiClient.post<SessionDetail>('/workout-sessions', input)
  return response.data
}

export async function addSet(sessionId: string, input: AddSetInput): Promise<SessionSet> {
  const response = await apiClient.post<SessionSet>(`/workout-sessions/${sessionId}/sets`, input)
  return response.data
}

export async function finishSession(id: string, input: FinishSessionInput): Promise<SessionDetail> {
  const response = await apiClient.patch<SessionDetail>(`/workout-sessions/${id}`, input)
  return response.data
}
