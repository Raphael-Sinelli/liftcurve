import { useQuery } from '@tanstack/react-query'
import { getPlateaus, getProgression, getVolume } from '../../api/dashboardApi'

export function useProgression(exerciseId: string | undefined) {
  return useQuery({
    queryKey: ['dashboard', 'progression', exerciseId ?? ''],
    queryFn: () => getProgression(exerciseId as string),
    enabled: Boolean(exerciseId),
  })
}

export function useVolume() {
  return useQuery({ queryKey: ['dashboard', 'volume'], queryFn: getVolume })
}

export function usePlateaus() {
  return useQuery({ queryKey: ['dashboard', 'plateaus'], queryFn: getPlateaus })
}
