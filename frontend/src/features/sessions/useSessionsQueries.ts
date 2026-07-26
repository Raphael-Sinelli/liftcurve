import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addSet,
  createSession,
  finishSession,
  getSession,
  listSessions,
  type AddSetInput,
  type CreateSessionInput,
  type FinishSessionInput,
  type SessionDetail,
  type SessionSummary,
} from '../../api/sessionsApi'

const SESSIONS_KEY = ['workout-sessions'] as const
const sessionKey = (id: string) => ['workout-sessions', id] as const

export function useSessions() {
  return useQuery({ queryKey: SESSIONS_KEY, queryFn: listSessions })
}

export function useSession(id: string | undefined) {
  return useQuery({
    queryKey: sessionKey(id ?? ''),
    queryFn: () => getSession(id as string),
    enabled: Boolean(id),
  })
}

function toSummary(session: SessionDetail): SessionSummary {
  return {
    id: session.id,
    routineId: session.routineId,
    startedAt: session.startedAt,
    finishedAt: session.finishedAt,
    setCount: session.sets.length,
  }
}

export function useCreateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateSessionInput) => createSession(input),
    onSuccess: (created) => {
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) => [toSummary(created), ...(current ?? [])])
      queryClient.setQueryData(sessionKey(created.id), created)
    },
  })
}

export function useAddSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, input }: { sessionId: string; input: AddSetInput }) => addSet(sessionId, input),
    onSuccess: (createdSet, { sessionId }) => {
      queryClient.setQueryData<SessionDetail>(sessionKey(sessionId), (current) =>
        current ? { ...current, sets: [...current.sets, createdSet] } : current,
      )
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) =>
        (current ?? []).map((session) => (session.id === sessionId ? { ...session, setCount: session.setCount + 1 } : session)),
      )
    },
  })
}

export function useFinishSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: FinishSessionInput }) => finishSession(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(sessionKey(updated.id), updated)
      queryClient.setQueryData<SessionSummary[]>(SESSIONS_KEY, (current) =>
        (current ?? []).map((session) => (session.id === updated.id ? toSummary(updated) : session)),
      )
    },
  })
}
