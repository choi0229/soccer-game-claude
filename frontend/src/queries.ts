import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { useUiStore } from './store';
import type { ActionOutcome, ActionRequest, GameView } from './types';

// 서버가 준 값은 모두 TanStack Query 캐시에만 둔다.
export const runKey = (runId: string) => ['run', runId] as const;
export const outcomesKey = (runId: string) => ['run', runId, 'outcomes'] as const;

export interface RecentOutcome {
  actionCount: number;
  outcome: ActionOutcome;
}

export function useMeta() {
  return useQuery({ queryKey: ['meta'], queryFn: api.meta, staleTime: Infinity });
}

/** 시작 화면의 학교 목록. 시드를 비우면 무작위 시드 하나를 받아 그 시드로 고정한다 */
export function useSchools(seed: number | undefined) {
  return useQuery({
    queryKey: ['schools', seed ?? 'random'],
    queryFn: () => api.schools(seed),
    staleTime: Infinity,
  });
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
    mutationFn: ({ seed, schoolId }: { seed: number; schoolId: number | null }) => api.createRun(seed, schoolId),
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
  const startLive = useUiStore((s) => s.startLive);
  const clearPlayback = useUiStore((s) => s.clearPlayback);
  return useMutation({
    mutationFn: (action: ActionRequest) => api.act(runId, action),
    onSuccess: ({ view, outcome }, action) => {
      const liveLength = queryClient.getQueryData<GameView>(runKey(runId))?.liveMatch?.timeline.length ?? 0;
      queryClient.setQueryData(runKey(runId), view);
      // 승부처에서 멈췄던 하루의 결과는 이어진 결과(그날 전체 기록)로 바꾼다
      queryClient.setQueryData<RecentOutcome[]>(outcomesKey(runId), (prev = []) => {
        const rest = prev.length > 0 && prev[0].outcome.partial ? prev.slice(1) : prev;
        return [{ actionCount: view.actionCount, outcome }, ...rest].slice(0, 10);
      });
      resetDraft();
      if (view.liveMatch) {
        startLive();
      } else if (action.type === 'CLUTCH_CHOICE') {
        // 승부처 직전까지의 줄 + 승부처 결과 한 줄을 바로 보여 준다
        startPlayback(view.matches.length - 1, liveLength + 1);
      } else if (outcome.match) {
        startPlayback(view.matches.length - 1);
      } else if (action.type !== 'EVENT_CHOICE') {
        clearPlayback();
      }
    },
  });
}
