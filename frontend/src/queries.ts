import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { useUiStore } from './store';
import type { ActionOutcome, ActionRequest } from './types';

// 서버가 준 값은 모두 TanStack Query 캐시에만 둔다.
export const runKey = (runId: string) => ['run', runId] as const;
export const outcomesKey = (runId: string) => ['run', runId, 'outcomes'] as const;

export interface RecentOutcome {
  actionCount: number;
  outcome: ActionOutcome;
}

export function useRun(runId: string | null) {
  return useQuery({
    queryKey: runKey(runId ?? ''),
    queryFn: () => api.getRun(runId!),
    enabled: runId !== null,
    retry: false,
  });
}

/** 이번 세션에서 받은 최근 행동 결과 (서버 응답을 캐시에 쌓아 둔 것) */
export function useRecentOutcomes(runId: string) {
  return useQuery({
    queryKey: outcomesKey(runId),
    queryFn: () => [] as RecentOutcome[],
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useCreateRun() {
  const queryClient = useQueryClient();
  const setRunId = useUiStore((s) => s.setRunId);
  return useMutation({
    mutationFn: (seed?: number) => api.createRun(seed),
    onSuccess: (view) => {
      queryClient.setQueryData(runKey(view.runId), view);
      setRunId(view.runId);
    },
  });
}

export function useAct(runId: string) {
  const queryClient = useQueryClient();
  const resetDraft = useUiStore((s) => s.resetDraft);
  const startPlayback = useUiStore((s) => s.startPlayback);
  const clearPlayback = useUiStore((s) => s.clearPlayback);
  return useMutation({
    mutationFn: (action: ActionRequest) => api.act(runId, action),
    onSuccess: ({ view, outcome }, action) => {
      queryClient.setQueryData(runKey(runId), view);
      queryClient.setQueryData<RecentOutcome[]>(outcomesKey(runId), (prev = []) =>
        [{ actionCount: view.actionCount, outcome }, ...prev].slice(0, 10),
      );
      resetDraft();
      if (outcome.match) {
        startPlayback(view.matches.length - 1);
      } else if (action.type !== 'EVENT_CHOICE') {
        clearPlayback();
      }
    },
  });
}
